package com.tonapps.tonkeeper.fragment.send

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.fragment.send.pager.SendScreenAdapter
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import io.tonapi.models.JettonBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import uikit.extensions.collectFlow
import uikit.extensions.textWithLabel
import uikit.mvi.UiFeature

@Deprecated("Need refactoring")
class SendScreenFeature(
    private val collectiblesRepository: CollectiblesRepository,
    private val walletRepository: WalletRepository,
): UiFeature<SendScreenState, SendScreenEffect>(SendScreenState()) {

    fun getPagerItems(hasNft: Boolean): List<SendScreenAdapter.Item> {
        if (hasNft) {
            return listOf(SendScreenAdapter.Item.Recipient, SendScreenAdapter.Item.Confirm)
        }
        return listOf(SendScreenAdapter.Item.Recipient, SendScreenAdapter.Item.Amount, SendScreenAdapter.Item.Confirm)
    }

    private val onNftAddress = MutableStateFlow<String?>(null)
    val nftAddress = onNftAddress.asStateFlow()

    val nftItemFlow = combine(nftAddress, walletRepository.activeWalletFlow) { nftAddress, wallet ->
        nftAddress?.let { address ->
            collectiblesRepository.getNft(wallet.accountId, wallet.testnet, address)
        }
    }.flowOn(Dispatchers.IO)

    private val _onReadyView: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val onReadyView = _onReadyView.asStateFlow()

    private val _transaction = MutableLiveData(TransactionData())
    val transaction: LiveData<TransactionData> = _transaction

    val transactionFlow = _transaction.asFlow()

    init {
        collectFlow(walletRepository.activeWalletFlow) {
            _transaction.value = _transaction.value?.copy(walletAddress = it.address)
        }
    }

    fun setNftAddress(address: String?) {
        onNftAddress.value = address
        _transaction.value = _transaction.value?.copy(nftAddress = address)
    }

    fun setBounce(bounce: Boolean) {
        _transaction.value = _transaction.value?.copy(bounce = bounce)
    }

    fun setAddress(address: String) {
        _transaction.value = _transaction.value?.copy(address = address)
    }

    fun setName(name: String?) {
        _transaction.value = _transaction.value?.copy(name = name)
    }

    fun setComment(comment: String?) {
        _transaction.value = _transaction.value?.copy(comment = comment)
    }

    fun toggleEncryptComment() {
        _transaction.value = _transaction.value?.copy(encryptComment = !_transaction.value?.encryptComment!!)
    }

    fun setAmount(amount: String) {
        _transaction.value = _transaction.value?.copy(amountRaw = amount)
    }

    fun setJetton(token: AccountTokenEntity?) {
        _transaction.value = _transaction.value?.copy(token = token)
    }

    fun setMax(max: Boolean) {
        _transaction.value = _transaction.value?.copy(max = max)
    }

    fun nextPage() {
        updateUiState { it.copy(currentPage = it.currentPage + 1) }
    }

    fun prevPage() {
        updateUiState { it.copy(currentPage = it.currentPage - 1) }
    }

    fun setCurrentPage(index: Int) {
        updateUiState { it.copy(currentPage = index) }
    }

    fun setHeaderTitle(title: String) {
        updateUiState { it.copy(headerTitle = title) }
    }

    fun setHeaderSubtitle(subtitle: CharSequence?) {
        updateUiState { it.copy(headerSubtitle = subtitle) }
    }

    fun setHeaderVisible(visibility: Boolean) {
        updateUiState { it.copy(headerVisible = visibility) }
    }

    fun setAmountHeader(context: Context) {
        setHeaderTitle(context.getString(Localization.amount))

        val address = _transaction.value?.address
        val name = _transaction.value?.name
        if (name.isNullOrEmpty()) {
            setHeaderSubtitle(address?.shortAddress)
        } else {
            setHeaderSubtitle(context.textWithLabel(name, address?.shortAddress))
        }
        setHeaderVisible(true)
    }

    fun readyView() {
        _onReadyView.value = true
    }
}