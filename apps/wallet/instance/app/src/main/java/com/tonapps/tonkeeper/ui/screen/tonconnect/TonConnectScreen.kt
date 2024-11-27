package com.tonapps.tonkeeper.ui.screen.tonconnect

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.ton.proof.TONProof
import com.tonapps.emoji.ui.EmojiView
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.short4
import com.tonapps.tonkeeper.ui.component.TonConnectCryptoView
import com.tonapps.tonkeeper.extensions.debugToast
import com.tonapps.tonkeeper.extensions.getWalletBadges
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerMode
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.color.textAccentColor
import com.tonapps.uikit.color.textTertiaryColor
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setColor
import uikit.extensions.setOnClickListener
import uikit.widget.CheckBoxView
import uikit.widget.FrescoView
import uikit.widget.LoaderView
import uikit.widget.ProcessTaskView
import java.util.concurrent.CancellationException

class TonConnectScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_tonconnect, ScreenContext.None), BaseFragment.Modal, BaseFragment.SingleTask {

    val contract = object : ResultContract<TonConnectResponse, TonConnectResponse> {

        private val KEY_RESPONSE = "response"

        override fun createResult(result: TonConnectResponse) = Bundle().apply {
            putParcelable(KEY_RESPONSE, result)
        }

        override fun parseResult(bundle: Bundle): TonConnectResponse {
            return bundle.getParcelableCompat<TonConnectResponse>(KEY_RESPONSE) ?: throw CancellationException("User canceled")
        }
    }

    private val args: TonConnectArgs by lazy { TonConnectArgs(requireArguments()) }

    override val viewModel: TonConnectViewModel by viewModel()

    private lateinit var loaderView: LoaderView
    private lateinit var bodyView: View
    private lateinit var cryptoView: TonConnectCryptoView
    private lateinit var appIconView: FrescoView
    private lateinit var titleView: AppCompatTextView
    private lateinit var descriptionView: AppCompatTextView
    private lateinit var walletPickerView: View
    private lateinit var walletColorView: View
    private lateinit var walletEmojiView: EmojiView
    private lateinit var walletNameView: AppCompatTextView
    private lateinit var walletTypesView: AppCompatTextView
    private lateinit var walletAddressView: AppCompatTextView
    private lateinit var warningView: AppCompatTextView
    private lateinit var button: Button
    private lateinit var taskView: ProcessTaskView
    private lateinit var pushView: View
    private lateinit var pushCheckBoxView: CheckBoxView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loaderView = view.findViewById(R.id.loader)
        bodyView = view.findViewById(R.id.body)

        view.setOnClickListener(R.id.close) { finish() }

        cryptoView = view.findViewById(R.id.crypto)
        appIconView = view.findViewById(R.id.app_icon)
        titleView = view.findViewById(R.id.title)
        descriptionView = view.findViewById(R.id.description)
        walletPickerView = view.findViewById(R.id.picker)
        walletColorView = view.findViewById(R.id.wallet_color)
        walletEmojiView = view.findViewById(R.id.wallet_emoji)
        walletNameView = view.findViewById(R.id.wallet_name)
        walletTypesView = view.findViewById(R.id.wallet_types)
        walletAddressView = view.findViewById(R.id.wallet_address)
        warningView = view.findViewById(R.id.warning)
        button = view.findViewById(R.id.button)

        taskView = view.findViewById(R.id.task)

        pushView = view.findViewById(R.id.push)
        pushCheckBoxView = view.findViewById(R.id.checkbox)
        pushCheckBoxView.checked = true
        pushView.setOnClickListener {
            pushCheckBoxView.toggle()
        }

