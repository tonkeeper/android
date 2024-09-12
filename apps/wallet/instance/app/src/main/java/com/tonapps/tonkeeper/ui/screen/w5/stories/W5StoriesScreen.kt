package com.tonapps.tonkeeper.ui.screen.w5.stories

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        contentView = view.findViewById(R.id.content)
        contentView.round(20f.dp)
        contentView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val x = event.x
                    val width = v.width
                    if (x < width / 2) {
                        viewModel.prevStory()
                    } else {
                        viewModel.nextStory()
                    }
                }
            }
            true
        }

        linesView = view.findViewById(R.id.lines)
        applyLines()

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
    }

    private fun applyLines() {

    }

    private fun addWallet() {
        viewModel.addWallet(requireContext()).catch {


        }.onEach { walletId ->
            navigation?.add(PickerScreen.newInstance(walletId))
            navigation?.removeByClass(SettingsScreen::class.java)
            finish()
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
            val fragment = W5StoriesScreen()
            fragment.putBooleanArg(ARG_ADD_BUTTON, addButton)
            return fragment
        }
    }
}