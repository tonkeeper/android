package com.tonapps.tonkeeper.ui.screen.camera

import android.os.Bundle
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment

class CameraScreen: BaseFragment(R.layout.fragment_camera), BaseFragment.BottomSheet {


    companion object {

        private const val ARG_REQUEST = "request"

        fun newInstance(request: String): CameraScreen {
            val fragment = CameraScreen()
            fragment.arguments = Bundle().apply {
                putString(ARG_REQUEST, request)
            }
            return fragment
        }
    }
}