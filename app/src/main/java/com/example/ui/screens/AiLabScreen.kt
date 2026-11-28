package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PositionStatus
import com.example.data.model.RlPolicyResult
import com.example.data.model.UiState
import com.example.data.remote.currencyFor
import com.example.quant.StrongPredictionResult
import com.example.ui.components.ErrorCard
import com.example.ui.components.InteractivePositionCalculatorCard
import com.example.ui.components.KeyLevelsAndPivotsCard
import com.example.ui.components.LoadingCard
import com.example.ui.components.MultiAiEnsembleCard
import com.example.ui.components.RlActionBadge
import com.example.ui.components.RlEquityCurveChart
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearCoral
import com.example.ui.theme.BearCoralDim
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.BullEmeraldDim
import com.example.ui.theme.MonoNumericMedium
import com.example.ui.theme.MonoNumericSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradePulseViewModel
import java.util.Locale

@Composable
fun AiLabScreen(
    viewModel: TradePulseViewModel,
    onOpenMarkets: () -> Unit
) {
    val selectedSymbol by viewModel.selectedSymbol.collectAsStateWithLifecycle()
    val intelState by viewModel.stockIntelligenceState.collectAsStateWithLifecycle()
    val forecastState by viewModel.aiForecastState.collectAsStateWithLifecycle()
    val positionsUi by viewModel.positionsState.collectAsStateWithLifecycle()
    val watchlistUi by viewModel.watchlistState.collectAsStateWithLifecycle()
    val riskProfile by viewModel.riskProfileState.collectAsStateWithLifecycle()

    val quickTickers = remember(positionsUi, watchlistUi, selectedSymbol) {
        val pos = (positionsUi as? UiState.Success)?.data
            ?.filter { it.statusEnum() == PositionStatus.ACTIVE }?.map { it.symbol }.orEmpty()
        val watch = (watchlistUi as? UiState.Success)?.data?.map { it.symbol }.orEmpty()
        (listOf(selectedSymbol) + pos + watch + listOf("RELIANCE", "TCS", "HDFCBANK", "NVDA", "AAPL", "MSFT", "TSLA")).distinct().take(12)
    }

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
                            text = "Q-LEARNING, AI FORECAST & PRO TOOLS",
                            style = MaterialTheme.typography.labelMedium,
                            color = AiCyan
                        )
                        Text(
                            text = "AI, RL & Trading Lab",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Surface(
                        color = TerminalSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TerminalBorder)
                    ) {
                        Text(
                            text = selectedSymbol,
                            style = MonoNumericMedium,
                            color = BullEmerald,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickTickers.forEach { sym ->
                        FilterChip(
                            selected = sym.equals(selectedSymbol, ignoreCase = true),
                            onClick = { viewModel.selectStock(sym) },
                            label = { Text(sym) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AiCyanDim,
                                selectedLabelColor = AiCyan
                            )
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = onOpenMarkets,
                        label = { Text("+ Search Stock") }
                    )
                }
            }

            when (val intel = intelState) {
                is UiState.Loading -> item { LoadingCard("Training Q-learning policy & multi-factor predictions...") }
                is UiState.Error -> item { ErrorCard(intel.message, onRetry = { viewModel.loadStockIntelligence(forceRefresh = true) }) }
                is UiState.Success -> {
                    val bundle = intel.data
                    val q = bundle.quote
                    val rl = bundle.rlPolicy
                    val tech = bundle.technicals
                    val pred = bundle.prediction
                    val currency = currencyFor(q.exchange, q.symbol)

                    item {
                        PredictionLabSummaryCard(
                            prediction = pred,
                            currencySymbol = currency,
                            currentPrice = q.price
                        )
                    }

                    item {
                        RlDecisionCard(rl = rl)
                    }

                    item {
                        RlEquityCurveChart(
                            strategyCurve = rl.strategyEquityCurve,
                            benchmarkCurve = rl.benchmarkEquityCurve
                        )
                    }

                    // Interactive Position Sizing & R:R Calculator Tool
                    item {
                        InteractivePositionCalculatorCard(
                            symbol = q.symbol,
                            currencySymbol = currency,
                            defaultCapital = riskProfile.accountCapital,
                            defaultRiskPct = riskProfile.defaultRiskPerTradePct,
                            technicals = tech
                        )
                    }

                    // Key Support/Resistance Pivots & Fibonacci Tool
                    item {
                        KeyLevelsAndPivotsCard(
                            currencySymbol = currency,
                            technicals = tech
                        )
                    }

                    // AI Predictive Forecasting Section
                    item {
                        Button(
                            onClick = { viewModel.runAiForecast() },
                            enabled = forecastState !is UiState.Loading,
                            colors = ButtonDefaults.buttonColors(containerColor = BullEmerald, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("run_ai_forecast_button")
                        ) {
                            if (forecastState is UiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = ObsidianBg,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Text("Comparing Multiple AIs...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Run Multi-AI Ensemble Prediction", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    when (val fState = forecastState) {
                        is UiState.Error -> item {
                            ErrorCard(fState.message, onRetry = { viewModel.runAiForecast() })
                        }
                        is UiState.Success -> item {
                            MultiAiEnsembleCard(
                                forecast = fState.data,
                                currencySymbol = currency,
                                currentPrice = q.price
                            )
                        }
                        else -> Unit
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun PredictionLabSummaryCard(
    prediction: StrongPredictionResult,
    currencySymbol: String,
    currentPrice: Double
) {
    val dirColor = if (prediction.isUp) BullEmerald else BearCoral
    val dirBg = if (prediction.isUp) BullEmeraldDim.copy(alpha = 0.45f) else BearCoralDim.copy(alpha = 0.45f)
    val prob = if (prediction.isUp) prediction.upProbabilityPct else 100 - prediction.upProbabilityPct

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, dirColor.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
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
                Surface(
                    color = dirBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, dirColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (prediction.isUp) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = dirColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${prediction.direction.label} • $prob%",
                            style = MaterialTheme.typography.labelLarge,
                            color = dirColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "Conviction ${prediction.convictionScore}/100",
                    style = MonoNumericSmall,
                    color = AiCyan
                )
            }

            Text(prediction.primaryAction, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(prediction.headlineSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile("7D TARGET", "$currencySymbol${fmtMoney(prediction.target7d)}", dirColor, Modifier.weight(1f))
                MetricTile("30D TARGET", "$currencySymbol${fmtMoney(prediction.target30d)}", AiCyan, Modifier.weight(1f))
                MetricTile("STOP LOSS", "$currencySymbol${fmtMoney(prediction.stopLossPrice)}", BearCoral, Modifier.weight(1f))
            }

            Surface(
                color = BullEmeraldDim.copy(alpha = 0.28f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BullEmerald.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (prediction.buyNowRecommended) "WHEN TO BUY • BUY ZONE ACTIVE" else "WHEN TO BUY",
                        style = MaterialTheme.typography.labelSmall,
                        color = BullEmerald,
                        fontWeight = FontWeight.Bold
                    )
                    Text(prediction.whenToBuy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Surface(
                color = BearCoralDim.copy(alpha = 0.22f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RiskAmber.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "WHEN TO SELL",
                        style = MaterialTheme.typography.labelSmall,
                        color = RiskAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Text(prediction.whenToSell, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            prediction.suggestions.forEach { sug ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TerminalSurfaceElevated)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("${sug.title} (${sug.actionLabel})", style = MaterialTheme.typography.labelLarge, color = AiCyan)
                    Text(sug.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun RlDecisionCard(rl: RlPolicyResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(Icons.Filled.Psychology, contentDescription = null, tint = AiCyan)
                    Text("Reinforcement Learning Policy", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "${rl.confidencePct}% conf",
                    style = MonoNumericSmall,
                    color = AiCyan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RlActionBadge(rl.optimalAction)
                Text(
                    text = rl.currentStateDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Text(
                text = rl.rationale,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile("Q(LONG)", String.format(Locale.US, "%+.2f", rl.qLong), BullEmerald, Modifier.weight(1f))
                MetricTile("Q(FLAT)", String.format(Locale.US, "%+.2f", rl.qFlat), TextSecondary, Modifier.weight(1f))
                MetricTile("Q(REDUCE)", String.format(Locale.US, "%+.2f", rl.qReduce), RiskAmber, Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    "RL RETURN",
                    String.format(Locale.US, "%+.1f%%", rl.strategyReturnPct),
                    if (rl.strategyReturnPct >= 0) BullEmerald else BearCoral,
                    Modifier.weight(1f)
                )
                MetricTile(
                    "BUY & HOLD",
                    String.format(Locale.US, "%+.1f%%", rl.buyAndHoldReturnPct),
                    TextSecondary,
                    Modifier.weight(1f)
                )
                MetricTile(
                    "WIN RATE",
                    String.format(Locale.US, "%.0f%%", rl.historicalWinRatePct),
                    AiCyan,
                    Modifier.weight(1f)
                )
                MetricTile(
                    "SHARPE",
                    String.format(Locale.US, "%.2f", rl.sharpeRatio),
                    if (rl.sharpeRatio >= 1.0) BullEmerald else RiskAmber,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
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
        Text(value, style = MonoNumericSmall, color = color, maxLines = 1)
    }
}

private fun fmtMoney(v: Double): String = String.format(Locale.US, "%,.2f", v)
