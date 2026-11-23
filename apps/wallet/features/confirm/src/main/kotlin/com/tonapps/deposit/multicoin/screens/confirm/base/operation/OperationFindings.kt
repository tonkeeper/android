package com.tonapps.deposit.multicoin.screens.confirm.base.operation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.ValidationError
import com.tonapps.chainkit.core.op.isUnlimited
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsOrange
import ui.components.moon.ButtonColorsRed
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.cell.TextCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.shortAddress
import ui.theme.UIKit

/**
 * A finding of the operation, with the asset its amounts are in when the intent lists it, shown as
 * [severity]: the error's own unless the flow lowers it.
 */
class OperationFinding(
    val error: ValidationError,
    val asset: Asset?,
    val severity: FindingSeverity = error.severity,
)

/** How a finding is shown: an [Info] one asks nothing, a [Warning] one is orange, a [Danger] one red. */
enum class FindingSeverity { Info, Warning, Danger }

/**
 * The asset of [intent] this finding's amounts are in. A finding names it as its chain does — a
 * contract, or an asset id — so an asset the intent does not list has no decimals to show it with.
 */
internal fun ValidationError.amountAsset(intent: Intent): Asset? {
    val named = when (this) {
        is ValidationError.Overspend -> asset
        is ValidationError.LargeApproval -> asset
        is ValidationError.Approval -> asset
        is ValidationError.LongAllowance -> token
        else -> return null
    }
    return intent.listed
        .map { it.asset }
        .firstOrNull { it.id == named || (it as? Asset.Token)?.contract?.display.equals(named, ignoreCase = true) }
}


val ValidationError.severity: FindingSeverity
    get() = when (this) {
        // A wrong number of decimals shows the user another amount than the one signed.
        is ValidationError.Dangerous,
        is ValidationError.WrongDecimals -> FindingSeverity.Danger

        is ValidationError.Info -> FindingSeverity.Info
        else -> FindingSeverity.Warning
    }

/** The most severe of the findings; null when there are none. */
val List<OperationFinding>.severity: FindingSeverity?
    get() = maxOfOrNull { it.severity }

@Composable
internal fun FindingSeverity.color(): Color = when (this) {
    FindingSeverity.Danger -> UIKit.colorScheme.accent.red
    FindingSeverity.Warning -> UIKit.colorScheme.accent.orange
    FindingSeverity.Info -> UIKit.colorScheme.accent.blue
}

@Composable
private fun ValidationError.title(): String = stringResource(
    when (this) {
        is ValidationError.Overspend -> Localization.operation_finding_overspend
        is ValidationError.ForeignRecipient -> Localization.operation_finding_foreign_recipient
        is ValidationError.ForeignAsset -> Localization.operation_finding_foreign_asset
        is ValidationError.CollectionApproval -> Localization.operation_finding_collection_approval
        is ValidationError.ForeignDelegate -> Localization.operation_finding_foreign_delegate
        is ValidationError.PermissionUpdate -> Localization.operation_finding_permission_update
        is ValidationError.PermissionShared -> Localization.operation_finding_permission_shared
        is ValidationError.ForeignChain -> Localization.operation_finding_foreign_chain
        is ValidationError.ForeignAccount -> Localization.operation_finding_foreign_account
        is ValidationError.ForeignOrigin -> Localization.operation_finding_foreign_origin
        is ValidationError.SelfAuthorization -> Localization.operation_finding_self_authorization
        is ValidationError.LooseSighash -> Localization.operation_finding_loose_sighash
        is ValidationError.AuthorityChange -> Localization.operation_finding_authority_change
        is ValidationError.TransactionAsMessage -> Localization.operation_finding_transaction_as_message
        is ValidationError.AccountDestruction -> Localization.operation_finding_account_destruction
        is ValidationError.BlindSignature -> Localization.operation_finding_blind_signature
        is ValidationError.ForeignStateInit -> Localization.operation_finding_foreign_state_init
        is ValidationError.Malformed -> Localization.operation_finding_malformed
        is ValidationError.UnknownScript -> Localization.operation_finding_unknown_script
        is ValidationError.WholeBalance -> Localization.operation_finding_whole_balance
        is ValidationError.ReceiverData -> Localization.operation_finding_receiver_data
        is ValidationError.SelfCall -> Localization.operation_finding_self_call
        is ValidationError.UnknownDocument -> Localization.operation_finding_unknown_document
        is ValidationError.OpaqueMessage -> Localization.operation_finding_opaque_message
        is ValidationError.UnresolvedAccount -> Localization.operation_finding_unresolved_account
        is ValidationError.UnsupportedAsset -> Localization.operation_finding_unsupported_asset
        is ValidationError.UnsupportedPayload -> Localization.operation_finding_unsupported_payload
        is ValidationError.LongAllowance -> Localization.operation_finding_long_allowance
        is ValidationError.DurableNonce -> Localization.operation_finding_durable_nonce
        is ValidationError.SingleSighash -> Localization.operation_finding_single_sighash
        is ValidationError.UnlistedAsset -> Localization.operation_finding_unlisted_asset
        is ValidationError.LargeApproval -> Localization.operation_finding_large_approval
        is ValidationError.BlindCall -> Localization.operation_finding_blind_call
        is ValidationError.ForeignOwner -> Localization.operation_finding_foreign_owner
        is ValidationError.MarketplaceApproval -> Localization.operation_finding_marketplace_approval
        is ValidationError.Approval -> Localization.operation_finding_approval
        is ValidationError.WrongDecimals -> Localization.operation_finding_wrong_decimals
        is ValidationError.UnsupportedChain -> Localization.operation_finding_unsupported_chain
        else -> Localization.operation_finding_unknown
    },
)

