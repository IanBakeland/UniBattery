package com.unibattery.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.unibattery.R
import com.unibattery.bluetooth.BatteryState
import com.unibattery.bluetooth.BtDevice
import com.unibattery.bluetooth.BtStatus
import com.unibattery.repository
import com.unibattery.ui.MainActivity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "Bluetooth battery": all connected devices. Each size bucket gets its own composition rather than one
 * stretched layout: chips (1 row high), overview ring (2x2), list (4x2), dashboard (4x3+) and a two-pane
 * dashboard for tablets and unfolded foldables.
 */
class BatteryWidget : GlanceAppWidget() {
  // Exact, not Responsive: launcher cells vary a lot (a One UI 2x2 is ~230dp square, a Pixel one ~140dp), so
  // layouts are picked and scaled from the real size instead of the nearest preset.
  override val sizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repo = context.repository
    provideContent {
      val state by repo.state.collectAsState()
      GlanceTheme { CompositionLocalProvider(LocalRefreshing provides state.refreshing) { Dashboard(state) } }
    }
  }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget = BatteryWidget()
}

class RefreshAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    context.repository.refresh().join()
    updateWidgets(context)
  }
}

/** Redraws every widget type; called whenever battery state changes. */
suspend fun updateWidgets(context: Context) {
  BatteryWidget().updateAll(context)
  DeviceWidget().updateAll(context)
  GridWidget().updateAll(context)
}

@Composable
private fun Dashboard(state: BatteryState) {
  val size = LocalSize.current
  // Same priority as the app: lowest battery first, devices that don't report battery last.
  val devices = state.connected.sortedWith(compareBy(nullsLast()) { it.battery })
  val message = statusMessage(state)
  WidgetSurface {
    when {
      message != null -> MessageLayout(message)
      size.width < 220.dp && size.height < 110.dp -> Chips(devices.take(1))
      size.width < 220.dp -> OverviewLayout(devices, size)
      size.height < 110.dp -> Chips(devices.take(3))
      size.height < 220.dp -> ListLayout(devices)
      size.width < 400.dp -> DashboardLayout(devices)
      else -> WideDashboardLayout(devices, columns = if (size.width >= 560.dp) 3 else 2)
    }
  }
}

/** 1 row high: a chip per device, icon in a cookie plus the percentage. */
@Composable
private fun Chips(devices: List<BtDevice>) {
  Row(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    devices.forEach { d ->
      Row(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        CookieIcon(d, 32.dp)
        Spacer(GlanceModifier.width(6.dp))
        Text(percentText(d.battery), style = textStyle(16.sp, accentFor(d.battery), bold = true), maxLines = 1)
      }
    }
  }
}

/** 2x2: one big ring for the lowest device. */
@Composable
private fun OverviewLayout(devices: List<BtDevice>, size: DpSize) {
  val ring = minOf(size.width - 24.dp, size.height - 48.dp).coerceIn(56.dp, 160.dp)
  val lowest = devices.first()
  Box(GlanceModifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) { RefreshButton(28.dp) }
  Column(GlanceModifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
    Ring(lowest.battery, ring, ring / 11) {
      if (lowest.battery != null) Text(percentText(lowest.battery), style = textStyle((ring.value * 0.24f).sp, GlanceTheme.colors.onSurface, bold = true))
      else DeviceGlyph(lowest, ring * 0.4f, GlanceTheme.colors.onSurfaceVariant)
    }
    Spacer(GlanceModifier.height(6.dp))
    Text(
      lowest.name, maxLines = 1,
      style = textStyle(12.sp, GlanceTheme.colors.onSurfaceVariant).copy(textAlign = TextAlign.Center),
    )
  }
}

