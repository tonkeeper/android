package uikit.widget.stories

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uikit.R
import uikit.base.BaseFragment
import uikit.extensions.dp
import uikit.extensions.getViews
import uikit.span.ClickableSpanCompat
import uikit.widget.AsyncImageView
import uikit.widget.RowLayout
import uikit.extensions.setMoonOnClickListener

open class BaseStoriesScreen: BaseFragment(R.layout.fragment_stories) {

    data class Item(
        val image: Uri,
        val title: String? = null,
        val subtitle: String? = null,
        val button: String? = null,
        val links: List<Link> = emptyList()
    )

    data class Link(val text: String, val url: String)

    private lateinit var contentView: View
    private lateinit var imageView: AsyncImageView
    private lateinit var linesView: RowLayout
    private lateinit var closeView: View
    private lateinit var textsView: View
    private lateinit var titleView: AppCompatTextView
    private lateinit var subtitleView: AppCompatTextView
    private lateinit var button: AppCompatTextView
    private lateinit var loaderView: View

    private val stories = mutableListOf<Item>()
    private var state = StoriesState()
    private var progress: Double = 0.0

    private var timerJob: Job? = null
    private var imageWaitJob: Job? = null
    private var isStoryReady = false
    private var isStoryShownReported = false
    private var storyRequest = 0

    private var pressedLink: ClickableSpan? = null

    val isLastStory: Boolean
        get() = state.currentIndex == stories.size - 1

    val isFirstStory: Boolean
        get() = state.currentIndex == 0

    val currentIndex: Int
        get() = state.currentIndex

    open val holdsLastStory: Boolean
        get() = false

    val windowInsetsController: WindowInsetsControllerCompat?
        get() = window?.let {
            WindowInsetsControllerCompat(it, it.decorView)
        }

    private val initialIsAppearanceLightStatusBars: Boolean by lazy {
        windowInsetsController?.isAppearanceLightStatusBars ?: false
    }

