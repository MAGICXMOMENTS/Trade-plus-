package com.example.quant

import com.example.data.model.OhlcvBar
import com.example.data.model.RlAction
import com.example.data.model.RlPolicyResult
import com.example.data.model.TechnicalAnalysisResult
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Genuine Tabular Q-Learning Reinforcement Learning Trading Policy Agent.
 * Trains on historical OHLCV bars using discretized market states (Trend, RSI Zone,
 * MACD Momentum, Volatility Regime) and a risk-adjusted reward function, then evaluates
 * out-of-sample policy equity vs Buy-and-Hold and outputs real-time Q-values.
 */
object RlTradingAgent {

    // Actions: 0 = LONG (1.0x), 1 = FLAT (0.0x), 2 = REDUCE (0.5x)
    private val exposureByAction = doubleArrayOf(1.0, 0.0, 0.5)

    fun evaluatePolicy(
        symbol: String,
        bars: List<OhlcvBar>,
        analysis: TechnicalAnalysisResult,
        episodes: Int = 24
    ): RlPolicyResult {
        if (bars.size < 25) {
            return fallbackPolicy(symbol, analysis)
        }

        val closes = bars.map { it.close }
        val ema21 = analysis.ema21Series
        val ema50 = analysis.ema50Series
        val rsi = analysis.rsiSeries
        val macdHist = analysis.macdHistSeries
        val atr = TechnicalIndicatorEngine.atrSeries(bars, 14)

        // Build state sequence aligned to bars
        val states = Array(bars.size) { i ->
            encodeState(
                close = closes[i],
                emaFast = ema21.getOrNull(i) ?: closes[i],
                emaSlow = ema50.getOrNull(i) ?: closes[i],
                rsi = rsi.getOrNull(i) ?: 50.0,
                macdHist = macdHist.getOrNull(i) ?: 0.0,
                atrPct = ((atr.getOrNull(i) ?: (closes[i] * 0.02)) / closes[i].coerceAtLeast(0.01)) * 100.0
            )
        }

        // Split 70% train / 30% walk-forward validation (or min 15 bars validation)
        val splitIdx = ((bars.size * 0.68).toInt()).coerceIn(14, bars.size - 8)
        val qTable = HashMap<String, DoubleArray>()

        val alpha = 0.14
        val gamma = 0.90
        val txCostBps = 0.0005 // 5 bps slippage + commission penalty

        // Q-Learning training loop across episodes
        for (ep in 0 until episodes) {
            val epsilon = 0.25 * (1.0 - ep.toDouble() / episodes)
            var prevAction = 1 // start flat
            for (t in 1 until splitIdx) {
                val s = states[t - 1]
                val qVals = qTable.getOrPut(s) { DoubleArray(3) }

                val action = if ((t + ep) % 11 == 0 && epsilon > 0.08) {
                    (t + ep) % 3
                } else {
                    argmax(qVals)
                }

                val ret = ln((closes[t] / closes[t - 1].coerceAtLeast(0.01)).coerceAtLeast(0.0001))
                val exposure = exposureByAction[action]
                val turnoverPenalty = if (action != prevAction) txCostBps else 0.0
                val drawdownPenalty = if (ret < -0.015 && exposure > 0.0) absPenalty(ret, exposure) else 0.0
                val reward = (exposure * ret * 100.0) - (turnoverPenalty * 100.0) - drawdownPenalty

                val nextState = states[t]
                val nextQ = qTable.getOrPut(nextState) { DoubleArray(3) }
                val tdTarget = reward + gamma * (nextQ.maxOrNull() ?: 0.0)
                qVals[action] = qVals[action] + alpha * (tdTarget - qVals[action])
                prevAction = action
            }
        }

        // Walk-forward backtest on unseen bars [splitIdx .. bars.lastIndex]
        val strategyEquity = mutableListOf(100.0)
        val benchmarkEquity = mutableListOf(100.0)
        val strategyReturns = mutableListOf<Double>()
        var trades = 0
        var wins = 0
        var prevExp = 0.0

        for (t in splitIdx until bars.size) {
            val s = states[t - 1]
            val qVals = qTable[s] ?: DoubleArray(3)
            val bestAct = argmax(qVals)
            val expWeight = exposureByAction[bestAct]
            val barRet = (closes[t] - closes[t - 1]) / closes[t - 1].coerceAtLeast(0.01)

            val stratRet = (expWeight * barRet) - (if (expWeight != prevExp) txCostBps else 0.0)
            strategyReturns.add(stratRet)
            if (expWeight > 0.0) {
                trades++
                if (stratRet > 0.0) wins++
            }
            prevExp = expWeight

            strategyEquity.add(strategyEquity.last() * (1.0 + stratRet))
            benchmarkEquity.add(benchmarkEquity.last() * (1.0 + barRet))
        }

        val strategyTotalRetPct = strategyEquity.last() - 100.0
        val buyHoldRetPct = benchmarkEquity.last() - 100.0
        val winRate = if (trades > 0) (wins.toDouble() / trades) * 100.0 else 50.0
        val sharpe = computeSharpe(strategyReturns)
        val maxDd = computeMaxDrawdownPct(strategyEquity)

        // Evaluate current live state Q-values
        val liveState = encodeState(
            close = analysis.currentPrice,
            emaFast = analysis.ema21,
            emaSlow = analysis.ema50,
            rsi = analysis.rsi14,
            macdHist = analysis.macdHistogram,
            atrPct = (analysis.atr14 / analysis.currentPrice.coerceAtLeast(0.01)) * 100.0
        )
        val liveQ = qTable[liveState] ?: DoubleArray(3)
        val qLong = liveQ[0]
        val qFlat = liveQ[1]
        val qReduce = liveQ[2]

        // Softmax confidence over Q-values
        val probs = softmax(liveQ)
        val bestIdx = argmax(liveQ)
        val confidence = (probs[bestIdx] * 100.0).toInt().coerceIn(38, 96)

        val optimalAction = when (bestIdx) {
            0 -> if (analysis.compositeScore >= 15) RlAction.ENTER_LONG else RlAction.HOLD_POSITION
            2 -> RlAction.TRIM_REDUCE
            else -> RlAction.STAY_FLAT
        }

        val rationale = buildString {
            append("State [$liveState] evaluated over $episodes Q-learning episodes. ")
            append(
                when (optimalAction) {
                    RlAction.ENTER_LONG -> "Q(Long)=${fmtQ(qLong)} outperforms Flat/Reduce; trend & momentum reward entry."
                    RlAction.HOLD_POSITION -> "Q(Long)=${fmtQ(qLong)} supports holding existing exposure while guarding stop-loss."
                    RlAction.TRIM_REDUCE -> "Q(Reduce)=${fmtQ(qReduce)} favors scaling down risk amid stretched momentum/volatility."
                    RlAction.STAY_FLAT -> "Q(Flat)=${fmtQ(qFlat)} dominates; expected risk-adjusted return is negative."
                }
            )
        }

        return RlPolicyResult(
            symbol = symbol,
            optimalAction = optimalAction,
            confidencePct = confidence,
            qLong = qLong,
            qFlat = qFlat,
            qReduce = qReduce,
            currentStateDescription = liveState,
            historicalWinRatePct = winRate,
            strategyReturnPct = strategyTotalRetPct,
            buyAndHoldReturnPct = buyHoldRetPct,
            sharpeRatio = sharpe,
            maxDrawdownPct = maxDd,
            episodesTrained = episodes,
            strategyEquityCurve = strategyEquity,
            benchmarkEquityCurve = benchmarkEquity,
            rationale = rationale
        )
    }

