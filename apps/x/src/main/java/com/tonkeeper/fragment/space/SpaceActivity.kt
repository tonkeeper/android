package com.tonkeeper.fragment.space

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.tonkeeper.fragment.settings.accounts.AccountsScreen
import uikit.base.BaseActivity

class SpaceActivity: BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(AccountsScreen.DeepLink)
        intent.`package` = packageName
        startActivity(intent)
        finish()
    }
}