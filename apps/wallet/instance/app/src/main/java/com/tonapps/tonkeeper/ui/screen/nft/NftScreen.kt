package com.tonapps.tonkeeper.ui.screen.nft

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.NestedScrollView
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.short4
import com.tonapps.tonkeeper.extensions.copyWithToast
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppArgs
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.send.SendScreen
import com.tonapps.tonkeeper.ui.screen.token.unverified.TokenUnverifiedScreen
import com.tonapps.tonkeeper.ui.screen.web.WebScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.buttonGreenBackgroundColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.dialog.alert.AlertDialog
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.inflate
import uikit.extensions.setRightDrawable
import uikit.extensions.topScrolled
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ColumnLayout
import uikit.widget.FrescoView
import uikit.widget.HeaderView

class NftScreen: BaseFragment(R.layout.fragment_nft), BaseFragment.BottomSheet {

    private val nftEntity: NftEntity by lazy {
        requireArguments().getParcelableCompat(ARG_ENTITY)!!
    }

    private val nftViewModel: NftViewModel by viewModel{ parametersOf(nftEntity) }

    private val verificationIcon: Drawable by lazy {
        getDrawable(UIKitIcon.ic_verification_16, requireContext().accentBlueColor)
    }

    private lateinit var headerView: HeaderView
    private lateinit var spamView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.title = nftEntity.name
        headerView.doOnCloseClick = { finish() }

        val contentView = view.findViewById<NestedScrollView>(R.id.content)
        contentView.applyNavBottomPadding()
        collectFlow(contentView.topScrolled, headerView::setDivider)

        spamView = view.findViewById(R.id.spam)

        view.findViewById<Button>(R.id.report_spam).setOnClickListener { reportSpam(true) }
        view.findViewById<Button>(R.id.not_spam).setOnClickListener { reportSpam(false) }

        val imageView = view.findViewById<FrescoView>(R.id.image)
        imageView.setImageURI(nftEntity.bigUri)

        val nameView = view.findViewById<AppCompatTextView>(R.id.name)
        nameView.text = nftEntity.name

        val collectionNameView = view.findViewById<AppCompatTextView>(R.id.collection_name)
        collectionNameView.text = nftEntity.collectionName.ifEmpty {
            getString(Localization.unnamed_collection)
        }
        if (nftEntity.verified) {
            collectionNameView.setCompoundDrawablesWithIntrinsicBounds(null, null, verificationIcon, null)
        } else {
            collectionNameView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }

        val descriptionView = view.findViewById<AppCompatTextView>(R.id.nft_description)
        if (nftEntity.description.isBlank()) {
            descriptionView.visibility = View.GONE
        } else {
            descriptionView.visibility = View.VISIBLE
            descriptionView.text = nftEntity.description
        }

        val transferButton = view.findViewById<Button>(R.id.transfer)
        transferButton.setOnClickListener {
            navigation?.add(SendScreen.newInstance(nftAddress = nftEntity.address))
        }

        val domainLinkButton = view.findViewById<Button>(R.id.domain_link)
        val domainRenewButton = view.findViewById<Button>(R.id.domain_renew)

        if (nftEntity.isDomain && nftEntity.metadata.buttons.isEmpty()) {
            val url = Uri.parse("https://dns.tonkeeper.com/manage?v=${nftEntity.userFriendlyAddress}")
            val dAppArgs = DAppArgs(
                host = url.host,
                url = url.toString()
            )
            domainLinkButton.setOnClickListener {
                navigation?.add(DAppScreen.newInstance(dAppArgs))
                finish()
            }
            domainRenewButton.setOnClickListener {
                navigation?.add(DAppScreen.newInstance(dAppArgs))
                finish()
            }
        } else {
            domainLinkButton.visibility = View.GONE
            domainRenewButton.visibility = View.GONE
        }

        val buttonsContainer = view.findViewById<ColumnLayout>(R.id.buttons_container)
        if (nftEntity.metadata.buttons.isEmpty()) {
            buttonsContainer.visibility = View.GONE
        } else {
            buttonsContainer.removeAllViews()
            buttonsContainer.visibility = View.VISIBLE
            for ((index, button) in nftEntity.metadata.buttons.take(5).withIndex()) {
                val buttonView = newNftButton(buttonsContainer, index == 0)
                buttonView.text = button.label
                buttonView.setOnClickListener { openButtonDApp(button.uri) }
            }
        }

