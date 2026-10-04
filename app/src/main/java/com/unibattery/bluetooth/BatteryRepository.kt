package com.unibattery.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class BtStatus { NoAdapter, NoPermission, Off, On }

data class BtDevice(
  val address: String,
  val name: String,
  val kind: DeviceKind,
  val connected: Boolean,
  /** 0..100, or null when the device doesn't report a level. Never estimated. */
  val battery: Int?,
  /** Last level read while connected, and when (epoch millis). Kept so a disconnected device still shows it. */
  val lastBattery: Int? = null,
  val lastSeen: Long? = null,
)

data class BatteryState(
  val status: BtStatus,
  val devices: List<BtDevice> = emptyList(),
  val refreshing: Boolean = false,
) {
  val connected get() = devices.filter { it.connected }
  val paired get() = devices.filterNot { it.connected }
}

/**
 * Single source of truth for Bluetooth devices and their battery levels.
 *
 * Battery sources, in order:
 * 1. Android's own per-device level (HFP/AVRCP/Apple accessory reports, LE Battery Service on 14+),
 *    read through the hidden-but-allowlisted BluetoothDevice.getBatteryLevel().
 * 2. The last value from the system BATTERY_LEVEL_CHANGED broadcast.
 * 3. A direct GATT read of the standard Battery Service (0x180F) for connected LE devices.
 */
class BatteryRepository(private val context: Context, private val scope: CoroutineScope) {

  private val adapter: BluetoothAdapter? =
    context.getSystemService(BluetoothManager::class.java)?.adapter

  private val broadcastLevels = ConcurrentHashMap<String, Int>()
  private val gattLevels = ConcurrentHashMap<String, Int>()
  private val noBatteryService = ConcurrentHashMap.newKeySet<String>()
  private val aclConnected = ConcurrentHashMap.newKeySet<String>()
  private val mutex = Mutex()

