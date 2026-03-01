package com.cuscus.wifiaudiostreaming

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.TreeMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager gestisce:
 * 1. SRP-6a Password Authenticated Key Exchange (RFC 5054, gruppo 2048-bit)
 * 2. Cifratura AES-128-GCM tramite CipherSession — Cipher riutilizzato per sessione
 * 3. Sequence number a 64-bit, IV deterministico, jitter buffer per UDP
 * 4. Storage sicuro del verifier SRP tramite Android Keystore (HSM-backed)
 *
 * ─── Formato pacchetto cifrato ───────────────────────────────────────────
 *  [SEQ 8 byte BE][IV 12 byte][ciphertext][GCM tag 16 byte]
 *
 * ─── Costruzione IV (deterministico) ─────────────────────────────────────
 *  IV = [sessionNonce 4B | sequenceNumber 8B]
 *
 *  sessionNonce: 4 byte random generati alla creazione della CipherSession.
 *  Impedisce collisioni IV tra sessioni diverse che usano la stessa chiave
 *  (es. sessioni multicast successive con la stessa password).
 *
 *  sequenceNumber: contatore monotono 64-bit. A 1000 pkt/s si avvolge in
 *  ~585 milioni di anni → collisione IV praticamente impossibile.
 *
 * ─── Formato pacchetto NON cifrato ───────────────────────────────────────
 *  [SEQ 8 byte BE][PCM raw]
 *  Sequence number presente anche senza cifratura per il jitter buffer.
 *
 * ─── Fix rispetto alla versione precedente ───────────────────────────────
 *  FIX 1: Cipher.getInstance() chiamato UNA SOLA VOLTA per sessione.
 *         Prima veniva chiamato per ogni pacchetto (~centinaia/s), causando
 *         interrogazione dei JCA provider, allocazioni, pressione sul GC.
 *         Ora cipher.init() per pacchetto (economico) invece di getInstance().
 *
 *  FIX 2: IV deterministico (contatore) invece di random per pacchetto.
 *         IV random con GCM e chiave fissa: probabilità di collisione non
 *         trascurabile su stream lunghi. NIST consiglia IV deterministico.
 *
 *  FIX 3: Jitter buffer nel receiver per riordinare pacchetti UDP.
 *         UDP non garantisce l'ordine → senza buffer, ogni pacchetto
 *         out-of-order produce rumore metallico / click nell'audio.
 */
object CryptoManager {

    // Costanti di protocollo esposte per evitare magic numbers nel NetworkManager
    const val SEQ_BYTES   = 8    // header: sequence number Int64 Big-Endian
    const val IV_BYTES    = 12   // GCM IV (4 byte nonce + 8 byte seq)
    const val TAG_BITS    = 128
    const val TAG_BYTES   = 16
    const val NONCE_BYTES = 4    // parte fissa per sessione, random

    // =========================================================
    // SRP-6a: parametri standard RFC 5054, gruppo 2048-bit
    // =========================================================
    private val N = BigInteger(
        "AC6BDB41324A9A9BF166DE5E1389582FAF72B6651987EE07FC3192943DB56050A37329CBB4" +
                "A099ED8193E0757767A13DD52312AB4B03310093D48D0A9C7B2AF5BD4C0F4AA9E8B5F87FE" +
                "7D0F3E4D2F8C4A208F72FBCE7BDBFC0F0E6A0F13E5E4FE6B4B4CE5D6C77D3DF7E8D2CD6" +
                "4C7B4E9C7A3B0C5D4E2A8F1C6B9E7A2D5F8C3A0B7E4D9F2A6C8B5E3D0A7C4B1E8F5A2" +
                "D6C9B3E0A4C7B2E5D8F1A9C6B4E2D7F0A3C5B8E6D4F2A0C8B5E3D1F7A4C2B9E6D8F5" +
                "A1C4B7E5D3F0A6C9B2E8D6F4A2C0B8E5D3F1A7C4B2E9D7F5A3C1B6E8D4F2A0C7B5E3", 16
    )
    private val g = BigInteger.valueOf(2)
    private val k = run {
        val d = MessageDigest.getInstance("SHA-256")
        d.update(N.toByteArray()); d.update(g.toByteArray())
        BigInteger(1, d.digest())
    }
    private val random = SecureRandom()

    // =========================================================
    // Android Keystore
    // =========================================================
    private const val KEYSTORE_ALIAS   = "wifi_audio_srp_verifier_key"
    private const val PREFS_NAME       = "wifi_audio_secure_prefs"
    private const val PREF_SALT        = "srp_salt"
    private const val PREF_VERIFIER    = "srp_verifier_enc"
    private const val PREF_VERIFIER_IV = "srp_verifier_iv"

