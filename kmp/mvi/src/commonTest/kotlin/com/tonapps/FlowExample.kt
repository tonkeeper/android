package com.tonapps
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.MviSubject
import com.tonapps.mvi.flow.mapLatestCatching
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class DataRepo {
    suspend fun verifyAddress(title: String): String? {
        return title
    }

    suspend fun loadQuote(address: String, amount: Long): Long {
        return amount
    }

    suspend fun resolveAmount(data: String): Long? {
        return data.toLong()
    }

    suspend fun createLink(quote: Long, address: String, amount: Long): String {
        return "link"
    }
}

class ConfirmRepo {
    suspend fun estimateFee(): Long {
        return 1L
    }

    suspend fun initData(request: ConfirmRequest, fee: Long) : PendingConfirmRequest {
        return  PendingConfirmRequest(request, fee, "")
    }

    suspend fun sendTx(request: PendingConfirmRequest) {

    }
}

data class PendingConfirmRequest(
    val initial: ConfirmRequest,
    val fee: Long,
    val data: String,
)

data class ConfirmRequest(
    val addressTo: String,
    val amount: Long,
    val walletId: String,
    val asset: String,
    val fee: Long?,
)

class ConfirmFeature(
    initRequest: ConfirmRequest,
    private val repo: ConfirmRepo,
) : AsyncViewModel() {

    val events = MviRelay<String>()
    val customFee = MutableStateFlow<Long?>(null)
    val request = MutableStateFlow(initRequest)

    val fee = combine(request, customFee)
        { request, customFee -> request to customFee }
        .mapLatest { (request, customFee) ->
            runCatching { customFee ?: request.fee ?: repo.estimateFee() }
                .getOrNull()
        }
        .cacheState()

    private val retryer = MviSubject<Unit>()
    val initData = combine(fee, request, retryer.events)
        { fee, request, _ -> fee to request }
        .mapLatest { (fee, request) ->
            fee ?: return@mapLatest null

            runCatching { repo.initData(request, fee) }
                .getOrNull()
        }
        .cacheState()

    fun setCustomFee(amount: Long) {
        customFee.tryEmit(amount)
    }

    fun retry() {
        retryer.emit(Unit)
    }

    private var job: Job? = null
    fun send() {
        job?.cancel()
        job = bgScope.launch {
            val data = initData.singleOrNull() ?: return@launch

            try {
                repo.sendTx(data)
                events.emit("continue")
            } catch (t: Throwable) {
                verifyError(t)
                events.emit("error")
            }
        }
    }
}



@OptIn(FlowPreview::class)
class SendFeature(
    initAddress: String,
    initAmount: String,
    private val repo: DataRepo,
) : AsyncViewModel() {

    val events = MviRelay<String>()

    val address = MutableStateFlow(initAddress)
    val addressError = MutableStateFlow<String?>(null)
    val addressLoading = MutableStateFlow(false)
    private val addressFilter = address
        .onEach { amountLoading.emit(true) }
        .debounce(300L)
        .mapLatestCatching(
            onError = { addressError.emit(it.localizedMessage) },
            onFinally = { amountLoading.tryEmit(false) },
        ) {
            withContext(bgContext) {
                repo.verifyAddress(it)
            }
        }
        .cacheState()

    val amount = MutableStateFlow(initAmount)
    val amountError = MutableStateFlow<String?>(null)
    val amountLoading = MutableStateFlow(false)
    private val amountFilter = amount
        .onEach { amountLoading.emit(true) }
        .debounce(300L)
        .mapLatestCatching(
            onError = { amountError.emit(it.localizedMessage) },
            onFinally = { amountLoading.tryEmit(false) },
        ) {
            repo.resolveAmount(it)
        }
        .cacheState()

    val quoteLoading = MutableStateFlow(false)
    val quote = combine(addressFilter, amountFilter, addressLoading, amountLoading)
        { address, amount, addressLoading, amountLoading ->
            if (address == null || amount == null || addressLoading || amountLoading) {
                null
            } else {
                address to amount
            }
        }
        .mapLatestCatching(
            onError = { events.emit(it.localizedMessage) },
            onFinally = { quoteLoading.tryEmit(false) },
        ) { data ->
            data?.let { (address, amount) ->
                quoteLoading.tryEmit(true)
                withContext(bgContext) {
                    repo.loadQuote(address, amount)
                }
            }
        }
        .cacheState()

    fun setAddress(newAddress: String) {
        address.tryEmit(newAddress)
    }

    fun setDescription(newAmount: String) {
        amount.tryEmit(newAmount)
    }

    private var job: Job? = null
    fun onContinue() {
        job?.cancel()

        job = bgScope.launch {
            val address = addressFilter.singleOrNull() ?: return@launch
            val amount = amountFilter.singleOrNull() ?: return@launch
            val quote = quote.singleOrNull() ?: return@launch

            try {
                val link = repo.createLink(quote, address, amount)
                events.emit(link)
            } catch (t: Throwable) {
                verifyError(t)
                events.emit("error")
            }
        }
    }
}
