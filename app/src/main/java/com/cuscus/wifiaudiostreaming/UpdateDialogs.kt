package com.cuscus.wifiaudiostreaming

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

private val updateAvailableTitle = Bilingual("Update available", "Aggiornamento disponibile")
private val checkUpdatesTitle = Bilingual("Check for updates", "Controllo aggiornamenti")
private val downloadLabel = Bilingual("Download", "Scarica")
private val laterLabel = Bilingual("Later", "Più tardi")
private val closeUpdateLabel = Bilingual("Close", "Chiudi")
private val upToDateBody = Bilingual("You are on the latest version", "Sei all'ultima versione")
private val failedBody = Bilingual(
    "Could not check for updates. Please try again later.",
    "Impossibile controllare gli aggiornamenti. Riprova più tardi."
)

@Composable
private fun availableBody(latest: String, current: String): String =
    Bilingual(
        "Version $latest is available. You have $current.",
        "La versione $latest è disponibile. Tu hai la $current."
    ).text()

@Composable
fun UpdateAvailableDialog(
    current: String,
    latest: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Update, contentDescription = null) },
        title = { Text(updateAvailableTitle.text()) },
        text = { Text(availableBody(latest, current)) },
        confirmButton = {
            Button(onClick = onUpdate) { Text(downloadLabel.text()) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(laterLabel.text()) }
        }
    )
}

@Composable
fun UpdateResultDialog(
    result: UpdateChecker.Result,
    onUpdate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val message = when (result) {
        is UpdateChecker.Result.Available -> availableBody(result.latest, result.current)
        is UpdateChecker.Result.UpToDate -> "${upToDateBody.text()} (${result.current})."
        is UpdateChecker.Result.Failed -> failedBody.text()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Update, contentDescription = null) },
        title = { Text(checkUpdatesTitle.text()) },
        text = { Text(message) },
        confirmButton = {
            if (result is UpdateChecker.Result.Available) {
                Button(onClick = { onUpdate(result.url) }) { Text(downloadLabel.text()) }
            } else {
                TextButton(onClick = onDismiss) { Text(closeUpdateLabel.text()) }
            }
        },
        dismissButton = {
            if (result is UpdateChecker.Result.Available) {
                TextButton(onClick = onDismiss) { Text(closeUpdateLabel.text()) }
            }
        }
    )
}
