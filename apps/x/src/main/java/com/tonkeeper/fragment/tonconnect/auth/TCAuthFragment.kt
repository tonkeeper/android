package com.tonkeeper.fragment.tonconnect.auth

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.viewModels
import com.facebook.common.util.UriUtil
import com.facebook.drawee.view.SimpleDraweeView
import com.tonapps.tonkeeperx.R
import com.tonkeeper.core.tonconnect.TonConnect
import com.tonkeeper.core.tonconnect.models.TCData
import com.tonkeeper.core.tonconnect.models.TCRequest
import com.tonkeeper.dialog.tc.TonConnectCryptoView
import com.tonkeeper.extensions.launch
import com.tonkeeper.fragment.passcode.lock.LockScreen
import ton.extensions.toUserFriendly
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.FrescoView
import uikit.widget.LoaderView
import uikit.widget.ProcessTaskView

class TCAuthFragment: BaseFragment(R.layout.dialog_ton_connect), BaseFragment.Modal {

    companion object {

        private const val TC_REQUEST = "tonconnect"

        private const val REQUEST_KEY = "request"

        fun newInstance(request: TCRequest): TCAuthFragment {
            val fragment = TCAuthFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(REQUEST_KEY, request)
            }
            return fragment
        }
    }

    private val request: TCRequest by lazy {
        arguments?.getParcelable(REQUEST_KEY)!!
    }

    private val viewModel: TCAuthViewModel by viewModels()

    private lateinit var closeView: View
    private lateinit var loaderView: LoaderView
    private lateinit var contentView: View
    private lateinit var appIconView: FrescoView
    private lateinit var siteIconView: SimpleDraweeView
    private lateinit var nameView: AppCompatTextView
    private lateinit var descriptionView: AppCompatTextView
    private lateinit var connectButton: Button
    private lateinit var connectProcessView: ProcessTaskView
    private lateinit var cryptoView: TonConnectCryptoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigation?.setFragmentResultListener(TC_REQUEST) { _, _ ->
            viewModel.connect()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener { finish() }

        loaderView = view.findViewById(R.id.loader)
        loaderView.visibility = View.VISIBLE

        contentView = view.findViewById(R.id.content)
        contentView.visibility = View.GONE

        appIconView = view.findViewById(R.id.app_icon)
        appIconView.setImageURI(UriUtil.getUriForResourceId(R.mipmap.ic_launcher))
        siteIconView = view.findViewById(R.id.site_icon)
        nameView = view.findViewById(R.id.name)
        descriptionView = view.findViewById(R.id.description)

        connectButton = view.findViewById(R.id.connect_button)
        connectButton.visibility = View.VISIBLE
        connectButton.setOnClickListener { connectWallet() }

        connectProcessView = view.findViewById(R.id.connect_process)
        connectProcessView.visibility = View.GONE

        cryptoView = view.findViewById(R.id.crypto)

        viewModel.dataState.launch(this) {
            val data = it ?: return@launch
            setData(data)
        }

        viewModel.connectState.launch(this) {
            if (it == ConnectState.Success) {
                setSuccess()
            } else if (it == ConnectState.Error) {
                setFailure()
            }
        }
        
        viewModel.requestData(request)
    }

    private fun setData(data: TCData) {
        cryptoView.setKey(data.accountId.toUserFriendly())
        siteIconView.setImageURI(data.manifest.iconUrl)
        nameView.text = getString(R.string.ton_connect_title, data.manifest.name)
        descriptionView.text = getString(R.string.ton_connect_description, data.host, data.shortAddress, "V")

        loaderView.visibility = View.GONE
        contentView.visibility = View.VISIBLE
    }

    private fun connectWallet() {
        connectButton.visibility = View.GONE
        connectProcessView.visibility = View.VISIBLE
        connectProcessView.state = ProcessTaskView.State.LOADING

        navigation?.add(LockScreen.newInstance(TC_REQUEST))
    }

    private fun setSuccess() {
        TonConnect.from(requireContext())?.restartEventHandler()

        connectProcessView.state = ProcessTaskView.State.SUCCESS
        finalDelay()
    }

    private fun setFailure() {
        connectProcessView.state = ProcessTaskView.State.FAILED
        finalDelay()
    }

    private fun finalDelay() {
        postDelayed(1000) {
            finish()
        }
    }
}