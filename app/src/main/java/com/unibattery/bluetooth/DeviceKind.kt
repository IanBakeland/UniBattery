package com.unibattery.bluetooth

import androidx.annotation.DrawableRes
import com.unibattery.R

enum class DeviceKind(@DrawableRes val icon: Int) {
  Headphones(R.drawable.ic_headphones),
  Headset(R.drawable.ic_headset),
  Speaker(R.drawable.ic_speaker),
  Keyboard(R.drawable.ic_keyboard),
  Mouse(R.drawable.ic_mouse),
  Gamepad(R.drawable.ic_gamepad),
  Watch(R.drawable.ic_watch),
  Car(R.drawable.ic_car),
  Phone(R.drawable.ic_phone),
  Computer(R.drawable.ic_computer),
  Other(R.drawable.ic_bluetooth),
}

/**
 * Picks an icon from the Bluetooth Class of Device (see BluetoothClass.Device constants).
 * LE-only accessories often report no class, so fall back to a name hint (icon only, never data).
 */
fun kindOf(deviceClass: Int?, name: String): DeviceKind {
  val cls = deviceClass ?: 0
  val minor = cls and 0xFF
  val fromClass = when (cls and 0x1F00) {
    0x0400 -> when (cls and 0x1FFC) { // audio/video
      0x0404, 0x0408 -> DeviceKind.Headset // wearable headset, hands-free
      0x0418 -> DeviceKind.Headphones
      0x0414, 0x041C, 0x0428 -> DeviceKind.Speaker // loudspeaker, portable audio, hifi
      0x0420 -> DeviceKind.Car
      else -> DeviceKind.Headphones
    }
    0x0500 -> when { // peripheral
      minor and 0x40 != 0 -> DeviceKind.Keyboard
      minor and 0x80 != 0 -> DeviceKind.Mouse
      minor and 0x0C != 0 -> DeviceKind.Gamepad // joystick / gamepad
      else -> null
    }
    0x0700 -> DeviceKind.Watch // wearable
    0x0200 -> DeviceKind.Phone
    0x0100 -> DeviceKind.Computer
    else -> null
  }
  if (fromClass != null) return fromClass
  val n = name.lowercase()
  return when {
    listOf("buds", "pods", "headphone", "earbud", "wh-", "wf-").any { it in n } -> DeviceKind.Headphones
    listOf("mouse", "mx master", "mx anywhere", "trackpad").any { it in n } -> DeviceKind.Mouse
    listOf("keyboard", "keys").any { it in n } -> DeviceKind.Keyboard
    listOf("controller", "gamepad", "xbox", "dualsense").any { it in n } -> DeviceKind.Gamepad
    listOf("watch", "band").any { it in n } -> DeviceKind.Watch
    listOf("speaker", "soundbar").any { it in n } -> DeviceKind.Speaker
    else -> DeviceKind.Other
  }
}
