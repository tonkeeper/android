package com.tonapps.singer.screen.sign

import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.extensions.copyToClipboard
import com.tonapps.singer.screen.qr.QRFragment
import com.tonapps.singer.screen.root.RootViewModel
import com.tonapps.singer.screen.sign.list.SignAdapter
import com.tonapps.singer.screen.sign.list.SignItem
import com.tonapps.singer.short4
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.setColor
import uikit.extensions.withAnimation
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.LoaderView
import uikit.widget.SimpleRecyclerView
import uikit.widget.SlideActionView

class SignFragment: BaseFragment(R.layout.fragment_sign), BaseFragment.Modal {

    companion object {

        private const val ID_KEY = "id"
        private const val BODY_KEY = "body"
        private const val QR_KEY = "qr"

        fun newInstance(id: Long, body: String, qr: Boolean): SignFragment {
            val fragment = SignFragment()
            fragment.arguments = Bundle().apply {
                putLong(ID_KEY, id)
                putBoolean(QR_KEY, qr)
                putString(BODY_KEY, body)
            }
            return fragment
        }
    }

    private val id: Long by lazy { requireArguments().getLong(ID_KEY) }
    private val body: String by lazy { requireArguments().getString(BODY_KEY)!! }
    private val qr: Boolean by lazy { requireArguments().getBoolean(QR_KEY) }

    private val signViewModel: SignViewModel by viewModel { parametersOf(id, body) }
    private val rootViewModel: RootViewModel by activityViewModel()

    private val adapter = SignAdapter()

    private lateinit var closeView: View
    private lateinit var subtitleView: AppCompatTextView
    private lateinit var listView: SimpleRecyclerView
    private lateinit var auditView: FrameLayout
    private lateinit var showAuditView: View
    private lateinit var rawView: View
    private lateinit var bocRawView: AppCompatTextView
    private lateinit var emulateButton: View
    private lateinit var copyButton: View
    private lateinit var slideView: SlideActionView
    private lateinit var processLoader: LoaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        behavior.isHideable = false

        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener { finish() }

        subtitleView = view.findViewById(R.id.subtitle)

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        auditView = view.findViewById(R.id.audit)

        showAuditView = view.findViewById(R.id.show_audit)
        showAuditView.setOnClickListener { showDetails() }

        rawView = view.findViewById(R.id.raw)

        bocRawView = view.findViewById(R.id.boc_raw)
        bocRawView.text = body
        bocRawView.setOnClickListener { copyBody() }

        emulateButton = view.findViewById(R.id.emulate)
        emulateButton.setOnClickListener { emulateBody() }

        copyButton = view.findViewById(R.id.copy)
        copyButton.setOnClickListener { copyBody() }

        slideView = view.findViewById(R.id.slide)
        slideView.doOnDone = { sign() }

        processLoader = view.findViewById(R.id.process_loader)

        signViewModel.keyEntity.onEach(::setKeyEntity).launchIn(lifecycleScope)
        signViewModel.actionsFlow.onEach(::submitList).launchIn(lifecycleScope)
        signViewModel.signedBody.onEach(::signed).launchIn(lifecycleScope)
    }

    private fun copyBody() {
        requireContext().copyToClipboard(body)
    }

    private fun emulateBody() {
        val uri = Uri.Builder().scheme("https")
            .authority("tonviewer.com")
            .appendPath(body).build()

        navigation?.openURL(uri.toString(), true)
    }

    private fun signed(boc: String) {
        if (qr) {
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

    private fun submitList(items: List<SignItem>) {
        adapter.submitList(items)
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