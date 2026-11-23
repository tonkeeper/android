package com.tonapps.perps.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonColorsTertiary
import ui.components.moon.ButtonSizeMedium
import ui.preview.ThemedPreview
import ui.theme.UIKit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerpsSkipIntroDialog(
    keeperTermsUrl: String?,
    onOpenLink: (String) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        PerpsSkipIntroDialogBody(
            keeperTermsUrl = keeperTermsUrl,
            onOpenLink = onOpenLink,
            onSkip = onSkip,
            onContinue = onContinue,
        )
    }
}

@Composable
private fun PerpsSkipIntroDialogBody(
    keeperTermsUrl: String?,
    onOpenLink: (String) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    val continueText = stringResource(Localization.continue_action)
    val lighterTerms = stringResource(Localization.perps_risk_disclosure_lighter_terms)
    val keeperTerms = stringResource(Localization.perps_risk_disclosure_keeper_terms)
    val text = stringResource(Localization.perps_skip_intro_text, continueText, lighterTerms, keeperTerms)
    val linkColor = UIKit.colorScheme.text.accent
    val message = remember(text, lighterTerms, keeperTerms, keeperTermsUrl, linkColor, onOpenLink) {
        perpsSkipIntroMessage(
            text = text,
            links = listOfNotNull(
                lighterTerms to PERPS_LIGHTER_TERMS_URL,
                keeperTermsUrl?.let { keeperTerms to it },
            ),
            linkStyle = SpanStyle(color = linkColor),
            onOpenLink = onOpenLink,
        )
    }

    Surface(
        modifier = Modifier.width(300.dp),
        shape = RoundedCornerShape(34.dp),
        color = UIKit.colorScheme.background.content,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 24.dp)) {
                Text(
                    text = stringResource(Localization.perps_skip_intro_title),
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.primary,
                )
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 10.dp),
                    style = UIKit.typography.body1,
                    color = UIKit.colorScheme.text.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PerpsSkipIntroButton(
                    text = stringResource(Localization.setup_finish_skip),
                    colors = ButtonColorsTertiary,
                    onClick = onSkip,
                )
                PerpsSkipIntroButton(
                    text = continueText,
                    colors = ButtonColorsPrimary,
                    onClick = onContinue,
                )
            }
        }
    }
}

@Composable
private fun RowScope.PerpsSkipIntroButton(
    text: String,
    colors: ButtonColors,
    onClick: () -> Unit,
) {
    val size = ButtonSizeMedium
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(size.height),
        shape = size.shape,
        colors = colors,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = text,
            style = size.textStyle,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp, stepSize = 1.sp),
        )
    }
}

private fun perpsSkipIntroMessage(
    text: String,
    links: List<Pair<String, String>>,
    linkStyle: SpanStyle,
    onOpenLink: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    append(text)
    links.forEach { (part, url) ->
        val start = text.indexOf(part)
        if (start >= 0) {
            addLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(linkStyle),
                    linkInteractionListener = { onOpenLink(url) },
                ),
                start,
                start + part.length,
            )
        }
    }
}

@Preview
@Composable
private fun PerpsSkipIntroDialogPreview() {
    ThemedPreview {
        PerpsSkipIntroDialogBody(
            keeperTermsUrl = "https://tonkeeper.com/terms",
            onOpenLink = {},
            onSkip = {},
            onContinue = {},
        )
    }
}
