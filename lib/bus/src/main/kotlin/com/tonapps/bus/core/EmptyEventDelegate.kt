package com.tonapps.bus.core

import android.net.Uri
import com.tonapps.bus.core.contract.EventDelegate

class EmptyEventDelegate : EventDelegate {
    override fun openRefDeeplink(deeplink: String) = Unit
    override fun batterySuccess(type: String, promo: String, token: String, size: String?) = Unit

    override fun swapOpen(uri: Uri, native: Boolean) = Unit
    override fun swapClick(
        jettonSymbolFrom: String,
        jettonSymbolTo: String,
        native: Boolean,
        providerName: String
    ) = Unit

    override fun swapConfirm(
        jettonSymbolFrom: String,
        jettonSymbolTo: String,
        providerName: String,
        providerUrl: String,
        native: Boolean
    ) = Unit

    override fun swapSuccess(
        jettonSymbolFrom: String,
        jettonSymbolTo: String,
        providerName: String,
        providerUrl: String,
        native: Boolean
    ) = Unit

    override fun tcRequest(url: String) = Unit
    override fun tcConnect(url: String, pushEnabled: Boolean) = Unit
    override fun tcViewConfirm(url: String, address: String) = Unit
    override fun tcSendSuccess(url: String, address: String, feePaid: String) = Unit

    override fun onRampOpen(source: String) = Unit
    override fun onRampEnterAmount(
        type: String,
        sellAsset: String,
        buyAsset: String,
        countryCode: String
    ) = Unit

    override fun onRampOpenWebview(
        type: String,
        sellAsset: String,
        buyAsset: String,
        countryCode: String,
        paymentMethod: String?,
        providerName: String,
        providerDomain: String
    ) = Unit

    override fun onRampClick(
        type: String,
        placement: String,
        location: String,
        name: String,
        url: String
    ) = Unit

    override fun trackPushClick(pushId: String, payload: String) = Unit
    override fun trackStoryClick(
        storiesId: String,
        title: String,
        type: String,
        payload: String, index: Int
    ) = Unit

    override fun trackStoryView(storiesId: String, index: Int) = Unit
    override fun trackStoryOpen(storiesId: String, from: String) = Unit

    override fun dappClick(url: String, name: String, source: String, country: String) = Unit
    override fun dappSharingCopy(name: String, from: String, url: String) = Unit
}
