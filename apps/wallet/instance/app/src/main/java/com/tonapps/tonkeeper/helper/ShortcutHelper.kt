package com.tonapps.tonkeeper.helper

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.tonapps.emoji.Emoji
import com.tonapps.extensions.max18
import com.tonapps.tonkeeper.App
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.backgroundContentColor
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.wallet.data.account.entities.WalletEntity
import uikit.extensions.dp
import uikit.extensions.drawable

object ShortcutHelper {

    private fun createIcon(
        context: Context,
        @DrawableRes resId: Int
    ): Bitmap {
        val iconDrawable = context.drawable(resId)

        val size = 64.dp
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val iconSize = 24.dp
        val iconBitmap = iconDrawable.toBitmap(iconSize, iconSize)

        val iconLeft = (size - iconBitmap.width) / 2f
        val iconTop = (size - iconBitmap.height) / 2f
        canvas.drawBitmap(iconBitmap, iconLeft, iconTop, null)

        return bitmap
    }

    fun shortcutAction(
        context: Context,
        titleRes: Int,
        iconRes: Int,
        url: String
    ): ShortcutInfoCompat {
        val icon = IconCompat.createWithBitmap(createIcon(context, iconRes))

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.setPackage(context.packageName)

        val builder = ShortcutInfoCompat.Builder(context, url)
            .setShortLabel(context.getString(titleRes))
            .setIcon(icon)
            .setIntent(intent)

        return builder.build()
    }

    private suspend fun createWalletIcon(
        context: Context,
        wallet: WalletEntity
    ): Bitmap {
        val iconBitmap = Emoji.getBitmap(context, wallet.label.emoji)

        val size = 64.dp
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(wallet.label.color)

        val iconLeft = (size - iconBitmap.width) / 2f
        val iconTop = (size - iconBitmap.height) / 2f
        canvas.drawBitmap(iconBitmap, iconLeft, iconTop, null)

        return bitmap
    }

    suspend fun shortcutWallet(
        context: Context,
        wallet: WalletEntity
    ): ShortcutInfoCompat {
        val url = "tonkeeper://pick/${wallet.id}"
        val icon = IconCompat.createWithBitmap(createWalletIcon(context, wallet))

        val person = Person.Builder()
            .setName(wallet.label.name.max18)
            .setIcon(icon)
            .setUri(url)
            .setKey(wallet.id)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.setPackage(context.packageName)

        val builder = ShortcutInfoCompat.Builder(context, "wallet_${wallet.id}")
            .setShortLabel(wallet.label.name)
            .setIcon(person.icon)
            .setPerson(person)
            .setLongLived(true)
            .setLocusId(LocusIdCompat(wallet.id))
            .setIntent(intent)

        return builder.build()
    }
}