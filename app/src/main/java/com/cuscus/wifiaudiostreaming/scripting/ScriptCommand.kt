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

package com.cuscus.wifiaudiostreaming.scripting

import android.content.Intent
import android.net.Uri

enum class ScriptActionType(val id: String) {
    START_SERVER("server"),
    CONNECT("connect"),
    STOP("stop"),
    TOGGLE("toggle"),
    SET("set"),
    USB("usb");

    companion object {
        fun fromId(id: String?): ScriptActionType? {
            val key = id?.lowercase()?.trim()
            return entries.find { it.id == key }
        }

        fun fromName(name: String?): ScriptActionType? {
            return entries.find { it.name.equals(name?.trim(), ignoreCase = true) }
        }
    }
}

object ScriptParams {
    const val INTERNAL = "internal"
    const val MIC = "mic"
    const val SAMPLERATE = "samplerate"
    const val CHANNELS = "channels"
    const val BUFFER = "buffer"
    const val PORT = "port"
    const val MICPORT = "micport"
    const val MULTICAST = "multicast"
    const val RTP = "rtp"
    const val RTPPORT = "rtpport"
    const val HTTP = "http"
    const val HTTPPORT = "httpport"
    const val HTTPSAFARI = "httpsafari"
    const val IFACE = "iface"
    const val IP = "ip"
    const val CLIENTMIC = "clientmic"
    const val CLIENTIP = "clientip"
    const val AUTOCONNECT = "autoconnect"
    const val CONNSOUND = "connsound"
    const val DISCSOUND = "discsound"
    const val MODE = "mode"
    const val AUTHMODE = "authmode"
    const val AUTHKEY = "authkey"
    const val USB = "usb"
    const val USBLATENCY = "usblatency"
    const val SNAPCAST = "snapcast"
    const val SNAPCASTPORT = "snapcastport"
    const val SNAPCASTCTRLPORT = "snapcastctrlport"
    const val SNAPCASTCODEC = "snapcastcodec"
    const val WFASMODE = "wfasmode"
    const val PERSIST = "persist"

    // Non e' un parametro di streaming ma la credenziale che autorizza i comandi
    // che arrivano da fuori dal processo: tenuto fuori da ALL apposta, cosi'
    // nessun esecutore puo' scambiarlo per un'impostazione e nessuno script
    // salvato se lo porta dietro.
    const val TOKEN = "token"

    val ALL = listOf(
        INTERNAL, MIC, SAMPLERATE, CHANNELS, BUFFER, PORT, MICPORT, MULTICAST,
        RTP, RTPPORT, HTTP, HTTPPORT, HTTPSAFARI, IFACE, IP, CLIENTMIC, CLIENTIP,
        AUTOCONNECT, CONNSOUND, DISCSOUND, MODE, AUTHMODE, AUTHKEY,
        USB, USBLATENCY, WFASMODE, PERSIST
    )

    fun parseBool(value: String?): Boolean? {
        return when (value?.lowercase()?.trim()) {
            "true", "1", "on", "yes", "y" -> true
            "false", "0", "off", "no", "n" -> false
            else -> null
        }
    }

    fun parseInt(value: String?): Int? = value?.trim()?.toIntOrNull()
}

