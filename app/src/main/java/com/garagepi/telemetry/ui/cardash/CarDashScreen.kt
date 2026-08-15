package com.garagepi.telemetry.ui.cardash

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garagepi.telemetry.obd.TelemetryFields
import com.garagepi.telemetry.obd.Units
import com.garagepi.telemetry.service.ConnectionState
import com.garagepi.telemetry.service.ObdLoggingState
import com.garagepi.telemetry.sync.AppSettings
import com.garagepi.telemetry.ui.gauge.FieldStyles
import com.garagepi.telemetry.ui.gauge.TileContent

private const val CAR_COLUMNS = 4

/**
 * Full-screen readout for a phone mounted in the car.
 *
 * Locks to landscape and holds the screen awake. The grid is a non-scrolling 4-column
 * layout that fills the viewport so every configured tile stays on one screen.
 */
@Composable
fun CarDashScreen() {
    val state by ObdLoggingState.state.collectAsState()
    val context = LocalContext.current
    val tiles = remember { AppSettings(context).dashboardTiles.filterNot { it.isEmpty } }
    val fahrenheit = remember { AppSettings(context).temperatureInFahrenheit }
    val displayValues = remember(state.latestValues, fahrenheit) {
        Units.forDisplay(state.latestValues, fahrenheit)
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            previousOrientation?.let { activity.requestedOrientation = it }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp),
    ) {
        if (state.connectionState !is ConnectionState.Connected) {
            Text(
                text = when (val c = state.connectionState) {
                    is ConnectionState.Error -> "Connection failed\n${c.message}"
                    ConnectionState.Connecting -> "Connecting…"
                    else -> "Not logging.\nStart from the Live tab."
                },
                color = Color.White,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        if (tiles.isEmpty()) {
            Text(
                text = "No tiles configured.\nSet them on the Live tab.",
                color = Color.White,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        val rows = tiles.chunked(CAR_COLUMNS)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { rowTiles ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowTiles.forEach { tile ->
                        val field = TelemetryFields.byAnyPid(tile.pid)
                            ?.let { Units.forDisplay(it, fahrenheit) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF1A1A1A))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (field != null) {
                                TileContent(
                                    field = field,
                                    style = FieldStyles.resolve(field, tile.style),
                                    values = displayValues,
                                    compact = true,
                                    textColor = Color.White,
                                    trackColor = Color(0xFF303030),
                                    accentColor = Color(0xFF64B5F6),
                                )
                            }
                        }
                    }
                    repeat(CAR_COLUMNS - rowTiles.size) {
                        Spacer(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}
