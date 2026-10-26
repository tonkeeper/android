package com.tonapps.perps.screens.order

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.data.formatSignedUsdAmount
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.limitFractionDigits
import com.tonapps.perps.data.matches
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.perps.screens.review.PerpsAutoCloseField
import com.tonapps.perps.screens.review.PerpsAutoCloseFix
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonOutlineChip
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonTextFieldCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit
import java.math.BigDecimal
import java.math.RoundingMode

// The rejected draft wins over the caller's own legs: the position page only knows the resting
// orders, so without it the sheet reopens empty instead of on what the user just typed.
internal fun PerpsAutoCloseDraft?.withFix(fix: PerpsAutoCloseFix?): PerpsAutoCloseDraft? {
    val fix = fix ?: return this
    val base = fix.draft ?: this
    val suggestion = fix.suggestion ?: return base

    return if (fix.field == PerpsAutoCloseField.TAKE_PROFIT) {
        PerpsAutoCloseDraft(takeProfit = suggestion, stopLoss = base?.stopLoss, stopLossRoi = base?.stopLossRoi)
    } else {
        PerpsAutoCloseDraft(takeProfit = base?.takeProfit, stopLoss = suggestion, takeProfitRoi = base?.takeProfitRoi)
    }
}

data class PerpsAutoCloseSubmit(
    val submitting: Boolean,
    val error: PerpsAutoCloseSubmitError?,
    val done: Boolean,
)

data class PerpsAutoCloseSubmitError(
    val field: PerpsAutoCloseField?,
    @StringRes val text: Int,
)

