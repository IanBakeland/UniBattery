package com.unibattery

import android.app.Application
import android.content.Context
import com.unibattery.bluetooth.BatteryRepository
import com.unibattery.notify.BatteryNotificationService
import com.unibattery.widget.updateWidgets
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class App : Application() {
  private val scope = MainScope()
  lateinit var repository: BatteryRepository
    private set

  override fun onCreate() {
    super.onCreate()
    repository = BatteryRepository(this, scope)
    scope.launch {
      repository.state.map { it.copy(refreshing = false) }.distinctUntilChanged().drop(1)
        .collect { updateWidgets(this@App) }
    }
    BatteryNotificationService.sync(this)
  }
}

val Context.repository get() = (applicationContext as App).repository
