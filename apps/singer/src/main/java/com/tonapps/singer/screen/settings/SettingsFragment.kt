package com.tonapps.singer.screen.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.singer.BuildConfig
import com.tonapps.singer.R
import uikit.base.BaseFragment
import uikit.widget.HeaderView

class SettingsFragment: BaseFragment(R.layout.fragment_settings), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = SettingsFragment()
    }

    private lateinit var headerView: HeaderView
    private lateinit var supportView: View
    private lateinit var versionView: AppCompatTextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        supportView = view.findViewById(R.id.support)
        supportView.setOnClickListener { openSupport() }

        versionView = view.findViewById(R.id.version)
        versionView.text = getString(R.string.version, BuildConfig.VERSION_NAME)
    }

    private fun openSupport() {
        val uri = Uri.parse("mailto:support@tonkeeper.com")
        val intent = Intent(Intent.ACTION_SENDTO, uri)
        startActivity(intent)
    }
}