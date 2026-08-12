package com.garagepi.telemetry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.garagepi.telemetry.ui.GarageNavHost
import com.garagepi.telemetry.ui.theme.GarageTelemetryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GarageTelemetryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GarageNavHost()
                }
            }
        }
    }
}