@Composable
fun PerpsAutoCloseDialog(
    priceDecimals: Int,
    side: PerpsOrderSide,
    bounds: PerpsAutoCloseBounds,
    initial: PerpsAutoCloseDraft?,
    estimate: PerpsAutoCloseEstimate?,
    onSet: (PerpsAutoCloseDraft?) -> Unit,
    onClose: () -> Unit,
    submit: PerpsAutoCloseSubmit? = null,
    limitPrice: BigDecimal? = null,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)
    val done = submit?.done == true

    LaunchedEffect(done) {
        if (done) {
            navigator.close()
        }
    }

    MoonModalDialog(navigator = navigator) {
        PerpsAutoCloseDialogBody(
            limitPrice = limitPrice,
            priceDecimals = priceDecimals,
            side = side,
            bounds = bounds,
            initial = initial,
            estimate = estimate,
            submit = submit,
            onSet = { draft ->
                val unchanged = submit != null && draft.matches(initial, priceDecimals)
                if (submit == null || unchanged) {
                    navigator.close()
                }
                if (!unchanged) {
                    onSet(draft)
                }
            },
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun PerpsAutoCloseDialogBody(
    limitPrice: BigDecimal?,
    priceDecimals: Int,
    side: PerpsOrderSide,
    bounds: PerpsAutoCloseBounds,
    initial: PerpsAutoCloseDraft?,
    estimate: PerpsAutoCloseEstimate?,
    submit: PerpsAutoCloseSubmit?,
    onSet: (PerpsAutoCloseDraft?) -> Unit,
    onClose: () -> Unit,
) {
    val isLong = side == PerpsOrderSide.LONG
    val entryPrice = bounds.entryPrice
    val leverage = bounds.leverage

    var profitPercent by rememberSaveable {
        mutableStateOf(
            legRoi(initial?.takeProfit, initial?.takeProfitRoi, entryPrice, leverage, side)
                ?.let { formatPlainDecimal(it.abs()) }
                .orEmpty()
        )
    }
    var profitSign by rememberSaveable {
        mutableIntStateOf(
            roiSign(legRoi(initial?.takeProfit, initial?.takeProfitRoi, entryPrice, leverage, side), TAKE_PROFIT_SIGN)
        )
    }
    var profitByPercent by rememberSaveable { mutableStateOf(initial?.takeProfitRoi != null) }
    var takeProfitPrice by rememberSaveable {
        mutableStateOf(
            initial?.takeProfitRoi
                ?.let { roiPriceText(entryPrice, it, leverage, priceDecimals, side) }
                ?: initial?.takeProfit?.let { formatPlainDecimal(it) }.orEmpty()
        )
    }
    var lossPercent by rememberSaveable {
        mutableStateOf(
            legRoi(initial?.stopLoss, initial?.stopLossRoi, entryPrice, leverage, side)
                ?.let { formatPlainDecimal(it.abs()) }
                .orEmpty()
        )
    }
    var lossSign by rememberSaveable {
        mutableIntStateOf(
            roiSign(legRoi(initial?.stopLoss, initial?.stopLossRoi, entryPrice, leverage, side), STOP_LOSS_SIGN)
        )
    }
    var lossByPercent by rememberSaveable { mutableStateOf(initial?.stopLossRoi != null) }
    var stopLossPrice by rememberSaveable {
        mutableStateOf(
            initial?.stopLossRoi
                ?.let { roiPriceText(entryPrice, it, leverage, priceDecimals, side) }
                ?: initial?.stopLoss?.let { formatPlainDecimal(it) }.orEmpty()
        )
    }

    val takeProfit = takeProfitPrice.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    val stopLoss = stopLossPrice.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    val draft = PerpsAutoCloseDraft(
        takeProfit = takeProfit,
        stopLoss = stopLoss,
        takeProfitRoi = typedRoi(profitPercent, profitSign).takeIf { profitByPercent && takeProfit != null },
        stopLossRoi = typedRoi(lossPercent, lossSign).takeIf { lossByPercent && stopLoss != null },
    )
        .takeIf { takeProfit != null || stopLoss != null }
    val takeProfitUnplaceable = profitByPercent && takeProfit == null && typedRoi(profitPercent, profitSign) != null
    val stopLossUnplaceable = lossByPercent && stopLoss == null && typedRoi(lossPercent, lossSign) != null
    val check = checkAutoClose(side, takeProfit, stopLoss, bounds)
    val submitting = submit?.submitting == true
    var submittedDraft by remember { mutableStateOf<PerpsAutoCloseDraft?>(null) }
    val submitError = submit?.error?.takeIf { draft.matches(submittedDraft, priceDecimals) }

    val unplaceableText = stringResource(Localization.perps_review_auto_close_invalid)
    val takeProfitError = unplaceableText.takeIf { takeProfitUnplaceable }
        ?: check.takeProfit?.let { perpsAutoCloseIssueText(it, priceDecimals) }
        ?: submitError?.takeIf { it.field == PerpsAutoCloseField.TAKE_PROFIT }?.let { stringResource(it.text) }
    val stopLossError = unplaceableText.takeIf { stopLossUnplaceable }
        ?: check.stopLoss?.let { perpsAutoCloseIssueText(it, priceDecimals) }
        ?: submitError?.takeIf { it.field == PerpsAutoCloseField.STOP_LOSS }?.let { stringResource(it.text) }
    val liquidation = bounds.liquidationPrice?.takeIf { it.signum() > 0 }
    val nearLiquidation = stopLoss != null && liquidation != null &&
        stopLoss.subtract(liquidation).abs() < entryPrice.subtract(liquidation).abs().multiply(NEAR_LIQUIDATION_SHARE)
    val stopLossWarning = if (stopLossError == null && nearLiquidation) {
        stringResource(
            Localization.perps_auto_close_sl_near_liquidation,
            formatUsdPrice(liquidation.toDouble(), priceDecimals),
        )
    } else {
        null
    }

    // Clearing both fields is how an existing draft is removed, so Set stays live for one.
    val canSet = (takeProfitPrice.isNotEmpty() || stopLossPrice.isNotEmpty() || initial != null) &&
        check.isValid &&
        !takeProfitUnplaceable &&
        !stopLossUnplaceable &&
        !submitting

    MoonTopAppBarSimple(
        title = stringResource(Localization.perps_auto_close_title),
        subtitle = if (limitPrice != null) {
            stringResource(
                Localization.perps_auto_close_limit_price,
                formatUsdPrice(limitPrice.toDouble(), priceDecimals),
            )
        } else {
            stringResource(
                Localization.perps_order_current_price,
                formatUsd(bounds.currentPrice),
            )
        },
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
    )

    MoonSurface {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
        ) {
            MoonBundleTitleCell(stringResource(Localization.perps_auto_close_take_profit_when))
            AmountFieldsRow(
                percent = profitPercent,
                onPercentChange = { value ->
                    profitPercent = limitFractionDigits(sanitizeAmount(value), PERCENT_DISPLAY_SCALE)
                    profitByPercent = true
                    if (profitPercent.isEmpty()) {
                        profitSign = TAKE_PROFIT_SIGN
                    }
                    takeProfitPrice = typedRoi(profitPercent, profitSign)
                        ?.let { roiPriceText(entryPrice, it, leverage, priceDecimals, side) }
                        .orEmpty()
                },
                percentHint = stringResource(percentHintRes(profitSign)),
                price = takeProfitPrice,
                onPriceChange = { value ->
                    takeProfitPrice = limitFractionDigits(sanitizeAmount(value), priceDecimals)
                    profitByPercent = false
                    val roi = takeProfitPrice.toBigDecimalOrNull()?.let { roiFromPrice(entryPrice, it, leverage, side) }
                    profitSign = roiSign(roi, TAKE_PROFIT_SIGN)
                    profitPercent = roi?.let { formatPlainDecimal(it.abs()) }.orEmpty()
                },
                enabled = !submitting,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PROFIT_PRESETS.forEach { percent ->
                    PercentChip(
                        text = stringResource(
                            Localization.perps_auto_close_profit_percent_preset,
                            percent,
                        ),
                        isSelected = profitSign == TAKE_PROFIT_SIGN && profitPercent.isPercent(percent),
                        enabled = !submitting,
                    ) {
                        profitPercent = percent.toString()
                        profitSign = TAKE_PROFIT_SIGN
                        profitByPercent = true
                        takeProfitPrice = roiPriceText(entryPrice, BigDecimal(percent), leverage, priceDecimals, side)
                    }
                }
            }

            if (takeProfitError == null) {
                AutoCloseEstimateRow(
                    estimate = estimate,
                    target = takeProfit,
                    isLong = isLong,
                    emptyRes = Localization.perps_auto_close_profit_empty,
                )
            } else {
                AutoCloseErrorRow(takeProfitError)
            }

            Spacer(Modifier.height(8.dp))

            MoonBundleTitleCell(stringResource(Localization.perps_auto_close_stop_loss_when))
            AmountFieldsRow(
                percent = lossPercent,
                onPercentChange = { value ->
                    lossPercent = limitFractionDigits(sanitizeAmount(value), PERCENT_DISPLAY_SCALE)
                    lossByPercent = true
                    if (lossPercent.isEmpty()) {
                        lossSign = STOP_LOSS_SIGN
                    }
                    stopLossPrice = typedRoi(lossPercent, lossSign)
                        ?.let { roiPriceText(entryPrice, it, leverage, priceDecimals, side) }
                        .orEmpty()
                },
                percentHint = stringResource(percentHintRes(lossSign)),
                price = stopLossPrice,
                onPriceChange = { value ->
                    stopLossPrice = limitFractionDigits(sanitizeAmount(value), priceDecimals)
                    lossByPercent = false
                    val roi = stopLossPrice.toBigDecimalOrNull()?.let { roiFromPrice(entryPrice, it, leverage, side) }
                    lossSign = roiSign(roi, STOP_LOSS_SIGN)
                    lossPercent = roi?.let { formatPlainDecimal(it.abs()) }.orEmpty()
                },
                enabled = !submitting,
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LOSS_PRESETS.forEach { percent ->
                    PercentChip(
                        text = stringResource(
                            Localization.perps_auto_close_loss_percent_preset,
                            percent,
                        ),
                        isSelected = lossSign == STOP_LOSS_SIGN && lossPercent.isPercent(percent),
                        enabled = !submitting,
                    ) {
                        lossPercent = percent.toString()
                        lossSign = STOP_LOSS_SIGN
                        lossByPercent = true
                        stopLossPrice = roiPriceText(entryPrice, BigDecimal(-percent), leverage, priceDecimals, side)
                    }
                }
            }

            if (stopLossError != null) {
                AutoCloseErrorRow(stopLossError)
            } else if (stopLossWarning != null) {
                AutoCloseErrorRow(stopLossWarning)
            } else {
                AutoCloseEstimateRow(
                    estimate = estimate,
                    target = stopLoss,
                    isLong = isLong,
                    emptyRes = Localization.perps_auto_close_loss_empty,
                )
            }

            if (submitError != null && submitError.field == null) {
                AutoCloseErrorRow(stringResource(submitError.text))
            }

            Spacer(Modifier.height(16.dp))

            MoonBottomButtonCell(
                text = stringResource(Localization.perps_order_set),
                enabled = canSet,
                loading = submitting,
                onClick = {
                    submittedDraft = draft
                    onSet(draft)
                },
            )
        }
    }
}

@Composable
private fun AutoCloseEstimateRow(
    estimate: PerpsAutoCloseEstimate?,
    target: BigDecimal?,
    isLong: Boolean,
    @StringRes emptyRes: Int,
) {
    val pnl = if (estimate != null && target != null) {
        legPnl(estimate, target, isLong)
    } else {
        null
    }
    val text = when (pnl?.signum()) {
        1 -> stringResource(Localization.perps_auto_close_expected_profit, formatSignedUsdAmount(pnl))
        -1 -> stringResource(Localization.perps_auto_close_expected_loss, formatSignedUsdAmount(pnl))
        else -> stringResource(emptyRes)
    }

    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp),
        text = text,
        style = UIKit.typography.body3,
        color = UIKit.colorScheme.text.secondary,
    )
}

