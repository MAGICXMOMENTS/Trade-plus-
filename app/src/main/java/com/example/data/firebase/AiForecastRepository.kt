package com.example.data.firebase

import android.util.Log
import com.example.data.model.AiForecastResult
import com.example.data.model.Position
import com.example.data.model.RiskProfile
import com.example.data.model.RlPolicyResult
import com.example.data.model.TechnicalAnalysisResult
import com.example.data.model.TradingViewQuote
import com.example.data.remote.currencyFor
import com.example.data.remote.isIndianStock
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

class AiForecastRepository {

    private val forecastSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "shortTermBias" to Schema.string(description = "Short-term (1-2 weeks) directional market bias"),
                "mediumTermBias" to Schema.string(description = "Medium-term (1-3 months) directional trend outlook"),
                "longTermThesis" to Schema.string(description = "Comprehensive 3-12 month price action thesis grounded in the quantitative telemetry"),
                "projectedTargetLow" to Schema.double(description = "Conservative downside support / bear-case price target in the asset's trading currency"),
                "projectedTargetBase" to Schema.double(description = "Base-case 3-6 month price target in the asset's trading currency"),
                "projectedTargetHigh" to Schema.double(description = "Bull-case upside price target in the asset's trading currency"),
                "confidencePercent" to Schema.integer(description = "Forecast confidence from 1 to 99"),
                "sentimentScore" to Schema.integer(description = "Synthesized market & technical sentiment score from -100 to 100"),
                "sentimentLabel" to Schema.string(description = "One of: Strongly Bearish, Bearish, Neutral, Bullish, Strongly Bullish"),
                "keyCatalysts" to Schema.array(
                    items = Schema.string(),
                    description = "3 concise technical/structural bullish or trend catalysts"
                ),
                "keyRiskFactors" to Schema.array(
                    items = Schema.string(),
                    description = "3 concise risk factors or invalidation levels"
                ),
                "entryExitGuidance" to Schema.string(description = "Precise entry, stop-loss, and scaling exit guidance"),
                "riskManagementAdvice" to Schema.string(description = "Position sizing and drawdown protection advice")
            )
        )
    }

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = forecastSchema
                temperature = 0.2f
            }
        )
    }

    private val streamingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.3f
            }
        )
    }

    suspend fun generatePredictiveForecast(
        quote: TradingViewQuote,
        technicals: TechnicalAnalysisResult,
        rlPolicy: RlPolicyResult,
        riskProfile: RiskProfile
    ): AiForecastResult {
        val curr = currencyFor(quote.exchange, quote.symbol)
        val currCode = if (curr == "₹") "INR" else "USD"
        val prompt = buildString {
            appendLine("You are an institutional quantitative strategist and risk analyst.")
            appendLine("Analyze the live TradingView telemetry, technical indicators, and Q-learning policy metrics below for ${quote.symbol} (${quote.companyName}) and produce a disciplined, objective price-action forecast in $currCode ($curr).")
            appendLine()
            appendLine("=== LIVE MARKET DATA (TradingView) ===")
            appendLine("Price: $curr${fmt(quote.price)} (${String.format(Locale.US, "%+.2f", quote.changePct)}%)")
            appendLine("Sector: ${quote.sector} | Exchange: ${quote.exchange}")
            appendLine("52W Range: $curr${fmt(quote.low52w ?: quote.price * 0.8)} - $curr${fmt(quote.high52w ?: quote.price * 1.2)}")
            appendLine("TV Consensus (All / MA / Osc): ${fmt(quote.tvRecommendAll)} / ${fmt(quote.tvRecommendMa)} / ${fmt(quote.tvRecommendOsc)}")
            appendLine("TV Sentiment Score: ${quote.sentimentScore} (${quote.sentimentLabel})")
            appendLine()
            appendLine("=== TECHNICAL INDICATORS (High-Fidelity OHLCV) ===")
            appendLine("Composite Score: ${technicals.compositeScore}/100 (${technicals.signalTier.label})")
            appendLine("RSI(14): ${fmt(technicals.rsi14)} | Stochastic %K/%D: ${fmt(technicals.stochK)}/${fmt(technicals.stochD)} | MFI(14): ${fmt(technicals.mfi14)}")
            appendLine("MACD(12,26,9): ${fmt(technicals.macd)} | Signal: ${fmt(technicals.macdSignal)} | Hist: ${fmt(technicals.macdHistogram)}")
            appendLine("Supertrend(10,3): $curr${fmt(technicals.supertrend)} (${if (technicals.supertrendBullish) "Bullish" else "Bearish"}) | VWAP: $curr${fmt(technicals.vwap)}")
            appendLine("EMA9/21/50/200: ${fmt(technicals.ema9)} / ${fmt(technicals.ema21)} / ${fmt(technicals.ema50)} / ${fmt(technicals.ema200)}")
            appendLine("Bollinger %B: ${fmt(technicals.bollingerPercentB * 100)}% | ATR(14): $curr${fmt(technicals.atr14)} | ADX(14): ${fmt(technicals.adx14)}")
            appendLine("Volume Ratio vs 20d Avg: ${fmt(technicals.volumeRatio)}x | CCI(20): ${fmt(technicals.cci20)} | OBV Flow: ${if (technicals.obvTrendBullish) "Accumulation" else "Distribution"}")
            appendLine("ATR Trade Levels -> Entry: $curr${fmt(technicals.entryZoneLow)}-$curr${fmt(technicals.entryZoneHigh)}, SL: $curr${fmt(technicals.stopLossLevel)}, TP1: $curr${fmt(technicals.takeProfit1)}, TP2: $curr${fmt(technicals.takeProfit2)} (R:R ${fmt(technicals.riskRewardRatio)})")
            appendLine()
            appendLine("=== REINFORCEMENT LEARNING (Q-LEARNING) TELEMETRY ===")
            appendLine("State: ${rlPolicy.currentStateDescription}")
            appendLine("Optimal Action: ${rlPolicy.optimalAction.label} (${rlPolicy.confidencePct}% policy confidence)")
            appendLine("Q-Values -> Long: ${fmt(rlPolicy.qLong)}, Flat: ${fmt(rlPolicy.qFlat)}, Reduce: ${fmt(rlPolicy.qReduce)}")
            appendLine("Walk-Forward Backtest -> Strategy Return: ${fmt(rlPolicy.strategyReturnPct)}% vs Buy&Hold: ${fmt(rlPolicy.buyAndHoldReturnPct)}% | Win Rate: ${fmt(rlPolicy.historicalWinRatePct)}% | Sharpe: ${fmt(rlPolicy.sharpeRatio)} | Max DD: ${fmt(rlPolicy.maxDrawdownPct)}%")
            appendLine()
            appendLine("=== USER RISK PROFILE ===")
            appendLine("Tolerance: ${riskProfile.riskTolerance} | Capital: ${fmt(riskProfile.accountCapital)} | Risk/Trade: ${riskProfile.defaultRiskPerTradePct}% | Max DD Limit: ${riskProfile.maxPortfolioDrawdownPct}%")
            appendLine()
            appendLine("Keep projectedTargetLow < projectedTargetBase < projectedTargetHigh in $currCode, realistic relative to ATR and 52W range.")
        }

        val response = withTimeout(60_000L) {
            structuredModel.generateContent(prompt)
        }
        val jsonText = response.text
            ?: throw IllegalStateException("Gemini returned an empty forecast response.")

        val json = JSONObject(jsonText)
        val catalystsArr = json.optJSONArray("keyCatalysts")
        val risksArr = json.optJSONArray("keyRiskFactors")

        val catalysts = if (catalystsArr != null) {
            (0 until catalystsArr.length()).map { catalystsArr.optString(it).trim() }.filter { it.isNotEmpty() }
        } else emptyList()

        val risks = if (risksArr != null) {
            (0 until risksArr.length()).map { risksArr.optString(it).trim() }.filter { it.isNotEmpty() }
        } else emptyList()

        val rawLow = json.optDouble("projectedTargetLow", quote.price * 0.9)
            .takeIf { it.isFinite() && it > 0.0 } ?: (quote.price * 0.9)
        val rawBase = json.optDouble("projectedTargetBase", quote.price * 1.08)
            .takeIf { it.isFinite() && it > 0.0 } ?: (quote.price * 1.08)
        val rawHigh = json.optDouble("projectedTargetHigh", quote.price * 1.2)
            .takeIf { it.isFinite() && it > 0.0 } ?: (quote.price * 1.2)
        val orderedTargets = listOf(rawLow, rawBase, rawHigh).sorted()

        return AiForecastResult(
            symbol = quote.symbol,
            shortTermBias = json.optString("shortTermBias", "Neutral"),
            mediumTermBias = json.optString("mediumTermBias", "Constructive"),
            longTermThesis = json.optString("longTermThesis", ""),
            projectedTargetLow = orderedTargets[0],
            projectedTargetBase = orderedTargets[1],
            projectedTargetHigh = orderedTargets[2],
            confidencePercent = json.optInt("confidencePercent", 65).coerceIn(1, 99),
            sentimentScore = json.optInt("sentimentScore", quote.sentimentScore).coerceIn(-100, 100),
            sentimentLabel = json.optString("sentimentLabel", quote.sentimentLabel),
            keyCatalysts = catalysts,
            keyRiskFactors = risks,
            entryExitGuidance = json.optString("entryExitGuidance", ""),
            riskManagementAdvice = json.optString("riskManagementAdvice", "")
        )
    }

    fun streamPortfolioRiskAudit(
        positions: List<Position>,
        liveQuotes: Map<String, TradingViewQuote>,
        riskProfile: RiskProfile
    ): Flow<String> = flow {
        val portCurr = if (positions.isNotEmpty() && positions.all { isIndianStock(it.exchange, it.symbol) }) "₹" else "$"
        val prompt = buildString {
            appendLine("You are an institutional portfolio risk manager. Provide a concise, actionable portfolio risk & exit-timing briefing in plain structured sections (1. Portfolio Risk Score & Concentration, 2. Stop-Loss & Drawdown Alerts, 3. Tactical Rebalancing Guidance).")
            appendLine("User Risk Profile: ${riskProfile.riskTolerance}, Capital $portCurr${fmt(riskProfile.accountCapital)}, Max Risk/Trade ${riskProfile.defaultRiskPerTradePct}%, Max Drawdown ${riskProfile.maxPortfolioDrawdownPct}%.")
            appendLine("Active Positions:")
            if (positions.isEmpty()) {
                appendLine("- No open positions yet. Advise on disciplined watchlist screening and position sizing.")
            } else {
                positions.forEach { pos ->
                    val quote = liveQuotes[pos.symbol.uppercase()]
                    val posCurr = currencyFor(quote?.exchange?.ifBlank { pos.exchange } ?: pos.exchange, pos.symbol)
                    val curPrice = quote?.price ?: pos.avgEntryPrice
                    val pnlPct = if (pos.avgEntryPrice > 0.0) {
                        ((curPrice - pos.avgEntryPrice) / pos.avgEntryPrice) * 100.0
                    } else 0.0
                    val value = curPrice * pos.shares
                    appendLine("- ${pos.symbol}: ${pos.shares} sh @ avg $posCurr${fmt(pos.avgEntryPrice)} | Live $posCurr${fmt(curPrice)} (${String.format(Locale.US, "%+.2f", pnlPct)}%) | Value $posCurr${fmt(value)} | SL $posCurr${fmt(pos.stopLossPrice)} | TP $posCurr${fmt(pos.takeProfitPrice)} | TV RSI: ${quote?.tvRsi?.let { fmt(it) } ?: "N/A"} | TV Sentiment: ${quote?.sentimentLabel ?: "N/A"}")
                }
            }
            appendLine("Be concise, specific to the tickers and levels above, and end with a one-line informational research disclaimer.")
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                streamingModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("AI risk audit returned an empty stream.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Portfolio risk audit failed", e)
            throw e
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)

    companion object {
        private const val TAG = "AiForecastRepo"
    }
}
