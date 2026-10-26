package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a fee method is routed and picked. What the fee is denominated in decides who pays the chain:
 * the chain's coin is paid by the wallet, battery charges and a same-chain token (gasless) by the
 * relayer.
 */
class TxFeeLogicTest : BaseUnitTest() {

    private val tonChain get() = TestAssets.ton.value.chain

    private val otherToken = testAsset(
        id = "ton/mainnet/jetton/0:2f956143c461769579baef2e32cc2d7bc18283f40d20bb03e432cd603ac33ffc",
        symbol = "NOT",
        decimals = 9,
    )

    // region routing

    @Test
    fun aFeeInTheChainCoinIsPaidByTheWallet() {
        val fee = native(amount = 5, balance = 10)

        assertTrue(fee.isNative)
        assertFalse(fee.viaRelayer)
        assertFalse(fee.isBattery)
        assertEquals(TestAssets.ton.id, fee.id)
    }

    @Test
    fun batteryChargesAreRelayed() {
        val fee = battery(charges = 3, balance = 10)

        assertTrue(fee.isBattery)
        assertTrue(fee.viaRelayer)
        assertFalse(fee.isNative)
        assertEquals(Asset.Battery.ID, fee.id)
    }

    @Test
    fun aGaslessFeeInTheSentTokenIsRelayed() {
        val fee = gasless(amount = 1, balance = 10)

        assertTrue(fee.viaRelayer)
        assertFalse(fee.isBattery)
        assertFalse(fee.isNative)
        assertEquals(TestAssets.usdtTon.id, fee.id)
    }

    // endregion

    // region isSufficient

    @Test
    fun aFeeWithinTheBalanceIsSufficient() {
        assertTrue(TxFeeLogic.isSufficient(native(amount = 10, balance = 10)))
        assertFalse(TxFeeLogic.isSufficient(native(amount = 11, balance = 10)))
    }

    @Test
    fun anUnknownBatteryBalanceIsNeverSufficient() {
        assertFalse(TxFeeLogic.isSufficient(battery(charges = 1, balance = null)))
    }

    @Test
    fun anUnavailableMethodCannotPayWhateverItsBalance() {
        assertFalse(TxFeeLogic.isSufficient(native(amount = 5, balance = 10).copy(available = false)))
    }

    // endregion

    // region resolve

    @Test
    fun nothingToResolveWithoutOptions() {
        assertNull(TxFeeLogic.resolve(emptyList(), pickedId = null))
    }

    @Test
    fun theFirstSufficientOptionWinsWithoutAPick() {
        val battery = battery(charges = 3, balance = 10)
        val native = native(amount = 5, balance = 10)

        assertSame(battery, TxFeeLogic.resolve(listOf(battery, native), pickedId = null))
    }

    @Test
    fun aSufficientPickWins() {
        val battery = battery(charges = 3, balance = 10)
        val native = native(amount = 5, balance = 10)

        assertSame(native, TxFeeLogic.resolve(listOf(battery, native), pickedId = native.id))
    }

    @Test
    fun anInsufficientPickFallsBackToTheChainCoin() {
        val battery = battery(charges = 30, balance = 10)
        val gasless = gasless(amount = 1, balance = 10)
        val native = native(amount = 5, balance = 10)

        // The coin is the method that always exists, so it beats another sufficient relayed method.
        assertSame(native, TxFeeLogic.resolve(listOf(battery, gasless, native), pickedId = battery.id))
    }

    @Test
    fun withoutASufficientCoinAnySufficientMethodIsTaken() {
        val battery = battery(charges = 30, balance = 10)
        val gasless = gasless(amount = 1, balance = 10)
        val native = native(amount = 50, balance = 10)

        assertSame(gasless, TxFeeLogic.resolve(listOf(battery, gasless, native), pickedId = battery.id))
    }

    @Test
    fun whenNothingIsSufficientThePickStays() {
        val battery = battery(charges = 30, balance = 10)
        val native = native(amount = 50, balance = 10)

        assertSame(native, TxFeeLogic.resolve(listOf(battery, native), pickedId = native.id))
        assertSame(battery, TxFeeLogic.resolve(listOf(battery, native), pickedId = null))
    }

