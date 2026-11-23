package com.tonapps.wallet.data.multichain.exchange.status

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.wallet.data.multichain.activity.TronResourceEntity
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import io.JsonAny
import io.walletapi.models.Activity
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityExplorerLink
import io.walletapi.models.ActivityFee
import io.walletapi.models.ActivityFeeType
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import io.walletapi.models.AssetInfo
import io.walletapi.models.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class SwapDetailsTest {

    private val swap = SentSwap(
        walletId = "wallet",
        txHash = "0xabc",
        sell = TestAssets.eth,
        sendAmount = BigInteger.parseString("1000000000000000000"),
        buy = TestAssets.btc,
        receiveAmount = BigInteger.parseString("3000000"),
    )

    @Test
    fun aCrossChainSwapLinksBothSides() {
        val details = details(SwapStatus.Done, activity(links = listOf(ETH_LINK, BTC_LINK)))

        assertEquals(ETH_LINK, details.sent)
        assertEquals(BTC_LINK, details.received)
        assertFalse(details.receiving)
    }

    @Test
    fun aCrossChainSwapWithOnlyTheSourceLinkIsReceiving() {
        val details = details(SwapStatus.Pending, activity(links = listOf(ETH_LINK)))

        assertEquals(ETH_LINK, details.sent)
        assertNull(details.received)
        assertTrue(details.receiving)
    }

    @Test
    fun aFailedSwapHasNoReceivedLink() {
        val details = details(SwapStatus.Failed, activity(links = listOf(ETH_LINK, BTC_LINK)))

        assertEquals(ETH_LINK, details.sent)
        assertNull(details.received)
        assertFalse(details.receiving)
    }

    @Test
    fun aDoneSwapShowsTheAmountActuallyReceived() {
        val details = details(SwapStatus.Done, activity(inToken = TestAssets.btc, inAmount = "+2500000"))

        assertEquals(amount(TestAssets.btc, "2500000"), details.receiveAmount)
    }

    @Test
    fun anotherReceivedTokenKeepsTheQuotedAmount() {
        val details = details(SwapStatus.Done, activity(inToken = TestAssets.usdtEth, inAmount = "2500000"))

        assertEquals(amount(TestAssets.btc, "3000000"), details.receiveAmount)
    }

    @Test
    fun aBatteryFeeIsCountedInCharges() {
        val details = details(
            SwapStatus.Done,
            activity(fee = ActivityFee(type = ActivityFeeType.battery, amount = "7")),
        )

        assertEquals(SwapNetworkFee.Charges(7), details.networkFee)
    }

    @Test
    fun aCoinFeeIsFormattedInItsToken() {
        val details = details(
            SwapStatus.Done,
            activity(feeType = ActivityFeeType.native, feeToken = TestAssets.eth, feeAmount = "30000000000000000"),
        )

        assertEquals(SwapNetworkFee.Amount(amount(TestAssets.eth, "30000000000000000")), details.networkFee)
    }

    @Test
    fun hiddenBalancesMaskTheCharges() {
        val details = swapDetails(
            swap,
            SwapStatus.Done,
            activity(fee = ActivityFee(type = ActivityFeeType.battery, amount = "7")),
            sentAt = 0,
            hidden = true,
        )

        assertEquals(SwapNetworkFee.Amount(HIDDEN_BALANCE), details.networkFee)
    }

    @Test
    fun aTronFeePaidWithResourcesShowsTheResources() {
        val details = details(
            SwapStatus.Done,
            activity(
                feeType = ActivityFeeType.native,
                feeToken = TestAssets.trx,
                feeAmount = "0",
                meta = mapOf("tron_resource" to JsonAny.Object(mapOf("energy" to JsonAny.Primitive(65000L)))),
            ),
        )

        assertEquals(SwapNetworkFee.TronResource(TronResourceEntity(energy = 65000, bandwidth = 0)), details.networkFee)
    }

    @Test
    fun aSwapNotInHistoryHasNoFee() {
        val details = swapDetails(swap, SwapStatus.Pending, activity = null, sentAt = 0, hidden = false)

        assertNull(details.networkFee)
    }

    @Test
    fun anEntryWithoutAFeeHasNoFee() {
        val details = details(SwapStatus.Done, activity())

        assertNull(details.networkFee)
    }

    private fun details(status: SwapStatus, activity: Activity) =
        swapDetails(swap, status, activity, sentAt = 0, hidden = false)

    private fun amount(asset: AssetEntity, value: String) =
        formatSwapAmount(asset, BigInteger.parseString(value), hidden = false)

    private fun activity(
        links: List<ActivityExplorerLink> = emptyList(),
        inToken: AssetEntity? = null,
        inAmount: String? = null,
        fee: ActivityFee? = null,
        feeType: ActivityFeeType? = null,
        feeToken: AssetEntity? = null,
        feeAmount: String? = null,
        meta: Map<String, JsonAny>? = null,
    ) = Activity(
        activityType = ActivityType.bridge.value,
        status = ActivityStatus.confirmed,
        blockTime = OffsetDateTime.now(),
        fromChain = "eth",
        toChain = "btc",
        direction = ActivityDirection.out,
        txIds = emptyList(),
        inToken = inToken?.toAssetInfo(),
        inAmount = inAmount,
        explorerUrls = links,
        fee = fee,
        feeType = feeType,
        feeToken = feeToken?.toAssetInfo(),
        feeAmount = feeAmount,
        meta = meta,
    )

    private fun AssetEntity.toAssetInfo() = AssetInfo(
        assetId = id,
        name = name,
        symbol = symbol,
        decimals = decimals,
        image = "",
        verification = AssetInfo.Verification.none,
    )

    private companion object {
        val ETH_LINK = ActivityExplorerLink(txId = "0xabc", chain = Chain.eth, url = "https://etherscan.io/tx/0xabc")
        val BTC_LINK = ActivityExplorerLink(txId = "def", chain = Chain.btc, url = "https://mempool.space/tx/def")
    }
}
