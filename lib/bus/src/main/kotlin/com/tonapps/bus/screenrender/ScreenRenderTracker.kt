package com.tonapps.bus.screenrender

import android.os.Process
import java.lang.ref.WeakReference

/**
 * Measures how long a screen takes to show content after the user action that opened it.
 *
 * A session starts with [begin] and reports once. Calls for a screen with no active session are ignored,
 * so screens report their stages unconditionally. The first object that reports a lifecycle event owns the
 * session; lifecycle events and [abandon] from other instances of the screen are ignored, and so are stages
 * that name another owner. Any thread may call it: the time is taken at the call, the state lives on main.
 */
class ScreenRenderTracker internal constructor(
    private val scheduler: ScreenRenderScheduler,
) {

    @Volatile
    var reporter: ScreenRenderReporter? = null

    private val sessions = HashMap<ScreenRenderId, Session>()

    // Lives until the launch session ends: steps such as `passcode_entered` can come after it started.
    private var launch: LaunchState? = LaunchState()

    // Outlives `launch`: on Android the passcode overlay can still be up when the launch session starts.
    private var launchPause: Pause? = Pause()

    fun begin(screen: ScreenRenderId, trigger: ScreenRenderTrigger, isFirstOpen: Boolean) {
        val now = scheduler.now
        scheduler.runOnMain {
            start(Session(screen, trigger, isFirstOpen, start = now, pause = null))
        }
    }

    /** A launch step before the cold-launch session starts; it is reported among that session's marks. */
    fun launchMark(step: ScreenRenderLaunchStep) {
        val now = scheduler.now
        scheduler.runOnMain {
            val launch = launch ?: return@runOnMain
            if (launch.marks.any { it.name == step.key }) {
                return@runOnMain
            }
            val record: (Long) -> Unit = { time -> launch.record(step.key, time, launchPause?.total ?: 0L) }
            launchPause?.whenVisible(now, record) ?: record(now)
        }
    }

    /** The launch waits for the user, e.g. on the passcode screen; the wait is left out of every later time. */
    fun pauseLaunch() {
        val now = scheduler.now
        scheduler.runOnMain { launchPause?.pause(now) }
    }

    fun resumeLaunch() {
        val now = scheduler.now
        scheduler.runOnMain { launchPause?.resume(now) }
    }

    /** The launch does not end on the portfolio, e.g. it opens onboarding or the process started in the background. */
    fun cancelLaunch() {
        scheduler.runOnMain {
            launch = null
            if (sessions.values.none { it.pause != null }) {
                launchPause = null
            }
        }
    }

    /**
     * Starts the cold-launch portfolio session from the process start. Skipped when the launch was cancelled,
     * or when the app's own work took longer than [LAUNCH_LIMIT_MS].
     */
    fun beginLaunch(processStartTime: Long? = ScreenRenderTracker.processStartTime) {
        val now = scheduler.now
        scheduler.runOnMain {
            val launch = launch ?: return@runOnMain
            if (launch.session != null) {
                return@runOnMain
            }
            val pause = launchPause
            if (processStartTime == null || pause == null || now - processStartTime - pause.totalAt(now) >= LAUNCH_LIMIT_MS) {
                this.launch = null
                launchPause = null
                return@runOnMain
            }
            val session = Session(
                screen = ScreenRenderId.Portfolio,
                trigger = ScreenRenderTrigger.AppLaunch,
                isFirstOpen = true,
                start = processStartTime,
                pause = pause,
            )
            launch.session = session
            start(session)
        }
    }

    fun lifecycle(event: ScreenRenderLifecycle, screen: ScreenRenderId, owner: Any) {
        val now = scheduler.now
        scheduler.runOnMain {
            val session = sessions[screen] ?: return@runOnMain
            if (!session.claim(owner)) {
                return@runOnMain
            }
            session.whenVisible(now) { time ->
                val elapsed = session.elapsed(time)
                when (event) {
                    ScreenRenderLifecycle.Created -> session.createdMs = session.createdMs ?: elapsed
                    ScreenRenderLifecycle.WillShow -> session.willShowMs = session.willShowMs ?: elapsed
                    ScreenRenderLifecycle.Shown -> session.shownMs = session.shownMs ?: elapsed
                }
            }
        }
    }

    /** Call right after the content is applied to the UI; the stage is timed at the next frame. */
    fun contentApplied(stage: ScreenRenderStage, screen: ScreenRenderId, owner: Any? = null) {
        scheduler.runOnMain {
            val session = session(screen, owner) ?: return@runOnMain
            when (stage) {
                ScreenRenderStage.Cached -> {
                    if (session.cachedMs != null || session.isCachedPending || session.isFreshPending) {
                        return@runOnMain
                    }
                    session.isCachedPending = true
                }
                ScreenRenderStage.Fresh -> {
                    if (session.isFreshPending) {
                        return@runOnMain
                    }
                    session.isFreshPending = true
                }
            }
            scheduler.onNextFrame {
                if (sessions[session.screen] !== session) {
                    return@onNextFrame
                }
                session.whenVisible(scheduler.now) { time ->
                    if (sessions[session.screen] !== session) {
                        return@whenVisible
                    }
                    when (stage) {
                        ScreenRenderStage.Cached -> session.cachedMs = session.elapsed(time)
                        ScreenRenderStage.Fresh -> {
                            session.freshMs = session.elapsed(time)
                            finish(session, ScreenRenderOutcome.Fresh)
                        }
                    }
                }
            }
        }
    }

    fun mark(name: ScreenRenderMarkName, screen: ScreenRenderId, owner: Any? = null) {
        val now = scheduler.now
        scheduler.runOnMain {
            val session = session(screen, owner) ?: return@runOnMain
            if (session.marks.any { it.name == name.key }) {
                return@runOnMain
            }
            session.whenVisible(now) { time ->
                if (session.marks.none { it.name == name.key }) {
                    session.marks.add(ScreenRenderMark(name.key, session.elapsed(time)))
                }
            }
        }
    }

    fun requestStarted(screen: ScreenRenderId, owner: Any? = null) {
        val now = scheduler.now
        scheduler.runOnMain {
            val session = session(screen, owner) ?: return@runOnMain
            if (session.requestStart == null) {
                session.requestStart = now
            }
        }
    }

    fun requestFinished(screen: ScreenRenderId, owner: Any? = null) {
        val now = scheduler.now
        scheduler.runOnMain {
            val session = session(screen, owner) ?: return@runOnMain
            val requestStart = session.requestStart ?: return@runOnMain
            if (session.requestMs == null) {
                session.requestMs = Session.milliseconds(now - requestStart)
            }
        }
    }

    /** Fresh content could not be loaded; `cached_ms` tells whether cached content was on screen. */
    fun failed(screen: ScreenRenderId, error: ScreenRenderErrorKind, owner: Any? = null) {
        endAtNextFrame(screen, owner, ScreenRenderOutcome.Failed, error)
    }

    /** The screen shows content that needs no refresh, e.g. a tab reopened after it already loaded. */
    fun finish(screen: ScreenRenderId, owner: Any? = null) {
        endAtNextFrame(screen, owner, ScreenRenderOutcome.CachedOnly, null)
    }

    /** The screen went away before fresh content. Only the owner can abandon; an unclaimed session ends by timeout. */
    fun abandon(screen: ScreenRenderId, owner: Any) {
        scheduler.runOnMain {
            val session = sessions[screen] ?: return@runOnMain
            val sessionOwner = session.owner ?: return@runOnMain
            if (sessionOwner === owner) {
                finish(session, ScreenRenderOutcome.Abandoned)
            }
        }
    }

    /** Time in the background is not render time, so every session and the pending launch end here. */
    fun appDidEnterBackground() {
        scheduler.runOnMain {
            launch = null
            sessions.values.toList().forEach { finish(it, ScreenRenderOutcome.Abandoned) }
            launchPause = null
        }
    }

    internal fun isActive(screen: ScreenRenderId): Boolean = sessions[screen] != null

    private fun session(screen: ScreenRenderId, owner: Any?): Session? {
        val session = sessions[screen] ?: return null
        if (owner == null) {
            return session
        }
        val sessionOwner = session.owner ?: return null
        return session.takeIf { sessionOwner === owner }
    }

    private fun start(session: Session) {
        sessions[session.screen]?.let { finish(it, ScreenRenderOutcome.Abandoned) }
        sessions[session.screen] = session
        scheduleTimeout(session, TIMEOUT_MS)
    }

    // The passcode pause does not count towards the timeout.
    private fun scheduleTimeout(session: Session, delayMs: Long) {
        scheduler.after(delayMs) {
            if (sessions[session.screen] !== session) {
                return@after
            }
            val elapsed = scheduler.now - session.start - (session.pause?.totalAt(scheduler.now) ?: 0L)
            if (elapsed >= TIMEOUT_MS) {
                finish(session, ScreenRenderOutcome.Timeout)
            } else {
                scheduleTimeout(session, TIMEOUT_MS - elapsed)
            }
        }
    }

    // Deferred so a cached stage applied in the same pass is timed first.
    private fun endAtNextFrame(
        screen: ScreenRenderId,
        owner: Any?,
        outcome: ScreenRenderOutcome,
        errorKind: ScreenRenderErrorKind?,
    ) {
        scheduler.runOnMain {
            val session = session(screen, owner) ?: return@runOnMain
            scheduler.onNextFrame {
                session.whenVisible(scheduler.now) { finish(session, outcome, errorKind) }
            }
        }
    }

    private fun finish(session: Session, outcome: ScreenRenderOutcome, errorKind: ScreenRenderErrorKind? = null) {
        if (sessions[session.screen] !== session) {
            return
        }
        sessions.remove(session.screen)
        val pause = session.pause
        if (pause != null && launchPause === pause) {
            launch = null
            launchPause = null
        }
        reporter?.report(
            ScreenRenderReport(
                screen = session.screen,
                trigger = session.trigger,
                outcome = outcome,
                isFirstOpen = session.isFirstOpen,
                cachedMs = session.cachedMs,
                freshMs = session.freshMs,
                createdMs = session.createdMs,
                willShowMs = session.willShowMs,
                shownMs = session.shownMs,
                requestMs = session.requestMs,
                errorKind = errorKind,
                pausedMs = pause?.total?.takeIf { it > 0 }?.let { Session.milliseconds(it) },
                hadPasscode = pause?.wasPaused,
                marks = session.marks.toList(),
            ),
        )
    }

    companion object {
        const val TIMEOUT_MS = 30_000L
        const val LAUNCH_LIMIT_MS = 60_000L

        val shared: ScreenRenderTracker by lazy { ScreenRenderTracker(AndroidScreenRenderScheduler()) }

        /** The process start on the same clock as [ScreenRenderScheduler.now]. */
        val processStartTime: Long
            get() = Process.getStartUptimeMillis()
    }
}

