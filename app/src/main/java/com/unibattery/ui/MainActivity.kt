package com.unibattery.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import com.unibattery.notify.BatteryNotificationService
import com.unibattery.repository

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent { AppTheme { HomeScreen() } }
  }

  private var askedScan = false

  // Picks up permission / Bluetooth changes made in system settings while we were away.
  override fun onResume() {
    super.onResume()
    // Scan (for AirPods) is in the same "Nearby devices" group as connect, so once connect is granted Android
    // grants this without a dialog. Covers new installs and updates from versions that didn't ask for it.
    if (!askedScan && repository.hasPermission() && !repository.canScan() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      askedScan = true
      ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN), 0)
    }
    repository.refresh(manual = false)
    BatteryNotificationService.sync(this)
  }
}
