package com.example.data.model

enum class TimeframeCategory(val label: String) {
    ALL("All"),
    SCALP("Scalp"),
    INTRADAY("Intraday"),
    SWING("Swing"),
    MACRO("Position / Macro")
}

enum class ChartTimeframe(
    val label: String,
    val yahooRange: String,
    val yahooInterval: String,
    val isIntraday: Boolean = false,
    val category: TimeframeCategory = TimeframeCategory.SWING,
    val useCase: String = "",
    val aggregateGroupSize: Int = 1,
    val refreshIntervalMs: Long = 20_000L
) {
    MIN1(
        label = "1m",
        yahooRange = "1d",
        yahooInterval = "1m",
        isIntraday = true,
        category = TimeframeCategory.SCALP,
        useCase = "Scalping & micro tick entries (1d live)",
        refreshIntervalMs = 5_000L
    ),
    MIN5(
        label = "5m",
        yahooRange = "5d",
        yahooInterval = "5m",
        isIntraday = true,
        category = TimeframeCategory.SCALP,
        useCase = "Opening range & fast momentum (5d)",
        refreshIntervalMs = 5_000L
    ),
    MIN15(
        label = "15m",
        yahooRange = "5d",
        yahooInterval = "15m",
        isIntraday = true,
        category = TimeframeCategory.INTRADAY,
        useCase = "Intraday trend & VWAP setups (5d)",
        refreshIntervalMs = 8_000L
    ),
    MIN30(
        label = "30m",
        yahooRange = "1mo",
        yahooInterval = "30m",
        isIntraday = true,
        category = TimeframeCategory.INTRADAY,
        useCase = "Multi-session intraday structure (1m)",
        refreshIntervalMs = 10_000L
    ),
    H1(
        label = "1h",
        yahooRange = "1mo",
        yahooInterval = "60m",
        isIntraday = true,
        category = TimeframeCategory.INTRADAY,
        useCase = "Intraday-to-swing transitions (1m)",
        refreshIntervalMs = 12_000L
    ),
    H4(
        label = "4h",
        yahooRange = "3mo",
        yahooInterval = "60m",
        isIntraday = true,
        category = TimeframeCategory.SWING,
        useCase = "Clean multi-day swing structure (3m)",
        aggregateGroupSize = 4,
        refreshIntervalMs = 15_000L
    ),
    D1(
        label = "1D",
        yahooRange = "1d",
        yahooInterval = "5m",
        isIntraday = true,
        category = TimeframeCategory.INTRADAY,
        useCase = "Today's live session tape (5m feed)",
        refreshIntervalMs = 5_000L
    ),
    W1(
        label = "1W",
        yahooRange = "5d",
        yahooInterval = "30m",
        isIntraday = true,
        category = TimeframeCategory.SWING,
        useCase = "This week's 30m price progression",
        refreshIntervalMs = 15_000L
    ),
    M1(
        label = "1M",
        yahooRange = "1mo",
        yahooInterval = "1d",
        category = TimeframeCategory.SWING,
        useCase = "1-month daily swing momentum",
        refreshIntervalMs = 20_000L
    ),
    M3(
        label = "3M",
        yahooRange = "3mo",
        yahooInterval = "1d",
        category = TimeframeCategory.SWING,
        useCase = "Quarterly technical & RL setups",
        refreshIntervalMs = 20_000L
    ),
    M6(
        label = "6M",
        yahooRange = "6mo",
        yahooInterval = "1d",
        category = TimeframeCategory.MACRO,
        useCase = "Half-year intermediate trend",
        refreshIntervalMs = 30_000L
    ),
    YTD(
        label = "YTD",
        yahooRange = "ytd",
        yahooInterval = "1d",
        category = TimeframeCategory.MACRO,
        useCase = "Year-to-date institutional benchmark",
        refreshIntervalMs = 30_000L
    ),
    Y1(
        label = "1Y",
        yahooRange = "1y",
        yahooInterval = "1d",
        category = TimeframeCategory.MACRO,
        useCase = "1-year primary trend & SMA200",
        refreshIntervalMs = 30_000L
    ),
    Y5(
        label = "5Y",
        yahooRange = "5y",
        yahooInterval = "1wk",
        category = TimeframeCategory.MACRO,
        useCase = "5-year weekly macro cycle",
        refreshIntervalMs = 60_000L
    ),
    MAX(
        label = "MAX",
        yahooRange = "max",
        yahooInterval = "1mo",
        category = TimeframeCategory.MACRO,
        useCase = "All-time secular market structure",
        refreshIntervalMs = 60_000L
    );

    companion object {
        val ALL_LABELS: Set<String> = entries.map { it.label }.toSet()

        fun fromLabel(label: String): ChartTimeframe =
            entries.firstOrNull { it.label == label } ?: M3
    }
}

