package com.tonapps.bus.core.contract

import android.net.Uri

interface EventDelegate {
    fun openRefDeeplink(deeplink: String)
    fun trackPushClick(pushId: String, payload: String)

    fun swapOpen(uri: Uri, native: Boolean)
    fun swapClick(
        jettonSymbolFrom: String,
        jettonSymbolTo: String,
        native: Boolean,
        providerName: String
    )

    fun swapConfirm(
        jettonSymbolFrom: String,
        jettonSymbolTo: String,
        providerName: String,
        providerUrl: String,
        native: Boolean
    )

    fun swapSuccess(
        jettonSymbolFrom: String,
        jettonSymbolTo: String,
        providerName: String,
        providerUrl: String,
        native: Boolean
    )

    fun dappSharingCopy(name: String, from: String, url: String)
    fun dappClick(url: String, name: String, source: String, country: String)

    fun tcRequest(url: String)
    fun tcConnect(url: String, pushEnabled: Boolean)
    fun tcViewConfirm(url: String, address: String)
    fun tcSendSuccess(url: String, address: String, feePaid: String)

    fun batterySuccess(type: String, promo: String, token: String, size: String?)

    fun onRampOpen(source: String)
    fun onRampEnterAmount(type: String, sellAsset: String, buyAsset: String, countryCode: String)
    fun onRampOpenWebview(
        type: String,
        sellAsset: String,
        buyAsset: String,
        countryCode: String,
        paymentMethod: String?,
        providerName: String,
        providerDomain: String
    )

    fun onRampClick(type: String, placement: String, location: String, name: String, url: String)

    fun trackStoryClick(
        storiesId: String,
        title: String,
        type: String,
        payload: String,
        index: Int
    )

    fun trackStoryView(storiesId: String, index: Int)
    fun trackStoryOpen(storiesId: String, from: String)
}