    @Test
    fun anUnavailablePickHandsTheSelectionBackToTheHeadOfTheList() {
        val battery = battery(charges = 3, balance = 10)
        val native = native(amount = 5, balance = 10)
        val gasless = gasless(amount = 1, balance = 10).copy(available = false)

        assertSame(battery, TxFeeLogic.resolve(listOf(battery, native, gasless), pickedId = gasless.id))
    }

    // endregion

    // region picker

    @Test
    fun severalMethodsOpenThePicker() {
        assertTrue(TxFeeLogic.isPickerAvailable(listOf(battery(3, 10), native(5, 10))))
    }

    @Test
    fun aLoneBatteryThatCannotPayOpensThePickerToRecharge() {
        assertTrue(TxFeeLogic.isPickerAvailable(listOf(battery(charges = 30, balance = 10))))
        assertFalse(TxFeeLogic.isPickerAvailable(listOf(battery(charges = 3, balance = 10))))
    }

    @Test
    fun aLoneCoinMethodHasNoPicker() {
        assertFalse(TxFeeLogic.isPickerAvailable(listOf(native(amount = 50, balance = 10))))
    }

    @Test
    fun onlyASufficientBatteryCountsAsABatteryOption() {
        assertTrue(TxFeeState(options = listOf(battery(3, 10), native(5, 10))).hasBatteryOption)
        assertFalse(TxFeeState(options = listOf(battery(30, 10), native(5, 10))).hasBatteryOption)
        assertFalse(TxFeeState(options = listOf(gasless(1, 10), native(5, 10))).hasBatteryOption)
    }

    // endregion

    // region recharge

    @Test
    fun aBatteryShortOnChargesIsRechargeable() {
        assertTrue(TxFeeLogic.isRechargeable(battery(charges = 30, balance = 10)))
    }

    @Test
    fun aBatteryTheChargesCoverIsNotRechargeable() {
        assertFalse(TxFeeLogic.isRechargeable(battery(charges = 10, balance = 10)))
    }

    @Test
    fun unknownChargesAreNotRechargeable() {
        assertFalse(TxFeeLogic.isRechargeable(battery(charges = 30, balance = null)))
    }

    @Test
    fun aBatteryTheRelayerRulesOutIsNotRechargeable() {
        assertFalse(TxFeeLogic.isRechargeable(battery(charges = 30, balance = 10).copy(available = false)))
    }

    @Test
    fun onlyABatteryIsRechargeable() {
        assertFalse(TxFeeLogic.isRechargeable(native(amount = 50, balance = 10)))
        assertFalse(TxFeeLogic.isRechargeable(gasless(amount = 50, balance = 10)))
    }

    /** The relayer pays it but did not price it: never payable, whatever the charges. */
    @Test
    fun anUnpricedBatteryCannotPay() {
        val unpriced = battery(charges = 0, balance = 1000)

        assertTrue(unpriced.isUnpriced)
        assertFalse(TxFeeLogic.isSufficient(unpriced))
    }

    /** A recharge is what gets it priced, but only a wallet with no charges is missing one. */
    @Test
    fun anUnpricedBatteryIsRechargeableWithNoCharges() {
        assertTrue(TxFeeLogic.isRechargeable(battery(charges = 0, balance = 0)))
        assertFalse(TxFeeLogic.isRechargeable(battery(charges = 0, balance = 1000)))
        assertFalse(TxFeeLogic.isRechargeable(battery(charges = 0, balance = null)))
        assertFalse(TxFeeLogic.isRechargeable(battery(charges = 0, balance = 0).copy(available = false)))
    }

    @Test
    fun anUnpricedBatteryOpensThePicker() {
        assertTrue(TxFeeLogic.isPickerAvailable(listOf(battery(charges = 0, balance = 10))))
        assertTrue(TxFeeState(options = listOf(battery(0, 0), native(50, 10))).batteryRechargeable)
    }

    @Test
    fun onlyABatteryCanBeUnpriced() {
        assertFalse(native(amount = 0, balance = 10).isUnpriced)
        assertFalse(gasless(amount = 0, balance = 10).isUnpriced)
    }

