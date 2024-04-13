package com.tonapps.tonkeeper.ui.component.label

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.emoji.Emoji
import com.tonapps.emoji.ui.EmojiView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.list.LinearLayoutManager
import com.tonapps.wallet.data.account.WalletColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.HapticHelper
import uikit.extensions.bottomBarsOffset
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.dp
import uikit.extensions.getRootWindowInsetsCompat
import uikit.extensions.runAnimation
import uikit.extensions.useAttributes
import uikit.extensions.withAlpha
import uikit.widget.ColumnLayout
import uikit.widget.InputView

class LabelEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ColumnLayout(context, attrs, defStyle) {

    var doOnDone: ((name: String, emoji: String, color: Int) -> Unit)? = null
    var doOnChange: ((name: String, emoji: String, color: Int) -> Unit)? = null

    private val colorAdapter = ColorAdapter {
        color = it
    }

    private val emojiAdapter = EmojiAdapter {
        emoji = it.value
    }

    private val nameInput: InputView
    private val colorView: View
    private val emojiView: EmojiView
    private val colorPicker: RecyclerView
    private val emojiPicker: RecyclerView
    private val overlayView: View
    private val actionView: View
    private val button: Button

    var name: String
        get() = nameInput.text
        set(value) {
            nameInput.text = value
            notifyChange()
        }

    var emoji: CharSequence
        get() = emojiView.getEmoji()
        set(value) {
            if (emojiView.setEmoji(value)) {
                HapticHelper.selection(context)
                emojiView.runAnimation(uikit.R.anim.scale_switch)
                notifyChange()
            }
        }

    var color: Int = Color.TRANSPARENT
        set(value) {
            if (value != field) {
                colorAdapter.activeColor = value
                colorView.backgroundTintList = value.stateList
                scrollToColor(value)
                field = value
                notifyChange()
            }
        }

    init {
        inflate(context, R.layout.view_editor_label, this)
        nameInput = findViewById(R.id.label_name_input)
        nameInput.setOnDoneActionListener { done() }

        colorView = findViewById(R.id.label_color)
        colorView.setOnClickListener { nameInput.hideKeyboard() }

        emojiView = findViewById(R.id.label_emoji)

        colorPicker = findViewById(R.id.label_color_picker)
        emojiPicker = findViewById(R.id.label_emoji_picker)

        overlayView = findViewById(R.id.label_overlay)
        overlayView.setBackgroundColor(context.backgroundPageColor.withAlpha(.68f))
        overlayView.setOnClickListener { nameInput.hideKeyboard() }

        actionView = findViewById(R.id.label_action)
        actionView.background.alpha = 0

        button = findViewById(R.id.label_button)
        button.setOnClickListener { done() }

        applyColorPicker()
        applyEmojiPicker()

        nameInput.doOnTextChange = {
            button.isEnabled = it.isNotEmpty()
        }

        context.useAttributes(attrs, R.styleable.LabelEditorView) {
            button.text = it.getString(R.styleable.LabelEditorView_android_button)
        }
    }

    private fun scrollToColor(color: Int) {
        val index = WalletColor.all.indexOf(color)
        if (index >= 0) {
            colorPicker.scrollToPosition(index)
        }
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val navigationInsets = insetsCompat.getInsets(WindowInsetsCompat.Type.navigationBars())
        applyEmojiMargin(navigationInsets.bottom)
        return super.dispatchApplyWindowInsets(insets)
    }

    fun setBottomOffset(offset: Int, progress: Float) {
        setExtrasAlpha(progress)
        actionView.translationY = -offset.toFloat()
    }

    private fun stopScroll() {
        colorPicker.stopScroll()
        emojiPicker.stopScroll()
    }

    fun removeFocus() {
        stopScroll()
        nameInput.hideKeyboard()
    }

    fun focus() {
        nameInput.focus()
    }

    private fun setExtrasAlpha(alpha: Float) {
        actionView.background.alpha = (alpha * 255).toInt()
        overlayView.alpha = alpha
        if (overlayView.alpha == 0f) {
            overlayView.visibility = View.GONE
        } else if (overlayView.visibility == View.GONE) {
            overlayView.visibility = View.VISIBLE
            stopScroll()
        }
    }

    private fun applyColorPicker() {
        colorPicker.adapter = colorAdapter
        colorPicker.layoutManager = object : LinearLayoutManager(context, RecyclerView.HORIZONTAL, false) {

            override fun onLayoutCompleted(state: RecyclerView.State) {
                super.onLayoutCompleted(state)
                val firstVisible = findFirstVisibleItemPosition()
                val lastVisible = findLastVisibleItemPosition()
                val count = (lastVisible - firstVisible) + 1
                if (emojiPicker.layoutManager == null) {
                    emojiPicker.layoutManager = GridLayoutManager(context, count)
                }
            }
        }
    }

    private fun applyEmojiPicker() {
        emojiPicker.adapter = emojiAdapter
    }

    private fun applyEmojiMargin(bottom: Int) {
        val params = emojiPicker.layoutParams as MarginLayoutParams
        if (params.bottomMargin != bottom) {
            params.bottomMargin = bottom
            emojiPicker.layoutParams = params
        }
    }

    suspend fun loadEmoji() = withContext(Dispatchers.IO) {
        val emojis = Emoji.get(context)
        withContext(Dispatchers.Main) {
            emojiAdapter.submitList(emojis)
        }
    }

    private fun done() {
        if (name.isBlank()) {
            return
        }
        removeFocus()
        doOnDone?.invoke(name, emoji.toString(), color)
    }

    private fun notifyChange() {
        doOnChange?.invoke(name, emoji.toString(), color)
    }

}