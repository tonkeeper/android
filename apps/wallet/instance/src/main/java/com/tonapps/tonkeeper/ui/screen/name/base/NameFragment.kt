package com.tonapps.tonkeeper.ui.screen.name.base

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.emoji.EmojiView
import com.tonapps.tonkeeper.data.AccountColor
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.ui.screen.name.adapter.ColorAdapter
import com.tonapps.tonkeeper.ui.screen.name.adapter.EmojiAdapter
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.list.LinearLayoutManager
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.HapticHelper
import uikit.base.BaseFragment
import uikit.extensions.bottomBarsOffset
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.getRootWindowInsetsCompat
import uikit.extensions.reject
import uikit.extensions.runAnimation
import uikit.extensions.withAlpha
import uikit.widget.InputView

abstract class NameFragment(mode: NameMode): BaseFragment(R.layout.fragment_name) {

    companion object {
        private const val MODE_KEY = "mode"
    }

    private val mode: NameMode by lazy { requireArguments().getInt(MODE_KEY) }
    private val nameViewModel: NameViewModel by viewModel { parametersOf(mode) }

    private val colorAdapter: ColorAdapter by lazy { ColorAdapter(nameViewModel::setColor) }
    private val emojiAdapter: EmojiAdapter by lazy { EmojiAdapter(nameViewModel::setEmoji) }

    private lateinit var walletName: InputView
    private lateinit var walletColor: View
    private lateinit var walletEmoji: EmojiView
    private lateinit var colorView: RecyclerView
    private lateinit var emojiView: RecyclerView
    private lateinit var overView: View
    private lateinit var actionView: View
    private lateinit var nextButton: Button

    init {
        arguments = Bundle().apply {
            putInt(MODE_KEY, mode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nameViewModel.loadEmojiPack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        walletName = view.findViewById(R.id.wallet_name)
        walletName.onEditorAction(EditorInfo.IME_ACTION_DONE)
        walletName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                sendData()
                true
            } else {
                false
            }
        }

        walletColor = view.findViewById(R.id.wallet_color)

        walletEmoji = view.findViewById(R.id.wallet_emoji)
        walletEmoji.setOnClickListener { walletName.hideKeyboard() }

        colorView = view.findViewById(R.id.color)
        colorView.adapter = colorAdapter

        emojiView = view.findViewById(R.id.emoji)
        emojiView.adapter = emojiAdapter

        overView = view.findViewById(R.id.over)
        overView.setBackgroundColor(getColor(UIKitColor.backgroundPage).withAlpha(.68f))
        overView.setOnClickListener { walletName.hideKeyboard() }

        actionView = view.findViewById(R.id.action)
        actionView.doOnLayout { applyEmojiMargin() }

        nextButton = view.findViewById(R.id.next)
        nextButton.setOnClickListener { sendData() }
        nextButton.setText(if (mode == NameModeCreate) Localization.continue_action else Localization.save)

        colorView.layoutManager = object : LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false) {

            override fun onLayoutCompleted(state: RecyclerView.State) {
                super.onLayoutCompleted(state)
                val firstVisible = findFirstCompletelyVisibleItemPosition()
                val lastVisible = findLastCompletelyVisibleItemPosition()
                val count = lastVisible - firstVisible + 1
                applyEmojiLayoutManager(count)
            }
        }

        view.doKeyboardAnimation { offset, progress ->
            setExtrasAlpha(progress)
            actionView.translationY = -offset.toFloat()
        }

        walletName.doOnTextChange = {
            nextButton.isEnabled = it.isNotEmpty()
        }

        collectFlow(nameViewModel.emojiFlow, emojiAdapter::submitList)
        collectFlow(nameViewModel.uiStateFlow, ::setUiState)
    }

    private fun setExtrasAlpha(alpha: Float) {
        overView.alpha = alpha
        if (overView.alpha == 0f) {
            overView.visibility = View.GONE
        } else if (overView.visibility == View.GONE) {
            overView.visibility = View.VISIBLE
            stopScroll()
        }
    }

    private fun setUiState(state: UiState) {
        setName(state.name)
        setColor(state.color)
        setEmoji(state.emoji)
    }

    fun setName(name: String) {
        if (walletName.text.isEmpty()) {
            walletName.text = name
        }
    }

    fun setColor(newColor: Int) {
        scrollToColor(newColor)
        walletColor.backgroundTintList = ColorStateList.valueOf(newColor)
        colorAdapter.activeColor = newColor
    }

    private fun scrollToColor(color: Int) {
        val index = AccountColor.all.indexOf(color)
        if (index >= 0) {
            colorView.scrollToPosition(index)
        }
    }

    fun setEmoji(newEmoji: CharSequence) {
        val oldEmoji = walletEmoji.getEmoji()
        if (oldEmoji.isEmpty()) {
            walletEmoji.setEmoji(newEmoji)
        } else if (oldEmoji != newEmoji) {
            HapticHelper.selection(requireContext())
            walletEmoji.runAnimation(uikit.R.anim.scale_switch)
            walletEmoji.setEmoji(newEmoji)
        }
    }

    fun stopScroll() {
        colorView.stopScroll()
        emojiView.stopScroll()
    }

    private fun applyEmojiMargin() {
        val actionHeight = actionView.measuredHeight
        val bottomOffset = actionView.getRootWindowInsetsCompat()?.bottomBarsOffset ?: 0

        emojiView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = actionHeight + bottomOffset
        }
    }

    private fun applyEmojiLayoutManager(count: Int) {
        if (emojiView.layoutManager == null) {
            emojiView.layoutManager = GridLayoutManager(requireContext(), count)
        }
    }

    fun focus(focus: Boolean) {
        if (focus) {
            walletName.focus()
        } else {
            walletName.hideKeyboard()
        }
    }

    private fun sendData() {
        val name = walletName.text
        if (name.isEmpty()) {
            walletName.reject()
            return
        }

        val emoji = walletEmoji.getEmoji()
        val color = colorAdapter.activeColor
        onData(name, emoji, color)
    }

    abstract fun onData(name: String, emoji: CharSequence, color: Int)

}