@Composable
private fun AutoCloseErrorRow(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp),
        text = text,
        style = UIKit.typography.body3,
        color = UIKit.colorScheme.accent.red,
    )
}

@Composable
private fun AmountFieldsRow(
    percent: String,
    onPercentChange: (String) -> Unit,
    percentHint: String,
    price: String,
    onPriceChange: (String) -> Unit,
    enabled: Boolean,
) {
    // MoonTextFieldCell pads itself by 16 dp per side, so -24 dp of arrangement space leaves the
    // designed 8 dp gap between the two fields and 16 dp margins against the screen.
    Row(horizontalArrangement = Arrangement.spacedBy((-24).dp)) {
        MoonTextFieldCell(
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            value = percent,
            onValueChange = onPercentChange,
            hint = percentHint,
            enabled = enabled,
            singleLine = true,
            disableClearButton = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        MoonTextFieldCell(
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            value = price,
            onValueChange = onPriceChange,
            hint = stringResource(Localization.perps_auto_close_price_hint),
            enabled = enabled,
            singleLine = true,
            disableClearButton = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}

@Composable
private fun RowScope.PercentChip(
    text: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    MoonOutlineChip(
        text = text,
        modifier = Modifier.weight(1f),
        isSelected = isSelected,
        onClick = {
            if (enabled) {
                onClick()
            }
        },
    )
}

private fun String.isPercent(percent: Int): Boolean {
    return toBigDecimalOrNull()?.compareTo(BigDecimal(percent)) == 0
}

internal fun legPnl(
    estimate: PerpsAutoCloseEstimate,
    target: BigDecimal,
    isLong: Boolean,
): BigDecimal {
    val delta = target.subtract(estimate.referencePrice).multiply(estimate.sizeBase)

    return if (isLong) {
        delta
    } else {
        delta.negate()
    }
}

@StringRes
private fun percentHintRes(roiSign: Int): Int {
    return if (roiSign < 0) {
        Localization.perps_auto_close_loss_percent_hint
    } else {
        Localization.perps_auto_close_profit_percent_hint
    }
}

internal fun roiSign(roi: BigDecimal?, defaultSign: Int): Int {
    val sign = roi?.signum() ?: 0
    return if (sign == 0) {
        defaultSign
    } else {
        sign
    }
}

private fun legRoi(
    price: BigDecimal?,
    typedRoi: BigDecimal?,
    entryPrice: BigDecimal,
    leverage: BigDecimal,
    side: PerpsOrderSide,
): BigDecimal? {
    return typedRoi ?: price?.let { roiFromPrice(entryPrice, it, leverage, side) }
}

private fun typedRoi(percent: String, roiSign: Int): BigDecimal? {
    return percent.toBigDecimalOrNull()?.multiply(BigDecimal(roiSign))
}

internal fun PerpsAutoCloseDraft.repriced(
    entryPrice: BigDecimal,
    leverage: BigDecimal,
    priceDecimals: Int,
    side: PerpsOrderSide,
): PerpsAutoCloseDraft {
    if (takeProfitRoi == null && stopLossRoi == null) {
        return this
    }
    if (entryPrice.signum() <= 0 || leverage.signum() <= 0) {
        return this
    }

    return copy(
        takeProfit = takeProfitRoi?.let { priceFromRoi(entryPrice, it, leverage, priceDecimals, side) } ?: takeProfit,
        stopLoss = stopLossRoi?.let { priceFromRoi(entryPrice, it, leverage, priceDecimals, side) } ?: stopLoss,
    )
}

internal fun PerpsAutoCloseDraft.hasUnplaceableRoi(
    entryPrice: BigDecimal,
    leverage: BigDecimal,
    priceDecimals: Int,
    side: PerpsOrderSide,
): Boolean {
    if (entryPrice.signum() <= 0 || leverage.signum() <= 0) {
        return false
    }
    return listOfNotNull(takeProfitRoi, stopLossRoi).any {
        priceFromRoi(entryPrice, it, leverage, priceDecimals, side) == null
    }
}

private fun roiPriceText(
    entryPrice: BigDecimal,
    roi: BigDecimal,
    leverage: BigDecimal,
    priceDecimals: Int,
    side: PerpsOrderSide,
): String {
    return priceFromRoi(entryPrice, roi, leverage, priceDecimals, side)?.let { formatPlainDecimal(it) }.orEmpty()
}

private fun priceFromRoi(
    entryPrice: BigDecimal,
    roi: BigDecimal,
    leverage: BigDecimal,
    priceDecimals: Int,
    side: PerpsOrderSide,
): BigDecimal? {
    return priceFromPercent(entryPrice, roi.abs(), roi.signum(), leverage, priceDecimals, side)
        .takeIf { it.signum() > 0 }
}

internal fun priceFromPercent(
    entryPrice: BigDecimal,
    percent: BigDecimal,
    roiSign: Int,
    leverage: BigDecimal,
    priceDecimals: Int,
    side: PerpsOrderSide,
): BigDecimal {
    val fraction = percent.divide(HUNDRED.multiply(leverage), PERCENT_SCALE, RoundingMode.HALF_UP)
    val above = (roiSign > 0) == (side == PerpsOrderSide.LONG)
    val factor = if (above) {
        BigDecimal.ONE.add(fraction)
    } else {
        BigDecimal.ONE.subtract(fraction)
    }
    return entryPrice.multiply(factor)
        .setScale(priceDecimals, RoundingMode.HALF_UP)
        .max(BigDecimal.ZERO)
}

internal fun roiFromPrice(
    entryPrice: BigDecimal,
    price: BigDecimal,
    leverage: BigDecimal,
    side: PerpsOrderSide,
): BigDecimal {
    if (entryPrice.signum() <= 0) {
        return BigDecimal.ZERO
    }
    val roi = price.subtract(entryPrice).multiply(leverage).multiply(HUNDRED)
        .divide(entryPrice, PERCENT_SCALE, RoundingMode.HALF_UP)
        .setScale(PERCENT_DISPLAY_SCALE, RoundingMode.DOWN)
    return if (side == PerpsOrderSide.LONG) {
        roi
    } else {
        roi.negate()
    }
}

internal const val TAKE_PROFIT_SIGN = 1
internal const val STOP_LOSS_SIGN = -1
private val HUNDRED = BigDecimal(100)
private val NEAR_LIQUIDATION_SHARE = BigDecimal("0.1")
private const val PERCENT_SCALE = 10
private const val PERCENT_DISPLAY_SCALE = 2
private val PROFIT_PRESETS = listOf(10, 20, 50, 100)
private val LOSS_PRESETS = listOf(5, 10, 25, 50)

@Preview
@Composable
private fun PerpsAutoCloseDialogPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAutoCloseDialogBody(
            limitPrice = null,
            priceDecimals = 2,
            side = PerpsOrderSide.LONG,
            bounds = PerpsAutoCloseBounds(
                entryPrice = BigDecimal("66141.70"),
                leverage = BigDecimal(20),
                currentPrice = BigDecimal("66141.70"),
                liquidationPrice = BigDecimal("63231.45"),
            ),
            initial = null,
            estimate = PerpsAutoCloseEstimate(
                sizeBase = BigDecimal("0.00781"),
                referencePrice = BigDecimal("66141.70"),
            ),
            submit = null,
            onSet = {},
            onClose = {},
        )
    }
}
