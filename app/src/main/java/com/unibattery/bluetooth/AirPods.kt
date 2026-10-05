package com.unibattery.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.ParcelUuid
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * AirPods and Beats don't report battery to Android. They do push exact levels over Apple's own accessory
 * protocol (AAP), an L2CAP channel on PSM 0x1001, so we keep that open while they're connected.
 * Protocol from https://github.com/tyalie/AAP-Protocol-Defintion. Android only allows the channel without
 * root after 16 QPR3; on older versions connecting fails and the device just shows no level, as before.
 */
class AirPodsBattery(private val scope: CoroutineScope, private val onChange: () -> Unit) {
  private val sockets = ConcurrentHashMap<String, BluetoothSocket>()
  private val failed = ConcurrentHashMap.newKeySet<String>() // not retried until reconnect or manual refresh
  private val levels = ConcurrentHashMap<String, Int>()

  fun level(address: String): Int? = levels[address]

  /** Opens the channel to [device] if it isn't open (or hasn't failed) yet. Levels arrive later via [onChange]. */
  @SuppressLint("MissingPermission")
  fun connect(adapter: BluetoothAdapter, device: BluetoothDevice) {
    val address = device.address
    if (sockets.containsKey(address) || address in failed) return
    val socket = runCatching { createL2capSocket(adapter, device) }.getOrNull()
      ?: return run { failed += address }
    sockets[address] = socket
    scope.launch(Dispatchers.IO) {
      runCatching {
        socket.connect()
        socket.outputStream.apply { write(HANDSHAKE); write(FEATURES); write(NOTIFICATIONS) }
        val buffer = ByteArray(1024)
        while (true) {
          val n = socket.inputStream.read(buffer) // L2CAP: one packet per read
          if (n < 0) break
          aapBatteryLevel(buffer.copyOf(n))?.let { levels[address] = it; onChange() }
        }
      }.onFailure { if (sockets[address] === socket && levels[address] == null) failed += address }
      // Only clean up if this is still the current channel; reset() may already have replaced or closed it.
      if (sockets.remove(address, socket)) { runCatching { socket.close() }; levels -= address }
    }
  }

  fun close(address: String) {
    sockets.remove(address)?.let { runCatching { it.close() } }
    levels -= address
  }

  /** Manual refresh: try devices that failed again, keeping channels that work. */
  fun retryFailed() = failed.clear()

  /** On disconnect or Bluetooth off: close, and allow connecting again later. */
  fun reset(address: String? = null) {
    if (address == null) { failed.clear(); sockets.keys.forEach(::close) } else { failed -= address; close(address) }
  }

  private companion object {
    const val PSM = 0x1001
    const val TYPE_L2CAP = 3
    val SERVICE: ParcelUuid = ParcelUuid.fromString(AAP_SERVICE)

    val HANDSHAKE = bytes(0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    val FEATURES = bytes(0x04, 0x00, 0x04, 0x00, 0x4D, 0x00, 0xD7, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    val NOTIFICATIONS = bytes(0x04, 0x00, 0x04, 0x00, 0x0F, 0x00, 0xFF, 0xFF, 0xFF, 0xFF)

    fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    /**
     * Classic (BR/EDR) L2CAP sockets on a fixed PSM aren't in the SDK (the public socket settings only allow
     * RFCOMM and LE), and Android blocks reflection on the constructor, so exempt it first, as LibrePods does.
     * The constructor gained an adapter parameter in 16 QPR3.
     */
    fun createL2capSocket(adapter: BluetoothAdapter, device: BluetoothDevice): BluetoothSocket {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")
      }
      val signatures = listOf(
        arrayOf(adapter, device, TYPE_L2CAP, true, true, PSM, SERVICE),
        arrayOf(device, TYPE_L2CAP, true, true, PSM, SERVICE),
      )
      var error: Exception? = null
      for (args in signatures) {
        try {
          val types = args.map { it::class.javaPrimitiveType ?: it::class.java }.toTypedArray()
          val constructor = BluetoothSocket::class.java.getDeclaredConstructor(*types).apply { isAccessible = true }
          return constructor.newInstance(*args)
        } catch (e: Exception) {
          error = e
        }
      }
      throw error!!
    }
  }
}

/** Apple's accessory protocol service. Paired AirPods and Beats list it, which is how we recognise them. */
private const val AAP_SERVICE = "74ec2172-0bad-4d01-8f77-997b2be0722a"

@SuppressLint("MissingPermission")
fun BluetoothDevice.isAirPods(): Boolean = uuids?.any { it.toString() == AAP_SERVICE } == true

/**
 * Battery from an AAP battery notification: header 04 00 04 00, opcode 04 00, a count, then 5 bytes per
 * component: type (1 single headset, 2 right, 4 left, 8 case), 0x01, level, status (4 = not connected), 0x01.
 * Returns the lowest earbud (the case isn't what you're wearing), or null when this is another packet.
 */
fun aapBatteryLevel(packet: ByteArray): Int? {
  if (packet.size < 7 || packet[0] != 0x04.toByte() || packet[4] != 0x04.toByte() || packet[5] != 0x00.toByte()) return null
  val count = packet[6].toInt() and 0xFF
  return (0 until count).mapNotNull { i ->
    val at = 7 + i * 5
    if (at + 3 >= packet.size) return@mapNotNull null
    val type = packet[at].toInt() and 0xFF
    val level = packet[at + 2].toInt() and 0xFF
    val status = packet[at + 3].toInt() and 0xFF
    level.takeIf { type in setOf(1, 2, 4) && status != 4 && level in 0..100 }
  }.minOrNull()
}
