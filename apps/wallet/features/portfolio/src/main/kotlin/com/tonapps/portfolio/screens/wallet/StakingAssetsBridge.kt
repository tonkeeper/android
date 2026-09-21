package com.tonapps.portfolio.screens.wallet

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.core.components.isNativeTon
import com.tonapps.core.extensions.formatFiat
import com.tonapps.core.extensions.formatWithSymbol
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.jettonAssetId
import com.tonapps.wallet.data.multichain.account.jettonToTon
import com.tonapps.wallet.data.multichain.account.matchesJettonMaster
import com.tonapps.wallet.data.multichain.account.tonCoinAssetId
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.StakingEntity

internal object StakingAssetsBridge {

    private const val TON_SYMBOL = "GRAM"

    suspend fun loadRows(
        walletId: String,
        accountId: String,
        network: TonNetwork,
        initializedAccount: Boolean,
        currencyCode: String,
        hiddenBalance: Boolean,
        hideDust: Boolean,
        accountRepo: McAccountRepository,
        stakingRepository: StakingRepository,
        ignoreCache: Boolean = false,
    ): List<StakedUi> {
        val staking = stakingRepository.get(
            accountId = accountId,
            network = network,
            ignoreCache = ignoreCache,
            initializedAccount = initializedAccount,
        )

        // Nothing writes the unfiltered cache key while the dust filter is on, and the cache has no
        // expiry, so reading it unfiltered would serve a frozen snapshot forever.
        val accounts = accountRepo.getCachedAccounts(
            walletId = walletId,
            currency = currencyCode,
            hideDust = hideDust,
        )?.accounts.orEmpty()

        val testnet = network.isTestnet
        val tonAccount = accounts.firstOrNull { it.asset.isNativeTon() }
            ?: runCatching {
                accountRepo.findAccount(walletId, tonCoinAssetId(testnet), currencyCode)
            }.getOrNull()

        val liquid = loadLiquidRows(
            walletId = walletId,
            testnet = testnet,
            currencyCode = currencyCode,
            hiddenBalance = hiddenBalance,
            accounts = accounts,
            tonAccount = tonAccount,
            staking = staking,
            accountRepo = accountRepo,
        )
        val nominators = loadNominatorRows(
            currencyCode = currencyCode,
            hiddenBalance = hiddenBalance,
            tonAccount = tonAccount,
            staking = staking,
        )
        return (liquid + nominators).sortedByDescending { it.fiat }
    }

    private suspend fun loadLiquidRows(
        walletId: String,
        testnet: Boolean,
        currencyCode: String,
        hiddenBalance: Boolean,
        accounts: List<AccountWithDetails>,
        tonAccount: AccountWithDetails?,
        staking: StakingEntity,
        accountRepo: McAccountRepository,
    ): List<StakedUi> {
        val tonPrice = tonAccount?.rate?.value?.value?.takeIf { it > BigDecimal.ZERO }
            ?: return emptyList()

        val liquidPools = staking.pools
            .flatMap { it.pools }
            .filter {
                it.implementation == StakingPool.Implementation.LiquidTF &&
                    it.liquidJettonMaster != null
            }
        if (liquidPools.isEmpty()) return emptyList()

        val rows = mutableListOf<StakedUi>()
        var tonstakersAdded = false
        for (pool in liquidPools) {
            if (tonstakersAdded) {
                break
            }
            val row = buildLiquidRow(
                walletId = walletId,
                pool = pool,
                testnet = testnet,
                currencyCode = currencyCode,
                hiddenBalance = hiddenBalance,
                accounts = accounts,
                tonPrice = tonPrice,
                staking = staking,
                accountRepo = accountRepo,
            )
            if (row == null) {
                continue
            }
            rows.add(row)
            tonstakersAdded = true
        }
        return rows
    }

    private fun loadNominatorRows(
        currencyCode: String,
        hiddenBalance: Boolean,
        tonAccount: AccountWithDetails?,
        staking: StakingEntity,
    ): List<StakedUi> {
        val rows = mutableListOf<StakedUi>()
        val seen = mutableSetOf<String>()
        for (info in staking.info) {
            val pool = staking.findPoolByAddress(info.pool) ?: continue
            if (pool.implementation == StakingPool.Implementation.LiquidTF) continue
            if (!seen.add(pool.address)) continue

            val amount = staking.getAmount(pool)
            val readyWithdraw = staking.getReadyWithdraw(pool)
            val pendingDeposit = staking.getPendingDeposit(pool)
            val pendingWithdraw = staking.getPendingWithdraw(pool)
            if (
                amount.isZero &&
                readyWithdraw.isZero &&
                pendingDeposit.isZero &&
                pendingWithdraw.isZero
            ) {
                continue
            }

            rows.add(
                buildNominatorRow(
                    pool = pool,
                    amount = amount,
                    readyWithdraw = readyWithdraw,
                    pendingDeposit = pendingDeposit,
                    pendingWithdraw = pendingWithdraw,
                    currencyCode = currencyCode,
                    hiddenBalance = hiddenBalance,
                    tonAccount = tonAccount,
                )
            )
        }
        return rows
    }

