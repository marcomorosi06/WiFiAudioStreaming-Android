package com.cuscus.wifiaudiostreaming

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale

object DownloadLinks {
    const val GITHUB_RELEASES = "https://github.com/marcomorosi06/WiFiAudioStreaming-Android/releases"
    private const val SITE_EN = "https://www.marcomorosi.eu/wifi-audio-streaming/download/"
    private const val SITE_IT = "https://www.marcomorosi.eu/it/wifi-audio-streaming/download/"

    fun site(languageTag: String): String =
        if (languageTag.startsWith("it", ignoreCase = true)) SITE_IT else SITE_EN
}

private val updateAvailableTitle = Bilingual("Update available", "Aggiornamento disponibile")
private val checkUpdatesTitle = Bilingual("Check for updates", "Controllo aggiornamenti")
private val upToDateTitle = Bilingual("You're up to date", "Sei aggiornato")
private val failedTitle = Bilingual("GitHub is not responding", "GitHub non risponde")
private val aheadTitle = Bilingual(
    "I see what you did there in build.gradle.",
    "Qualcuno qua ha smanettato nel build.gradle, eh?"
)
private val aheadBody = Bilingual(
    "Look at you, hacking your own local file. Proud of yourself? ;)",
    "Congratulazioni! Hai ottenuto: assolutamente nulla. Però ehi, hai trovato questo messaggio. ;)"
)
private val siteLabel = Bilingual("Download from website", "Scarica dal sito")
private val githubLabel = Bilingual("Download from GitHub", "Scarica da GitHub")
private val laterLabel = Bilingual("Later", "Più tardi")
private val closeUpdateLabel = Bilingual("Close", "Chiudi")
private val upToDateBody = Bilingual("You are on the latest version", "Sei all'ultima versione")
private val availableShort = Bilingual(
    "A newer version is ready to download.",
    "È disponibile una versione più recente."
)
private val failedBody = Bilingual(
    "Could not reach GitHub to check for updates. Please try again later.",
    "Impossibile raggiungere GitHub per controllare gli aggiornamenti. Riprova più tardi."
)

@Composable
fun UpdateAvailableDialog(
    current: String,
    latest: String,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val site = DownloadLinks.site(Locale.current.language)
    ExpressiveVersionDialog(
        icon = Icons.Outlined.Update,
        accent = MaterialTheme.colorScheme.primary,
        title = updateAvailableTitle.text(),
        body = availableShort.text(),
        fromVersion = current,
        toVersion = latest,
        confirmLabel = siteLabel.text(),
        confirmIcon = Icons.Outlined.Language,
        onConfirm = { onOpenUrl(site) },
        secondaryLabel = githubLabel.text(),
        secondaryIcon = Icons.Outlined.Code,
        onSecondary = { onOpenUrl(DownloadLinks.GITHUB_RELEASES) },
        dismissLabel = laterLabel.text(),
        onDismiss = onDismiss
    )
}

@Composable
fun UpdateResultDialog(
    result: UpdateChecker.Result,
    onUpdate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val site = DownloadLinks.site(Locale.current.language)
    when (result) {
        is UpdateChecker.Result.Available -> ExpressiveVersionDialog(
            icon = Icons.Outlined.Update,
            accent = MaterialTheme.colorScheme.primary,
            title = updateAvailableTitle.text(),
            body = availableShort.text(),
            fromVersion = result.current,
            toVersion = result.latest,
            confirmLabel = siteLabel.text(),
            confirmIcon = Icons.Outlined.Language,
            onConfirm = { onUpdate(site) },
            secondaryLabel = githubLabel.text(),
            secondaryIcon = Icons.Outlined.Code,
            onSecondary = { onUpdate(DownloadLinks.GITHUB_RELEASES) },
            dismissLabel = closeUpdateLabel.text(),
            onDismiss = onDismiss
        )

        is UpdateChecker.Result.UpToDate -> ExpressiveVersionDialog(
            icon = Icons.Outlined.CheckCircle,
            accent = MaterialTheme.colorScheme.tertiary,
            title = upToDateTitle.text(),
            body = "${upToDateBody.text()} (${result.current}).",
            fromVersion = null,
            toVersion = null,
            confirmLabel = null,
            dismissLabel = closeUpdateLabel.text(),
            onConfirm = null,
            onDismiss = onDismiss
        )

        is UpdateChecker.Result.Ahead -> VersionAheadDialog(
            current = result.current,
            latest = result.latest,
            onDismiss = onDismiss
        )

        is UpdateChecker.Result.Failed -> ExpressiveVersionDialog(
            icon = Icons.Outlined.CloudOff,
            accent = MaterialTheme.colorScheme.error,
            title = failedTitle.text(),
            body = failedBody.text(),
            fromVersion = null,
            toVersion = null,
            confirmLabel = null,
            dismissLabel = closeUpdateLabel.text(),
            onConfirm = null,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun VersionAheadDialog(
    current: String,
    latest: String,
    onDismiss: () -> Unit
) {
    ExpressiveVersionDialog(
        icon = Icons.Outlined.EmojiEvents,
        accent = MaterialTheme.colorScheme.tertiary,
        title = aheadTitle.text(),
        body = aheadBody.text(),
        fromVersion = latest,
        toVersion = current,
        confirmLabel = null,
        dismissLabel = closeUpdateLabel.text(),
        onConfirm = null,
        onDismiss = onDismiss
    )
}
