package com.tonapps.tonkeeper.core.history.list.holder

import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.imagepipeline.postprocessors.BlurPostProcessor
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.tonapps.extensions.logError
import com.tonapps.extensions.short12
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.core.history.ActionType
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.iconRes
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.core.history.nameRes
import com.tonapps.tonkeeper.koin.historyHelper
import com.tonapps.tonkeeper.ui.screen.transaction.TransactionScreen
import com.tonapps.tonkeeper.ui.screen.nft.NftScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.iconSecondaryColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.uikit.color.textTertiaryColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uikit.extensions.clearDrawables
import uikit.extensions.drawable
import uikit.extensions.reject
import uikit.extensions.setLeftDrawable
import uikit.navigation.Navigation
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.FrescoView
import uikit.widget.LoaderView

class HistoryActionHolder(
    parent: ViewGroup,
    private val disableOpenAction: Boolean,
) : HistoryHolder<HistoryItem.Event>(parent, R.layout.view_history_action) {

    private val amountColorReceived = context.accentGreenColor
    private val amountColorDefault = context.textPrimaryColor
    private val amountColorTertiary = context.textTertiaryColor

    private val loaderView = findViewById<LoaderView>(R.id.loader)
    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    private val commentView = findViewById<AppCompatTextView>(R.id.comment)
    private val amountView = findViewById<AppCompatTextView>(R.id.amount)
    private val amount2View = findViewById<AppCompatTextView>(R.id.amount2)
    private val dateView = findViewById<AppCompatTextView>(R.id.date)
    private val warningView = findViewById<AppCompatTextView>(R.id.warning)
    private val unverifiedTokenView = findViewById<AppCompatTextView>(R.id.unverified_token)

    private val nftView = findViewById<View>(R.id.nft)
    private val nftIconView = findViewById<FrescoView>(R.id.nft_icon)
    private val nftNameView = findViewById<AppCompatTextView>(R.id.nft_name)
    private val nftCollectionView = findViewById<AppCompatTextView>(R.id.nft_collection)
    private val lockDrawable: Drawable by lazy {
        val drawable = context.drawable(UIKitIcon.ic_lock_16)
        drawable.setTint(context.accentGreenColor)
        drawable
    }

    override fun onBind(item: HistoryItem.Event) {
        commentView.clearDrawables()

        unverifiedTokenView.visibility = if (item.unverifiedToken) {
            View.VISIBLE
        } else {
            View.GONE
        }

        if (!disableOpenAction) {
            itemView.setOnClickListener { context.navigation?.add(TransactionScreen.newInstance(item)) }
        }

        itemView.background = item.position.drawable(context)
        if (item.isScam) {
            titleView.setText(Localization.spam)
            subtitleView.setTextColor(amountColorTertiary)
        } else {
            titleView.setText(item.action.nameRes)
            subtitleView.setTextColor(context.textSecondaryColor)
        }

        subtitleView.text = item.subtitle

        dateView.text = item.date

        if (item.failed) {
            iconView.setImageResource(UIKitIcon.ic_exclamationmark_circle_28)
            iconView.imageTintList = context.iconSecondaryColor.stateList
            warningView.visibility = View.VISIBLE
        } else if (item.iconURL.isNullOrEmpty()) {
            iconView.setImageResource(item.action.iconRes)
            iconView.imageTintList = context.iconSecondaryColor.stateList
            warningView.visibility = View.GONE
        } else {
            loadIcon(Uri.parse(item.iconURL))
            warningView.visibility = View.GONE
        }

        bindPending(item.pending)
        if (item.comment == null || item.isScam) {
            commentView.visibility = View.GONE
        } else {
            bindComment(item.comment, item.txId, item.sender?.address ?: "")
        }

        bindNft(item)
        bindAmount(item)
    }

    private fun loadIcon(uri: Uri) {
        iconView.imageTintList = null
        iconView.setImageURI(uri, this)
    }

    private fun bindPending(pending: Boolean) {
        loaderView.visibility = if (pending) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun bindAmount(item: HistoryItem.Event) {
        if (item.isScam || item.action == ActionType.WithdrawStakeRequest) {
            amountView.setTextColor(amountColorTertiary)
        } else {
            amountView.setTextColor(getAmountColor(item.value))
        }
        if (item.hiddenBalance) {
            amountView.text = HIDDEN_BALANCE
        } else {
            amountView.text = item.value.withCustomSymbol(context)
        }

        if (item.value2.isEmpty()) {
            amount2View.visibility = View.GONE
        } else {
            amount2View.visibility = View.VISIBLE
            if (item.hiddenBalance) {
                amount2View.text = HIDDEN_BALANCE
            } else if (item.isScam) {
                amount2View.text = item.value2.toString()
            } else {
                amount2View.text = item.value2.withCustomSymbol(context)
            }
        }
    }

    private fun bindComment(
        comment: HistoryItem.Event.Comment,
        txId: String,
        senderAddress: String,
    ) {
        commentView.visibility = View.VISIBLE
        if (comment.isEncrypted) {
            commentView.text = context.getString(Localization.encrypted_comment)
            commentView.setLeftDrawable(lockDrawable)
            commentView.setOnClickListener { requestDecryptComment(comment, txId, senderAddress) }
        } else {
            commentView.text = comment.body
            commentView.setLeftDrawable(null)
            commentView.setOnClickListener(null)
        }
    }

    private fun requestDecryptComment(
        comment: HistoryItem.Event.Comment,
        txId: String,
        senderAddress: String
    ) {
        val scope = lifecycleScope ?: return
        val flow = context.historyHelper?.requestDecryptComment(context, comment, txId, senderAddress) ?: return
        flow.catch {
            context.logError(it)
            commentView.reject()
        }.onEach {
            bindComment(it, txId, senderAddress)
        }.launchIn(scope)
    }

    private fun bindNft(item: HistoryItem.Event) {
        if (!item.hasNft || item.isScam) {
            nftView.visibility = View.GONE
            return
        }

        val nft = item.nft!!
        nftView.visibility = View.VISIBLE
        nftView.setOnClickListener {
            Navigation.from(context)?.add(NftScreen.newInstance(item.wallet, nft))
        }
        loadNftImage(nft.mediumUri, item.hiddenBalance)
        if (item.hiddenBalance) {
            nftNameView.text = HIDDEN_BALANCE
            nftCollectionView.text = HIDDEN_BALANCE
        } else {
            nftNameView.text = nft.name
            nftCollectionView.text = nft.collectionName.ifEmpty {
                getString(Localization.unnamed_collection)
            }
        }
    }

    private fun loadNftImage(uri: Uri, blur: Boolean) {
        if (blur) {
            val request = ImageRequestBuilder.newBuilderWithSource(uri)
                .setPostprocessor(BlurPostProcessor(25, context, 3))
                .build()
            nftIconView.setImageRequest(request)
        } else {
            nftIconView.setImageURI(uri, null)
        }
    }

    @ColorInt
    private fun getAmountColor(amount: CharSequence): Int {
        if (amount == HistoryHelper.MINUS_SYMBOL) {
            return amountColorTertiary
        }
        return if (amount.startsWith(HistoryHelper.PLUS_SYMBOL)) {
            amountColorReceived
        } else {
            amountColorDefault
        }
    }
}