    private suspend fun buildLiquidRow(
        walletId: String,
        pool: PoolEntity,
        testnet: Boolean,
        currencyCode: String,
        hiddenBalance: Boolean,
        accounts: List<AccountWithDetails>,
        tonPrice: BigDecimal,
        staking: StakingEntity,
        accountRepo: McAccountRepository,
    ): StakedUi? {
        val master = pool.liquidJettonMaster ?: return null
        val assetId = jettonAssetId(master, testnet)
        val liquidAccount = accounts.firstOrNull { it.matchesJettonMaster(master) }
            ?: runCatching {
                accountRepo.findAccount(walletId, assetId, currencyCode)
            }.getOrNull()
            ?: return null

        val jettonPrice = liquidAccount.rate?.value?.value?.takeIf { it > BigDecimal.ZERO }
            ?: return null

        val jettonAmount = liquidAccount.displayBalance.value
        val pendingWithdraw = staking.getPendingWithdraw(pool)
        val readyWithdraw = staking.getReadyWithdraw(pool)
        val pendingDeposit = staking.getPendingDeposit(pool)
        if (
            jettonAmount <= BigDecimal.ZERO &&
            pendingWithdraw.isZero &&
            readyWithdraw.isZero &&
            pendingDeposit.isZero
        ) {
            return null
        }

        val tonAmount = jettonToTon(jettonAmount, jettonPrice, tonPrice)
        val tonDisplay = DisplayUnit(tonAmount, Decimal(9))
        val fiatFormat = liquidAccount.rate?.let { rate ->
            Formatter.formatFiat(
                value = liquidAccount.displayBalance,
                rate = rate.value,
            )
        } ?: Coins.of(java.math.BigDecimal(liquidAccount.fiatValue.toPlainString()))
            .formatFiat(currencyCode)

        return StakedUi(
            poolAddress = pool.address,
            poolName = pool.name,
            poolImplementation = pool.implementation,
            balanceFormat = Formatter.formatShort(value = tonDisplay),
            fiatFormat = fiatFormat,
            fiat = liquidAccount.fiatValue,
            readyWithdraw = readyWithdraw,
            readyWithdrawFormat = readyWithdraw.takeIf { it.isPositive }?.formatWithSymbol(TON_SYMBOL),
            pendingDeposit = pendingDeposit,
            pendingDepositFormat = pendingDeposit.takeIf { it.isPositive }?.formatWithSymbol(TON_SYMBOL),
            pendingWithdraw = pendingWithdraw,
            pendingWithdrawFormat = pendingWithdraw.takeIf { it.isPositive }?.formatWithSymbol(TON_SYMBOL),
            cycleEnd = pool.cycleEnd,
            liquidAssetId = liquidAccount.asset.id.ifBlank { assetId },
            hiddenBalance = hiddenBalance,
        )
    }

    private fun buildNominatorRow(
        pool: PoolEntity,
        amount: Coins,
        readyWithdraw: Coins,
        pendingDeposit: Coins,
        pendingWithdraw: Coins,
        currencyCode: String,
        hiddenBalance: Boolean,
        tonAccount: AccountWithDetails?,
    ): StakedUi {
        val tonPrice = tonAccount?.rate?.value?.value?.takeIf { it > BigDecimal.ZERO }
        val amountBd = BigDecimal.parseString(amount.value.toPlainString())
        val tonDisplay = DisplayUnit(amountBd, Decimal(amount.decimals.coerceAtLeast(9)))
        val fiat = if (tonPrice != null) {
            amountBd.multiply(tonPrice)
        } else {
            BigDecimal.ZERO
        }
        val fiatFormat = tonAccount?.rate?.let { rate ->
            Formatter.formatFiat(value = tonDisplay, rate = rate.value)
        } ?: Coins.of(java.math.BigDecimal(fiat.toPlainString())).formatFiat(currencyCode)

        return StakedUi(
            poolAddress = pool.address,
            poolName = pool.name,
            poolImplementation = pool.implementation,
            balanceFormat = Formatter.formatShort(value = tonDisplay),
            fiatFormat = fiatFormat,
            fiat = fiat,
            readyWithdraw = readyWithdraw,
            readyWithdrawFormat = readyWithdraw.takeIf { it.isPositive }?.formatWithSymbol(TON_SYMBOL),
            pendingDeposit = pendingDeposit,
            pendingDepositFormat = pendingDeposit.takeIf { it.isPositive }?.formatWithSymbol(TON_SYMBOL),
            pendingWithdraw = pendingWithdraw,
            pendingWithdrawFormat = pendingWithdraw.takeIf { it.isPositive }?.formatWithSymbol(TON_SYMBOL),
            cycleEnd = pool.cycleEnd,
            liquidAssetId = null,
            hiddenBalance = hiddenBalance,
        )
    }
}
