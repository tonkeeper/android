package uikit.extensions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi

internal fun View.generateBitmapFromDraw(
    destBitmap: Bitmap,
    callback: (Bitmap) -> Unit
) {
    destBitmap.density = resources.displayMetrics.densityDpi
    computeScroll()
    val canvas = Canvas(destBitmap)
    canvas.translate((-scrollX).toFloat(), (-scrollY).toFloat())
    draw(canvas)
    callback(destBitmap)
}

@RequiresApi(Build.VERSION_CODES.O)
private fun SurfaceView.generateBitmapFromSurfaceViewPixelCopy(
    destBitmap: Bitmap,
    callback: (Bitmap) -> Unit
) {
    val onCopyFinished = PixelCopy.OnPixelCopyFinishedListener { result ->
        if (result == PixelCopy.SUCCESS) {
            callback(destBitmap)
        }
    }
    PixelCopy.request(this, null, destBitmap, onCopyFinished, handler)
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun Window.generateBitmapFromPixelCopy(
    boundsInWindow: Rect? = null,
    destBitmap: Bitmap,
    callback: (Bitmap) -> Unit
) {
    val onCopyFinished = PixelCopy.OnPixelCopyFinishedListener { result ->
        if (result == PixelCopy.SUCCESS) {
            callback(destBitmap)
        }
    }
    PixelCopy.request(this, boundsInWindow, destBitmap, onCopyFinished, Handler(Looper.getMainLooper()))
}

@RequiresApi(Build.VERSION_CODES.O)
private fun View.generateBitmapFromPixelCopy(
    window: Window,
    destBitmap: Bitmap,
    callback: (Bitmap) -> Unit
) {
    val locationInWindow = intArrayOf(0, 0)
    getLocationInWindow(locationInWindow)
    val x = locationInWindow[0]
    val y = locationInWindow[1]
    val boundsInWindow = Rect(x, y, x + width, y + height)
    return window.generateBitmapFromPixelCopy(boundsInWindow, destBitmap, callback)
}

fun View.generateBitmap(callback: (Bitmap) -> Unit) {
    val destBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    when {
        Build.VERSION.SDK_INT < 26 -> generateBitmapFromDraw(destBitmap, callback)
        this is SurfaceView -> generateBitmapFromSurfaceViewPixelCopy(destBitmap, callback)
        else -> {
            val window = context.activity?.window
            if (window != null) {
                generateBitmapFromPixelCopy(window, destBitmap, callback)
            } else {
                generateBitmapFromDraw(destBitmap, callback)
            }
        }
    }
}