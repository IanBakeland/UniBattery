package com.unibattery.bluetooth

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.unibattery.R

enum class DeviceKind(@DrawableRes val icon: Int, @StringRes val label: Int) {
  Headphones(R.drawable.ic_headphones, R.string.kind_headphones),
  Headset(R.drawable.ic_headset, R.string.kind_headset),
  Speaker(R.drawable.ic_speaker, R.string.kind_speaker),
  Keyboard(R.drawable.ic_keyboard, R.string.kind_keyboard),
  Mouse(R.drawable.ic_mouse, R.string.kind_mouse),
  Pen(R.drawable.ic_pen, R.string.kind_pen),
  Gamepad(R.drawable.ic_gamepad, R.string.kind_gamepad),
  Watch(R.drawable.ic_watch, R.string.kind_watch),
  Car(R.drawable.ic_car, R.string.kind_car),
  Phone(R.drawable.ic_phone, R.string.kind_phone),
  Computer(R.drawable.ic_computer, R.string.kind_computer),
  Other(R.drawable.ic_bluetooth, R.string.kind_other),
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
    0x0500 -> when { // peripheral: bits 6-7 are flags, bits 2-5 a sub-type *value* (not more flags)
      minor and 0x40 != 0 -> DeviceKind.Keyboard
      minor and 0x80 != 0 -> DeviceKind.Mouse
      else -> when (minor and 0x3C) {
        0x04, 0x08 -> DeviceKind.Gamepad // joystick, gamepad
        0x14, 0x1C -> DeviceKind.Pen // digitizer tablet, digital pen
        else -> null
      }
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
    listOf("pencil", "stylus", "s pen", " pen").any { it in " $n" } -> DeviceKind.Pen
    listOf("controller", "gamepad", "xbox", "dualsense").any { it in n } -> DeviceKind.Gamepad
    listOf("watch", "band").any { it in n } -> DeviceKind.Watch
    listOf("speaker", "soundbar").any { it in n } -> DeviceKind.Speaker
    else -> DeviceKind.Other
  }
}
