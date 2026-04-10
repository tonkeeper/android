package com.tonapps.tonkeeper.ui.screen.events.compose.details.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import com.tonapps.tonkeeper.ui.screen.events.compose.details.TxDetailsViewModel
import com.tonapps.tonkeeper.ui.screen.events.compose.details.state.UiState
import ui.animation.ContentCrossfade
import ui.components.details.TKDetails
import ui.components.details.TKDetailsInfo
import ui.components.modal.TKModalScaffold
import ui.components.moon.MoonAsyncImage
import ui.theme.Dimens
import ui.theme.Shapes

@Composable
fun TxDetailsComposable(
    viewModel: TxDetailsViewModel,
    onCloseClick: () -> Unit
) {
    val uiState by viewModel.uiStateFlow.collectAsState()
    val spam by remember { derivedStateOf { uiState.spam } }
    val interop = rememberNestedScrollInteropConnection()

    TKModalScaffold(
        horizontalAlignment = Alignment.CenterHorizontally,
        onCloseClick = onCloseClick,
        headerLeftButton = { TxActionsMenu(viewModel) },
        actionBar = {
            ContentCrossfade(
                targetState = spam,
                label = "ActionBarSpamFade"
            ) { state ->
                if (state == UiState.Spam.Maybe) {
                    TxSpamButtons(viewModel::reportSpam)
                } else {
                    TxDetailsExplorer(
                        viewModel = viewModel,
                        hash = uiState.hash
                    )
                }
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(interop)
                .padding(top = Dimens.cornerMedium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                if (spam == UiState.Spam.Spam) {
                    TxSpamBadge()
                } else if (uiState.imageUrl != null) {
                    MoonAsyncImage(
                        modifier = Modifier
                            .clip(Shapes.large)
                            .clickable(onClick = { viewModel.openNft() }),
                        image = uiState.imageUrl!!,
                        size = 96.dp,
                    )
                } else {
                    TxDetailsIcon(
                        icons = uiState.icons
                    )
                }
            }

            item {
                TKDetailsInfo(
                    modifier = Modifier.padding(top = 22.dp, bottom = 32.dp),
                    aboveTitle = uiState.aboveTitle,
                    title = uiState.title,
                    subtitle = uiState.subtitle,
                    verifiedSubtitle = uiState.verifiedSubtitle,
                    date = uiState.date,
                    failedText = uiState.warningText
                )
            }


            item {
                TKDetails(
                    details = uiState.details,
                    onClick = { row ->
                        viewModel.onClickDetailsRow(row.id)
                    }
                )
            }

        }
    }
}
