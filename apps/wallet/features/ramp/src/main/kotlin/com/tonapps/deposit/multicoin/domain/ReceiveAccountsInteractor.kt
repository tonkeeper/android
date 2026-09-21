package com.tonapps.deposit.multicoin.domain

import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository

// Resolves the accounts a user can receive to on the selected wallet. Shared between the
// "Receiving address" list (all accounts) and the standalone per-asset QR sheet (one account).
class ReceiveAccountsInteractor(
    private val oldAccount: AccountRepository,
    private val accountRepo: McAccountRepository,
) {

    private val chainDisplayOrder: Map<Chain, Int> = listOf(
        Chain.Ton.Mainnet,
        Chain.Tron.Mainnet,
        Chain.Ethereum.Mainnet,
        Chain.Bitcoin.Mainnet,
        Chain.Base.Mainnet,
        Chain.Smartchain.Mainnet,
        Chain.Arbitrum.Mainnet,
    ).withIndex().associate { (index, chain) -> chain to index }

    suspend fun getAccounts(): List<AccountEntity> {
        val walletId = oldAccount.getSelectedWalletId() ?: return emptyList()
        // Legacy TON wallets aren't in the multichain infra yet, so getCoinAccounts is empty
        // for them — fall back to their single TON address so receive isn't blank.
        return accountRepo.getCoinAccounts(walletId)
            .ifEmpty { legacyTonAccounts(walletId) }
            .sortedBy { chainDisplayOrder[it.chain] ?: chainDisplayOrder.size }
    }

    suspend fun findAccountForAsset(assetId: String): AccountEntity? {
        val chain = Asset.coinFromString(assetId)?.chain ?: return null
        return getAccounts().firstOrNull { it.chain == chain }
    }

    private suspend fun legacyTonAccounts(walletId: String): List<AccountEntity> {
        val wallet = oldAccount.getWalletById(walletId) ?: return emptyList()
        val chain = Chain.Ton.Mainnet
        return listOf(
            AccountEntity(
                walletId = wallet.id,
                network = chain.network.type.id,
                mode = chain.network.mode.id,
                displayAddress = wallet.address,
                publicKey = "",
                segwitPublicKey = "",
                addressType = Address.Type.TonV5R1,
            )
        )
    }
}