enum class SignalTier(val label: String) {
    STRONG_BUY("STRONG BUY"),
    BUY("BUY"),
    HOLD("HOLD"),
    SELL("SELL"),
    STRONG_SELL("STRONG SELL")
}

enum class RlAction(val label: String, val guidance: String) {
    ENTER_LONG("ENTER / ACCUMULATE", "Q-policy favors long exposure on trend & momentum edge."),
    HOLD_POSITION("HOLD POSITION", "Maintain current allocation; trend intact with controlled risk."),
    TRIM_REDUCE("TRIM / PARTIAL EXIT", "Take partial profits; elevated overbought or volatility risk."),
    STAY_FLAT("EXIT / STAY FLAT", "Capital preservation mode; unfavorable risk-adjusted reward.")
}

data class OhlcvBar(
    val timestampSec: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class TradingViewQuote(
    val symbol: String,
    val exchange: String,
    val companyName: String,
    val sector: String,
    val price: Double,
    val changePct: Double,
    val volume: Long,
    val avgVolume10d: Long,
    val marketCap: Double,
    val tvRecommendAll: Double,
    val tvRecommendMa: Double,
    val tvRecommendOsc: Double,
    val tvRsi: Double?,
    val tvMacd: Double?,
    val tvMacdSignal: Double?,
    val tvEma20: Double?,
    val tvSma50: Double?,
    val tvSma200: Double?,
    val tvAdx: Double?,
    val tvAtr: Double?,
    val high52w: Double?,
    val low52w: Double?,
    val sentimentScore: Int,
    val sentimentLabel: String,
    val fetchedAtMs: Long = System.currentTimeMillis()
)

data class SymbolSearchResult(
    val symbol: String,
    val exchange: String,
    val description: String,
    val type: String
)

enum class OscillatorType(val label: String, val isPremium: Boolean = false) {
    RSI("RSI (14)"),
    MACD("MACD (12,26,9)"),
    STOCHASTIC("Stochastic"),
    MFI("MFI (14)"),
    SQUEEZE_MOMENTUM("PRO: Squeeze", isPremium = true)
}

data class PivotLevels(
    val pivot: Double,
    val r1: Double,
    val r2: Double,
    val s1: Double,
    val s2: Double
)

data class FibonacciLevels(
    val swingHigh: Double,
    val swingLow: Double,
    val level236: Double,
    val level382: Double,
    val level500: Double,
    val level618: Double,
    val level786: Double
)

data class IndicatorSignalItem(
    val name: String,
    val valueFormatted: String,
    val stance: String,
    val bullish: Boolean?
)

data class ChartTradeMarker(
    val barIndex: Int,
    val timestampSec: Long,
    val isBuy: Boolean,
    val price: Double,
    val reason: String
)

enum class SmartMoneyZoneType(val tag: String) {
    BULLISH_FVG("FVG+"),
    BEARISH_FVG("FVG-"),
    BULLISH_OB("OB+"),
    BEARISH_OB("OB-")
}

data class SmartMoneyZone(
    val startBarIndex: Int,
    val endBarIndex: Int,
    val lowPrice: Double,
    val highPrice: Double,
    val isBullish: Boolean,
    val type: SmartMoneyZoneType
)

data class VolumeProfileBin(
    val lowPrice: Double,
    val highPrice: Double,
    val bullVolume: Double,
    val bearVolume: Double
) {
    val totalVolume: Double get() = bullVolume + bearVolume
}

data class VolumeProfileResult(
    val bins: List<VolumeProfileBin>,
    val pocPrice: Double,
    val vahPrice: Double,
    val valPrice: Double,
    val maxBucketVolume: Double
)

data class TechnicalAnalysisResult(
    val symbol: String,
    val currentPrice: Double,
    val rsi14: Double,
    val macd: Double,
    val macdSignal: Double,
    val macdHistogram: Double,
    val ema9: Double,
    val ema21: Double,
    val ema50: Double,
    val ema200: Double,
    val sma20: Double,
    val sma50: Double,
    val sma200: Double,
    val bollingerUpper: Double,
    val bollingerMiddle: Double,
    val bollingerLower: Double,
    val bollingerPercentB: Double,
    val atr14: Double,
    val stochK: Double,
    val stochD: Double,
    val adx14: Double,
    val volumeRatio: Double,
    val vwap: Double,
    val supertrend: Double,
    val supertrendBullish: Boolean,
    val mfi14: Double,
    val cci20: Double,
    val williamsR14: Double,
    val obvTrendBullish: Boolean,
    val obvChangePct: Double,
    val pivotLevels: PivotLevels,
    val fibLevels: FibonacciLevels,
    val ema21Series: List<Double?>,
    val ema50Series: List<Double?>,
    val bollingerUpperSeries: List<Double?>,
    val bollingerLowerSeries: List<Double?>,
    val vwapSeries: List<Double?>,
    val supertrendSeries: List<Double?>,
    val supertrendBullSeries: List<Boolean?>,
    val rsiSeries: List<Double?>,
    val macdSeries: List<Double?>,
    val macdSignalSeries: List<Double?>,
    val macdHistSeries: List<Double?>,
    val stochKSeries: List<Double?>,
    val stochDSeries: List<Double?>,
    val mfiSeries: List<Double?>,
    val ichimokuTenkanSeries: List<Double?> = emptyList(),
    val ichimokuKijunSeries: List<Double?> = emptyList(),
    val ichimokuSenkouASeries: List<Double?> = emptyList(),
    val ichimokuSenkouBSeries: List<Double?> = emptyList(),
    val squeezeMomentumSeries: List<Double?> = emptyList(),
    val squeezeOnSeries: List<Boolean?> = emptyList(),
    val smartMoneyZones: List<SmartMoneyZone> = emptyList(),
    val tradeMarkers: List<ChartTradeMarker>,
    val entryZoneLow: Double,
    val entryZoneHigh: Double,
    val stopLossLevel: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val riskRewardRatio: Double,
    val compositeScore: Int,
    val signalTier: SignalTier,
    val breakdown: List<IndicatorSignalItem>
)

data class RlPolicyResult(
    val symbol: String,
    val optimalAction: RlAction,
    val confidencePct: Int,
    val qLong: Double,
    val qFlat: Double,
    val qReduce: Double,
    val currentStateDescription: String,
    val historicalWinRatePct: Double,
    val strategyReturnPct: Double,
    val buyAndHoldReturnPct: Double,
    val sharpeRatio: Double,
    val maxDrawdownPct: Double,
    val episodesTrained: Int,
    val strategyEquityCurve: List<Double>,
    val benchmarkEquityCurve: List<Double>,
    val rationale: String
)

enum class PredictionDirectionVote(val label: String) {
    BULLISH("BULLISH"),
    NEUTRAL("NEUTRAL"),
    BEARISH("BEARISH")
}

data class AiAgentPrediction(
    val agentId: String,
    val agentName: String,
    val specialty: String,
    val direction: PredictionDirectionVote,
    val upProbabilityPct: Int,
    val confidencePercent: Int,
    val targetLow: Double,
    val targetBase: Double,
    val targetHigh: Double,
    val keySignal: String,
    val horizonNote: String,
    val weight: Double
)

data class AiForecastResult(
    val symbol: String,
    val shortTermBias: String,
    val mediumTermBias: String,
    val longTermThesis: String,
    val projectedTargetLow: Double,
    val projectedTargetBase: Double,
    val projectedTargetHigh: Double,
    val confidencePercent: Int,
    val sentimentScore: Int,
    val sentimentLabel: String,
    val keyCatalysts: List<String>,
    val keyRiskFactors: List<String>,
    val entryExitGuidance: String,
    val riskManagementAdvice: String,
    val agentPredictions: List<AiAgentPrediction> = emptyList(),
    val consensusVerdict: String = "",
    val agreementPercent: Int = 0,
    val bullishVotes: Int = 0,
    val neutralVotes: Int = 0,
    val bearishVotes: Int = 0,
    val ensembleSpreadPct: Double = 0.0,
    val comparisonSummary: String = "",
    val recommendedAction: String = "",
    val generatedAtMs: Long = System.currentTimeMillis()
)

enum class AlertSeverity {
    CRITICAL,
    WARNING,
    INFO
}

data class TriggeredRiskAlert(
    val id: String,
    val symbol: String,
    val title: String,
    val detail: String,
    val severity: AlertSeverity
)

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Streaming(val partialText: String) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
