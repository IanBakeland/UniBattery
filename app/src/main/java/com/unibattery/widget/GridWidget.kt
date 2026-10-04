package com.unibattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.unibattery.bluetooth.BtDevice
import com.unibattery.repository
import kotlin.math.ceil

/**
 * "All devices (compact)": every connected device as a ring in a grid that fits whatever size the widget
 * is, so even a 2x2 shows them all. Wide widgets get a single row; extra devices scroll.
 */
class GridWidget : GlanceAppWidget() {
  override val sizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repo = context.repository
    provideContent {
      val state by repo.state.collectAsState()
      GlanceTheme {
        CompositionLocalProvider(LocalRefreshing provides state.refreshing) {
          val message = statusMessage(state)
          WidgetSurface {
            if (message != null) MessageLayout(message)
            else RingGrid(state.connected.sortedWith(compareBy(nullsLast()) { it.battery }))
          }
        }
      }
    }
  }
}

class GridWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget = GridWidget()
}

private val Padding = 10.dp
private val LabelHeight = 22.dp
private val MinCellHeight = 64.dp

@Composable
private fun RingGrid(devices: List<BtDevice>) {
  val size = LocalSize.current
  val width = size.width - Padding * 2
  val height = size.height - Padding * 2
  val columns = when {
    devices.size <= 1 -> 1
    width >= height * 1.6f -> minOf(devices.size, 4) // wide: one row
    else -> 2
  }
  val rows = ceil(devices.size / columns.toFloat()).toInt()
  // Fill the height when everything fits; otherwise keep cells readable and let the grid scroll.
  val cellHeight = maxOf(height / rows, MinCellHeight)
  val ring = minOf(width / columns - 8.dp, cellHeight - LabelHeight - 6.dp).coerceIn(28.dp, 120.dp)

  LazyVerticalGrid(GridCells.Fixed(columns), GlanceModifier.fillMaxSize().padding(Padding)) {
    items(devices, itemId = { it.address.hashCode().toLong() }) { d -> GridCell(d, ring, cellHeight) }
  }
}

@Composable
private fun GridCell(d: BtDevice, ring: Dp, height: Dp) {
  Column(
    GlanceModifier.fillMaxWidth().height(height).clickable(openApp()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Ring(d.battery, ring, (ring / 11).coerceAtLeast(3.dp)) { DeviceGlyph(d, ring * 0.42f, accentFor(d.battery)) }
    Text(
      percentText(d.battery), maxLines = 1,
      style = textStyle(if (ring >= 56.dp) 16.sp else 13.sp, accentFor(d.battery), bold = true).copy(textAlign = TextAlign.Center),
    )
  }
}
