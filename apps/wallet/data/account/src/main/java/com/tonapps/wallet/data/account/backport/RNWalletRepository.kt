package com.tonapps.wallet.data.account.backport

import android.content.Context
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.WalletSource
import com.tonapps.wallet.data.account.backport.data.RNWallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletLabel
import com.tonapps.wallet.data.account.repository.BaseWalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.mnemonic.Mnemonic
import java.util.UUID

class RNWalletRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val api: API
): BaseWalletRepository(scope, api) {

    private val backport = RNBackport(context)

    init {
        scope.launch(Dispatchers.IO) {
            updateWallets()
        }
    }

    override fun removeCurrent() {
        scope.launch {
            val walletId = getActiveWalletId()
            backport.delete(walletId)
            updateWallets()
        }
    }

    override suspend fun getTonProofToken(walletId: String): String? {
        return ""
    }

    override suspend fun getWalletByAccountId(accountId: String): WalletEntity? {
        return getWallets().find { it.accountId.equals(accountId, ignoreCase = true) }
    }

    override suspend fun getWalletById(id: String): WalletEntity? {
        val wallet = backport.getWallets().wallets.find { it.identifier == id } ?: return null
        return WalletEntity(wallet)
    }

    override suspend fun setActiveWallet(id: String): WalletEntity? {
        val rnWallets = backport.getWallets()
        if (rnWallets.selectedIdentifier == id) {
            return null
        }
        val wallet = rnWallets.wallets.find { it.identifier == id } ?: return null
        backport.setWallets(rnWallets.copy(
            selectedIdentifier = id
        ))

        updateWallets()

        return WalletEntity(wallet)
    }

    override suspend fun editLabel(id: String, name: String, emoji: String, color: Int) {
        backport.edit(id, name, emoji, color)

        updateWallets()
    }

    override suspend fun editLabel(name: String, emoji: String, color: Int) {
        editLabel(getActiveWalletId(), name, emoji, color)
    }

    override suspend fun createNewWallet(label: WalletLabel): WalletEntity = withContext(Dispatchers.IO) {
        val mnemonic = Mnemonic.generate()
        val seed = Mnemonic.toSeed(mnemonic)
        val privateKey = PrivateKeyEd25519(seed)
        val publicKey = privateKey.publicKey()

        val contract = WalletV4R2Contract(publicKey = publicKey)

        val wallet = RNWallet(
            name = label.name,
            emoji = label.emoji.toString(),
            color = RNWallet.resolveColor(label.color),
            identifier = UUID.randomUUID().toString(),
            pubkey = publicKey.hex(),
            network = RNWallet.Network.Mainnet,
            type = RNWallet.Type.Regular,
            version = RNWallet.ContractVersion.v4R2,
            workchain = contract.workchain,
            allowedDestinations = null,
            configPubKey = null,
            ledger = null
        )

        backport.addWallet(wallet)

        updateWallets()
        WalletEntity(wallet)
    }

    override suspend fun addWatchWallet(
        publicKey: PublicKeyEd25519,
        label: WalletLabel,
        version: WalletVersion,
        source: WalletSource
    ): WalletEntity = withContext(Dispatchers.IO) {
        val contract = BaseWalletContract.create(publicKey, version.title)

        val wallet = RNWallet(
            name = label.name,
            emoji = label.emoji.toString(),
            color = RNWallet.resolveColor(label.color),
            identifier = UUID.randomUUID().toString(),
            pubkey = publicKey.hex(),
            network = RNWallet.Network.Mainnet,
            type = RNWallet.Type.Regular,
            version = RNWallet.ContractVersion.v4R2,
            workchain = contract.workchain,
            allowedDestinations = null,
            configPubKey = null,
            ledger = null
        )

        backport.addWallet(wallet)

        updateWallets()
        WalletEntity(wallet)
    }

    override suspend fun addWallets(
        mnemonic: List<String>,
        publicKey: PublicKeyEd25519,
        versions: List<WalletVersion>,
        name: String?,
        emoji: CharSequence,
        color: Int,
        testnet: Boolean
    ): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        for (version in versions) {
            val contract = BaseWalletContract.create(publicKey, version.title)
            var walletName = (name ?: "Wallet")
            if (versions.size > 1) {
                walletName += " ${version.title}"
            }

            val wallet = RNWallet(
                name = walletName,
                emoji = emoji.toString(),
                color = RNWallet.resolveColor(color),
                identifier = UUID.randomUUID().toString(),
                pubkey = publicKey.hex(),
                network = if (!testnet) RNWallet.Network.Mainnet else RNWallet.Network.Testnet,
                type = RNWallet.Type.Regular,
                version = RNWallet.fromOriginal(version),
                workchain = contract.workchain,
                allowedDestinations = null,
                configPubKey = null,
                ledger = null
            )

            backport.addWallet(wallet)
            list.add(WalletEntity(wallet))
        }

        updateWallets()
        return list
    }

    override suspend fun addSignerWallet(
        publicKey: PublicKeyEd25519,
        name: String,
        emoji: CharSequence,
        color: Int,
        source: WalletSource,
        versions: List<WalletVersion>
    ): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        for (version in versions) {
            val contract = BaseWalletContract.create(publicKey, version.title)
            var walletName = name
            if (versions.size > 1) {
                walletName += " ${version.title}"
            }

            val wallet = RNWallet(
                name = walletName,
                emoji = emoji.toString(),
                color = RNWallet.resolveColor(color),
                identifier = UUID.randomUUID().toString(),
                pubkey = publicKey.hex(),
                network = RNWallet.Network.Mainnet,
                type = RNWallet.Type.Regular,
                version = RNWallet.fromOriginal(version),
                workchain = contract.workchain,
                allowedDestinations = null,
                configPubKey = null,
                ledger = null
            )

            backport.addWallet(wallet)
            list.add(WalletEntity(wallet))
        }

        updateWallets()
        return list
    }


    override suspend fun getMnemonic(id: String): Array<String> {
        return emptyArray()
    }

    override suspend fun getPrivateKey(id: String): PrivateKeyEd25519 {
        return EmptyPrivateKeyEd25519
    }

    override suspend fun clear() {
        backport.clear()
        updateWallets()
    }

    override suspend fun getWallets(): List<WalletEntity> {
        return backport.getWallets().wallets.map { WalletEntity(it) }
    }

    override suspend fun getActiveWalletId() = backport.getWallets().selectedIdentifier

    private suspend fun updateWallets() {
        val activeWalletId = getActiveWalletId()
        val wallets = getWallets()
        _walletsFlow.value = wallets
        _activeWalletFlow.value = wallets.find { it.id == activeWalletId }
    }
}
