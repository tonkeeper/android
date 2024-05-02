package com.tonapps.tonkeeper.fragment.tonconnect.auth

import android.os.Bundle
import android.text.SpannableString
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.facebook.common.util.UriUtil
import com.facebook.drawee.view.SimpleDraweeView
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.core.tonconnect.models.TCData
import com.tonapps.tonkeeper.dialog.tc.TonConnectCryptoView
import com.tonapps.uikit.color.textAccentColor
import com.tonapps.uikit.color.textTertiaryColor
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppEventSuccessEntity
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.setColor
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.CheckBoxView
import uikit.widget.FrescoView
import uikit.widget.LoaderView
import uikit.widget.ProcessTaskView

class TCAuthFragment: BaseFragment(R.layout.dialog_ton_connect), BaseFragment.Modal {

    companion object {

        const val REPLY_ARG = "reply"

        private const val REQUEST_KEY = "request"
        private const val CALLBACK_KEY = "callback"

        fun newInstance(
            request: DAppRequestEntity,
            callbackKey: String? = null
        ): TCAuthFragment {
            val fragment = TCAuthFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(REQUEST_KEY, request)
                putString(CALLBACK_KEY, callbackKey)
            }
            return fragment
        }
    }

    private val callbackKey: String? by lazy { arguments?.getString(CALLBACK_KEY) }

    private val viewModel: TCAuthViewModel by viewModel { parametersOf(arguments?.getParcelable(REQUEST_KEY)!!) }

    private lateinit var closeView: View
    private lateinit var loaderView: LoaderView
    private lateinit var contentView: View
    private lateinit var appIconView: AppCompatImageView
    private lateinit var siteIconView: SimpleDraweeView
    private lateinit var nameView: AppCompatTextView
    private lateinit var descriptionView: AppCompatTextView
    private lateinit var connectButton: Button
    private lateinit var connectProcessView: ProcessTaskView
    private lateinit var cryptoView: TonConnectCryptoView
    private lateinit var allowNotificationView: View
    private lateinit var allowNotificationCheckbox: CheckBoxView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener {
            cancelCallback()
            finish()
        }

        loaderView = view.findViewById(R.id.loader)
        loaderView.visibility = View.VISIBLE

        contentView = view.findViewById(R.id.content)
        contentView.visibility = View.GONE

        appIconView = view.findViewById(R.id.app_icon)

        siteIconView = view.findViewById(R.id.site_icon)
        nameView = view.findViewById(R.id.name)
        descriptionView = view.findViewById(R.id.description)

        connectButton = view.findViewById(R.id.connect_button)
        connectButton.visibility = View.VISIBLE
        connectButton.setOnClickListener { connectWallet() }

        connectProcessView = view.findViewById(R.id.connect_process)
        connectProcessView.visibility = View.GONE

        cryptoView = view.findViewById(R.id.crypto)

        allowNotificationView = view.findViewById(R.id.allow_notification)
        allowNotificationCheckbox = view.findViewById(R.id.allow_notification_checkbox)
        allowNotificationCheckbox.checked = true

        allowNotificationView.setOnClickListener {
            allowNotificationCheckbox.toggle()
        }

        collectFlow(viewModel.dataState, ::setData)
    }

    private fun setData(data: TCData) {
        cryptoView.setKey(data.accountId.toUserFriendly(testnet = data.testnet))
        siteIconView.setImageURI(data.manifest.iconUrl)
        setName(data.host)
        setDescription(data.manifest.name, data.shortAddress)

        loaderView.visibility = View.GONE
        contentView.visibility = View.VISIBLE
    }

    private fun setName(host: String) {
        val name = getString(Localization.ton_connect_title, host)
        val spannableString = SpannableString(name)
        spannableString.setColor(requireContext().textAccentColor, name.length - host.length, name.length)
        nameView.text = spannableString
    }

    private fun setDescription(name: String, shortAddress: String) {
        val description = getString(Localization.ton_connect_description, name, shortAddress)
        val spannableString = SpannableString(description)
        spannableString.setColor(requireContext().textTertiaryColor, description.length - shortAddress.length, description.length)
        descriptionView.text = spannableString
    }

    private fun connectWallet() {
        connectButton.visibility = View.GONE
        connectProcessView.visibility = View.VISIBLE
        connectProcessView.state = ProcessTaskView.State.LOADING

        viewModel.connect(requireContext(), allowNotificationCheckbox.checked).catch {
            setFailure()
        }.onEach {
            setSuccess(it)
        }.launchIn(lifecycleScope)
    }

    private fun setSuccess(result: DAppEventSuccessEntity) {
        connectProcessView.state = ProcessTaskView.State.SUCCESS
        finalDelay()
        callbackKey?.let {
            navigation?.setFragmentResult(it, Bundle().apply {
                putString(REPLY_ARG, result.toJSON().toString())
            })
        }
    }

    private fun setFailure() {
        connectProcessView.state = ProcessTaskView.State.FAILED
        finalDelay()
    }

    private fun cancelCallback() {
        callbackKey?.let {
            navigation?.setFragmentResult(it, Bundle())
        }
    }

    private fun finalDelay() {
        postDelayed(1000) {
            finish()
        }
    }
}