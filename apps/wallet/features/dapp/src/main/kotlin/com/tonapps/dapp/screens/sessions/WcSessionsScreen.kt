package com.tonapps.dapp.screens.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonapps.core.helper.T
import com.tonapps.extensions.toUriOrNull
import com.tonapps.log.L
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectWithDetails
import com.tonapps.wallet.data.dapps.source.db.ConnectEntity
import com.tonapps.wallet.localization.Localization
import com.tonapps.wc.models.WcConnection
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonScaffold
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.theme.UIKit

@Composable
fun WcSessionsScreen(
    feature: WcSessionsFeature,
    onBack: () -> Unit,
    onExploreApps: () -> Unit,
) {
    MoonScaffold(
        title = stringResource(Localization.apps),
        onBack = onBack,
    ) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            feature.events.collect {
                when (it) {
                    WcSessionsEvents.DisconnectError -> {
                        L.d("Disconnect error")
                        T.show(context.getString(Localization.wc_disconnect_error))
                    }
                    WcSessionsEvents.DisconnectSuccess -> {
                        L.d("Disconnected")
                        T.show(context.getString(Localization.wc_disconnected))
                    }
                }
            }
        }

        val apps by feature.apps.collectAsState()
        var disconnectRequest by remember { mutableStateOf<AppConnectWithDetails?>(null) }

        when {
            apps.isEmpty() -> {
                MoonEmptyScreen(
                    text = stringResource(Localization.connected_placeholder_title),
                    description = stringResource(Localization.connected_placeholder_subtitle),
                    type = MoonEmptyScreenType.EmptyApps,
                    buttonText = stringResource(Localization.explore_apps),
                    buttonIconRes = null,
                    onButtonClick = onExploreApps,
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    )
                ) {
                    items(apps.size) {
                        val item = apps[it]
                        MoonBundleCell(
                            position = MoonBundlePosition.default(apps.size, it)
                        ) {
                            TextCell(
                                title = item.app.name.orEmpty(),
                                subtitle = remember(item.app.url) { item.app.url.toUriOrNull()?.host },
                                description = {
                                    val source = item.connect.displaySource
                                    val createdAt = item.connect.createdAtFormatted
                                    if (source != null) {
                                        val label = source.connectionName()
                                        MoonItemSubtitle(
                                            text = if (createdAt != null) {
                                                stringResource(
                                                    Localization.connected_apps_source_format,
                                                    label,
                                                    createdAt,
                                                )
                                            } else {
                                                label
                                            }
                                        )
                                    }
                                },
                                image = {
                                    MoonCutBadgedBox(
                                        badge = {
                                            item.connect.displaySource?.let { source ->
                                                ConnectionBadge(source)
                                            }
                                        },
                                        direction = BadgeDirection.EndBottom,
                                    ) {
                                        MoonItemImage(
                                            image = item.app.iconUrl,
                                            size = 44.dp,
                                            shape = UIKit.shapes.large,
                                        )
                                    }
                                },
                                content = {
                                    MoonCircleIcon(
                                        modifier = Modifier.clickable { disconnectRequest = item },
                                        painter = painterResource(UIKitIcon.ic_close_16),
                                        size = 36.dp,
                                    )
                                },
                                minHeight = 96.dp,
                            )
                        }
                    }
                }
            }
        }

        disconnectRequest?.let { request ->
            MoonDisconnectBottomSheet(
                name = request.app.name.orEmpty(),
                iconUrl = request.app.iconUrl,
                onDisconnect = {
                    feature.disconnect(request)
                },
                onClose = { disconnectRequest = null }
            )
        }
    }
}


@Composable
fun MoonDisconnectBottomSheet(
    name: String,
    iconUrl: String?,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    val router = rememberDialogNavigator { onClose() }
    MoonModalDialog(navigator = router) {
        DisconnectDappContent(
            name = name,
            iconUrl = iconUrl,
            onDisconnect = {
                onDisconnect()
                router.close()
            },
            onClose = { router.close() },
        )
    }
}

@Composable
fun DisconnectDappContent(
    name: String,
    iconUrl: String?,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    val disconnectAccent = UIKit.colorScheme.accent.red
    val disconnectColors = ButtonColors(
        containerColor = disconnectAccent.copy(alpha = 0.08f),
        contentColor = disconnectAccent,
        disabledContainerColor = disconnectAccent.copy(alpha = 0.08f),
        disabledContentColor = disconnectAccent,
    )

    MoonTopAppBarSimple(
        title = "",
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
        backgroundColor = Color.Transparent,
    )

    Spacer(modifier = Modifier.height(8.dp))

    MoonItemImage(
        image = iconUrl,
        size = 72.dp,
        shape = UIKit.shapes.large,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(Localization.disconnect_dapp_confirm, name),
        modifier = Modifier.padding(horizontal = 32.dp),
        style = UIKit.typography.h2,
        color = UIKit.colorScheme.text.primary,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = stringResource(Localization.wc_disconnect_remove_access, name),
        modifier = Modifier.padding(horizontal = 32.dp),
        style = UIKit.typography.body1,
        color = UIKit.colorScheme.text.secondary,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(32.dp))

    MoonAccentButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onDisconnect,
        text = stringResource(id = Localization.disconnect),
        buttonColors = disconnectColors,
        size = ButtonSizeLarge,
    )

    Spacer(modifier = Modifier.height(16.dp))

    MoonAccentButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClose,
        text = stringResource(id = Localization.cancel),
        buttonColors = ButtonColorsSecondary,
        size = ButtonSizeLarge,
    )

    Spacer(modifier = Modifier.height(16.dp))
}

// TonConnect rows never persist a source; `type` is the only origin signal they carry.
private val ConnectEntity.displaySource: WcConnection?
    get() = source ?: when (type) {
        AppConnectEntity.Type.Internal.value -> WcConnection.DApp
        AppConnectEntity.Type.External.value -> WcConnection.Deeplink
        else -> null
    }

@Composable
private fun WcConnection.connectionName(): String = when (this) {
    WcConnection.Deeplink -> stringResource(Localization.connected_apps_source_deeplink)
    WcConnection.DApp -> stringResource(Localization.connected_apps_source_dapp)
    WcConnection.Qr -> stringResource(Localization.connected_apps_source_qr)
}

@Composable
private fun ConnectionBadge(connection: WcConnection) {
    when (connection) {
        WcConnection.Deeplink -> MoonCircleIcon(
            painterResource(id = UIKitIcon.ic_link_outline_28),
            color = UIKit.colorScheme.accent.purple,
            size = 20.dp,
            iconSize = 12.dp,
        )
        WcConnection.DApp -> MoonCircleIcon(
            painterResource(id = UIKitIcon.ic_logo_128),
            color = UIKit.colorScheme.accent.blue,
            size = 20.dp,
            iconSize = 12.dp,
        )
        WcConnection.Qr -> MoonCircleIcon(
            painterResource(id = UIKitIcon.ic_qr_code_16),
            color = UIKit.colorScheme.accent.green,
            size = 20.dp,
            iconSize = 12.dp,
        )
    }
}
