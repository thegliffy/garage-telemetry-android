package com.garagepi.telemetry.ui.history

/**
 * Keeps chart series bounded so a multi-hour drive does not allocate tens of thousands
 * of Compose points. Evenly samples by index (first and last always kept when possible).
 */
object ChartSeries {
    const val MAX_POINTS = 400

    fun <T> downsample(points: List<T>, maxPoints: Int = MAX_POINTS): List<T> {
        if (points.size <= maxPoints || maxPoints < 2) return points
        val last = points.size - 1
        val out = ArrayList<T>(maxPoints)
        for (i in 0 until maxPoints) {
            val index = ((i.toLong() * last) / (maxPoints - 1)).toInt()
            out.add(points[index])
        }
        return out
    }
}
