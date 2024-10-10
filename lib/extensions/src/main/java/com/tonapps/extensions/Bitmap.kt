package com.tonapps.extensions

import android.graphics.Bitmap

fun Bitmap.circle(): Bitmap {
    val size = width.coerceAtMost(height)
    val x = (width - size) / 2
    val y = (height - size) / 2
    val bitmap = Bitmap.createBitmap(this, x, y, size, size)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint()
    val rect = android.graphics.Rect(0, 0, size, size)
    paint.isAntiAlias = true
    canvas.drawARGB(0, 0, 0, 0)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, rect, rect, paint)
    bitmap.recycle()
    return output
}