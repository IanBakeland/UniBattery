@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.unibattery.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.compose.ui.unit.Dp
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.Spacer as GlanceSpacer
import androidx.glance.layout.fillMaxSize as glanceFillMaxSize
import androidx.glance.layout.height as glanceHeight
import androidx.glance.layout.padding as glancePadding
import androidx.glance.layout.width
import androidx.glance.text.Text as GlanceText
import androidx.glance.text.TextAlign
import androidx.lifecycle.lifecycleScope
import com.unibattery.R
import com.unibattery.bluetooth.BatteryState
import com.unibattery.bluetooth.BtDevice
import com.unibattery.bluetooth.BtStatus
import com.unibattery.repository
import com.unibattery.ui.AppTheme
import com.unibattery.ui.DeviceIcon
import com.unibattery.ui.groupedShape
import kotlinx.coroutines.launch

/** Address of the device a widget follows; absent means "whichever connected device is lowest". */
private val DEVICE_KEY = stringPreferencesKey("device")

/**
 * "Device battery": one device, chosen when the widget is added (or automatic). Compositions: ring only
 * (1x1), focused big number (2x2), strip (3x1+) and ring + details (3x2+).
 */
class DeviceWidget : GlanceAppWidget() {
  // Exact so the layout is picked and scaled from the real size (see BatteryWidget).
  override val sizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repo = context.repository
    provideContent {
      val state by repo.state.collectAsState()
      val address = currentState(DEVICE_KEY)
      GlanceTheme { CompositionLocalProvider(LocalRefreshing provides state.refreshing) { DeviceContent(state, address) } }
    }
  }
}

class DeviceWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget = DeviceWidget()
}

@Composable
private fun DeviceContent(state: BatteryState, address: String?) {
  val size = LocalSize.current
  val device = if (address == null) {
    state.connected.filter { it.battery != null }.minByOrNull { it.battery!! } ?: state.connected.firstOrNull()
  } else {
    state.devices.find { it.address == address }
  }
  // A chosen device that's merely disconnected still shows (greyed); otherwise explain what's wrong.
  val message = if (device == null || state.status != BtStatus.On) statusMessage(state) ?: R.string.no_devices_connected else null
  WidgetSurface {
    when {
      message != null || device == null -> GlanceText(
        LocalContext.current.getString(message ?: R.string.no_devices_connected), maxLines = 3,
        style = textStyle(12.sp, GlanceTheme.colors.onSurfaceVariant).copy(textAlign = TextAlign.Center),
        modifier = GlanceModifier.glancePadding(8.dp),
      )
      size.width < 110.dp && size.height < 110.dp -> RingOnly(device, minOf(size.width, size.height) - 8.dp)
      size.height < 110.dp -> Strip(device)
      size.height >= 150.dp && size.width < size.height * 1.6f -> Hero(device, size)
      size.width < 180.dp -> Focus(device)
      else -> Detail(device, size)
    }
  }
}

private val BtDevice.level get() = if (connected) battery else null

@Composable
private fun statusLine(d: BtDevice): String {
  val context = LocalContext.current
  return when {
    !d.connected -> context.getString(R.string.not_connected)
    d.battery == null -> context.getString(R.string.battery_unavailable)
    else -> context.getString(R.string.connected)
  }
}

/** 1x1: just the ring with the device icon inside. */
@Composable
private fun RingOnly(d: BtDevice, ring: Dp) {
  Ring(d.level, ring, ring / 11) { DeviceGlyph(d, ring * 0.42f, accentFor(d.level)) }
}

/** Square and roomy (e.g. a One UI 2x2): a ring that fills the widget, name and status underneath. */
@Composable
private fun Hero(d: BtDevice, size: DpSize) {
  val ring = minOf(size.width - 40.dp, size.height - 84.dp).coerceIn(72.dp, 200.dp)
  Box(GlanceModifier.glanceFillMaxSize().glancePadding(10.dp), contentAlignment = Alignment.TopEnd) { RefreshButton(32.dp) }
  Column(
    GlanceModifier.glanceFillMaxSize().glancePadding(horizontal = 16.dp, vertical = 14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Ring(d.level, ring, ring / 16) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DeviceGlyph(d, ring * 0.2f, accentFor(d.level))
        GlanceText(percentText(d.level), style = textStyle((ring.value * 0.2f).sp, GlanceTheme.colors.onSurface, bold = true))
      }
    }
    GlanceSpacer(GlanceModifier.glanceHeight(10.dp))
    GlanceText(
      d.name, maxLines = 1,
      style = textStyle(15.sp, GlanceTheme.colors.onSurface, bold = true).copy(textAlign = TextAlign.Center),
    )
    GlanceText(
      statusLine(d), maxLines = 1,
      style = textStyle(12.sp, GlanceTheme.colors.onSurfaceVariant).copy(textAlign = TextAlign.Center),
    )
  }
}

