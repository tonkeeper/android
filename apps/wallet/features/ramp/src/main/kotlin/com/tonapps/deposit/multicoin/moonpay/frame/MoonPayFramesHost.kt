package com.tonapps.deposit.multicoin.moonpay.frame

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.tonapps.log.L
import com.tonapps.security.Security
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonLoader
import ui.theme.UIKit
import kotlin.time.Duration.Companion.seconds

private const val HEADLESS_ALPHA = 0.01f
private const val ONE_HOST_ONLY = "MoonPayFrames already has a host on screen"

@Composable
fun MoonPayFramesHost(frames: MoonPayFrames, concealed: StateFlow<Boolean>) {
    val context = LocalContext.current
    val token = remember { Any() }
    val state by frames.state.collectAsState()
    val platform = rememberFramePlatform()
    LaunchedEffect(frames, token) {
        val current = frames.state.value.host
        if (current != null && current !== token) {
            check(!Security.isDebuggable(context)) { ONE_HOST_ONLY }
            L.e(IllegalStateException(ONE_HOST_ONLY))
        }
        do {
            frames.state.first { it.host == null }
        } while (!frames.attachHost(token))
    }
    DisposableEffect(frames, token) {
        onDispose { frames.detachHost(token) }
    }
    if (state.host !== token) {
        return
    }
    state.entries.forEach { entry ->
        key(entry) {
            when (entry.presentation) {
                MoonPayFrames.Presentation.Headless -> FrameView(
                    entry = entry,
                    platform = platform,
                    modifier = Modifier.size(1.dp).alpha(HEADLESS_ALPHA),
                    onReady = {},
                    onLoaded = {},
                    accessible = false,
                )
                MoonPayFrames.Presentation.Modal -> Dialog(
                    onDismissRequest = entry::onClose,
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnBackPress = false,
                        decorFitsSystemWindows = false,
                    ),
                ) {
                    WindowBackground(UIKit.colorScheme.background.page)
                    Conceal(concealed)
                    ModalFrame(entry, platform)
                }
                MoonPayFrames.Presentation.Inline -> Unit
            }
        }
    }
}

// Removing the dialog would destroy its frame, so it is only hidden.
@Composable
private fun Conceal(concealed: StateFlow<Boolean>) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    LaunchedEffect(window, concealed) {
        withContext(Dispatchers.Main.immediate) {
            concealed.collect { hidden ->
                val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                if (hidden) {
                    window.addFlags(flags)
                } else {
                    window.clearFlags(flags)
                }
                window.decorView.visibility = if (hidden) {
                    View.INVISIBLE
                } else {
                    View.VISIBLE
                }
            }
        }
    }
}

