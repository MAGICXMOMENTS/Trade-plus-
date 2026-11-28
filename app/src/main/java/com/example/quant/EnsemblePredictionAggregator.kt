package com.example.quant

import com.example.data.model.AiAgentPrediction
import com.example.data.model.AiForecastResult
import com.example.data.model.PredictionDirectionVote
import com.example.data.model.RiskProfile
import com.example.data.model.RlPolicyResult
import com.example.data.model.TechnicalAnalysisResult
import com.example.data.model.TradingViewQuote
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AiPredictorOutput(
    val agentId: String,
    val agentName: String,
    val specialty: String,
    val weight: Double,
    val shortTermBias: String,
    val mediumTermBias: String,
    val directionVote: PredictionDirectionVote,
    val upProbabilityPct: Int,
    val confidencePercent: Int,
    val sentimentScore: Int,
    val targetLow: Double,
    val targetBase: Double,
    val targetHigh: Double,
    val keySignal: String,
    val thesis: String,
    val catalysts: List<String>,
    val riskFactors: List<String>,
    val entryExitGuidance: String,
    val riskManagementAdvice: String
)

object EnsemblePredictionAggregator {

    fun buildQuantAnchorPrediction(
        quote: TradingViewQuote,
        technicals: TechnicalAnalysisResult,
        rlPolicy: RlPolicyResult,
        quantPrediction: StrongPredictionResult,
        weight: Double = 0.18
    ): AiPredictorOutput {
        val price = quote.price.coerceAtLeast(0.01)
        val direction = when {
            quantPrediction.upProbabilityPct >= 56 -> PredictionDirectionVote.BULLISH
            quantPrediction.upProbabilityPct <= 44 -> PredictionDirectionVote.BEARISH
            else -> PredictionDirectionVote.NEUTRAL
        }

        val lowTarget = min(technicals.stopLossLevel, quantPrediction.target7d * 0.94)
            .coerceIn(price * 0.50, price * 1.10)
        val baseTarget = quantPrediction.target30d.coerceAtLeast(price * 0.55)
        val highTarget = max(baseTarget * 1.06, technicals.takeProfit2 * 1.04)
            .coerceAtLeast(baseTarget + price * 0.01)

        val ordered = listOf(lowTarget, baseTarget, highTarget).sorted()
        val sentiment = ((quantPrediction.upProbabilityPct - 50) * 1.8).roundToInt().coerceIn(-100, 100)

        return AiPredictorOutput(
            agentId = "quant_rl_engine",
            agentName = "Quant & Q-Learning Model",
            specialty = "14-Indicator Confluence + RL Walk-Forward",
            weight = weight,
            shortTermBias = quantPrediction.direction.label,
            mediumTermBias = rlPolicy.optimalAction.label,
            directionVote = direction,
            upProbabilityPct = quantPrediction.upProbabilityPct.coerceIn(1, 99),
            confidencePercent = quantPrediction.convictionScore.coerceIn(1, 99),
            sentimentScore = sentiment,
            targetLow = ordered[0],
            targetBase = ordered[1],
            targetHigh = ordered[2],
            keySignal = "Score ${technicals.compositeScore}/100 • ${rlPolicy.optimalAction.label.substringBefore(" ")} (${String.format(Locale.US, "%.0f%%", rlPolicy.historicalWinRatePct)} WR)",
            thesis = "${quantPrediction.headlineSummary}. Stop-loss anchored at ATR level ${fmt(technicals.stopLossLevel)} with R:R 1:${fmt(technicals.riskRewardRatio)}.",
            catalysts = quantPrediction.suggestions.filter { it.bullish == true }.map { it.title },
            riskFactors = quantPrediction.suggestions.filter { it.bullish == false }.map { it.title },
            entryExitGuidance = quantPrediction.whenToBuy,
            riskManagementAdvice = quantPrediction.whenToSell
        )
    }

