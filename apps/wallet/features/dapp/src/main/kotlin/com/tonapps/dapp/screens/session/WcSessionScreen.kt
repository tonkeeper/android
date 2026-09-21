package com.tonapps.dapp.screens.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.tonapps.blockchain.model.DappConnectRequest.DomainStatus
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.core.components.WalletRowCell
import com.tonapps.core.components.WalletsSelectorDialog
import com.tonapps.core.components.painterResource
import com.tonapps.core.helper.T
import com.tonapps.dapp.component.ConnectCryptoView
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.localization.Localization
import androidx.compose.material3.ButtonColors
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonButtonCellDefaults
import ui.components.moon.container.MoonCutRow
import ui.components.moon.container.MoonScaffold
import ui.components.moon.container.OverlapDirection
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.theme.Dimens
import ui.theme.UIKit

private const val MaxVisibleNetworkIcons = 6

@Composable
fun WcSessionScreen(
    feature: WcSessionFeature,
    onBack: () -> Unit,
    onOpenNetworks: (List<AccountEntity>) -> Unit,
) {
    val appInfo = feature.appInfo

    val wallets by feature.wallets.collectAsState()
    val wallet by feature.selectedWallet.collectAsState()
    val accounts by feature.accounts.collectAsState()

    val isLoading by feature.isLoading.collectAsState()

    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        feature.events.flowWithLifecycle(lifecycle.lifecycle).collect { event ->
            when (event) {
                is WcSessionEvent.Done -> {
                    onBack()
                }
                is WcSessionEvent.ShowError -> {
                    T.show(context.getString(Localization.error_with_message, event.message))
                    onBack()
                }
            }
        }
    }

    MoonScaffold(
        Modifier.fillMaxSize(),
        title = "",
        onClose = { feature.reject() },
    ) {
        Box(
            Modifier.fillMaxSize()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(rememberNestedScrollInteropConnection())
            ) {
                DappHeader(
                    name = appInfo.dappName,
                    host = appInfo.dappHost,
                    iconUrl = appInfo.iconUrl,
                    status = feature.dappConnection.domainStatus,
                    walletAddress = accounts?.firstOrNull()?.displayAddress.orEmpty(),
                )

                var showWalletSelector by remember { mutableStateOf(false) }
                val balances by feature.balances.collectAsState()
                wallet?.let {
                    Spacer(Modifier.height(32.dp))

                    WalletRowCell(
                        wallet = it,
                        content = {
                            MoonItemIcon(
                                painter = ui.painterResource(UIKitIcon.ic_switch_16),
                            )
                        },
                        position = MoonBundlePosition.Default,
                        onClick = { showWalletSelector = true },
                        subtitle = balances[it.id],
                    )

                    if (showWalletSelector) {
                        WalletsSelectorDialog(
                            wallets = wallets,
                            selectedWallet = it,
                            balances = balances,
                            onSelectWallet = { feature.selectWallet(it) },
                            onClose = { showWalletSelector = false },
                        )
                    }
                }

                Spacer(Modifier.height(Dimens.offsetMedium))

                PermissionsSection()

                Spacer(Modifier.height(Dimens.offsetMedium))

                accounts?.let { networkAccounts ->
                    NetworksSection(
                        accounts = networkAccounts,
                        onOpenNetworks = { onOpenNetworks(networkAccounts) },
                    )
                }

                Spacer(Modifier.height(192.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }

            val bottomBarBackground = UIKit.colorScheme.background.page
            val bottomBarFadeStops = remember(bottomBarBackground) {
                arrayOf(0f to Color.Transparent, 0.7f to bottomBarBackground)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(brush = Brush.verticalGradient(colorStops = bottomBarFadeStops))
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp)
            ) {
                val isValid = feature.dappConnection.domainStatus == DomainStatus.Valid
                var isWarningOpened by remember { mutableStateOf(false) }

                MoonBottomButtonCell(
                    text = stringResource(Localization.connect_wallet),
                    loading = isLoading,
                    colors = when (feature.dappConnection.domainStatus) {
                        DomainStatus.Scam -> MoonButtonCellDefaults.ButtonColorsRed
                        DomainStatus.Invalid -> MoonButtonCellDefaults.ButtonColorsRed
                        DomainStatus.Valid -> MoonButtonCellDefaults.ButtonColorsPrimary
                        DomainStatus.Unknown,
                        null -> MoonButtonCellDefaults.ButtonColorsOrange
                    }
                ) {
                    if (isValid) {
                        feature.approve()
                    } else {
                        isWarningOpened = true
                    }
                }

                if (isWarningOpened) {
                    MoonDomainRiskBottomSheet(
                        status = feature.dappConnection.domainStatus,
                        onConnect = { feature.approve() },
                        onClose = { isWarningOpened = false }
                    )
                }

                Text(
                    text = when (feature.dappConnection.domainStatus) {
                        DomainStatus.Scam -> stringResource(Localization.domain_status_scam)
                        DomainStatus.Invalid -> stringResource(Localization.domain_status_invalid)
                        DomainStatus.Valid -> stringResource(Localization.domain_status_valid)
                        DomainStatus.Unknown,
                        null -> stringResource(Localization.domain_status_unknown)
                    },
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    color = when (feature.dappConnection.domainStatus) {
                        DomainStatus.Scam -> UIKit.colorScheme.accent.red
                        DomainStatus.Invalid -> UIKit.colorScheme.accent.red
                        DomainStatus.Valid,
                        DomainStatus.Unknown,
                        null -> UIKit.colorScheme.text.secondary
                    },
                    style = UIKit.typography.body2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PermissionsSection() {
    val permissions = listOf(
        Localization.wc_permission_view_info,
        Localization.wc_permission_request_approvals,
        Localization.wc_permission_no_asset_transfer,
    )

    MoonBundleTitleCell(title = stringResource(Localization.wc_allow_this_app))
    MoonBundleCell(position = MoonBundlePosition.Default) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            permissions.forEach { textRes ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_done_bold_16),
                        color = UIKit.colorScheme.accent.blue,
                        size = 16.dp,
                    )
                    Text(
                        text = stringResource(textRes),
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworksSection(
    accounts: List<AccountEntity>,
    onOpenNetworks: () -> Unit,
) {
    MoonBundleTitleCell(title = stringResource(Localization.networks))
    MoonBundleCell(
        position = MoonBundlePosition.Default,
        onClick = onOpenNetworks,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NetworkIconsRow(accounts = accounts)
            Spacer(modifier = Modifier.weight(1f))
            MoonChevronRight()
        }
    }
}

@Composable
private fun NetworkIconsRow(
    accounts: List<AccountEntity>,
) {
    val visibleAccounts = accounts.take(MaxVisibleNetworkIcons)
    val hasMore = accounts.size > MaxVisibleNetworkIcons

    MoonCutRow(
        direction = OverlapDirection.StartOnTop,
    ) {
        visibleAccounts.forEach { account ->
            MoonItemImage(
                painter = account.chain.painterResource(),
                size = 24.dp,
            )
        }
        if (hasMore) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(UIKit.colorScheme.background.contentTint),
                contentAlignment = Alignment.Center,
            ) {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_ellipsis_16),
                    size = 14.dp,
                    color = UIKit.colorScheme.icon.secondary,
                )
            }
        }
    }
}