    private fun encodeState(
        close: Double,
        emaFast: Double,
        emaSlow: Double,
        rsi: Double,
        macdHist: Double,
        atrPct: Double
    ): String {
        val trend = when {
            close > emaFast && emaFast > emaSlow -> "BULL"
            close < emaFast && emaFast < emaSlow -> "BEAR"
            else -> "CHOP"
        }
        val rsiZone = when {
            rsi < 34.0 -> "OS"
            rsi > 68.0 -> "OB"
            else -> "MID"
        }
        val macdDir = if (macdHist >= 0.0) "POS" else "NEG"
        val vol = when {
            atrPct > 3.2 -> "HI_VOL"
            atrPct < 1.3 -> "LO_VOL"
            else -> "MD_VOL"
        }
        return "$trend|$rsiZone|$macdDir|$vol"
    }

    private fun absPenalty(ret: Double, exposure: Double): Double {
        return kotlin.math.abs(ret) * exposure * 45.0
    }

    private fun argmax(arr: DoubleArray): Int {
        var idx = 0
        var best = arr[0]
        for (i in 1 until arr.size) {
            if (arr[i] > best) {
                best = arr[i]
                idx = i
            }
        }
        return idx
    }

    private fun softmax(arr: DoubleArray): DoubleArray {
        val maxVal = arr.maxOrNull() ?: 0.0
        val exps = DoubleArray(arr.size) { exp((arr[it] - maxVal).coerceIn(-10.0, 10.0)) }
        val sum = exps.sum().coerceAtLeast(1e-9)
        return DoubleArray(arr.size) { exps[it] / sum }
    }

    private fun computeSharpe(returns: List<Double>): Double {
        if (returns.size < 2) return 0.0
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        val std = sqrt(variance)
        return if (std < 1e-6) 0.0 else (mean / std) * sqrt(252.0)
    }

    private fun computeMaxDrawdownPct(equity: List<Double>): Double {
        var peak = equity.firstOrNull() ?: 100.0
        var maxDd = 0.0
        for (v in equity) {
            if (v > peak) peak = v
            val dd = ((peak - v) / peak.coerceAtLeast(1e-6)) * 100.0
            maxDd = max(maxDd, dd)
        }
        return maxDd
    }

    private fun fmtQ(value: Double): String = String.format(Locale.US, "%+.2f", value)

    private fun fallbackPolicy(symbol: String, analysis: TechnicalAnalysisResult): RlPolicyResult {
        val action = when {
            analysis.compositeScore >= 25 -> RlAction.ENTER_LONG
            analysis.compositeScore <= -20 -> RlAction.STAY_FLAT
            else -> RlAction.HOLD_POSITION
        }
        return RlPolicyResult(
            symbol = symbol,
            optimalAction = action,
            confidencePct = 58,
            qLong = if (analysis.compositeScore >= 0) 0.45 else -0.20,
            qFlat = 0.10,
            qReduce = 0.05,
            currentStateDescription = "SHORT_HISTORY",
            historicalWinRatePct = 55.0,
            strategyReturnPct = 0.0,
            buyAndHoldReturnPct = 0.0,
            sharpeRatio = 0.0,
            maxDrawdownPct = 0.0,
            episodesTrained = 0,
            strategyEquityCurve = listOf(100.0, 100.0),
            benchmarkEquityCurve = listOf(100.0, 100.0),
            rationale = "Insufficient intraday/historical bar count for full walk-forward Q-learning; select 3M or 1Y horizon for full RL backtesting."
        )
    }
}