/** 2x2: device-focused, big percentage and a level bar. */
@Composable
private fun Focus(d: BtDevice) {
  Column(GlanceModifier.glanceFillMaxSize().glancePadding(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      CookieIcon(d, 30.dp)
      GlanceSpacer(GlanceModifier.width(8.dp))
      GlanceText(d.name, maxLines = 1, style = textStyle(13.sp, GlanceTheme.colors.onSurface, bold = true), modifier = GlanceModifier.defaultWeight())
      RefreshButton(28.dp)
    }
    GlanceSpacer(GlanceModifier.defaultWeight())
    GlanceText(percentText(d.level), style = textStyle(38.sp, accentFor(d.level), bold = true))
    GlanceSpacer(GlanceModifier.glanceHeight(6.dp))
    val level = d.level
    if (level != null) LevelBar(level)
    else GlanceText(statusLine(d), maxLines = 1, style = textStyle(11.sp, GlanceTheme.colors.onSurfaceVariant))
  }
}

/** 3x1 and wider: ring, name and status, percentage. */
@Composable
private fun Strip(d: BtDevice) {
  Row(GlanceModifier.glanceFillMaxSize().glancePadding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Ring(d.level, 42.dp, 4.dp) { DeviceGlyph(d, 18.dp, accentFor(d.level)) }
    GlanceSpacer(GlanceModifier.width(10.dp))
    Column(GlanceModifier.defaultWeight()) {
      GlanceText(d.name, maxLines = 1, style = textStyle(14.sp, GlanceTheme.colors.onSurface, bold = true))
      GlanceText(statusLine(d), maxLines = 1, style = textStyle(11.sp, GlanceTheme.colors.onSurfaceVariant))
    }
    GlanceText(percentText(d.level), style = textStyle(22.sp, accentFor(d.level), bold = true))
  }
}

/** 3x2 and up: large ring on a level-coloured panel, details beside it. */
@Composable
private fun Detail(d: BtDevice, size: DpSize) {
  val ring = minOf(size.height - 24.dp, size.width * 0.42f).coerceIn(72.dp, 160.dp)
  Row(GlanceModifier.glanceFillMaxSize().glancePadding(12.dp), verticalAlignment = Alignment.CenterVertically) {
    Ring(d.level, ring, ring / 11) {
      GlanceText(percentText(d.level), style = textStyle((ring.value * 0.24f).sp, GlanceTheme.colors.onSurface, bold = true))
    }
    GlanceSpacer(GlanceModifier.width(14.dp))
    Column(GlanceModifier.defaultWeight()) {
      Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CookieIcon(d, 34.dp)
        GlanceSpacer(GlanceModifier.defaultWeight())
        RefreshButton(32.dp)
      }
      GlanceSpacer(GlanceModifier.glanceHeight(6.dp))
      GlanceText(d.name, maxLines = 2, style = textStyle(16.sp, GlanceTheme.colors.onSurface, bold = true))
      GlanceText(statusLine(d), maxLines = 1, style = textStyle(12.sp, GlanceTheme.colors.onSurfaceVariant))
    }
  }
}

/**
 * Picks the device for a [DeviceWidget]. Optional on Android 12+ (the widget starts on "automatic" and can
 * be reconfigured with a long press); required when adding on older versions.
 */
class DeviceWidgetConfigActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
      ?: AppWidgetManager.INVALID_APPWIDGET_ID
    // Backing out cancels adding the widget.
    setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
    if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()

    setContent {
      AppTheme {
        val state by repository.state.collectAsState()
        DevicePicker(state.devices) { address -> save(widgetId, address) }
      }
    }
  }

  private fun save(widgetId: Int, address: String?) = lifecycleScope.launch {
    val glanceId = GlanceAppWidgetManager(this@DeviceWidgetConfigActivity).getGlanceIdBy(widgetId)
    updateAppWidgetState(this@DeviceWidgetConfigActivity, glanceId) { prefs ->
      if (address == null) prefs.remove(DEVICE_KEY) else prefs[DEVICE_KEY] = address
    }
    DeviceWidget().update(this@DeviceWidgetConfigActivity, glanceId)
    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
    finish()
  }
}

@Composable
private fun DevicePicker(devices: List<BtDevice>, onPick: (String?) -> Unit) {
  val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  Scaffold(
    modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = {
      LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.widget_choose)) },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer,
          scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scroll,
      )
    },
  ) { padding ->
    LazyColumn(
      Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      item("auto") {
        Surface(onClick = { onPick(null) }, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
          ListItem(
            headlineContent = { Text(stringResource(R.string.widget_auto)) },
            supportingContent = { Text(stringResource(R.string.widget_auto_body)) },
            leadingContent = { Icon(painterResource(R.drawable.ic_bluetooth), null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
        }
        Spacer(Modifier.height(14.dp))
      }
      itemsIndexed(devices, key = { _, d -> d.address }) { i, d ->
        Surface(onClick = { onPick(d.address) }, shape = groupedShape(i, devices.size), color = MaterialTheme.colorScheme.surfaceBright) {
          ListItem(
            headlineContent = { Text(d.name) },
            supportingContent = { Text(stringResource(if (d.connected) R.string.connected else R.string.not_connected)) },
            leadingContent = { DeviceIcon(d, size = 40) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
        }
      }
    }
  }
}
