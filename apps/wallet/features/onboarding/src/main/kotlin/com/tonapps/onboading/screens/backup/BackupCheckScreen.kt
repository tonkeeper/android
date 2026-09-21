package com.tonapps.onboading.screens.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.animation.rememberShakeOffset
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.container.MoonScaffold
import ui.preview.ThemedPreview
import ui.theme.UIKit

private const val CHIPS_PER_ROW = 3
private val chipSpacing = 6.dp

// When `onBack` is null no top bar is drawn: the init flow hosts this screen below the floating
// InitScreen header, which already provides navigation.
@Composable
fun BackupCheckScreen(
    data: BackupCheckData,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onError: () -> Unit = {},
) {
    val feature = koinViewModel<BackupCheckFeature> { parametersOf(data) }

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                is BackupCheckEvent.Done -> onDone()
                is BackupCheckEvent.Error -> onError()
            }
        }
    }

    if (onBack != null) {
        MoonScaffold(
            modifier = modifier,
            title = "",
            onBack = onBack
        ) {
            BackupCheckFeatureContent(feature = feature)
        }
    } else {
        MoonScaffold(modifier = modifier) {
            BackupCheckFeatureContent(feature = feature)
        }
    }
}

@Composable
private fun BackupCheckFeatureContent(
    feature: BackupCheckFeature,
) {
    BackupCheckContent(
        questions = feature.questions,
        indexes = feature.indexes,
        selections = feature.selections,
        hasError = feature.hasError,
        continueEnabled = feature.continueEnabled,
        onSelect = { index, option -> feature.select(index, option) },
        onDone = { feature.onDone() },
    )
}

@Composable
private fun BackupCheckContent(
    questions: List<BackupCheckQuestion>,
    indexes: Array<Int>,
    selections: Map<Int, Int>,
    hasError: Boolean,
    continueEnabled: Boolean,
    onSelect: (index: Int, option: Int) -> Unit,
    onDone: () -> Unit,
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
                title = stringResource(Localization.backup_check),
                description = if (hasError) {
                    stringResource(Localization.backup_check_error_subtitle)
                } else {
                    stringResource(Localization.backup_check_choose_subtitle, *indexes)
                },
                descriptionColor = if (hasError) {
                    UIKit.colorScheme.accent.red
                } else {
                    Color.Unspecified
                },
            )

            Spacer(Modifier.height(24.dp))

            questions.forEachIndexed { index, question ->
                WordQuestionCell(
                    question = question,
                    selectedOption = selections[index],
                    isError = hasError,
                    onSelect = { option -> onSelect(index, option) },
                )

                Spacer(Modifier.height(24.dp))
            }

            Spacer(Modifier.height(88.dp))
        }

        MoonBottomButtonCell(
            modifier = Modifier.align(Alignment.BottomCenter),
            text = stringResource(Localization.done),
            enabled = continueEnabled,
        ) {
            onDone()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordQuestionCell(
    question: BackupCheckQuestion,
    selectedOption: Int?,
    isError: Boolean,
    onSelect: (Int) -> Unit,
) {
    val shake = rememberShakeOffset(active = isError)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value }
            .padding(horizontal = 32.dp)
    ) {
        Text(
            modifier = Modifier.padding(vertical = 16.dp),
            text = "${question.wordNumber}.",
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.secondary,
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val minChipWidth = with(LocalDensity.current) {
                val gapsPx = (CHIPS_PER_ROW - 1) * chipSpacing.roundToPx()
                ((constraints.maxWidth - gapsPx) / CHIPS_PER_ROW).toDp()
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(chipSpacing),
                verticalArrangement = Arrangement.spacedBy(chipSpacing),
            ) {
                question.options.forEachIndexed { option, word ->
                    WordOptionChip(
                        text = word,
                        selected = option == selectedOption,
                        isError = isError && option == selectedOption,
                        onClick = { onSelect(option) },
                        modifier = Modifier.widthIn(min = minChipWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun WordOptionChip(
    text: String,
    selected: Boolean,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        isError -> UIKit.colorScheme.field.errorBorder
        selected -> UIKit.colorScheme.field.activeBorder
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(UIKit.colorScheme.field.background)
            .border(width = 1.5.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 11.dp, end = 8.dp, bottom = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private val previewQuestions = listOf(
    BackupCheckQuestion(wordNumber = 2, options = listOf("afraid", "immense", "afraidd"), correctWord = "immense"),
    BackupCheckQuestion(wordNumber = 8, options = listOf("afraiddd", "afraid", "afraid"), correctWord = "drama"),
    BackupCheckQuestion(wordNumber = 9, options = listOf("either", "east", "one"), correctWord = "east"),
)

private val previewIndexes = previewQuestions.map { it.wordNumber }
    .toTypedArray()

private val previewSelections = mapOf(0 to 1, 1 to 0, 2 to 1)

@Composable
private fun BackupCheckPreviewContent(hasError: Boolean) {
    BackupCheckContent(
        questions = previewQuestions,
        indexes = previewIndexes,
        selections = previewSelections,
        hasError = hasError,
        continueEnabled = !hasError,
        onSelect = { _, _ -> },
        onDone = {},
    )
}

@Preview
@Composable
private fun BackupCheckPreview() {
    ThemedPreview(isDarkOnly = true) {
        BackupCheckPreviewContent(hasError = false)
    }
}

@Preview
@Composable
private fun BackupCheckErrorPreview() {
    ThemedPreview(isDarkOnly = true) {
        BackupCheckPreviewContent(hasError = true)
    }
}
