package com.unibattery.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unibattery.repository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Wakes the app on device connect/disconnect so widgets update without the app running. When the process
 * is already alive the repository's own receiver handles the same broadcast too; refreshing twice is harmless.
 */
class BluetoothEventReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    MainScope().launch {
      try {
        val repo = context.repository
        repo.onBroadcast(intent)
        repo.refresh(manual = false).join()
        updateWidgets(context)
      } finally {
        pending.finish()
      }
    }
  }
}
