package com.tonapps.deposit.multicoin.screens.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.tonapps.deposit.common.QrSheetContent
import ui.theme.UIKit

@Composable
fun ReceiveQrScreen(
    feature: ReceiveQrFeature,
    onFallbackToList: () -> Unit,
    onClose: () -> Unit,
) {
    val state by feature.state.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(UIKit.colorScheme.background.page),
    ) {
        when (val currentState = state) {
            ReceiveQrFeature.State.Loading -> Unit

            is ReceiveQrFeature.State.Qr -> QrSheetContent(
                account = currentState.account,
                onClose = onClose,
            )

            ReceiveQrFeature.State.AccountNotFound -> LaunchedEffect(Unit) {
                onFallbackToList()
            }
        }
    }
}
