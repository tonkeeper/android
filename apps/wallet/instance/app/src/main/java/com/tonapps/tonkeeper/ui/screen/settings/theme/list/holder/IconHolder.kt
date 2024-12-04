package com.tonapps.tonkeeper.ui.screen.settings.theme.list.holder

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.withSave
import androidx.core.view.doOnLayout
import com.tonapps.tonkeeper.core.LauncherIcon
import com.tonapps.tonkeeper.ui.screen.settings.theme.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.resolveColor
import com.tonapps.uikit.color.separatorCommonColor
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import uikit.extensions.activity
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.getDimensionPixelSize

class IconHolder(parent: ViewGroup): Holder<Item.Icon>(parent, R.layout.view_theme_icon) {

    private val activity: Activity?
        get() = itemView.context.activity

    private val iconView = findViewById<AppCompatImageView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)

    init {
        borderPaint.color = context.separatorCommonColor
    }

    override fun onBind(item: Item.Icon) {
        setDrawable(item.icon)
        titleView.text = item.icon.type
        itemView.setOnClickListener {
            LauncherIcon.setEnable(itemView.context, item.icon)
            Toast.makeText(itemView.context, getString(Localization.app_icon_changed), Toast.LENGTH_SHORT).show()
            activity?.finish()
        }
    }

    private fun setDrawable(icon: LauncherIcon) {
        val backgroundDrawable = icon.getBackgroundDrawable(itemView.context).apply {
            setBounds(0, 0, iconSize, iconSize)
        }

        val foregroundDrawable = itemView.context.drawable(icon.fgRes).apply {
            setBounds(0, 0, iconSize, iconSize)
        }

        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(path)
        backgroundDrawable.draw(canvas)

        canvas.withSave {
            val scale = 1.5f
            scale(scale, scale, iconSize / 2f, iconSize / 2f)
            foregroundDrawable.setBounds(0, 0, iconSize, iconSize)
            foregroundDrawable.draw(this)
        }

        canvas.drawRoundRect(0f, 0f, iconSize.toFloat(), iconSize.toFloat(), radius, radius, borderPaint)
        iconView.setImageBitmap(bitmap)
    }

    private companion object {

        private val iconSize = 64.dp
        private val radius = 16f.dp
        private val borderSize = 0.5f.dp

        private val path = Path().apply {
            addRoundRect(
                RectF(0f, 0f, iconSize.toFloat(), iconSize.toFloat()),
                radius,
                radius,
                Path.Direction.CW
            )
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = borderSize
        }

    }


}