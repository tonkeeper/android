package com.tonapps.tonkeeper.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.GetBillingConfigParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.ErrorForUserException
import com.tonapps.log.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

private const val PURCHASE_RESULTS_BUFFER = 16
private const val CONNECTION_TIMEOUT_MILLIS = 10_000L

class BillingManager(
    private val context: Context,
) : PurchasesUpdatedListener {

    @Volatile
    private var billingClient: BillingClient = newClient()

    private var billingClientConnected = false

    private val _productsFlow = MutableStateFlow<List<ProductDetails>?>(null)
    val productsFlow = _productsFlow.asStateFlow()

    private val _purchaseResultsFlow = MutableSharedFlow<PurchasesUpdated>(extraBufferCapacity = PURCHASE_RESULTS_BUFFER)
    val purchaseResultsFlow = _purchaseResultsFlow.asSharedFlow()

    private var connection: CompletableDeferred<BillingClient>? = null

    private var connectionStartedAt = 0L

    private fun newClient(): BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams
                .newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    private suspend fun <T> ready(block: suspend (client: BillingClient) -> T): T {
        val current = billingClient
        if (current.isReady && current.connectionState == BillingClient.ConnectionState.CONNECTED) {
            return block(current)
        }
        val client = withTimeoutOrNull(CONNECTION_TIMEOUT_MILLIS) { connect().await() }
            ?: throw ErrorForUserException.of("Google Play is not responding")
        return block(client)
    }

    @Synchronized
    private fun connect(): Deferred<BillingClient> {
        val pending = connection?.takeIf { it.isActive }
        val now = SystemClock.elapsedRealtime()
        if (pending != null) {
            if (billingClientConnected || now - connectionStartedAt < CONNECTION_TIMEOUT_MILLIS) {
                return pending
            }
            billingClient.endConnection()
            billingClient = newClient()
        }
        val client = billingClient
        val result = pending ?: CompletableDeferred()
        connection = result
        connectionStartedAt = now
        val listener = object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                onSetupFinished(client, result, billingResult)
            }

            override fun onBillingServiceDisconnected() { }
        }
        try {
            client.startConnection(listener)
        } catch (e: Exception) {
            result.completeExceptionally(e)
        }
        return result
    }

    @Synchronized
    private fun onSetupFinished(
        client: BillingClient,
        result: CompletableDeferred<BillingClient>,
        billingResult: BillingResult,
    ) {
        if (client !== billingClient) {
            return
        }
        if (billingResult.isSuccess) {
            billingClientConnected = true
            result.complete(client)
        } else {
            val message = billingResult.debugMessage ?: "unknown error"
            result.completeExceptionally(ErrorForUserException.of(message))
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (!result.isSuccess) {
            log("Purchases updated", BillingException(result))
        }

        _purchaseResultsFlow.tryEmit(PurchasesUpdated(result, purchases.orEmpty()))
    }

    suspend fun consumeProduct(purchaseToken: String) = ready { client ->
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        client.consumePurchase(params)
    }

    suspend fun loadProducts(
        productIds: List<String>,
        productType: String = ProductType.INAPP
    ) {
        val products = withTimeoutOrNull(5.seconds) {
            try {
                getProducts(productIds, productType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
                null
            }
        } ?: emptyList()

        _productsFlow.value = products
    }

    fun setEmptyProducts() {
        _productsFlow.value = emptyList()
    }

    private suspend fun getProductDetails(client: BillingClient, params: QueryProductDetailsParams): List<ProductDetails> = suspendCancellableCoroutine { continuation ->
        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                continuation.resume(productDetailsList.productDetailsList)
            } else {
                continuation.resumeWithException(BillingException(billingResult))
            }
        }
    }

    private suspend fun getProducts(
        productIds: List<String>,
        productType: String = ProductType.INAPP
    ): List<ProductDetails> = ready { client ->
        if (productIds.isEmpty()) {
            return@ready emptyList()
        }

        try {
            val productList = productIds.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(productType)
                    .build()
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            getProductDetails(client, params)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("Failed to get products", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            emptyList()
        }
    }

    private fun log(msg: String, e: Throwable? = null) {
        if (e == null) {
            L.d("BillingManager", msg)
        } else {
            L.e("BillingManager", e.message ?: msg)
            L.e("BillingManager", msg, e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private suspend fun getPendingPurchases(client: BillingClient): List<Purchase> {
        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.INAPP)
            return queryPurchases(client, params.build())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("Failed to get pending purchases", e)
            return emptyList()
        }
    }

    private suspend fun queryPurchases(client: BillingClient, params: QueryPurchasesParams): List<Purchase> = suspendCancellableCoroutine { continuation ->
        client.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                continuation.resume(purchasesList)
            } else {
                continuation.resumeWithException(BillingException(billingResult))
            }
        }
    }

    suspend fun requestPurchase(
        activity: Activity,
        productDetails: ProductDetails
    ) = ready { client ->
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = client.launchBillingFlow(activity, billingFlowParams)
        if (!result.isSuccess) {
            log("Failed to launch billing flow", BillingException(result))
        }
        result.isSuccess
    }

    private suspend fun requestCountry(client: BillingClient): String? = suspendCancellableCoroutine { continuation ->
        client.getBillingConfigAsync(GetBillingConfigParams.newBuilder().build()) { billingResult, billingConfig ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && billingConfig != null) {
                continuation.resume(billingConfig.countryCode)
            } else {
                continuation.resume(null)
            }
        }
    }

    suspend fun getCountry() = ready { client ->
        requestCountry(client)
    }

    suspend fun restorePurchases(): List<Purchase> = ready {
        getPendingPurchases(it)
    }
}