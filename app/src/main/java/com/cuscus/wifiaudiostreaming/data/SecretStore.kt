/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package com.cuscus.wifiaudiostreaming.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cuscus.wifiaudiostreaming.scripting.AutomationGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore

/**
 * I segreti dell'app, tenuti fuori dal DataStore.
 *
 * Il DataStore va benissimo per le impostazioni, ma il suo file finisce
 * nell'Auto Backup e nel trasferimento device-to-device, e un token in chiaro
 * dentro un backup e' un token che ha lasciato il telefono. Qui il valore e'
 * cifrato con una chiave che vive nel Keystore e non esce mai dal TEE: anche se
 * il file venisse copiato altrove sarebbe illeggibile, e questo lega il token al
 * dispositivo che lo ha generato — per un token di automazione e' la proprieta'
 * che vogliamo, non un effetto collaterale.
 *
 * Contro root non protegge (root diventa il nostro UID e chiede al Keystore di
 * decifrare): serve contro l'estrazione offline della partizione dati e contro i
 * backup. La regola di esclusione in `data_extraction_rules.xml` copre il resto.
 */
class SecretStore private constructor(private val prefs: SharedPreferences) {

    private val _automationToken = MutableStateFlow(prefs.getString(KEY_AUTOMATION_TOKEN, "").orEmpty())
    val automationToken: StateFlow<String> = _automationToken.asStateFlow()

    /** Genera il token al primo accesso e lo riusa sempre dopo. */
    @Synchronized
    fun ensureAutomationToken(): String {
        val current = prefs.getString(KEY_AUTOMATION_TOKEN, "").orEmpty()
        if (current.isNotBlank()) {
            _automationToken.value = current
            return current
        }
        return regenerateAutomationToken()
    }

    @Synchronized
    fun regenerateAutomationToken(): String {
        val token = AutomationGate.newToken()
        prefs.edit().putString(KEY_AUTOMATION_TOKEN, token).commit()
        _automationToken.value = token
        return token
    }

    @Synchronized
    fun storeServerKey(serverIp: String, key: String) {
        prefs.edit().putString(KEY_SERVER_PREFIX + serverIp.trim().lowercase(), key).commit()
    }

    @Synchronized
    fun loadServerKey(serverIp: String): String? {
        return prefs.getString(KEY_SERVER_PREFIX + serverIp.trim().lowercase(), null)
    }

    @Synchronized
    fun clearServerKey(serverIp: String) {
        prefs.edit().remove(KEY_SERVER_PREFIX + serverIp.trim().lowercase()).commit()
    }

    @Synchronized
    fun clearAllServerKeys() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_SERVER_PREFIX) }.forEach { editor.remove(it) }
        editor.commit()
    }

    companion object {
        private const val FILE_NAME = "wfas_secrets"
        private const val MASTER_KEY_ALIAS = "wfas_secrets_master_key"
        private const val KEY_AUTOMATION_TOKEN = "automation_token"
        private const val KEY_SERVER_PREFIX = "server_key_"

        @Volatile
        private var instance: SecretStore? = null

        /**
         * Costruire il file cifrato tocca il Keystore, quindi va chiamato fuori
         * dal main thread. L'istanza e' condivisa: MainActivity, receiver e
         * trampoline vivono nello stesso processo.
         */
        fun get(context: Context): SecretStore {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: SecretStore(openPrefs(context.applicationContext)).also { instance = it }
            }
        }

        // Un backup ripristinato altrove, o un Keystore invalidato dal sistema,
        // lascia un file che non si apre piu'. Non e' un errore da propagare:
        // si butta via tutto e si riparte con un token nuovo, che e' esattamente
        // il comportamento voluto quando i dati arrivano da un altro telefono.
        private fun openPrefs(context: Context): SharedPreferences =
            runCatching { createPrefs(context) }.getOrElse {
                wipe(context)
                createPrefs(context)
            }

        private fun createPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        private fun wipe(context: Context) {
            runCatching { context.deleteSharedPreferences(FILE_NAME) }
            runCatching {
                KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                    .deleteEntry(MASTER_KEY_ALIAS)
            }
        }

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
}
