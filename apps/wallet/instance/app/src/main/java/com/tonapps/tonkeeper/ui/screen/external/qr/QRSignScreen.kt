package com.tonapps.tonkeeper.ui.screen.external.qr

import android.os.Bundle
import android.view.View
import androidx.camera.view.PreviewView
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.ur.ResultType
import com.tonapps.ur.URDecoder
import com.tonapps.ur.registry.RegistryItem
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import uikit.base.BaseFragment
import uikit.drawable.HeaderDrawable
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.gone
import uikit.extensions.pinToBottomInsets
import uikit.extensions.round
import uikit.extensions.roundBottom
import uikit.extensions.scrollDown
import uikit.extensions.scrollView
import uikit.extensions.setOnClickListener
import uikit.extensions.topScrolled
import uikit.navigation.Navigation.Companion.navigation
import java.util.concurrent.atomic.AtomicBoolean

abstract class QRSignScreen: QRCameraScreen(R.layout.fragment_external_qr_sign), BaseFragment.BottomSheet {

    override lateinit var cameraView: PreviewView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerDrawable = HeaderDrawable(requireContext())

        val headerView = view.findViewById<View>(R.id.header)
        headerView.background = headerDrawable

        view.setOnClickListener(R.id.close) { finish() }

        val scrollView = view.findViewById<NestedScrollView>(R.id.scroll)
        scrollView.applyNavBottomPadding()

        val radius = requireContext().getDimensionPixelSize(uikit.R.dimen.cornerMedium)
        view.roundBottom(R.id.camera_frame, radius)
        view.round(R.id.content, radius)
        view.gone(R.id.transaction)
        view.gone(R.id.label)

        cameraView = view.findViewById(R.id.camera)

        collectFlow(scrollView.topScrolled, headerDrawable::setDivider)
    }
}