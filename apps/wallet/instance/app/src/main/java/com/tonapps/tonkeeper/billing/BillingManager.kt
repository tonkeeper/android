package com.tonapps.tonkeeper.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.filterList
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

class BillingManager(
    context: Context,
    scope: CoroutineScope,
) : PurchasesUpdatedListener {

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _productsFlow = MutableStateFlow<List<ProductDetails>?>(null)
    val productsFlow = _productsFlow.asStateFlow().filterNotNull()

    private val _purchasesUpdatedFlow = MutableEffectFlow<PurchasesUpdated>()
    val purchasesUpdatedFlow = _purchasesUpdatedFlow.shareIn(scope, SharingStarted.Lazily, 0).distinctUntilChanged()

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (!result.isSuccess) {
            log("Purchases updated", BillingException(result))
        }

        if (result.isSuccess && !purchases.isNullOrEmpty()) {
            _purchasesUpdatedFlow.tryEmit(PurchasesUpdated(result, purchases))
        }
    }

    suspend fun consumeProduct(purchaseToken: String) = billingClient.ready { client ->
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        client.consumePurchase(params)
    }

    suspend fun loadProducts(
        productIds: List<String>,
        productType: String = ProductType.INAPP
    ) {
        val products = withTimeoutOrNull(5.seconds) {
            getProducts(productIds, productType)
        } ?: emptyList()

        _productsFlow.value = products
    }

    fun setEmptyProducts() {
        _productsFlow.value = emptyList()
    }

    private suspend fun getProductDetails(client: BillingClient, params: QueryProductDetailsParams): List<ProductDetails> = suspendCancellableCoroutine { continuation ->
        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                continuation.resume(productDetailsList)
            } else {
                continuation.resumeWithException(BillingException(billingResult))
            }
        }
    }

    private suspend fun getProducts(
        productIds: List<String>,
        productType: String = ProductType.INAPP
    ): List<ProductDetails> = billingClient.ready { client ->
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
        } catch (e: Throwable) {
            log("Failed to get products", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            emptyList()
        }
    }

    private fun log(msg: String, e: Throwable? = null) {
        if (e == null) {
            Log.d("BillingManager", msg)
        } else {
            Log.e("BillingManager", e.message ?: msg)
            Log.e("BillingManager", msg, e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private suspend fun getPendingPurchases(client: BillingClient): List<Purchase> {
        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.INAPP)

            return queryPurchases(client, params.build()).filter { !it.isAcknowledged }
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
        wallet: WalletEntity,
        productDetails: ProductDetails
    ) = billingClient.ready { client ->
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = client.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            log("Failed to launch billing flow", BillingException(result))
        }
        Unit
    }

    @OptIn(FlowPreview::class)
    fun productFlow(productId: String) = productsFlow.take(1).filterList { product ->
        product.productId == productId
    }.mapNotNull { it.firstOrNull() }.timeout(5.seconds)

    suspend fun restorePurchases(): List<Purchase> = billingClient.ready {
        getPendingPurchases(it)
    }
}