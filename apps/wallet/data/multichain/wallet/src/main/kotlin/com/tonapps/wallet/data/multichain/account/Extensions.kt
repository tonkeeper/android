package com.tonapps.wallet.data.multichain.account

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.token.entities.TokenRateEntity
import io.walletapi.models.AssetInfo
import io.walletapi.models.AssetPrice
import io.walletapi.models.Chain as WalletApiChain
import java.math.BigDecimal

// TODO refactor usage
fun AssetInfo.toAssetEntity(): AssetEntity {
    return AssetEntity(
        id = assetId,
        name = name,
        symbol = symbol,
        decimals = decimals,
        imageUrl = image,
        verification = when (verification) {
            AssetInfo.Verification.trusted -> AssetEntity.Verification.trusted
            AssetInfo.Verification.whitelist -> AssetEntity.Verification.whitelist
            AssetInfo.Verification.none -> AssetEntity.Verification.none
            AssetInfo.Verification.blacklist -> AssetEntity.Verification.blacklist
        },
    )
}

fun AssetPrice.toAssetRateEntity(currency: String): AssetRateEntity? {
    return prices?.get(currency)?.let { price ->
        AssetRateEntity(
            price = price.toString(),
            percentChange24h = diff24h?.get(currency) ?: "0.00%",
            currencyCode = currency,
        )
    }
}

fun Chain.toApiChain(): WalletApiChain? {
    return WalletApiChain.decode(network.type.id)
}

fun Asset.toAssetEntity(): AssetEntity {
    return AssetEntity(
        id = id,
        name = name,
        symbol = symbol,
        decimals = decimals.value,
        imageUrl = "" // TODO
    )
}

// The chain's native asset (e.g. an ETH-chain account yields ETH, not USDC.ETH) — for labelling
// the receive address of a chain, not a specific token.
fun AccountEntity.toChainAssetEntity(): AssetEntity = chain.toAsset().toAssetEntity()

fun AccountWithDetails.toAccountTokenEntity(
    currency: WalletCurrency,
    token: TokenEntity? = null,
): AccountTokenEntity {
    val value = Coins.ofNano(balance.available, asset.decimals)
    val rateCoins = rate?.price
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Coins.of(BigDecimal(it)) }.getOrNull() }
        ?: Coins.ZERO
    val resolvedToken = token ?: tokenEntityFromAsset()
    return AccountTokenEntity(
        balance = BalanceEntity(
            token = resolvedToken,
            value = value,
            walletAddress = data.displayAddress,
            initializedAccount = true,
            isRequestMinting = false,
            isTransferable = true,
        ),
        fiatRate = if (rateCoins.isZero) {
            null
        } else {
            TokenRateEntity(
                currency = currency,
                fiat = value.multiply(rateCoins),
                rate = rateCoins,
                rateDiff24h = rate?.percentChange24h.orEmpty(),
            )
        },
    )
}

private fun AccountWithDetails.tokenEntityFromAsset(): TokenEntity {
    if (!asset.id.contains("/jetton/")) {
        return TokenEntity.TON
    }

    return TokenEntity(
        blockchain = Blockchain.TON,
        address = asset.id.substringAfterLast('/'),
        name = asset.name,
        symbol = asset.symbol,
        imageUri = asset.imageUrl.takeIf { it.isNotBlank() }?.toUri() ?: Uri.EMPTY,
        decimals = asset.decimals,
        verification = when (asset.verification) {
            AssetEntity.Verification.trusted -> TokenEntity.Verification.whitelist
            AssetEntity.Verification.whitelist -> TokenEntity.Verification.whitelist
            AssetEntity.Verification.none -> TokenEntity.Verification.none
            AssetEntity.Verification.blacklist -> TokenEntity.Verification.blacklist
        },
        isRequestMinting = false,
        isTransferable = true,
        customPayloadApiUri = null,
    )
}
