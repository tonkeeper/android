package com.tonapps.scanner

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import uikit.extensions.activity

class ScannerFragment : ComposableFragment() {

    override val fragmentName: String = "ScannerFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? NavigationDelegate
        setContent {
            ScannerScreen(
                onResult = { value ->
                    delegate?.onProcessDeeplink(value, fromQR = true)
                    finish()
                },
                onClose = { finish() },
                isFullScreen = true,
            )
        }
    }

    companion object {
        fun newInstance(): ScannerFragment = ScannerFragment()
    }
}
