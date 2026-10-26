package com.tonapps.perps.funding

data class PerpsFundingAssetId(
    val raw: String,
    val chainId: String,
    val isCoin: Boolean,
    val tokenAddress: String?,
) {

    val isEvm: Boolean
        get() = chainId.substringBefore('/') in evmChains

    companion object {

        const val USDC_ETHEREUM = "eth/mainnet/erc20/0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"

        private const val ETH_ADDRESS_LENGTH = 42
        private const val TRON_ADDRESS_LENGTH = 34
        private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

        private const val TON_CHAIN = "ton"
        private const val BSC_CHAIN = "bsc"
        private const val TRON_CHAIN = "tron"
        private const val BTC_CHAIN = "btc"
        private const val BSC_TOKEN_STANDARD = "bep20"
        private const val TRON_TOKEN_STANDARD = "trc20"
        private const val EVM_TOKEN_STANDARD = "erc20"

        private val evmChains = setOf("eth", BSC_CHAIN, "base", "arb", "polygon", "avax", "robinhood")
        private val depositChains = evmChains + TON_CHAIN + TRON_CHAIN + BTC_CHAIN
        private val withdrawChains = setOf("eth", BSC_CHAIN, "base", "arb", TON_CHAIN, TRON_CHAIN, BTC_CHAIN)

        fun parse(raw: String): PerpsFundingAssetId? = parse(raw, depositChains)

        fun parseDestination(raw: String): PerpsFundingAssetId? = parse(raw, withdrawChains)

        private fun parse(raw: String, chains: Set<String>): PerpsFundingAssetId? {
            val parts = raw.split('/')
            if (parts.size < 3 || parts[0].lowercase() !in chains || parts[1].lowercase() != "mainnet") {
                return null
            }
            val chain = parts[0].lowercase()
            val chainId = "$chain/mainnet"
            val kind = parts[2].lowercase()
            return when {
                kind == "coin" && parts.size == 3 ->
                    PerpsFundingAssetId(raw, chainId, isCoin = true, tokenAddress = null)

                kind == tokenStandard(chain) && parts.size == 4 && isTokenAddress(chain, parts[3]) ->
                    PerpsFundingAssetId(raw, chainId, isCoin = false, tokenAddress = tokenAddress(chain, parts[3]))
                else -> null
            }
        }

        private fun tokenStandard(chain: String): String? {
            return when (chain) {
                BSC_CHAIN -> BSC_TOKEN_STANDARD
                TRON_CHAIN -> TRON_TOKEN_STANDARD
                TON_CHAIN, BTC_CHAIN -> null
                else -> EVM_TOKEN_STANDARD
            }
        }

        private fun isTokenAddress(chain: String, value: String): Boolean {
            return if (chain == TRON_CHAIN) {
                isTronAddress(value)
            } else {
                isEthereumAddress(value)
            }
        }

        private fun tokenAddress(chain: String, value: String): String {
            return if (chain == TRON_CHAIN) {
                value
            } else {
                value.lowercase()
            }
        }

        fun isEthereumAddress(value: String): Boolean {
            return value.length == ETH_ADDRESS_LENGTH &&
                value.startsWith("0x") &&
                value.drop(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        }

        private fun isTronAddress(value: String): Boolean {
            return value.length == TRON_ADDRESS_LENGTH && value.startsWith('T') && value.all { it in BASE58_ALPHABET }
        }
    }
}
