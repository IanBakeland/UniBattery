package com.unibattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.unibattery.R
import com.unibattery.bluetooth.BatteryState
import com.unibattery.bluetooth.BtDevice
import com.unibattery.bluetooth.BtStatus
import com.unibattery.repository
import com.unibattery.ui.MainActivity

class BatteryWidget : GlanceAppWidget() {
  override val sizeMode = SizeMode.Responsive(setOf(COMPACT, REGULAR))

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repo = context.repository
    provideContent {
      val state by repo.state.collectAsState()
      GlanceTheme { Content(state) }
    }
  }

  private companion object {
    val COMPACT = DpSize(110.dp, 110.dp)
    val REGULAR = DpSize(250.dp, 110.dp)
  }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget = BatteryWidget()
}

class RefreshAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    context.repository.refresh().join()
    BatteryWidget().updateAll(context)
  }
}

@Composable
private fun Content(state: BatteryState) {
  val context = LocalContext.current
  val wide = LocalSize.current.width >= 250.dp
  Column(
    GlanceModifier.fillMaxSize()
      .background(GlanceTheme.colors.widgetBackground)
      .cornerRadius(28.dp)
      .padding(12.dp)
      .clickable(actionStartActivity<MainActivity>()),
  ) {
    Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (wide) context.getString(R.string.title) else context.getString(R.string.app_name),
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
        modifier = GlanceModifier.defaultWeight(),
      )
      CircleIconButton(
        imageProvider = ImageProvider(R.drawable.ic_refresh),
        contentDescription = context.getString(R.string.refresh),
        onClick = actionRunCallback<RefreshAction>(),
        backgroundColor = GlanceTheme.colors.secondaryContainer,
        contentColor = GlanceTheme.colors.onSecondaryContainer,
        modifier = GlanceModifier.size(36.dp),
      )
    }
    val message = when (state.status) {
      BtStatus.NoAdapter -> R.string.no_adapter_title
      BtStatus.NoPermission -> R.string.widget_permission
      BtStatus.Off -> R.string.widget_off
      BtStatus.On -> if (state.connected.isEmpty()) R.string.no_devices_connected else null
    }
    if (message != null) {
      Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          context.getString(message),
          style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        )
      }
    } else {
      LazyColumn {
        items(state.connected, itemId = { it.address.hashCode().toLong() }) { DeviceTile(it, wide) }
      }
    }
  }
}

@Composable
private fun DeviceTile(device: BtDevice, wide: Boolean) {
  val context = LocalContext.current
  val level = device.battery
  val low = level != null && level <= 20
  val accent = if (low) GlanceTheme.colors.error else GlanceTheme.colors.primary
  Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
    Column(
      GlanceModifier.fillMaxWidth()
        .background(GlanceTheme.colors.secondaryContainer)
        .cornerRadius(20.dp)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
      Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
          GlanceModifier.size(32.dp).background(accent).cornerRadius(16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            ImageProvider(device.kind.icon), null,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(if (low) GlanceTheme.colors.onError else GlanceTheme.colors.onPrimary),
          )
        }
        Spacer(GlanceModifier.width(10.dp))
        if (wide) {
          Text(
            device.name, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 14.sp),
          )
        } else {
          Spacer(GlanceModifier.defaultWeight())
        }
        Text(
          if (level != null) context.getString(R.string.percent, level) else "—",
          style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
      }
      if (level != null) {
        Spacer(GlanceModifier.height(8.dp))
        LinearProgressIndicator(
          progress = level / 100f,
          modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
          color = accent,
          backgroundColor = GlanceTheme.colors.surface,
        )
      }
    }
  }
}
