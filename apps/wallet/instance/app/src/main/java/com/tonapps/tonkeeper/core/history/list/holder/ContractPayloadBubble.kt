package com.tonapps.tonkeeper.core.history.list.holder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonExpandableText
import ui.preview.ThemedPreview
import ui.theme.UIKit

private const val CONTRACT_PAYLOAD_MAX_LINES = 10
private val ContractPayloadShape = RoundedCornerShape(18.dp)

@Composable
internal fun ContractPayloadBubble(payload: String) {
    key(payload) {
        MoonExpandableText(
            modifier = Modifier
                .clip(ContractPayloadShape)
                .background(UIKit.colorScheme.background.contentTint)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            backgroundColor = UIKit.colorScheme.background.contentTint,
            text = payload,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
            showMoreText = stringResource(Localization.more),
            maxLines = CONTRACT_PAYLOAD_MAX_LINES,
        )
    }
}

@Preview
@Composable
private fun ContractPayloadBubblePreview() {
    ThemedPreview {
        ContractPayloadBubble(payload = "QueryId: 0")
        ContractPayloadBubble(
            payload = (1..14).joinToString("\n") { "Signer$it: 0:5ab8bd6c4e01f8323ebf5795db6e03505b13756cda6acf8c40850fcb6b1afe4a" }
        )
    }
}
