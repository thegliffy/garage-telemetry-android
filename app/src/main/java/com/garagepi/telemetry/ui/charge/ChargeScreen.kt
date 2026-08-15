package com.garagepi.telemetry.ui.charge

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagepi.telemetry.obd.Units
import com.garagepi.telemetry.service.ObdLoggingState
import com.garagepi.telemetry.sync.AppSettings
import com.garagepi.telemetry.ui.history.LineChart
import com.garagepi.telemetry.ui.theme.BatteryTempLowBlue
import com.garagepi.telemetry.ui.theme.ChargeGreen
import com.garagepi.telemetry.ui.theme.DischargeRed

/**
 * Full-screen DC fast-charge view. Opened from the Live tab Charging button, the same
 * way Car mode is. Charts cover this logging session at the faster charge poll rate;
 * hide the bottom nav while shown.
 */
@Composable
fun ChargeScreen(onDismiss: () -> Unit) {
    val state by ObdLoggingState.state.collectAsState()
    val context = LocalContext.current
    val fahrenheit = remember { AppSettings(context).temperatureInFahrenheit }
    val samples = state.chargeSamples
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val soc = samples.mapNotNull { s -> s.soc?.let { s.ts to it } }
    val power = samples.mapNotNull { s -> s.chargeKw?.let { s.ts to it } }
    val volts = samples.mapNotNull { s -> s.packVoltage?.let { s.ts to it } }
    val tMax = samples.mapNotNull { s ->
        s.battTempMaxC?.let { c -> s.ts to if (fahrenheit) Units.celsiusToFahrenheit(c) else c }
    }
    val tMin = samples.mapNotNull { s ->
        s.battTempMinC?.let { c -> s.ts to if (fahrenheit) Units.celsiusToFahrenheit(c) else c }
    }
    val tempUnit = if (fahrenheit) "°F" else "°C"
    val latest = samples.lastOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (landscape) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DC fast charge", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }

        if (landscape) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChargeChart("State of charge", soc, "%", ChargeGreen)
                    ChargeChart("Charging power", power, "kW", ChargeGreen)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChargeChart("Pack voltage", volts, "V", MaterialTheme.colorScheme.secondary)
                    TempChart(tMax, tMin, tempUnit)
                }
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlagBox("DC charging", latest?.dcCharging == true)
                    FlagBox("Battery heater", latest?.heaterOn == true)
                }
            }
        } else {
            ChargeChart("State of charge", soc, "%", ChargeGreen)
            ChargeChart("Charging power", power, "kW", ChargeGreen)
            ChargeChart("Pack voltage", volts, "V", MaterialTheme.colorScheme.secondary)
            TempChart(tMax, tMin, tempUnit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlagBox("DC charging", latest?.dcCharging == true, Modifier.weight(1f))
                FlagBox("Battery heater", latest?.heaterOn == true, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChargeChart(
    title: String,
    points: List<Pair<Long, Double>>,
    unit: String,
    color: Color,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (points.size < 2) {
            Text("Waiting for samples…", style = MaterialTheme.typography.bodyLarge)
        } else {
            LineChart(
                points = points,
                unit = unit,
                lineColor = color,
                modifier = Modifier.height(150.dp),
            )
        }
    }
}

@Composable
private fun TempChart(
    tMax: List<Pair<Long, Double>>,
    tMin: List<Pair<Long, Double>>,
    unit: String,
) {
    Column {
        Text("Battery temps  ·  red max  ·  blue min", style = MaterialTheme.typography.titleMedium)
        if (tMax.size < 2 && tMin.size < 2) {
            Text("Waiting for samples…", style = MaterialTheme.typography.bodyLarge)
        } else {
            LineChart(
                points = tMax,
                extraPoints = tMin,
                unit = unit,
                lineColor = DischargeRed,
                extraLineColor = BatteryTempLowBlue,
                modifier = Modifier.height(150.dp),
            )
        }
    }
}

@Composable
private fun FlagBox(label: String, on: Boolean, modifier: Modifier = Modifier) {
    val container = if (on) ChargeGreen.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (on) "On" else "Off",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (on) ChargeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
