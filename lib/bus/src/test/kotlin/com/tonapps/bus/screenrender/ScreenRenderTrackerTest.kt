package com.tonapps.bus.screenrender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenRenderTrackerTest {

    private class ManualScheduler : ScreenRenderScheduler {
        override var now: Long = 100_000L
        private val frameActions = ArrayList<() -> Unit>()
        private val delayed = ArrayList<Pair<Long, () -> Unit>>()

        override fun runOnMain(action: () -> Unit) = action()

        override fun onNextFrame(action: () -> Unit) {
            frameActions.add(action)
        }

        override fun after(delayMs: Long, action: () -> Unit) {
            delayed.add(now + delayMs to action)
        }

        fun advance(ms: Long) {
            now += ms
            while (true) {
                val due = delayed.filter { it.first <= now }
                if (due.isEmpty()) {
                    return
                }
                delayed.removeAll(due.toSet())
                due.forEach { it.second() }
            }
        }

        fun renderFrame(afterMs: Long = 0) {
            now += afterMs
            val actions = frameActions.toList()
            frameActions.clear()
            actions.forEach { it() }
        }
    }

    private lateinit var scheduler: ManualScheduler
    private lateinit var reports: MutableList<ScreenRenderReport>
    private lateinit var tracker: ScreenRenderTracker
    private val owner = Any()

    @Before
    fun setUp() {
        scheduler = ManualScheduler()
        reports = ArrayList()
        tracker = ScreenRenderTracker(scheduler)
        tracker.reporter = ScreenRenderReporter { reports.add(it) }
    }

    @Test
    fun cachedThenFreshReportsBothStagesAtTheirFrames() {
        tracker.begin(ScreenRenderId.Portfolio, ScreenRenderTrigger.InApp, isFirstOpen = true)
        scheduler.advance(10)
        tracker.lifecycle(ScreenRenderLifecycle.Created, ScreenRenderId.Portfolio, owner)
        tracker.contentApplied(ScreenRenderStage.Cached, ScreenRenderId.Portfolio)
        scheduler.renderFrame(afterMs = 16)
        tracker.requestStarted(ScreenRenderId.Portfolio)
        scheduler.advance(300)
        tracker.requestFinished(ScreenRenderId.Portfolio)
        tracker.mark(ScreenRenderMarkName("total_fresh"), ScreenRenderId.Portfolio)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Portfolio)
        scheduler.renderFrame(afterMs = 16)

        assertEquals(
            listOf(
                ScreenRenderReport(
                    screen = ScreenRenderId.Portfolio,
                    trigger = ScreenRenderTrigger.InApp,
                    outcome = ScreenRenderOutcome.Fresh,
                    isFirstOpen = true,
                    cachedMs = 26,
                    freshMs = 342,
                    createdMs = 10,
                    willShowMs = null,
                    shownMs = null,
                    requestMs = 300,
                    errorKind = null,
                    pausedMs = null,
                    hadPasscode = null,
                    marks = listOf(ScreenRenderMark("total_fresh", 326)),
                ),
            ),
            reports,
        )
        assertFalse(tracker.isActive(ScreenRenderId.Portfolio))
    }

    @Test
    fun freshWithoutCacheLeavesCachedEmpty() {
        tracker.begin(ScreenRenderId.History, ScreenRenderTrigger.InApp, isFirstOpen = true)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.History)
        scheduler.renderFrame(afterMs = 200)

        assertEquals(ScreenRenderOutcome.Fresh, reports.single().outcome)
        assertNull(reports.single().cachedMs)
        assertEquals(200L, reports.single().freshMs)
    }

    @Test
    fun failureAfterCachedKeepsTheCachedStage() {
        tracker.begin(ScreenRenderId.Trade, ScreenRenderTrigger.InApp, isFirstOpen = false)
        tracker.contentApplied(ScreenRenderStage.Cached, ScreenRenderId.Trade)
        tracker.failed(ScreenRenderId.Trade, ScreenRenderErrorKind.Server)
        scheduler.renderFrame(afterMs = 50)

        assertEquals(ScreenRenderOutcome.Failed, reports.single().outcome)
        assertEquals(ScreenRenderErrorKind.Server, reports.single().errorKind)
        assertEquals(50L, reports.single().cachedMs)
    }

    @Test
    fun finishReportsCachedOnlyWithoutError() {
        tracker.begin(ScreenRenderId.Trade, ScreenRenderTrigger.InApp, isFirstOpen = false)
        tracker.contentApplied(ScreenRenderStage.Cached, ScreenRenderId.Trade)
        tracker.finish(ScreenRenderId.Trade)
        scheduler.renderFrame()

        assertEquals(ScreenRenderOutcome.CachedOnly, reports.single().outcome)
        assertNull(reports.single().errorKind)
    }

    @Test
    fun secondBeginAbandonsThePreviousSession() {
        tracker.begin(ScreenRenderId.Trade, ScreenRenderTrigger.InApp, isFirstOpen = true)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Trade)
        tracker.begin(ScreenRenderId.Trade, ScreenRenderTrigger.Deeplink, isFirstOpen = false)
        scheduler.renderFrame()

        assertEquals(listOf(ScreenRenderOutcome.Abandoned), reports.map { it.outcome })
        assertNull(reports.single().freshMs)
        assertTrue(tracker.isActive(ScreenRenderId.Trade))
    }

    @Test
    fun timeoutEndsAnUnfinishedSession() {
        tracker.begin(ScreenRenderId.Portfolio, ScreenRenderTrigger.InApp, isFirstOpen = true)
        scheduler.advance(ScreenRenderTracker.TIMEOUT_MS)

        assertEquals(listOf(ScreenRenderOutcome.Timeout), reports.map { it.outcome })
    }

    @Test
    fun callsWithoutSessionAreIgnored() {
        tracker.lifecycle(ScreenRenderLifecycle.Created, ScreenRenderId.Browser, owner)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Browser)
        tracker.mark(ScreenRenderMarkName("x"), ScreenRenderId.Browser)
        tracker.failed(ScreenRenderId.Browser, ScreenRenderErrorKind.Network)
        tracker.abandon(ScreenRenderId.Browser, owner)
        scheduler.renderFrame()

        assertTrue(reports.isEmpty())
    }

    @Test
    fun cachedAfterFreshIsIgnored() {
        tracker.begin(ScreenRenderId.History, ScreenRenderTrigger.InApp, isFirstOpen = true)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.History)
        tracker.contentApplied(ScreenRenderStage.Cached, ScreenRenderId.History)
        scheduler.renderFrame()

        assertNull(reports.single().cachedMs)
    }

    @Test
    fun abandonFromAnotherInstanceIsIgnored() {
        val coveredScreen = Any()
        tracker.begin(ScreenRenderId.History, ScreenRenderTrigger.Push, isFirstOpen = true)
        tracker.lifecycle(ScreenRenderLifecycle.Created, ScreenRenderId.History, owner)
        tracker.abandon(ScreenRenderId.History, coveredScreen)

        assertTrue(tracker.isActive(ScreenRenderId.History))

        tracker.abandon(ScreenRenderId.History, owner)

        assertEquals(listOf(ScreenRenderOutcome.Abandoned), reports.map { it.outcome })
    }

    @Test
    fun unclaimedSessionEndsByTimeoutNotByAbandon() {
        tracker.begin(ScreenRenderId.Trade, ScreenRenderTrigger.InApp, isFirstOpen = false)
        tracker.abandon(ScreenRenderId.Trade, Any())
        scheduler.advance(ScreenRenderTracker.TIMEOUT_MS)

        assertEquals(listOf(ScreenRenderOutcome.Timeout), reports.map { it.outcome })
    }

    @Test
    fun stagesFromAnotherInstanceAreIgnored() {
        val previousScreen = Any()
        tracker.begin(ScreenRenderId.History, ScreenRenderTrigger.Push, isFirstOpen = true)
        tracker.lifecycle(ScreenRenderLifecycle.Created, ScreenRenderId.History, owner)
        tracker.requestStarted(ScreenRenderId.History, previousScreen)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.History, previousScreen)
        tracker.failed(ScreenRenderId.History, ScreenRenderErrorKind.Server, previousScreen)
        scheduler.renderFrame()

        assertTrue(reports.isEmpty())

        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.History, owner)
        scheduler.renderFrame()

        assertEquals(listOf(ScreenRenderOutcome.Fresh), reports.map { it.outcome })
        assertNull(reports.single().requestMs)
    }

    @Test
    fun backgroundAbandonsEverySessionAndTheLaunch() {
        tracker.begin(ScreenRenderId.Trade, ScreenRenderTrigger.InApp, isFirstOpen = true)
        tracker.begin(ScreenRenderId.History, ScreenRenderTrigger.InApp, isFirstOpen = true)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Trade)
        tracker.appDidEnterBackground()
        scheduler.renderFrame(afterMs = 120_000)
        tracker.beginLaunch(processStartTime = scheduler.now - 1_000)

        assertEquals(setOf(ScreenRenderId.Trade, ScreenRenderId.History), reports.map { it.screen }.toSet())
        assertTrue(reports.all { it.outcome == ScreenRenderOutcome.Abandoned })
        assertFalse(tracker.isActive(ScreenRenderId.Portfolio))
    }

    @Test
    fun launchIsTimedFromTheProcessStartWithItsMarks() {
        val processStart = scheduler.now - 1_000
        tracker.launchMark(ScreenRenderLaunchStep.DidFinishLaunching)
        scheduler.advance(500)
        tracker.launchMark(ScreenRenderLaunchStep.RootStart)
        tracker.beginLaunch(processStart)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Portfolio)
        scheduler.renderFrame(afterMs = 500)

        val report = reports.single()
        assertEquals(ScreenRenderTrigger.AppLaunch, report.trigger)
        assertEquals(2_000L, report.freshMs)
        assertEquals(
            listOf(ScreenRenderMark("did_finish_launching", 1_000), ScreenRenderMark("root_start", 1_500)),
            report.marks,
        )
        assertEquals(false, report.hadPasscode)
        assertNull(report.pausedMs)
    }

    @Test
    fun passcodeWaitBeforeTheSessionIsLeftOut() {
        val processStart = scheduler.now - 1_000
        tracker.launchMark(ScreenRenderLaunchStep.PasscodeShown)
        tracker.pauseLaunch()
        scheduler.advance(5_000)
        tracker.resumeLaunch()
        tracker.launchMark(ScreenRenderLaunchStep.PasscodeEntered)
        scheduler.advance(200)
        tracker.beginLaunch(processStart)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Portfolio)
        scheduler.renderFrame(afterMs = 300)

        val report = reports.single()
        assertEquals(
            listOf(ScreenRenderMark("passcode_shown", 1_000), ScreenRenderMark("passcode_entered", 1_000)),
            report.marks,
        )
        assertEquals(1_500L, report.freshMs)
        assertEquals(5_000L, report.pausedMs)
        assertEquals(true, report.hadPasscode)
    }

    @Test
    fun stagesDuringAPauseStillOpenAtSessionStartAreTimedAtTheResume() {
        val processStart = scheduler.now - 1_000
        tracker.pauseLaunch()
        scheduler.advance(200)
        tracker.launchMark(ScreenRenderLaunchStep.MainStart)
        tracker.beginLaunch(processStart)
        tracker.lifecycle(ScreenRenderLifecycle.Created, ScreenRenderId.Portfolio, owner)
        tracker.contentApplied(ScreenRenderStage.Cached, ScreenRenderId.Portfolio)
        scheduler.renderFrame(afterMs = 100)
        scheduler.advance(3_000)
        tracker.resumeLaunch()
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Portfolio)
        scheduler.renderFrame(afterMs = 50)

        val report = reports.single()
        // The pause ran from 1 000 ms to 4 300 ms of the launch: 3 300 ms, all left out.
        assertEquals(3_300L, report.pausedMs)
        assertEquals(1_000L, report.createdMs)
        assertEquals(1_000L, report.cachedMs)
        assertEquals(listOf(ScreenRenderMark("main_start", 1_000)), report.marks)
        assertEquals(1_050L, report.freshMs)
    }

    @Test
    fun passcodeEnteredAfterTheSessionStartIsReported() {
        val processStart = scheduler.now - 1_000
        tracker.launchMark(ScreenRenderLaunchStep.PasscodeShown)
        tracker.pauseLaunch()
        tracker.launchMark(ScreenRenderLaunchStep.MainStart)
        tracker.beginLaunch(processStart)
        scheduler.advance(2_000)
        tracker.resumeLaunch()
        tracker.launchMark(ScreenRenderLaunchStep.PasscodeEntered)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Portfolio)
        scheduler.renderFrame()

        assertEquals(
            listOf(
                ScreenRenderMark("passcode_shown", 1_000),
                ScreenRenderMark("main_start", 1_000),
                ScreenRenderMark("passcode_entered", 1_000),
            ),
            reports.single().marks,
        )
    }

    @Test
    fun launchBeginsOnce() {
        tracker.beginLaunch(processStartTime = scheduler.now - 1_000)
        tracker.lifecycle(ScreenRenderLifecycle.Created, ScreenRenderId.Portfolio, owner)
        tracker.abandon(ScreenRenderId.Portfolio, owner)
        tracker.beginLaunch(processStartTime = scheduler.now - 1_000)

        assertEquals(1, reports.size)
        assertFalse(tracker.isActive(ScreenRenderId.Portfolio))
    }

    @Test
    fun passcodeWaitDoesNotCountTowardsTheTimeout() {
        tracker.pauseLaunch()
        tracker.beginLaunch(processStartTime = scheduler.now)
        scheduler.advance(ScreenRenderTracker.TIMEOUT_MS + 5_000)

        assertTrue(reports.isEmpty())

        tracker.resumeLaunch()
        scheduler.advance(ScreenRenderTracker.TIMEOUT_MS)

        assertEquals(listOf(ScreenRenderOutcome.Timeout), reports.map { it.outcome })
    }

    @Test
    fun launchLongerThanTheLimitIsSkipped() {
        tracker.beginLaunch(processStartTime = scheduler.now - ScreenRenderTracker.LAUNCH_LIMIT_MS - 1)

        assertFalse(tracker.isActive(ScreenRenderId.Portfolio))
    }

    @Test
    fun cancelledLaunchDoesNotBegin() {
        tracker.cancelLaunch()
        tracker.beginLaunch(processStartTime = scheduler.now - 1_000)

        assertFalse(tracker.isActive(ScreenRenderId.Portfolio))
    }

    @Test
    fun regularSessionHasNoLaunchFields() {
        tracker.begin(ScreenRenderId.Trade, ScreenRenderTrigger.InApp, isFirstOpen = true)
        tracker.contentApplied(ScreenRenderStage.Fresh, ScreenRenderId.Trade)
        scheduler.renderFrame(afterMs = 100)

        assertNull(reports.single().hadPasscode)
        assertNull(reports.single().pausedMs)
        assertEquals(100L, reports.single().freshMs)
    }
}
