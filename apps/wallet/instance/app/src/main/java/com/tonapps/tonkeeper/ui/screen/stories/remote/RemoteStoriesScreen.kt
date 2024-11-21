package com.tonapps.tonkeeper.ui.screen.stories.remote

import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.wallet.api.entity.StoryEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import org.koin.android.ext.android.inject
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.stories.BaseStoriesScreen

class RemoteStoriesScreen: BaseStoriesScreen() {

    private val stories: StoryEntity.Stories by lazy {
        requireArguments().getParcelableCompat(ARG_STORIES)!!
    }

    private val settingsRepository: SettingsRepository by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        putItems(stories.list.map {
            Item(
                image = it.image.toUri(),
                title = it.title,
                subtitle = it.description,
                button = it.button?.title
            )
        })
    }

    override fun onStoryButton(index: Int) {
        super.onStoryButton(index)
        val button = stories.list.getOrNull(index)?.button ?: return
        if (button.type == "deeplink") {
            navigation?.openURL(button.payload)
            finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        settingsRepository.setStoriesViewed(stories.id)
    }

    companion object {

        private const val ARG_STORIES = "ARG_STORIES"

        fun newInstance(stories: StoryEntity.Stories): RemoteStoriesScreen {
            val fragment = RemoteStoriesScreen()
            fragment.putParcelableArg(ARG_STORIES, stories)
            return fragment
        }
    }
}