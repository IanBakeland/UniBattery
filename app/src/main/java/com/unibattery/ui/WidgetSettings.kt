package com.unibattery.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.unibattery.R
import com.unibattery.widget.WidgetStyle
import com.unibattery.widget.setWidgetStyle
import com.unibattery.widget.widgetStyle
import kotlin.math.roundToInt

/**
 * Opened from a widget's long-press "Widget settings" (Android 12+), or when adding one on older versions.
 * Changes save as they're made, so there's nothing to confirm; backing out keeps the widget.
 */
class WidgetSettingsActivity : ComponentActivity() {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
      ?: AppWidgetManager.INVALID_APPWIDGET_ID
    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

    setContent {
      AppTheme {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
          modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
          containerColor = MaterialTheme.colorScheme.surfaceContainer,
          topBar = {
            LargeFlexibleTopAppBar(
              title = { Text(stringResource(R.string.settings_widgets)) },
              colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              ),
              scrollBehavior = scroll,
            )
          },
        ) { padding ->
          Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            WidgetSettings(heading = false)
          }
        }
      }
    }
  }
}

/** Widget appearance, applied to every widget on the home screen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WidgetSettings(heading: Boolean = true) {
  val context = LocalContext.current
  // Sliders move this copy and only save when released, so dragging doesn't redraw every widget per step.
  var style by remember { mutableStateOf(context.widgetStyle.value) }
  fun save(new: WidgetStyle) {
    style = new
    context.setWidgetStyle(new)
  }
  // Rounded corners and wallpaper colours only exist on Android 12+.
  val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    if (heading) {
      Text(
        stringResource(R.string.settings_widgets),
        style = MaterialTheme.typography.titleMediumEmphasized,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 6.dp),
      )
    }
    SliderSetting(
      R.string.settings_widget_opacity, stringResource(R.string.percent, style.opacity),
      style.opacity, 0..100, step = 5, { style = style.copy(opacity = it) }, { save(style) },
    )
    if (modern) {
      SliderSetting(
        R.string.settings_widget_corners, stringResource(R.string.dp_value, style.cornerRadius),
        style.cornerRadius, 0..40, step = 2, { style = style.copy(cornerRadius = it) }, { save(style) },
      )
    }
    SliderSetting(
      R.string.settings_widget_text, stringResource(R.string.percent, style.textScale),
      style.textScale, 70..150, step = 10, { style = style.copy(textScale = it) }, { save(style) },
    )
    if (modern) {
      SwitchSetting(R.string.settings_widget_wallpaper, R.string.settings_widget_wallpaper_body, style.wallpaperColors) {
        save(style.copy(wallpaperColors = it))
      }
    }
    SwitchSetting(R.string.settings_widget_last_known, R.string.settings_widget_last_known_body, style.showLastKnown) {
      save(style.copy(showLastKnown = it))
    }
  }
}

@Composable
private fun SliderSetting(
  @StringRes label: Int,
  valueText: String,
  value: Int,
  range: IntRange,
  step: Int,
  onChange: (Int) -> Unit,
  onDone: () -> Unit,
) {
  Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
      Row(Modifier.fillMaxWidth()) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(valueText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
      }
      Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        onValueChangeFinished = onDone,
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / step - 1,
      )
    }
  }
}

@Composable
private fun SwitchSetting(@StringRes label: Int, @StringRes body: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
  Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    ListItem(
      headlineContent = { Text(stringResource(label)) },
      supportingContent = { Text(stringResource(body)) },
      trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
  }
}
