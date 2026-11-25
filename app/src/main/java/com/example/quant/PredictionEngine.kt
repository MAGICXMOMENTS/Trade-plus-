package com.example.quant

import com.example.data.model.OhlcvBar
import com.example.data.model.RlAction
import com.example.data.model.RlPolicyResult
import com.example.data.model.TechnicalAnalysisResult
import com.example.data.model.TradingViewQuote
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class PredictionDirection(val label: String) {
    STRONG_UP("STRONG UP"),
    UP("BULLISH UP"),
    DOWN("BEARISH DOWN"),
    STRONG_DOWN("STRONG DOWN")
}

data class TradeSuggestion(
    val title: String,
    val detail: String,
    val actionLabel: String,
    val bullish: Boolean?
)

data class StrongPredictionResult(
    val symbol: String,
    val direction: PredictionDirection,
    val isUp: Boolean,
    val upProbabilityPct: Int,
    val convictionScore: Int, // 0..100
    val target7d: Double,
    val target30d: Double,
    val stopLossPrice: Double,
    val whenToBuy: String,
    val whenToSell: String,
    val buyNowRecommended: Boolean,
    val primaryAction: String,
    val headlineSummary: String,
    val suggestions: List<TradeSuggestion>
)

object PredictionEngine {

    fun predict(
        symbol: String,
        bars: List<OhlcvBar>,
        tech: TechnicalAnalysisResult,
        rl: RlPolicyResult,
        quote: TradingViewQuote? = null,
        currencySymbol: String = "$"
    ): StrongPredictionResult {
        val price = tech.currentPrice.coerceAtLeast(0.01)
        val closes = bars.map { it.close }

        // 1. Linear regression slope & R^2 over last 20 bars
        val regWindow = closes.takeLast(min(20, closes.size))
        val (slopePerBar, r2) = linearRegression(regWindow)
        val normalizedSlopePct = (slopePerBar / price) * 100.0

        // 2. Momentum & Regime Confluence (-100 to +100)
        var ensembleScore = tech.compositeScore * 0.55

        val regSignal = (normalizedSlopePct * 18.0 * (0.4 + 0.6 * r2)).coerceIn(-25.0, 25.0)
        ensembleScore += regSignal

        val rlEdge = (rl.qLong - max(rl.qFlat, rl.qReduce)) * 20.0
        ensembleScore += rlEdge.coerceIn(-18.0, 18.0)

        if (tech.supertrendBullish && price > tech.vwap) ensembleScore += 8.0
        if (!tech.supertrendBullish && price < tech.vwap) ensembleScore -= 8.0
        if (tech.obvTrendBullish && tech.volumeRatio >= 1.1) ensembleScore += 6.0
        if (!tech.obvTrendBullish && tech.volumeRatio >= 1.1) ensembleScore -= 6.0

        if (quote != null) {
            ensembleScore += (quote.tvRecommendAll * 14.0).coerceIn(-14.0, 14.0)
        }

        val clamped = ensembleScore.coerceIn(-100.0, 100.0)
        val isUp = clamped >= 0.0
        val direction = when {
            clamped >= 34.0 -> PredictionDirection.STRONG_UP
            clamped >= 0.0 -> PredictionDirection.UP
            clamped <= -34.0 -> PredictionDirection.STRONG_DOWN
            else -> PredictionDirection.DOWN
        }

        val upProb = (50.0 + clamped * 0.42).toInt().coerceIn(8, 94)
        val conviction = (abs(clamped) * 0.65 + r2 * 20.0 + (rl.confidencePct * 0.15))
            .toInt()
            .coerceIn(35, 96)

        val atrPct = (tech.atr14 / price).coerceIn(0.006, 0.08)
        val strengthFactor = (0.45 + abs(clamped) / 100.0).coerceIn(0.45, 1.35)
        val move7d = price * atrPct * 2.1 * strengthFactor
        val move30d = price * atrPct * 4.4 * strengthFactor

        val target7d = if (isUp) {
            max(price + move7d, tech.takeProfit1)
        } else {
            (price - move7d).coerceAtLeast(price * 0.65)
        }
        val target30d = if (isUp) {
            max(price + move30d, tech.takeProfit2)
        } else {
            (price - move30d).coerceAtLeast(price * 0.50)
        }

        val buyNowRecommended = isUp && tech.rsi14 < 70.0 && (tech.supertrendBullish || tech.rsi14 < 38.0)

        val whenToBuy = when {
            direction == PredictionDirection.STRONG_UP && tech.rsi14 < 70.0 ->
                "BUY NOW inside $currencySymbol${fmt(tech.entryZoneLow)}–$currencySymbol${fmt(tech.entryZoneHigh)} (trend, Supertrend & RL aligned)"
            isUp && tech.rsi14 >= 70.0 ->
                "WAIT PULLBACK TO BUY near VWAP $currencySymbol${fmt(tech.vwap)} or EMA21 $currencySymbol${fmt(tech.ema21)} (RSI overheated)"
            isUp ->
                "ACCUMULATE ON DIP near $currencySymbol${fmt(tech.entryZoneLow)} or on breakout above R1 $currencySymbol${fmt(tech.pivotLevels.r1)}"
            else ->
                "WAIT TO BUY — Do not enter long until price reclaims Supertrend $currencySymbol${fmt(tech.supertrend)} & EMA21 $currencySymbol${fmt(tech.ema21)}"
        }

        val whenToSell = when {
            !isUp ->
                "SELL / EXIT NOW on rallies into $currencySymbol${fmt(tech.ema21)}–$currencySymbol${fmt(tech.pivotLevels.r1)}; hard exit if price drops under SL $currencySymbol${fmt(tech.stopLossLevel)}"
            tech.rsi14 >= 72.0 ->
                "TRIM / SELL 50% NOW near overbought R1 $currencySymbol${fmt(tech.pivotLevels.r1)}; sell remainder at TP2 $currencySymbol${fmt(target30d)} or below SL $currencySymbol${fmt(tech.stopLossLevel)}"
            else ->
                "SELL 50% at TP1 $currencySymbol${fmt(target7d)}, trail remaining shares to TP2 $currencySymbol${fmt(target30d)}; exit immediately on break below SL $currencySymbol${fmt(tech.stopLossLevel)}"
        }

        val primaryAction = when {
            direction == PredictionDirection.STRONG_UP && tech.rsi14 < 72.0 ->
                "BUY / ACCUMULATE near $currencySymbol${fmt(tech.entryZoneLow)}–$currencySymbol${fmt(tech.entryZoneHigh)}"
            direction == PredictionDirection.UP && tech.rsi14 >= 72.0 ->
                "HOLD / WAIT PULLBACK to VWAP ($currencySymbol${fmt(tech.vwap)})"
            direction == PredictionDirection.UP ->
                "ACCUMULATE ON DIPS above SL $currencySymbol${fmt(tech.stopLossLevel)}"
            direction == PredictionDirection.STRONG_DOWN ->
                "SELL / PROTECT CAPITAL — Hard Stop $currencySymbol${fmt(tech.stopLossLevel)}"
            else ->
                "TRIM / STAY DEFENSIVE below EMA21 ($currencySymbol${fmt(tech.ema21)})"
        }

        val headlineSummary = buildString {
            append(if (isUp) "Upward trajectory favored ($upProb% prob)" else "Downward pressure favored (${100 - upProb}% prob)")
            append(" • Trend R²=${String.format(Locale.US, "%.2f", r2)}")
            append(" • RL: ${rl.optimalAction.label}")
        }

        val suggestions = buildSuggestions(
            price = price,
            tech = tech,
            rl = rl,
            isUp = isUp,
            r2 = r2,
            target7d = target7d,
            currency = currencySymbol
        )

        return StrongPredictionResult(
            symbol = symbol,
            direction = direction,
            isUp = isUp,
            upProbabilityPct = upProb,
            convictionScore = conviction,
            target7d = target7d,
            target30d = target30d,
            stopLossPrice = tech.stopLossLevel,
            whenToBuy = whenToBuy,
            whenToSell = whenToSell,
            buyNowRecommended = buyNowRecommended,
            primaryAction = primaryAction,
            headlineSummary = headlineSummary,
            suggestions = suggestions
        )
    }

