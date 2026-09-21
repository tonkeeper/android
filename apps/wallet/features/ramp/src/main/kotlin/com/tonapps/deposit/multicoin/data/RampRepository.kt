package com.tonapps.deposit.multicoin.data

import com.tonapps.async.Async
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.models.CreateOfframpOrderRequest
import io.exchangeapi.models.CreateOnrampOrderRequest
import io.exchangeapi.models.ExchangeMerchantSlug
import io.exchangeapi.models.ExchangePaymentMethodType
import io.exchangeapi.models.OfframpQuoteRequest
import io.exchangeapi.models.OnrampQuoteRequest
import io.exchangeapi.models.Platform
import kotlinx.coroutines.withContext

class RampRepository(
    private val api: API,
    private val environment: EnvironmentHelper,
    private val settings: SettingsRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
) {

    private suspend fun multichainWalletId(): String? {
        return unifiedAccountRepository.getSelectedWallet()?.multichainWalletId
    }

    suspend fun getConfiguration(
        rampType: RampType,
        chain: String? = null,
        fiat: String? = null,
        paymentMethod: ExchangePaymentMethodType? = null,
        query: String? = null,
        cursor: String? = null,
        limit: Int = 25,
    ): RampConfiguration = withContext(Async.Io) {
        when (rampType) {
            RampType.RampOn -> api.exchange.onramp.getOnrampConfiguration(
                destinationChain = chain,
                fiat = fiat,
                paymentMethod = paymentMethod,
                q = query,
                country = api.country,
                deviceCountryCode = environment.deviceCountry(),
                storeCountryCode = environment.storeCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                platform = Platform.android,
                cursor = cursor,
                limit = limit,
                xWalletID = multichainWalletId(),
                F = settings.installId,
            ).toRamp()

            RampType.RampOff -> api.exchange.offramp.getOfframpConfiguration(
                sourceChain = chain,
                fiat = fiat,
                payoutMethod = paymentMethod,
                q = query,
                country = api.country,
                deviceCountryCode = environment.deviceCountry(),
                storeCountryCode = environment.storeCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                platform = Platform.android,
                cursor = cursor,
                limit = limit,
                F = settings.installId,
            ).toRamp()
        }
    }

    suspend fun getOnrampChains(fiat: String? = null): List<Chain> = withContext(Async.Io) {
        api.exchange.onramp.getOnrampChains(
            fiat = fiat,
            country = api.country,
            deviceCountryCode = environment.deviceCountry(),
            storeCountryCode = environment.storeCountry(),
            simCountry = environment.simCountry(),
            timezone = environment.timezone(),
            isVpnActive = environment.isVpnActive(),
            platform = Platform.android,
            xWalletID = multichainWalletId(),
            F = settings.installId,
        ).chains.mapNotNull { Chain.find(it, Network.Mode.Mainnet.id) }.distinct()
    }

    suspend fun getAsset(rampType: RampType, assetId: String, fiat: String? = null): RampAssetDetail = withContext(Async.Io) {
        when (rampType) {
            RampType.RampOn -> api.exchange.onramp.getOnrampAsset(
                assetId = assetId,
                fiat = fiat,
                country = api.country,
                deviceCountryCode = environment.deviceCountry(),
                storeCountryCode = environment.storeCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                platform = Platform.android,
                xWalletID = multichainWalletId(),
                F = settings.installId,
            ).toRamp()

            RampType.RampOff -> api.exchange.offramp.getOfframpAsset(
                assetId = assetId,
                country = api.country,
                deviceCountryCode = environment.deviceCountry(),
                simCountry = environment.simCountry(),
                storeCountryCode = environment.storeCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                platform = Platform.android,
                F = settings.installId,
            ).toRamp()
        }
    }

    suspend fun getQuotes(
        rampType: RampType,
        assetId: String,
        fiat: String,
        amount: String,
        paymentMethod: ExchangePaymentMethodType? = null,
        merchant: ExchangeMerchantSlug? = null,
        reverse: Boolean? = null,
    ): RampQuotes = withContext(Async.Io) {
        when (rampType) {
            RampType.RampOn -> api.exchange.onramp.onrampQuote(
                onrampQuoteRequest = OnrampQuoteRequest(
                    targetAssetId = assetId,
                    fiat = fiat,
                    amount = amount,
                    reverse = reverse,
                    paymentMethod = paymentMethod,
                    merchant = merchant,
                ),
                deviceCountryCode = environment.deviceCountry(),
                storeCountryCode = environment.storeCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                platform = Platform.android,
                xWalletID = multichainWalletId(),
                F = settings.installId,
            ).toRamp()

            RampType.RampOff -> api.exchange.offramp.offrampQuote(
                offrampQuoteRequest = OfframpQuoteRequest(
                    sourceAssetId = assetId,
                    fiat = fiat,
                    amount = amount,
                    reverse = reverse,
                    payoutMethod = paymentMethod,
                    merchant = merchant,
                ),
                deviceCountryCode = environment.deviceCountry(),
                storeCountryCode = environment.storeCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                platform = Platform.android,
                F = settings.installId,
            ).toRamp()
        }
    }

    /**
     * Creates an order pinning the quote ([merchantTransactionId]) and returns the
     * provider widget URL the user should be redirected to.
     */
    suspend fun createOrder(
        rampType: RampType,
        assetId: String,
        fiat: String,
        amount: String,
        address: String,
        paymentMethod: ExchangePaymentMethodType,
        merchant: ExchangeMerchantSlug,
        merchantTransactionId: String?,
        extraId: String? = null,
    ): String = withContext(Async.Io) {
        when (rampType) {
            RampType.RampOn -> api.exchange.onramp.createOnrampOrder(
                createOnrampOrderRequest = CreateOnrampOrderRequest(
                    targetAssetId = assetId,
                    fiat = fiat,
                    amount = amount,
                    destinationAddress = address,
                    paymentMethod = paymentMethod,
                    merchant = merchant,
                    merchantTransactionId = merchantTransactionId,
                    extraId = extraId,
                ),
                deviceCountryCode = environment.deviceCountry(),
                storeCountryCode = environment.storeCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                platform = Platform.android,
                xWalletID = multichainWalletId(),
                F = settings.installId,
            ).widgetUrl

            RampType.RampOff -> api.exchange.offramp.createOfframpOrder(
                createOfframpOrderRequest = CreateOfframpOrderRequest(
                    sourceAssetId = assetId,
                    fiat = fiat,
                    amount = amount,
                    fromAddress = address,
                    payoutMethod = paymentMethod,
                    merchant = merchant,
                    merchantTransactionId = merchantTransactionId,
                    extraId = extraId,
                ),
                deviceCountryCode = environment.deviceCountry(),
                storeCountryCode = environment.storeCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
                platform = Platform.android,
                F = settings.installId,
            ).widgetUrl
        }
    }
}