    private val initialIsAppearanceLightNavigationBars: Boolean by lazy {
        windowInsetsController?.isAppearanceLightNavigationBars ?: false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(Color.BLACK)

        view.findViewById<View>(R.id.stories_close).setMoonOnClickListener { onCloseRequested() }

        contentView = view.findViewById(R.id.stories_content)
        contentView.setOnTouchListener { v, event ->
            onTouchEvent(event)
            true
        }

        imageView = view.findViewById(R.id.story_image)
        imageView.setScaleTypeCenterCrop()

        linesView = view.findViewById(R.id.stories_lines)
        closeView = view.findViewById(R.id.stories_close)
        titleView = view.findViewById(R.id.story_title)
        subtitleView = view.findViewById(R.id.story_subtitle)
        subtitleView.setOnTouchListener { _, event -> onSubtitleTouch(event) }
        textsView = view.findViewById(R.id.story_texts)
        button = view.findViewById(R.id.story_button)
        button.setMoonOnClickListener { onStoryButton(state.currentIndex) }
        loaderView = view.findViewById(R.id.story_loader)

        val closeTopMargin = resources.getDimensionPixelSize(R.dimen.tertiaryHeight)
        val textsBottomMargin = 32.dp
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusBarOffset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarOffset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            linesView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarOffset
            }
            closeView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = closeTopMargin + statusBarOffset
            }
            textsView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = textsBottomMargin + navBarOffset
            }
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        if (initialIsAppearanceLightStatusBars || initialIsAppearanceLightNavigationBars) {
            windowInsetsController?.let {
                it.isAppearanceLightStatusBars = false
                it.isAppearanceLightNavigationBars = false
            }
        }
        if (stories.isNotEmpty() && !state.isAutoSwitchPaused) {
            startStoryTimer()
        }
    }

    override fun onPause() {
        super.onPause()
        stopStoryTimer()
        windowInsetsController?.let {
            it.isAppearanceLightStatusBars = initialIsAppearanceLightStatusBars
            it.isAppearanceLightNavigationBars = initialIsAppearanceLightNavigationBars
        }
    }

    open fun onProgress(targetIndex: Int, progress: Float) {
        val views = linesView.getViews().map { it as StoriesProgressView }
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

    open fun onStoryButton(index: Int) {
    }

    open fun onStoryLink(url: String) {}

    open fun onCloseRequested() {
        finish()
    }

    /** The current page started playing on a resumed screen; not called for a page left while loading. */
    open fun onStoryShown(index: Int) {}

    open fun onStoryItem(item: Item) {
        setTitle(item.title)
        setSubtitle(item.subtitle, item.links)
        setButton(item.button)
        showStoryContent(false)
        isStoryShownReported = false
        val request = ++storyRequest
        // A blocked or stalled CDN must not hold the text back for the whole download timeout;
        // the image still lands in the view if it arrives later. Started before the load, so a
        // memory-cache hit that settles synchronously cancels it.
        imageWaitJob = lifecycleScope.launch {
            delay(loaderDelay)
            loaderView.visibility = View.VISIBLE
            delay(imageWait - loaderDelay)
            onStoryImageSettled(request)
        }
        imageView.loadImageURI(item.image) { onStoryImageSettled(request) }
    }

    private fun onStoryImageSettled(request: Int) {
        if (request != storyRequest || isStoryReady) {
            return
        }
        showStoryContent(true)
        if (isResumed && !state.isAutoSwitchPaused) {
            startStoryTimer()
        }
    }

    private fun showStoryContent(isReady: Boolean) {
        isStoryReady = isReady
        imageWaitJob?.cancel()
        textsView.animate().cancel()
        if (isReady) {
            loaderView.visibility = View.GONE
            textsView.visibility = View.VISIBLE
            textsView.animate().alpha(1f).setDuration(contentFadeDuration).start()
        } else {
            loaderView.visibility = View.GONE
            textsView.alpha = 0f
            textsView.visibility = View.INVISIBLE
        }
    }

    private fun onTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pause()
            }
            MotionEvent.ACTION_UP -> {
                val screenWidth = resources.displayMetrics.widthPixels
                val next = event.x > screenWidth / 2
                resume(next)
            }
        }
    }
    private fun setTitle(title: String?) {
        if (!title.isNullOrBlank()) {
            titleView.visibility = View.VISIBLE
            titleView.text = title
        } else {
            titleView.visibility = View.GONE
        }
    }

    private fun setSubtitle(subtitle: String?, links: List<Link>) {
        if (!subtitle.isNullOrBlank()) {
            subtitleView.visibility = View.VISIBLE
            subtitleView.text = subtitle.withLinks(links)
        } else {
            subtitleView.visibility = View.GONE
        }
    }

    private fun String.withLinks(links: List<Link>): CharSequence {
        if (links.isEmpty()) {
            return this
        }
        val color = requireContext().getColor(com.tonapps.ui.uikit.color.R.color.textAccentDark)
        val builder = SpannableStringBuilder(this)
        for (link in links) {
            val start = indexOf(link.text)
            if (link.text.isNotEmpty() && start >= 0) {
                builder.setSpan(
                    ClickableSpanCompat(color) { onStoryLink(link.url) },
                    start,
                    start + link.text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }

    private fun onSubtitleTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pressedLink = findLink(event)
            if (pressedLink != null) {
                pause()
            }
        }
        val link = pressedLink ?: return false
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pressedLink = null
            resumeTimer()
            if (action == MotionEvent.ACTION_UP && findLink(event) === link) {
                link.onClick(subtitleView)
            }
        }
        return true
    }

    private fun findLink(event: MotionEvent): ClickableSpan? {
        val text = subtitleView.text as? Spanned ?: return null
        val layout = subtitleView.layout ?: return null
        val x = event.x - subtitleView.totalPaddingLeft + subtitleView.scrollX
        val y = event.y.toInt() - subtitleView.totalPaddingTop + subtitleView.scrollY
        if (y < 0 || y > layout.height) {
            return null
        }
        val line = layout.getLineForVertical(y)
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
            return null
        }
        val offset = layout.getOffsetForHorizontal(line, x)
        return text.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
    }

    private fun setButton(text: String?) {
        if (!text.isNullOrBlank()) {
            button.visibility = View.VISIBLE
            button.text = text
        } else {
            button.visibility = View.GONE
        }
    }

    private fun setProgress(newProgress: Double) {
        progress = newProgress
        onProgress(state.currentIndex, newProgress.toFloat())
    }

    fun putItems(items: List<Item>, startIndex: Int = 0) {
        this.stories.clear()
        this.stories.addAll(items)
        AsyncImageView.prefetch(requireContext(), items.map { it.image }.filter { it.scheme == "http" || it.scheme == "https" })
        state = state.copy(currentIndex = startIndex)
        applyLines()
        applyCurrentStory()
    }

    fun pause() {
        stopStoryTimer()
        state = state.copy(
            isAutoSwitchPaused = true,
            lastPauseTime = System.currentTimeMillis()
        )
    }

    fun resumeTimer() {
        state = state.copy(isAutoSwitchPaused = false)
        startStoryTimer()
    }

    fun resume(next: Boolean) {
        state = state.copy(
            isAutoSwitchPaused = false
        )
        val elapsedSincePause = System.currentTimeMillis() - state.lastPauseTime
        if (172 >= elapsedSincePause) {
            if (next) {
                if (!isLastStory) {
                    nextStory()
                } else if (holdsLastStory) {
                    startStoryTimer()
                } else {
                    finish()
                }
            } else if (!isFirstStory) {
                prevStory()
            } else {
                startStoryTimer()
            }
        } else {
            startStoryTimer()
        }
    }

    private fun startStoryTimer() {
        stopStoryTimer()
        if (!isStoryReady) {
            return
        }
        if (isResumed && !isStoryShownReported) {
            isStoryShownReported = true
            onStoryShown(state.currentIndex)
        }
        timerJob = lifecycleScope.launch {
            val startFromProgress = progress
            var remainingTime = autoSwitchDuration * (1 - startFromProgress)

            while (isActive && !state.isAutoSwitchPaused && remainingTime > 0) {
                delay(progressDelay)
                remainingTime -= progressDelay
                val progress = 1 - remainingTime / autoSwitchDuration
                setProgress(progress)
            }
            setProgress(1.0)
            nextStory()
        }
    }

    fun prevStory() {
        if (isFirstStory) {
            return
        }
        state = state.copy(currentIndex = state.currentIndex - 1)
        applyCurrentStory()
    }

    fun nextStory() {
        if (isLastStory) {
            setProgress(1.0)
            return
        }
        state = state.copy(
            currentIndex = (state.currentIndex + 1) % stories.size
        )
        applyCurrentStory()
    }

    private fun applyCurrentStory() {
        if (state.currentIndex < 0 || state.currentIndex >= stories.size) {
            state = state.copy(currentIndex = 0)
        }
        setProgress(0.0)
        onStoryItem(stories[state.currentIndex])
        startStoryTimer()
    }

    private fun stopStoryTimer() {
        timerJob?.cancel()
    }

    private fun applyLines() {
        linesView.removeAllViews()

        for (i in 0 until stories.size) {
            val view = StoriesProgressView(requireContext())
            val params = LinearLayoutCompat.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 4.dp, 1f)
            params.gravity = Gravity.CENTER_VERTICAL
            params.setMargins(2.dp, 0, 2.dp, 0)
            linesView.addView(view, params)
        }
    }

    companion object {
        private val autoSwitchDuration = 5000L
        private val progressDelay = 8L
        private const val loaderDelay = 500L
        private const val imageWait = 5000L
        private const val contentFadeDuration = 200L
    }

}