package com.tonapps.deposit.multicoin.domain

import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.sortedByChainDisplayOrder

// Resolves the accounts a user can receive to on a wallet. Shared between the
// "Receiving address" list (all accounts) and the standalone per-asset QR sheet (one account).
class ReceiveAccountsInteractor(
    private val oldAccount: AccountRepository,
    private val accountRepo: McAccountRepository,
) {

    suspend fun getAccounts(walletId: String): List<AccountEntity> {
        // Legacy TON wallets aren't in the multichain infra yet, so getCoinAccounts is empty
        // for them — fall back to their single TON address so receive isn't blank.
        return accountRepo.getCoinAccounts(walletId)
            .ifEmpty { legacyTonAccounts(walletId) }
            .sortedByChainDisplayOrder { it.chain }
    }

    suspend fun findAccountForAsset(walletId: String, assetId: String): AccountEntity? {
        val chain = Asset.coinFromString(assetId)?.chain ?: return null
        return getAccounts(walletId).firstOrNull { it.chain == chain }
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
