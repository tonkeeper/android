package com.tonapps.settings.dev.glass

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tonapps.uikit.color.GlassStyle
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.buttonPrimaryForegroundColor
import com.tonapps.uikit.color.iconPrimaryColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.container.MoonScaffold
import ui.theme.AppColorScheme
import ui.theme.MoonTheme
import ui.theme.UIKit
import ui.theme.appColorSchemeBlue
import ui.theme.appColorSchemeDark
import ui.theme.appColorSchemeLight
import ui.theme.modifiers.glass
import uikit.glass.GlassConfig
import uikit.glass.applyGlass
import uikit.extensions.dp as px

@Composable
fun GlassGalleryScreen(
    onBack: () -> Unit,
) {
    MoonScaffold(
        title = "Glass",
        onBack = onBack,
        content = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val context = LocalContext.current
                val isOverlayApplied = remember(context) {
                    val value = TypedValue()
                    context.theme.resolveAttribute(UIKitColor.glassEnabled, value, true) && value.data != 0
                }
                MoonItemSubtitle(
                    text = "Flag latched: ${GlassConfig.isEnabled.toOnOff()} · overlay: ${isOverlayApplied.toOnOff()}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                for (section in SECTIONS) {
                    MoonItemTitle(
                        text = section.title,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            MoonItemSubtitle(
                                text = "Views",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { viewContext ->
                                    val themed = ContextThemeWrapper(viewContext, section.themeRes)
                                    themed.theme.applyStyle(uikit.R.style.ThemeOverlay_App_Glass, true)
                                    val icons = LinearLayout(themed).apply {
                                        orientation = LinearLayout.HORIZONTAL
                                        clipChildren = false
                                        clipToPadding = false
                                        setGap(GAP_DP.px)
                                        addView(glassIcon(themed, UIKitIcon.ic_chevron_left_28, "Back"))
                                        addView(glassIcon(themed, UIKitIcon.ic_chevron_down_28, "Collapse"))
                                        addView(glassIcon(themed, UIKitIcon.ic_xmark_outline_28, "Close"))
                                    }
                                    LinearLayout(themed).apply {
                                        orientation = LinearLayout.VERTICAL
                                        setBackgroundColor(themed.backgroundPageColor)
                                        val padding = COLUMN_PADDING_DP.px
                                        setPadding(padding, padding, padding, padding)
                                        clipChildren = false
                                        clipToPadding = false
                                        setGap(GAP_DP.px)
                                        addView(glassCapsule(themed, "Regular", GlassStyle.Regular))
                                        addView(glassCapsule(themed, "Prominent", GlassStyle.Prominent))
                                        addView(
                                            glassCapsule(
                                                themed,
                                                "Custom tint",
                                                GlassStyle.Prominent,
                                                themed.accentRedColor
                                            )
                                        )
                                        addView(icons)
                                    }
                                },
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            MoonItemSubtitle(
                                text = "Compose",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            MoonTheme(section.colorScheme()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(UIKit.colorScheme.background.page)
                                        .padding(COLUMN_PADDING_DP.dp),
                                    verticalArrangement = Arrangement.spacedBy(GAP_DP.dp),
                                ) {
                                    GlassCapsule("Regular", GlassStyle.Regular)
                                    GlassCapsule("Prominent", GlassStyle.Prominent)
                                    GlassCapsule(
                                        "Custom tint",
                                        GlassStyle.Prominent,
                                        UIKit.colorScheme.accent.red
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(GAP_DP.dp)) {
                                        GlassIcon(UIKitIcon.ic_chevron_left_28, "Back")
                                        GlassIcon(UIKitIcon.ic_chevron_down_28, "Collapse")
                                        GlassIcon(UIKitIcon.ic_xmark_outline_28, "Close")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

private fun glassCapsule(
    context: Context,
    label: String,
    style: GlassStyle,
    @ColorInt tint: Int? = null,
): View {
    return TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CAPSULE_HEIGHT_DP.px)
        text = label
        setTextAppearance(uikit.R.style.TextAppearance_Label1)
        setTextColor(
            when (style) {
                GlassStyle.Regular -> context.textPrimaryColor
                GlassStyle.Prominent -> context.buttonPrimaryForegroundColor
            }
        )
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        setPaddingRelative(PADDING_DP.px, 0, 0, 0)
        applyGlass(style, tint)
    }
}

private fun glassIcon(
    context: Context,
    @DrawableRes iconRes: Int,
    label: String,
): View {
    return ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(CIRCLE_SIZE_DP.px, CIRCLE_SIZE_DP.px)
        setImageResource(iconRes)
        imageTintList = context.iconPrimaryColor.stateList
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = label
        applyGlass(GlassStyle.Regular)
    }
}

@Composable
private fun GlassCapsule(
    label: String,
    style: GlassStyle,
    tint: Color = Color.Unspecified,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CAPSULE_HEIGHT_DP.dp)
            .glass(style = style, tint = tint)
            .padding(start = PADDING_DP.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = UIKit.typography.label1,
            color = when (style) {
                GlassStyle.Regular -> UIKit.colorScheme.text.primary
                GlassStyle.Prominent -> UIKit.colorScheme.buttonPrimary.primaryForeground
            },
        )
    }
}

@Composable
private fun GlassIcon(
    @DrawableRes iconRes: Int,
    label: String,
) {
    Box(
        modifier = Modifier
            .size(CIRCLE_SIZE_DP.dp)
            .glass(GlassStyle.Regular),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = UIKit.colorScheme.icon.primary,
        )
    }
}

private fun LinearLayout.setGap(gapPx: Int) {
    dividerDrawable = GradientDrawable().apply {
        setSize(gapPx, gapPx)
    }
    showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
}

private fun Boolean.toOnOff(): String {
    return if (this) {
        "on"
    } else {
        "off"
    }
}

private class GlassSection(
    val title: String,
    @StyleRes val themeRes: Int,
    val colorScheme: @Composable () -> AppColorScheme,
)

private const val PADDING_DP = 16
private const val COLUMN_PADDING_DP = 8
private const val GAP_DP = 12
private const val CAPSULE_HEIGHT_DP = 56
private const val CIRCLE_SIZE_DP = 44

private val SECTIONS = listOf(
    GlassSection("Blue", uikit.R.style.Theme_App_Blue) { appColorSchemeBlue() },
    GlassSection("Dark", uikit.R.style.Theme_App_Dark) { appColorSchemeDark() },
    GlassSection("Light", uikit.R.style.Theme_App_Light) { appColorSchemeLight() },
)