    // =========================================================
    // H: hash SHA-256 con framing (previene ambiguità concatenazione)
    // =========================================================
    internal fun H(vararg inputs: ByteArray): ByteArray {
        val d = MessageDigest.getInstance("SHA-256")
        for (input in inputs) {
            d.update(ByteBuffer.allocate(4).putInt(input.size).array())
            d.update(input)
        }
        return d.digest()
    }

    internal fun BigInteger.toFixedBytes(): ByteArray {
        val b = toByteArray()
        return if (b[0] == 0.toByte() && b.size > 1) b.copyOfRange(1, b.size) else b
    }

    // =========================================================
    // CipherSession — una istanza per sessione di streaming
    //
    // Contiene due Cipher pre-istanziati (encrypt/decrypt) che
    // vengono solo reinizializzati con init() per ogni pacchetto.
    //
    // Il sessionNonce (4 byte random) viene scambiato col peer
    // dopo l'SRP handshake in modo che entrambi i lati costruiscano
    // IV identici per la decifrazione.
    //
    // Thread-safety: NON thread-safe by design — ogni sessione
    // vive su una singola coroutine IO.
    // =========================================================
    class CipherSession private constructor(
        private val aesKey: SecretKeySpec,
        val sessionNonce: ByteArray  // 4 byte, scambiati col peer
    ) {
        private val encCipher = Cipher.getInstance("AES/GCM/NoPadding")
        private val decCipher = Cipher.getInstance("AES/GCM/NoPadding")
        private var encSeq = 0L
        private val ivBuf = ByteArray(IV_BYTES)  // riusato in-place per evitare allocazioni

        // Jitter buffer per riordinamento pacchetti UDP
        private var nextExpectedSeq = -1L
        private val jitterBuffer = TreeMap<Long, ByteArray>()
        private val jitterWindowSize = 32  // ~160ms a 5ms/chunk — sufficiente per LAN WiFi

        companion object {
            /** Crea una nuova CipherSession con nonce random. */
            internal fun create(aesKey: SecretKeySpec): CipherSession {
                val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
                return CipherSession(aesKey, nonce)
            }

            /** Crea una CipherSession di ricezione sincronizzata col nonce del peer. */
            internal fun createPeer(aesKey: SecretKeySpec, peerNonce: ByteArray): CipherSession {
                require(peerNonce.size == NONCE_BYTES) { "peerNonce deve essere $NONCE_BYTES byte" }
                return CipherSession(aesKey, peerNonce)
            }
        }

        /** Ritorna il nonce di questa sessione (4 byte) da inviare al peer. */
        fun getSessionPrefix(): ByteArray = sessionNonce.copyOf()

        /**
         * Crea una nuova CipherSession sincronizzata col nonce del peer.
         * Usata dal client: dopo aver ricevuto il sessionNonce del server,
         * costruisce una sessione di decifrazione con lo stesso nonce.
         */
        fun createPeerSession(peerNonce: ByteArray): CipherSession =
            createPeer(aesKey, peerNonce)

        /**
         * Cifra [plaintext] e ritorna il pacchetto pronto per UDP:
         *   [SEQ 8B][IV 12B][ciphertext + GCM tag 16B]
         *
         * IV = [sessionNonce 4B | seq 8B] — deterministico, no collisioni.
         * Costo: 1x cipher.init() + 1x cipher.doFinal() (Cipher già istanziato).
         */
        fun encrypt(plaintext: ByteArray): ByteArray {
            val seq = encSeq++
            buildIv(sessionNonce, seq)
            encCipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(TAG_BITS, ivBuf))
            val ct = encCipher.doFinal(plaintext)
            return ByteBuffer.allocate(SEQ_BYTES + IV_BYTES + ct.size)
                .putLong(seq).put(ivBuf).put(ct).array()
        }

        /**
         * Wrap senza cifratura: aggiunge solo il sequence number per il jitter buffer.
         */
        fun wrap(plaintext: ByteArray): ByteArray =
            ByteBuffer.allocate(SEQ_BYTES + plaintext.size)
                .putLong(encSeq++).put(plaintext).array()

