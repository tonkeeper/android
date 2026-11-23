package com.tonapps.wallet.features.events.data

import com.tonapps.wallet.data.multichain.asset.AssetEntity
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerpsDepositOriginTest {

    @Test
    fun aDirectDepositIsFoundByItsTransactionHash() {
        val records = listOf(record(transactions = setOf(DIRECT_HASH), expectedCredit = null, origin = USDC_ORIGIN))

        assertEquals(USDC_ORIGIN, records.originOf(deposit(txIds = listOf("lighter:0x$DIRECT_HASH"))))
    }

    @Test
    fun anAggregatorDepositIsFoundByTheCreditedAmountSoonAfterItWasSent() {
        val records = listOf(
            record(expectedCredit = BigDecimal("1.476995"), createdAt = CREDITED_AT - HOUR_MS, origin = USDC_ORIGIN),
            record(expectedCredit = BigDecimal("4.969993"), createdAt = CREDITED_AT - MINUTE_MS, origin = GRAM_ORIGIN),
        )

        assertEquals(GRAM_ORIGIN, records.originOf(deposit(inAmount = "4969993")))
    }

    @Test
    fun aBridgeFeeTakenFromTheQuotedAmountStillFindsTheDeposit() {
        val records = listOf(record(expectedCredit = BigDecimal("5"), createdAt = CREDITED_AT - MINUTE_MS, origin = USDC_ORIGIN))

        assertEquals(USDC_ORIGIN, records.originOf(deposit(inAmount = "4998000")))
    }

    @Test
    fun theSameAmountSentTwiceIsTakenFromTheClosestDeposit() {
        val records = listOf(
            record(expectedCredit = BigDecimal("4.969993"), createdAt = CREDITED_AT - 30 * MINUTE_MS, origin = USDC_ORIGIN),
            record(expectedCredit = BigDecimal("4.969993"), createdAt = CREDITED_AT - MINUTE_MS, origin = GRAM_ORIGIN),
        )

        assertEquals(GRAM_ORIGIN, records.originOf(deposit(inAmount = "4969993")))
    }

    @Test
    fun aDepositSentAfterTheCreditOrLongBeforeItIsNotItsOrigin() {
        val late = listOf(record(expectedCredit = BigDecimal("4.969993"), createdAt = CREDITED_AT + HOUR_MS))
        val early = listOf(record(expectedCredit = BigDecimal("4.969993"), createdAt = CREDITED_AT - 2 * HOUR_MS))

        assertNull(late.originOf(deposit(inAmount = "4969993")))
        assertNull(early.originOf(deposit(inAmount = "4969993")))
    }

    @Test
    fun aDirectDepositIsNeverTakenByItsAmountForAnotherOne() {
        val records = listOf(record(expectedCredit = BigDecimal("5"), viaAggregator = false))

        assertNull(records.originOf(deposit(inAmount = "5000000")))
    }

    @Test
    fun anotherAmountOrAWithdrawalHasNoOrigin() {
        val records = listOf(record(expectedCredit = BigDecimal("4.969993"), createdAt = CREDITED_AT - MINUTE_MS))

        assertNull(records.originOf(deposit(inAmount = "3000000")))
        assertNull(records.originOf(deposit(inAmount = "4969993", activityType = ActivityType.perpsPeriodBalance_withdrawal)))
    }

    private fun record(
        transactions: Set<String> = emptySet(),
        expectedCredit: BigDecimal?,
        createdAt: Long = CREDITED_AT - MINUTE_MS,
        origin: PerpsDepositOrigin = GRAM_ORIGIN,
        viaAggregator: Boolean = true,
    ) = PerpsDepositOriginRecord(
        transactions = transactions,
        expectedCredit = expectedCredit,
        viaAggregator = viaAggregator,
        createdAt = createdAt,
        origin = origin,
    )

    private fun deposit(
        inAmount: String = "1000000",
        txIds: List<String> = emptyList(),
        activityType: ActivityType = ActivityType.perpsPeriodBalance_deposit,
    ) = HistoryEventEntity(
        activityType = activityType,
        status = ActivityStatus.confirmed,
        blockTime = OffsetDateTime.parse(CREDITED_AT_TEXT),
        walletAddress = null,
        fromAddress = null,
        toAddress = null,
        fromChain = null,
        toChain = null,
        inAmount = inAmount,
        outAmount = null,
        inAmountUsd = null,
        outAmountUsd = null,
        feeAmount = null,
        feeAmountUsd = null,
        inAsset = LIGHTER_USDC,
        outAsset = null,
        feeAsset = null,
        txIds = txIds,
        blockNumber = null,
        protocol = "lighter",
        explorerUrl = null,
        comment = null,
        direction = ActivityDirection.self,
        perps = PerpsMetaEntity(symbol = null, side = null, reason = null),
    )

    private companion object {
        const val CREDITED_AT_TEXT = "2026-10-28T18:04:09Z"
        val CREDITED_AT = OffsetDateTime.parse(CREDITED_AT_TEXT).toInstant().toEpochMilli()
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
        const val DIRECT_HASH = "6c25517e37018fc8494c444b7f27fc4e3da2d1d1ad88f31ebe46dc0f3a83ea02"
        val LIGHTER_USDC = AssetEntity(
            id = "lighter/mainnet/3",
            name = "USDC",
            symbol = "USDC",
            decimals = 6,
            imageUrl = "",
        )
        val USDC_ORIGIN = PerpsDepositOrigin(
            asset = AssetEntity(
                id = "eth/mainnet/erc20/0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                name = "USDC",
                symbol = "USDC",
                decimals = 6,
                imageUrl = "",
            ),
            amount = BigDecimal("1.5"),
        )
        val GRAM_ORIGIN = PerpsDepositOrigin(
            asset = AssetEntity(id = "ton/mainnet/coin", name = "GRAM", symbol = "GRAM", decimals = 9, imageUrl = ""),
            amount = BigDecimal("3.5"),
        )
    }
}
