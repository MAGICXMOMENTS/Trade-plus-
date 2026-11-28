package com.example.data.firebase

import android.util.Log
import com.example.data.model.AiForecastResult
import com.example.data.model.Position
import com.example.data.model.PredictionDirectionVote
import com.example.data.model.RiskProfile
import com.example.data.model.RlPolicyResult
import com.example.data.model.TechnicalAnalysisResult
import com.example.data.model.TradingViewQuote
import com.example.data.remote.currencyFor
import com.example.data.remote.isIndianStock
import com.example.quant.AiPredictorOutput
import com.example.quant.EnsemblePredictionAggregator
import com.example.quant.PredictionEngine
import com.example.quant.StrongPredictionResult
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

class AiForecastRepository {

    private val agentForecastSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "directionVote" to Schema.enumeration(
                    values = listOf("BULLISH", "NEUTRAL", "BEARISH"),
                    description = "Primary directional vote"
                ),
                "shortTermBias" to Schema.string(description = "Short-term (1-2 weeks) directional market bias"),
                "mediumTermBias" to Schema.string(description = "Medium-term (1-3 months) directional trend outlook"),
                "upProbabilityPct" to Schema.integer(description = "Estimated upside probability percentage from 5 to 95"),
                "confidencePercent" to Schema.integer(description = "Predictor conviction from 10 to 98"),
                "sentimentScore" to Schema.integer(description = "Sentiment score from -100 to 100"),
                "projectedTargetLow" to Schema.double(description = "Conservative bear-case price target in asset currency"),
                "projectedTargetBase" to Schema.double(description = "Base-case 3-6 month price target in asset currency"),
                "projectedTargetHigh" to Schema.double(description = "Bull-case upside price target in asset currency"),
                "keySignal" to Schema.string(description = "One crisp 3-6 word headline signal from this specialist lens"),
                "thesis" to Schema.string(description = "Concise 2-sentence institutional thesis from this specialist perspective"),
                "keyCatalysts" to Schema.array(
                    items = Schema.string(),
                    description = "2 concise catalysts or setups supporting the view"
                ),
                "keyRiskFactors" to Schema.array(
                    items = Schema.string(),
                    description = "2 concise risk factors or price invalidation levels"
                ),
                "entryExitGuidance" to Schema.string(description = "Concrete entry zone, stop-loss, and scaling profit plan"),
                "riskManagementAdvice" to Schema.string(description = "Sizing and capital preservation guidance tailored to user risk limits")
            )
        )
    }

    // Specialist Predictor 1: Technical & Market Structure AI
    private val technicalStrategistModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = agentForecastSchema
                temperature = 0.15f
            }
        )
    }

    // Specialist Predictor 2: Fast Quantitative & RL Probability AI
    private val quantAnalystModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = agentForecastSchema
                temperature = 0.15f
            }
        )
    }

    // Specialist Predictor 3: Institutional Macro & Risk Sentinel AI
    private val riskSentinelModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = agentForecastSchema
                temperature = 0.25f
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
        riskProfile: RiskProfile,
        quantPrediction: StrongPredictionResult? = null
    ): AiForecastResult {
        val curr = currencyFor(quote.exchange, quote.symbol)
        val currCode = if (curr == "₹") "INR" else "USD"
        val telemetryBlock = buildSharedTelemetry(quote, technicals, rlPolicy, riskProfile, curr, currCode)

        val techPrompt = buildString {
            appendLine("ROLE: AI Technical & Price-Action Strategist.")
            appendLine("Focus heavily on chart structure, Supertrend, Ichimoku Cloud, TTM Squeeze, Smart Money FVG/Order Blocks, VWAP, EMA alignments, Fibonacci pivots, and momentum oscillators.")
            appendLine(telemetryBlock)
            appendLine("Keep projectedTargetLow < projectedTargetBase < projectedTargetHigh in $currCode, anchored strictly to ATR, pivots, and 52W range.")
        }

        val quantPrompt = buildString {
            appendLine("ROLE: AI Quantitative & Reinforcement-Learning Analyst.")
            appendLine("Focus heavily on Q-Learning Q-values (Long vs Flat vs Reduce), walk-forward backtest win rate, Sharpe ratio, max drawdown, ATR(14) statistical dispersion, and Risk-Reward expectancy.")
            appendLine(telemetryBlock)
            appendLine("Keep projectedTargetLow < projectedTargetBase < projectedTargetHigh in $currCode calibrated to statistical volatility.")
        }

        val riskPrompt = buildString {
            appendLine("ROLE: AI Macro Sentiment & Risk Sentinel.")
            appendLine("Focus heavily on TradingView institutional rating consensus, OBV accumulation/distribution, 52W valuation position, downside invalidation scenarios, and strict adherence to user risk limits.")
            appendLine(telemetryBlock)
            appendLine("Keep projectedTargetLow < projectedTargetBase < projectedTargetHigh in $currCode with disciplined capital protection.")
        }

        val outcomes = withTimeout(60_000L) {
            coroutineScope {
                val dTech = async {
                    runAgentPrediction(
                        model = technicalStrategistModel,
                        agentId = "tech_strategist",
                        agentName = "AI Technical Strategist",
                        specialty = "Chart Structure, SMC & Momentum",
                        weight = 0.34,
                        prompt = techPrompt,
                        referencePrice = quote.price
                    )
                }
                val dQuant = async {
                    runAgentPrediction(
                        model = quantAnalystModel,
                        agentId = "quant_rl_analyst",
                        agentName = "AI Quant & RL Analyst",
                        specialty = "Q-Policy, Sharpe & Volatility Math",
                        weight = 0.26,
                        prompt = quantPrompt,
                        referencePrice = quote.price
                    )
                }
                val dRisk = async {
                    runAgentPrediction(
                        model = riskSentinelModel,
                        agentId = "risk_sentinel",
                        agentName = "AI Macro & Risk Sentinel",
                        specialty = "Sentiment, 52W Regime & Downside Guard",
                        weight = 0.22,
                        prompt = riskPrompt,
                        referencePrice = quote.price
                    )
                }
                listOf(dTech.await(), dQuant.await(), dRisk.await())
            }
        }

        val successes = outcomes.mapNotNull { it.getOrNull() }
        if (successes.isEmpty()) {
            val firstErr = outcomes.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IllegalStateException("All AI predictors returned empty responses.")
            throw firstErr
        }

        val anchorQuant = quantPrediction ?: PredictionEngine.predict(
            symbol = quote.symbol,
            bars = emptyList(),
            tech = technicals,
            rl = rlPolicy,
            quote = quote,
            currencySymbol = curr
        )

        return EnsemblePredictionAggregator.synthesizeEnsemble(
            quote = quote,
            technicals = technicals,
            rlPolicy = rlPolicy,
            riskProfile = riskProfile,
            cloudPredictions = successes,
            quantPrediction = anchorQuant,
            currencySymbol = curr
        )
    }

    private suspend fun runAgentPrediction(
        model: GenerativeModel,
        agentId: String,
        agentName: String,
        specialty: String,
        weight: Double,
        prompt: String,
        referencePrice: Double
    ): Result<AiPredictorOutput> {
        return try {
            val response = model.generateContent(prompt)
            val jsonText = response.text
                ?: throw IllegalStateException("$agentName returned empty response.")
            val json = JSONObject(jsonText)

            val catalystsArr = json.optJSONArray("keyCatalysts")
            val risksArr = json.optJSONArray("keyRiskFactors")
            val catalysts = if (catalystsArr != null) {
                (0 until catalystsArr.length()).map { catalystsArr.optString(it).trim() }.filter { it.isNotEmpty() }
            } else emptyList()
            val risks = if (risksArr != null) {
                (0 until risksArr.length()).map { risksArr.optString(it).trim() }.filter { it.isNotEmpty() }
            } else emptyList()

            val rawLow = json.optDouble("projectedTargetLow", referencePrice * 0.92)
                .takeIf { it.isFinite() && it > 0.0 } ?: (referencePrice * 0.92)
            val rawBase = json.optDouble("projectedTargetBase", referencePrice * 1.06)
                .takeIf { it.isFinite() && it > 0.0 } ?: (referencePrice * 1.06)
            val rawHigh = json.optDouble("projectedTargetHigh", referencePrice * 1.18)
                .takeIf { it.isFinite() && it > 0.0 } ?: (referencePrice * 1.18)
            val ordered = listOf(rawLow, rawBase, rawHigh).sorted()

            val voteRaw = json.optString("directionVote", "NEUTRAL").trim().uppercase(Locale.US)
            val vote = when (voteRaw) {
                "BULLISH" -> PredictionDirectionVote.BULLISH
                "BEARISH" -> PredictionDirectionVote.BEARISH
                else -> PredictionDirectionVote.NEUTRAL
            }

            Result.success(
                AiPredictorOutput(
                    agentId = agentId,
                    agentName = agentName,
                    specialty = specialty,
                    weight = weight,
                    shortTermBias = json.optString("shortTermBias", "Neutral"),
                    mediumTermBias = json.optString("mediumTermBias", "Constructive"),
                    directionVote = vote,
                    upProbabilityPct = json.optInt("upProbabilityPct", 50).coerceIn(1, 99),
                    confidencePercent = json.optInt("confidencePercent", 65).coerceIn(1, 99),
                    sentimentScore = json.optInt("sentimentScore", 0).coerceIn(-100, 100),
                    targetLow = ordered[0],
                    targetBase = ordered[1],
                    targetHigh = ordered[2],
                    keySignal = json.optString("keySignal", vote.label),
                    thesis = json.optString("thesis", ""),
                    catalysts = catalysts,
                    riskFactors = risks,
                    entryExitGuidance = json.optString("entryExitGuidance", ""),
                    riskManagementAdvice = json.optString("riskManagementAdvice", "")
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$agentName inference error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildSharedTelemetry(
        quote: TradingViewQuote,
        technicals: TechnicalAnalysisResult,
        rlPolicy: RlPolicyResult,
        riskProfile: RiskProfile,
        curr: String,
        currCode: String
    ): String {
        val tenkan = technicals.ichimokuTenkanSeries.lastOrNull { it != null }
        val kijun = technicals.ichimokuKijunSeries.lastOrNull { it != null }
        val squeezeMom = technicals.squeezeMomentumSeries.lastOrNull { it != null }
        val squeezeOn = technicals.squeezeOnSeries.lastOrNull { it != null }

        return buildString {
            appendLine("ASSET: ${quote.symbol} (${quote.companyName}) | Currency: $currCode ($curr)")
            appendLine("=== LIVE MARKET DATA (TradingView) ===")
            appendLine("Price: $curr${fmt(quote.price)} (${String.format(Locale.US, "%+.2f", quote.changePct)}%)")
            appendLine("Sector: ${quote.sector} | Exchange: ${quote.exchange}")
            appendLine("52W Range: $curr${fmt(quote.low52w ?: quote.price * 0.8)} - $curr${fmt(quote.high52w ?: quote.price * 1.2)}")
            appendLine("TV Consensus (All / MA / Osc): ${fmt(quote.tvRecommendAll)} / ${fmt(quote.tvRecommendMa)} / ${fmt(quote.tvRecommendOsc)}")
            appendLine("TV Sentiment Score: ${quote.sentimentScore} (${quote.sentimentLabel})")
            appendLine()
            appendLine("=== TECHNICAL & PRO INDICATORS (OHLCV) ===")
            appendLine("Composite Score: ${technicals.compositeScore}/100 (${technicals.signalTier.label})")
            appendLine("RSI(14): ${fmt(technicals.rsi14)} | Stochastic %K/%D: ${fmt(technicals.stochK)}/${fmt(technicals.stochD)} | MFI(14): ${fmt(technicals.mfi14)}")
            appendLine("MACD(12,26,9): ${fmt(technicals.macd)} | Signal: ${fmt(technicals.macdSignal)} | Hist: ${fmt(technicals.macdHistogram)}")
            appendLine("Supertrend(10,3): $curr${fmt(technicals.supertrend)} (${if (technicals.supertrendBullish) "Bullish" else "Bearish"}) | VWAP: $curr${fmt(technicals.vwap)}")
            if (tenkan != null && kijun != null) {
                appendLine("Ichimoku Tenkan/Kijun: $curr${fmt(tenkan)} / $curr${fmt(kijun)}")
            }
            if (squeezeMom != null) {
                appendLine("TTM Squeeze: ${if (squeezeOn == true) "COMPRESSION ON" else "FIRED"} | Momentum: ${fmt(squeezeMom)}")
            }
            appendLine("Smart Money Zones Detected: ${technicals.smartMoneyZones.size} (Bullish=${technicals.smartMoneyZones.count { it.isBullish }}, Bearish=${technicals.smartMoneyZones.count { !it.isBullish }})")
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
        }
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