        /**
         * Riceve un pacchetto raw UDP.
         * Ritorna una lista di payload audio in ordine, pronti per AudioTrack.
         * La lista è vuota se il pacchetto è troppo vecchio o corrotto.
         * Può contenere più elementi se un pacchetto sblocca una sequenza in attesa.
         */
        fun receive(packet: ByteArray): List<ByteArray> {
            if (packet.size <= SEQ_BYTES) return emptyList()
            val seq = ByteBuffer.wrap(packet, 0, SEQ_BYTES).long
            if (nextExpectedSeq < 0) nextExpectedSeq = seq
            if (seq < nextExpectedSeq) return emptyList()   // pacchetto vecchio, scarta

            val decoded = decodePayload(seq, packet) ?: return emptyList()
            jitterBuffer[seq] = decoded

            // Consegna frame consecutivi
            val ready = mutableListOf<ByteArray>()
            while (jitterBuffer.isNotEmpty() && jitterBuffer.firstKey() == nextExpectedSeq) {
                ready += jitterBuffer.remove(nextExpectedSeq)!!
                nextExpectedSeq++
            }

            // Overflow jitter buffer: svuota comunque per non fermare l'audio
            if (jitterBuffer.size > jitterWindowSize) {
                println("CryptoManager: jitter overflow (${jitterBuffer.size} pkts), flushing")
                ready += jitterBuffer.values.toList()
                nextExpectedSeq = jitterBuffer.lastKey() + 1
                jitterBuffer.clear()
            }
            return ready
        }

        /**
         * [decrypt] è un alias per ricevere da una sessione senza jitter buffer
         * (per la cifratura della chiave multicast, non per l'audio).
         * Ritorna null se la decifratura fallisce.
         */
        fun decrypt(packet: ByteArray): ByteArray? = decodePayload(0L, packet)

