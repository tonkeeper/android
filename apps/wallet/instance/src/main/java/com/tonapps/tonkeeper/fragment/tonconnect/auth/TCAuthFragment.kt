package com.tonapps.tonkeeper.fragment.tonconnect.auth

import android.os.Bundle
import android.text.SpannableString
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.facebook.common.util.UriUtil
import com.facebook.drawee.view.SimpleDraweeView
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.core.tonconnect.TonConnect
import com.tonapps.tonkeeper.core.tonconnect.models.TCData
import com.tonapps.tonkeeper.core.tonconnect.models.TCRequest
import com.tonapps.tonkeeper.dialog.tc.TonConnectCryptoView
import com.tonapps.uikit.color.textAccentColor
import com.tonapps.uikit.color.textTertiaryColor
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.setColor
import uikit.widget.FrescoView
import uikit.widget.LoaderView
import uikit.widget.ProcessTaskView

class TCAuthFragment: BaseFragment(R.layout.dialog_ton_connect), BaseFragment.Modal {

    companion object {

        private const val REQUEST_KEY = "request"

        fun newInstance(request: TCRequest): TCAuthFragment {
            val fragment = TCAuthFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(REQUEST_KEY, request)
            }
            return fragment
        }
    }

    private val viewModel: TCAuthViewModel by viewModel { parametersOf(arguments?.getParcelable(REQUEST_KEY)!!) }

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

        collectFlow(viewModel.dataState, ::setData)
    }

    private fun setData(data: TCData) {
        cryptoView.setKey(data.accountId.toUserFriendly(testnet = data.testnet))
        siteIconView.setImageURI(data.manifest.iconUrl)
        setName(data.host)
        setDescription(data.host, data.shortAddress)

        loaderView.visibility = View.GONE
        contentView.visibility = View.VISIBLE
    }

    private fun setName(host: String) {
        val name = getString(Localization.ton_connect_title, host)
        val spannableString = SpannableString(name)
        spannableString.setColor(requireContext().textAccentColor, name.length - host.length, name.length)
        nameView.text = spannableString
    }

    private fun setDescription(host: String, shortAddress: String) {
        val description = getString(Localization.ton_connect_description, host, shortAddress)
        val spannableString = SpannableString(description)
        spannableString.setColor(requireContext().textTertiaryColor, description.length - shortAddress.length, description.length)
        descriptionView.text = spannableString
    }

    private fun connectWallet() {
        connectButton.visibility = View.GONE
        connectProcessView.visibility = View.VISIBLE
        connectProcessView.state = ProcessTaskView.State.LOADING

        viewModel.connect(requireContext()).catch {
            setFailure()
        }.onEach {
            setSuccess()
        }.launchIn(lifecycleScope)
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