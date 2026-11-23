package com.tonapps.tonkeeper.ui.screen.stories.remote

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.extensions.containsQuery
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.toUriOrNull
import com.tonapps.perps.PerpsFragment
import com.tonapps.perps.screens.common.PerpsSkipIntroDialog
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.koin.analytics
import com.tonapps.tonkeeper.koin.serverConfig
import com.tonapps.wallet.api.entity.StoryEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import org.koin.android.ext.android.inject
import ui.theme.MoonTheme
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.stories.BaseStoriesScreen

private const val FROM_QUERY = "from"
private const val STORY_SOURCE = "story"

private fun Uri.withStorySource(): Uri {
    if (isOpaque) {
        return this
    }
    if (!containsQuery(FROM_QUERY)) {
        return buildUpon().appendQueryParameter(FROM_QUERY, STORY_SOURCE).build()
    }
    if (DeepLinkRoute.resolve(this) !is DeepLinkRoute.Tabs.Browser) {
        return this
    }
    val query = encodedQuery.orEmpty()
        .split('&')
        .filter { it.substringBefore('=') != FROM_QUERY }
        .joinToString("&")
        .ifEmpty { null }
    return buildUpon().encodedQuery(query).appendQueryParameter(FROM_QUERY, STORY_SOURCE).build()
}

class RemoteStoriesScreen : BaseStoriesScreen() {

    override val fragmentName: String = "RemoteStoriesScreen"

    private val stories: StoryEntity.Stories by lazy {
        requireArguments().getParcelableCompat(ARG_STORIES)!!
    }

    private val from: String by lazy {
        requireArguments().getString(ARG_FROM)!!
    }

    private val perpsArgs: Bundle? by lazy {
        arguments?.getBundle(ARG_PERPS_ARGS)
    }

    private val hideLastButton: Boolean by lazy {
        arguments?.getBoolean(ARG_HIDE_LAST_BUTTON) == true
    }

    private val progressId: String by lazy {
        if (hideLastButton) {
            stories.id + BASICS_PROGRESS_SUFFIX
        } else {
            stories.id
        }
    }

    override val holdsLastStory: Boolean
        get() = perpsArgs != null

    private var gateCompleted = false

    private var skipIntroHost: ComposeView? = null

    private var skipIntroChosen = false

    private val settingsRepository: SettingsRepository by inject()

    private val environment: Environment by inject()

    private val installId: String
        get() = settingsRepository.installId

    private val closeNeedsConfirm: Boolean
        get() = !skipIntroChosen &&
            !gateCompleted &&
            perpsCloseNeedsConfirm(stories, perpsArgs != null, settingsRepository.perpsTermsAccepted)

    override val predictiveBackPreviewEnabled: Boolean
        get() = !closeNeedsConfirm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val startIndex = when {
            savedInstanceState != null -> savedInstanceState.getInt(STATE_CURRENT_INDEX)
            perpsArgs != null -> (settingsRepository.getStoryWatchedPage(progressId) + 1)
                .coerceAtMost(stories.list.lastIndex)
            hideLastButton -> {
                val next = settingsRepository.getStoryWatchedPage(progressId) + 1
                if (next <= stories.list.lastIndex) {
                    next
                } else {
                    settingsRepository.clearStoryWatchedPage(progressId)
                    0
                }
            }
            else -> 0
        }
        putItems(
            stories.list.mapIndexed { index, story ->
                val isLast = index == stories.list.lastIndex
                val showButton = when {
                    perpsArgs != null -> isLast
                    hideLastButton -> !isLast
                    else -> true
                }
                Item(
                    image = story.image.toUri(),
                    title = story.title,
                    subtitle = story.description,
                    button = story.button?.title?.takeIf { showButton },
                    links = story.links.map { link -> Link(link.text, link.url) }
                )
            },
            startIndex
        )

