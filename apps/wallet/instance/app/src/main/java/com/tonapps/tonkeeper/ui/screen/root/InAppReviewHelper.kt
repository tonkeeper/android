package com.tonapps.tonkeeper.ui.screen.root

import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.InappReview.InappReviewAction
import com.tonapps.core.flags.InAppReviewManager
import com.tonapps.log.L
import com.tonapps.tonkeeper.Environment

class InAppReviewHelper(
    private val activity: RootActivity,
    private val environment: Environment,
) {

    private val reviewManager: ReviewManager by lazy(LazyThreadSafetyMode.NONE) {
        ReviewManagerFactory.create(activity)
    }

    private var requested = false

    fun requestReview() {
        if (requested) {
            return
        }
        if (!isAvailable()) {
            L.d(TAG, "skipped, app was not installed from Google Play")
            return
        }
        if (activity.isFinishing) {
            return
        }

        requested = true
        reviewManager.requestReviewFlow().addOnCompleteListener(activity) { task ->
            if (task.isSuccessful) {
                startReviewFlow(task.result)
            } else {
                requested = false
                L.d(TAG, "requestReviewFlow failed: ${task.exception?.message}")
            }
        }
    }

    private fun startReviewFlow(reviewInfo: ReviewInfo) {
        if (activity.isFinishing) {
            requested = false
            return
        }

        AnalyticsHelper.Default.events.inappReview.inappReview(InappReviewAction.SentTransaction)
        InAppReviewManager.onSentTransactionReviewRequested()
        reviewManager.launchReviewFlow(activity, reviewInfo)
    }

    private fun isAvailable(): Boolean {
        return environment.isFromGooglePlay && environment.isGooglePlayServicesAvailable
    }

    private companion object {
        private const val TAG = "InAppReview"
    }
}
