package com.tonapps.wallet.features.events.data

import com.tonapps.wallet.data.multichain.asset.AssetEntity
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import io.walletapi.models.Chain
import java.time.OffsetDateTime
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OpenedHistoryEventTest {

    @Test
    fun aPendingSwapIsReplacedByTheRecordThatSharesItsTransaction() {
        val pending = swap(ActivityStatus.pending, txIds = listOf(EVM_TX))
        val settled = swap(ActivityStatus.confirmed, txIds = listOf(EVM_TX, BSC_TX), inAmount = BSC_RECEIVED)
        val history = listOf(send(txIds = listOf("ton:aa01")), settled, send(txIds = listOf("ton:aa02")))

        assertSame(settled, history.latestVersionOf(pending))
    }

    @Test
    fun aPendingTonSwapIsReplacedByTheIndexedRecordUnderAnotherHash() {
        val pending = swap(ActivityStatus.pending, txIds = listOf(REPORTED_TON_TX), blockTime = "2026-11-03T14:27:59Z")
        val settled = swap(
            ActivityStatus.confirmed,
            txIds = listOf(INDEXED_TON_TX, BSC_TX),
            inAmount = BSC_RECEIVED,
            blockTime = "2026-11-03T14:27:43Z",
        )
        val history = listOf(send(txIds = listOf("ton:aa01")), settled, send(txIds = listOf("ton:aa02")))

        assertSame(settled, history.latestVersionOf(pending))
    }

    @Test
    fun anUnchangedActivityStaysTheSameEntry() {
        val opened = swap(ActivityStatus.pending, txIds = listOf(REPORTED_TON_TX))
        val history = listOf(send(txIds = listOf("ton:aa01")), opened.copy())

        assertSame(history[1], history.latestVersionOf(opened))
    }

    @Test
    fun aTxIdIsMatchedRegardlessOfTheChainPrefixAndCase() {
        val pending = swap(ActivityStatus.pending, txIds = listOf("0xABCDEF0123"))
        val settled = swap(ActivityStatus.confirmed, txIds = listOf("bsc:0xabcdef0123"), outAmount = "-12000000")

        assertSame(settled, listOf(settled).latestVersionOf(pending))
    }

    @Test
    fun aSwapReclassifiedAsABridgeIsTheSameOperation() {
        val pending = swap(ActivityStatus.pending, txIds = listOf(REPORTED_TON_TX))
        val settled = swap(ActivityStatus.confirmed, txIds = listOf(INDEXED_TON_TX, BSC_TX))
            .copy(activityType = ActivityType.bridge)

        assertSame(settled, listOf(settled).latestVersionOf(pending))
    }

    @Test
    fun anotherKindOfActivityIsNeverTheOpenedOne() {
        val pending = swap(ActivityStatus.pending, txIds = listOf(REPORTED_TON_TX))
        val sameTransaction = send(txIds = listOf(REPORTED_TON_TX))
        val sameMoment = send(txIds = listOf(INDEXED_TON_TX), outAmount = "12000000")

        assertNull(listOf(sameTransaction, sameMoment).latestVersionOf(pending))
    }

    @Test
    fun anActivityMissingFromTheListHasNoNewerVersion() {
        val pending = swap(ActivityStatus.pending, txIds = listOf(REPORTED_TON_TX))
        val otherSwap = swap(ActivityStatus.confirmed, txIds = listOf("ton:bb02"), outAmount = "7000000")

        assertNull(listOf(send(txIds = listOf("ton:aa01")), otherSwap).latestVersionOf(pending))
        assertNull(emptyList<HistoryEventEntity>().latestVersionOf(pending))
    }

    @Test
    fun aLookalikeSwapOutsideTheSettlementWindowIsAnotherSwap() {
        val pending = swap(ActivityStatus.pending, txIds = listOf(REPORTED_TON_TX), blockTime = "2026-11-03T14:27:59Z")
        val yesterday = swap(ActivityStatus.confirmed, txIds = listOf(INDEXED_TON_TX), blockTime = "2026-11-02T14:27:43Z")
        val earlier = swap(ActivityStatus.confirmed, txIds = listOf("ton:cc03"), blockTime = "2026-11-03T14:20:00Z")

        assertNull(listOf(yesterday, earlier).latestVersionOf(pending))
    }

    @Test
    fun twoLookalikeSwapsKeepTheOpenedVersion() {
        val pending = swap(ActivityStatus.pending, txIds = listOf(REPORTED_TON_TX))
        val first = swap(ActivityStatus.confirmed, txIds = listOf(INDEXED_TON_TX))
        val second = swap(ActivityStatus.confirmed, txIds = listOf("ton:dd04"))

        assertNull(listOf(first, second).latestVersionOf(pending))
    }

    @Test
    fun aLookalikeAlreadyInTheHistoryWhenOpenedIsNotTheReplacement() {
        val earlier = swap(ActivityStatus.confirmed, txIds = listOf("ton:aa11", BSC_TX), blockTime = "2026-11-03T14:25:00Z")
        val pending = swap(ActivityStatus.pending, txIds = listOf(REPORTED_TON_TX), blockTime = "2026-11-03T14:27:59Z")

        assertNull(listOf(earlier).latestVersionOf(pending, shownHistory = listOf(pending, earlier)))
    }

    @Test
    fun theReplacementIsFoundNextToALookalikeAlreadyInTheHistory() {
        val earlier = swap(ActivityStatus.confirmed, txIds = listOf("ton:aa11", BSC_TX), blockTime = "2026-11-03T14:25:00Z")
        val pending = swap(ActivityStatus.pending, txIds = listOf(REPORTED_TON_TX), blockTime = "2026-11-03T14:27:59Z")
        val settled = swap(ActivityStatus.confirmed, txIds = listOf(INDEXED_TON_TX), blockTime = "2026-11-03T14:27:43Z")

        assertSame(settled, listOf(settled, earlier).latestVersionOf(pending, shownHistory = listOf(pending, earlier)))
    }

    @Test
    fun aSettledActivityIsNeverMatchedByLooksAlone() {
        val opened = swap(ActivityStatus.confirmed, txIds = listOf(INDEXED_TON_TX, BSC_TX))
        val lookalike = swap(ActivityStatus.confirmed, txIds = listOf("ton:ee05"))

        assertNull(listOf(lookalike).latestVersionOf(opened))
    }

    @Test
    fun aPendingRecordWithoutAnAmountIsNotMatchedByLooks() {
        val pending = swap(ActivityStatus.pending, txIds = listOf(REPORTED_TON_TX), outAmount = null)
        val settled = swap(ActivityStatus.confirmed, txIds = listOf(INDEXED_TON_TX, BSC_TX))

        assertNull(listOf(settled).latestVersionOf(pending))
    }

    @Test
    fun aBatchedTransferIsTrackedByItsRecipient() {
        val opened = send(txIds = listOf(INDEXED_TON_TX), toAddress = "UQB-second", status = ActivityStatus.pending)
        val first = send(txIds = listOf(INDEXED_TON_TX), toAddress = "UQA-first")
        val second = send(txIds = listOf(INDEXED_TON_TX), toAddress = "UQB-second")

        assertSame(second, listOf(first, second).latestVersionOf(opened))
    }

    @Test
    fun indistinguishableSiblingsKeepTheOpenedVersion() {
        val opened = send(txIds = listOf(INDEXED_TON_TX), toAddress = "UQA-first", status = ActivityStatus.pending)
        val first = send(txIds = listOf(INDEXED_TON_TX), toAddress = "UQA-first")
        val twin = first.copy(outAmount = "2000000")

        assertNull(listOf(first, twin).latestVersionOf(opened))
    }

    private fun List<HistoryEventEntity>.latestVersionOf(
        opened: HistoryEventEntity,
        shownHistory: List<HistoryEventEntity> = listOf(opened),
    ): HistoryEventEntity? = OpenedHistoryEvent(opened, shownHistory).latestVersionIn(this)

    private fun swap(
        status: ActivityStatus,
        txIds: List<String>,
        inAmount: String? = null,
        outAmount: String? = "12000000",
        blockTime: String = "2026-11-03T14:27:50Z",
    ) = event(
        activityType = ActivityType.swap,
        status = status,
        txIds = txIds,
        toChain = Chain.bsc,
        outAmount = outAmount,
        inAsset = BSC_USDT,
        inAmount = inAmount,
        blockTime = blockTime,
    )

    private fun send(
        txIds: List<String>,
        toAddress: String = "UQA-recipient",
        status: ActivityStatus = ActivityStatus.confirmed,
        outAmount: String = "1000000",
    ) = event(
        activityType = ActivityType.send,
        status = status,
        txIds = txIds,
        toChain = Chain.ton,
        outAmount = outAmount,
        toAddress = toAddress,
    )

    private fun event(
        activityType: ActivityType,
        status: ActivityStatus,
        txIds: List<String>,
        toChain: Chain,
        outAmount: String?,
        inAsset: AssetEntity? = null,
        inAmount: String? = null,
        toAddress: String? = null,
        blockTime: String = "2026-11-03T14:27:50Z",
    ) = HistoryEventEntity(
        activityType = activityType,
        status = status,
        blockTime = OffsetDateTime.parse(blockTime),
        walletAddress = "UQA-wallet",
        fromAddress = "UQA-wallet",
        toAddress = toAddress,
        fromChain = Chain.ton,
        toChain = toChain,
        inAmount = inAmount,
        outAmount = outAmount,
        inAmountUsd = null,
        outAmountUsd = null,
        feeAmount = null,
        feeAmountUsd = null,
        inAsset = inAsset,
        outAsset = TON_USDT,
        feeAsset = null,
        txIds = txIds,
        blockNumber = null,
        protocol = null,
        explorerUrl = null,
        comment = null,
        direction = ActivityDirection.self,
    )

    private companion object {
        const val REPORTED_TON_TX = "ton:ff76943fea98bd603f484b66e69a7861b81fabbf90a3b898b9f2a12d2021b215"
        const val INDEXED_TON_TX = "ton:b730c0ab6fee7547d9e9daa6fbf21d647bcbdc5f423c89bfad4782914d07c43a"
        const val BSC_TX = "bsc:0x3bed71717f806adceda2c98c5d2c12950c1eb24da791e0a74fdeb572864ebfb6"
        const val EVM_TX = "bsc:0x9ab2b3e4aae0d96fcdaa5f9ec7dd54b10149d21b14e3026bb148bc97bb44f1b0"
        const val BSC_RECEIVED = "11760130620000000000"

        val TON_USDT = AssetEntity(
            id = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe",
            name = "Tether USD",
            symbol = "USD₮",
            decimals = 6,
            imageUrl = "",
        )
        val BSC_USDT = AssetEntity(
            id = "bsc/mainnet/bep20/0x55d398326f99059ff775485246999027b3197955",
            name = "Tether USD",
            symbol = "USDT",
            decimals = 18,
            imageUrl = "",
        )
    }
}
