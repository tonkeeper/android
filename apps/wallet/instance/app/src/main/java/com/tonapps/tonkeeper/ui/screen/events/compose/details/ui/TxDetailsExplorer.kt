package com.tonapps.tonkeeper.ui.screen.events.compose.details.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.tonkeeper.ui.screen.events.compose.details.TxDetailsViewModel
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun TxDetailsExplorer(
    viewModel: TxDetailsViewModel,
    hash: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = { viewModel.openTx() },
                onLongClick = { viewModel.copyTxHash() }
            )
            .height(36.dp)
            .background(UIKit.colorScheme.buttonSecondary.primaryBackground)
            .padding(horizontal = Dimens.offsetMedium),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(UIKitIcon.ic_globe_16),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = UIKit.colorScheme.buttonSecondary.primaryForeground
        )

        Text(
            text = stringResource(Localization.transaction),
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.buttonSecondary.primaryForeground
        )

        Text(
            text = hash,
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.text.tertiary
        )
    }
}