        warningView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))

        val initialWallet = args.wallet
        if (initialWallet == null) {
            collectFlow(viewModel.stateFlow, ::applyState)
        } else {
            applyState(TonConnectScreenState.Data(initialWallet, false))
        }

        setDefaultState()
    }

    private fun connect(wallet: WalletEntity) {
        val proofPayload = args.proofPayload
        if (wallet.signer && proofPayload != null) {
            setResponse(wallet, proofError = BridgeError.methodNotSupported("SignerApp version incompatible. Installed version lacks tonProof signing capability."))
        } else if (proofPayload == null) {
            setResponse(wallet)
        } else {
            setLoadingState()
            lifecycleScope.launch {
                try {
                    val proof = viewModel.requestProof(wallet, args.app, proofPayload)
                    setResponse(wallet, proof)
                } catch (e: CancellationException) {
                    context?.debugToast(e)
                    setDefaultState()
                } catch (e: Throwable) {
                    context?.debugToast(e)
                    setFailedState()
                }
            }
        }
    }

    private fun setResponse(
        wallet: WalletEntity,
        proof: TONProof.Result? = null,
        proofError: BridgeError? = null,
    ) {
        setResponse(TonConnectResponse(
            notifications = pushCheckBoxView.checked,
            proof = proof,
            wallet = wallet,
            proofError = proofError
        ))
    }

    private fun setResponse(response: TonConnectResponse) {
        setSuccessState()
        setResult(contract.createResult(response), false)
        postDelayed(2000) {
            returnToApp()
            finish()
        }
    }

    private fun setTaskState() {
        button.visibility = View.GONE
        taskView.visibility = View.VISIBLE
    }

    private fun setLoadingState() {
        setTaskState()
        taskView.state = ProcessTaskView.State.LOADING
    }

    private fun setSuccessState() {
        setTaskState()
        taskView.state = ProcessTaskView.State.SUCCESS
    }

    private fun setFailedState() {
        setTaskState()
        taskView.state = ProcessTaskView.State.FAILED
        postDelayed(5000) { setDefaultState() }
    }

    private fun setDefaultState() {
        button.visibility = View.VISIBLE
        taskView.visibility = View.GONE
        taskView.state = ProcessTaskView.State.DEFAULT
    }

    private fun returnToApp() {
        val uri = args.returnUri ?: return
        if (uri.scheme == "tg" || uri.host == "t.me") {
            returnToTg(uri, args.fromPackageName)
        } else {
            returnToDefault(uri)
        }
    }

    private fun returnToTg(uri: Uri, fromPackageName: String?) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            fromPackageName?.let { intent.`package` = it }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            returnToDefault(uri)
        }
    }

    private fun returnToDefault(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            navigation?.toast(Localization.unknown_error)
        }
    }

    private fun applyState(state: TonConnectScreenState) {
        if (state is TonConnectScreenState.Data) {
            applyDataState(state)
        } else {
            navigation?.toast(Localization.not_supported)
            finish()
        }
    }

    private fun applyDataState(state: TonConnectScreenState.Data) {
        loaderView.visibility = View.GONE
        bodyView.visibility = View.VISIBLE

        appIconView.setImageURI(args.app.iconUrl)
        cryptoView.setKey(state.wallet.address)
        applyAppTitle(args.app.host)
        applyAppDescription(args.app.name, if (!state.hasWalletPicker) {
            state.wallet.address
        } else null)

        if (state.hasWalletPicker) {
            walletPickerView.visibility = View.VISIBLE
            applyWallet(state.wallet)
        } else {
            walletPickerView.visibility = View.GONE
        }

        button.setOnClickListener { connect(state.wallet) }
    }

    private fun applyAppTitle(host: String) {
        val name = getString(Localization.ton_connect_title, host)
        val spannableString = SpannableString(name)
        spannableString.setColor(requireContext().textAccentColor, name.length - host.length, name.length)
        titleView.text = spannableString
    }

    private fun applyAppDescription(name: String, walletAddress: String?) {
        val description: CharSequence = if (walletAddress != null) {
            val shortAddress = walletAddress.short4
            val text = getString(Localization.ton_connect_description, name, shortAddress).trim() + "."
            val spannableString = SpannableString(text)
            spannableString.setColor(requireContext().textTertiaryColor, (text.length - shortAddress.length) - 1, text.length - 1)
            spannableString
        } else {
            getString(Localization.ton_connect_description, name, "").trim() + ":"
        }

        descriptionView.text = description
    }

    private fun applyWallet(wallet: WalletEntity) {
        walletColorView.backgroundTintList = wallet.label.color.stateList
        walletEmojiView.setEmoji(wallet.label.emoji, Color.TRANSPARENT)
        walletNameView.text = wallet.label.name
        walletTypesView.text = requireContext().getWalletBadges(wallet.type, wallet.version)
        walletAddressView.text = wallet.address.short4
        walletPickerView.setOnClickListener { openWalletPicker(wallet) }
    }

    private fun openWalletPicker(wallet: WalletEntity) {
        val fragment = PickerScreen.newInstance(PickerMode.TonConnect(wallet.id))
        navigation?.addForResult(fragment) { bundle ->
            fragment.contract.parseResult(bundle)?.let {
                viewModel.setWallet(it)
            }
        }
    }

    companion object {

        fun newInstance(
            app: AppEntity,
            proofPayload: String?,
            returnUri: Uri?,
            wallet: WalletEntity?,
            fromPackageName: String?
        ): TonConnectScreen {
            val fragment = TonConnectScreen()
            fragment.setArgs(TonConnectArgs(app, proofPayload, returnUri, wallet, fromPackageName))
            return fragment
        }
    }
}