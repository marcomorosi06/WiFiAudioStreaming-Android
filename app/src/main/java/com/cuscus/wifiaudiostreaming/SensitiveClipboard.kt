/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
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

package com.cuscus.wifiaudiostreaming

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Copies a secret (automation token, pairing key, a URI that carries either)
 * with the clipboard flag that marks it as sensitive. Android 13 and later then
 * hide it from the copy preview, and keyboards that honour the flag keep it out
 * of their clipboard history and cloud sync. Earlier versions ignore the extra.
 */
object SensitiveClipboard {

    // ClipDescription.EXTRA_IS_SENSITIVE, spelled out for releases before 33
    // where the constant does not exist but some keyboards already read it.
    private const val EXTRA_IS_SENSITIVE_COMPAT = "android.content.extra.IS_SENSITIVE"

    fun copy(context: Context, text: String, label: String = "WiFi Audio Streaming") {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text)
        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            EXTRA_IS_SENSITIVE_COMPAT
        }
        clip.description.extras = PersistableBundle().apply { putBoolean(key, true) }
        runCatching { manager.setPrimaryClip(clip) }
    }
}

/** A copy action bound to the current context, for use inside composables. */
@Composable
fun rememberSensitiveCopy(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text: String -> SensitiveClipboard.copy(context, text) } }
}