    fun synthesizeEnsemble(
        quote: TradingViewQuote,
        technicals: TechnicalAnalysisResult,
        rlPolicy: RlPolicyResult,
        riskProfile: RiskProfile,
        cloudPredictions: List<AiPredictorOutput>,
        quantPrediction: StrongPredictionResult,
        currencySymbol: String
    ): AiForecastResult {
        val quantAnchor = buildQuantAnchorPrediction(quote, technicals, rlPolicy, quantPrediction)
        val allOutputs = (cloudPredictions + quantAnchor).filter { it.targetBase > 0.0 && it.targetBase.isFinite() }
        require(allOutputs.isNotEmpty()) { "No valid AI predictor outputs available to synthesize." }

        val currentPrice = quote.price.coerceAtLeast(0.01)

        // Effective confidence-calibrated weights
        val rawEffectiveWeights = allOutputs.map { out ->
            out.weight.coerceAtLeast(0.05) * (0.55 + 0.45 * (out.confidencePercent.coerceIn(1, 99) / 100.0))
        }
        val weightSum = rawEffectiveWeights.sum().coerceAtLeast(1e-6)
        val normalizedWeights = rawEffectiveWeights.map { it / weightSum }

        var wLow = 0.0
        var wBase = 0.0
        var wHigh = 0.0
        var wUpProb = 0.0
        var wConf = 0.0
        var wSent = 0.0

        val agentCards = allOutputs.mapIndexed { idx, out ->
            val w = normalizedWeights[idx]
            val sorted = listOf(out.targetLow, out.targetBase, out.targetHigh).sorted()
            wLow += sorted[0] * w
            wBase += sorted[1] * w
            wHigh += sorted[2] * w
            wUpProb += out.upProbabilityPct * w
            wConf += out.confidencePercent * w
            wSent += out.sentimentScore * w

            AiAgentPrediction(
                agentId = out.agentId,
                agentName = out.agentName,
                specialty = out.specialty,
                direction = out.directionVote,
                upProbabilityPct = out.upProbabilityPct.coerceIn(1, 99),
                confidencePercent = out.confidencePercent.coerceIn(1, 99),
                targetLow = sorted[0],
                targetBase = sorted[1],
                targetHigh = sorted[2],
                keySignal = out.keySignal.ifBlank { out.shortTermBias },
                horizonNote = out.thesis,
                weight = w
            )
        }

        val orderedUnified = listOf(wLow, wBase, wHigh).sorted()
        val unifiedLow = orderedUnified[0]
        val unifiedBase = orderedUnified[1]
        val unifiedHigh = orderedUnified[2]

        val bullVotes = agentCards.count { it.direction == PredictionDirectionVote.BULLISH }
        val neutralVotes = agentCards.count { it.direction == PredictionDirectionVote.NEUTRAL }
        val bearishVotes = agentCards.count { it.direction == PredictionDirectionVote.BEARISH }
        val totalVotes = agentCards.size.coerceAtLeast(1)

        val baseTargets = agentCards.map { it.targetBase }
        val minBase = baseTargets.minOrNull() ?: unifiedBase
        val maxBase = baseTargets.maxOrNull() ?: unifiedBase
        val avgBase = baseTargets.average().coerceAtLeast(0.01)
        val spreadPct = ((maxBase - minBase) / avgBase) * 100.0

        val dominantVotes = max(bullVotes, max(neutralVotes, bearishVotes))
        val voteAgreement = (dominantVotes.toDouble() / totalVotes) * 100.0
        val targetTightness = (100.0 - spreadPct * 3.5).coerceIn(20.0, 100.0)
        val agreementPct = (voteAgreement * 0.68 + targetTightness * 0.32).roundToInt().coerceIn(25, 99)

        val finalUpProb = wUpProb.roundToInt().coerceIn(5, 95)
        val finalSent = wSent.roundToInt().coerceIn(-100, 100)
        val calibratedConf = (wConf * (0.72 + 0.28 * (agreementPct / 100.0))).roundToInt().coerceIn(15, 98)

        val consensusVerdict = when {
            bullVotes >= bearishVotes + 2 && finalUpProb >= 62 -> "STRONG BULLISH CONSENSUS"
            bullVotes > bearishVotes && finalUpProb >= 53 -> "BULLISH CONSENSUS"
            bearishVotes >= bullVotes + 2 && finalUpProb <= 38 -> "STRONG BEARISH CONSENSUS"
            bearishVotes > bullVotes && finalUpProb <= 47 -> "BEARISH CONSENSUS"
            else -> "MIXED / RANGE-BOUND"
        }

        val unifiedAction = when {
            consensusVerdict.startsWith("STRONG BULLISH") ->
                "BUY / ACCUMULATE (${currencySymbol}${fmt(technicals.entryZoneLow)}–${currencySymbol}${fmt(technicals.entryZoneHigh)})"
            consensusVerdict == "BULLISH CONSENSUS" ->
                "CAUTIOUS LONG / SCALE IN ABOVE ${currencySymbol}${fmt(technicals.stopLossLevel)}"
            consensusVerdict.startsWith("STRONG BEARISH") ->
                "EXIT / AVOID LONGS (PROTECT BELOW ${currencySymbol}${fmt(technicals.stopLossLevel)})"
            consensusVerdict == "BEARISH CONSENSUS" ->
                "TRIM EXPOSURE / HEDGE RALLIES"
            else ->
                "WAIT FOR BREAKOUT CONFIRMATION"
        }

        val sentimentLabel = when {
            finalSent >= 45 -> "Strongly Bullish"
            finalSent >= 15 -> "Bullish"
            finalSent <= -45 -> "Strongly Bearish"
            finalSent <= -15 -> "Bearish"
            else -> "Neutral"
        }

        val lowestAgent = agentCards.minByOrNull { it.targetBase }
        val highestAgent = agentCards.maxByOrNull { it.targetBase }
        val targetDeltaPct = ((unifiedBase - currentPrice) / currentPrice) * 100.0

        val comparisonSummary = buildString {
            append("${cloudPredictions.size} specialist cloud AIs + Quant/RL engine compared (${bullVotes} Bullish, ${neutralVotes} Neutral, ${bearishVotes} Bearish). ")
            if (lowestAgent != null && highestAgent != null && lowestAgent.agentId != highestAgent.agentId) {
                append("Base targets range from $currencySymbol${fmt(lowestAgent.targetBase)} (${lowestAgent.agentName}) to $currencySymbol${fmt(highestAgent.targetBase)} (${highestAgent.agentName}), a ${fmt(spreadPct)}% spread. ")
            }
            append("Confidence-weighted synthesis yields $consensusVerdict at $currencySymbol${fmt(unifiedBase)} (${String.format(Locale.US, "%+.1f%%", targetDeltaPct)}) with $agreementPct% model alignment.")
        }

        val bestShortTerm = cloudPredictions.firstOrNull { it.agentId == "tech_strategist" }?.shortTermBias
            ?: cloudPredictions.firstOrNull()?.shortTermBias
            ?: quantAnchor.shortTermBias
        val bestMediumTerm = cloudPredictions.firstOrNull { it.agentId == "quant_rl_analyst" }?.mediumTermBias
            ?: cloudPredictions.firstOrNull()?.mediumTermBias
            ?: quantAnchor.mediumTermBias

        val combinedThesis = buildString {
            appendLine(comparisonSummary)
            cloudPredictions.forEach { pred ->
                if (pred.thesis.isNotBlank()) {
                    appendLine("• ${pred.agentName}: ${pred.thesis.trim()}")
                }
            }
        }.trim()

        val mergedCatalysts = (cloudPredictions.flatMap { it.catalysts } + quantAnchor.catalysts)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.US) }
            .take(5)

        val mergedRisks = (cloudPredictions.flatMap { it.riskFactors } + quantAnchor.riskFactors)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.US) }
            .take(5)

        val mergedEntryExit = cloudPredictions.firstOrNull { it.agentId == "tech_strategist" }?.entryExitGuidance
            ?.takeIf { it.isNotBlank() }
            ?: cloudPredictions.firstOrNull()?.entryExitGuidance
            ?: quantAnchor.entryExitGuidance

        val mergedRiskAdvice = cloudPredictions.firstOrNull { it.agentId == "risk_sentinel" }?.riskManagementAdvice
            ?.takeIf { it.isNotBlank() }
            ?: cloudPredictions.firstOrNull()?.riskManagementAdvice
            ?: "Risk ${riskProfile.defaultRiskPerTradePct}% per trade with stop-loss near $currencySymbol${fmt(technicals.stopLossLevel)}."

        return AiForecastResult(
            symbol = quote.symbol,
            shortTermBias = "$bestShortTerm ($finalUpProb% Up)",
            mediumTermBias = bestMediumTerm,
            longTermThesis = combinedThesis,
            projectedTargetLow = unifiedLow,
            projectedTargetBase = unifiedBase,
            projectedTargetHigh = unifiedHigh,
            confidencePercent = calibratedConf,
            sentimentScore = finalSent,
            sentimentLabel = sentimentLabel,
            keyCatalysts = mergedCatalysts,
            keyRiskFactors = mergedRisks,
            entryExitGuidance = mergedEntryExit,
            riskManagementAdvice = mergedRiskAdvice,
            agentPredictions = agentCards,
            consensusVerdict = consensusVerdict,
            agreementPercent = agreementPct,
            bullishVotes = bullVotes,
            neutralVotes = neutralVotes,
            bearishVotes = bearishVotes,
            ensembleSpreadPct = spreadPct,
            comparisonSummary = comparisonSummary,
            recommendedAction = unifiedAction
        )
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
}
