package com.unibattery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.unibattery.notify.BatteryNotificationService
import com.unibattery.repository

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent { AppTheme { HomeScreen() } }
  }

  // Picks up permission / Bluetooth changes made in system settings while we were away.
  override fun onResume() {
    super.onResume()
    repository.refresh(manual = false)
    BatteryNotificationService.sync(this)
  }
}
