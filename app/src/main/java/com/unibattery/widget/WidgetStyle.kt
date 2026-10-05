package com.unibattery.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import com.unibattery.App
import com.unibattery.bluetooth.BatteryState
import com.unibattery.ui.FixedDarkColors
import com.unibattery.ui.FixedLightColors
import androidx.glance.color.ColorProvider as DayNightColorProvider

/** How the home-screen widgets look; set in the app's settings and shared by every widget. */
data class WidgetStyle(
  val opacity: Int = 100, // background, percent
  val cornerRadius: Int = 28, // dp
  val textScale: Int = 100, // percent
  val wallpaperColors: Boolean = true,
  val showLastKnown: Boolean = true,
) {
  companion object {
    private fun prefs(context: Context) = context.getSharedPreferences("widget_style", Context.MODE_PRIVATE)

    fun load(context: Context) = with(prefs(context)) {
      val d = WidgetStyle()
      WidgetStyle(
        opacity = getInt("opacity", d.opacity),
        cornerRadius = getInt("corner_radius", d.cornerRadius),
        textScale = getInt("text_scale", d.textScale),
        wallpaperColors = getBoolean("wallpaper_colors", d.wallpaperColors),
        showLastKnown = getBoolean("show_last_known", d.showLastKnown),
      )
    }
  }

  fun save(context: Context) = prefs(context).edit {
    putInt("opacity", opacity)
    putInt("corner_radius", cornerRadius)
    putInt("text_scale", textScale)
    putBoolean("wallpaper_colors", wallpaperColors)
    putBoolean("show_last_known", showLastKnown)
  }
}

val Context.widgetStyle get() = (applicationContext as App).widgetStyle

/** Saves [style]; App redraws the widgets when it changes. */
fun Context.setWidgetStyle(style: WidgetStyle) {
  style.save(this)
  widgetStyle.value = style
}

internal val LocalWidgetStyle = compositionLocalOf { WidgetStyle() }

private val FixedWidgetColors = ColorProviders(light = FixedLightColors, dark = FixedDarkColors)

/**
 * Theme, style and refresh state for a widget. [content] gets the state with remembered devices removed when
 * "show last known" is off, so every layout and message follows the setting.
 */
@Composable
internal fun WidgetTheme(state: BatteryState, content: @Composable (BatteryState) -> Unit) {
  val style by LocalContext.current.widgetStyle.collectAsState()
  GlanceTheme(if (style.wallpaperColors) GlanceTheme.colors else FixedWidgetColors) {
    CompositionLocalProvider(LocalRefreshing provides state.refreshing, LocalWidgetStyle provides style) {
      content(if (style.showLastKnown) state else state.copy(devices = state.connected))
    }
  }
}

/**
 * Glance can't fade a theme colour, so resolve its light and dark values and fade those. Baked in at draw
 * time: a wallpaper colour change shows on the next redraw, which is why 100% keeps the original colour.
 */
internal fun ColorProvider.withAlpha(context: Context, alpha: Float): ColorProvider = DayNightColorProvider(
  day = getColor(context.withNightMode(false)).copy(alpha = alpha),
  night = getColor(context.withNightMode(true)).copy(alpha = alpha),
)

private fun Context.withNightMode(night: Boolean): Context {
  val config = Configuration(resources.configuration)
  val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
  config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
  return createConfigurationContext(config)
}
