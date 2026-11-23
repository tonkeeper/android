package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.catchingResult
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.deposit.multicoin.fiatOrUsd
import com.tonapps.deposit.multicoin.loadKnownFiats
import com.tonapps.deposit.multicoin.pickFiat
import com.tonapps.deposit.multicoin.supportedFiats
import com.tonapps.deposit.multicoin.data.RampAsset
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.models.CreateP2PSessionRequest

class MerchantRampSource(
    private val walletId: String,
    private val rampRepository: RampRepository,
    private val exchangeRepository: ExchangeRepository,
    private val accountRepository: UnifiedAccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val walletAddress: RampWalletAddress,
) : RampSource {

    override suspend fun findDefaultAsset(fiat: String): Result<RampAsset?> = catchingResult {
        val assets = rampRepository.getConfiguration(walletId, RampType.RampOn, fiat = fiat, query = DEFAULT_SYMBOL, limit = DEFAULT_ASSETS_LIMIT)
            .assets
            .filter { it.asset.valueOrNull != null }
        assets.firstOrNull { it.asset.id.startsWith(DEFAULT_CHAIN_PREFIX) && it.asset.symbol.equals(DEFAULT_SYMBOL, ignoreCase = true) }
            ?: assets.firstOrNull()
    }

    override suspend fun loadAssetDetail(assetId: String): Result<RampAssetDetail?> = catchingResult {
        nullIfNotSold { rampRepository.getAsset(walletId, RampType.RampOn, assetId) }
    }

    override suspend fun appAssetDetail(assetId: String): Result<RampAssetDetail?> = catchingResult {
        // A 404 is a token the app cannot describe; any other failure is a failed load to retry.
        val asset = nullIfNotSold { mcAccountRepository.findAccount(walletId, assetId) }?.asset ?: return@catchingResult null
        RampAssetDetail(
            assetId = assetId,
            symbol = asset.symbol,
            decimals = asset.decimals,
            image = asset.imageUrl,
            networkName = asset.valueOrNull?.chain?.name,
            networkImage = null,
            methods = emptyList(),
            extraIdRequired = false,
            extraIdName = null,
        )
    }

    // An asset no method lists stays in the preferred currency if the ramp lists it (or the list failed to load and it is
    // a fiat), its stub naming it; else USD.
    override suspend fun resolveFiat(asset: RampAssetDetail, preferred: String): String {
        val known = loadKnownFiats(walletId, accountRepository, exchangeRepository, settingsRepository)
        if (asset.supportedFiats.isEmpty()) {
            return if (known.isEmpty()) {
                fiatOrUsd(preferred)
            } else {
                known.firstOrNull { it.equals(preferred, ignoreCase = true) } ?: WalletCurrency.USD.code
            }
        }
        return pickFiat(asset.supportedFiats, known, preferred)
    }

    override suspend fun createP2PSessionUrl(assetId: String, fiat: String): Result<String> = catchingResult {
        exchangeRepository.createP2PSession(
            CreateP2PSessionRequest(wallet = walletAddress.of(walletId, assetId), assetId = assetId, fiatCurrency = fiat),
        ).deeplinkUrl
    }

    private companion object {
        const val DEFAULT_SYMBOL = "USDT"
        const val DEFAULT_CHAIN_PREFIX = "ton/"
        const val DEFAULT_ASSETS_LIMIT = 30
    }
}
