package ui.components.moon.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonDivider
import ui.theme.Shapes
import ui.theme.UIKit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonAlertDialog(
    title: String? = null,
    message: String,
    positiveButtonText: String,
    onDismiss: () -> Unit,
    positiveButtonColor: Color = Color.Unspecified,
    onPositiveClick: (() -> Unit)? = null,
    negativeButtonText: String? = null,
    negativeButtonColor: Color = Color.Unspecified,
    onNegativeClick: (() -> Unit)? = null,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = Shapes.large,
            color = UIKit.colorScheme.background.page,
            shadowElevation = 8.dp,
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!title.isNullOrEmpty()) {
                        Text(
                            text = title,
                            style = UIKit.typography.h3,
                            color = UIKit.colorScheme.text.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        text = message,
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.primary,
                        textAlign = TextAlign.Center,
                        modifier = if (!title.isNullOrEmpty()) {
                            Modifier.padding(top = 8.dp)
                        } else {
                            Modifier
                        },
                    )
                }

                MoonDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    if (negativeButtonText != null) {
                        AlertButton(
                            text = negativeButtonText,
                            color = negativeButtonColor,
                            onClick = {
                                onNegativeClick?.invoke()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    AlertButton(
                        text = positiveButtonText,
                        color = positiveButtonColor,
                        onClick = {
                            onPositiveClick?.invoke()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = UIKit.typography.label1,
            color = if (color != Color.Unspecified) color else UIKit.colorScheme.text.primary,
            textAlign = TextAlign.Center,
        )
    }
}
