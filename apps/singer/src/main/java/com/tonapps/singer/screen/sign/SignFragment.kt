package com.tonapps.singer.screen.sign

import android.os.Bundle
import android.text.SpannableString
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.singer.R
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.extensions.copyToClipboard
import com.tonapps.singer.screen.qr.QRFragment
import com.tonapps.singer.screen.root.RootViewModel
import com.tonapps.singer.screen.root.RootMode
import com.tonapps.singer.screen.sign.list.SignAdapter
import com.tonapps.singer.screen.sign.list.SignItem
import com.tonapps.singer.short4
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.setColor
import uikit.list.LinearLayoutManager
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.LoaderView
import uikit.widget.SlideActionView

class SignFragment: BaseFragment(R.layout.fragment_sign), BaseFragment.Modal {

    companion object {

        private const val ID_KEY = "id"
        private const val BOC_KEY = "boc"
        private const val QR_KEY = "qr"

        fun newInstance(id: Long, boc: String, qr: Boolean): SignFragment {
            val fragment = SignFragment()
            fragment.arguments = Bundle().apply {
                putLong(ID_KEY, id)
                putBoolean(QR_KEY, qr)
                putString(BOC_KEY, boc)
            }
            return fragment
        }
    }

    private val id: Long by lazy { requireArguments().getLong(ID_KEY) }
    private val boc: String by lazy { requireArguments().getString(BOC_KEY)!! }
    private val qr: Boolean by lazy { requireArguments().getBoolean(QR_KEY) }

    private val signViewModel: SignViewModel by viewModel { parametersOf(id, boc) }
    private val rootViewModel: RootViewModel by lazy {
        requireActivity().getViewModel()
    }

    private val adapter = SignAdapter()

    private lateinit var closeView: View
    private lateinit var subtitleView: AppCompatTextView
    private lateinit var actionsView: View
    private lateinit var listView: RecyclerView
    private lateinit var loaderView: LoaderView
    private lateinit var showAuditView: View
    private lateinit var rawView: View
    private lateinit var bocRawView: AppCompatTextView
    private lateinit var copyButton: View
    private lateinit var slideView: SlideActionView
    private lateinit var processLoader: LoaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        behavior.isHideable = false

        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener { finish() }

        subtitleView = view.findViewById(R.id.subtitle)
        actionsView = view.findViewById(R.id.actions)

        listView = view.findViewById(R.id.list)
        listView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        listView.adapter = adapter

        loaderView = view.findViewById(R.id.loader)

        showAuditView = view.findViewById(R.id.show_audit)
        showAuditView.setOnClickListener { showDetails() }

        rawView = view.findViewById(R.id.raw)

        bocRawView = view.findViewById(R.id.boc_raw)
        bocRawView.text = boc
        bocRawView.setOnClickListener { copyBoc()  }

        copyButton = view.findViewById(R.id.copy)
        copyButton.setOnClickListener { copyBoc()  }

        slideView = view.findViewById(R.id.slide)
        slideView.doOnDone = { sign() }

        processLoader = view.findViewById(R.id.process_loader)

        signViewModel.keyEntity.onEach(::setKeyEntity).launchIn(lifecycleScope)
        signViewModel.actionsFlow.onEach(::setActions).launchIn(lifecycleScope)
        signViewModel.signedBody.onEach(::signed).launchIn(lifecycleScope)
    }

    private fun copyBoc() {
        requireContext().copyToClipboard(boc)
    }

    private fun signed(boc: String) {
        if (qr) {
            rootViewModel.setMode(RootMode.Default)
            navigation?.add(QRFragment.newInstance(id, boc))
            finish()
        } else {
            rootViewModel.responseSignedBoc(boc)
        }
    }

    private fun sign() {
        signViewModel.sign()

        slideView.visibility = View.GONE
        processLoader.visibility = View.VISIBLE
    }

    private fun showDetails() {
        showAuditView.visibility = View.GONE
        rawView.visibility = View.VISIBLE
    }

    private fun setActions(list: List<SignItem>) {
        loaderView.visibility = View.GONE
        listView.visibility = View.VISIBLE
        adapter.submitList(list)
    }

    private fun setKeyEntity(entity: KeyEntity) {
        setSubtitle(entity.name, entity.hex.short4)
    }

    private fun setSubtitle(label: String, hex: String) {
        val span = SpannableString("$label $hex")
        span.setColor(getColor(uikit.R.color.textTertiary),0, label.length)
        span.setColor(getColor(uikit.R.color.textSecondary), label.length, span.length)
        subtitleView.text = span
    }
}