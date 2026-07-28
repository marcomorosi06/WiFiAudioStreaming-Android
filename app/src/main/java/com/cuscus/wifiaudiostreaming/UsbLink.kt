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
    const val MAX_USB_LATENCY_MS = 120

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
            if (on) refresh()
            return
        }
        enabled = on
        if (on) {
            attach(context.applicationContext)
            refresh()
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
                refresh()
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

    fun refresh(): State {
        if (!enabled) {
            _state.value = State()
            return _state.value
        }
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
        if (isReady()) minOf(configured, latencyMs) else configured

    fun isUsbAddress(host: String?): Boolean {
        if (host == null) return false
        return TETHER_SUBNETS.any { host.startsWith(it) }
    }

    fun isUsbPeer(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val iface = activeInterface() ?: return false
        val peer = runCatching { java.net.InetAddress.getByName(host) }.getOrNull() ?: return false
        if (peer !is java.net.Inet4Address) return false
        val peerBits = toInt(peer.address)
        val same = runCatching {
            iface.interfaceAddresses.any { ia ->
                val local = ia.address
                if (local !is java.net.Inet4Address) return@any false
                val prefix = ia.networkPrefixLength.toInt()
                if (prefix !in 1..32) return@any false
                val mask = if (prefix == 32) -1 else (-1 shl (32 - prefix))
                (toInt(local.address) and mask) == (peerBits and mask)
            }
        }.getOrDefault(false)
        return same || isUsbAddress(host)
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
        if (IFACE_TOKENS.any { name.startsWith(it) }) s += 100
        if (IFACE_TOKENS.any { display.contains(it) }) s += 40
        if (display.contains("remote ndis")) s += 60
        val addresses = ipv4List(iface)
        if (addresses.any { host -> TETHER_SUBNETS.any { host.startsWith(it) } }) s += 60
        if (name.startsWith("wlan") || name.startsWith("p2p") || name.startsWith("ap")) s -= 300
        if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun")) s -= 300
        if (display.contains("wi-fi") || display.contains("wireless")) s -= 300
        return s
    }

    private fun ipv4List(iface: NetworkInterface): List<String> = runCatching {
        iface.inetAddresses.toList()
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    private fun firstIpv4(iface: NetworkInterface): String? = ipv4List(iface).firstOrNull()

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
