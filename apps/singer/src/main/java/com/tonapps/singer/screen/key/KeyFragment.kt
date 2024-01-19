package com.tonapps.singer.screen.key

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.core.TonkeeperApp
import com.tonapps.singer.extensions.copyToClipboard
import com.tonapps.singer.screen.name.NameFragment
import com.tonapps.singer.screen.password.lock.LockFragment
import com.tonapps.singer.screen.phrase.PhraseFragment
import com.tonapps.singer.short4
import com.tonapps.singer.short8
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
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

        private const val REQUEST_PHRASE = "request_phrase"

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
    private lateinit var exportQrView: View
    private lateinit var qrView: SquareImageView
    private lateinit var exportLocalView: View
    private lateinit var nameView: ActionCellView
    private lateinit var hexAddressView: ActionCellView
    private lateinit var phraseView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigation?.setFragmentResultListener(REQUEST_PHRASE) { _, _ ->
            navigation?.add(PhraseFragment.newInstance(id))
        }
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

        keyViewModel.keyEntity.filterNotNull().onEach {
            setName(it.name)
            setHex(it.hex)
            setExportUri(it.exportUri)
        }.launchIn(lifecycleScope)
    }

    override fun onEndShowingAnimation() {
        super.onEndShowingAnimation()
        qrView.doOnLayout {
            requestBitmap(it.width, it.height)
        }
    }

    private fun openRecoveryPhrase() {
        navigation?.add(LockFragment.newInstance(REQUEST_PHRASE))
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

    private fun setExportUri(uri: Uri) {
        exportLocalView.setOnClickListener {
            TonkeeperApp.openOrInstall(requireContext(), uri)
        }
    }

}