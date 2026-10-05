package com.unibattery.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AirPodsTest {
  // Apple manufacturer data: type, length, prefix, model (2), status, buds, case/flags, ...
  private fun message(model: Int, buds: Int) =
    byteArrayOf(0x07, 0x19, 0x01, (model shr 8).toByte(), model.toByte(), 0x2B, buds.toByte(), 0x05, 0x00)

  @Test fun lowestBud() {
    assertEquals(70, airPodsLevel(message(0x1420, 0x97))) // AirPods Pro 2: 90% and 70%
    assertEquals(80, airPodsLevel(message(0x2420, 0x8F))) // one bud unknown
    assertEquals(100, airPodsLevel(message(0x1420, 0xBB))) // >10 means full
    assertNull(airPodsLevel(message(0x1420, 0xFF)))
  }

  @Test fun singleBatteryUsesLowNibble() {
    assertEquals(60, airPodsLevel(message(0x0A20, 0x06))) // AirPods Max; high nibble isn't a bud
  }

  @Test fun rejectsOtherMessages() {
    assertNull(airPodsLevel(null))
    assertNull(airPodsLevel(byteArrayOf(0x10, 0x05, 0x01, 0x14, 0x20, 0x2B, 0x55))) // nearby-info, not pairing
    assertNull(airPodsLevel(byteArrayOf(0x07, 0x19, 0x07, 0x14, 0x20, 0x2B, 0x55))) // non-status 0x07 frame
    assertNull(airPodsLevel(byteArrayOf(0x07, 0x19, 0x01)))
  }
}
