package com.tonapps.wallet.data.attribution.branch

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import com.tonapps.wallet.data.attribution.AttributedClick
import com.tonapps.wallet.data.attribution.branchUtm
import com.tonapps.wallet.data.attribution.entities.AttributionEvent
import com.tonapps.wallet.data.attribution.entities.AttributionProvider
import com.tonapps.wallet.data.attribution.entities.AttributionTouch
import com.tonapps.wallet.data.attribution.toAppDeepLink
import com.tonapps.wallet.data.attribution.withCampaignUtm
import io.branch.referral.Branch
import io.branch.referral.Defines
import org.json.JSONObject

internal class BranchAttribution {

    private var isInitialized = false

    fun initialize(application: Application, key: String, isDebug: Boolean) {
        if (key.isBlank()) {
            return
        }
        if (isDebug) {
            Branch.enableLogging()
        }
        Branch.getAutoInstance(application, key).setWhiteListedSchemes(COLLECTED_LINK_PATTERNS)
        isInitialized = true
    }

    fun isBranchLink(uri: Uri): Boolean {
        if (uri.host in HOSTS) {
            return true
        }
        if (uri.scheme != URI_SCHEME || uri.host != URI_SCHEME_HOST) {
            return false
        }
        return uri.getQueryParameter(Defines.Jsonkey.LinkClickID.key) != null
    }

    fun startSession(activity: Activity, onClick: (AttributedClick) -> Unit) {
        if (!isInitialized) {
            return
        }
        Branch.sessionBuilder(activity)
            .withCallback(sessionListener(onClick))
            .withData(activity.intent?.data)
            .init()
    }

    fun restartSession(activity: Activity, intent: Intent, onClick: (AttributedClick) -> Unit) {
        if (!isInitialized || !intent.getBooleanExtra(Defines.IntentKeys.ForceNewBranchSession.key, false)) {
            return
        }
        Branch.sessionBuilder(activity)
            .withCallback(sessionListener(onClick))
            .reInit()
    }

    private fun sessionListener(onClick: (AttributedClick) -> Unit): Branch.BranchReferralInitListener {
        return Branch.BranchReferralInitListener { params, error ->
            if (error == null && params != null && params.optBoolean(Defines.Jsonkey.Clicked_Branch_Link.key)) {
                onClick(params.toClick())
            }
        }
    }

    private fun JSONObject.toClick(): AttributedClick {
        val event = if (optBoolean(Defines.Jsonkey.IsFirstSession.key)) {
            AttributionEvent.Install
        } else {
            AttributionEvent.Open
        }
        val params = keys().asSequence().associateWith { key -> optString(key) }
        val utm = branchUtm(params)
        return AttributedClick(
            touch = AttributionTouch(
                provider = AttributionProvider.Branch,
                event = event,
                attributedAt = System.currentTimeMillis(),
                link = optString(REFERRING_LINK).ifBlank { null },
                params = params,
            ),
            deepLink = if (optBoolean(MATCH_GUARANTEED)) {
                val target = DEEP_LINK_KEYS.firstNotNullOfOrNull { key -> optString(key).toAppDeepLink() }?.toString()
                    ?: APP_LINK.takeUnless { utm.isEmpty }
                target?.withCampaignUtm(utm)?.let(Uri::parse)
            } else {
                null
            },
        )
    }

    private companion object {
        const val MATCH_GUARANTEED = "+match_guaranteed"
        const val REFERRING_LINK = "~referring_link"
        const val URI_SCHEME = "tonkeeper"
        const val URI_SCHEME_HOST = "open"
        const val APP_LINK = "$URI_SCHEME://"

        val HOSTS = setOf("go.tonkeeper.com", "x1zxr.app.link", "x1zxr-alternate.app.link")

        val COLLECTED_LINK_PATTERNS = HOSTS.map { host ->
            "https?://${host.replace(".", "\\.")}([/?#].*)?"
        } + "$URI_SCHEME://$URI_SCHEME_HOST([/?#].*)?"

        val DEEP_LINK_KEYS = listOf(
            Defines.Jsonkey.AndroidDeepLinkPath.key,
            Defines.Jsonkey.DeepLinkPath.key,
            Defines.Jsonkey.CanonicalUrl.key,
            "\$original_web_url",
        )
    }
}