// Who or what the finding is about, as the chain names it.
@Composable
private fun ValidationError.subject(): String? = when (this) {
    is ValidationError.Overspend -> asset.shortAddress
    is ValidationError.ForeignRecipient -> actual.shortAddress
    is ValidationError.ForeignAsset -> asset.shortAddress
    is ValidationError.CollectionApproval -> operator.shortAddress
    is ValidationError.ForeignDelegate -> implementation.shortAddress
    is ValidationError.PermissionUpdate -> keys.map { key -> key.shortAddress }.joinToString()
    is ValidationError.PermissionShared -> keys.map { key -> key.shortAddress }.joinToString()
    is ValidationError.ForeignChain -> actual
    is ValidationError.ForeignAccount -> actual.shortAddress
    is ValidationError.ForeignOrigin -> actual
    is ValidationError.AuthorityChange -> authority.shortAddress
    is ValidationError.ForeignStateInit -> destination.shortAddress
    is ValidationError.Malformed -> part
    is ValidationError.WholeBalance -> target.shortAddress
    is ValidationError.UnknownDocument -> part
    is ValidationError.UnresolvedAccount -> account.shortAddress
    is ValidationError.UnsupportedAsset -> asset.shortAddress
    is ValidationError.UnsupportedPayload -> type
    is ValidationError.LongAllowance -> spender.shortAddress
    is ValidationError.UnlistedAsset -> asset.shortAddress
    is ValidationError.LargeApproval -> spender.shortAddress
    is ValidationError.BlindCall -> target.shortAddress
    is ValidationError.ForeignOwner -> owner.shortAddress
    is ValidationError.MarketplaceApproval -> operator.shortAddress
    is ValidationError.Approval -> spender.shortAddress
    is ValidationError.WrongDecimals -> asset.shortAddress
    is ValidationError.UnsupportedChain -> chain
    else -> null
}

// What the finding moves or allows, against what the screen showed: in [asset], which the intent lists.
@Composable
private fun OperationFinding.amounts(): String? {
    val asset = asset ?: return null
    return when (val error = error) {
        is ValidationError.Overspend -> stringResource(
            Localization.operation_finding_amount_instead,
            asset.format(error.actual),
            asset.format(error.limit),
        )

        is ValidationError.LargeApproval -> when (val limit = error.limit) {
            null -> asset.format(error.amount)
            else -> stringResource(Localization.operation_finding_amount_instead, asset.format(error.amount), asset.format(limit))
        }

        is ValidationError.Approval -> asset.format(error.amount)
        is ValidationError.LongAllowance -> asset.format(error.amount)
        else -> null
    }
}

@Composable
private fun Asset.format(amount: BigInteger): String {
    if (amount.isUnlimited) {
        return stringResource(Localization.operation_finding_unlimited, symbol)
    }

    return Formatter.formatShort(value = toDisplayUnit(amount), asset = this)
}

/** Asks the user to read [findings] before the operation is signed: [onConfirm] signs it anyway. */
@Composable
internal fun OperationFindingsDialog(
    findings: List<OperationFinding>,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val router = rememberDialogNavigator { onClose() }
    MoonModalDialog(navigator = router) {
        OperationFindingsContent(
            findings = findings,
            onConfirm = {
                onConfirm()
                router.close()
            },
            onClose = { router.close() },
        )
    }
}