    @Test
    fun aStateIsRechargeableByAListedOrAnUnlistedShortBattery() {
        assertTrue(TxFeeState(options = listOf(battery(30, 10), native(50, 10))).batteryRechargeable)
        assertTrue(TxFeeState(options = listOf(native(50, 10)), shortBattery = battery(30, 10)).batteryRechargeable)
        assertFalse(TxFeeState(options = listOf(battery(3, 10), native(50, 10))).batteryRechargeable)
        assertFalse(TxFeeState(options = listOf(native(50, 10))).batteryRechargeable)
        assertFalse(TxFeeState().batteryRechargeable)
    }

    // endregion

    // region priority

    @Test
    fun withoutAPreferenceBatteryLeadsThenTheCoinThenTheSentAssetThenAnyOther() {
        val battery = battery(charges = 3, balance = 10)
        val native = native(amount = 5, balance = 10)
        val sent = gasless(amount = 1, balance = 10)
        val other = gasless(amount = 1, balance = 10, asset = otherToken)

        val ordered = TxFeeLogic.prioritize(
            options = listOf(other, sent, native, battery),
            sentAssetId = TestAssets.usdtTon.id,
            preferred = PreferredFeeMethod.UNSPECIFIED,
        )

        assertEquals(listOf(battery, native, sent, other), ordered)
    }

    @Test
    fun thePreferredKindGoesFirstAndTheRestKeepTheirOrder() {
        val battery = battery(charges = 3, balance = 10)
        val native = native(amount = 5, balance = 10)
        val sent = gasless(amount = 1, balance = 10)
        val other = gasless(amount = 1, balance = 10, asset = otherToken)
        val options = listOf(other, sent, native, battery)
        val sentAssetId = TestAssets.usdtTon.id.uppercase()

        assertEquals(
            listOf(native, battery, sent, other),
            TxFeeLogic.prioritize(options, sentAssetId, PreferredFeeMethod.TON),
        )
        assertEquals(
            listOf(sent, other, battery, native),
            TxFeeLogic.prioritize(options, sentAssetId, PreferredFeeMethod.GASLESS),
        )
        assertEquals(
            listOf(battery, native, sent, other),
            TxFeeLogic.prioritize(options, sentAssetId, PreferredFeeMethod.BATTERY),
        )
    }

    @Test
    fun methodsThatCannotPayAreListedLastInTheSameOrder() {
        val battery = battery(charges = 30, balance = 10)
        val native = native(amount = 5, balance = 10)
        val sent = gasless(amount = 1, balance = 10).copy(available = false)
        val other = gasless(amount = 1, balance = 10, asset = otherToken)

        val ordered = TxFeeLogic.prioritize(
            options = listOf(other, sent, native, battery),
            sentAssetId = TestAssets.usdtTon.id,
            preferred = PreferredFeeMethod.BATTERY,
        )

        assertEquals(listOf(native, other, battery, sent), ordered)
    }

    // endregion

    // region quote

    @Test
    fun aStateWithNothingToQuoteIsLeftAsItIs() = runTest {
        val state = TxFeeState(options = listOf(battery(3, 10), native(5, 10)))

        assertSame(state, TxFeeLogic.quote(state, pickedId = null))
    }

    @Test
    fun theSelectedRelayedOptionIsQuotedAndNoOther() = runTest {
        val quotes = Quotes()
        val battery = battery(charges = 3, balance = 10)
        val sent = gasless(amount = 1, balance = 10)
        val state = TxFeeState(options = listOf(battery, sent, native(5, 10)), quote = quotes)

        val quoted = TxFeeLogic.quote(state, pickedId = sent.id)

        assertEquals(listOf(sent.id), quotes.asked)
        assertEquals(sent.id, quoted.quotedId)
        assertEquals(BigInteger.fromInt(2), quoted.findOption(sent.id)?.fee?.amount)
        assertEquals(battery, quoted.findOption(battery.id))
    }

    @Test
    fun theChainCoinIsNeverQuoted() = runTest {
        val quotes = Quotes()
        val native = native(amount = 5, balance = 10)
        val state = TxFeeState(options = listOf(battery(3, 10), native), quote = quotes)

        val quoted = TxFeeLogic.quote(state, pickedId = native.id)

        assertTrue(quotes.asked.isEmpty())
        assertSame(state, quoted)
    }

