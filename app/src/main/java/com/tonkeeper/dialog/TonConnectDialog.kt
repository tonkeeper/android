package com.tonkeeper.dialog

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.drawee.view.SimpleDraweeView
import com.tonkeeper.R
import com.tonkeeper.api.shortAddress
import com.tonkeeper.api.userLikeAddress
import com.tonkeeper.core.tonconnect.models.TCData
import uikit.base.BaseSheetDialog
import uikit.widget.LoaderView

class TonConnectDialog(
    context: Context,
    private val doOnConnect: ((data: TCData) -> Unit)
): BaseSheetDialog(context) {

    private val loaderView: LoaderView
    private val contentView: View
    private val appIconView: SimpleDraweeView
    private val siteIconView: SimpleDraweeView
    private val nameView: AppCompatTextView
    private val descriptionView: AppCompatTextView
    private val connectButton: Button
    private val connectLoaderView: LoaderView
    private var data: TCData? = null

    init {
        setContentView(R.layout.dialog_ton_connect)
        loaderView = findViewById(R.id.loader)!!
        contentView = findViewById(R.id.content)!!
        appIconView = findViewById(R.id.app_icon)!!
        appIconView.setImageURI("res:///${R.raw.tonkeeper_logo}")
        siteIconView = findViewById(R.id.site_icon)!!
        nameView = findViewById(R.id.name)!!
        descriptionView = findViewById(R.id.description)!!
        connectButton = findViewById(R.id.connect_button)!!
        connectButton.setOnClickListener { connectWallet() }
        connectLoaderView = findViewById(R.id.connect_loader)!!
    }

    private fun connectWallet() {
        connectButton.visibility = View.GONE

        connectLoaderView.visibility = View.VISIBLE
        connectLoaderView.resetAnimation()

        data?.let(doOnConnect)
    }

    override fun show() {
        super.show()
        loaderView.visibility = View.VISIBLE
        loaderView.resetAnimation()

        contentView.visibility = View.GONE
    }

    fun setData(
        d: TCData,
    ) {

        siteIconView.setImageURI(d.manifest.iconUrl)
        nameView.text = context.getString(R.string.ton_connect_title, d.manifest.name)
        descriptionView.text = context.getString(R.string.ton_connect_description, d.host, d.shortAddress, "V")

        data = d

        showContent()
    }

    private fun showContent() {
        loaderView.visibility = View.GONE
        loaderView.stopAnimation()

        contentView.visibility = View.VISIBLE
    }

}