/** 4x2: title, refresh and a compact row per device. */
@Composable
private fun ListLayout(devices: List<BtDevice>) {
  Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
    Header(LocalContext.current.getString(R.string.title))
    LazyColumn {
      items(devices, itemId = { it.address.hashCode().toLong() }) { d ->
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(openApp()), verticalAlignment = Alignment.CenterVertically) {
          CookieIcon(d, 28.dp)
          Spacer(GlanceModifier.width(10.dp))
          Text(d.name, maxLines = 1, style = textStyle(14.sp, GlanceTheme.colors.onSurface), modifier = GlanceModifier.defaultWeight())
          Text(percentText(d.battery), style = textStyle(16.sp, accentFor(d.battery), bold = true))
        }
      }
    }
  }
}

/** 4x3 and up: overview header (ring + summary) above tonal device tiles with level bars. */
@Composable
private fun DashboardLayout(devices: List<BtDevice>) {
  Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      LowestRing(devices.first(), 64.dp)
      Spacer(GlanceModifier.width(12.dp))
      Summary(devices, GlanceModifier.defaultWeight())
      RefreshButton()
    }
    LazyColumn {
      items(devices, itemId = { it.address.hashCode().toLong() }) { d ->
        Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) { DeviceBarTile(d) }
      }
    }
  }
}

/** Tablets and unfolded foldables: overview pane on the left, grid of ring tiles on the right. */
@Composable
private fun WideDashboardLayout(devices: List<BtDevice>, columns: Int) {
  Row(GlanceModifier.fillMaxSize().padding(12.dp)) {
    Column(
      GlanceModifier.width(150.dp).fillMaxHeight().background(primaryContainerFor(devices.first().battery)).cornerRadius(24.dp).padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LowestRing(devices.first(), 96.dp)
      Spacer(GlanceModifier.height(10.dp))
      Summary(devices, GlanceModifier, center = true)
      Spacer(GlanceModifier.height(10.dp))
      RefreshButton()
    }
    Spacer(GlanceModifier.width(10.dp))
    LazyVerticalGrid(GridCells.Fixed(columns), GlanceModifier.defaultWeight()) {
      items(devices, itemId = { it.address.hashCode().toLong() }) { d ->
        Box(GlanceModifier.padding(3.dp)) { RingTile(d) }
      }
    }
  }
}

@Composable
private fun Header(title: String) {
  Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, maxLines = 1, style = textStyle(15.sp, GlanceTheme.colors.onSurface, bold = true), modifier = GlanceModifier.defaultWeight())
    RefreshButton()
  }
}

/** Whether a manual refresh is running, so the refresh button can show progress instead. */
internal val LocalRefreshing = compositionLocalOf { false }

/** Refresh button; turns into a spinner while refreshing so a tap visibly does something. */
@Composable
internal fun RefreshButton(size: Dp = 36.dp) {
  if (LocalRefreshing.current) {
    Box(GlanceModifier.size(size).background(GlanceTheme.colors.secondaryContainer).cornerRadius(size / 2), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(GlanceModifier.size(size * 0.55f), color = GlanceTheme.colors.onSecondaryContainer)
    }
  } else {
    CircleIconButton(
      imageProvider = ImageProvider(R.drawable.ic_refresh),
      contentDescription = LocalContext.current.getString(R.string.refresh),
      onClick = actionRunCallback<RefreshAction>(),
      backgroundColor = GlanceTheme.colors.secondaryContainer,
      contentColor = GlanceTheme.colors.onSecondaryContainer,
      modifier = GlanceModifier.size(size),
    )
  }
}

@Composable
private fun LowestRing(lowest: BtDevice, size: Dp) {
  Ring(lowest.battery, size, if (size > 80.dp) 9.dp else 6.dp) {
    if (lowest.battery != null) {
      Text(percentText(lowest.battery), style = textStyle(if (size > 80.dp) 24.sp else 17.sp, GlanceTheme.colors.onSurface, bold = true))
    } else {
      DeviceGlyph(lowest, size * 0.4f, GlanceTheme.colors.onSurfaceVariant)
    }
  }
}

