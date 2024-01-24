package com.tonapps.singer.screen.key

import android.os.Bundle
import android.view.View
import androidx.core.view.doOnLayout
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.core.TonkeeperApp
import com.tonapps.singer.core.password.Password
import com.tonapps.singer.extensions.copyToClipboard
import com.tonapps.singer.screen.name.NameFragment
import com.tonapps.singer.core.password.PasswordPrompt
import com.tonapps.singer.screen.phrase.PhraseFragment
import com.tonapps.singer.short8
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import org.ton.api.pub.PublicKeyEd25519
import uikit.base.BaseFragment
import uikit.dialog.alert.AlertDialog
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.round
import uikit.list.ListCell
import uikit.list.ListCell.Companion.drawable
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ActionCellView
import uikit.widget.HeaderView
import uikit.widget.SquareImageView

class KeyFragment: BaseFragment(R.layout.fragment_key), BaseFragment.SwipeBack {

    companion object {

        private const val KEY_ID = "key_id"

        fun newInstance(id: Long): KeyFragment {
            val fragment = KeyFragment()
            fragment.arguments = Bundle().apply {
                putLong(KEY_ID, id)
            }
            return fragment
        }
    }

    private val id: Long by lazy { requireArguments().getLong(KEY_ID) }
    private val keyViewModel: KeyViewModel by viewModel { parametersOf(id) }

    private val authenticationCallback = object : PasswordPrompt.AuthenticationCallback() {

        override fun onAuthenticationResult(result: Password.Result) {
            if (result is Password.Result.Success) {
                keyViewModel.delete()
                finish()
            }
        }
    }

    private lateinit var passwordPrompt: PasswordPrompt

    private lateinit var headerView: HeaderView
    private lateinit var scrollView: NestedScrollView
    private lateinit var exportQrView: View
    private lateinit var qrView: SquareImageView
    private lateinit var exportLocalView: View
    private lateinit var nameView: ActionCellView
    private lateinit var hexAddressView: ActionCellView
    private lateinit var phraseView: View
    private lateinit var deleteView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passwordPrompt = PasswordPrompt(this, authenticationCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        exportQrView = view.findViewById(R.id.export_qr)
        exportQrView.background = ListCell.Position.SINGLE.drawable(requireContext())
        exportQrView.round(requireContext().getDimensionPixelSize(uikit.R.dimen.cornerMedium))

        qrView = view.findViewById(R.id.qr)

        exportLocalView = view.findViewById(R.id.export_local)

        nameView = view.findViewById(R.id.name)
        nameView.background = ListCell.Position.FIRST.drawable(requireContext())
        nameView.setOnClickListener { navigation?.add(NameFragment.newInstance(id)) }

        hexAddressView = view.findViewById(R.id.hex)
        hexAddressView.background = ListCell.Position.MIDDLE.drawable(requireContext())

        phraseView = view.findViewById(R.id.phrase)
        phraseView.background = ListCell.Position.LAST.drawable(requireContext())
        phraseView.setOnClickListener { openRecoveryPhrase() }

        deleteView = view.findViewById(R.id.delete_key)
        deleteView.setOnClickListener { deleteKey() }

        scrollView = view.findViewById(R.id.scroll)
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            headerView.divider = scrollY > 0
        }

        keyViewModel.keyEntity.onEach(::applyEntity).launchIn(lifecycleScope)
    }

    override fun onEndShowingAnimation() {
        super.onEndShowingAnimation()
        qrView.doOnLayout {
            requestBitmap(it.width, it.height)
        }
    }

    private fun applyEntity(key: KeyEntity) {
        setName(key.name)
        setHex(key.hex)
        setExportByUri(key.publicKey, key.name)
    }

    private fun openRecoveryPhrase() {
        navigation?.add(PhraseFragment.newInstance(id))
    }

    private fun requestBitmap(width: Int, height: Int) {
        keyViewModel.requestQrBitmap(width, height).onEach {
            qrView.setImageBitmap(it)
        }.launchIn(lifecycleScope)
    }

    private fun setName(name: String) {
        headerView.title = name
        nameView.subtitle = name
    }

    private fun setHex(hex: String) {
        hexAddressView.subtitle = hex.short8
        hexAddressView.setOnClickListener {
            requireContext().copyToClipboard(hex)
        }
    }

    private fun setExportByUri(publicKey: PublicKeyEd25519, name: String) {
        val uri = TonkeeperApp.buildExportUri(publicKey, name)
        exportLocalView.setOnClickListener {
            TonkeeperApp.openOrInstall(requireContext(), uri)
        }
    }

    private fun deleteKey() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setColoredButtons()
        builder.setTitle(R.string.delete_key_question)
        builder.setMessage(R.string.delete_key_question_subtitle)
        builder.setNegativeButton(R.string.delete_key) { _ ->
            passwordPrompt.authenticate()
        }
        builder.setPositiveButton(R.string.cancel)
        builder.show()
    }

}