@Composable
private fun DappHeader(
    name: String,
    host: String,
    iconUrl: String?,
    status: DomainStatus?,
    walletAddress: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconShape = remember { RoundedCornerShape(20.dp) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                MoonItemImage(
                    painter = painterResource(UIKitIcon.bg_logo_tile),
                    size = 72.dp,
                    shape = iconShape,
                )

                Image(
                    painter = painterResource(UIKitIcon.ic_logo_tile),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
            }

            if (walletAddress.isNotEmpty()) {
                ConnectCryptoView(
                    key = walletAddress,
                )
            }

            MoonItemImage(
                image = iconUrl,
                size = 72.dp,
                shape = iconShape,
            )
        }

        Spacer(Modifier.height(20.dp))

        val accentColor = when (status) {
            DomainStatus.Scam,
            DomainStatus.Invalid -> UIKit.colorScheme.accent.red
            DomainStatus.Valid -> UIKit.colorScheme.accent.blue
            DomainStatus.Unknown,
            null -> UIKit.colorScheme.accent.orange
        }

        Text(
            text = stringResource(Localization.wc_connect_title, name),
            style = UIKit.typography.h2,
            color = UIKit.colorScheme.text.primary,
            maxLines = 3,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        val requestTemplate = stringResource(Localization.wc_connect_request, host)
        Text(
            text = remember(host, accentColor, requestTemplate) {
                buildAnnotatedString {
                    val hostStart = requestTemplate.indexOf(host)
                    if (hostStart >= 0) {
                        append(requestTemplate.substring(0, hostStart))
                        withStyle(SpanStyle(color = accentColor)) {
                            append(host)
                        }
                        append(requestTemplate.substring(hostStart + host.length))
                    } else {
                        append(requestTemplate)
                    }
                }
            },
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 3,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun MoonDomainRiskBottomSheet(
    status: DomainStatus?,
    onConnect: () -> Unit,
    onClose: () -> Unit,
) {
    val isCritical = status == DomainStatus.Scam || status == DomainStatus.Invalid
    val router = rememberDialogNavigator { onClose() }
    MoonModalDialog(navigator = router) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { router.close() },
            backgroundColor = Color.Transparent,
        )

        Spacer(modifier = Modifier.height(8.dp))

        MoonItemImage(
            painter = painterResource(
                if (isCritical) {
                    UIKitIcon.ic_exclamationmark_triangle_colorful_84
                } else {
                    UIKitIcon.ic_exclamationmark_circle_colorful_84
                }
            ),
            size = 84.dp,
            shape = RectangleShape,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (status) {
                DomainStatus.Scam -> stringResource(Localization.domain_risk_scam_title)
                DomainStatus.Invalid -> stringResource(Localization.domain_risk_invalid_title)
                else -> stringResource(Localization.domain_risk_unknown_title)
            },
            modifier = Modifier.padding(horizontal = 32.dp),
            style = UIKit.typography.h2,
            color = UIKit.colorScheme.text.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (status) {
                DomainStatus.Scam -> stringResource(Localization.domain_risk_scam_description)
                DomainStatus.Invalid -> stringResource(Localization.domain_risk_invalid_description)
                else -> stringResource(Localization.domain_status_unknown)
            },
            modifier = Modifier.padding(horizontal = 32.dp),
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.secondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        val connectAnywayAccent = if (isCritical) {
            UIKit.colorScheme.accent.red
        } else {
            UIKit.colorScheme.accent.orange
        }
        val connectAnywayColors = ButtonColors(
            containerColor = connectAnywayAccent.copy(alpha = 0.08f),
            contentColor = connectAnywayAccent,
            disabledContainerColor = connectAnywayAccent.copy(alpha = 0.08f),
            disabledContentColor = connectAnywayAccent,
        )

        MoonAccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = {
                onConnect()
                router.close()
            },
            text = stringResource(Localization.connect_anyway),
            buttonColors = connectAnywayColors,
            size = ButtonSizeLarge,
        )

        Spacer(modifier = Modifier.height(16.dp))

        MoonAccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = { router.close() },
            text = stringResource(id = Localization.cancel),
            buttonColors = ButtonColorsSecondary,
            size = ButtonSizeLarge,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
