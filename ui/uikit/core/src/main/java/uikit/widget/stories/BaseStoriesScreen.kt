package uikit.widget.stories

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uikit.R
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.extensions.dp
import uikit.extensions.getViews
import uikit.extensions.round
import uikit.widget.ColumnLayout
import uikit.widget.FrescoView
import uikit.widget.RowLayout
import java.util.concurrent.atomic.AtomicInteger

open class BaseStoriesScreen: BaseFragment(R.layout.fragment_stories) {

    data class Item(
        val image: Uri,
        val title: String? = null,
        val subtitle: String? = null,
        val button: String? = null
    )

    private lateinit var contentView: View
    private lateinit var imageView: FrescoView
    private lateinit var linesView: RowLayout
    private lateinit var closeView: View
    private lateinit var titleView: AppCompatTextView
    private lateinit var subtitleView: AppCompatTextView
    private lateinit var button: AppCompatTextView

    private val stories = mutableListOf<Item>()
    private var state = StoriesState()
    private var progress: Double = 0.0

    private var timerJob: Job? = null

    val isLastStory: Boolean
        get() = state.currentIndex == stories.size - 1

    val isFirstStory: Boolean
        get() = state.currentIndex == 0

    val currentIndex: Int
        get() = state.currentIndex

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

        view.findViewById<View>(R.id.stories_close).setOnClickListener { finish() }

        contentView = view.findViewById(R.id.stories_content)
        contentView.round(20f.dp)
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
        button = view.findViewById(R.id.story_button)
        button.setOnClickListener { onStoryButton(state.currentIndex) }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusBarOffset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarOffset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            applyContentMargin(statusBarOffset, navBarOffset)
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
    }

    override fun onPause() {
        super.onPause()
        windowInsetsController?.let {
            it.isAppearanceLightStatusBars = initialIsAppearanceLightStatusBars
            it.isAppearanceLightNavigationBars = initialIsAppearanceLightNavigationBars
        }
    }

    private fun applyContentMargin(top: Int, bottom: Int) {
        contentView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = top
            bottomMargin = bottom
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

    open fun onStoryItem(item: Item) {
        setTitle(item.title)
        setSubtitle(item.subtitle)
        setButton(item.button)
        imageView.setImageURI(item.image)
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

    private fun setSubtitle(subtitle: String?) {
        if (!subtitle.isNullOrBlank()) {
            subtitleView.visibility = View.VISIBLE
            subtitleView.text = subtitle
        } else {
            subtitleView.visibility = View.GONE
        }
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

    fun putItems(items: List<Item>) {
        this.stories.clear()
        this.stories.addAll(items)
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

    fun resume(next: Boolean) {
        state = state.copy(
            isAutoSwitchPaused = false
        )
        val elapsedSincePause = System.currentTimeMillis() - state.lastPauseTime
        if (172 >= elapsedSincePause) {
            if (next && !isLastStory) {
                nextStory()
            } else if (!next && !isFirstStory) {
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
        state = state.copy(
            currentIndex = if (state.currentIndex - 1 < 0) stories.size - 1 else state.currentIndex - 1
        )
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
        onStoryItem(stories[state.currentIndex])
        setProgress(0.0)
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
    }

}