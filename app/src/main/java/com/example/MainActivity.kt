package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.firebase.configureAppCheck
import com.example.notifications.RiskAlertNotifier
import com.example.ui.TradePulseApp
import com.example.ui.theme.TradePulseTheme

class MainActivity : ComponentActivity() {

    private var launchSymbol by mutableStateOf<String?>(null)
    private var launchOpenAlerts by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory: Configure App Check with launch intent before setContent or ViewModels
        configureAppCheck(this, intent)
        extractNotificationTarget(intent)

        enableEdgeToEdge()
        setContent {
            TradePulseTheme {
                TradePulseApp(
                    launchSymbol = launchSymbol,
                    launchOpenAlerts = launchOpenAlerts,
                    onConsumeLaunchTarget = {
                        launchSymbol = null
                        launchOpenAlerts = false
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractNotificationTarget(intent)
    }

    private fun extractNotificationTarget(intent: Intent?) {
        if (intent == null) return
        val sym = intent.getStringExtra(RiskAlertNotifier.EXTRA_ALERT_SYMBOL)
        val openAlerts = intent.getBooleanExtra(RiskAlertNotifier.EXTRA_OPEN_ALERTS, false)
        if (!sym.isNullOrBlank()) {
            launchSymbol = sym
        }
        if (openAlerts) {
            launchOpenAlerts = true
        }
    }
}
