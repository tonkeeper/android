package com.tonapps.core.flags

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import com.tonapps.extensions.appVersionCode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@SuppressLint("StaticFieldLeak")
object InAppReviewManager {

    private const val PREFERENCES_NAME = "AppInAppReviewManager"

    private const val SENT_TRANSACTIONS_KEY = "sent_transactions"
    private const val LAST_REQUEST_DATE_KEY = "last_request_date"
    private const val LAST_REQUEST_VERSION_KEY = "last_request_version"

    private const val SENT_TRANSACTIONS_THRESHOLD = 3
    private const val REQUEST_COOLDOWN_MS = 180L * 24L * 60L * 60L * 1000L

    private lateinit var context: Context

    private val prefs by lazy {
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    }

    private val _requestFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val requestFlow = _requestFlow.asSharedFlow()

    fun initialize(context: Context) {
        this.context = context
    }

    fun onTransactionSent() {
        val count = prefs.getInt(SENT_TRANSACTIONS_KEY, 0) + 1
        prefs.edit {
            putInt(SENT_TRANSACTIONS_KEY, count)
        }
        if (isEligible(count)) {
            _requestFlow.tryEmit(Unit)
        }
    }

    fun onManualReviewRequested() {
        saveRequest(resetSentTransactions = false)
    }

    fun onSentTransactionReviewRequested() {
        saveRequest(resetSentTransactions = true)
    }

    private fun saveRequest(resetSentTransactions: Boolean) {
        prefs.edit {
            if (resetSentTransactions) {
                putInt(SENT_TRANSACTIONS_KEY, 0)
            }
            putLong(LAST_REQUEST_DATE_KEY, System.currentTimeMillis())
            putLong(LAST_REQUEST_VERSION_KEY, context.appVersionCode)
        }
    }

    private fun isEligible(count: Int): Boolean {
        if (WalletFeature.InAppReview.isDisabled) {
            return false
        }
        if (count < SENT_TRANSACTIONS_THRESHOLD) {
            return false
        }
        val lastRequestDate = prefs.getLong(LAST_REQUEST_DATE_KEY, 0L)
        if (System.currentTimeMillis() - lastRequestDate < REQUEST_COOLDOWN_MS) {
            return false
        }
        return prefs.getLong(LAST_REQUEST_VERSION_KEY, 0L) != context.appVersionCode
    }
}
