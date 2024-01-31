package com.tonapps.signer.screen.key

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.tonapps.signer.R
import com.tonapps.signer.core.entities.KeyEntity
import com.tonapps.signer.TonkeeperApp
import com.tonapps.signer.extensions.authorizationRequiredError
import com.tonapps.signer.extensions.copyToClipboard
import com.tonapps.signer.screen.name.NameFragment
import com.tonapps.signer.extensions.short8
import com.tonapps.signer.password.Password
import com.tonapps.signer.screen.phrase.PhraseFragment
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import org.ton.api.pub.PublicKeyEd25519
import qr.QRView
import uikit.base.BaseFragment
import uikit.dialog.alert.AlertDialog
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.round
import uikit.list.ListCell
import uikit.list.ListCell.Companion.drawable
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ActionCellView
import uikit.widget.HeaderView

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

    private lateinit var headerView: HeaderView
    private lateinit var scrollView: NestedScrollView
    private lateinit var exportQrView: View
    private lateinit var qrView: QRView
    private lateinit var exportLocalView: View
    private lateinit var nameView: ActionCellView
    private lateinit var hexAddressView: ActionCellView
    private lateinit var phraseView: View
    private lateinit var deleteView: View

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
        deleteView.setOnClickListener { openDeleteDialog() }

        scrollView = view.findViewById(R.id.scroll)
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            headerView.divider = scrollY > 0
        }

        collectFlow(keyViewModel.keyEntity, ::applyEntity)
    }

    private fun applyEntity(key: KeyEntity) {
        setName(key.name)
        setHex(key.hex)
        setExportByUri(key.publicKey, key.name)
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
        qrView.content = uri.toString()
        exportLocalView.setOnClickListener {
            TonkeeperApp.openOrInstall(requireContext(), uri)
        }
    }

    private fun openRecoveryPhrase() {
        keyViewModel.getRecoveryPhrase(requireContext()).catch {
            requireContext().authorizationRequiredError()
        }.onEach(::openRecoveryPhrase).launchIn(lifecycleScope)
    }

    private fun openRecoveryPhrase(mnemonic: Array<String>) {
        navigation?.add(PhraseFragment.newInstance(mnemonic))
    }

    private fun openDeleteDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setColoredButtons()
        builder.setTitle(R.string.delete_key_question)
        builder.setMessage(R.string.delete_key_question_subtitle)
        builder.setNegativeButton(R.string.delete_key) { _ -> delete() }
        builder.setPositiveButton(R.string.cancel)
        builder.show()
    }

    private fun delete() {
        keyViewModel.delete(requireContext()).catch {
            requireContext().authorizationRequiredError()
        }.onEach {
            finish()
        }.launchIn(lifecycleScope)
    }

}