package com.cuscus.wifiaudiostreaming

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

val LocalHapticsEnabled: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

@Immutable
class AppHaptics(
    private val view: View,
    private val enabled: Boolean
) {
    private fun fire(vararg constants: Int) {
        if (!enabled) return
        for (constant in constants) {
            if (view.performHapticFeedback(constant)) return
        }
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    private fun sdk(min: Int, constant: Int): IntArray =
        if (Build.VERSION.SDK_INT >= min) intArrayOf(constant) else IntArray(0)

    private fun chain(vararg groups: IntArray): IntArray =
        groups.fold(IntArray(0)) { acc, g -> acc + g }

    fun tap() = fire(
        *chain(
            intArrayOf(HapticFeedbackConstants.KEYBOARD_TAP),
            intArrayOf(HapticFeedbackConstants.VIRTUAL_KEY)
        )
    )

    fun tick() = fire(
        *chain(
            sdk(34, HapticFeedbackConstants.SEGMENT_TICK),
            intArrayOf(HapticFeedbackConstants.CLOCK_TICK),
            intArrayOf(HapticFeedbackConstants.KEYBOARD_TAP),
            intArrayOf(HapticFeedbackConstants.VIRTUAL_KEY)
        )
    )

    fun toggle(on: Boolean) = fire(
        *chain(
            sdk(34, if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF),
            sdk(30, if (on) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK),
            intArrayOf(HapticFeedbackConstants.KEYBOARD_TAP),
            intArrayOf(HapticFeedbackConstants.VIRTUAL_KEY)
        )
    )

    fun confirm() = fire(
        *chain(
            sdk(30, HapticFeedbackConstants.CONFIRM),
            intArrayOf(HapticFeedbackConstants.LONG_PRESS),
            intArrayOf(HapticFeedbackConstants.VIRTUAL_KEY)
        )
    )

    fun reject() = fire(
        *chain(
            sdk(30, HapticFeedbackConstants.REJECT),
            intArrayOf(HapticFeedbackConstants.LONG_PRESS),
            intArrayOf(HapticFeedbackConstants.VIRTUAL_KEY)
        )
    )

    fun press() = fire(
        HapticFeedbackConstants.VIRTUAL_KEY,
        HapticFeedbackConstants.KEYBOARD_TAP
    )

    fun longPress() = fire(
        HapticFeedbackConstants.LONG_PRESS,
        HapticFeedbackConstants.VIRTUAL_KEY
    )

    fun gestureStart() = fire(
        *chain(
            sdk(30, HapticFeedbackConstants.GESTURE_START),
            intArrayOf(HapticFeedbackConstants.CLOCK_TICK),
            intArrayOf(HapticFeedbackConstants.VIRTUAL_KEY)
        )
    )

    fun gestureEnd() = fire(
        *chain(
            sdk(30, HapticFeedbackConstants.GESTURE_END),
            intArrayOf(HapticFeedbackConstants.CLOCK_TICK),
            intArrayOf(HapticFeedbackConstants.VIRTUAL_KEY)
        )
    )
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { AppHaptics(view, enabled) }
}
