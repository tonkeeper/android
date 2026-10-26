package uikit.base

import androidx.activity.BackEventCompat

interface PredictiveBackGesture {

    fun onPredictiveOnBackStarted(backEvent: BackEventCompat) {}

    fun onPredictiveBackProgressed(backEvent: BackEventCompat) {}

    fun onPredictiveBackCancelled() {}
}
