package com.tonapps.tonkeeper.core

import android.content.Context
import androidx.annotation.UiThread
import com.aptabase.Aptabase
import com.aptabase.InitOptions
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.StoryEntity

object AnalyticsHelper {

    fun setConfig(context: Context, config: ConfigEntity) {
        initAptabase(
            context = context,
            appKey = config.aptabaseAppKey,
            host = config.aptabaseEndpoint
        )
    }

    @UiThread
    fun firstLaunch(installId: String) {
        Aptabase.instance.trackEvent("first_launch", hashMapOf(
            "firebase_user_id" to installId
        ))
    }

    @UiThread
    fun onRampOpen(installId: String, source: String) {
        Aptabase.instance.trackEvent("onramp_open", hashMapOf(
            "firebase_user_id" to installId,
            "from" to source
        ))
    }

    @UiThread
    fun onRampClick(installId: String, type: String, placement: String, location: String, name: String, url: String) {
        Aptabase.instance.trackEvent("onramp_click", hashMapOf(
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
        Aptabase.instance.trackEvent("push_click", hashMapOf(
            "firebase_user_id" to installId,
            "push_id" to pushId,
            "payload" to payload
        ))
    }

    @UiThread
    fun trackBrowserOpen(installId: String, from: String) {
        Aptabase.instance.trackEvent("browser_open", hashMapOf(
            "firebase_user_id" to installId,
            "from" to from
        ))
    }

    @UiThread
    fun trackStoryClick(installId: String, storiesId: String, button: StoryEntity.Button) {
        Aptabase.instance.trackEvent("story_click", hashMapOf(
            "firebase_user_id" to installId,
            "story_id" to storiesId,
            "button_title" to button.title,
            "button_type" to button.type,
            "button_payload" to button.payload,
        ))
    }

    @UiThread
    fun trackStoryView(installId: String, storiesId: String, index: Int) {
        Aptabase.instance.trackEvent("story_page_view", hashMapOf(
            "firebase_user_id" to installId,
            "story_id" to storiesId,
            "page_number" to index
        ))
    }

    @UiThread
    fun trackStoryOpen(installId: String, storiesId: String, from: String) {
        Aptabase.instance.trackEvent("story_open", hashMapOf(
            "firebase_user_id" to installId,
            "story_id" to storiesId,
            "from" to from
        ))
    }

    @UiThread
    fun trackEvent(name: String, installId: String) {
        Aptabase.instance.trackEvent(name, hashMapOf(
            "firebase_user_id" to installId
        ))
    }

    @UiThread
    fun trackEventClickDApp(url: String, name: String, source: String, installId: String) {
        Aptabase.instance.trackEvent("click_dapp", hashMapOf(
            "url" to url,
            "name" to name,
            "from" to source,
            "firebase_user_id" to installId,
        ))
    }

    private fun initAptabase(
        context: Context,
        appKey: String,
        host: String
    ) {
        try {
            val options = InitOptions(
                host = host
            )
            Aptabase.instance.initialize(context, appKey, options)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}