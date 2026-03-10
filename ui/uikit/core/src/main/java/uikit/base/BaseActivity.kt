package uikit.base

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat

open class BaseActivity: AppCompatActivity() {

    val windowInsetsController: WindowInsetsControllerCompat by lazy {
        WindowInsetsControllerCompat(window, window.decorView)
    }

}