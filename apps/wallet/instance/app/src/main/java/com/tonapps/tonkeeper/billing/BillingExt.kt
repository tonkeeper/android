package com.tonapps.tonkeeper.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase

val ProductDetails.priceFormatted: CharSequence
    get() = oneTimePurchaseOfferDetails!!.formattedPrice

val Purchase.walletId: String?
    get() = accountIdentifiers?.obfuscatedAccountId

val BillingResult.isSuccess: Boolean
    get() = responseCode == BillingClient.BillingResponseCode.OK
