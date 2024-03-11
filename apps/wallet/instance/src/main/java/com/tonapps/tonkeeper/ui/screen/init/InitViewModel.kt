package com.tonapps.tonkeeper.ui.screen.init

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.Coin
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.Tonapi
import com.tonapps.wallet.api.TonapiHelper
import com.tonapps.wallet.api.entity.AccountPreviewEntity
import com.tonapps.wallet.data.account.WalletColor
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletLabel
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.crypto.hex
import org.ton.mnemonic.Mnemonic

@OptIn(FlowPreview::class)
class InitViewModel(
    private val type: InitScreen.Type,
    private val passcodeRepository: PasscodeRepository,
    private val walletRepository: WalletRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
): ViewModel() {

    private val savedState = InitModelState(savedStateHandle)
    private val testnet: Boolean = type == InitScreen.Type.Testnet

    private val _uiTopOffset = MutableStateFlow(0)
    val uiTopOffset = _uiTopOffset.asStateFlow()

    private val _eventFlow = MutableSharedFlow<InitEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    private val _watchAccountResolveFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val watchAccountFlow = _watchAccountResolveFlow.asSharedFlow()
        .debounce(1000)
        .filter { it.isNotBlank() }
        .map {
            val account = TonapiHelper.resolveAddressOrName(it, testnet)
            if (account == null || !account.active) {
                setWatchAccount(null)
                return@map null
            }
            setWatchAccount(account)
            account
        }.flowOn(Dispatchers.IO)

    val accountsFlow = savedState.accountsFlow.filterNotNull()

    init {
        if (!passcodeRepository.hasPinCode) {
            _eventFlow.tryEmit(InitEvent.Step.CreatePasscode)
        } else {
            startWalletFlow()
        }
    }

    fun toggleAccountSelection(item: AccountItem) {
        val accounts = savedState.accounts?.toMutableList() ?: return
        val index = accounts.indexOfFirst { it.address == item.address }
        if (index == -1) {
            return
        }
        accounts[index] = item.copy(selected = !item.selected)
        savedState.accounts = accounts
    }

    private fun startWalletFlow() {
        if (type == InitScreen.Type.Watch) {
            _eventFlow.tryEmit(InitEvent.Step.WatchAccount)
        } else if (type == InitScreen.Type.New) {
            _eventFlow.tryEmit(InitEvent.Step.LabelAccount)
        } else if (type == InitScreen.Type.Import) {
            _eventFlow.tryEmit(InitEvent.Step.ImportWords)
        }
    }

    fun setUiTopOffset(offset: Int) {
        _uiTopOffset.value = offset
    }

    fun routePopBackStack() {
        _eventFlow.tryEmit(InitEvent.Back)
    }

    fun resolveWatchAccount(value: String) {
        _watchAccountResolveFlow.tryEmit(value)
    }

    fun setPasscode(passcode: String) {
        savedState.passcode = passcode

        _eventFlow.tryEmit(InitEvent.Step.ReEnterPasscode)
    }

    fun reEnterPasscode(passcode: String) {
        val valid = savedState.passcode == passcode
        if (!valid) {
            routePopBackStack()
            return
        }
        startWalletFlow()
    }

    fun setMnemonic(words: List<String>) {
        savedState.mnemonic = words
        resolveWallets()
    }

    private fun resolveWallets() {
        val mnemonic = savedState.mnemonic ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val seed = Mnemonic.toSeed(mnemonic)
            savedState.seed = seed
            val privateKey = PrivateKeyEd25519(seed)
            val publicKey = privateKey.publicKey()
            val accounts = TonapiHelper.resolvePublicKey(publicKey, testnet).filter {
                it.isWallet && it.walletVersion != WalletVersion.UNKNOWN && it.active
            }.sortedByDescending { it.walletVersion.index }.toMutableList()

            if (accounts.count { it.walletVersion == WalletVersion.V4R2 } == 0) {
                accounts.add(0, AccountPreviewEntity(
                    query = "",
                    address = WalletV4R2Contract(publicKey = publicKey).address.toWalletAddress(testnet),
                    name = "",
                    isWallet = true,
                    active = true,
                    walletVersion = WalletVersion.V4R2,
                    balance = 0
                ))
            }

            val deferredTokens = mutableListOf<Deferred<List<AccountTokenEntity>>>()
            for (account in accounts) {
                deferredTokens.add(async { getTokens(account.address) })
            }

            val items = mutableListOf<AccountItem>()
            for ((index, account) in accounts.withIndex()) {
                val balance = Coin.toCoins(account.balance)
                val hasTokens = deferredTokens[index].await().size > 1
                val item = AccountItem(
                    address = AddrStd(account.address).toWalletAddress(testnet),
                    name = account.name,
                    walletVersion = account.walletVersion,
                    balanceFormat = CurrencyFormatter.format("TON", balance),
                    tokens = hasTokens,
                    selected = account.walletVersion == WalletVersion.V4R2 || (account.balance > 0 && hasTokens),
                    position = ListCell.getPosition(accounts.size, index)
                )
                items.add(item)
            }
            savedState.accounts = items

            if (items.size > 1) {
                _eventFlow.tryEmit(InitEvent.Step.SelectAccount)
            } else {
                _eventFlow.tryEmit(InitEvent.Step.LabelAccount)
            }
        }
    }

    private suspend fun getTokens(accountId: String): List<AccountTokenEntity> {
        return tokenRepository.getRemote(settingsRepository.currency, accountId, testnet)
    }

    private fun setWatchAccount(account: AccountPreviewEntity?) {
        val oldAccount = getWatchAccount()
        if (oldAccount?.equals(account) == true) {
            return
        }

        savedState.watchAccount = account
        setLabelName(account?.name ?: "")
    }

    fun getWatchAccount(): AccountPreviewEntity? {
        return savedState.watchAccount
    }

    fun getAccounts(): List<AccountItem> {
        return savedState.accounts ?: emptyList()
    }

    fun setLabel(name: String, emoji: String, color: Int) {
        setLabel(WalletLabel(name, emoji, color))
    }

    fun setLabel(label: WalletLabel) {
        savedState.label = label
    }

    fun getLabel(): WalletLabel {
        return savedState.label ?: WalletLabel("","\uD83D\uDE00", WalletColor.all.first())
    }

    fun setLabelName(name: String) {
        val oldLabel = getLabel()
        setLabel(oldLabel.copy(name = name))
    }

    fun nextStep(from: InitEvent.Step) {
        if (from == InitEvent.Step.LabelAccount) {
            execute()
        } else if (from == InitEvent.Step.WatchAccount) {
            _eventFlow.tryEmit(InitEvent.Step.LabelAccount)
        } else if (from == InitEvent.Step.SelectAccount) {
            applyNameFromSelectedAccounts()
            _eventFlow.tryEmit(InitEvent.Step.LabelAccount)
        }
    }

    private fun applyNameFromSelectedAccounts() {
        val selected = savedState.accounts?.filter { it.selected && !it.name.isNullOrBlank() } ?: return
        if (selected.isEmpty()) {
            return
        }
        val name = selected.first().name ?: return
        setLabelName(name)
    }

    private fun execute() {
        _eventFlow.tryEmit(InitEvent.Loading(true))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!passcodeRepository.hasPinCode) {
                    passcodeRepository.set(savedState.passcode!!)
                }

                if (type == InitScreen.Type.Watch) {
                    saveWatchWallet()
                } else if (type == InitScreen.Type.New) {
                    createNewWallet()
                } else if (type == InitScreen.Type.Import) {
                    importWallet()
                }

                _eventFlow.tryEmit(InitEvent.Finish)
            } catch (e: Throwable) {
                _eventFlow.tryEmit(InitEvent.Loading(false))
            }
        }
    }

    private suspend fun createNewWallet() {
        walletRepository.createNewWallet(getLabel())
    }

    private suspend fun saveWatchWallet() {
        val account = getWatchAccount() ?: throw IllegalStateException("Account is not set")
        val label = getLabel()
        val publicKey = resolvePublicKey(account.address)

        walletRepository.addWatchWallet(publicKey, label, account.walletVersion)
    }

    private suspend fun importWallet() {
        val versions = savedState.accounts?.filter { it.selected }?.map { it.walletVersion } ?: throw IllegalStateException("No selected accounts")
        val mnemonic = savedState.mnemonic ?: throw IllegalStateException("Mnemonic is not set")
        val seed = savedState.seed ?: throw IllegalStateException("Seed is not set")
        val label = getLabel()
        val privateKey = PrivateKeyEd25519(seed)
        val publicKey = privateKey.publicKey()

        walletRepository.addWallets(mnemonic, publicKey, versions, label.name, label.emoji, label.color, testnet)
    }

    private fun resolvePublicKey(
        accountId: String,
    ): PublicKeyEd25519 {
        val hex = Tonapi.accounts.get(false).getAccountPublicKey(accountId).publicKey
        return PublicKeyEd25519(hex(hex))
    }

}