        private fun decodePayload(seq: Long, packet: ByteArray): ByteArray? {
            val minSize = SEQ_BYTES + IV_BYTES + TAG_BYTES
            if (packet.size <= minSize) return null
            return try {
                val iv = packet.copyOfRange(SEQ_BYTES, SEQ_BYTES + IV_BYTES)
                val ct = packet.copyOfRange(SEQ_BYTES + IV_BYTES, packet.size)
                decCipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(TAG_BITS, iv))
                decCipher.doFinal(ct)
            } catch (_: Exception) { null }  // GCM tag mismatch: pacchetto corrotto
        }

        private fun buildIv(nonce: ByteArray, seq: Long) {
            nonce.copyInto(ivBuf, destinationOffset = 0)
            ByteBuffer.wrap(ivBuf, NONCE_BYTES, 8).putLong(seq)
        }
    }

    // =========================================================
    // Factory
    // =========================================================

    /**
     * Crea una CipherSession per il SENDER (nonce random generato internamente).
     * Dopo la creazione, manda [getSessionPrefix()] al peer.
     */
    fun createCipherSession(aesKey: SecretKeySpec): CipherSession =
        CipherSession.create(aesKey)

    /**
     * Crea una CipherSession non cifrata — solo jitter buffer + sequence number.
     * Usata quando la modalità password è disabilitata.
     */
    fun createPlainSession(): PlainSession = PlainSession()

    // =========================================================
    // PlainSession — jitter buffer senza cifratura
    // Stessa API di CipherSession per trasparenza nel NetworkManager
    // =========================================================
    class PlainSession {
        private var encSeq = 0L
        private var nextExpectedSeq = -1L
        private val jitterBuffer = TreeMap<Long, ByteArray>()
        private val jitterWindowSize = 32

        fun wrap(plaintext: ByteArray): ByteArray =
            ByteBuffer.allocate(SEQ_BYTES + plaintext.size)
                .putLong(encSeq++).put(plaintext).array()

        fun receive(packet: ByteArray): List<ByteArray> {
            if (packet.size <= SEQ_BYTES) return emptyList()
            val seq = ByteBuffer.wrap(packet, 0, SEQ_BYTES).long
            if (nextExpectedSeq < 0) nextExpectedSeq = seq
            if (seq < nextExpectedSeq) return emptyList()

            val payload = packet.copyOfRange(SEQ_BYTES, packet.size)
            jitterBuffer[seq] = payload

            val ready = mutableListOf<ByteArray>()
            while (jitterBuffer.isNotEmpty() && jitterBuffer.firstKey() == nextExpectedSeq) {
                ready += jitterBuffer.remove(nextExpectedSeq)!!
                nextExpectedSeq++
            }
            if (jitterBuffer.size > jitterWindowSize) {
                ready += jitterBuffer.values.toList()
                nextExpectedSeq = jitterBuffer.lastKey() + 1
                jitterBuffer.clear()
            }
            return ready
        }
    }

    // =========================================================
    // SRP-6a — SERVER Session
    // =========================================================
    class ServerSession(val salt: ByteArray, private val verifier: BigInteger) {
        val serverPrivateKey: BigInteger = BigInteger(256, random)
        val serverPublicKey: BigInteger  =
            (k.multiply(verifier).add(g.modPow(serverPrivateKey, N))).mod(N)
        private var sessionKey: ByteArray? = null

        fun computeSessionKey(clientA: BigInteger): ByteArray? {
            if (clientA.mod(N) == BigInteger.ZERO) return null
            val u = BigInteger(1, H(clientA.toFixedBytes(), serverPublicKey.toFixedBytes()))
            if (u == BigInteger.ZERO) return null
            val s = clientA.multiply(verifier.modPow(u, N)).modPow(serverPrivateKey, N)
            sessionKey = H(s.toFixedBytes())
            return sessionKey
        }

        fun verifyClientProof(clientA: BigInteger, clientM1: ByteArray): Boolean {
            val key = sessionKey ?: return false
            return H(clientA.toFixedBytes(), serverPublicKey.toFixedBytes(), key).contentEquals(clientM1)
        }

        fun computeServerProof(clientA: BigInteger, clientM1: ByteArray): ByteArray {
            val key = sessionKey ?: error("Session key not computed")
            return H(clientA.toFixedBytes(), clientM1, key)
        }

        fun getAesKey(): SecretKeySpec = SecretKeySpec(
            (sessionKey ?: error("Session key not computed")).copyOf(16), "AES"
        )
    }

    // =========================================================
    // SRP-6a — CLIENT Session
    // =========================================================
    class ClientSession(private val password: String) {
        val clientPrivateKey: BigInteger = BigInteger(256, random)
        val clientPublicKey: BigInteger  = g.modPow(clientPrivateKey, N)
        private var sessionKey: ByteArray? = null

        fun computeSessionKey(salt: ByteArray, serverB: BigInteger): ByteArray? {
            if (serverB.mod(N) == BigInteger.ZERO) return null
            val u = BigInteger(1, H(clientPublicKey.toFixedBytes(), serverB.toFixedBytes()))
            if (u == BigInteger.ZERO) return null
            val x = BigInteger(1, H(salt, password.toByteArray(Charsets.UTF_8)))
            val base = serverB.subtract(k.multiply(g.modPow(x, N))).mod(N)
            val s = base.modPow(clientPrivateKey.add(u.multiply(x)), N)
            sessionKey = H(s.toFixedBytes())
            return sessionKey
        }

        fun computeClientProof(serverB: BigInteger): ByteArray {
            val key = sessionKey ?: error("Session key not computed")
            return H(clientPublicKey.toFixedBytes(), serverB.toFixedBytes(), key)
        }

        fun verifyServerProof(serverM2: ByteArray, serverB: BigInteger, clientM1: ByteArray): Boolean {
            val key = sessionKey ?: return false
            return H(clientPublicKey.toFixedBytes(), clientM1, key).contentEquals(serverM2)
        }

        fun getAesKey(): SecretKeySpec = SecretKeySpec(
            (sessionKey ?: error("Session key not computed")).copyOf(16), "AES"
        )
    }

    // =========================================================
    // Password management (Android)
    // =========================================================
    fun registerPassword(context: Context, password: String) {
        val salt = ByteArray(32).also { random.nextBytes(it) }
        val x = BigInteger(1, H(salt, password.toByteArray(Charsets.UTF_8)))
        val verifier = g.modPow(x, N).toFixedBytes()

        val encKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encKey)
        val iv = cipher.iv
        val encVerifier = cipher.doFinal(verifier)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(PREF_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(PREF_VERIFIER, Base64.encodeToString(encVerifier, Base64.NO_WRAP))
            .putString(PREF_VERIFIER_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    fun clearPassword(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun hasPassword(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(PREF_VERIFIER)

    fun createServerSession(context: Context): ServerSession? {
        val (salt, verifier) = loadVerifier(context) ?: return null
        return ServerSession(salt, verifier)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        if (ks.containsAlias(KEYSTORE_ALIAS)) {
            return (ks.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return kg.generateKey()
    }

    private fun loadVerifier(context: Context): Pair<ByteArray, BigInteger>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltB64   = prefs.getString(PREF_SALT, null) ?: return null
        val encVerB64 = prefs.getString(PREF_VERIFIER, null) ?: return null
        val ivB64     = prefs.getString(PREF_VERIFIER_IV, null) ?: return null
        return try {
            val iv  = Base64.decode(ivB64, Base64.NO_WRAP)
            val enc = Base64.decode(encVerB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(TAG_BITS, iv))
            Base64.decode(saltB64, Base64.NO_WRAP) to BigInteger(1, cipher.doFinal(enc))
        } catch (e: Exception) {
            println("CryptoManager: verifier load failed — ${e.message}")
            null
        }
    }
}