        context?.analytics?.trackStoryOpen(
            storiesId = stories.id,
            from = from
        )
    }

    override fun onStoryShown(index: Int) {
        super.onStoryShown(index)
        context?.analytics?.trackStoryView(
            storiesId = stories.id,
            index = index + 1
        )
        settingsRepository.setStoryWatchedPage(progressId, index)
    }

    override fun onStoryButton(index: Int) {
        super.onStoryButton(index)
        val button = stories.list.getOrNull(index)?.button ?: return

        context?.analytics?.trackStoryClick(
            storiesId = stories.id,
            payload = button.payload,
            type = button.type,
            title = button.title,
            index = index
        )

        if (perpsArgs != null) {
            if (isPerpsAgreement(stories, index)) {
                completePerpsAgreement()
            }
        } else {
            val url = buttonUrl(button)
            if (url != null) {
                navigation?.openURL(url)
                finish()
            }
        }
    }

    private fun buttonUrl(button: StoryEntity.Button): String? =
        button.payload.toUriOrNull()?.let { uri ->
            when (button.type) {
                "deeplink" -> uri.withStorySource().toString()
                "link" -> uri.toString()
                else -> null
            }
        }

    private fun completePerpsAgreement() {
        if (gateCompleted) {
            return
        }
        gateCompleted = true
        settingsRepository.perpsTermsAccepted = true
        val nav = navigation ?: return
        val pendingPerpsArgs = perpsArgs ?: return
        nav.removeByClass({}, RemoteStoriesScreen::class.java, PerpsFragment::class.java)
        nav.add(PerpsFragment.newInstance(pendingPerpsArgs))
    }

    override fun onCloseRequested() {
        if (closeNeedsConfirm) {
            showSkipIntro()
        } else {
            super.onCloseRequested()
        }
    }

    override fun onBackPressed(): Boolean {
        if (closeNeedsConfirm) {
            showSkipIntro()
            return false
        }
        return super.onBackPressed()
    }

    private fun showSkipIntro() {
        if (skipIntroHost != null) {
            return
        }
        val root = view as? ViewGroup ?: return
        pause()
        val keeperTermsUrl = requireContext().serverConfig?.termsOfUseUrl
        val host = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MoonTheme(colorScheme = environment.theme) {
                    PerpsSkipIntroDialog(
                        keeperTermsUrl = keeperTermsUrl,
                        onOpenLink = { url -> navigation?.openURL(url) },
                        onSkip = { chooseSkipIntro("skip") { finish() } },
                        onContinue = { chooseSkipIntro("continue") { completePerpsAgreement() } },
                        onDismiss = {
                            trackSkipIntro("dismiss")
                            removeSkipIntro()
                            if (isResumed) {
                                resumeTimer()
                            }
                        },
                    )
                }
            }
        }
        skipIntroHost = host
        root.addView(host)
    }

    private fun chooseSkipIntro(outcome: String, action: () -> Unit) {
        if (skipIntroChosen) {
            return
        }
        skipIntroChosen = true
        trackSkipIntro(outcome)
        removeSkipIntro()
        action()
    }

    private fun trackSkipIntro(outcome: String) {
        context?.analytics?.trackStoryCloseAlert(
            storiesId = stories.id,
            pageNumber = currentIndex.coerceAtLeast(0) + 1,
            outcome = outcome
        )
    }

    private fun removeSkipIntro() {
        skipIntroHost?.let { host ->
            (host.parent as? ViewGroup)?.removeView(host)
        }
        skipIntroHost = null
    }

    override fun onStoryLink(url: String) {
        super.onStoryLink(url)
        val uri = url.toUriOrNull() ?: return
        if (DeepLinkRoute.isInApp(uri)) {
            if (perpsArgs != null) {
                return
            }
            navigation?.openURL(uri.withStorySource().toString())
            finish()
        } else {
            navigation?.openURL(url)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_INDEX, currentIndex)
    }

    override fun onDestroyView() {
        removeSkipIntro()
        super.onDestroyView()
        settingsRepository.setStoriesViewed(stories.id)
    }

    companion object {

        private const val ARG_STORIES = "ARG_STORIES"
        private const val ARG_FROM = "ARG_FROM"
        private const val ARG_PERPS_ARGS = "ARG_PERPS_ARGS"
        private const val ARG_HIDE_LAST_BUTTON = "ARG_HIDE_LAST_BUTTON"
        private const val PERPS_GATE_FROM = "perps-gate"
        private const val STATE_CURRENT_INDEX = "STATE_CURRENT_INDEX"
        private const val BASICS_PROGRESS_SUFFIX = ":basics"

        fun newInstance(
            stories: StoryEntity.Stories,
            from: String,
            hideLastButton: Boolean = false
        ): RemoteStoriesScreen {
            val fragment = RemoteStoriesScreen()
            fragment.putParcelableArg(ARG_STORIES, stories)
            fragment.putStringArg(ARG_FROM, from)
            fragment.putBooleanArg(ARG_HIDE_LAST_BUTTON, hideLastButton)
            return fragment
        }

        fun newPerpsGate(stories: StoryEntity.Stories, perpsArgs: Bundle): RemoteStoriesScreen {
            val fragment = newInstance(stories, PERPS_GATE_FROM)
            fragment.setArgs(Bundle().apply { putBundle(ARG_PERPS_ARGS, perpsArgs) })
            return fragment
        }
    }
}