@Composable
internal fun ColumnScope.OperationFindingsContent(
    findings: List<OperationFinding>,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val danger = findings.severity == FindingSeverity.Danger

    MoonTopAppBarSimple(
        title = "",
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
        backgroundColor = Color.Transparent,
    )

    Spacer(modifier = Modifier.height(20.dp))

    MoonItemIcon(
        painter = when {
            danger -> painterResource(UIKitIcon.ic_exclamationmark_triangle_colorful_84)
            else -> painterResource(UIKitIcon.ic_exclamationmark_circle_colorful_84)
        },
        color = Color.Unspecified,
        size = 66.dp,
    )

    MoonTextContentCell(
        title = stringResource(
            when {
                danger -> Localization.operation_risk_danger_title
                else -> Localization.operation_risk_warning_title
            },
        ),
        description = stringResource(Localization.operation_risk_description),
    )

    Spacer(modifier = Modifier.height(16.dp))

    OperationFindingsList(findings)

    Spacer(modifier = Modifier.height(16.dp))

    MoonButtonCell(
        onClick = onConfirm,
        text = stringResource(Localization.confirm_anyway),
        colors = when {
            danger -> ButtonColorsRed
            else -> ButtonColorsOrange
        },
    )

    MoonButtonCell(
        onClick = onClose,
        text = stringResource(Localization.cancel),
        colors = ButtonColorsSecondary,
    )

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun OperationFindingsList(findings: List<OperationFinding>) {
    findings.forEachIndexed { index, finding ->
        val severity = finding.severity
        // The amounts lead when there are any, and who gets them goes under.
        val amounts = finding.amounts()
        val subject = finding.error.subject()
        MoonBundleCell(
            position = MoonBundlePosition.default(findings.size, index),
        ) {
            TextCell(
                title = finding.error.title(),
                subtitle = amounts ?: subject,
                description = subject?.takeIf { amounts != null }?.let { text -> { MoonItemSubtitle(text = text) } },
                maxLinesTitle = 2,
                image = {
                    MoonCircleIcon(
                        painter = painterResource(
                            when (severity) {
                                FindingSeverity.Danger -> UIKitIcon.ic_exclamationmark_triangle_28
                                FindingSeverity.Warning -> UIKitIcon.ic_exclamationmark_circle_28
                                FindingSeverity.Info -> UIKitIcon.ic_information_circle_28
                            },
                        ),
                        size = 44.dp,
                        iconSize = 28.dp,
                        color = UIKit.colorScheme.background.contentTint,
                        tint = UIKit.colorScheme.icon.secondary,
                    )
                },
                minHeight = 76.dp,
            )
        }
    }
}

////////////////////
// PREVIEW
////////////////////
private const val PREVIEW_PAYLOAD = "0"
private const val PREVIEW_ROUTER = "0x3B4D794a66304F130a4Db8F2551B0070dfCf5ca7"
private const val PREVIEW_TOKEN = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"

private val PREVIEW_INTENT = Intent(
    spends = listOf(
        Intent.Spend(
            asset = Asset.Token(
                id = "eth/mainnet/erc20/$PREVIEW_TOKEN",
                chain = Chain.Ethereum.Mainnet,
                contract = Address.unchecked(PREVIEW_TOKEN, Chain.Ethereum.Mainnet),
                type = TokenType.Erc20,
                tokenId = "",
                symbol = "USDC",
                name = "USD Coin",
                decimals = Decimal(6),
            ),
            amount = BigInteger.fromInt(50_000_000),
        ),
    ),
    to = null,
    source = Intent.Source.DApp,
)

@Composable
private fun OperationFindingsPreview(findings: List<ValidationError>) {
    ThemedPreview {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OperationFindingsContent(
                findings = findings.map { OperationFinding(it, it.amountAsset(PREVIEW_INTENT)) },
                onConfirm = {},
                onClose = {},
            )
        }
    }
}

@Preview
@Composable
private fun OperationFindingsDangerPreview() {
    OperationFindingsPreview(
        listOf(
            ValidationError.ForeignRecipient(PREVIEW_PAYLOAD, expected = PREVIEW_ROUTER, actual = "0xd1c44e466b70002ac54fb727ed7cbe8f92c782f1"),
            ValidationError.Overspend(
                PREVIEW_PAYLOAD,
                asset = PREVIEW_TOKEN,
                limit = BigInteger.fromInt(50_000_000),
                actual = BigInteger.fromInt(90_000_000),
                approved = BigInteger.ZERO,
            ),
            ValidationError.UnsupportedPayload(PREVIEW_PAYLOAD, type = "eip7702/authorization"),
            ValidationError.Approval(PREVIEW_PAYLOAD, asset = PREVIEW_TOKEN, spender = PREVIEW_ROUTER, amount = BigInteger.fromInt(50_000_000)),
        ),
    )
}

@Preview
@Composable
private fun OperationFindingsWarningPreview() {
    OperationFindingsPreview(
        listOf(
            ValidationError.LargeApproval(
                PREVIEW_PAYLOAD,
                asset = PREVIEW_TOKEN,
                spender = PREVIEW_ROUTER,
                amount = BigInteger.parseString("115792089237316195423570985008687907853269984665640564039457584007913129639935"),
                limit = BigInteger.fromInt(50_000_000),
            ),
            ValidationError.DurableNonce(PREVIEW_PAYLOAD),
        ),
    )
}
