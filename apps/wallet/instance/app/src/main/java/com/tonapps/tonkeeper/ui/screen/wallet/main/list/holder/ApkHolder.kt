package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.koin.apkManager
import com.tonapps.tonkeeper.manager.apk.APKManager
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization

class ApkHolder(parent: ViewGroup): Holder<Item.ApkStatus>(parent, R.layout.view_wallet_apk) {

    private val apkManager: APKManager? by lazy {
        context.apkManager
    }

    private val appTitle: String
        get() = context.getString(Localization.app_name)

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val iconView = findViewById<AppCompatImageView>(R.id.icon)
    private val descriptionView = findViewById<AppCompatTextView>(R.id.description)

    override fun onBind(item: Item.ApkStatus) {
        when (item.status) {
            is APKManager.Status.Downloaded -> setReadyToInstall(item.status)
            is APKManager.Status.Downloading -> downloading(item.status)
            is APKManager.Status.Failed -> failed(item.status)
            else -> { }
        }
    }

    private fun setReadyToInstall(status: APKManager.Status.Downloaded) {
        titleView.text = "%s %s".format(appTitle, status.apk.apkName.value)
        descriptionView.setText(Localization.tap_to_update)
        iconView.setImageResource(UIKitIcon.ic_update_24)
        itemView.setOnClickListener {
            apkManager?.install(context, status.file)
        }
    }

    private fun downloading(status: APKManager.Status.Downloading) {
        titleView.text = "%s %s".format(appTitle, status.apk.apkName.value)
        descriptionView.text = "${getString(Localization.downloading)} %d%%".format(status.progress)
        iconView.setImageResource(UIKitIcon.ic_download_28)
        itemView.setOnClickListener(null)
    }

    private fun failed(status: APKManager.Status.Failed) {
        titleView.text = "%s %s".format(appTitle, status.apk.apkName.value)
        descriptionView.setText(Localization.download_error)
        itemView.setOnClickListener {
            apkManager?.download(status.apk)
        }
    }

}