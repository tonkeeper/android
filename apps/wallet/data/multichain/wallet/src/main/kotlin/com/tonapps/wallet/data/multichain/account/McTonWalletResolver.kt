package com.tonapps.wallet.data.multichain.account

import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.Wallet
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.account.MultichainWalletResolver
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class McTonWalletResolver(
    private val mcAccountRepository: McAccountRepository,
    private val chainKitProvider: ChainKitProvider,
) : MultichainWalletResolver {

    override suspend fun getTonWallet(id: String): WalletEntity? = lookup {
        mcAccountRepository.getWallet(id)?.let { toTonWalletEntity(it) }
    }

    override suspend fun getDefaultTonWallet(): WalletEntity? = lookup {
        mcAccountRepository.getWallets().firstNotNullOfOrNull { toTonWalletEntity(it) }
    }

    private suspend fun lookup(block: suspend () -> WalletEntity?): WalletEntity? = withContext(Async.Io) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw MultichainWalletResolver.LookupException(e)
        }
    }

    suspend fun toTonWalletEntity(mc: McWalletEntity): WalletEntity? {
        val account = mcAccountRepository.getTonAccount(mc.id) ?: return null
        val hex = account.publicKey.removePrefix("0x")
        val publicKey = hex.publicKeyFromHex()
        // TON Connect ton_addr must send a state init that hashes to addressOverride (the TW Core
        // address MC actually holds), so for V5R1 it is built by TW Core the same way. V4R2 has no
        // TW Core builder here → leave null and fall back to the V4R2 `contract`.
        val stateInitOverride = if (mc.tonWalletType == Address.Type.TonV4R2) {
            null
        } else {
            chainKitProvider.buildTonV5R1StateInitBase64(account.publicKey)
        }
        return WalletEntity(
            id = mc.id,
            publicKey = publicKey,
            type = WalletType.Multichain,
            version = when (mc.tonWalletType) {
                Address.Type.TonV4R2 -> WalletVersion.V4R2
                else -> WalletVersion.V5R1
            },
            label = Wallet.Label(mc.name, mc.emoji, mc.color),
            initialized = true,
            addressOverride = account.displayAddress,
            stateInitOverride = stateInitOverride,
        )
    }
}