package ui.components.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ui.theme.Dimens
import ui.theme.UIKit
import ui.theme.resources.Res
import ui.theme.resources.ic_verification_16

@Composable
fun TKDetailsInfo(
    modifier: Modifier = Modifier,
    aboveTitle: CharSequence?,
    title: CharSequence?,
    subtitle: CharSequence?,
    verifiedSubtitle: Boolean,
    date: String,
    failedText: String?
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.offsetMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        aboveTitle?.let {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = it.toString(),
                textAlign = TextAlign.Center,
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.tertiary
            )
        }

        title?.let {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = it.toString(),
                textAlign = TextAlign.Center,
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.primary
            )
        }

        if (subtitle != null && verifiedSubtitle) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = subtitle.toString(),
                    style = UIKit.typography.body1,
                    color = UIKit.colorScheme.text.secondary
                )
                Icon(
                    painter = painterResource(Res.drawable.ic_verification_16),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = UIKit.colorScheme.text.secondary
                )
            }
        } else {
            subtitle?.let {
                Text(
                    text = it.toString(),
                    style = UIKit.typography.body1,
                    color = UIKit.colorScheme.text.secondary
                )
            }
        }

        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = date,
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.secondary
        )

        if (failedText != null) {
            Text(
                text = failedText,
                style = UIKit.typography.body1,
                color = UIKit.colorScheme.accent.orange
            )
        }
    }
}