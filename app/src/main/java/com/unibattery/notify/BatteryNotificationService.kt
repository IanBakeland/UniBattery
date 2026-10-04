package com.unibattery.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.unibattery.R
import com.unibattery.bluetooth.BtDevice
import com.unibattery.repository
import com.unibattery.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service whose (silent, low-importance) notification is the battery readout.
 * It is only re-posted when the visible text actually changes, so it never buzzes or spams.
 */
class BatteryNotificationService : Service() {
  private val scope = MainScope()

  override fun onBind(intent: Intent?) = null

  override fun onCreate() {
    super.onCreate()
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
      NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        .apply { setShowBadge(false) }
    )
    scope.launch {
      repository.state.map { it.connected }.distinctUntilChanged().collect { nm.notify(ID, build(it)) }
    }
  }

  // Called for every startForegroundService(), so startForeground() must run here each time.
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
      ServiceCompat.startForeground(
        this, ID, build(repository.state.value.connected),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
      )
    } catch (_: Exception) { // permission revoked or background-start restriction
      stopSelf()
      return START_NOT_STICKY
    }
    return START_STICKY
  }

  override fun onDestroy() {
    scope.cancel()
    getSystemService(NotificationManager::class.java).cancel(ID)
    super.onDestroy()
  }

  private fun build(devices: List<BtDevice>) = NotificationCompat.Builder(this, CHANNEL).run {
    val lines = devices.map { notificationLine(it, ::getString) }
    setSmallIcon(R.drawable.ic_bluetooth)
    setOngoing(true)
    setOnlyAlertOnce(true)
    setSilent(true)
    setShowWhen(false)
    setCategory(NotificationCompat.CATEGORY_STATUS)
    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    setContentIntent(
      PendingIntent.getActivity(
        this@BatteryNotificationService, 0, Intent(this@BatteryNotificationService, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
      )
    )
    when (lines.size) {
      0 -> setContentTitle(getString(R.string.notification_none))
      1 -> setContentTitle(lines[0])
      else -> {
        setContentTitle(resources.getQuantityString(R.plurals.devices_connected, lines.size, lines.size))
        setContentText(lines.joinToString(" · "))
        setStyle(NotificationCompat.InboxStyle().also { s -> lines.forEach(s::addLine) })
      }
    }
    build()
  }

  companion object {
    private const val CHANNEL = "battery"
    private const val ID = 1
    private const val KEY = "notification_enabled"

    private fun prefs(context: Context) = context.getSharedPreferences("settings", MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
      prefs(context).edit { putBoolean(KEY, enabled) }
      sync(context)
    }

    /** Starts or stops the service to match the setting. Safe to call from anywhere. */
    fun sync(context: Context) {
      val intent = Intent(context, BatteryNotificationService::class.java)
      if (isEnabled(context) && context.repository.hasPermission()) {
        runCatching { ContextCompat.startForegroundService(context, intent) }
      } else {
        context.stopService(intent)
      }
    }
  }
}

/** "AirPods — 78%" or "Keyboard — Battery unavailable". */
fun notificationLine(device: BtDevice, string: (Int) -> String): String =
  string(R.string.notification_line).format(
    device.name,
    device.battery?.let { string(R.string.percent).format(it) } ?: string(R.string.battery_unavailable),
  )

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) = BatteryNotificationService.sync(context)
}
