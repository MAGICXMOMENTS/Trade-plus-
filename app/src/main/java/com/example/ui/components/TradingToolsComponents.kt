package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.TechnicalAnalysisResult
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearCoral
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.MonoNumericMedium
import com.example.ui.theme.MonoNumericSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VwapViolet
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

@Composable
fun InteractivePositionCalculatorCard(
    symbol: String,
    currencySymbol: String,
    defaultCapital: Double,
    defaultRiskPct: Double,
    technicals: TechnicalAnalysisResult,
    onApplyToPositionEditor: ((entry: Double, stopLoss: Double, target: Double, shares: Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var capitalText by remember(symbol, defaultCapital) {
        mutableStateOf(String.format(Locale.US, "%.0f", defaultCapital.coerceAtLeast(1000.0)))
    }
    var riskPctText by remember(symbol, defaultRiskPct) {
        mutableStateOf(String.format(Locale.US, "%.1f", defaultRiskPct.coerceIn(0.2, 10.0)))
    }
    var entryText by remember(symbol, technicals.currentPrice) {
        mutableStateOf(String.format(Locale.US, "%.2f", technicals.currentPrice))
    }
    var stopLossText by remember(symbol, technicals.stopLossLevel) {
        mutableStateOf(String.format(Locale.US, "%.2f", technicals.stopLossLevel))
    }
    var targetText by remember(symbol, technicals.takeProfit1) {
        mutableStateOf(String.format(Locale.US, "%.2f", technicals.takeProfit1))
    }

    val capital = capitalText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: defaultCapital
    val riskPct = riskPctText.toDoubleOrNull()?.coerceIn(0.1, 25.0) ?: defaultRiskPct
    val entry = entryText.toDoubleOrNull()?.coerceAtLeast(0.01) ?: technicals.currentPrice
    val stopLoss = stopLossText.toDoubleOrNull()?.coerceAtLeast(0.01) ?: technicals.stopLossLevel
    val target = targetText.toDoubleOrNull()?.coerceAtLeast(0.01) ?: technicals.takeProfit1

    val maxRiskCash = capital * (riskPct / 100.0)
    val riskPerShare = abs(entry - stopLoss).coerceAtLeast(0.01)
    val rewardPerShare = abs(target - entry).coerceAtLeast(0.01)
    val shares = floor(maxRiskCash / riskPerShare).toInt().coerceAtLeast(1)
    val requiredCapital = shares * entry
    val potentialProfit = shares * rewardPerShare
    val actualRiskCash = shares * riskPerShare
    val rrRatio = rewardPerShare / riskPerShare
    val breakevenWinRatePct = (1.0 / (1.0 + rrRatio)) * 100.0

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("position_calculator_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Calculate, contentDescription = null, tint = AiCyan)
                    Text("Position Size & R:R Calculator", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "1:${fmt(rrRatio)} R:R",
                    style = MonoNumericSmall,
                    color = if (rrRatio >= 1.5) BullEmerald else RiskAmber
                )
            }

            // Risk % Presets + Stop Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.5, 1.0, 2.0, 3.0).forEach { preset ->
                    FilterChip(
                        selected = abs(riskPct - preset) < 0.05,
                        onClick = { riskPctText = String.format(Locale.US, "%.1f", preset) },
                        label = { Text("${preset}% Risk") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AiCyanDim,
                            selectedLabelColor = AiCyan
                        )
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { stopLossText = String.format(Locale.US, "%.2f", technicals.stopLossLevel) },
                    label = { Text("ATR SL") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        val st = technicals.supertrend.coerceAtMost(entry * 0.99)
                        stopLossText = String.format(Locale.US, "%.2f", st)
                    },
                    label = { Text("Supertrend SL") }
                )
                FilterChip(
                    selected = false,
                    onClick = { targetText = String.format(Locale.US, "%.2f", technicals.takeProfit2) },
                    label = { Text("TP2 Target") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = capitalText,
                    onValueChange = { capitalText = it },
                    label = { Text("Capital ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = riskPctText,
                    onValueChange = { riskPctText = it },
                    label = { Text("Risk %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.75f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = entryText,
                    onValueChange = { entryText = it },
                    label = { Text("Entry ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = stopLossText,
                    onValueChange = { stopLossText = it },
                    label = { Text("Stop Loss ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Target ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolMetricTile(
                    label = "BUY QTY",
                    value = "$shares sh",
                    color = BullEmerald,
                    modifier = Modifier.weight(1f)
                )
                ToolMetricTile(
                    label = "CAPITAL NEEDED",
                    value = "$currencySymbol${fmtMoney(requiredCapital)}",
                    color = AiCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolMetricTile(
                    label = "MAX RISK",
                    value = "-$currencySymbol${fmtMoney(actualRiskCash)}",
                    color = BearCoral,
                    modifier = Modifier.weight(1f)
                )
                ToolMetricTile(
                    label = "TARGET GAIN",
                    value = "+$currencySymbol${fmtMoney(potentialProfit)}",
                    color = BullEmerald,
                    modifier = Modifier.weight(1f)
                )
                ToolMetricTile(
                    label = "MIN WIN RATE",
                    value = String.format(Locale.US, "%.0f%%", breakevenWinRatePct),
                    color = RiskAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            if (onApplyToPositionEditor != null) {
                Button(
                    onClick = { onApplyToPositionEditor(entry, stopLoss, target, shares.toDouble()) },
                    colors = ButtonDefaults.buttonColors(containerColor = BullEmerald, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("apply_calculated_trade_button")
                ) {
                    Text("Track $shares Shares @ $currencySymbol${fmt(entry)}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun KeyLevelsAndPivotsCard(
    currencySymbol: String,
    technicals: TechnicalAnalysisResult,
    modifier: Modifier = Modifier
) {
    var showFibonacci by remember { mutableStateOf(false) }
    val price = technicals.currentPrice.coerceAtLeast(0.01)

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = null, tint = RiskAmber)
                    Text("Support & Resistance Levels", style = MaterialTheme.typography.titleMedium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showFibonacci,
                    onClick = { showFibonacci = false },
                    label = { Text("Pivot Points") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AiCyanDim,
                        selectedLabelColor = AiCyan
                    )
                )
                FilterChip(
                    selected = showFibonacci,
                    onClick = { showFibonacci = true },
                    label = { Text("Fibonacci Retracement") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AiCyanDim,
                        selectedLabelColor = AiCyan
                    )
                )
            }

            val rows = if (!showFibonacci) {
                val p = technicals.pivotLevels
                listOf(
                    Triple("Resistance 2 (R2)", p.r2, BearCoral),
                    Triple("Resistance 1 (R1)", p.r1, RiskAmber),
                    Triple("Daily Pivot (P)", p.pivot, AiCyan),
                    Triple("Support 1 (S1)", p.s1, BullEmerald),
                    Triple("Support 2 (S2)", p.s2, BullEmerald)
                )
            } else {
                val f = technicals.fibLevels
                listOf(
                    Triple("Swing High (0%)", f.swingHigh, BearCoral),
                    Triple("Fib 23.6%", f.level236, RiskAmber),
                    Triple("Fib 38.2%", f.level382, AiCyan),
                    Triple("Fib 50.0% Mid", f.level500, VwapViolet),
                    Triple("Fib 61.8% Golden", f.level618, BullEmerald),
                    Triple("Swing Low (100%)", f.swingLow, BullEmerald)
                )
            }

            rows.forEach { (label, level, color) ->
                val distPct = ((level - price) / price) * 100.0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TerminalSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format(Locale.US, "%+.2f%%", distPct),
                            style = MonoNumericSmall,
                            color = if (distPct >= 0) BullEmerald else BearCoral
                        )
                        Text(
                            text = "$currencySymbol${fmt(level)}",
                            style = MonoNumericSmall,
                            color = color,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrailingStopAndVolatilityCard(
    currencySymbol: String,
    technicals: TechnicalAnalysisResult,
    modifier: Modifier = Modifier
) {
    val price = technicals.currentPrice.coerceAtLeast(0.01)
    val atrPct = (technicals.atr14 / price) * 100.0
    val supertrendDistPct = ((technicals.supertrend - price) / price) * 100.0
    val vwapDistPct = ((price - technicals.vwap) / technicals.vwap.coerceAtLeast(0.01)) * 100.0

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = BullEmerald)
                Text("Volatility & Trailing Stop Suite", style = MaterialTheme.typography.titleMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolMetricTile(
                    label = "SUPERTREND (10,3)",
                    value = "$currencySymbol${fmt(technicals.supertrend)}",
                    color = if (technicals.supertrendBullish) BullEmerald else BearCoral,
                    modifier = Modifier.weight(1f)
                )
                ToolMetricTile(
                    label = "VWAP ANCHOR",
                    value = "$currencySymbol${fmt(technicals.vwap)}",
                    color = VwapViolet,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolMetricTile(
                    label = "ATR(14) MOVE",
                    value = "±${String.format(Locale.US, "%.2f", atrPct)}%",
                    color = RiskAmber,
                    modifier = Modifier.weight(1f)
                )
                ToolMetricTile(
                    label = "VOLUME SURGE",
                    value = "${String.format(Locale.US, "%.2f", technicals.volumeRatio)}x",
                    color = if (technicals.volumeRatio >= 1.2) BullEmerald else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                ToolMetricTile(
                    label = "MFI / CCI",
                    value = "${String.format(Locale.US, "%.0f", technicals.mfi14)} / ${String.format(Locale.US, "%+.0f", technicals.cci20)}",
                    color = AiCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                color = TerminalSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Supertrend is ${if (technicals.supertrendBullish) "BULLISH trailing support" else "BEARISH resistance"} (${String.format(Locale.US, "%+.2f%%", supertrendDistPct)} vs price). Price trades ${String.format(Locale.US, "%+.2f%%", vwapDistPct)} vs VWAP with ${if (technicals.obvTrendBullish) "positive OBV accumulation" else "OBV distribution"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolMetricTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(value, style = MonoNumericMedium, color = color, maxLines = 1)
    }
}

private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
private fun fmtMoney(v: Double): String = String.format(Locale.US, "%,.2f", v)
