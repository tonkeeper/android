package com.tonapps.bus.screenrender

enum class ScreenRenderId(val key: String) {
    Portfolio("portfolio"),
    History("history"),
    Trade("trade"),
    Browser("browser"),
}

enum class ScreenRenderTrigger {
    InApp,
    AppLaunch,
    Deeplink,
    Push,
    Auto,
}

enum class ScreenRenderStage {
    Cached,
    Fresh,
}

enum class ScreenRenderLifecycle {
    Created,
    WillShow,
    Shown,
}

enum class ScreenRenderOutcome {
    Fresh,
    CachedOnly,
    Failed,
    Abandoned,
    Timeout,
}

enum class ScreenRenderErrorKind {
    Timeout,
    Offline,
    Network,
    Server,
    Decode,
    Unknown,
}

/** Steps of a cold launch, reported among the launch session's marks. Same names as on iOS. */
enum class ScreenRenderLaunchStep(val key: String) {
    DidFinishLaunching("did_finish_launching"),
    SceneConnect("scene_connect"),
    RemoteConfigLoaded("remote_config_loaded"),
    RootStart("root_start"),
    PasscodeShown("passcode_shown"),
    PasscodeEntered("passcode_entered"),
    MainStart("main_start"),
}

/** A screen-specific step; each screen declares its own names next to itself. */
@JvmInline
value class ScreenRenderMarkName(val key: String)

data class ScreenRenderMark(val name: String, val ms: Long)

data class ScreenRenderReport(
    val screen: ScreenRenderId,
    val trigger: ScreenRenderTrigger,
    val outcome: ScreenRenderOutcome,
    val isFirstOpen: Boolean,
    val cachedMs: Long?,
    val freshMs: Long?,
    val createdMs: Long?,
    val willShowMs: Long?,
    val shownMs: Long?,
    val requestMs: Long?,
    val errorKind: ScreenRenderErrorKind?,
    /** Time the launch waited for the user; left out of every other time. */
    val pausedMs: Long?,
    /** Set on cold-launch sessions only. */
    val hadPasscode: Boolean?,
    val marks: List<ScreenRenderMark>,
)

fun interface ScreenRenderReporter {
    fun report(report: ScreenRenderReport)
}
