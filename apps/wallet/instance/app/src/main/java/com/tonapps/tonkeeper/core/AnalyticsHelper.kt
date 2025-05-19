package com.tonapps.tonkeeper.core

import android.content.Context
import android.util.Log
import androidx.annotation.UiThread
import com.aptabase.Aptabase
import com.aptabase.InitOptions
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.StoryEntity
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

object AnalyticsHelper {

    /*var allEvents = arrayOf(
        "first_launch",
        "battery_success",
        "onramp_open",
        "onramp_click",
        "push_click",
        "browser_open",
        "story_click",
        "story_page_view",
        "story_open",
        "click_dapp",
        "battery_open",
        "battery_select",
        "scan_open",
        "collectibles_open",
        "history_open",
        "create_wallet",
        "import_wallet",
        "wallet_import",
        "browser_click",
        "collectibles_select",
        "receive_open",
        "send_open",
        "send_click",
        "send_confirm",
        "send_success",
        "settings_open",
        "settings_select",
        "delete_wallet",
        "staking_open",
        "swap_open",
        "swap_click",
        "swap_success",
        "token_open",
        "dapp_sharing_copy"
    )*/

    private data class QueuedEvent(
        val eventName: String,
        val props: Map<String, Any>
    )

    private val isInitialized = AtomicBoolean(false)
    private val eventQueue = ConcurrentLinkedQueue<QueuedEvent>()
    private val regexPrivateData: Regex by lazy {
        Regex("[a-f0-9]{64}|0:[a-f0-9]{64}")
    }

    private fun getAddressType(address: String): String {
        return when {
            address.startsWith("0:") -> "raw"
            address.startsWith("E") -> "bounce"
            else -> "non-bounce"
        }
    }

    @UiThread
    fun simpleTrackEvent(eventName: String, installId: String, props: MutableMap<String, Any> = hashMapOf()) {
        props["firebase_user_id"] = installId
        trackEvent(eventName, props)
    }

    private fun trackEvent(eventName: String, props: Map<String, Any> = hashMapOf()) {
        if (isInitialized.get()) {
            send(eventName, props)
        } else {
            eventQueue.offer(QueuedEvent(eventName, props))
        }
    }

    private fun send(eventName: String, props: Map<String, Any> = hashMapOf()) {
        Aptabase.instance.trackEvent(eventName, props)
    }

    @UiThread
    fun simpleTrackScreenEvent(eventName: String, installId: String, from: String) {
        simpleTrackEvent(eventName, installId, hashMapOf(
            "from" to from
        ))
    }

    fun setConfig(context: Context, config: ConfigEntity) {
        initAptabase(
            context = context,
            appKey = config.aptabaseAppKey,
            host = config.aptabaseEndpoint
        )
    }

    private fun removePrivateDataFromUrl(url: String): String {
        return url.replace(regexPrivateData, "X")
    }

    @UiThread
    fun tcRequest(installId: String, url: String) {
        val props = hashMapOf(
            "firebase_user_id" to installId,
            "dapp_url" to url
        )
        trackEvent("tc_request", props)
    }

    @UiThread
    fun dappSharingCopy(installId: String, name: String, from: String, url: String) {
        val props = hashMapOf(
            "firebase_user_id" to installId,
            "name" to name,
            "from" to from,
            "url" to url
        )
        trackEvent("dapp_sharing_copy", props)
    }

    @UiThread
    fun tcConnect(installId: String, url: String, pushEnabled: Boolean) {
        val props = hashMapOf(
            "firebase_user_id" to installId,
            "dapp_url" to url,
            "allow_notifications" to pushEnabled
        )
        trackEvent("tc_connect", props)
    }

    @UiThread
    fun tcViewConfirm(installId: String, url: String, address: String) {
        val props = hashMapOf(
            "firebase_user_id" to installId,
            "dapp_url" to url,
            "address_type" to getAddressType(address)
        )
        trackEvent("tc_view_confirm", props)
    }

    @UiThread
    fun tcSendSuccess(installId: String, url: String, address: String, feePaid: String) {
        val props = hashMapOf(
            "firebase_user_id" to installId,
            "dapp_url" to url,
            "address_type" to getAddressType(address),
            "network_fee_paid" to feePaid
        )
        trackEvent("tc_send_success", props)
    }

    @UiThread
    fun firstLaunch(installId: String, referrer: String?, deeplink: String?) {
        val props = hashMapOf(
            "firebase_user_id" to installId
        )
        referrer?.let {
            props["referrer"] = it
        }
        deeplink?.let {
            props["deeplink"] = it
        }
        trackEvent("first_launch", props)
    }

    @UiThread
    fun openRefDeeplink(installId: String, deeplink: String) {
        val props = hashMapOf(
            "firebase_user_id" to installId,
            "deeplink" to deeplink
        )
        trackEvent("ads_deeplink", props)
    }

    @UiThread
    fun batterySuccess(installId: String, type: String, promo: String, token: String) {
        simpleTrackEvent("battery_success", installId, hashMapOf(
            "type" to type,
            "promo" to promo,
            "jetton" to token
        ))
    }

    @UiThread
    fun onRampOpen(installId: String, source: String) {
        simpleTrackScreenEvent("onramp_open", installId, source)
    }

    @UiThread
    fun onRampClick(installId: String, type: String, placement: String, location: String, name: String, url: String) {
        trackEvent("onramp_click", hashMapOf(
            "firebase_user_id" to installId,
            "type" to type,
            "placement" to placement,
            "location" to location,
            "name" to name,
            "url" to url
        ))
    }

    @UiThread
    fun trackPushClick(installId: String, pushId: String, payload: String) {
        trackEvent("push_click", hashMapOf(
            "firebase_user_id" to installId,
            "push_id" to pushId,
            "payload" to removePrivateDataFromUrl(payload)
        ))
    }

    @UiThread
    fun trackStoryClick(installId: String, storiesId: String, button: StoryEntity.Button) {
        trackEvent("story_click", hashMapOf(
            "firebase_user_id" to installId,
            "story_id" to storiesId,
            "button_title" to button.title,
            "button_type" to button.type,
            "button_payload" to button.payload,
        ))
    }

    @UiThread
    fun trackStoryView(installId: String, storiesId: String, index: Int) {
        trackEvent("story_page_view", hashMapOf(
            "firebase_user_id" to installId,
            "story_id" to storiesId,
            "page_number" to index
        ))
    }

    @UiThread
    fun trackStoryOpen(installId: String, storiesId: String, from: String) {
        trackEvent("story_open", hashMapOf(
            "firebase_user_id" to installId,
            "story_id" to storiesId,
            "from" to from
        ))
    }

    @UiThread
    fun trackEventClickDApp(url: String, name: String, source: String, installId: String) {
        trackEvent("click_dapp", hashMapOf(
            "url" to url,
            "name" to name,
            "from" to source,
            "firebase_user_id" to installId,
        ))
    }

    private fun processEventQueue() {
        if (!isInitialized.get()) {
            return
        }
        while (eventQueue.isNotEmpty()) {
            val queuedEvent = eventQueue.poll()
            if (queuedEvent != null) {
                send(queuedEvent.eventName, queuedEvent.props)
            }
        }
    }

    private fun initAptabase(
        context: Context,
        appKey: String,
        host: String
    ) {
        Log.d("AnalyticsHelper", "initAptabase: appKey=$appKey, host=$host")
        try {
            val options = InitOptions(
                host = host
            )
            Aptabase.instance.initialize(context, appKey, options)
            if (isInitialized.compareAndSet(false, true)) {
                processEventQueue()
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}