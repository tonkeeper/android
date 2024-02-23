package com.tonapps.tonkeeper.fragment.wallet.main.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowInsetsCompat
import com.tonapps.emoji.ui.EmojiView
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletLabel
import uikit.drawable.HeaderDrawable
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal
import uikit.extensions.setPaddingTop
import uikit.widget.RowLayout

class WalletHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle) {

    private val barHeight = context.getDimensionPixelSize(uikit.R.dimen.barHeight)
    private var topOffset: Int = 0
        set(value) {
            if (field != value) {
                field = value
                setPaddingTop(value)
                requestLayout()
            }
        }

    private val settingsView: View
    private val walletView: View
    private val emojiView: EmojiView
    private val nameView: AppCompatTextView
    private val arrowView: AppCompatImageView
    private val drawable = HeaderDrawable(context)

    var onSettingsClick: (() -> Unit)? = null
        set(value) {
            field = value
            settingsView.setOnClickListener { value?.invoke() }
        }

    var onWalletClick: (() -> Unit)? = null
        set(value) {
            field = value
            walletView.setOnClickListener { value?.invoke() }
        }

    init {
        setPaddingHorizontal(context.getDimensionPixelSize(uikit.R.dimen.offsetMedium))
        super.setBackground(drawable)
        inflate(context, R.layout.view_wallet_header, this)
        settingsView = findViewById(R.id.settings)
        walletView = findViewById(R.id.wallet)
        emojiView = findViewById(R.id.wallet_emoji)
        nameView = findViewById(R.id.wallet_name)
        arrowView = findViewById(R.id.wallet_arrow)
    }

    fun setDivider(value: Boolean) {
        drawable.setDivider(value)
    }

    fun setWallet(walletLabel: WalletLabel) {
        if (walletLabel.isEmpty) {
            walletView.visibility = View.GONE
            return
        }

        walletView.visibility = View.VISIBLE
        nameView.text = walletLabel.name
        emojiView.setEmoji(walletLabel.emoji)
        walletView.backgroundTintList = ColorStateList.valueOf(walletLabel.color)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val statusInsets = compatInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        topOffset = statusInsets.top
        return super.onApplyWindowInsets(insets)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(barHeight + topOffset, MeasureSpec.EXACTLY))
    }
}