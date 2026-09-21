package com.tonapps.tonkeeper.ui.screen.backup.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.legacy.enteties.AssetsEntity
import com.tonapps.legacy.enteties.StakedEntity
import com.tonapps.tonkeeper.Wallet
import com.tonapps.tonkeeper.core.BalanceType
import com.tonapps.legacy.assets.sumOfVerifiedFiat
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.Wallet as TonWallet
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository,
    private val passcodeManager: PasscodeManager,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val stakingRepository: StakingRepository,
    private val tokenRepository: TokenRepository,
    private val mcAccountRepository: McAccountRepository,
) : BaseWalletVM(app) {

    val walletFlow: StateFlow<Wallet?> = combine(
        accountRepository.selectedWalletFlow,
        settingsRepository.walletPrefsChangedFlow,
        mcAccountRepository.refreshTrigger,
    ) { selected, _, _ ->
        withContext(Dispatchers.IO) {
            resolveWallet(selected)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    val uiItemsFlow = combine(
        walletFlow.filterNotNull(),
        backupRepository.stream,
    ) { kind, backups ->
        val wallet = walletEntity(kind)
        val filtered = backups.filter { it.walletId == wallet.id }
        val backupsCount = filtered.size
        val balanceFiat = when {
            backupsCount > 0 -> Coins.ZERO
            accountRepository.getWalletById(wallet.id) != null -> getTotalBalanceFiat(wallet)
            else -> Coins.ZERO
        }
        val balanceType = getBalanceType(balanceFiat, wallet)

        val items = mutableListOf<Item>()
        if (balanceType != BalanceType.Zero) {
            val format = CurrencyFormatter.formatFiat(settingsRepository.currency.code, balanceFiat)
            items.add(Item.Alert(format, balanceType == BalanceType.Huge))
            items.add(Item.Space)
        }
        items.add(Item.Header)
        items.add(Item.Space)

        for ((index, backup) in filtered.withIndex()) {
            val position = ListCell.getPosition(backupsCount, index)
            items.add(Item.Backup(position, backup, settingsRepository.getLocale()))
        }
        if (backupsCount > 0) {
            items.add(Item.Space)
            items.add(Item.RecoveryPhrase)
        } else if (balanceType == BalanceType.Zero) {
            items.add(Item.ManualBackup)
        } else {
            items.add(Item.ManualAccentBackup)
        }
        items.toList()
    }.flowOn(Dispatchers.IO)

    fun getRecoveryPhrase(
        context: Context,
        callback: (Array<String>, Throwable?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val words = when (val kind = walletFlow.value) {
                    is Wallet.Legacy -> {
                        if (!passcodeManager.confirmation(context, getString(Localization.app_name))) {
                            return@launch
                        }
                        val mnemonic = accountRepository.getMnemonic(kind.id) ?: emptyArray()
                        if (mnemonic.isEmpty()) {
                            val hasPrivateKey = accountRepository.getPrivateKey(kind.id) != null
                            if (hasPrivateKey) {
                                throw IllegalStateException("No mnemonic but has private key")
                            } else {
                                throw IllegalStateException("No mnemonic and no private key")
                            }
                        }
                        mnemonic
                    }
                    is Wallet.Multichain -> {
                        passcodeManager.unlockMultichainVault(context) { coder ->
                            mcAccountRepository.getMnemonic(kind.id, coder)
                                ?: throw IllegalStateException("No mnemonic for multichain wallet")
                        }
                    }
                    null -> {
                        throw IllegalStateException("Wallet not found")
                    }
                }
                withContext(Dispatchers.Main) {
                    callback(words, null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    callback(emptyArray(), e)
                }
            }
        }
    }

    private suspend fun resolveWallet(selected: WalletEntity): Wallet? {
        val id = selected.id
        if (id.isBlank()) {
            return null
        }
        accountRepository.getWalletById(id)?.let { return Wallet.Legacy(it) }
        mcAccountRepository.getWallet(id)?.let { return Wallet.Multichain(it) }
        return null
    }

    private fun walletEntity(kind: Wallet): WalletEntity {
        return when (kind) {
            is Wallet.Legacy -> kind.entity
            is Wallet.Multichain -> mcToDisplayEntity(kind.entity)
        }
    }

    private fun mcToDisplayEntity(mc: McWalletEntity): WalletEntity {
        return WalletEntity.EMPTY.copy(
            id = mc.id,
            label = TonWallet.Label(mc.name, mc.emoji, mc.color),
            version = WalletVersion.V4R2,
            type = WalletType.Default,
        )
    }

    private suspend fun getBalanceType(
        balanceFiat: Coins,
        displayWallet: WalletEntity,
    ): Int {
        val rates = ratesRepository.getTONRates(displayWallet.network, settingsRepository.currency)
        val balanceTON = rates.convertFromFiat(TokenEntity.TON.address, balanceFiat)
        return BalanceType.getBalanceType(balanceTON)
    }

    private suspend fun getTotalBalanceFiat(
        wallet: WalletEntity
    ): Coins {
        val assets = getAssets(wallet)
        return if (wallet.testnet) {
            assets.first().fiat
        } else {
            assets.sumOfVerifiedFiat()
        }
    }

    private suspend fun getAssets(
        wallet: WalletEntity,
    ): List<AssetsEntity> {
        val currency = settingsRepository.currency
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.network) ?: emptyList()
        val staking = stakingRepository.get(wallet.accountId, wallet.network)
        val staked = StakedEntity.create(wallet, staking, tokens, currency, ratesRepository)
        val liquid = staked.find { it.isTonstakers }?.liquidToken
        val filteredTokens = if (liquid == null) {
            tokens
        } else {
            tokens.filter { !liquid.token.address.contains(it.address) }
        }
        return (filteredTokens.map { AssetsEntity.Token(it) } + staked.map {
            AssetsEntity.Staked(it)
        }).sortedBy { it.fiat }.reversed()
    }
}