    @Test
    fun anOptionTheQuoteRulesOutGoesLastAndTheNextInLineIsQuoted() = runTest {
        val battery = battery(charges = 3, balance = 10)
        val sent = gasless(amount = 1, balance = 10)
        val other = gasless(amount = 1, balance = 10, asset = otherToken)
        val quotes = Quotes(gone = setOf(battery.id, sent.id))
        val state = TxFeeState(options = listOf(battery, sent, other), quote = quotes)

        val quoted = TxFeeLogic.quote(state, pickedId = null)

        assertEquals(listOf(battery.id, sent.id, other.id), quotes.asked)
        assertEquals(other.id, quoted.options.first().id)
        assertEquals(setOf(battery.id, sent.id), quoted.options.drop(1).map { it.id }.toSet())
        assertEquals(listOf(true, false, false), quoted.options.map { it.available })
        assertEquals(other.id, quoted.quotedId)
    }

    @Test
    fun aPickTheQuoteRulesOutHandsTheSelectionToTheHeadOfTheList() = runTest {
        val battery = battery(charges = 3, balance = 10)
        val native = native(amount = 5, balance = 10)
        val sent = gasless(amount = 1, balance = 10)
        val quotes = Quotes(gone = setOf(sent.id))
        val state = TxFeeState(options = listOf(battery, native, sent), quote = quotes)

        val quoted = TxFeeLogic.quote(state, pickedId = sent.id)

        assertEquals(listOf(sent.id, battery.id), quotes.asked)
        assertEquals(battery.id, quoted.quotedId)
        assertEquals(battery.id, quoted.resolve(sent.id)?.id)
    }

    @Test
    fun whenEveryRelayedOptionIsRuledOutTheCoinLeads() = runTest {
        val battery = battery(charges = 3, balance = 10)
        val native = native(amount = 5, balance = 10)
        val quotes = Quotes(gone = setOf(battery.id))
        val state = TxFeeState(options = listOf(battery, native), quote = quotes)

        val quoted = TxFeeLogic.quote(state, pickedId = null)

        assertEquals(listOf(native.id, battery.id), quoted.options.map { it.id })
        assertSame(native, quoted.resolve(null))
        assertNull(quoted.quotedId)
    }

    @Test
    fun aMethodThatCannotPayIsNeverQuoted() = runTest {
        val quotes = Quotes()
        val battery = battery(charges = 30, balance = 10)
        val sent = gasless(amount = 1, balance = 10).copy(available = false)
        val state = TxFeeState(options = listOf(battery, sent), quote = quotes)

        val quoted = TxFeeLogic.quote(state, pickedId = null)

        assertTrue(quotes.asked.isEmpty())
        assertSame(state, quoted)
    }

    // endregion

    // Prices every option one unit above its estimate; an option in [gone] cannot be paid after all.
    private class Quotes(private val gone: Set<String> = emptySet()) : RelayerQuote {

        val asked = mutableListOf<String>()

        override suspend fun quote(state: TxFeeState, option: TxFee): TxFeeState? {
            asked += option.id
            if (option.id in gone) {
                return null
            }
            val quoted = option.copy(fee = Fee.Value(option.fee.asset, option.fee.amount + BigInteger.ONE))
            return state.copy(
                options = state.options.map { listed ->
                    if (listed.id == quoted.id) {
                        quoted
                    } else {
                        listed
                    }
                },
                quotedId = quoted.id,
            )
        }
    }

    private fun native(amount: Int, balance: Int): TxFee {
        val account = testAccount(TestAssets.ton, balance = balance.toString())
        return TxFee(FeeAccount.Chain(account), Fee.Value(tonChain.toAsset(), BigInteger.fromInt(amount)))
    }

    private fun gasless(amount: Int, balance: Int, asset: AssetEntity = TestAssets.usdtTon): TxFee {
        val account = testAccount(asset, balance = balance.toString())
        return TxFee(FeeAccount.Chain(account), Fee.Value(account.asset.value, BigInteger.fromInt(amount)))
    }

    private fun battery(charges: Int, balance: Int?): TxFee = TxFee(
        account = FeeAccount.Keeper(balance = balance?.let { BigInteger.fromInt(it) }, rate = null),
        fee = Fee.Value(Asset.Battery(tonChain), BigInteger.fromInt(charges)),
    )
}