    private fun buildSuggestions(
        price: Double,
        tech: TechnicalAnalysisResult,
        rl: RlPolicyResult,
        isUp: Boolean,
        r2: Double,
        target7d: Double,
        currency: String
    ): List<TradeSuggestion> {
        val list = mutableListOf<TradeSuggestion>()

        if (isUp) {
            val slPct = ((tech.stopLossLevel - price) / price) * 100.0
            list.add(
                TradeSuggestion(
                    title = "Optimal Entry & Stop Protection",
                    detail = "Scale in between $currency${fmt(tech.entryZoneLow)} and $currency${fmt(tech.entryZoneHigh)}. Place protective stop-loss at $currency${fmt(tech.stopLossLevel)} (${String.format(Locale.US, "%.1f%%", slPct)}) with first sell target at $currency${fmt(target7d)}.",
                    actionLabel = "1:${fmt(tech.riskRewardRatio)} R:R",
                    bullish = true
                )
            )
        } else {
            list.add(
                TradeSuggestion(
                    title = "Capital Preservation & Exit Guard",
                    detail = "Bearish regime active (${if (!tech.supertrendBullish) "below Supertrend" else "weak momentum"}). Avoid fresh longs until price reclaims VWAP $currency${fmt(tech.vwap)} and EMA21 $currency${fmt(tech.ema21)}; enforce stop at $currency${fmt(tech.stopLossLevel)}.",
                    actionLabel = "DEFENSIVE",
                    bullish = false
                )
            )
        }

        when {
            tech.rsi14 < 34.0 || tech.mfi14 < 25.0 -> list.add(
                TradeSuggestion(
                    title = "Oversold Mean-Reversion Watch",
                    detail = "RSI (${fmt(tech.rsi14)}) & MFI (${fmt(tech.mfi14)}) sit in oversold exhaustion. Look for a bullish candle close above $currency${fmt(tech.ema9)} before entering.",
                    actionLabel = "OVERSOLD",
                    bullish = true
                )
            )
            tech.rsi14 > 72.0 || tech.williamsR14 > -15.0 -> list.add(
                TradeSuggestion(
                    title = "Overbought Trim / Profit Lock",
                    detail = "RSI (${fmt(tech.rsi14)}) is stretched. Trail stop up to Supertrend ($currency${fmt(tech.supertrend)}) or book partial profits near R1 ($currency${fmt(tech.pivotLevels.r1)}).",
                    actionLabel = "TRAIL STOP",
                    bullish = null
                )
            )
            else -> list.add(
                TradeSuggestion(
                    title = "Institutional Flow & Trend Quality",
                    detail = "Price is ${if (price >= tech.vwap) "above" else "below"} VWAP ($currency${fmt(tech.vwap)}) with ${if (tech.obvTrendBullish) "rising OBV accumulation" else "soft OBV volume"} and regression R²=${String.format(Locale.US, "%.2f", r2)}.",
                    actionLabel = if (tech.obvTrendBullish) "INFLOW" else "OUTFLOW",
                    bullish = tech.obvTrendBullish
                )
            )
        }

        val rlSummary = when (rl.optimalAction) {
            RlAction.ENTER_LONG -> "Q-Policy favors long exposure (${rl.confidencePct}% conf, ${String.format(Locale.US, "%.0f%%", rl.historicalWinRatePct)} backtest win rate)."
            RlAction.HOLD_POSITION -> "Q-Policy recommends holding existing exposure while guarding S1 support at $currency${fmt(tech.pivotLevels.s1)}."
            RlAction.TRIM_REDUCE -> "Q-Policy detects elevated reward-to-risk decay; trim into R1 resistance ($currency${fmt(tech.pivotLevels.r1)})."
            RlAction.STAY_FLAT -> "Q-Policy stays flat (${String.format(Locale.US, "%.2f", rl.sharpeRatio)} Sharpe); wait for Fib 61.8% support ($currency${fmt(tech.fibLevels.level618)}) stabilization."
        }
        list.add(
            TradeSuggestion(
                title = "RL Policy & Key Level Playbook",
                detail = rlSummary,
                actionLabel = rl.optimalAction.label.substringBefore(" "),
                bullish = rl.optimalAction == RlAction.ENTER_LONG || rl.optimalAction == RlAction.HOLD_POSITION
            )
        )

        return list
    }

    private fun linearRegression(values: List<Double>): Pair<Double, Double> {
        val n = values.size
        if (n < 3) return 0.0 to 0.0

        val meanX = (n - 1) / 2.0
        val meanY = values.average()
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0

        for (i in values.indices) {
            val dx = i - meanX
            val dy = values[i] - meanY
            sxx += dx * dx
            sxy += dx * dy
            syy += dy * dy
        }
        if (sxx <= 0.0) return 0.0 to 0.0
        val slope = sxy / sxx
        val r2 = if (syy > 0.0) ((sxy * sxy) / (sxx * syy)).coerceIn(0.0, 1.0) else 0.0
        return slope to r2
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
}
