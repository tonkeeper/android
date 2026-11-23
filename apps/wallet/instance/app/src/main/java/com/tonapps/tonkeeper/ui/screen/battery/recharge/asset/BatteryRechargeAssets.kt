package com.tonapps.tonkeeper.ui.screen.battery.recharge.asset

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodType
import com.tonapps.wallet.data.multichain.account.AccountWithDetails

/** Which of a multichain wallet's positions the battery can be recharged with, on the chains other than TON. */
object BatteryRechargeAssets {

    // `<chain>/<mode>/coin`; a token adds its standard and its contract.
    private const val COIN_PARTS = 3

    /**
     * The positions a [methods] entry takes that hold something to pay with: only where the wallet has an
     * address on that chain and a balance of that asset. The largest by fiat value first.
     */
    fun payable(methods: List<RechargeMethodEntity>, accounts: List<AccountWithDetails>): List<AccountWithDetails> {
        val recharging = methods.filter { it.supportRecharge && !it.type.isTon }
        return accounts
            .filter { account -> account.unitBalance.value.signum() > 0 && method(recharging, account.asset.id) != null }
            .distinctBy { it.asset.id.lowercase() }
            .sortedByDescending { it.fiatValue }
    }

    /** The method for [assetId], as the multichain API names it. */
    fun method(methods: List<RechargeMethodEntity>, assetId: String): RechargeMethodEntity? {
        return methods.firstOrNull { sameAsset(it.assetId, assetId) }
    }

    /** The chain [method] pays on, from its `ton/mainnet`; null for a method the battery gave no chain for. */
    fun chain(method: RechargeMethodEntity): Chain? {
        val parts = method.chain.split('/')
        if (parts.size < 2) {
            return null
        }
        return Chain.find(parts[0], parts[1])
    }

    /**
     * The chain whose icon badges [method]'s: a token's, and a coin's that is not the chain's own (ETH on Base).
     * Null for the chain's own coin, which needs no badge.
     */
    fun badgeChain(method: RechargeMethodEntity): Chain? {
        val chain = chain(method) ?: return null
        val isCoin = method.type == RechargeMethodType.TON || method.type == RechargeMethodType.COIN
        return chain.takeIf { !isCoin || it.coin.primaryCoin != null }
    }

    /**
     * Whether the battery's [a] and the multichain API's [b] name one asset: the same chain and the same
     * contract. The token standard between them is left out, since the two spell it differently
     * (`bsc/mainnet/erc20/…` for the battery, `bsc/mainnet/bep20/…` for the balance). An EVM address
     * is the same in any case; a base58 one (TRON, Solana) is not.
     */
    internal fun sameAsset(a: String, b: String): Boolean {
        val left = a.split('/')
        val right = b.split('/')
        if (left.size < COIN_PARTS || left.size != right.size) {
            return false
        }

        if (left[0] != right[0] || left[1] != right[1]) {
            return false
        }

        if (left.size == COIN_PARTS) {
            return left[2] == right[2]
        }

        val contract = left.last()
        val ignoreCase = contract.startsWith("0x", ignoreCase = true)
        return contract.equals(right.last(), ignoreCase = ignoreCase)
    }
}
