package com.cuscus.wifiaudiostreaming

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Unicast or multicast for a v3 server, as the same segmented choice as the
 * codec, with one plain line under it on what the choice means. [lockedBy]
 * names the protocols that need multicast; while any is on, the choice is
 * fixed and the line says why.
 */
@Composable
fun DeliveryPicker(
    shared: Boolean,
    lockedBy: List<String>,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val locked = lockedBy.isNotEmpty()
    val on = shared || locked
    val and = stringResource(R.string.list_and)
    val hint = when {
        locked -> stringResource(R.string.w3u_delivery_locked, ProtocolStatus.join(lockedBy, and))
        on -> stringResource(R.string.w3u_delivery_all_note)
        else -> stringResource(R.string.w3u_delivery_each_note)
    }
    Panel(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline(stringResource(R.string.w3u_delivery_title), Modifier.padding(start = 4.dp))
            SegmentedChoice(
                options = listOf(
                    Option(false, stringResource(R.string.w3u_delivery_each), Icons.Rounded.Person),
                    Option(true, stringResource(R.string.w3u_delivery_all), Icons.Rounded.Groups)
                ),
                selected = on,
                onChange = onChange,
                enabled = !locked
            )
            AnimatedContent(
                targetState = hint,
                transitionSpec = { fadeIn(Motion.defaultEffects()) togetherWith fadeOut(Motion.fastEffects()) using SizeTransform(clip = false) },
                label = "deliveryHint"
            ) { text ->
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
