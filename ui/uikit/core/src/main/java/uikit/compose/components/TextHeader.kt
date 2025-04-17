package uikit.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import uikit.compose.Dimens
import uikit.compose.UIKit

@Composable
fun TextHeader(
    title: String,
    description: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = UIKit.typography.h2,
            color = UIKit.colors.textPrimary,
            textAlign = TextAlign.Center
        )

        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Dimens.offsetExtraSmall))
            Text(
                text = description,
                style = UIKit.typography.body1,
                color = UIKit.colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}


