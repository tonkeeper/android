package uikit.extensions

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.VectorDrawable
import androidx.annotation.ColorRes
import androidx.core.graphics.createBitmap
import com.facebook.drawee.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.toDrawable

fun VectorDrawable.asBitmapDrawable(): BitmapDrawable {
    return asBitmap().toDrawable(Resources.getSystem())
}

fun Drawable.setTintRes(context: Context, @ColorRes colorRes: Int) {
    setTint(context.getColor(colorRes))
}

fun Drawable.asBitmap(): Bitmap {
    if (this is BitmapDrawable && this.bitmap != null) {
        return this.bitmap
    } else {
        val width = if (intrinsicWidth > 0) intrinsicWidth else 1
        val height = if (intrinsicHeight > 0) intrinsicHeight else 1
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}

fun Drawable.asCircle(): Drawable {
    try {
        val bitmap = asBitmap()
        val output = createBitmap(bitmap.width, bitmap.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = bitmap.width / 2f
        val canvas = Canvas(output)
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        bitmap.recycle()
        return output.toDrawable(Resources.getSystem())
    } catch (e: Throwable) {
        return this
    }
}

val RippleDrawable.contentDrawable: Drawable?
    get() = findDrawableByLayerId(0)