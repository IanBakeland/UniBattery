package com.unibattery.bluetooth

import com.unibattery.R
import com.unibattery.notify.notificationLine
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceKindTest {
  @Test fun classOfDevice() {
    assertEquals(DeviceKind.Headphones, kindOf(0x0418, "x"))
    assertEquals(DeviceKind.Headset, kindOf(0x0404, "x"))
    assertEquals(DeviceKind.Car, kindOf(0x0420, "x"))
    assertEquals(DeviceKind.Keyboard, kindOf(0x0540, "x"))
    assertEquals(DeviceKind.Keyboard, kindOf(0x05C0, "x")) // combo keyboard/pointer
    assertEquals(DeviceKind.Mouse, kindOf(0x0580, "x"))
    assertEquals(DeviceKind.Gamepad, kindOf(0x0508, "x"))
    assertEquals(DeviceKind.Gamepad, kindOf(0x0504, "x")) // joystick
    // Sub-type is a value, not flags: pens and tablets used to come out as gamepads.
    assertEquals(DeviceKind.Pen, kindOf(0x051C, "x")) // digital pen
    assertEquals(DeviceKind.Pen, kindOf(0x0514, "x")) // digitizer tablet
    assertEquals(DeviceKind.Other, kindOf(0x050C, "x")) // remote control
    assertEquals(DeviceKind.Watch, kindOf(0x0704, "x"))
  }

  @Test fun nameFallbackOnlyWithoutClass() {
    assertEquals(DeviceKind.Mouse, kindOf(null, "MX Master 3S"))
    assertEquals(DeviceKind.Earbuds, kindOf(0, "AirPods Pro"))
    assertEquals(DeviceKind.Other, kindOf(null, "Thing"))
    assertEquals(DeviceKind.Pen, kindOf(null, "Apple Pencil"))
    assertEquals(DeviceKind.Pen, kindOf(null, "Galaxy S Pen Pro"))
    assertEquals(DeviceKind.Other, kindOf(null, "Open Run")) // "pen" inside a word isn't a pen
    assertEquals(DeviceKind.Keyboard, kindOf(0x0540, "Mouse-named keyboard"))
  }

  @Test fun earbudsByName() {
    assertEquals(DeviceKind.Earbuds, kindOf(0x0404, "Ian's AirPods Pro")) // headset class, earbuds name
    assertEquals(DeviceKind.Earbuds, kindOf(0x0418, "Galaxy Buds3 Pro"))
    assertEquals(DeviceKind.Earbuds, kindOf(0x0404, "WF-1000XM5"))
    assertEquals(DeviceKind.Headphones, kindOf(0x0418, "AirPods Max"))
    assertEquals(DeviceKind.Headset, kindOf(0x0404, "Jabra Talk"))
    assertEquals(DeviceKind.Keyboard, kindOf(0x0540, "Buds keyboard")) // name only counts for audio devices
  }

  @Test fun notificationLines() {
    val strings = mapOf(R.string.notification_line to "%1\$s — %2\$s", R.string.percent to "%d%%", R.string.battery_unavailable to "Battery unavailable")
    val d = BtDevice("a", "AirPods", DeviceKind.Headphones, connected = true, battery = 78)
    assertEquals("AirPods — 78%", notificationLine(d, strings::getValue))
    assertEquals("AirPods — Battery unavailable", notificationLine(d.copy(battery = null), strings::getValue))
  }
}