private class LaunchMark(val name: String, val time: Long, val pausedBefore: Long)

private class LaunchState {
    val marks = ArrayList<LaunchMark>()

    // A step queued during the passcode pause can land after the session started; it then goes to the session.
    var session: Session? = null
        set(value) {
            field = value
            value?.let { session -> marks.forEach { session.addLaunchMark(it) } }
        }

    fun record(name: String, time: Long, pausedBefore: Long) {
        val mark = LaunchMark(name, time, pausedBefore)
        marks.add(mark)
        session?.addLaunchMark(mark)
    }
}

/** The passcode wait of a launch. Anything that happens while it is open is timed when it ends. */
private class Pause {
    private var openSince: Long? = null
    private val waiting = ArrayList<(Long) -> Unit>()

    var total = 0L
        private set
    var wasPaused = false
        private set

    fun pause(now: Long) {
        if (openSince != null) {
            return
        }
        openSince = now
        wasPaused = true
    }

    fun resume(now: Long) {
        val since = openSince ?: return
        total += now - since
        openSince = null
        val actions = waiting.toList()
        waiting.clear()
        actions.forEach { it(now) }
    }

    fun totalAt(now: Long): Long = total + (openSince?.let { now - it } ?: 0L)

    fun whenVisible(now: Long, action: (Long) -> Unit) {
        if (openSince != null) {
            waiting.add(action)
        } else {
            action(now)
        }
    }
}

