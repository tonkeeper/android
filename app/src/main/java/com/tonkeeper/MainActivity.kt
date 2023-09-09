package com.tonkeeper

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.tonkeeper.ui.fragment.SettingsFragment
import com.tonkeeper.ui.fragment.WalletFragment
import com.tonkeeper.ui.widget.BottomTabsView

class MainActivity : AppCompatActivity() {

    private val walletFragment = WalletFragment()
    private val settingsFragment = SettingsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomTabs = findViewById<BottomTabsView>(R.id.bottom_tabs)
        bottomTabs.doOnClick = { _, itemId ->
            when (itemId) {
                R.id.wallet -> {
                    setFragment(walletFragment)
                }
                R.id.settings -> {
                    setFragment(settingsFragment)
                }
            }
        }

        setFragment(walletFragment)
    }

    private fun setFragment(fragment: Fragment) {
        val tr = supportFragmentManager.beginTransaction()
        tr.replace(R.id.fragment_container_view, fragment)
        tr.commit()
    }

}