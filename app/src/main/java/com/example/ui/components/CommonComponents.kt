package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AlertType
import com.example.data.model.Position
import com.example.data.model.RlAction
import com.example.data.model.SignalTier
import com.example.data.remote.currencyFor
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearCoral
import com.example.ui.theme.BearCoralDim
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.BullEmeraldDim
import com.example.ui.theme.MonoNumericSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.RiskAmberDim
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SignalBadge(
    tier: SignalTier,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (tier) {
        SignalTier.STRONG_BUY -> BullEmeraldDim to BullEmerald
        SignalTier.BUY -> BullEmeraldDim.copy(alpha = 0.7f) to BullEmerald
        SignalTier.HOLD -> RiskAmberDim to RiskAmber
        SignalTier.SELL -> BearCoralDim.copy(alpha = 0.7f) to BearCoral
        SignalTier.STRONG_SELL -> BearCoralDim to BearCoral
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Text(
            text = tier.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun RlActionBadge(
    action: RlAction,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (action) {
        RlAction.ENTER_LONG -> BullEmeraldDim to BullEmerald
        RlAction.HOLD_POSITION -> AiCyanDim to AiCyan
        RlAction.TRIM_REDUCE -> RiskAmberDim to RiskAmber
        RlAction.STAY_FLAT -> BearCoralDim to BearCoral
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Text(
            text = "RL: ${action.label}",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SentimentBar(
    score: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val normalized = ((score + 100) / 200f).coerceIn(0.05f, 1f)
    val barColor = when {
        score >= 15 -> BullEmerald
        score <= -15 -> BearCoral
        else -> RiskAmber
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Sentiment: $label",
                style = MaterialTheme.typography.labelMedium,
                color = barColor
            )
            Text(
                text = "${if (score > 0) "+" else ""}$score / 100",
                style = MonoNumericSmall,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TerminalSurfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalized)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun LoadingCard(
    message: String = "Syncing live market telemetry...",
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AiCyan,
                strokeWidth = 2.5.dp
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BearCoralDim.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, BearCoral.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = "Error",
                    tint = BearCoral
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (onRetry != null) {
                OutlinedButton(
                    onClick = onRetry,
                    border = BorderStroke(1.dp, BearCoral)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = BearCoral,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Retry", color = BearCoral)
                }
            }
        }
    }
}

@Composable
fun PositionEditorDialog(
    existingPosition: Position? = null,
    prefillSymbol: String = "",
    prefillExchange: String = "",
    prefillCompanyName: String = "",
    prefillPrice: Double? = null,
    prefillStopLoss: Double? = null,
    prefillTakeProfit: Double? = null,
    onDismiss: () -> Unit,
    onSave: (
        symbol: String,
        exchange: String,
        companyName: String,
        shares: Double,
        avgEntryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double,
        maxRiskPercent: Double,
        notes: String
    ) -> Unit
) {
    var symbol by remember(existingPosition?.id, prefillSymbol) {
        mutableStateOf(existingPosition?.symbol ?: prefillSymbol)
    }
    var exchange by remember(existingPosition?.id, prefillExchange) {
        mutableStateOf(existingPosition?.exchange ?: prefillExchange)
    }
    var companyName by remember(existingPosition?.id, prefillCompanyName) {
        mutableStateOf(existingPosition?.companyName ?: prefillCompanyName)
    }
    var sharesText by remember(existingPosition?.id) {
        mutableStateOf(existingPosition?.shares?.let { fmtInput(it) } ?: "10")
    }
    var entryText by remember(existingPosition?.id, prefillPrice) {
        mutableStateOf(
            existingPosition?.avgEntryPrice?.let { fmtInput(it) }
                ?: prefillPrice?.let { fmtInput(it) }
                ?: ""
        )
    }
    var slText by remember(existingPosition?.id, prefillStopLoss) {
        mutableStateOf(
            existingPosition?.stopLossPrice?.takeIf { it > 0.0 }?.let { fmtInput(it) }
                ?: prefillStopLoss?.let { fmtInput(it) }
                ?: ""
        )
    }
    var tpText by remember(existingPosition?.id, prefillTakeProfit) {
        mutableStateOf(
            existingPosition?.takeProfitPrice?.takeIf { it > 0.0 }?.let { fmtInput(it) }
                ?: prefillTakeProfit?.let { fmtInput(it) }
                ?: ""
        )
    }
    var riskPctText by remember(existingPosition?.id) {
        mutableStateOf(existingPosition?.maxRiskPercent?.let { fmtInput(it) } ?: "2.0")
    }
    var notes by remember(existingPosition?.id) { mutableStateOf(existingPosition?.notes ?: "") }
    var validationError by remember(existingPosition?.id) { mutableStateOf<String?>(null) }
    val currHint = currencyFor(exchange, symbol)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TerminalSurface,
        title = {
            Text(
                text = if (existingPosition == null) "Track Active Investment" else "Edit ${existingPosition.symbol} Position",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { if (existingPosition == null) symbol = it.uppercase() },
                    enabled = existingPosition == null,
                    label = { Text("Ticker Symbol (e.g. NVDA, AAPL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("position_symbol_input")
                )
                if (existingPosition == null) {
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sharesText,
                        onValueChange = { sharesText = it },
                        label = { Text("Shares") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("position_shares_input")
                    )
                    OutlinedTextField(
                        value = entryText,
                        onValueChange = { entryText = it },
                        label = { Text("Avg Entry $currHint") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("position_entry_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = slText,
                        onValueChange = { slText = it },
                        label = { Text("Stop-Loss $currHint") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tpText,
                        onValueChange = { tpText = it },
                        label = { Text("Take-Profit $currHint") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = riskPctText,
                    onValueChange = { riskPctText = it },
                    label = { Text("Max Capital Risk %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Thesis / Notes") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (validationError != null) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = BearCoral
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanSym = symbol.trim().uppercase()
                    val sh = sharesText.toDoubleOrNull()
                    val ep = entryText.toDoubleOrNull()
                    val sl = slText.toDoubleOrNull() ?: 0.0
                    val tp = tpText.toDoubleOrNull() ?: 0.0
                    val rp = riskPctText.toDoubleOrNull() ?: 2.0

                    val inferredExchange = exchange.trim().ifBlank {
                        when {
                            cleanSym.endsWith(".NS") -> "NSE"
                            cleanSym.endsWith(".BO") -> "BSE"
                            else -> "NASDAQ"
                        }
                    }

                    when {
                        cleanSym.isEmpty() || cleanSym.length > 20 ->
                            validationError = "Enter a valid ticker symbol (1-20 chars)."
                        sh == null || !sh.isFinite() || sh <= 0.0 || sh > 100_000_000.0 ->
                            validationError = "Shares must be between 0 and 100,000,000."
                        ep == null || !ep.isFinite() || ep <= 0.0 || ep > 10_000_000.0 ->
                            validationError = "Average entry price must be between 0 and 10,000,000."
                        !sl.isFinite() || sl < 0.0 || sl > 10_000_000.0 ->
                            validationError = "Stop-loss must be between 0 and 10,000,000."
                        !tp.isFinite() || tp < 0.0 || tp > 10_000_000.0 ->
                            validationError = "Take-profit must be between 0 and 10,000,000."
                        !rp.isFinite() || rp < 0.1 || rp > 100.0 ->
                            validationError = "Max risk % must be between 0.1 and 100."
                        else -> onSave(
                            cleanSym,
                            inferredExchange,
                            companyName.ifBlank { cleanSym },
                            sh,
                            ep,
                            sl,
                            tp,
                            rp,
                            notes
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BullEmerald,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier.testTag("save_position_button")
            ) {
                Text("Save Position", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRiskAlertDialog(
    prefillSymbol: String = "",
    prefillPrice: Double? = null,
    prefillStopLoss: Double? = null,
    onDismiss: () -> Unit,
    onSave: (symbol: String, alertType: AlertType, threshold: Double, notes: String) -> Unit
) {
    var symbol by remember(prefillSymbol) { mutableStateOf(prefillSymbol.ifBlank { "NVDA" }) }
    var selectedType by remember(prefillSymbol) { mutableStateOf(AlertType.STOP_LOSS) }
    var expanded by remember { mutableStateOf(false) }
    var thresholdText by remember(prefillSymbol, prefillPrice, prefillStopLoss) {
        mutableStateOf(
            prefillStopLoss?.let { fmtInput(it) }
                ?: prefillPrice?.let { fmtInput(it * 0.94) }
                ?: "100.0"
        )
    }
    var notes by remember(prefillSymbol) { mutableStateOf("") }
    var errorText by remember(prefillSymbol) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TerminalSurface,
        title = { Text("Create Risk Management Alert", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase() },
                    label = { Text("Ticker Symbol") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("alert_symbol_input")
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedType.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alert Condition") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        AlertType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                onClick = {
                                    selectedType = type
                                    thresholdText = when (type) {
                                        AlertType.RSI_OVERBOUGHT -> "70.0"
                                        AlertType.RSI_OVERSOLD -> "30.0"
                                        AlertType.DRAWDOWN_LIMIT -> "8.0"
                                        AlertType.SIGNAL_FLIP -> "0.0"
                                        AlertType.STOP_LOSS -> prefillStopLoss?.let { fmtInput(it) }
                                            ?: prefillPrice?.let { fmtInput(it * 0.94) }
                                            ?: "100.0"
                                        AlertType.TAKE_PROFIT -> prefillPrice?.let { fmtInput(it * 1.12) } ?: "150.0"
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = { Text("Threshold (${selectedType.unitHint})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("alert_threshold_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Action Plan Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(errorText.orEmpty(), color = BearCoral, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanSym = symbol.trim().uppercase()
                    val threshold = thresholdText.toDoubleOrNull()
                    when {
                        cleanSym.isEmpty() || cleanSym.length > 20 ->
                            errorText = "Enter a valid ticker symbol."
                        threshold == null || !threshold.isFinite() || threshold < 0.0 || threshold > 10_000_000.0 ->
                            errorText = "Enter a threshold between 0 and 10,000,000."
                        else -> onSave(cleanSym, selectedType, threshold, notes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RiskAmber, contentColor = ObsidianBg),
                modifier = Modifier.testTag("save_alert_button")
            ) {
                Text("Create Alert", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun fmtInput(v: Double): String = String.format(Locale.US, "%.2f", v)