        val transferDisabled = view.findViewById<View>(R.id.transfer_disabled)

        if (nftEntity.inSale) {
            transferButton.isEnabled = false
            transferDisabled.visibility = View.VISIBLE
        }

        val aboutView = view.findViewById<View>(R.id.about)
        val collectionDescription = view.findViewById<AppCompatTextView>(R.id.collection_description)
        if (nftEntity.collectionDescription.isBlank()) {
            aboutView.visibility = View.GONE
        } else {
            aboutView.visibility = View.VISIBLE
            collectionDescription.text = nftEntity.collectionDescription
        }

        nftEntity.owner?.address?.let {
            setOwner(view, it)
        }
        setAddress(view, nftEntity.userFriendlyAddress)

        collectFlow(nftViewModel.trustFlow) {
            if (it) {
                showTrustState()
            } else {
                showUnverifiedState()
            }
        }
    }

    private fun openButtonDApp(url: String) {
        if (nftEntity.suspicious) {
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle(Localization.nft_warning_buttton_title)
            builder.setMessage(getString(Localization.nft_warning_buttton_subtitle, url))
            builder.setNegativeButton(Localization.open_anyway, requireContext().accentRedColor) { mustOpenButtonDApp(url) }
            builder.setPositiveButton(Localization.cancel, requireContext().accentBlueColor)
            builder.show()
        } else {
            mustOpenButtonDApp(url)
        }
    }

    private fun mustOpenButtonDApp(url: String) {
        navigation?.add(DAppScreen.newInstance(url = url))
        finish()
    }

    private fun newNftButton(parent: ColumnLayout, first: Boolean): Button {
        val layout = if (first) R.layout.view_nft_button_green else R.layout.view_nft_button
        val view = parent.context.inflate(layout)
        parent.addView(view, ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)
            leftMargin = topMargin
            rightMargin = topMargin
        })
        return view.findViewById(R.id.nft_button)
    }

    private fun showUnverifiedState() {
        spamView.visibility = View.VISIBLE
        val color = requireContext().accentOrangeColor
        val icon = requireContext().drawable(UIKitIcon.ic_information_circle_16, color)

        headerView.setSubtitle(Localization.nft_unverified)
        with(headerView.subtitleView) {
            visibility = View.VISIBLE
            compoundDrawablePadding = 8.dp
            setTextColor(color)
            setRightDrawable(icon)
        }
    }

    private fun showTrustState() {
        spamView.visibility = View.GONE
        headerView.setSubtitle(null)
    }

    private fun reportSpam(spam: Boolean) {
        collectFlow(nftViewModel.reportSpam(spam)) {
            finish()
        }
    }

    private fun setOwner(view: View, address: String) {
        val ownerContainerView = view.findViewById<View>(R.id.owner_container)
        ownerContainerView.background = ListCell.Position.FIRST.drawable(requireContext())
        ownerContainerView.setOnClickListener {
            context?.copyWithToast(address)
        }

        val ownerAddressView = view.findViewById<AppCompatTextView>(R.id.owner)
        ownerAddressView.text = address.short4
    }

    private fun setAddress(view: View, address: String) {
        val explorerView = view.findViewById<AppCompatTextView>(R.id.open_explorer)
        explorerView.setOnClickListener {
            val nftExplorer = context?.remoteConfig?.nftExplorer ?: return@setOnClickListener
            val explorerUrl = nftExplorer.format(address)
            navigation?.openURL(explorerUrl, true)
        }

        val addressContainerView = view.findViewById<View>(R.id.address_container)
        addressContainerView.background = ListCell.Position.LAST.drawable(requireContext())
        addressContainerView.setOnClickListener {
            context?.copyWithToast(address)
        }

        val addressView = view.findViewById<AppCompatTextView>(R.id.address)
        addressView.text = address.short4
    }

    companion object {

        private const val ARG_ENTITY = "entity"

        fun newInstance(entity: NftEntity): NftScreen {
            val fragment = NftScreen()
            fragment.arguments = Bundle().apply {
                putParcelable(ARG_ENTITY, entity)
            }
            return fragment
        }
    }
}