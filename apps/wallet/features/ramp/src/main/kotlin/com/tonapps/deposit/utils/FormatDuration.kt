package com.tonapps.deposit.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tonapps.wallet.localization.Localization

@Composable
fun formatDuration(seconds: Int): String {
    return if (seconds < 60) {
        stringResource(Localization.up_to_duration, seconds.toString(), stringResource(Localization.time_seconds))
    } else {
        val minutes = seconds / 60
        stringResource(Localization.up_to_duration, minutes.toString(), stringResource(Localization.time_minutes))
    }
}
