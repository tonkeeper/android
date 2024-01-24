package com.tonapps.singer.screen.main.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.singer.R
import com.tonapps.singer.screen.add.AddFragment
import com.tonapps.singer.screen.camera.CameraFragment
import com.tonapps.singer.screen.main.list.MainItem
import com.tonapps.singer.screen.settings.SettingsFragment

class MainActionsHolder(
    parent: ViewGroup
): MainHolder<MainItem.Actions>(parent, R.layout.view_main_actions) {

    private val scanView = findViewById<View>(R.id.scan)
    private val addView = findViewById<View>(R.id.add)
    private val settingsView = findViewById<View>(R.id.settings)

    init {
        scanView.setOnClickListener { nav?.add(CameraFragment.newInstance()) }
        addView.setOnClickListener { nav?.add(AddFragment.newInstance()) }
        settingsView.setOnClickListener { nav?.add(SettingsFragment.newInstance()) }
    }

    override fun onBind(item: MainItem.Actions) {

    }

}