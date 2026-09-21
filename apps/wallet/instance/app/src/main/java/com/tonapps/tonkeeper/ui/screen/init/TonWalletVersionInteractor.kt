package com.tonapps.tonkeeper.ui.screen.init

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.FIAT_DECIMALS
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.core.components.toAssetEntity
import com.tonapps.extensions.fiatSymbol
import com.tonapps.log.L
import com.tonapps.onboading.screens.selector.WalletVersionFiat
import com.tonapps.onboading.screens.selector.WalletVersionItem
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.account.AccountBalanceEntity
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tonapi.models.GetBlockchainRawAccountsRequest
import io.tonapi.models.JettonBalance
import io.tonapi.models.JettonVerificationType
import io.tonapi.models.MigrationWalletValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val VERSION_SUBTITLE_TIMEOUT_MS = 10_000L

class WalletVersions(
    val items: List<WalletVersionItem>,
    val funded: List<WalletVersionItem>,
) {
    companion object {
        val Empty = WalletVersions(emptyList(), emptyList())
    }
}

class TonWalletVersionInteractor(
    private val api: API,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun resolve(cryptoWallet: CryptoWallet, network: TonNetwork): WalletVersions =
        withContext(Dispatchers.IO) {
            try {
                val chain = Chain.Ton.Mainnet
                val addresses = mapOf(
                    Address.Type.TonV5R1 to cryptoWallet.getAddress(chain, Address.Type.TonV5R1).display,
                    Address.Type.TonV4R2 to cryptoWallet.getAddress(chain, Address.Type.TonV4R2).display,
                )
                buildVersions(addresses, network)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
                WalletVersions.Empty
            }
        }

    private suspend fun buildVersions(
        addresses: Map<Address.Type, String>,
        network: TonNetwork,
    ): WalletVersions {
        val currency = settingsRepository.currency
        val (values, tonRate) = coroutineScope {
            val valuesDeferred = async {
                fetchMigrationValues(addresses.values.map { it.toRawAddress().lowercase() }, network)
            }
            val tonRateDeferred = async { fetchTonRate(currency) }
            valuesDeferred.await() to tonRateDeferred.await()
        }
        if (values == null) {
            return WalletVersions.Empty
        }

        val resolved = addresses.map { (addressType, address) ->
            val value = values[address.toRawAddress().lowercase()]
            val account = buildTonAccount(address, addressType, value?.balance ?: 0L, tonRate)
            val fiat = if (tonRate != null) {
                value?.versionFiat(account, currency)
            } else {
                null
            }
            WalletVersionItem(account, fiat) to (value?.hasAssets == true)
        }
        return WalletVersions(
            items = resolved.map { it.first },
            funded = resolved.filter { it.second }.map { it.first },
        )
    }

    // toRawAddress() keeps an already-raw string's case — hence the explicit lowercase on both sides.
    private suspend fun fetchMigrationValues(
        rawAddresses: List<String>,
        network: TonNetwork,
    ): Map<String, MigrationWalletValue>? {
        return try {
            withTimeoutOrNull(VERSION_SUBTITLE_TIMEOUT_MS) {
                val response = runInterruptible(Dispatchers.IO) {
                    api.migration(network).getMigrationWallets(
                        currencies = listOf(settingsRepository.currency.code),
                        getBlockchainRawAccountsRequest = GetBlockchainRawAccountsRequest(
                            accountIds = rawAddresses,
                        ),
                    )
                }
                response.wallets.associateBy { it.account.toRawAddress().lowercase() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private suspend fun fetchTonRate(currency: WalletCurrency): AssetRateEntity? {
        val tokens = listOf(TokenEntity.TON.address)
        return try {
            withTimeoutOrNull(VERSION_SUBTITLE_TIMEOUT_MS) {
                val rates = runInterruptible(Dispatchers.IO) {
                    ratesRepository.getRatesBlocking(TonNetwork.MAINNET, currency, tokens)
                }
                rates.rateValue(TokenEntity.TON.address)
                    .takeIf { it.isPositive }
                    ?.let { price ->
                        AssetRateEntity(
                            price = price.value.toPlainString(),
                            percentChange24h = "0.00%",
                            currencyCode = currency.code,
                        )
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private fun buildTonAccount(
        address: String,
        addressType: Address.Type,
        balance: Long,
        rate: AssetRateEntity?,
    ): AccountWithDetails {
        val chain = Chain.Ton.Mainnet
        return AccountWithDetails(
            data = AccountEntity(
                walletId = "",
                network = chain.network.type.id,
                mode = chain.network.mode.id,
                displayAddress = address,
                publicKey = "",
                segwitPublicKey = "",
                addressType = addressType,
            ),
            balance = AccountBalanceEntity(available = balance.toString()),
            asset = chain.toAssetEntity(),
            rate = rate,
        )
    }
}

private fun MigrationWalletValue.versionFiat(
    account: AccountWithDetails,
    currency: WalletCurrency,
): WalletVersionFiat? {
    return runCatching {
        var total = account.fiatValue
        for (jetton in jettons) {
            if (jetton.jetton.verification == JettonVerificationType.blacklist) {
                continue
            }
            total += jetton.fiatValue(currency) ?: continue
        }
        WalletVersionFiat(
            balance = DisplayUnit(total, Decimal(FIAT_DECIMALS.toInt())),
            currency = FiatCurrency(currency.code.fiatSymbol()),
            nftCount = nftCount,
        )
    }.getOrNull()
}

private val MigrationWalletValue.hasAssets: Boolean
    get() = balance > 0 || jettons.any { it.isCountable } || nftCount > 0

private val JettonBalance.isCountable: Boolean
    get() = jetton.verification != JettonVerificationType.blacklist &&
        balance.toBigIntegerOrNull()?.signum() == 1

private fun JettonBalance.fiatValue(currency: WalletCurrency): BigDecimal? {
    val price = price?.prices?.get(currency.code) ?: return null
    val amount = Decimal(jetton.decimals).toDisplayUnit(balance)
    if (!amount.isPositive) {
        return null
    }
    return amount.value * BigDecimal.parseString(price)
}
