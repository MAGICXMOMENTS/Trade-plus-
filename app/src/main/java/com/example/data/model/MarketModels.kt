package com.example.data.model

enum class ChartTimeframe(
    val label: String,
    val yahooRange: String,
    val yahooInterval: String,
    val isIntraday: Boolean = false
) {
    MIN15("15m", "5d", "15m", isIntraday = true),
    H1("1h", "1mo", "1h", isIntraday = true),
    D1("1D", "1d", "5m", isIntraday = true),
    W1("1W", "5d", "30m", isIntraday = true),
    M1("1M", "1mo", "1d"),
    M3("3M", "3mo", "1d"),
    Y1("1Y", "1y", "1d"),
    Y5("5Y", "5y", "1wk");

    companion object {
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

enum class OscillatorType(val label: String) {
    RSI("RSI (14)"),
    MACD("MACD (12,26,9)"),
    STOCHASTIC("Stochastic"),
    MFI("MFI (14)")
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
