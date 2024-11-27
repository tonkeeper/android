package com.tonapps.tonkeeper.ui.screen.stories.w5

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.facebook.common.util.UriUtil
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerMode
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerScreen
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.navigation.Navigation
import uikit.widget.stories.BaseStoriesScreen

class W5StoriesScreen: BaseStoriesScreen() {

    val viewModel: W5StoriesViewModel by viewModel()

    private val showAddButton: Boolean by lazy { requireArguments().getBoolean(ARG_ADD_BUTTON) }

    private val navigation: Navigation?
        get() = context?.let { Navigation.from(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val stories = mutableListOf<Item>()
        for ((index, story) in StoryEntity.all.withIndex()) {
            stories.add(Item(
                image = UriUtil.getUriForResourceId(story.imageResId),
                title = getString(story.titleResId),
                subtitle = getString(story.descriptionResId),
                button = if (showAddButton && index >= StoryEntity.all.size - 1) getString(Localization.w5_add_wallet) else null
            ))
        }
        putItems(stories)
    }

    override fun onStoryButton(index: Int) {
        super.onStoryButton(index)
        if (isLastStory) {
            addWallet()
        }
    }

    private fun addWallet() {
        viewModel.addWallet(requireContext()).catch {
            FirebaseCrashlytics.getInstance().recordException(it)
        }.onEach { walletId ->
            navigation?.add(PickerScreen.newInstance(PickerMode.Focus(walletId)))
            navigation?.removeByClass({
                finish()
            }, SettingsScreen::class.java)
        }.launchIn(lifecycleScope)
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