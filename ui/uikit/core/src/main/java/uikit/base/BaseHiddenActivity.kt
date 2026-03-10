package uikit.base

import android.app.Activity
import android.os.Bundle

abstract class BaseHiddenActivity: Activity() {

    private companion object {
        private const val KEY_AWAITING_RESULT = "awaiting_result"
    }

    private var waitingForActivityResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        restoreState(savedInstanceState)
        if (waitingForActivityResult) {
            return
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            waitingForActivityResult = savedInstanceState.getBoolean(KEY_AWAITING_RESULT, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_AWAITING_RESULT, waitingForActivityResult)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        super.finish()
        waitingForActivityResult = false
    }
}