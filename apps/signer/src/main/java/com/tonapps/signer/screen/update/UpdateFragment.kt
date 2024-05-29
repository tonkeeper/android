package com.tonapps.signer.screen.update

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import androidx.core.graphics.toRectF
import androidx.core.net.toUri
import com.tonapps.signer.R
import com.tonapps.signer.drawable.IconBackgroundDrawable
import com.tonapps.uikit.color.backgroundContentColor
import com.tonapps.uikit.color.separatorCommonColor
import uikit.base.BaseDrawable
import uikit.base.BaseFragment
import uikit.extensions.dp
import uikit.extensions.round
import uikit.widget.HeaderView

class UpdateFragment: BaseFragment(R.layout.fragment_update), BaseFragment.Modal {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<HeaderView>(R.id.header).doOnActionClick = { finish() }
        view.findViewById<View>(R.id.update).setOnClickListener { openGooglePlay() }
        view.findViewById<View>(R.id.later).setOnClickListener { finish() }

        val radius = 24f.dp
        val icon = view.findViewById<View>(R.id.icon)
        icon.background = IconBackgroundDrawable(requireContext(), radius)
        icon.round(radius)
    }

    private fun openGooglePlay() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = "market://details?id=${requireContext().packageName}".toUri()
        startActivity(intent)
    }

    companion object {
        fun newInstance() = UpdateFragment()
    }

}