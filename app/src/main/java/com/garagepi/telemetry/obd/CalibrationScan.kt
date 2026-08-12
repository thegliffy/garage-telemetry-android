package com.garagepi.telemetry.obd

import kotlin.math.abs

/**
 * A guess at where a value lives inside a UDS payload: which bytes, what order, what scale.
 *
 * Byte offsets for the Ioniq 5 odometer and speed aren't published, and guessing one from
 * memory is what produced the wrong 220105 display-SOC decode. Instead the app derives
 * them: you tell it what the dash reads, it finds every field that could produce that
 * number, and further samples eliminate the coincidences.
 */
data class CandidateSpec(
    val pid: String,
    val offset: Int,
    val width: Int,
    val littleEndian: Boolean,
    val divisor: Double,
) {
    fun extract(data: ByteArray): Double? {
        if (offset < 0 || offset + width > data.size) return null
        var raw = 0L
        if (littleEndian) {
            for (i in width - 1 downTo 0) raw = (raw shl 8) or (data[offset + i].toLong() and 0xFF)
        } else {
            for (i in 0 until width) raw = (raw shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return raw / divisor
    }

    fun describe(): String {
        val order = if (width == 1) "" else if (littleEndian) " LE" else " BE"
        val scale = if (divisor == 1.0) "" else " ÷${divisor.toInt()}"
        return "byte ${offset}${if (width > 1) "–${offset + width - 1}" else ""}$order$scale"
    }

    fun serialize(): String = listOf(pid, offset, width, littleEndian, divisor).joinToString("|")

    companion object {
        fun parse(s: String?): CandidateSpec? {
            val parts = s?.split("|") ?: return null
            if (parts.size != 5) return null
            return runCatching {
                CandidateSpec(
                    pid = parts[0],
                    offset = parts[1].toInt(),
                    width = parts[2].toInt(),
                    littleEndian = parts[3].toBoolean(),
                    divisor = parts[4].toDouble(),
                )
            }.getOrNull()
        }
    }
}

object CalibrationScan {

    /** Odometers are multi-byte counters; speed fits in one or two. */
    val ODOMETER_WIDTHS = listOf(2, 3, 4)
    val SPEED_WIDTHS = listOf(1, 2)
    val DIVISORS = listOf(1.0, 2.0, 10.0, 100.0)

    /** Every field in [data] that could produce [target], within [tolerance]. */
    fun scan(
        pid: String,
        data: ByteArray,
        target: Double,
        tolerance: Double,
        widths: List<Int>,
    ): List<CandidateSpec> {
        val out = mutableListOf<CandidateSpec>()
        for (width in widths) {
            for (offset in 0..(data.size - width)) {
                for (littleEndian in listOf(false, true)) {
                    // Byte order is meaningless for a single byte; skip the duplicate.
                    if (width == 1 && littleEndian) continue
                    for (divisor in DIVISORS) {
                        val spec = CandidateSpec(pid, offset, width, littleEndian, divisor)
                        val value = spec.extract(data) ?: continue
                        if (abs(value - target) <= tolerance) out.add(spec)
                    }
                }
            }
        }
        return out
    }

    /**
     * Keep only candidates that also explain a later sample.
     *
     * This is what makes the result trustworthy: a single reading always throws up
     * coincidences, but a field that tracks two genuinely different values — an odometer
     * after some miles, a speed at a different cruise — is almost certainly the real one.
     */
    fun narrow(
        candidates: List<CandidateSpec>,
        data: ByteArray,
        target: Double,
        tolerance: Double,
    ): List<CandidateSpec> = candidates.filter { spec ->
        val value = spec.extract(data)
        value != null && abs(value - target) <= tolerance
    }
}
