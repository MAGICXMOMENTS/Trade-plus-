package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.data.model.AiAgentPrediction
import com.example.data.model.AiForecastResult
import com.example.data.model.PredictionDirectionVote
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearCoral
import com.example.ui.theme.BearCoralDim
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.BullEmeraldDim
import com.example.ui.theme.MonoNumericLarge
import com.example.ui.theme.MonoNumericSmall
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.RiskAmberDim
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MultiAiEnsembleCard(
    forecast: AiForecastResult,
    currencySymbol: String,
    currentPrice: Double
) {
    val safePrice = currentPrice.coerceAtLeast(0.01)
    val baseDeltaPct = ((forecast.projectedTargetBase - safePrice) / safePrice) * 100.0
    val isBullVerdict = forecast.consensusVerdict.contains("BULLISH") || forecast.projectedTargetBase >= safePrice
    val isBearVerdict = forecast.consensusVerdict.contains("BEARISH")
    val verdictColor = when {
        isBearVerdict -> BearCoral
        isBullVerdict -> BullEmerald
        else -> RiskAmber
    }
    val verdictBg = when {
        isBearVerdict -> BearCoralDim
        isBullVerdict -> BullEmeraldDim
        else -> RiskAmberDim
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("multi_ai_ensemble_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: One Unified Consensus Verdict
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = AiCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ONE UNIFIED MULTI-AI RESULT",
                            style = MaterialTheme.typography.labelMedium,
                            color = AiCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = verdictBg.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = forecast.consensusVerdict.ifBlank { "${forecast.symbol} UNIFIED VERDICT" },
                            style = MaterialTheme.typography.labelLarge,
                            color = verdictColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${forecast.confidencePercent}% Conf",
                        style = MonoNumericSmall,
                        color = BullEmerald,
                        fontWeight = FontWeight.Bold
                    )
                    if (forecast.agreementPercent > 0) {
                        Text(
                            text = "${forecast.agreementPercent}% Aligned",
                            style = MaterialTheme.typography.labelSmall,
                            color = AiCyan
                        )
                    }
                }
            }

            // Unified Target Hero + Action Banner
            Surface(
                color = TerminalSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("UNIFIED BASE TARGET", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                text = "$currencySymbol${fmtMoney(forecast.projectedTargetBase)}",
                                style = MonoNumericLarge,
                                color = verdictColor
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%+.2f%% vs spot", baseDeltaPct),
                            style = MonoNumericSmall,
                            color = verdictColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ConsensusTile("BEAR CASE", "$currencySymbol${fmtMoney(forecast.projectedTargetLow)}", BearCoral, Modifier.weight(1f))
                        ConsensusTile("BULL CASE", "$currencySymbol${fmtMoney(forecast.projectedTargetHigh)}", BullEmerald, Modifier.weight(1f))
                    }

                    if (forecast.recommendedAction.isNotBlank()) {
                        Surface(
                            color = AiCyanDim.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "UNIFIED ACTION: ${forecast.recommendedAction}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AiCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Vote Tally Bar & Spread Comparison
            if (forecast.agentPredictions.isNotEmpty()) {
                VoteSplitBar(
                    bullish = forecast.bullishVotes,
                    neutral = forecast.neutralVotes,
                    bearish = forecast.bearishVotes,
                    spreadPct = forecast.ensembleSpreadPct
                )
            }

            if (forecast.comparisonSummary.isNotBlank()) {
                Text(
                    text = forecast.comparisonSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Short & Medium Bias + Sentiment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConsensusTile("SHORT-TERM", forecast.shortTermBias, AiCyan, Modifier.weight(1f))
                ConsensusTile("MEDIUM-TERM", forecast.mediumTermBias, BullEmerald, Modifier.weight(1f))
            }

            SentimentBar(score = forecast.sentimentScore, label = forecast.sentimentLabel)

            // Side-by-Side Individual AI Model Comparison
            if (forecast.agentPredictions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = null,
                            tint = RiskAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Individual AI Model Comparison (${forecast.agentPredictions.size} Predictors)",
                            style = MaterialTheme.typography.titleSmall,
                            color = RiskAmber
                        )
                    }
                    forecast.agentPredictions.forEach { agent ->
                        AgentComparisonRow(
                            agent = agent,
                            currencySymbol = currencySymbol,
                            currentPrice = safePrice
                        )
                    }
                }
            }

            if (forecast.keyCatalysts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Consensus Catalysts", style = MaterialTheme.typography.titleSmall, color = BullEmerald)
                    forecast.keyCatalysts.forEach { c ->
                        Text("• $c", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            if (forecast.keyRiskFactors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Risk & Invalidation Factors", style = MaterialTheme.typography.titleSmall, color = BearCoral)
                    forecast.keyRiskFactors.forEach { r ->
                        Text("• $r", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Unified Entry / Exit Guidance", style = MaterialTheme.typography.titleSmall, color = AiCyan)
                Text(forecast.entryExitGuidance, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("Risk Management", style = MaterialTheme.typography.titleSmall, color = RiskAmber)
                Text(forecast.riskManagementAdvice, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
fun CompactMultiAiComparisonStrip(
    forecast: AiForecastResult,
    currencySymbol: String,
    currentPrice: Double
) {
    var expanded by remember(forecast.symbol, forecast.generatedAtMs) { mutableStateOf(false) }
    val safePrice = currentPrice.coerceAtLeast(0.01)
    val deltaPct = ((forecast.projectedTargetBase - safePrice) / safePrice) * 100.0
    val isBull = forecast.consensusVerdict.contains("BULLISH") || forecast.projectedTargetBase >= safePrice
    val isBear = forecast.consensusVerdict.contains("BEARISH")
    val color = when {
        isBear -> BearCoral
        isBull -> BullEmerald
        else -> RiskAmber
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TerminalSurfaceElevated)
            .padding(10.dp)
            .testTag("compact_multi_ai_comparison"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "MULTI-AI UNIFIED VERDICT • ${forecast.agreementPercent}% ALIGNED",
                    style = MaterialTheme.typography.labelSmall,
                    color = AiCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${forecast.consensusVerdict.ifBlank { forecast.shortTermBias }} → $currencySymbol${fmtMoney(forecast.projectedTargetBase)} (${String.format(Locale.US, "%+.1f%%", deltaPct)})",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${forecast.bullishVotes}B/${forecast.neutralVotes}N/${forecast.bearishVotes}S",
                    style = MonoNumericSmall,
                    color = TextSecondary
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse AI comparison" else "Expand AI comparison",
                    tint = AiCyan
                )
            }
        }

        if (forecast.comparisonSummary.isNotBlank()) {
            Text(
                text = forecast.comparisonSummary,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        AnimatedVisibility(visible = expanded && forecast.agentPredictions.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                forecast.agentPredictions.forEach { agent ->
                    AgentComparisonRow(
                        agent = agent,
                        currencySymbol = currencySymbol,
                        currentPrice = safePrice
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteSplitBar(
    bullish: Int,
    neutral: Int,
    bearish: Int,
    spreadPct: Double
) {
    val total = (bullish + neutral + bearish).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Votes: $bullish Bull • $neutral Neutral • $bearish Bear",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "Spread ${String.format(Locale.US, "%.1f%%", spreadPct)}",
                style = MonoNumericSmall,
                color = if (spreadPct <= 6.0) BullEmerald else RiskAmber
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (bullish > 0) {
                Box(
                    modifier = Modifier
                        .weight(bullish.toFloat() / total)
                        .height(8.dp)
                        .background(BullEmerald)
                )
            }
            if (neutral > 0) {
                Box(
                    modifier = Modifier
                        .weight(neutral.toFloat() / total)
                        .height(8.dp)
                        .background(RiskAmber)
                )
            }
            if (bearish > 0) {
                Box(
                    modifier = Modifier
                        .weight(bearish.toFloat() / total)
                        .height(8.dp)
                        .background(BearCoral)
                )
            }
        }
    }
}

@Composable
private fun AgentComparisonRow(
    agent: AiAgentPrediction,
    currencySymbol: String,
    currentPrice: Double
) {
    val safePrice = currentPrice.coerceAtLeast(0.01)
    val deltaPct = ((agent.targetBase - safePrice) / safePrice) * 100.0
    val voteColor = when (agent.direction) {
        PredictionDirectionVote.BULLISH -> BullEmerald
        PredictionDirectionVote.BEARISH -> BearCoral
        PredictionDirectionVote.NEUTRAL -> RiskAmber
    }
    val voteBg = when (agent.direction) {
        PredictionDirectionVote.BULLISH -> BullEmeraldDim
        PredictionDirectionVote.BEARISH -> BearCoralDim
        PredictionDirectionVote.NEUTRAL -> RiskAmberDim
    }
    val weightPct = (agent.weight * 100.0).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${agent.agentName} ($weightPct% wt)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = agent.specialty,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Surface(
                color = voteBg.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "${agent.direction.label} ${agent.upProbabilityPct}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = voteColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target: $currencySymbol${fmtMoney(agent.targetBase)} (${String.format(Locale.US, "%+.1f%%", deltaPct)})",
                style = MonoNumericSmall,
                color = voteColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Range $currencySymbol${fmtMoney(agent.targetLow)}–$currencySymbol${fmtMoney(agent.targetHigh)} • ${agent.confidencePercent}% conf",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        Text(
            text = "Signal: ${agent.keySignal}",
            style = MaterialTheme.typography.labelSmall,
            color = AiCyan
        )
        if (agent.horizonNote.isNotBlank()) {
            Text(
                text = agent.horizonNote,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ConsensusTile(
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
