package com.tonapps.scanner

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.Scanner.ScannerErrorType
import com.tonapps.bus.scanner.ScanAnalytics
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import com.tonapps.extensions.getParcelableCompat
import uikit.base.BaseFragment
import uikit.extensions.activity

// SwipeBack so the scanner can be dragged away and follows the predictive back
// preview like the other stacked screens.
class ScannerFragment : ComposableFragment(), BaseFragment.SwipeBack {

    override val fragmentName: String = "ScannerFragment"

    private val entryUtm: UtmParameters
        get() = arguments?.getParcelableCompat(ARG_UTM) ?: UtmParameters.Empty

    private val scanAnalytics = ScanAnalytics.default()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanAnalytics.open(entryUtm)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? NavigationDelegate
        setContent {
            ScannerScreen(
                onResult = { value, input ->
                    val opened = delegate?.onProcessDeeplink(value, fromQR = true) == true
                    if (opened) {
                        scanAnalytics.success(value, input)
                    } else {
                        scanAnalytics.error(value, input, ScannerErrorType.InvalidLink)
                    }
                    finish()
                },
                onClose = { finish() },
                onNoCode = scanAnalytics::noCode,
                isFullScreen = true,
            )
        }
    }

    companion object {
        private const val ARG_UTM = "utm"

        fun newInstance(utm: UtmParameters = UtmParameters.Empty): ScannerFragment {
            val fragment = ScannerFragment()
            fragment.arguments = bundleOf(ARG_UTM to utm)
            return fragment
        }
    }
}
