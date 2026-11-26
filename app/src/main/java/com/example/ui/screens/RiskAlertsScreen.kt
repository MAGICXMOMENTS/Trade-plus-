package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.R
import com.example.data.model.AlertSeverity
import com.example.data.model.RiskTolerance
import com.example.data.model.UiState
import com.example.notifications.RiskAlertNotifier
import com.example.ui.auth.signOut
import com.example.ui.components.AddRiskAlertDialog
import com.example.ui.components.ErrorCard
import com.example.ui.components.LoadingCard
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearCoral
import com.example.ui.theme.BearCoralDim
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.MonoNumericSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.RiskAmberDim
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradePulseViewModel
import com.example.worker.AlertMonitorScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RiskAlertsScreen(
    viewModel: TradePulseViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val triggeredAlerts by viewModel.triggeredRiskAlerts.collectAsStateWithLifecycle()
    val rulesUi by viewModel.riskAlertsState.collectAsStateWithLifecycle()
    val riskProfile by viewModel.riskProfileState.collectAsStateWithLifecycle()

    var showAddAlert by remember { mutableStateOf(false) }
    var monitorStatus by remember { mutableStateOf(RiskAlertNotifier.getMonitorStatus(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(RiskAlertNotifier.hasNotificationPermission(context))
    }

    val immediateWorkInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(AlertMonitorScheduler.IMMEDIATE_WORK_NAME)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val isCheckingNow = immediateWorkInfos.any {
        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
    }

    DisposableEffect(context) {
        val unregister = RiskAlertNotifier.registerStatusListener(context) { updated ->
            monitorStatus = updated
        }
        onDispose { unregister() }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || RiskAlertNotifier.hasNotificationPermission(context)
        if (hasNotificationPermission) {
            AlertMonitorScheduler.setBackgroundMonitoringEnabled(context, true)
            AlertMonitorScheduler.runImmediateCheck(context, forceNotify = true)
            monitorStatus = RiskAlertNotifier.getMonitorStatus(context)
        }
    }

    var tolerance by remember { mutableStateOf(riskProfile.toleranceEnum()) }
    var capitalText by remember { mutableStateOf(String.format(Locale.US, "%.0f", riskProfile.accountCapital)) }
    var riskPctText by remember { mutableStateOf(String.format(Locale.US, "%.2f", riskProfile.defaultRiskPerTradePct)) }
    var maxDdText by remember { mutableStateOf(String.format(Locale.US, "%.1f", riskProfile.maxPortfolioDrawdownPct)) }

    LaunchedEffect(riskProfile) {
        tolerance = riskProfile.toleranceEnum()
        capitalText = String.format(Locale.US, "%.0f", riskProfile.accountCapital)
        riskPctText = String.format(Locale.US, "%.2f", riskProfile.defaultRiskPerTradePct)
        maxDdText = String.format(Locale.US, "%.1f", riskProfile.maxPortfolioDrawdownPct)
    }

    val currentUser = Firebase.auth.currentUser

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PERSONALIZED RISK GUARDRAILS",
                            style = MaterialTheme.typography.labelMedium,
                            color = RiskAmber
                        )
                        Text(
                            text = "Risk & Alerts",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Button(
                        onClick = { showAddAlert = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RiskAmber, contentColor = ObsidianBg),
                        modifier = Modifier.testTag("add_risk_alert_button")
                    ) {
                        Icon(Icons.Filled.AddAlert, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("New Alert", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Background Worker & Local Notification Monitor Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    border = BorderStroke(1.dp, if (monitorStatus.enabled) AiCyan.copy(alpha = 0.45f) else TerminalBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Background Price Monitor (15m)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (monitorStatus.enabled) {
                                        "WorkManager checks live prices against your thresholds & fires local notifications."
                                    } else {
                                        "Background threshold checking is paused."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = monitorStatus.enabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        AlertMonitorScheduler.setBackgroundMonitoringEnabled(context, enabled)
                                        monitorStatus = RiskAlertNotifier.getMonitorStatus(context)
                                    }
                                },
                                modifier = Modifier.testTag("bg_monitor_switch")
                            )
                        }

                        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RiskAmberDim.copy(alpha = 0.45f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = RiskAmber, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Enable notifications so background alerts appear in your status bar.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.size(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    border = BorderStroke(1.dp, RiskAmber),
                                    modifier = Modifier.testTag("enable_notifications_button")
                                ) {
                                    Text("Allow", color = RiskAmber)
                                }
                            }
                        }

                        val lastTimeText = remember(monitorStatus.lastCheckTimestampMs) {
                            if (monitorStatus.lastCheckTimestampMs > 0L) {
                                SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(monitorStatus.lastCheckTimestampMs))
                            } else {
                                "Not run yet"
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TerminalSurfaceElevated)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("LAST SCAN: $lastTimeText", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(monitorStatus.lastCheckSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        AlertMonitorScheduler.runImmediateCheck(context, forceNotify = true)
                                        viewModel.refreshMarketData()
                                    }
                                },
                                enabled = !isCheckingNow,
                                border = BorderStroke(1.dp, AiCyan),
                                modifier = Modifier.testTag("check_prices_now_button")
                            ) {
                                if (isCheckingNow) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = AiCyan,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Checking...", color = AiCyan)
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = AiCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Check Now", color = AiCyan)
                                }
                            }
                        }
                    }
                }
            }

            // Triggered Live Alerts Feed
            item {
                Text(
                    text = "Live Triggered Signals (${triggeredAlerts.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (triggeredAlerts.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                        border = BorderStroke(1.dp, TerminalBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = BullEmerald)
                            Text(
                                text = "All monitored positions and alert thresholds are currently within your risk bounds.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(triggeredAlerts, key = { it.id }) { alert ->
                    val color = when (alert.severity) {
                        AlertSeverity.CRITICAL -> BearCoral
                        AlertSeverity.WARNING -> RiskAmber
                        AlertSeverity.INFO -> AiCyan
                    }
                    val bg = when (alert.severity) {
                        AlertSeverity.CRITICAL -> BearCoralDim.copy(alpha = 0.55f)
                        AlertSeverity.WARNING -> RiskAmberDim.copy(alpha = 0.55f)
                        AlertSeverity.INFO -> AiCyanDim.copy(alpha = 0.55f)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = bg),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = color)
                            Column {
                                Text(alert.title, style = MaterialTheme.typography.titleSmall, color = color)
                                Text(alert.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // Configured Alert Rules
            item {
                Text(
                    text = "Configured Alert Rules",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            when (val rState = rulesUi) {
                is UiState.Loading -> item { LoadingCard("Loading risk alert rules...") }
                is UiState.Error -> item { ErrorCard(rState.message) }
                is UiState.Success -> {
                    if (rState.data.isEmpty()) {
                        item {
                            Text(
                                text = "No custom alert rules yet. Tap \"New Alert\" to monitor price stops, RSI overbought/oversold, or drawdown limits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    } else {
                        items(rState.data, key = { it.id }) { rule ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                                border = BorderStroke(1.dp, TerminalBorder),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${rule.symbol} • ${rule.alertTypeEnum().label}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Threshold: ${String.format(Locale.US, "%.2f", rule.thresholdValue)} ${rule.alertTypeEnum().unitHint}",
                                            style = MonoNumericSmall,
                                            color = RiskAmber
                                        )
                                        if (rule.notes.isNotBlank()) {
                                            Text(rule.notes, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = rule.active,
                                            onCheckedChange = { checked ->
                                                viewModel.toggleRiskAlert(rule.id, checked)
                                            }
                                        )
                                        IconButton(onClick = { viewModel.deleteRiskAlert(rule.id) }) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete alert", tint = TextMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }

            // Risk Profile Editor
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    border = BorderStroke(1.dp, TerminalBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Portfolio Risk Profile", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RiskTolerance.entries.forEach { t ->
                                FilterChip(
                                    selected = tolerance == t,
                                    onClick = {
                                        tolerance = t
                                        riskPctText = String.format(Locale.US, "%.2f", t.defaultRiskPct)
                                        maxDdText = String.format(Locale.US, "%.1f", t.maxDrawdownPct)
                                    },
                                    label = { Text(t.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AiCyanDim,
                                        selectedLabelColor = AiCyan
                                    )
                                )
                            }
                        }
                        OutlinedTextField(
                            value = capitalText,
                            onValueChange = { capitalText = it },
                            label = { Text("Account Capital ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = riskPctText,
                                onValueChange = { riskPctText = it },
                                label = { Text("Risk / Trade %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxDdText,
                                onValueChange = { maxDdText = it },
                                label = { Text("Max Drawdown %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = {
                                val cap = capitalText.toDoubleOrNull()?.coerceIn(100.0, 1_000_000_000.0) ?: 25000.0
                                val rpt = riskPctText.toDoubleOrNull()?.coerceIn(0.1, 25.0) ?: 1.5
                                val mdd = maxDdText.toDoubleOrNull()?.coerceIn(1.0, 80.0) ?: 10.0
                                viewModel.saveRiskProfile(
                                    tolerance = tolerance,
                                    accountCapital = cap,
                                    defaultRiskPerTradePct = rpt,
                                    maxPortfolioDrawdownPct = mdd,
                                    preferredTimeframe = riskProfile.preferredTimeframe
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AiCyan, contentColor = ObsidianBg),
                            modifier = Modifier.fillMaxWidth().testTag("save_risk_profile_button")
                        ) {
                            Text("Save Risk Profile", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Account & Disclaimer Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    border = BorderStroke(1.dp, TerminalBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Account: ${currentUser?.email ?: currentUser?.displayName ?: "Signed In"}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.disclaimer_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        OutlinedButton(
                            onClick = {
                                signOut(credentialManager = credentialManager, scope = scope)
                            },
                            border = BorderStroke(1.dp, BearCoral),
                            modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = BearCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Sign Out", color = BearCoral)
                        }
                    }
                }
            }
        }
    }

    if (showAddAlert) {
        AddRiskAlertDialog(
            onDismiss = { showAddAlert = false },
            onSave = { sym, type, threshold, notes ->
                viewModel.addRiskAlert(sym, type, threshold, notes)
                AlertMonitorScheduler.runImmediateCheck(context, forceNotify = false)
                showAddAlert = false
            }
        )
    }
}
