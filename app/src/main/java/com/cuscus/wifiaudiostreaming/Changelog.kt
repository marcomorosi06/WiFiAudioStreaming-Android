package com.cuscus.wifiaudiostreaming

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
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
    val linkUrl: String? = null
)

private const val DESKTOP_RELEASES_URL = "https://github.com/marcomorosi06/WiFiAudioStreaming-Desktop/releases"

data class ChangelogEntry(
    val version: String,
    val date: Bilingual,
    val headline: Bilingual,
    val items: List<ChangelogItem>
)

object Changelog {

    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "1.1",
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
                    linkUrl = DESKTOP_RELEASES_URL
                )
            )
        )
    )

    val latest: ChangelogEntry get() = entries.first()
}

private val whatsNewTitle = Bilingual("What's new", "Novità")
private val versionHistoryTitle = Bilingual("Version history", "Cronologia versioni")
private val continueLabel = Bilingual("Continue", "Continua")
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
                        text = whatsNewTitle.text(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${shown.version} · ${shown.date.text()}",
                        style = MaterialTheme.typography.labelMedium,
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = shown.headline.text(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                shown.items.forEachIndexed { i, item -> ChangelogItemCard(item, accentForIndex(i)) }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (onContinue != null) {
                val continueHaptics = rememberAppHaptics()
                Button(
                    onClick = { continueHaptics.confirm(); onContinue() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(continueLabel.text())
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
private fun ChangelogItemCard(item: ChangelogItem, accent: ChangelogAccent) {
    val cs = MaterialTheme.colorScheme
    val (container, onContainer, badge, onBadge) = when (accent) {
        ChangelogAccent.PRIMARY -> listOf(cs.primaryContainer, cs.onPrimaryContainer, cs.primary, cs.onPrimary)
        ChangelogAccent.SECONDARY -> listOf(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary, cs.onSecondary)
        ChangelogAccent.TERTIARY -> listOf(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary, cs.onTertiary)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(badge),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = onBadge,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title.text(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer
                )
                Text(
                    text = item.body.text(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.85f)
                )
                if (item.linkUrl != null && item.linkLabel != null) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val linkHaptics = rememberAppHaptics()
                    TextButton(
                        onClick = {
                            linkHaptics.tap()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.linkUrl)))
                            }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(
                            item.linkLabel.text(),
                            color = onContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionHistorySheet(
    onDismiss: () -> Unit,
    onSelect: (ChangelogEntry) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.NewReleases, contentDescription = null) },
        title = { Text(versionHistoryTitle.text()) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(Changelog.entries) { entry ->
                    val entryHaptics = rememberAppHaptics()
                    Surface(
                        onClick = { entryHaptics.tap(); onSelect(entry) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "v${entry.version}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = entry.date.text(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val dismissHaptics = rememberAppHaptics()
            TextButton(onClick = { dismissHaptics.tap(); onDismiss() }) { Text(closeLabel.text()) }
        }
    )
}
