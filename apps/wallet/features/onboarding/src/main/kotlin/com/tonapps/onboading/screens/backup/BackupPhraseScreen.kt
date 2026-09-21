package com.tonapps.onboading.screens.backup

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.core.components.ScreenCaptureEffect
import com.tonapps.wallet.localization.Localization
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.container.MoonScaffold
import ui.theme.UIKit
import uikit.navigation.Navigation
import uikit.widget.ToastView

// When `onBack` is null no top bar is drawn: the init flow hosts this screen below the floating
// InitScreen header, which already provides navigation.
@Composable
fun BackupPhraseScreen(
    words: List<String>,
    onCheckBackup: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    ScreenshotWarning()
    if (onBack != null) {
        MoonScaffold(
            modifier = modifier,
            title = "",
            onBack = onBack
        ) {
            BackupPhraseContent(words = words, onCheckBackup = onCheckBackup)
        }
    } else {
        MoonScaffold(modifier = modifier) {
            BackupPhraseContent(words = words, onCheckBackup = onCheckBackup)
        }
    }
}

@Composable
private fun ScreenshotWarning() {
    val navigation = LocalActivity.current?.let(Navigation::from)
    val message = stringResource(Localization.screenshot_warning)
    val color = UIKit.colorScheme.background.contentTint.toArgb()
    ScreenCaptureEffect {
        navigation?.toast(
            message = message,
            loading = false,
            color = color,
            duration = ToastView.DURATION_LONG,
        )
    }
}

@Composable
private fun BackupPhraseContent(
    words: List<String>,
    onCheckBackup: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            MoonTextContentCell(
                title = stringResource(Localization.setup_finish_backup_title),
                description = stringResource(Localization.phrase_description),
                titleStyle = UIKit.typography.h2,
            )

            Spacer(Modifier.height(32.dp))

            PhraseWordsCell(words = words)

            Spacer(Modifier.height(88.dp))
        }

        MoonBottomButtonCell(
            modifier = Modifier.align(Alignment.BottomCenter),
            text = stringResource(Localization.manual_backup_check)
        ) {
            onCheckBackup()
        }
    }
}

@Composable
private fun PhraseWordsCell(words: List<String>) {
    val half = (words.size + 1) / 2

    MoonBundleCell(
        margins = PaddingValues(horizontal = 32.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            PhraseWordsColumn(
                words = words.subList(0, half),
                firstNumber = 1,
                modifier = Modifier.weight(1f),
            )
            PhraseWordsColumn(
                words = words.subList(half, words.size),
                firstNumber = half + 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PhraseWordsColumn(
    words: List<String>,
    firstNumber: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        words.forEachIndexed { index, word ->
            Row {
                Text(
                    modifier = Modifier.width(32.dp),
                    text = "${firstNumber + index}.",
                    style = UIKit.typography.body1,
                    color = UIKit.colorScheme.text.secondary,
                )
                Text(
                    text = word,
                    style = UIKit.typography.body1,
                    color = UIKit.colorScheme.text.primary,
                )
            }
        }
    }
}
