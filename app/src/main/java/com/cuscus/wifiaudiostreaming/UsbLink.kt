package com.cuscus.wifiaudiostreaming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

object UsbLink {

    private const val TAG = "WFAS-USB"
    private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"

    const val DEFAULT_USB_LATENCY_MS = 20
    const val MIN_USB_LATENCY_MS = 5
    const val MAX_USB_LATENCY_MS = 300

    private val IFACE_TOKENS = listOf("rndis", "ncm", "usb")
    private val TETHER_SUBNETS = listOf("192.168.42.", "192.168.112.")

    enum class Stage { DISABLED, NO_CABLE, CABLE_NO_TETHER, READY }

    data class State(
        val stage: Stage = Stage.DISABLED,
        val interfaceName: String? = null,
        val displayName: String? = null,
        val localAddress: String? = null,
        val cableConnected: Boolean = false,
        val tetherFunctionActive: Boolean = false
    ) {
        val isReady: Boolean get() = stage == Stage.READY
    }

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var latencyMs: Int = DEFAULT_USB_LATENCY_MS
        private set

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    @Volatile private var cableConnected = false
    @Volatile private var tetherFunctionActive = false
    @Volatile private var cachedInterface: NetworkInterface? = null

    private var receiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    fun configure(context: Context, on: Boolean, presetLatencyMs: Int = DEFAULT_USB_LATENCY_MS) {
        latencyMs = presetLatencyMs.coerceIn(MIN_USB_LATENCY_MS, MAX_USB_LATENCY_MS)
        if (on == enabled) {
            if (on) refresh(force = true)
            return
        }
        enabled = on
        if (on) {
            attach(context.applicationContext)
            NetAddr.invalidateScan()
            refresh(force = true)
        } else {
            detach()
            cachedInterface = null
            _state.value = State()
        }
    }