/** "Lowest: X" plus the average, mirroring the in-app overview. */
@Composable
private fun Summary(devices: List<BtDevice>, modifier: GlanceModifier, center: Boolean = false) {
  val context = LocalContext.current
  val reporting = devices.mapNotNull { it.battery }
  val align = if (center) TextAlign.Center else TextAlign.Start
  Column(modifier, horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
    Text(
      devices.first().name, maxLines = 2,
      style = textStyle(14.sp, GlanceTheme.colors.onSurface, bold = true).copy(textAlign = align),
    )
    if (reporting.size > 1) {
      Text(
        context.getString(R.string.overview_average, reporting.sum() / reporting.size), maxLines = 1,
        style = textStyle(12.sp, GlanceTheme.colors.onSurfaceVariant).copy(textAlign = align),
      )
    }
  }
}

@Composable
private fun DeviceBarTile(d: BtDevice) {
  Column(
    GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.secondaryContainer).cornerRadius(20.dp)
      .padding(horizontal = 12.dp, vertical = 9.dp).clickable(openApp()),
  ) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      CookieIcon(d, 30.dp)
      Spacer(GlanceModifier.width(10.dp))
      Text(d.name, maxLines = 1, style = textStyle(14.sp, GlanceTheme.colors.onSecondaryContainer), modifier = GlanceModifier.defaultWeight())
      Text(percentText(d.battery), style = textStyle(20.sp, accentFor(d.battery), bold = true))
    }
    if (d.battery != null) {
      Spacer(GlanceModifier.height(7.dp))
      LevelBar(d.battery)
    }
  }
}

@Composable
private fun RingTile(d: BtDevice) {
  Column(
    GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.secondaryContainer).cornerRadius(24.dp)
      .padding(vertical = 10.dp, horizontal = 6.dp).clickable(openApp()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Ring(d.battery, 52.dp, 5.dp) { DeviceGlyph(d, 22.dp, accentFor(d.battery)) }
    Spacer(GlanceModifier.height(4.dp))
    Text(percentText(d.battery), style = textStyle(16.sp, accentFor(d.battery), bold = true))
    Text(d.name, maxLines = 1, style = textStyle(11.sp, GlanceTheme.colors.onSecondaryContainer).copy(textAlign = TextAlign.Center))
  }
}

@Composable
internal fun MessageLayout(message: Int) {
  Column(GlanceModifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
    Box(contentAlignment = Alignment.Center) {
      Image(ImageProvider(R.drawable.widget_cookie), null, GlanceModifier.size(40.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer))
      Image(ImageProvider(R.drawable.ic_bluetooth), null, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer))
    }
    Spacer(GlanceModifier.height(6.dp))
    Text(
      LocalContext.current.getString(message), maxLines = 2,
      style = textStyle(13.sp, GlanceTheme.colors.onSurfaceVariant).copy(textAlign = TextAlign.Center),
    )
  }
}

// ---- Shared widget parts (also used by DeviceWidget) ----

internal val WidgetRadius = 28.dp

internal fun openApp(): Action = actionStartActivity<MainActivity>()

/** Rounded widget container; the whole widget opens the app. */
@Composable
internal fun WidgetSurface(content: @Composable () -> Unit) {
  Box(
    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(WidgetRadius).clickable(openApp()),
    contentAlignment = Alignment.Center,
  ) { content() }
}

/** Null when there's something to show, otherwise the message explaining why not. */
internal fun statusMessage(state: BatteryState): Int? = when (state.status) {
  BtStatus.NoAdapter -> R.string.no_adapter_title
  BtStatus.NoPermission -> R.string.widget_permission
  BtStatus.Off -> R.string.widget_off
  BtStatus.On -> if (state.connected.isEmpty()) R.string.no_devices_connected else null
}

@Composable
internal fun percentText(level: Int?): String =
  if (level != null) LocalContext.current.getString(R.string.percent, level) else "—"

