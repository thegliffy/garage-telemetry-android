package com.garagepi.telemetry.sync

import com.garagepi.telemetry.ui.gauge.TileStyle

/**
 * One dashboard slot: which field, and how to draw it.
 *
 * A null [style] means "whatever the field's default is", which is also how a layout saved
 * before styles existed is read back.
 */
data class TileConfig(val pid: String, val style: TileStyle? = null) {

    val isEmpty: Boolean get() = pid.isBlank()

    fun serialize(): String = if (style == null) pid else "$pid$STYLE_SEPARATOR${style.name}"

    companion object {
        private const val STYLE_SEPARATOR = ':'

        fun parse(raw: String): TileConfig {
            if (raw.isBlank()) return TileConfig("")
            val separator = raw.indexOf(STYLE_SEPARATOR)
            if (separator < 0) return TileConfig(raw) // legacy: bare pid, default style
            return TileConfig(
                pid = raw.substring(0, separator),
                style = TileStyle.fromName(raw.substring(separator + 1)),
            )
        }
    }
}