    private fun attach(context: Context) {
        if (receiver != null) return
        appContext = context
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_USB_STATE) return
                cableConnected = intent.getBooleanExtra("connected", false)
                tetherFunctionActive =
                    intent.getBooleanExtra("rndis", false) || intent.getBooleanExtra("ncm", false)
                NetAddr.invalidateScan()
                refresh(force = true)
            }
        }
        receiver = r
        val filter = IntentFilter(ACTION_USB_STATE)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(r, filter)
            }
        }.onFailure { Log.w(TAG, "registerReceiver failed: ${it.message}") }
    }

    private fun detach() {
        val ctx = appContext
        val r = receiver
        if (ctx != null && r != null) runCatching { ctx.unregisterReceiver(r) }
        receiver = null
    }

    private const val SCAN_THROTTLE_MS = 1000L

    @Volatile private var lastScanAt = 0L

    fun refresh(force: Boolean = false): State {
        if (!enabled) {
            _state.value = State()
            return _state.value
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastScanAt < SCAN_THROTTLE_MS) return _state.value
        lastScanAt = now
        val iface = findInterface()
        cachedInterface = iface
        val addr = iface?.let { firstIpv4(it) }
        val stage = when {
            iface != null && addr != null -> Stage.READY
            cableConnected -> Stage.CABLE_NO_TETHER
            else -> Stage.NO_CABLE
        }
        val next = State(
            stage = stage,
            interfaceName = iface?.name,
            displayName = iface?.displayName ?: iface?.name,
            localAddress = addr,
            cableConnected = cableConnected,
            tetherFunctionActive = tetherFunctionActive
        )
        if (next != _state.value) {
            _state.value = next
            Log.i(TAG, "state=$next")
        }
        return next
    }

    fun activeInterface(): NetworkInterface? {
        if (!enabled) return null
        val cached = cachedInterface
        if (cached != null && runCatching { cached.isUp }.getOrDefault(false) &&
            firstIpv4(cached) != null
        ) return cached
        refresh()
        return cachedInterface
    }

    fun isReady(): Boolean = enabled && activeInterface() != null

    fun effectiveLatencyMs(configured: Int): Int =
        if (isReady()) latencyMs else configured

    fun isTetherInterfaceName(name: String?): Boolean {
        val n = name?.lowercase().orEmpty()
        return IFACE_TOKENS.any { n.startsWith(it) }
    }

    fun isUsbAddress(host: String?): Boolean {
        if (host == null) return false
        return TETHER_SUBNETS.any { host.startsWith(it) }
    }

    private fun sharesSubnet(iface: NetworkInterface, host: String): Boolean {
        val peer = runCatching { java.net.InetAddress.getByName(host) }.getOrNull() ?: return false
        if (peer !is java.net.Inet4Address) return false
        val peerBits = toInt(peer.address)
        return runCatching {
            iface.interfaceAddresses.any { ia ->
                val local = ia.address
                if (local !is java.net.Inet4Address) return@any false
                val prefix = ia.networkPrefixLength.toInt()
                if (prefix !in 1..32) return@any false
                val mask = if (prefix == 32) -1 else (-1 shl (32 - prefix))
                (toInt(local.address) and mask) == (peerBits and mask)
            }
        }.getOrDefault(false)
    }

    fun isUsbPeer(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val iface = activeInterface() ?: return false
        return sharesSubnet(iface, host) || isUsbAddress(host)
    }

    @Volatile private var detectedCache: NetworkInterface? = null
    @Volatile private var detectedAt = 0L

    fun detectedInterface(): NetworkInterface? {
        if (enabled) activeInterface()?.let { return it }
        val now = System.currentTimeMillis()
        if (detectedAt != 0L && now - detectedAt < SCAN_THROTTLE_MS) return detectedCache
        detectedAt = now
        detectedCache = findInterface()
        return detectedCache
    }

    fun isUsbPeerDetected(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        if (isUsbAddress(host)) return true
        val iface = detectedInterface() ?: return false
        return sharesSubnet(iface, host)
    }

    private fun toInt(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)

    fun findInterface(): NetworkInterface? {
        val all = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }
            .getOrNull() ?: return null
        return all
            .mapNotNull { iface ->
                val usable = runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false)
                if (!usable || firstIpv4(iface) == null) null else iface to score(iface)
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    fun candidates(): List<NetworkInterface> {
        val all = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }
            .getOrNull() ?: return emptyList()
        return all
            .mapNotNull { iface ->
                val usable = runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false)
                if (!usable || firstIpv4(iface) == null) null else iface to score(iface)
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun score(iface: NetworkInterface): Int {
        var s = 0
        val name = iface.name?.lowercase().orEmpty()
        val display = runCatching { iface.displayName?.lowercase() }.getOrNull().orEmpty()
        if (display.contains("remote ndis")) s += 120
        if (display.contains("ndis")) s += 60
        if (display.contains("internet sharing")) s += 60
        if (display.contains("tether")) s += 60
        if (display.contains("android")) s += 40
        if (IFACE_TOKENS.any { display.contains(it) }) s += 70
        if (IFACE_TOKENS.any { name.startsWith(it) }) s += 70
        if (ipv4List(iface).any { host -> TETHER_SUBNETS.any { host.startsWith(it) } }) s += 90
        if (display.contains("virtual") || display.contains("vmware") ||
            display.contains("hyper-v") || display.contains("wsl") ||
            display.contains("loopback") || display.contains("tap") ||
            display.contains("tunnel")
        ) s -= 300
        if (display.contains("wi-fi") || display.contains("wireless") ||
            name.startsWith("wlan") || name.startsWith("wl")
        ) s -= 300
        return s
    }

    private fun ipv4List(iface: NetworkInterface): List<String> = runCatching {
        iface.inetAddresses.toList()
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    private fun firstIpv4(iface: NetworkInterface): String? = ipv4List(iface).firstOrNull()

    fun inspect(): List<String> {
        val all = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull()
            ?: return listOf("enumeration failed: NetworkInterface.getNetworkInterfaces() returned nothing")
        if (all.isEmpty()) return listOf("enumeration returned no interfaces")
        return all.map { iface ->
            runCatching {
                val name = runCatching { iface.name }.getOrDefault("?")
                val display = runCatching { iface.displayName }.getOrNull() ?: "-"
                val v4 = ipv4List(iface).joinToString(",").ifBlank { "-" }
                val v6 = runCatching {
                    iface.inetAddresses.toList()
                        .filterIsInstance<java.net.Inet6Address>()
                        .mapNotNull { it.hostAddress }
                        .joinToString(",")
                }.getOrNull()?.ifBlank { "-" } ?: "-"
                val up = runCatching { iface.isUp }.getOrDefault(false)
                val virt = runCatching { iface.isVirtual }.getOrDefault(false)
                val mcast = runCatching { iface.supportsMulticast() }.getOrDefault(false)
                "$name | $display | v4=$v4 | v6=$v6 | up=$up virtual=$virt mcast=$mcast | score=${score(iface)}"
            }.getOrElse { "interface unreadable: ${it.javaClass.simpleName}: ${it.message}" }
        }
    }

    fun openTetherSettings(context: Context): Boolean {
        val intents = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$TetherSettingsActivity"
            ),
            Intent("com.android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            val ok = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }
}
