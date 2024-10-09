package com.tonapps.tonkeeper.ui.screen.w5.stories

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerMode
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.getViews
import uikit.extensions.round
import uikit.widget.FrescoView
import uikit.widget.RowLayout

class W5StoriesScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_w5_stories, ScreenContext.None) {

    override val viewModel: W5StoriesViewModel by viewModel()

    private val showAddButton: Boolean by lazy { requireArguments().getBoolean(ARG_ADD_BUTTON) }

    private lateinit var contentView: FrameLayout
    private lateinit var linesView: RowLayout
    private lateinit var imageView: FrescoView
    private lateinit var titleView: AppCompatTextView
    private lateinit var descriptionView: AppCompatTextView
    private lateinit var addButton: View

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        contentView = view.findViewById(R.id.content)
        contentView.round(20f.dp)
        contentView.setOnTouchListener { v, event ->
            onTouchEvent(event)
            true
        }

        linesView = view.findViewById(R.id.lines)

        view.findViewById<View>(R.id.close).setOnClickListener { finish() }

        imageView = view.findViewById(R.id.image)

        titleView = view.findViewById(R.id.title)
        descriptionView = view.findViewById(R.id.description)

        addButton = view.findViewById(R.id.add)
        addButton.setOnClickListener { addWallet() }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusBarOffset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarOffset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(0, statusBarOffset, 0, navBarOffset)
            insets
        }

        collectFlow(viewModel.storyFlow, ::applyStory)
        collectFlow(viewModel.progressFlow) { (index, progress) ->
            setProgress(index, progress)
        }
        applyLines()
    }

    private fun setProgress(targetIndex: Int, progress: Float) {
        val views = linesView.getViews().map { it as StoryProgressView }
        for ((index, view) in views.withIndex()) {
            if (targetIndex > index) {
                view.progress = 1f
            } else if (targetIndex == index) {
                view.progress = progress
            } else {
                view.progress = 0f
            }
        }
    }

    private fun onTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                viewModel.pause()
            }
            MotionEvent.ACTION_UP -> {
                val screenWidth = resources.displayMetrics.widthPixels
                val next = event.x > screenWidth / 2
                viewModel.resume(next)
            }
        }
    }

    private fun applyLines() {
        linesView.removeAllViews()

        for (i in 0 until viewModel.stories.size) {
            val view = StoryProgressView(requireContext())
            val params = LinearLayoutCompat.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 4.dp, 1f)
            params.gravity = Gravity.CENTER_VERTICAL
            params.setMargins(2.dp, 0, 2.dp, 0)
            linesView.addView(view, params)
        }
    }

    private fun addWallet() {
        viewModel.addWallet(requireContext()).catch {


        }.onEach { walletId ->
            navigation?.add(PickerScreen.newInstance(PickerMode.Focus(walletId)))
            navigation?.removeByClass({
                finish()
            }, SettingsScreen::class.java)
        }.launchIn(lifecycleScope)
    }

    private fun applyStory(story: StoryEntity) {
        imageView.setLocalRes(story.imageResId)
        titleView.setText(story.titleResId)
        descriptionView.setText(story.descriptionResId)
        addButton.visibility = if (story.showButton && showAddButton) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    companion object {

        private const val ARG_ADD_BUTTON = "add_button"

        fun newInstance(addButton: Boolean): W5StoriesScreen {
            StoryEntity.prefetchImages()
            val fragment = W5StoriesScreen()
            fragment.putBooleanArg(ARG_ADD_BUTTON, addButton)
            return fragment
        }
    }
}