private class Session(
    val screen: ScreenRenderId,
    val trigger: ScreenRenderTrigger,
    val isFirstOpen: Boolean,
    val start: Long,
    val pause: Pause?,
) {
    private var ownerRef: WeakReference<Any>? = null
    private var isClaimed = false

    val owner: Any?
        get() = ownerRef?.get()

    var cachedMs: Long? = null
    var freshMs: Long? = null
    var isCachedPending = false
    var isFreshPending = false
    var createdMs: Long? = null
    var willShowMs: Long? = null
    var shownMs: Long? = null
    var requestStart: Long? = null
    var requestMs: Long? = null
    val marks = ArrayList<ScreenRenderMark>()

    /** The first caller owns the session; an owner that has gone away keeps it from being claimed again. */
    fun claim(candidate: Any): Boolean {
        if (!isClaimed) {
            isClaimed = true
            ownerRef = WeakReference(candidate)
            return true
        }
        return owner === candidate
    }

    fun whenVisible(now: Long, action: (Long) -> Unit) {
        pause?.whenVisible(now, action) ?: action(now)
    }

    fun addLaunchMark(mark: LaunchMark) {
        if (marks.none { it.name == mark.name }) {
            marks.add(ScreenRenderMark(mark.name, milliseconds(mark.time - start - mark.pausedBefore)))
        }
    }

    /** Stages are timed after the pause, so the pause so far is left out. */
    fun elapsed(time: Long): Long = milliseconds(time - start - (pause?.total ?: 0L))

    companion object {
        fun milliseconds(interval: Long): Long = interval.coerceAtLeast(0L)
    }
}
