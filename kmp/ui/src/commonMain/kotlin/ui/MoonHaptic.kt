package ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

// App-wide haptic vocabulary on top of Compose's HapticFeedback, mirroring the classic-View
// helpers in uikit.extensions (hapticTick / hapticConfirm / hapticReject / hapticToggle).

fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.SegmentTick)

fun HapticFeedback.confirm() = performHapticFeedback(HapticFeedbackType.Confirm)

fun HapticFeedback.reject() = performHapticFeedback(HapticFeedbackType.Reject)

// ToggleOn/Off is silent on many devices, so use a plain tick that is always felt.
fun HapticFeedback.toggle() = performHapticFeedback(HapticFeedbackType.SegmentTick)