data class ScriptCommand(
    val action: ScriptActionType,
    val params: Map<String, String> = emptyMap(),
    val token: String? = null
) {

    fun toUri(): String {
        val builder = Uri.Builder()
            .scheme(SCHEME)
            .authority(action.id)
        params.filter { it.value.isNotBlank() }.forEach { (key, value) ->
            builder.appendQueryParameter(key, value)
        }
        token?.takeIf { it.isNotBlank() }?.let {
            builder.appendQueryParameter(ScriptParams.TOKEN, it)
        }
        return builder.build().toString()
    }

    fun withToken(token: String?): ScriptCommand =
        copy(token = token?.trim()?.takeIf { it.isNotBlank() })

    fun toBroadcastAction(): String = ACTION_PREFIX + action.name

    fun toAdbCommand(packageName: String): String {
        val extras = buildMap<String, String> {
            putAll(params.filter { it.value.isNotBlank() })
            token?.takeIf { it.isNotBlank() }?.let { put(ScriptParams.TOKEN, it) }
        }.entries.joinToString(" ") { (k, v) -> "-e $k \"$v\"" }
        return "am broadcast -a ${toBroadcastAction()} -n $packageName/.scripting.ScriptCommandReceiver $extras".trim()
    }

    fun bool(key: String): Boolean? = ScriptParams.parseBool(params[key])
    fun int(key: String): Int? = ScriptParams.parseInt(params[key])
    fun str(key: String): String? = params[key]?.takeIf { it.isNotBlank() }

    companion object {
        const val SCHEME = "wifiaudio"
        const val ACTION_PREFIX = "com.cuscus.wifiaudiostreaming.action."

        fun fromUri(uri: Uri): ScriptCommand? {
            val action = ScriptActionType.fromId(uri.host ?: uri.authority) ?: return null
            val params = mutableMapOf<String, String>()
            var token: String? = null
            val names = runCatching { uri.queryParameterNames }.getOrNull().orEmpty()
            for (key in names) {
                val value = runCatching { uri.getQueryParameter(key) }.getOrNull()
                if (value.isNullOrBlank()) continue
                val normalized = key.lowercase()
                if (normalized == ScriptParams.TOKEN) token = value.trim()
                else params[normalized] = value
            }
            return ScriptCommand(action, params, token)
        }

        fun fromIntent(intent: Intent): ScriptCommand? {
            val data = intent.data
            if (data != null && data.scheme.equals(SCHEME, ignoreCase = true)) {
                return fromUri(data)
            }
            val rawAction = intent.action ?: return null
            if (!rawAction.startsWith(ACTION_PREFIX)) return null
            val action = ScriptActionType.fromName(rawAction.removePrefix(ACTION_PREFIX)) ?: return null
            val params = mutableMapOf<String, String>()
            var token: String? = null
            val extras = runCatching { intent.extras }.getOrNull()
            if (extras != null) {
                for (key in ScriptParams.ALL) {
                    val value = runCatching { extras.get(key)?.toString() }.getOrNull()
                    if (!value.isNullOrBlank()) params[key] = value
                }
                token = runCatching { extras.get(ScriptParams.TOKEN)?.toString() }
                    .getOrNull()?.trim()?.takeIf { it.isNotBlank() }
            }
            return ScriptCommand(action, params, token)
        }
    }
}

data class ResolvedServerParams(
    val streamInternal: Boolean,
    val streamMic: Boolean,
    val sampleRate: Int,
    val channelConfig: String,
    val bufferSize: Int,
    val isMulticast: Boolean,
    val streamingPort: Int,
    val networkInterface: String,
    val rtpEnabled: Boolean,
    val rtpPort: Int,
    val httpEnabled: Boolean,
    val httpPort: Int,
    val dlnaEnabled: Boolean = false,
    val dlnaPort: Int = 8081,
    val dlnaFormat: String = "auto",
    val dlnaDevices: List<String> = emptyList(),
    val snapcastEnabled: Boolean = false,
    val snapcastPort: Int = com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.STREAM_PORT,
    val snapcastControlPort: Int = com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.CONTROL_PORT,
    val snapcastCodec: String = com.cuscus.wifiaudiostreaming.snapcast.SnapcastCodecs.PCM,
    val snapcastChunkMs: Int = com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.CHUNK_MS,
    val snapcastBufferMs: Int = com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.BUFFER_MS,
    val snapcastStreamName: String = com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.STREAM_NAME,
    val usbMode: Boolean = false,
    val usbLatencyMs: Int = 20,
    val persist: Boolean = false
)
