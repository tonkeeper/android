package com.tonkeeper.fragment.send

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tonapps.tonkeeperx.R
import com.tonkeeper.api.shortAddress
import io.tonapi.models.JettonBalance
import uikit.extensions.textWithLabel
import uikit.mvi.UiFeature

class SendScreenFeature: UiFeature<SendScreenState, SendScreenEffect>(SendScreenState()) {

    private val _transaction = MutableLiveData(TransactionData())
    val transaction: LiveData<TransactionData> = _transaction

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

    fun setAmount(amount: String) {
        _transaction.value = _transaction.value?.copy(amountRaw = amount)
    }

    fun setJetton(jetton: JettonBalance?) {
        _transaction.value = _transaction.value?.copy(jetton = jetton)
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
        setHeaderTitle(context.getString(R.string.amount))

        val address = _transaction.value?.address
        val name = _transaction.value?.name
        if (name.isNullOrEmpty()) {
            setHeaderSubtitle(address?.shortAddress)
        } else {
            setHeaderSubtitle(context.textWithLabel(name, address?.shortAddress))
        }
        setHeaderVisible(true)
    }
}