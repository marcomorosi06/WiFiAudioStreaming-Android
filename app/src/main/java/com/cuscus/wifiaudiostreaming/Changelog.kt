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

package com.cuscus.wifiaudiostreaming

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.unit.dp

data class Bilingual(val en: String, val it: String) {
    @Composable
    fun text(): String = if (Locale.current.language == "it") it else en
}

enum class ChangelogAccent { PRIMARY, SECONDARY, TERTIARY }

data class ChangelogItem(
    val icon: ImageVector,
    val title: Bilingual,
    val body: Bilingual,
    val linkLabel: Bilingual? = null,
    val linkUrl: String? = null,
    @StringRes val secondaryLinkLabelRes: Int? = null,
    val secondaryLinkUrl: String? = null
)

private const val DESKTOP_RELEASES_URL = "https://github.com/marcomorosi06/WiFiAudioStreaming-Desktop/releases"
private const val DESKTOP_DOWNLOAD_URL = "https://www.marcomorosi.eu/wifi-audio-streaming/download/"

data class ChangelogEntry(
    val version: String,
    val date: Bilingual,
    val headline: Bilingual,
    val items: List<ChangelogItem>
)

object Changelog {

    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "1.2.1",
            date = Bilingual("September 2026", "Settembre 2026"),
            headline = Bilingual(
                "Your phone can listen too: join Snapcast systems and play RTP streams straight from the Receive screen.",
                "Ora il telefono sa anche ascoltare: entra negli impianti Snapcast e riproduci i flussi RTP direttamente dalla schermata Ricevi."
            ),
            items = listOf(
                ChangelogItem(
                    icon = Icons.Filled.SpeakerGroup,
                    title = Bilingual("Join a Snapcast system", "Entra in un impianto Snapcast"),
                    body = Bilingual(
                        "Connect as a client to any Snapcast server, found on its own on the network or added by address, and play in sync with the other rooms. The same card shows every client in the system, with volume, mute, latency and groups at hand.",
                        "Collegati come client a qualsiasi server Snapcast, trovato da solo in rete o aggiunto per indirizzo, e suona in sincrono con le altre stanze. La stessa scheda mostra tutti i client dell'impianto, con volume, muto, latenza e gruppi a portata di mano."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.Radio,
                    title = Bilingual("Listen to RTP streams", "Ascolta i flussi RTP"),
                    body = Bilingual(
                        "Receive audio over RTP, multicast included, from ffmpeg, VLC, a hardware sender or another WFAS device. Import the .sdp file or paste the descriptor and the fields fill themselves in; on Android the stream must be L16 (raw PCM).",
                        "Ricevi l'audio via RTP, multicast compreso, da ffmpeg, VLC, un trasmettitore hardware o un altro dispositivo WFAS. Importa il file .sdp o incolla il descrittore e i campi si compilano da soli; su Android il flusso deve essere L16 (PCM grezzo)."
                    )
                )
            )
        ),
        ChangelogEntry(
            version = "1.2.0",
            date = Bilingual("August 2026", "Agosto 2026"),
            headline = Bilingual(
                "Two new protocols — DLNA and Snapcast — pairing by QR code, and streaming over USB.",
                "Due nuovi protocolli — DLNA e Snapcast — accoppiamento con QR code e streaming via USB."
            ),
            items = listOf(
                ChangelogItem(
                    icon = Icons.Filled.Cast,
                    title = Bilingual("Play on your DLNA devices", "Riproduci sui tuoi dispositivi DLNA"),
                    body = Bilingual(
                        "Send the audio straight to the Smart TVs, AV receivers and network speakers you already own. They need nothing installed: pick a renderer and it starts playing.",
                        "Manda l'audio direttamente alle Smart TV, ai sintoamplificatori e ai diffusori di rete che possiedi già. Su di loro non serve installare nulla: scegli un renderer e parte."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.SpeakerGroup,
                    title = Bilingual("Snapcast multiroom", "Multiroom con Snapcast"),
                    body = Bilingual(
                        "Every snapclient on the network — Raspberry Pi, ESP32, Home Assistant, the Snapcast apps — joins in and plays in sync, room after room.",
                        "Ogni snapclient sulla rete — Raspberry Pi, ESP32, Home Assistant, le app Snapcast — si unisce e riproduce in sincrono, stanza dopo stanza."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.QrCode2,
                    title = Bilingual("Pair with a QR code", "Accoppiamento con QR code"),
                    body = Bilingual(
                        "Point the camera at the code shown by the desktop app: address, port and key arrive together. Nothing to type, and the connection is encrypted from the first packet.",
                        "Inquadra il codice mostrato dall'app desktop: indirizzo, porta e chiave arrivano insieme. Niente da digitare, e la connessione è cifrata dal primo pacchetto."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.Usb,
                    title = Bilingual("Stream over USB", "Streaming via USB"),
                    body = Bilingual(
                        "Plug the phone in and the audio takes the cable instead of the air: lower latency, no Wi-Fi congestion, and it keeps working where the network does not.",
                        "Collega il telefono e l'audio prende il cavo invece dell'aria: meno latenza, nessuna congestione Wi-Fi e continua a funzionare dove la rete non arriva."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.Router,
                    title = Bilingual("IPv6 and mixed networks", "IPv6 e reti miste"),
                    body = Bilingual(
                        "Discovery and streaming now behave on IPv6 and on networks with several interfaces, picking the one that actually reaches the other device.",
                        "Discovery e streaming ora si comportano bene su IPv6 e su reti con più interfacce, scegliendo quella che raggiunge davvero l'altro dispositivo."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.Sync,
                    title = Bilingual("Older desktops spotted sooner", "Desktop vecchi riconosciuti prima"),
                    body = Bilingual(
                        "When the computer is running an older version, you are told before the connection fails rather than after, and exactly which side to update.",
                        "Quando il computer usa una versione più vecchia, te lo dice prima che la connessione fallisca invece che dopo, e ti indica esattamente quale lato aggiornare."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.Shield,
                    title = Bilingual(
                        "Automation now needs your token",
                        "L'automazione ora richiede il tuo token"
                    ),
                    body = Bilingual(
                        "Commands arriving from outside the app — NFC tags, Tasker, MacroDroid, ADB — are now off until you turn them on, and once on only those carrying your personal token are accepted. If you use automations, open Automation & Scripting: turn the switch on, then copy the URIs and commands generated there — they already include the token.",
                        "I comandi che arrivano da fuori dall'app — tag NFC, Tasker, MacroDroid, ADB — ora sono spenti finché non li attivi, e una volta attivi vengono accettati solo quelli che portano il tuo token personale. Se usi le automazioni, apri Automazione e scripting: accendi l'interruttore, poi ricopia gli URI e i comandi generati lì, che il token lo includono già."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.Computer,
                    title = Bilingual("Update the desktop app too", "Aggiorna anche l'app desktop"),
                    body = Bilingual(
                        "WiFi Audio Streaming for desktop has been updated as well. Update it too so both ends stay compatible.",
                        "Anche WiFi Audio Streaming per desktop è stata aggiornata. Aggiornala anche tu, così i due lati restano compatibili."
                    ),
                    linkLabel = Bilingual("Open on GitHub", "Apri su GitHub"),
                    linkUrl = DESKTOP_RELEASES_URL,
                    secondaryLinkLabelRes = R.string.changelog_download_from_website,
                    secondaryLinkUrl = DESKTOP_DOWNLOAD_URL
                )
            )
        ),
        ChangelogEntry(
            version = "1.1.0",
            date = Bilingual("June 2026", "Giugno 2026"),
            headline = Bilingual(
                "Automation, a clearer microphone, smarter device matching — and connection security with end-to-end encryption.",
                "Automazione, microfono più pulito, riconoscimento dei dispositivi più intelligente — e sicurezza delle connessioni con cifratura end-to-end."
            ),
            items = listOf(
                ChangelogItem(
                    icon = Icons.Filled.Lock,
                    title = Bilingual("Protect your server", "Proteggi il tuo server"),
                    body = Bilingual(
                        "Right where you start the server, choose who can connect: approve every device by hand, or require a shared key. Mutual verification keeps out unknown clients and rogue servers alike.",
                        "Proprio dove avvii il server, scegli chi può connettersi: approva ogni dispositivo a mano, oppure richiedi una chiave condivisa. La verifica reciproca tiene fuori sia i client sconosciuti sia i server malevoli."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.VpnKey,
                    title = Bilingual("Connect with a key", "Connessione con chiave"),
                    body = Bilingual(
                        "When a server is locked, just type its key when prompted — nothing to set up in advance, and you're told right away if the key is wrong.",
                        "Quando un server è protetto, basta digitare la sua chiave quando richiesto — niente da configurare prima, e ti viene detto subito se la chiave è sbagliata."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.Https,
                    title = Bilingual("End-to-end encryption", "Cifratura end-to-end"),
                    body = Bilingual(
                        "Your audio travels encrypted from end to end, so only your paired devices can decode the stream — no one else on the network can listen in.",
                        "Il tuo audio viaggia cifrato da un capo all'altro: solo i tuoi dispositivi accoppiati possono decodificare lo stream — nessun altro sulla rete può ascoltare."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.AutoAwesome,
                    title = Bilingual("Automation & shortcuts", "Automazione e scorciatoie"),
                    body = Bilingual(
                        "Start or stop streaming automatically from NFC tags, Tasker, MacroDroid, home-screen shortcuts or links — and save your own presets with a name.",
                        "Avvia o ferma lo streaming automaticamente da tag NFC, Tasker, MacroDroid, scorciatoie sulla home o link — e salva i tuoi preset con un nome."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.Mic,
                    title = Bilingual("Clearer microphone", "Microfono più pulito"),
                    body = Bilingual(
                        "Echo cancellation, noise suppression and automatic gain make your voice clearer. Muting is now instant, with no lag when you turn it back on.",
                        "Cancellazione dell'eco, riduzione del rumore e guadagno automatico rendono la tua voce più chiara. Il mute ora è immediato, senza ritardo alla riattivazione."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.Sync,
                    title = Bilingual("Smarter compatibility", "Compatibilità più intelligente"),
                    body = Bilingual(
                        "The app checks that your phone and desktop speak the same version, and tells you exactly which one to update if they don't match.",
                        "L'app verifica che telefono e desktop parlino la stessa versione e ti dice esattamente quale aggiornare se non combaciano."
                    )
                ),
                ChangelogItem(
                    icon = Icons.Filled.Computer,
                    title = Bilingual("Update the desktop app too", "Aggiorna anche l'app desktop"),
                    body = Bilingual(
                        "WiFi Audio Streaming for desktop has been updated as well. Update it too so both ends stay compatible.",
                        "Anche WiFi Audio Streaming per desktop è stata aggiornata. Aggiornala anche tu, così i due lati restano compatibili."
                    ),
                    linkLabel = Bilingual("Open on GitHub", "Apri su GitHub"),
                    linkUrl = DESKTOP_RELEASES_URL,
                    secondaryLinkLabelRes = R.string.changelog_download_from_website,
                    secondaryLinkUrl = DESKTOP_DOWNLOAD_URL
                )
            )
        )
    )

    val latest: ChangelogEntry get() = entries.first()
}

private val whatsNewTitle = Bilingual("What's new", "Novità")
private val versionHistoryTitle = Bilingual("Version history", "Cronologia versioni")
private val continueLabel = Bilingual("Continue", "Continua")
private val currentLabel = Bilingual("current", "attuale")
private val closeLabel = Bilingual("Close", "Chiudi")

@Composable
fun WhatsNewPage(
    entry: ChangelogEntry = Changelog.latest,
    onContinue: (() -> Unit)? = null
) {
    var shown by remember { mutableStateOf(entry) }
    var showHistory by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = whatsNewTitle.text().uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v${shown.version}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = shown.date.text(),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val historyHaptics = rememberAppHaptics()
                IconButton(onClick = { historyHaptics.tap(); showHistory = true }) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = versionHistoryTitle.text()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = shown.headline.text(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Spacer(modifier = Modifier.height(12.dp))
                shown.items.forEachIndexed { i, item ->
                    ChangelogItemCard(item, accentForIndex(i), isLast = i == shown.items.lastIndex)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (onContinue != null) {
                val continueHaptics = rememberAppHaptics()
                Button(
                    onClick = { continueHaptics.confirm(); onContinue() },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .height(58.dp)
                ) {
                    Text(
                        text = continueLabel.text(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showHistory) {
            VersionHistorySheet(
                onDismiss = { showHistory = false },
                onSelect = {
                    shown = it
                    showHistory = false
                }
            )
        }
    }
}

@Composable
fun WhatsNewStandaloneScreen(onContinue: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Box(modifier = Modifier.weight(1f)) {
                WhatsNewPage(entry = Changelog.latest, onContinue = onContinue)
            }
        }
    }
}

private fun accentForIndex(i: Int): ChangelogAccent = when (i % 3) {
    0 -> ChangelogAccent.PRIMARY
    1 -> ChangelogAccent.SECONDARY
    else -> ChangelogAccent.TERTIARY
}

@Composable
private fun ChangelogItemCard(
    item: ChangelogItem,
    accent: ChangelogAccent,
    isLast: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val badge = when (accent) {
        ChangelogAccent.PRIMARY -> cs.primary
        ChangelogAccent.SECONDARY -> cs.secondary
        ChangelogAccent.TERTIARY -> cs.tertiary
    }

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {

        // binario della timeline
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(56.dp)
        ) {
            ExpressiveHeroBadge(size = 48.dp, accent = badge, containerAlpha = 0.20f) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = badge,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(badge.copy(alpha = 0.22f))
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .padding(top = 6.dp, bottom = if (isLast) 0.dp else 26.dp)
        ) {
            Text(
                text = item.title.text(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.3).sp,
                color = cs.onSurface
            )
            Text(
                text = item.body.text(),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            val primaryLink = item.linkUrl?.let { url -> item.linkLabel?.let { url to it.text() } }
            val secondaryLink = item.secondaryLinkUrl?.let { url ->
                item.secondaryLinkLabelRes?.let { url to stringResource(it) }
            }
            if (primaryLink != null || secondaryLink != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    secondaryLink?.let { (url, label) -> ChangelogLinkChip(label, url, badge, filled = true) }
                    primaryLink?.let { (url, label) -> ChangelogLinkChip(label, url, badge, filled = false) }
                }
            }
        }
    }
}

@Composable
private fun ChangelogLinkChip(
    label: String,
    url: String,
    badge: Color,
    filled: Boolean
) {
    val context = LocalContext.current
    val linkHaptics = rememberAppHaptics()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (filled) Modifier.background(badge.copy(alpha = 0.16f))
                else Modifier.border(1.dp, badge.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            )
            .clickable {
                linkHaptics.tap()
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = badge,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VersionHistorySheet(
    onDismiss: () -> Unit,
    onSelect: (ChangelogEntry) -> Unit
) {
    val haptics = rememberAppHaptics()
    val cs = MaterialTheme.colorScheme
    val current = Changelog.latest.version

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = cs.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Text(
                    text = versionHistoryTitle.text().uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = cs.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${Changelog.entries.size}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp,
                    color = cs.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    itemsIndexed(Changelog.entries) { index, entry ->
                        val isCurrent = entry.version == current
                        val accent = when (index % 3) {
                            0 -> cs.primary
                            1 -> cs.secondary
                            else -> cs.tertiary
                        }
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val corner by animateDpAsState(
                            targetValue = if (pressed) 12.dp else 22.dp,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "historyCorner"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (pressed) 0.97f else 1f,
                            animationSpec = tween(150, easing = FastOutSlowInEasing),
                            label = "historyScale"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(40.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isCurrent) 16.dp else 10.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrent) accent else accent.copy(alpha = 0.35f))
                                )
                                if (index != Changelog.entries.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .weight(1f)
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(accent.copy(alpha = 0.20f))
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 8.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(RoundedCornerShape(corner))
                                    .background(
                                        if (isCurrent) accent.copy(alpha = 0.16f)
                                        else cs.surfaceContainerHighest
                                    )
                                    .clickable(interactionSource = interaction, indication = null) {
                                        haptics.confirm()
                                        onSelect(entry)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "v${entry.version}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        color = if (isCurrent) accent else cs.onSurface
                                    )
                                    Text(
                                        text = entry.date.text(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                if (isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(accent)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = currentLabel.text().uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            color = cs.surfaceContainerLowest
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(cs.surfaceContainerHighest)
                        .clickable { haptics.tap(); onDismiss() },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = closeLabel.text(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}
