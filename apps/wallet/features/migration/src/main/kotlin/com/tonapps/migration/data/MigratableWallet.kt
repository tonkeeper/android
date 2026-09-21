package com.tonapps.migration.data

import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.rates.entity.RatesEntity
import io.tonapi.models.JettonBalance
import io.tonapi.models.MigrationWalletValue
import java.math.BigDecimal

data class TronBalances(
    val usdt: Coins = Coins.ZERO,
    val trx: Coins = Coins.ZERO,
    val usdtFiat: Coins = Coins.ZERO,
    val trxFiat: Coins = Coins.ZERO,
) {
    val migratableTrx: Coins
        get() = trx.withoutMigrationDust()

    val hasBalance: Boolean
        get() = usdt.isPositive || migratableTrx.isPositive

    val fiatBalance: Coins
        get() = usdtFiat + trxFiat

    fun withFiat(rates: RatesEntity): TronBalances {
        val migratable = migratableTrx
        return copy(
            usdtFiat = rates.convert(TokenEntity.TRON_USDT.address, usdt),
            trxFiat = rates.convert(TokenEntity.TRX.address, migratable),
        )
    }

    companion object {
        val Empty = TronBalances()

        // Residual after USDT/native fees is treated as migration dust.
        val TrxDustThreshold: Coins = Coins.of("0.25", TokenEntity.TRX.decimals)
    }
}

fun Coins.withoutMigrationDust(): Coins {
    return if (this < TronBalances.TrxDustThreshold) Coins.ZERO else this
}

data class MigratableWallet(
    val wallet: WalletEntity,
    val fiatBalance: Coins,
    val nftCount: Int,
    val hasTonAssets: Boolean = false,
    val tonBalance: Coins = Coins.ZERO,
    val tronBalances: TronBalances = TronBalances.Empty,
) {
    companion object {
        fun create(
            wallet: WalletEntity,
            value: MigrationWalletValue?,
            tronBalances: TronBalances,
            currency: WalletCurrency,
            rates: RatesEntity,
        ): MigratableWallet? {
            val hasTonAssets = value?.hasAssets == true
            if (!hasTonAssets && !tronBalances.hasBalance) return null

            val tonFiat = value?.fiatBalance(currency, rates) ?: Coins.ZERO
            val tron = tronBalances.withFiat(rates)

            return MigratableWallet(
                wallet = wallet,
                fiatBalance = tonFiat + tron.fiatBalance,
                nftCount = value?.nftCount ?: 0,
                hasTonAssets = hasTonAssets,
                tonBalance = value?.let { Coins.of(it.balance) } ?: Coins.ZERO,
                tronBalances = tron,
            )
        }
    }
}

private val MigrationWalletValue.hasAssets: Boolean
    get() = balance > 0 ||
        jettons.any { it.balance.toBigIntegerOrNull()?.signum() == 1 } ||
        nftCount > 0

private fun MigrationWalletValue.fiatBalance(
    currency: WalletCurrency,
    rates: RatesEntity,
): Coins {
    var total = if (balance > 0) rates.convertTON(Coins.of(balance)) else Coins.ZERO
    for (jetton in jettons) {
        total += jetton.fiatBalance(currency) ?: continue
    }
    return total
}

private fun JettonBalance.fiatBalance(currency: WalletCurrency): Coins? {
    val price = price?.prices?.get(currency.code) ?: return null
    val amount = Coins.ofNano(balance, jetton.decimals)
    if (!amount.isPositive) return null
    return Coins.of(amount.value.multiply(BigDecimal(price)), currency.decimals)
}