internal fun textStyle(size: TextUnit, color: ColorProvider, bold: Boolean = false) =
  TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** Same colour roles as the app: error when critical, tertiary when low, primary otherwise. */
@Composable
internal fun accentFor(level: Int?): ColorProvider = with(GlanceTheme.colors) {
  when {
    level == null -> outline
    level <= 15 -> error
    level <= 30 -> tertiary
    else -> primary
  }
}

@Composable
internal fun primaryContainerFor(level: Int?): ColorProvider = with(GlanceTheme.colors) {
  when {
    level == null -> surfaceVariant
    level <= 15 -> errorContainer
    level <= 30 -> tertiaryContainer
    else -> primaryContainer
  }
}

@Composable
internal fun onPrimaryContainerFor(level: Int?): ColorProvider = with(GlanceTheme.colors) {
  when {
    level == null -> onSurfaceVariant
    level <= 15 -> onErrorContainer
    level <= 30 -> onTertiaryContainer
    else -> onPrimaryContainer
  }
}

@Composable
internal fun DeviceGlyph(d: BtDevice, size: Dp, tint: ColorProvider) {
  Image(ImageProvider(d.kind.icon), null, GlanceModifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Device icon on the app's 9-sided cookie shape, coloured by battery level. */
@Composable
internal fun CookieIcon(d: BtDevice, size: Dp) {
  val level = if (d.connected) d.battery else null
  Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
    Image(ImageProvider(R.drawable.widget_cookie), null, GlanceModifier.size(size), colorFilter = ColorFilter.tint(primaryContainerFor(level)))
    DeviceGlyph(d, size * 0.5f, onPrimaryContainerFor(level))
  }
}

@Composable
internal fun LevelBar(level: Int) {
  LinearProgressIndicator(
    progress = level / 100f,
    modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
    color = accentFor(level),
    backgroundColor = GlanceTheme.colors.surface,
  )
}

/**
 * Battery ring. Glance has no determinate circular indicator, so the track and the (wavy, like the app's
 * CircularWavyProgressIndicator) arc are drawn as white masks and tinted with theme colours, which keeps
 * dynamic colour and light/dark switching working.
 */
@Composable
internal fun Ring(level: Int?, size: Dp, stroke: Dp, center: @Composable () -> Unit) {
  val context = LocalContext.current
  Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
    Image(
      ImageProvider(ringBitmap(context, size, stroke, 1f)), null, GlanceModifier.size(size),
      colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant),
    )
    if (level != null && level > 0) {
      Image(
        ImageProvider(ringBitmap(context, size, stroke, level / 100f)), null, GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(accentFor(level)),
      )
    }
    center()
  }
}

internal fun ringBitmap(context: Context, size: Dp, stroke: Dp, fraction: Float): Bitmap {
  val density = context.resources.displayMetrics.density
  val px = (size.value * density).roundToInt().coerceAtLeast(1)
  val strokePx = stroke.value * density
  val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = strokePx
    strokeCap = Paint.Cap.ROUND
    color = android.graphics.Color.WHITE
  }
  // Flat near empty/full (like the Material wavy indicator) and on small rings, where a few big waves look lumpy.
  val amplitude = if (fraction in 0.25f..0.95f && size >= 60.dp) strokePx * 0.2f else 0f
  val c = px / 2f
  val radius = c - strokePx / 2 - amplitude
  val waves = (2 * PI * radius / (strokePx * 2.2)).roundToInt().coerceAtLeast(10)
  val steps = (720 * fraction).roundToInt().coerceAtLeast(2)
  val path = Path()
  for (i in 0..steps) {
    val t = 2 * PI * fraction * i / steps
    val r = radius + amplitude * sin(waves * t)
    val a = t - PI / 2
    val x = (c + r * cos(a)).toFloat()
    val y = (c + r * sin(a)).toFloat()
    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  Canvas(bitmap).drawPath(path, paint)
  return bitmap
}
