package com.tonapps.tonkeeper.ui.screen.stories.remote

import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import com.tonapps.extensions.containsQuery
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.wallet.api.entity.StoryEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import org.koin.android.ext.android.inject
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.stories.BaseStoriesScreen

class RemoteStoriesScreen: BaseStoriesScreen() {

    override val fragmentName: String = "RemoteStoriesScreen"

    private val stories: StoryEntity.Stories by lazy {
        requireArguments().getParcelableCompat(ARG_STORIES)!!
    }

    private val from: String by lazy {
        requireArguments().getString(ARG_FROM)!!
    }

    private val settingsRepository: SettingsRepository by inject()

    private val installId: String
        get() = settingsRepository.installId

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

        AnalyticsHelper.trackStoryOpen(
            installId = installId,
            storiesId = stories.id,
            from = from
        )
    }

    override fun onStoryItem(item: Item) {
        super.onStoryItem(item)
        AnalyticsHelper.trackStoryView(
            installId = installId,
            storiesId = stories.id,
            index = currentIndex + 1
        )
    }

    override fun onStoryButton(index: Int) {
        super.onStoryButton(index)
        val button = stories.list.getOrNull(index)?.button ?: return

        AnalyticsHelper.trackStoryClick(
            installId = installId,
            storiesId = stories.id,
            button = button,
            index = index
        )

        if (button.type == "deeplink") {
            val uri = button.payload.toUriOrNull() ?: return
            val builder = uri.buildUpon() ?: return
            if (!uri.containsQuery("from")) {
                builder.appendQueryParameter("from", "story")
            }
            navigation?.openURL(builder.build().toString())
            finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        settingsRepository.setStoriesViewed(stories.id)
    }

    companion object {

        private const val ARG_STORIES = "ARG_STORIES"
        private const val ARG_FROM = "ARG_FROM"

        fun newInstance(stories: StoryEntity.Stories, from: String): RemoteStoriesScreen {
            val fragment = RemoteStoriesScreen()
            fragment.putParcelableArg(ARG_STORIES, stories)
            fragment.putStringArg(ARG_FROM, from)
            return fragment
        }
    }
}