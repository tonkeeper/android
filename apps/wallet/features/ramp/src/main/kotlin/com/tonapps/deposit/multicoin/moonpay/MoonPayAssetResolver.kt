package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformAsset

// Finds MoonPay's asset for ours by network and contract, not by ticker: USDT is priced under its own code on
// every network.
internal object MoonPayAssetResolver {

    fun match(assetId: String, symbol: String, assets: List<MoonPayPlatformAsset>): MoonPayPlatformAsset? {
        val asset = Asset.fromString(assetId, name = symbol, symbol = symbol, decimals = 0) ?: return null
        val type = asset.chain.network.type
        val codes = networkCodes(type)
        val onChain = assets.filter { it.isAvailable && it.network.lowercase() in codes }
        return when (asset) {
            is Asset.Coin -> {
                val natives = onChain.filter(::isNative)
                natives.firstOrNull { it.code.equals(symbol, ignoreCase = true) } ?: natives.singleOrNull()
            }
            // One address can be a different token on each EVM network, so only this network's assets count.
            is Asset.Token -> {
                val wanted = normalized(asset.contract.display, type)
                onChain.firstOrNull { candidate -> candidate.contractAddress?.let { normalized(it, type) } == wanted }
            }
            is Asset.Battery -> null
        }
    }

    // MoonPay marks a native token with no contract or with the zero address.
    private fun isNative(asset: MoonPayPlatformAsset): Boolean {
        val contract = asset.contractAddress ?: return true
        val digits = contract.lowercase().removePrefix("0x")
        return digits.isNotEmpty() && digits.all { it == '0' }
    }

    // Whole values: a substring would also take in networks such as `ethereum_classic`.
    private fun networkCodes(type: Network.Type): Set<String> {
        return when (type) {
            Network.Type.Ton -> setOf("ton")
            Network.Type.Ethereum -> setOf("ethereum")
            Network.Type.Base -> setOf("base")
            Network.Type.Bitcoin -> setOf("bitcoin")
            Network.Type.Tron -> setOf("tron")
            Network.Type.Arbitrum -> setOf("arbitrum", "arbitrum_one")
            Network.Type.Smartchain -> setOf("binance_smart_chain", "bnb_smart_chain", "bsc")
            Network.Type.Solana -> setOf("solana")
            Network.Type.Polygon -> setOf("polygon", "polygon_pos")
            Network.Type.Avalanche -> setOf("avalanche", "avalanche_c_chain", "avax")
            Network.Type.Robinhood -> setOf("robinhood")
        }
    }

    private fun normalized(address: String, type: Network.Type): String {
        return if (type == Network.Type.Ton) {
            address.toRawAddress().lowercase()
        } else {
            address.lowercase()
        }
    }
}