// Until the frame's first draw the dialog shows its window, white by default. The window draws under the
// system bars, so their icons follow the theme.
@Composable
private fun WindowBackground(color: Color) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val light = UIKit.isLightTheme
    SideEffect {
        window ?: return@SideEffect
        window.setBackgroundDrawable(ColorDrawable(color.toArgb()))
        // A dialog drawn under the system bars dims the screen behind it on the way in and out.
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        WindowCompat.getInsetsController(window, window.decorView).run {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

// MoonPay's buy button of the payment [externalTransactionId], drawn in the caller's layout while it runs; nothing
// before it starts. Transparent, so the space shows the screen until the frame draws its button.
@Composable
fun MoonPayInlineFrame(
    frames: MoonPayFrames,
    externalTransactionId: String,
    // False while the button cannot be tapped: TalkBack must not reach it either.
    accessible: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by frames.state.collectAsState()
    val entry = state.entries.firstOrNull {
        (it.frame as? MoonPayFrame.BuyButton)?.externalTransactionId == externalTransactionId
    } ?: return
    val platform = rememberFramePlatform()
    key(entry) {
        FrameView(
            entry = entry,
            platform = platform,
            modifier = modifier,
            onReady = {},
            onLoaded = {},
            accessible = accessible,
        )
    }
}

// The widget's ready came 6 s after its load on a Pixel 6; this only bounds a frame that never sends one.
private val READY_FALLBACK = 10.seconds

// The card form and the 3DS page are drawn as they load, seconds before a ready.
private val MoonPayFrame.drawnOnLoad: Boolean
    get() = this is MoonPayFrame.AddCard || this is MoonPayFrame.Challenge

// Full screen as on iOS: the page fills the window between the system bars, the close button floats over it,
// and a loader stands in for the page until it is drawn. The widget and connect pages are drawn when MoonPay
// says they are ready, seconds after they load; the load only bounds the loader if that never comes.
@Composable
private fun ModalFrame(entry: MoonPayFrames.Entry, platform: FramePlatform) {
    var ready by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        if (loaded) {
            delay(READY_FALLBACK)
            ready = true
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UIKit.colorScheme.background.page),
    ) {
        FrameView(
            entry = entry,
            platform = platform,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            onReady = { ready = true },
            onLoaded = { loaded = true },
            accessible = true,
        )
        if (!ready) {
            // A page not drawn yet is already live: a tap must not reach a control nobody sees. A page drawn as it
            // loads stays tappable, as its load can hang on a resource long after the form is shown.
            val blockTouches = !entry.frame.drawnOnLoad
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (blockTouches) {
                            Modifier.pointerInput(Unit) {}
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MoonLoader()
            }
        }
        MoonActionIcon(
            painter = painterResource(UIKitIcon.ic_close_16),
            onClick = entry::onClose,
            tintColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                .padding(top = 12.dp, end = 16.dp),
            contentDescription = stringResource(Localization.close),
        )
    }
}

@Composable
private fun FrameView(
    entry: MoonPayFrames.Entry,
    platform: FramePlatform,
    modifier: Modifier,
    onReady: () -> Unit,
    onLoaded: () -> Unit,
    accessible: Boolean,
) {
    val scope = rememberCoroutineScope()
    val pageColor = if (entry.presentation == MoonPayFrames.Presentation.Inline) {
        android.graphics.Color.TRANSPARENT
    } else {
        UIKit.colorScheme.background.page.toArgb()
    }
    var view by remember { mutableStateOf<MoonPayFrameView?>(null) }
    AndroidView(
        factory = { context ->
            MoonPayFrameView(
                context = context,
                frame = entry.frame,
                environment = entry.environment,
                parentScope = scope,
                host = FrameHost(entry, platform, onReady, onLoaded),
            ).also {
                if (entry.presentation == MoonPayFrames.Presentation.Headless) {
                    it.isFocusable = false
                    it.isFocusableInTouchMode = false
                }
                // A WebView wrapping its content lays a page sized to 100% of the window out at zero height.
                it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                it.setBackgroundColor(pageColor)
                view = it
                it.load()
            }
        },
        modifier = modifier,
        update = {
            it.importantForAccessibility = if (accessible) {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        },
        onRelease = {
            it.destroy()
            entry.onFailure(MoonPayFrameException.HostGone())
        },
    )
    BackHandler(enabled = entry.presentation == MoonPayFrames.Presentation.Modal) {
        val frameView = view
        if (frameView != null && frameView.canGoBack()) {
            frameView.goBack()
        } else {
            entry.onClose()
        }
    }
    LaunchedEffect(view) {
        val frameView = view ?: return@LaunchedEffect
        entry.commands
            .catch { e ->
                if (e is CancellationException) {
                    throw e
                }
                entry.onFailure(MoonPayFrameException.LoadFailed("commands failed: ${e.message}"))
            }
            .collect(frameView::send)
    }
}

private class FrameHost(
    private val entry: MoonPayFrames.Entry,
    private val platform: FramePlatform,
    private val onReady: () -> Unit,
    private val onLoaded: () -> Unit,
) : MoonPayFrameView.Host {

    override val isListening: Boolean
        get() = entry.isListening

    override fun onEvent(event: MoonPayFrameEvent) {
        if (event is MoonPayFrameEvent.Ready) {
            onReady()
        }
        entry.onEvent(event)
    }

    override fun onPageFinished() {
        if (entry.frame.drawnOnLoad) {
            onReady()
        } else {
            onLoaded()
        }
    }

    override fun onFailure(error: MoonPayFrameException) {
        entry.onFailure(error)
    }

    override fun requestPermissions(permissions: Array<String>, onResult: (granted: Boolean) -> Unit) {
        platform.requestPermissions(permissions, onResult)
    }

    override fun pickFiles(params: WebChromeClient.FileChooserParams, onResult: (Array<Uri>?) -> Unit) {
        platform.pickFiles(params, onResult)
    }
}

private class FramePlatform(
    private val hasPermission: (String) -> Boolean,
) {

    lateinit var permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>

    lateinit var fileLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>

    private var pendingPermissions: ((Boolean) -> Unit)? = null

    private var pendingFiles: ((Array<Uri>?) -> Unit)? = null

    fun requestPermissions(permissions: Array<String>, onResult: (granted: Boolean) -> Unit) {
        when {
            permissions.all(hasPermission) -> onResult(true)
            pendingPermissions != null -> onResult(false)
            else -> {
                pendingPermissions = onResult
                permissionLauncher.launch(permissions)
            }
        }
    }

    fun pickFiles(params: WebChromeClient.FileChooserParams, onResult: (Array<Uri>?) -> Unit) {
        if (pendingFiles != null) {
            onResult(null)
            return
        }
        try {
            fileLauncher.launch(params.createIntent())
            pendingFiles = onResult
        } catch (_: ActivityNotFoundException) {
            onResult(null)
        }
    }

    fun onPermissionsResult(result: Map<String, Boolean>) {
        val callback = pendingPermissions ?: return
        pendingPermissions = null
        callback(result.isNotEmpty() && result.values.all { it })
    }

    fun onFilesResult(result: ActivityResult) {
        val callback = pendingFiles ?: return
        pendingFiles = null
        callback(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }
}

@Composable
private fun rememberFramePlatform(): FramePlatform {
    val context = LocalContext.current
    val platform = remember(context) {
        FramePlatform { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
    platform.permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        platform::onPermissionsResult,
    )
    platform.fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        platform::onFilesResult,
    )
    return platform
}
