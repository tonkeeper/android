package com.tonapps.perps.screens.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.perps.screens.common.PERPS_LIGHTER_TERMS_URL
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun PerpsRiskDisclosureDialog(
    keeperTermsUrl: String,
    onOpenLink: (String) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsRiskDisclosureDialogBody(
            keeperTermsUrl = keeperTermsUrl,
            onOpenLink = onOpenLink,
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun PerpsRiskDisclosureDialogBody(
    keeperTermsUrl: String,
    onOpenLink: (String) -> Unit,
    onClose: () -> Unit,
) {
    val lighterTerms = stringResource(Localization.perps_risk_disclosure_lighter_terms)
    val keeperTerms = stringResource(Localization.perps_risk_disclosure_keeper_terms)
    val linkColor = UIKit.colorScheme.text.accent
    val links = remember(lighterTerms, keeperTerms, keeperTermsUrl, linkColor, onOpenLink) {
        perpsRiskDisclosureLinks(
            lighterTerms = lighterTerms,
            keeperTerms = keeperTerms,
            keeperTermsUrl = keeperTermsUrl,
            linkStyle = SpanStyle(color = linkColor),
            onOpenLink = onOpenLink,
        )
    }

    MoonTopAppBarSimple(
        title = stringResource(Localization.perps_risk_disclosure_title),
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
        actionModifier = Modifier.semantics {
            testTagsAsResourceId = true
            testTag = "bottom_sheet_close"
        },
    )

    MoonSurface {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
        ) {
            MoonBundleCell {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Localization.perps_risk_disclosure_text),
                        style = UIKit.typography.body1,
                        color = UIKit.colorScheme.text.primary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = links,
                        style = UIKit.typography.body1,
                        color = UIKit.colorScheme.text.primary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun perpsRiskDisclosureLinks(
    lighterTerms: String,
    keeperTerms: String,
    keeperTermsUrl: String,
    linkStyle: SpanStyle,
    onOpenLink: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    withLink(
        LinkAnnotation.Url(
            url = PERPS_LIGHTER_TERMS_URL,
            styles = TextLinkStyles(linkStyle),
            linkInteractionListener = { onOpenLink(PERPS_LIGHTER_TERMS_URL) },
        )
    ) { append(lighterTerms) }
    append(LINKS_SEPARATOR)
    withLink(
        LinkAnnotation.Url(
            url = keeperTermsUrl,
            styles = TextLinkStyles(linkStyle),
            linkInteractionListener = { onOpenLink(keeperTermsUrl) },
        )
    ) { append(keeperTerms) }
}

@Preview(showSystemUi = true)
@Composable
private fun PerpsRiskDisclosureDialogPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsRiskDisclosureDialogBody(
            keeperTermsUrl = "https://tonkeeper.com/terms",
            onOpenLink = {},
            onClose = {},
        )
    }
}

private const val LINKS_SEPARATOR = " · "
