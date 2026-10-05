package com.unibattery.bluetooth

const val APPLE_COMPANY_ID = 0x004C

/** Over-ear and neckband models that report one battery (low nibble) instead of a left and right bud. */
private val singleBattery = setOf(0x0A20, 0x1F20, 0x2D20, 0x0320, 0x0520, 0x0620, 0x0920, 0x0C20, 0x0D20, 0x1020, 0x1720, 0x2520, 0x3820)

/**
 * Battery from the "proximity pairing" message AirPods keep broadcasting, given Apple's manufacturer data:
 * [0] 0x07 type, [1] length, [2] 0x01, [3..4] model, [5] status, [6] left/right bud as tens of percent in
 * the two nibbles (15 = unknown, e.g. not in range). Returns the lowest bud, since that's the one that runs
 * out first, or null when this isn't such a message or nothing is known.
 */
fun airPodsLevel(data: ByteArray?): Int? {
  if (data == null || data.size < 7 || data[0] != 0x07.toByte() || data[2] != 0x01.toByte()) return null
  val model = (data[3].toInt() and 0xFF shl 8) or (data[4].toInt() and 0xFF)
  val buds = data[6].toInt() and 0xFF
  val nibbles = if (model in singleBattery) listOf(buds and 0x0F) else listOf(buds shr 4, buds and 0x0F)
  return nibbles.filter { it != 15 }.minOrNull()?.let { minOf(it, 10) * 10 }
}
