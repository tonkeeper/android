package uikit.extensions

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel

val AndroidViewModel.context: Context
    get() = getApplication<Application>().applicationContext

fun AndroidViewModel.getString(resId: Int): String {
    return context.getString(resId)
}