  private val _state = MutableStateFlow(BatteryState(status()))
  val state: StateFlow<BatteryState> = _state.asStateFlow()

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
      val device = intent.bluetoothDevice()
      when (intent.action) {
        ACTION_BATTERY_LEVEL_CHANGED -> {
          val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
          if (device != null) {
            if (level in 0..100) broadcastLevels[device.address] = level
            else broadcastLevels.remove(device.address)
          }
        }
        BluetoothDevice.ACTION_ACL_CONNECTED -> device?.let { aclConnected += it.address }
        BluetoothDevice.ACTION_ACL_DISCONNECTED -> device?.let { forget(it.address) }
        BluetoothAdapter.ACTION_STATE_CHANGED -> if (adapter?.isEnabled != true) {
          broadcastLevels.clear(); gattLevels.clear(); noBatteryService.clear(); aclConnected.clear()
        }
      }
      refresh(manual = false)
    }
  }

  init {
    val filter = IntentFilter().apply {
      addAction(ACTION_BATTERY_LEVEL_CHANGED)
      addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
      addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
      addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
      addAction(BluetoothDevice.ACTION_NAME_CHANGED)
      addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    }
    // Exported: on modern Android these come from the Bluetooth app process, not system_server.
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    refresh(manual = false)
    // ponytail: fixed 5 min poll only matters for GATT-read devices; system-reported levels arrive by broadcast.
    scope.launch {
      while (isActive) {
        delay(5.minutes)
        gattLevels.clear() // re-read LE Battery Service values
        refresh(manual = false)
      }
    }
  }

  // Icons the user picked, by device address. Used everywhere a device is shown (app, widgets, notification).
  private val icons = context.getSharedPreferences("device_icons", Context.MODE_PRIVATE)

  fun kindOverride(address: String): DeviceKind? =
    icons.getString(address, null)?.let { name -> DeviceKind.entries.find { it.name == name } }

  /** Pins a device's icon, or null to go back to automatic detection. */
  fun setKind(address: String, kind: DeviceKind?) {
    icons.edit().apply { if (kind == null) remove(address) else putString(address, kind.name) }.apply()
    refresh(manual = false)
  }

  // Last reported level per device address, as "level:epochMillis".
  private val lastLevels = context.getSharedPreferences("last_levels", Context.MODE_PRIVATE)

  /** Same handling as the runtime receiver, for broadcasts delivered to [com.unibattery.widget.BluetoothEventReceiver]. */
  fun onBroadcast(intent: Intent) = receiver.onReceive(context, intent)

  fun hasPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
      PackageManager.PERMISSION_GRANTED

  /** Manual refreshes also retry GATT on devices that previously had no Battery Service. */
  fun refresh(manual: Boolean = true): Job = scope.launch {
    mutex.withLock {
      if (manual) {
        _state.update { it.copy(refreshing = true) }
        noBatteryService.clear()
      }
      val start = System.currentTimeMillis()
      val next = load()
      // Keep the expressive loading indicator on screen long enough to read as feedback.
      if (manual) delay((600 - (System.currentTimeMillis() - start)).coerceAtLeast(0))
      _state.value = next
    }
  }

  private fun status(): BtStatus = when {
    adapter == null -> BtStatus.NoAdapter
    !hasPermission() -> BtStatus.NoPermission
    !adapter.isEnabled -> BtStatus.Off
    else -> BtStatus.On
  }

  @SuppressLint("MissingPermission") // status() == On implies permission
  private suspend fun load(): BatteryState {
    val status = status()
    if (status != BtStatus.On) return BatteryState(status)
    val bonded = try {
      adapter!!.bondedDevices.orEmpty()
    } catch (_: SecurityException) {
      return BatteryState(BtStatus.NoPermission)
    }
    val devices = coroutineScope {
      bonded.map { d -> async { toDevice(d) } }.awaitAll()
    }.map(::withLastLevel).sortedWith(compareByDescending<BtDevice> { it.connected }.thenBy { it.name.lowercase() })
    return BatteryState(status, devices)
  }

  @SuppressLint("MissingPermission")
  private suspend fun toDevice(d: BluetoothDevice): BtDevice {
    val connected = isConnected(d)
    val name = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) d.alias else null)
      ?: d.name ?: d.address
    var battery = if (connected) systemLevel(d) ?: broadcastLevels[d.address] else null
    if (connected && battery == null && d.type != BluetoothDevice.DEVICE_TYPE_CLASSIC &&
      d.address !in noBatteryService
    ) {
      battery = gattLevels[d.address] ?: readGattBattery(d)?.also { gattLevels[d.address] = it }
      if (battery == null) noBatteryService += d.address
    }
    return BtDevice(d.address, name, kindOverride(d.address) ?: kindOf(d.bluetoothClass?.deviceClass, name), connected, battery)
  }

  /** Saves a fresh reading, and attaches the last saved one so it survives a disconnect. */
  private fun withLastLevel(d: BtDevice): BtDevice {
    if (d.battery != null) lastLevels.edit().putString(d.address, "${d.battery}:${System.currentTimeMillis()}").apply()
    val last = lastLevels.getString(d.address, null)?.split(':')
    return d.copy(lastBattery = last?.getOrNull(0)?.toIntOrNull(), lastSeen = last?.getOrNull(1)?.toLongOrNull())
  }

  private fun isConnected(d: BluetoothDevice): Boolean =
    runCatching { isConnectedMethod?.invoke(d) as Boolean? }.getOrNull()
      ?: (d.address in aclConnected)

  private fun systemLevel(d: BluetoothDevice): Int? =
    runCatching { getBatteryLevelMethod?.invoke(d) as Int? }.getOrNull()?.takeIf { it in 0..100 }

  private fun forget(address: String) {
    aclConnected -= address
    broadcastLevels -= address
    gattLevels -= address
    noBatteryService -= address
  }

  @SuppressLint("MissingPermission")
  private suspend fun readGattBattery(device: BluetoothDevice): Int? =
    withContext(Dispatchers.IO) {
      withTimeoutOrNull(8.seconds) {
        suspendCancellableCoroutine { cont ->
          val done = AtomicBoolean(false)
          fun finish(gatt: BluetoothGatt, level: Int?) {
            if (!done.compareAndSet(false, true)) return
            gatt.close()
            cont.resume(level?.takeIf { it in 0..100 })
          }
          val gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
              if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
              else if (newState == BluetoothProfile.STATE_DISCONNECTED) finish(g, null)
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
              val c = g.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)
              if (c == null || !g.readCharacteristic(c)) finish(g, null)
            }

            override fun onCharacteristicRead(
              g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int,
            ) = finish(g, value.firstOrNull()?.toInt()?.and(0xFF)?.takeIf { status == BluetoothGatt.GATT_SUCCESS })

            @Deprecated("API < 33")
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
              @Suppress("DEPRECATION")
              finish(g, c.value?.firstOrNull()?.toInt()?.and(0xFF)?.takeIf { status == BluetoothGatt.GATT_SUCCESS })
            }
          }, BluetoothDevice.TRANSPORT_LE)
          if (gatt == null) cont.resume(null)
          else cont.invokeOnCancellation { if (done.compareAndSet(false, true)) gatt.close() }
        }
      }
    }

  private fun Intent.bluetoothDevice(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
      getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

  private companion object {
    // Hidden in the SDK but public in the platform and on the non-SDK allowlist.
    const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    val isConnectedMethod = runCatching { BluetoothDevice::class.java.getMethod("isConnected") }.getOrNull()
    val getBatteryLevelMethod = runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel") }.getOrNull()
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
  }
}
