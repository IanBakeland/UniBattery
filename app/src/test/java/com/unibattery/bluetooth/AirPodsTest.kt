package com.unibattery.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AirPodsTest {
  private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

  @Test fun batteryNotification() {
    // From the protocol docs: left 100% charging, right 100% charging, case 12%.
    assertEquals(100, aapBatteryLevel(hex("040004000400030401640101020164010108010c0201")))
    // Left 45%, right 80%, case 30%: lowest earbud, never the case.
    assertEquals(45, aapBatteryLevel(hex("040004000400030401" + "2d0201" + "020150" + "0201" + "08011e0201")))
    // Right bud out of range (status 4) is ignored.
    assertEquals(70, aapBatteryLevel(hex("04000400040002040146020102010a0401")))
    // Single headset (AirPods Max).
    assertEquals(55, aapBatteryLevel(hex("0400040004000101013702 01".replace(" ", ""))))
  }

  @Test fun otherPackets() {
    assertNull(aapBatteryLevel(hex("010004000000010002000500494e0500a54f"))) // connect response
    assertNull(aapBatteryLevel(hex("040004000600020101"))) // in-ear state
    assertNull(aapBatteryLevel(hex("0400040004000108010c0201"))) // only the case
    assertNull(aapBatteryLevel(hex("0400")))
  }
}
