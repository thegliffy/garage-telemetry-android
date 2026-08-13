package com.garagepi.telemetry.ui.cardash

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garagepi.telemetry.obd.TelemetryFields
import com.garagepi.telemetry.service.ConnectionState
import com.garagepi.telemetry.service.ObdLoggingState
import com.garagepi.telemetry.sync.AppSettings
import com.garagepi.telemetry.ui.gauge.FieldStyles
import com.garagepi.telemetry.ui.gauge.TileContent

/**
 * Full-screen readout for a phone mounted in the car.
 *
 * Unlike the Android Auto surface this has no template restrictions, so it updates at the
 * full sample rate and can be as large and high-contrast as it likes. Locks to landscape
 * and holds the screen awake for as long as it is shown.
 */
@Composable
fun CarDashScreen() {
    val state by ObdLoggingState.state.collectAsState()
    val context = LocalContext.current
    // Same layout the user configured on the phone dashboard, so car mode is not a second
    // thing to set up.
    val tiles = remember { AppSettings(context).dashboardTiles.filterNot { it.isEmpty } }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            // Both must be undone, or the rest of the app inherits a landscape lock and a
            // screen that never sleeps.
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            previousOrientation?.let { activity.requestedOrientation = it }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Deliberately black rather than the theme surface: less glare at night and
            // far better contrast in direct sun.
            .background(Color.Black)
            .padding(12.dp),
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(tiles) { tile ->
                val field = TelemetryFields.bySelectablePid(tile.pid)
                if (field != null) {
                    // Same gauges as the phone dashboard, but with colours pinned for the
                    // black background — the theme's onSurface would be near-invisible.
                    TileContent(
                        field = field,
                        style = FieldStyles.resolve(field, tile.style),
                        values = state.latestValues,
                        compact = false,
                        textColor = Color.White,
                        trackColor = Color(0xFF303030),
                        accentColor = Color(0xFF64B5F6),
                    )
                }
            }
        }
    }
}

// BigTile removed: car mode now renders through the shared TileContent, so gauges and the
// number style stay consistent between here and the phone dashboard.
