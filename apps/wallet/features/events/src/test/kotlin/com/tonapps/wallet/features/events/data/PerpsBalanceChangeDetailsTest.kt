package com.tonapps.wallet.features.events.data

import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.features.events.isPerpsAmountMuted
import com.tonapps.wallet.features.events.perpsBalanceChangeTitleRes
import com.tonapps.wallet.features.events.screens.details.perpsFundingTransactionId
import com.tonapps.wallet.localization.Localization
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import io.walletapi.models.Chain
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsBalanceChangeDetailsTest {

    @Test
    fun aWithdrawalReceivesTheIncomingTokenOnTopOfTheDebitedLeg() {
        val received = withdrawal(inAsset = GRAM, inAmount = "726118840").perpsReceived

        assertEquals(BigDecimal("0.72611884"), received?.value?.value?.stripTrailingZeros())
        assertEquals("GRAM", received?.symbol)
        assertTrue(received?.isPositive == true)
        assertFalse(received?.isDollar == true)
    }

    @Test
    fun aWithdrawalDebitsItsAmountBecauseTheFeeIsAlreadyInIt() {
        val event = withdrawal(inAsset = ARB_USDC, inAmount = "976253", feeAmount = "23741", feeAsset = LIGHTER_USDC)

        assertEquals(BigDecimal("1"), event.perpsResult?.value?.value?.stripTrailingZeros())
        assertFalse(event.perpsResult?.isPositive == true)
        assertEquals(BigDecimal("0.023741"), event.perpsFee?.value?.value?.stripTrailingZeros())
        assertEquals(BigDecimal("0.976253"), event.perpsReceived?.value?.value?.stripTrailingZeros())
    }

    @Test
    fun aReceivedStablecoinIsADollarOnlyForUsdc() {
        assertTrue(withdrawal(inAsset = ARB_USDC, inAmount = "1975942").perpsReceived?.isDollar == true)
        assertFalse(withdrawal(inAsset = ETH_USDT, inAmount = "952761").perpsReceived?.isDollar == true)
    }

    @Test
    fun nothingIsReceivedWithoutTheBackendsIncomingLeg() {
        assertNull(withdrawal(inAsset = null, inAmount = null).perpsReceived)
        assertNull(withdrawal(inAsset = GRAM, inAmount = null).perpsReceived)
        assertNull(withdrawal(inAsset = GRAM, inAmount = "726118840", outAmount = null).perpsReceived)
    }

    @Test
    fun aDepositReceivesNothingBecauseItsIncomingLegIsThePerpsBalance() {
        assertNull(deposit().perpsReceived)
    }

    @Test
    fun aSettledFundingIsDoneAndAnUnsettledOneIsFailedOrDropped() {
        assertEquals(PerpsBalanceChangeStatus.Done, deposit(ActivityStatus.confirmed).perpsBalanceChangeStatus)
        assertEquals(PerpsBalanceChangeStatus.Failed, withdrawal(ActivityStatus.failed).perpsBalanceChangeStatus)
        assertEquals(PerpsBalanceChangeStatus.Dropped, deposit(ActivityStatus.dropped).perpsBalanceChangeStatus)
        assertNull(deposit(ActivityStatus.pending).perpsBalanceChangeStatus)
    }

    @Test
    fun aPositionEventHasNoFundingStatusTitleOrReceivedToken() {
        val closed = event(ActivityType.perpsPeriodPosition_closed, inAsset = GRAM, inAmount = "10250000")

        assertNull(closed.perpsBalanceChangeStatus)
        assertNull(closed.perpsBalanceChangeTitleRes())
        assertNull(closed.perpsReceivedAsset)
    }

    @Test
    fun aSettledFundingIsNamedAsDoneAndAnUnsettledOneByTheOperation() {
        assertEquals(Localization.history_perps_deposited, deposit(ActivityStatus.confirmed).perpsBalanceChangeTitleRes())
        assertEquals(Localization.history_perps_withdrawn, withdrawal(ActivityStatus.confirmed).perpsBalanceChangeTitleRes())
        assertEquals(Localization.history_perps_deposit, deposit(ActivityStatus.dropped).perpsBalanceChangeTitleRes())
        assertEquals(Localization.history_perps_deposit, deposit(ActivityStatus.pending).perpsBalanceChangeTitleRes())
        assertEquals(Localization.history_perps_withdrawal, withdrawal(ActivityStatus.failed).perpsBalanceChangeTitleRes())
    }

    @Test
    fun aDepositAmountIsMutedUntilItIsCreditedAndAWithdrawalOnlyWhenItFails() {
        assertTrue(deposit(ActivityStatus.pending).isPerpsAmountMuted())
        assertTrue(deposit(ActivityStatus.failed).isPerpsAmountMuted())
        assertTrue(deposit(ActivityStatus.dropped).isPerpsAmountMuted())
        assertTrue(deposit(ActivityStatus.unknown).isPerpsAmountMuted())
        assertFalse(deposit(ActivityStatus.confirmed).isPerpsAmountMuted())
        assertFalse(withdrawal(ActivityStatus.pending).isPerpsAmountMuted())
        assertFalse(withdrawal(ActivityStatus.confirmed).isPerpsAmountMuted())
        assertFalse(withdrawal(ActivityStatus.unknown).isPerpsAmountMuted())
        assertTrue(withdrawal(ActivityStatus.failed).isPerpsAmountMuted())
        assertTrue(withdrawal(ActivityStatus.dropped).isPerpsAmountMuted())
    }

    @Test
    fun aPositionEventAmountIsNeverMuted() {
        val failedClose = event(
            ActivityType.perpsPeriodPosition_closed,
            status = ActivityStatus.failed,
            inAsset = GRAM,
            inAmount = "10250000",
        )

        assertFalse(failedClose.isPerpsAmountMuted())
    }

    @Test
    fun aWithdrawalIsPicturedByTheTokenItPaidOut() {
        assertEquals(GRAM, withdrawal(inAsset = GRAM).perpsReceivedAsset)
        assertEquals(ETH_USDT, withdrawal(inAsset = ETH_USDT, inAmount = "952761").perpsReceivedAsset)
    }

    @Test
    fun aFundingWithoutAPaidOutTokenHasNoReceivedToken() {
        assertNull(deposit().perpsReceivedAsset)
        assertNull(deposit(inAsset = ARB_USDC).perpsReceivedAsset)
        assertNull(withdrawal(inAsset = null, inAmount = null).perpsReceivedAsset)
        assertNull(withdrawal(inAsset = LIGHTER_USDC, inAmount = "1000000").perpsReceivedAsset)
    }

    @Test
    fun aDepositShowsItsLighterTransactionHash() {
        val deposit = deposit().copy(txIds = listOf("lighter:0xffa77ca60ca81f2b"))

        assertEquals("0xffa77ca60ca81f2b", deposit.perpsFundingTransactionId())
    }

    @Test
    fun aWithdrawalShowsThePayoutTransactionOnItsNetwork() {
        val secure = withdrawal(inAsset = null, inAmount = null).copy(
            toChain = Chain.eth,
            txIds = listOf("lighter:0xf8cf94cfd56e43c2", "eth:0xc28f3e797a6c5f31"),
        )
        val toTon = withdrawal().copy(toChain = Chain.ton, txIds = listOf("ton:82ac91191c69b7aa"))

        assertEquals("0xc28f3e797a6c5f31", secure.perpsFundingTransactionId())
        assertEquals("82ac91191c69b7aa", toTon.perpsFundingTransactionId())
        assertNull(deposit(ActivityStatus.dropped).perpsFundingTransactionId())
    }

    private fun deposit(
        status: ActivityStatus = ActivityStatus.confirmed,
        inAsset: AssetEntity = LIGHTER_USDC,
    ) = event(
        activityType = ActivityType.perpsPeriodBalance_deposit,
        status = status,
        inAsset = inAsset,
        inAmount = "2005752",
    )

    private fun withdrawal(
        status: ActivityStatus = ActivityStatus.confirmed,
        inAsset: AssetEntity? = GRAM,
        inAmount: String? = "726118840",
        outAmount: String? = "1000000",
        feeAmount: String? = null,
        feeAsset: AssetEntity? = null,
    ) = event(
        activityType = ActivityType.perpsPeriodBalance_withdrawal,
        status = status,
        inAsset = inAsset,
        inAmount = inAmount,
        outAsset = LIGHTER_USDC,
        outAmount = outAmount,
        feeAmount = feeAmount,
        feeAsset = feeAsset,
    )

    private fun event(
        activityType: ActivityType,
        status: ActivityStatus = ActivityStatus.confirmed,
        inAsset: AssetEntity? = null,
        inAmount: String? = null,
        outAsset: AssetEntity? = null,
        outAmount: String? = null,
        feeAmount: String? = null,
        feeAsset: AssetEntity? = null,
    ) = HistoryEventEntity(
        activityType = activityType,
        status = status,
        blockTime = OffsetDateTime.parse("2026-10-19T14:48:30Z"),
        walletAddress = null,
        fromAddress = null,
        toAddress = null,
        fromChain = null,
        toChain = null,
        inAmount = inAmount,
        outAmount = outAmount,
        inAmountUsd = null,
        outAmountUsd = null,
        feeAmount = feeAmount,
        feeAmountUsd = null,
        inAsset = inAsset,
        outAsset = outAsset,
        feeAsset = feeAsset,
        txIds = emptyList(),
        blockNumber = null,
        protocol = "lighter",
        explorerUrl = null,
        comment = null,
        direction = ActivityDirection.self,
        perps = PerpsMetaEntity(symbol = null, side = null, reason = null),
    )

    private companion object {
        val LIGHTER_USDC = AssetEntity(
            id = "lighter/mainnet/3",
            name = "USDC",
            symbol = "USDC",
            decimals = 6,
            imageUrl = "",
        )
        val ARB_USDC = AssetEntity(
            id = "arb/mainnet/erc20/0xaf88d065e77c8cc2239327c5edb3a432268e5831",
            name = "USDC",
            symbol = "USDC",
            decimals = 6,
            imageUrl = "https://example.com/usdc.png",
        )
        val ETH_USDT = AssetEntity(
            id = "eth/mainnet/erc20/0xdac17f958d2ee523a2206206994597c13d831ec7",
            name = "Tether USD",
            symbol = "USDT",
            decimals = 6,
            imageUrl = "https://example.com/usdt.png",
        )
        val GRAM = AssetEntity(
            id = "ton/mainnet/coin",
            name = "GRAM",
            symbol = "GRAM",
            decimals = 9,
            imageUrl = "https://example.com/gram.png",
        )
    }
}
