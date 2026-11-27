package com.example.quant

import com.example.data.model.ChartTradeMarker
import com.example.data.model.FibonacciLevels
import com.example.data.model.IndicatorSignalItem
import com.example.data.model.OhlcvBar
import com.example.data.model.PivotLevels
import com.example.data.model.SignalTier
import com.example.data.model.TechnicalAnalysisResult
import com.example.data.model.TradingViewQuote
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object TechnicalIndicatorEngine {

    fun analyze(
        symbol: String,
        bars: List<OhlcvBar>,
        tvQuote: TradingViewQuote? = null,
        currencySymbol: String = "$",
        isIntraday: Boolean = false
    ): TechnicalAnalysisResult {
        require(bars.isNotEmpty()) { "OHLCV bars required for technical analysis" }

        val closes = bars.map { it.close }
        val volumes = bars.map { it.volume.toDouble() }
        val currentPrice = tvQuote?.price?.takeIf { it > 0.0 } ?: closes.last()

        val ema9Series = emaSeries(closes, 9)
        val ema21Series = emaSeries(closes, 21)
        val ema50Series = emaSeries(closes, 50)
        val ema200Series = emaSeries(closes, 200)

        val sma20Series = smaSeries(closes, 20)
        val sma50Series = smaSeries(closes, 50)
        val sma200Series = smaSeries(closes, 200)

        val rsiSeries = rsiSeries(closes, 14)
        val (macdSeries, macdSignalSeries, macdHistSeries) = macdSeries(closes)
        val (bbUpperSeries, bbMiddleSeries, bbLowerSeries) = bollingerSeries(closes, 20, 2.0)
        val atrSeries = atrSeries(bars, 14)
        val (stochKSeries, stochDSeries) = stochasticSeries(bars, 14, 3)
        val adxSeries = adxSeries(bars, 14)

        val vwapSeries = vwapSeries(bars, isIntraday = isIntraday)
        val (supertrendSeries, supertrendBullSeries) = supertrendSeries(bars, 10, 3.0)
        val mfiSeries = mfiSeries(bars, 14)
        val cciSeries = cciSeries(bars, 20)
        val williamsSeries = williamsRSeries(bars, 14)
        val obvSeries = obvSeries(bars)

        val rsi14 = rsiSeries.lastOrNull { it != null } ?: tvQuote?.tvRsi ?: 50.0
        val macd = macdSeries.lastOrNull { it != null } ?: tvQuote?.tvMacd ?: 0.0
        val macdSignal = macdSignalSeries.lastOrNull { it != null } ?: tvQuote?.tvMacdSignal ?: 0.0
        val macdHist = macdHistSeries.lastOrNull { it != null } ?: (macd - macdSignal)

        val ema9 = ema9Series.lastOrNull { it != null } ?: currentPrice
        val ema21 = ema21Series.lastOrNull { it != null } ?: tvQuote?.tvEma20 ?: currentPrice
        val ema50 = ema50Series.lastOrNull { it != null } ?: currentPrice
        val ema200 = ema200Series.lastOrNull { it != null } ?: currentPrice

        val sma20 = sma20Series.lastOrNull { it != null } ?: currentPrice
        val sma50 = sma50Series.lastOrNull { it != null } ?: tvQuote?.tvSma50 ?: currentPrice
        val sma200 = sma200Series.lastOrNull { it != null } ?: tvQuote?.tvSma200 ?: currentPrice

        val bbUpper = bbUpperSeries.lastOrNull { it != null } ?: (currentPrice * 1.04)
        val bbMid = bbMiddleSeries.lastOrNull { it != null } ?: currentPrice
        val bbLower = bbLowerSeries.lastOrNull { it != null } ?: (currentPrice * 0.96)
        val bbWidth = (bbUpper - bbLower).coerceAtLeast(0.0001)
        val bbPercentB = ((currentPrice - bbLower) / bbWidth).coerceIn(-0.5, 1.5)

        val atr14 = (atrSeries.lastOrNull { it != null } ?: tvQuote?.tvAtr ?: (currentPrice * 0.02))
            .coerceAtLeast(currentPrice * 0.004)
        val stochK = stochKSeries.lastOrNull { it != null } ?: 50.0
        val stochD = stochDSeries.lastOrNull { it != null } ?: 50.0
        val adx14 = adxSeries.lastOrNull { it != null } ?: tvQuote?.tvAdx ?: 20.0

        val vwap = vwapSeries.lastOrNull { it != null } ?: currentPrice
        val supertrend = supertrendSeries.lastOrNull { it != null } ?: (currentPrice - 2.0 * atr14)
        val supertrendBullish = supertrendBullSeries.lastOrNull { it != null } ?: (currentPrice >= supertrend)
        val mfi14 = mfiSeries.lastOrNull { it != null } ?: 50.0
        val cci20 = cciSeries.lastOrNull { it != null } ?: 0.0
        val williamsR14 = williamsSeries.lastOrNull { it != null } ?: -50.0

        val obvRecent = obvSeries.takeLast(10)
        val obvBaseline = obvRecent.firstOrNull() ?: 0.0
        val obvLatest = obvRecent.lastOrNull() ?: 0.0
        val obvEma5 = obvSeries.takeLast(5).average()
        val obvEma14 = obvSeries.takeLast(14).average()
        val obvTrendBullish = obvLatest >= obvEma5 && obvEma5 >= obvEma14
        val obvChangePct = if (abs(obvBaseline) > 1.0) {
            ((obvLatest - obvBaseline) / abs(obvBaseline)) * 100.0
        } else 0.0

        val pivotLevels = computePivotLevels(bars, currentPrice)
        val fibLevels = computeFibonacciLevels(bars, currentPrice)

        val avgVol20 = volumes.takeLast(20).average().coerceAtLeast(1.0)
        val volumeRatio = (volumes.lastOrNull() ?: avgVol20) / avgVol20

        // Multi-factor technical confluence scoring (-100 to +100)
        var score = 0.0
        val breakdown = mutableListOf<IndicatorSignalItem>()

        // 1. RSI (14)
        val rsiStance = when {
            rsi14 < 30.0 -> { score += 16; "Oversold Reversal" to true }
            rsi14 in 45.0..65.0 -> { score += 10; "Bullish Momentum" to true }
            rsi14 > 72.0 -> { score -= 15; "Overbought Risk" to false }
            rsi14 < 45.0 -> { score -= 9; "Weak Momentum" to false }
            else -> { "Neutral" to null }
        }
        breakdown.add(
            IndicatorSignalItem(
                name = "RSI (14)",
                valueFormatted = String.format(Locale.US, "%.1f", rsi14),
                stance = rsiStance.first,
                bullish = rsiStance.second
            )
        )

        // 2. MACD (12, 26, 9)
        val macdBullish = macd > macdSignal && macdHist > 0
        val macdBearish = macd < macdSignal && macdHist < 0
        if (macdBullish) score += 14 else if (macdBearish) score -= 14
        breakdown.add(
            IndicatorSignalItem(
                name = "MACD (12,26,9)",
                valueFormatted = String.format(Locale.US, "%+.2f", macdHist),
                stance = when {
                    macdBullish -> "Bullish Crossover"
                    macdBearish -> "Bearish Divergence"
                    else -> "Consolidating"
                },
                bullish = if (macdBullish) true else if (macdBearish) false else null
            )
        )

        // 3. Supertrend (10, 3.0)
        if (supertrendBullish) score += 16 else score -= 16
        breakdown.add(
            IndicatorSignalItem(
                name = "Supertrend (10,3)",
                valueFormatted = "$currencySymbol${String.format(Locale.US, "%.2f", supertrend)}",
                stance = if (supertrendBullish) "Bullish Trailing Support" else "Bearish Resistance Cap",
                bullish = supertrendBullish
            )
        )

        // 4. EMA Ribbon (9 / 21 / 50)
        val emaAlignedBull = currentPrice > ema21 && ema21 > ema50
        val emaAlignedBear = currentPrice < ema21 && ema21 < ema50
        if (emaAlignedBull) score += 16 else if (emaAlignedBear) score -= 16
        breakdown.add(
            IndicatorSignalItem(
                name = "EMA 21 / 50",
                valueFormatted = "$currencySymbol${String.format(Locale.US, "%.2f", ema21)} / $currencySymbol${String.format(Locale.US, "%.2f", ema50)}",
                stance = when {
                    emaAlignedBull -> "Golden Alignment"
                    emaAlignedBear -> "Death Alignment"
                    else -> "Mixed Trend"
                },
                bullish = if (emaAlignedBull) true else if (emaAlignedBear) false else null
            )
        )

        // 5. VWAP Institutional Anchor
        val aboveVwap = currentPrice >= vwap
        if (aboveVwap) score += 8 else score -= 8
        breakdown.add(
            IndicatorSignalItem(
                name = "VWAP Benchmark",
                valueFormatted = "$currencySymbol${String.format(Locale.US, "%.2f", vwap)}",
                stance = if (aboveVwap) "Price Above VWAP" else "Price Below VWAP",
                bullish = aboveVwap
            )
        )

        // 6. Bollinger Bands (20, 2)
        val bbBull = bbPercentB < 0.25
        val bbBear = bbPercentB > 0.92
        if (bbBull) score += 8 else if (bbBear) score -= 8
        breakdown.add(
            IndicatorSignalItem(
                name = "Bollinger %B",
                valueFormatted = String.format(Locale.US, "%.0f%%", bbPercentB * 100.0),
                stance = when {
                    bbBull -> "Lower Band Support"
                    bbBear -> "Upper Band Stretch"
                    else -> "Inside Envelope"
                },
                bullish = if (bbBull) true else if (bbBear) false else null
            )
        )

        // 7. Stochastic (14, 3)
        val stochBull = stochK > stochD && stochK < 80
        val stochBear = stochK < stochD && stochK > 20
        if (stochBull) score += 7 else if (stochBear) score -= 7
        breakdown.add(
            IndicatorSignalItem(
                name = "Stochastic %K/%D",
                valueFormatted = String.format(Locale.US, "%.0f / %.0f", stochK, stochD),
                stance = if (stochBull) "Upward Cross" else if (stochBear) "Downward Cross" else "Neutral",
                bullish = if (stochBull) true else if (stochBear) false else null
            )
        )

        // 8. Money Flow Index (MFI 14)
        val mfiBull = mfi14 < 25.0 || (mfi14 in 50.0..75.0)
        val mfiBear = mfi14 > 80.0 || mfi14 < 40.0
        if (mfi14 < 25.0) score += 7 else if (mfi14 > 80.0) score -= 7
        breakdown.add(
            IndicatorSignalItem(
                name = "MFI (14) Flow",
                valueFormatted = String.format(Locale.US, "%.1f", mfi14),
                stance = when {
                    mfi14 < 25.0 -> "Oversold Inflow Zone"
                    mfi14 > 80.0 -> "Distribution Overheat"
                    mfi14 >= 50.0 -> "Positive Money Flow"
                    else -> "Weak Money Flow"
                },
                bullish = if (mfi14 >= 50.0 && mfi14 <= 80.0 || mfi14 < 25.0) true else if (mfiBear) false else null
            )
        )

        // 9. CCI (20)
        val cciStance = when {
            cci20 > 100.0 -> { score += 7; "Breakout Momentum" to true }
            cci20 < -100.0 -> { score += 5; "Oversold Bounce Setup" to true }
            cci20 < -20.0 -> { score -= 5; "Bearish Cycle" to false }
            else -> "Balanced Range" to null
        }
        breakdown.add(
            IndicatorSignalItem(
                name = "CCI (20)",
                valueFormatted = String.format(Locale.US, "%+.0f", cci20),
                stance = cciStance.first,
                bullish = cciStance.second
            )
        )

        // 10. Williams %R (14)
        val willStance = when {
            williamsR14 <= -80.0 -> "Oversold Zone" to true
            williamsR14 >= -20.0 -> "Overbought Zone" to false
            williamsR14 >= -50.0 -> "Bullish Half" to true
            else -> "Bearish Half" to false
        }
        breakdown.add(
            IndicatorSignalItem(
                name = "Williams %R (14)",
                valueFormatted = String.format(Locale.US, "%.1f", williamsR14),
                stance = willStance.first,
                bullish = willStance.second
            )
        )

        // 11. OBV Accumulation Trend
        if (obvTrendBullish) score += 6 else score -= 6
        breakdown.add(
            IndicatorSignalItem(
                name = "OBV Volume Flow",
                valueFormatted = String.format(Locale.US, "%+.1f%%", obvChangePct),
                stance = if (obvTrendBullish) "Accumulation" else "Distribution",
                bullish = obvTrendBullish
            )
        )

        // 12. ADX Trend Strength + TradingView Consensus
        val tvScore = (tvQuote?.tvRecommendAll ?: 0.0) * 16.0
        score += tvScore
        breakdown.add(
            IndicatorSignalItem(
                name = "ADX (14) Trend",
                valueFormatted = String.format(Locale.US, "%.1f", adx14),
                stance = when {
                    adx14 >= 25.0 && currentPrice >= ema21 -> "Strong Uptrend"
                    adx14 >= 25.0 && currentPrice < ema21 -> "Strong Downtrend"
                    else -> "Range-Bound"
                },
                bullish = if (adx14 >= 25.0) currentPrice >= ema21 else null
            )
        )

        val finalScore = score.toInt().coerceIn(-100, 100)
        val tier = when {
            finalScore >= 40 -> SignalTier.STRONG_BUY
            finalScore >= 15 -> SignalTier.BUY
            finalScore <= -40 -> SignalTier.STRONG_SELL
            finalScore <= -15 -> SignalTier.SELL
            else -> SignalTier.HOLD
        }

        // ATR-calibrated Entry Zone, Stop-Loss, and Take-Profit levels
        val entryLow = (minOf(currentPrice, ema21) - 0.35 * atr14).coerceAtLeast(0.01)
        val entryHigh = currentPrice + 0.15 * atr14
        val atrStop = (currentPrice - 1.8 * atr14).coerceAtLeast(0.01)
        val stopLoss = if (supertrendBullish && supertrend < currentPrice && supertrend > currentPrice * 0.85) {
            maxOf(atrStop, supertrend)
        } else {
            atrStop
        }
        val tp1 = currentPrice + 2.4 * atr14
        val tp2 = currentPrice + 4.2 * atr14
        val riskPerShare = (currentPrice - stopLoss).coerceAtLeast(0.01)
        val rewardPerShare = (tp1 - currentPrice).coerceAtLeast(0.01)
        val rrRatio = rewardPerShare / riskPerShare

        val tradeMarkers = detectChartTradeMarkers(
            bars = bars,
            ema9Series = ema9Series,
            ema21Series = ema21Series,
            rsiSeries = rsiSeries,
            macdHistSeries = macdHistSeries,
            supertrendBullSeries = supertrendBullSeries
        )

        return TechnicalAnalysisResult(
            symbol = symbol,
            currentPrice = currentPrice,
            rsi14 = rsi14,
            macd = macd,
            macdSignal = macdSignal,
            macdHistogram = macdHist,
            ema9 = ema9,
            ema21 = ema21,
            ema50 = ema50,
            ema200 = ema200,
            sma20 = sma20,
            sma50 = sma50,
            sma200 = sma200,
            bollingerUpper = bbUpper,
            bollingerMiddle = bbMid,
            bollingerLower = bbLower,
            bollingerPercentB = bbPercentB,
            atr14 = atr14,
            stochK = stochK,
            stochD = stochD,
            adx14 = adx14,
            volumeRatio = volumeRatio,
            vwap = vwap,
            supertrend = supertrend,
            supertrendBullish = supertrendBullish,
            mfi14 = mfi14,
            cci20 = cci20,
            williamsR14 = williamsR14,
            obvTrendBullish = obvTrendBullish,
            obvChangePct = obvChangePct,
            pivotLevels = pivotLevels,
            fibLevels = fibLevels,
            ema21Series = ema21Series,
            ema50Series = ema50Series,
            bollingerUpperSeries = bbUpperSeries,
            bollingerLowerSeries = bbLowerSeries,
            vwapSeries = vwapSeries,
            supertrendSeries = supertrendSeries,
            supertrendBullSeries = supertrendBullSeries,
            rsiSeries = rsiSeries,
            macdSeries = macdSeries,
            macdSignalSeries = macdSignalSeries,
            macdHistSeries = macdHistSeries,
            stochKSeries = stochKSeries,
            stochDSeries = stochDSeries,
            mfiSeries = mfiSeries,
            tradeMarkers = tradeMarkers,
            entryZoneLow = entryLow,
            entryZoneHigh = entryHigh,
            stopLossLevel = stopLoss,
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            riskRewardRatio = rrRatio,
            compositeScore = finalScore,
            signalTier = tier,
            breakdown = breakdown
        )
    }

    fun smaSeries(values: List<Double>, period: Int): List<Double?> {
        if (values.isEmpty()) return emptyList()
        val out = MutableList<Double?>(values.size) { null }
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out
    }

    fun emaSeries(values: List<Double>, period: Int): List<Double?> {
        if (values.isEmpty()) return emptyList()
        val out = MutableList<Double?>(values.size) { null }
        if (values.size < period) {
            // Short series fallback: progressive EMA
            var ema = values.first()
            val k = 2.0 / (period + 1)
            for (i in values.indices) {
                ema = values[i] * k + ema * (1.0 - k)
                out[i] = ema
            }
            return out
        }
        val k = 2.0 / (period + 1)
        var ema = values.take(period).average()
        out[period - 1] = ema
        for (i in period until values.size) {
            ema = values[i] * k + ema * (1.0 - k)
            out[i] = ema
        }
        return out
    }

    fun rsiSeries(closes: List<Double>, period: Int = 14): List<Double?> {
        val out = MutableList<Double?>(closes.size) { null }
        if (closes.size <= period) return out

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gainSum += diff else lossSum += -diff
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        out[period] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))

        for (i in (period + 1) until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }
        return out
    }

    fun macdSeries(closes: List<Double>): Triple<List<Double?>, List<Double?>, List<Double?>> {
        val ema12 = emaSeries(closes, 12)
        val ema26 = emaSeries(closes, 26)
        val macdLine = MutableList<Double?>(closes.size) { null }
        val nonNullMacd = mutableListOf<Double>()
        val nonNullIndices = mutableListOf<Int>()

        for (i in closes.indices) {
            val f = ema12[i]
            val s = ema26[i]
            if (f != null && s != null) {
                val m = f - s
                macdLine[i] = m
                nonNullMacd.add(m)
                nonNullIndices.add(i)
            }
        }

        val signalLine = MutableList<Double?>(closes.size) { null }
        val histLine = MutableList<Double?>(closes.size) { null }
        if (nonNullMacd.isNotEmpty()) {
            val sigRaw = emaSeries(nonNullMacd, 9)
            for (idx in nonNullIndices.indices) {
                val origIndex = nonNullIndices[idx]
                val sigVal = sigRaw[idx]
                signalLine[origIndex] = sigVal
                val mVal = macdLine[origIndex]
                if (mVal != null && sigVal != null) {
                    histLine[origIndex] = mVal - sigVal
                }
            }
        }
        return Triple(macdLine, signalLine, histLine)
    }

    fun bollingerSeries(
        closes: List<Double>,
        period: Int = 20,
        mult: Double = 2.0
    ): Triple<List<Double?>, List<Double?>, List<Double?>> {
        val mid = smaSeries(closes, period)
        val upper = MutableList<Double?>(closes.size) { null }
        val lower = MutableList<Double?>(closes.size) { null }

        for (i in (period - 1) until closes.size) {
            val m = mid[i] ?: continue
            var sqSum = 0.0
            for (j in (i - period + 1)..i) {
                val d = closes[j] - m
                sqSum += d * d
            }
            val std = sqrt(sqSum / period)
            upper[i] = m + mult * std
            lower[i] = m - mult * std
        }
        return Triple(upper, mid, lower)
    }

    fun atrSeries(bars: List<OhlcvBar>, period: Int = 14): List<Double?> {
        val out = MutableList<Double?>(bars.size) { null }
        if (bars.size <= period) return out
        val trs = DoubleArray(bars.size)
        trs[0] = bars[0].high - bars[0].low
        for (i in 1 until bars.size) {
            val h = bars[i].high
            val l = bars[i].low
            val prevClose = bars[i - 1].close
            trs[i] = max(h - l, max(abs(h - prevClose), abs(l - prevClose)))
        }
        var atr = trs.slice(1..period).average()
        out[period] = atr
        for (i in (period + 1) until bars.size) {
            atr = (atr * (period - 1) + trs[i]) / period
            out[i] = atr
        }
        return out
    }

    fun stochasticSeries(
        bars: List<OhlcvBar>,
        kPeriod: Int = 14,
        dPeriod: Int = 3
    ): Pair<List<Double?>, List<Double?>> {
        val kSeries = MutableList<Double?>(bars.size) { null }
        if (bars.size < kPeriod) return kSeries to kSeries

        for (i in (kPeriod - 1) until bars.size) {
            var highest = Double.NEGATIVE_INFINITY
            var lowest = Double.MAX_VALUE
            for (j in (i - kPeriod + 1)..i) {
                if (bars[j].high > highest) highest = bars[j].high
                if (bars[j].low < lowest) lowest = bars[j].low
            }
            val range = (highest - lowest).coerceAtLeast(0.0001)
            kSeries[i] = (((bars[i].close - lowest) / range) * 100.0).coerceIn(0.0, 100.0)
        }

        val dSeries = MutableList<Double?>(bars.size) { null }
        for (i in (kPeriod - 1 + dPeriod - 1) until bars.size) {
            val window = (0 until dPeriod).mapNotNull { kSeries[i - it] }
            if (window.size == dPeriod) dSeries[i] = window.average()
        }
        return kSeries to dSeries
    }

    fun adxSeries(bars: List<OhlcvBar>, period: Int = 14): List<Double?> {
        val out = MutableList<Double?>(bars.size) { null }
        if (bars.size <= period * 2) return out

        val plusDm = DoubleArray(bars.size)
        val minusDm = DoubleArray(bars.size)
        val tr = DoubleArray(bars.size)
        for (i in 1 until bars.size) {
            val upMove = bars[i].high - bars[i - 1].high
            val downMove = bars[i - 1].low - bars[i].low
            plusDm[i] = if (upMove > downMove && upMove > 0) upMove else 0.0
            minusDm[i] = if (downMove > upMove && downMove > 0) downMove else 0.0
            val prevClose = bars[i - 1].close
            tr[i] = max(bars[i].high - bars[i].low, max(abs(bars[i].high - prevClose), abs(bars[i].low - prevClose)))
        }

        var smTr = tr.slice(1..period).sum()
        var smPlus = plusDm.slice(1..period).sum()
        var smMinus = minusDm.slice(1..period).sum()
        val dxList = mutableListOf<Double>()

        for (i in period until bars.size) {
            if (i > period) {
                smTr = smTr - (smTr / period) + tr[i]
                smPlus = smPlus - (smPlus / period) + plusDm[i]
                smMinus = smMinus - (smMinus / period) + minusDm[i]
            }
            if (smTr > 0.0) {
                val pdi = 100.0 * (smPlus / smTr)
                val mdi = 100.0 * (smMinus / smTr)
                val denom = (pdi + mdi).coerceAtLeast(0.0001)
                dxList.add(100.0 * abs(pdi - mdi) / denom)
            } else {
                dxList.add(0.0)
            }
            if (dxList.size >= period) {
                out[i] = dxList.takeLast(period).average()
            }
        }
        return out
    }

    fun vwapSeries(
        bars: List<OhlcvBar>,
        rollingWindow: Int = 20,
        isIntraday: Boolean = false
    ): List<Double?> {
        val out = MutableList<Double?>(bars.size) { null }
        if (bars.isEmpty()) return out
        var cumTpVol = 0.0
        var cumVol = 0.0
        if (isIntraday) {
            for (i in bars.indices) {
                if (i > 0 && (bars[i].timestampSec - bars[i - 1].timestampSec) >= 10_800L) {
                    cumTpVol = 0.0
                    cumVol = 0.0
                }
                val tp = (bars[i].high + bars[i].low + bars[i].close) / 3.0
                val v = bars[i].volume.toDouble().coerceAtLeast(1.0)
                cumTpVol += tp * v
                cumVol += v
                out[i] = cumTpVol / cumVol.coerceAtLeast(1.0)
            }
            return out
        }
        val tpVols = DoubleArray(bars.size)
        val vols = DoubleArray(bars.size)
        for (i in bars.indices) {
            val tp = (bars[i].high + bars[i].low + bars[i].close) / 3.0
            val v = bars[i].volume.toDouble().coerceAtLeast(1.0)
            tpVols[i] = tp * v
            vols[i] = v
            cumTpVol += tpVols[i]
            cumVol += v
            if (i >= rollingWindow) {
                cumTpVol -= tpVols[i - rollingWindow]
                cumVol -= vols[i - rollingWindow]
            }
            out[i] = cumTpVol / cumVol.coerceAtLeast(1.0)
        }
        return out
    }

    fun supertrendSeries(
        bars: List<OhlcvBar>,
        period: Int = 10,
        multiplier: Double = 3.0
    ): Pair<List<Double?>, List<Boolean?>> {
        val stOut = MutableList<Double?>(bars.size) { null }
        val bullOut = MutableList<Boolean?>(bars.size) { null }
        if (bars.size <= period) return stOut to bullOut

        val atr = atrSeries(bars, period)
        val finalUpper = DoubleArray(bars.size)
        val finalLower = DoubleArray(bars.size)
        var inUptrend = true

        for (i in bars.indices) {
            val a = atr[i] ?: continue
            val hl2 = (bars[i].high + bars[i].low) / 2.0
            val basicUpper = hl2 + multiplier * a
            val basicLower = hl2 - multiplier * a

            if (i == 0 || atr[i - 1] == null) {
                finalUpper[i] = basicUpper
                finalLower[i] = basicLower
                inUptrend = bars[i].close >= hl2
            } else {
                finalUpper[i] = if (basicUpper < finalUpper[i - 1] || bars[i - 1].close > finalUpper[i - 1]) {
                    basicUpper
                } else {
                    finalUpper[i - 1]
                }
                finalLower[i] = if (basicLower > finalLower[i - 1] || bars[i - 1].close < finalLower[i - 1]) {
                    basicLower
                } else {
                    finalLower[i - 1]
                }

                inUptrend = if (inUptrend) {
                    bars[i].close >= finalLower[i]
                } else {
                    bars[i].close > finalUpper[i]
                }
            }
            stOut[i] = if (inUptrend) finalLower[i] else finalUpper[i]
            bullOut[i] = inUptrend
        }
        return stOut to bullOut
    }

    fun mfiSeries(bars: List<OhlcvBar>, period: Int = 14): List<Double?> {
        val out = MutableList<Double?>(bars.size) { null }
        if (bars.size <= period) return out

        val tp = DoubleArray(bars.size) { (bars[it].high + bars[it].low + bars[it].close) / 3.0 }
        val posFlow = DoubleArray(bars.size)
        val negFlow = DoubleArray(bars.size)

        for (i in 1 until bars.size) {
            val rawFlow = tp[i] * bars[i].volume.toDouble().coerceAtLeast(1.0)
            if (tp[i] > tp[i - 1]) {
                posFlow[i] = rawFlow
            } else if (tp[i] < tp[i - 1]) {
                negFlow[i] = rawFlow
            }
        }

        for (i in period until bars.size) {
            var posSum = 0.0
            var negSum = 0.0
            for (j in (i - period + 1)..i) {
                posSum += posFlow[j]
                negSum += negFlow[j]
            }
            out[i] = if (negSum <= 0.0) {
                100.0
            } else {
                val mr = posSum / negSum
                100.0 - (100.0 / (1.0 + mr))
            }
        }
        return out
    }

    fun cciSeries(bars: List<OhlcvBar>, period: Int = 20): List<Double?> {
        val out = MutableList<Double?>(bars.size) { null }
        if (bars.size < period) return out
        val tp = bars.map { (it.high + it.low + it.close) / 3.0 }
        val smaTp = smaSeries(tp, period)

        for (i in (period - 1) until bars.size) {
            val mean = smaTp[i] ?: continue
            var devSum = 0.0
            for (j in (i - period + 1)..i) {
                devSum += abs(tp[j] - mean)
            }
            val meanDev = (devSum / period).coerceAtLeast(0.0001)
            out[i] = (tp[i] - mean) / (0.015 * meanDev)
        }
        return out
    }

    fun williamsRSeries(bars: List<OhlcvBar>, period: Int = 14): List<Double?> {
        val out = MutableList<Double?>(bars.size) { null }
        if (bars.size < period) return out
        for (i in (period - 1) until bars.size) {
            var highest = Double.NEGATIVE_INFINITY
            var lowest = Double.MAX_VALUE
            for (j in (i - period + 1)..i) {
                highest = max(highest, bars[j].high)
                lowest = min(lowest, bars[j].low)
            }
            val range = (highest - lowest).coerceAtLeast(0.0001)
            out[i] = (((highest - bars[i].close) / range) * -100.0).coerceIn(-100.0, 0.0)
        }
        return out
    }

    fun obvSeries(bars: List<OhlcvBar>): List<Double> {
        if (bars.isEmpty()) return emptyList()
        val out = MutableList(bars.size) { 0.0 }
        for (i in 1 until bars.size) {
            val v = bars[i].volume.toDouble()
            out[i] = when {
                bars[i].close > bars[i - 1].close -> out[i - 1] + v
                bars[i].close < bars[i - 1].close -> out[i - 1] - v
                else -> out[i - 1]
            }
        }
        return out
    }

    fun computePivotLevels(bars: List<OhlcvBar>, currentPrice: Double): PivotLevels {
        val refBar = if (bars.size >= 2) bars[bars.size - 2] else bars.lastOrNull()
        if (refBar == null) {
            return PivotLevels(currentPrice, currentPrice * 1.02, currentPrice * 1.04, currentPrice * 0.98, currentPrice * 0.96)
        }
        val p = (refBar.high + refBar.low + refBar.close) / 3.0
        val r1 = 2.0 * p - refBar.low
        val s1 = 2.0 * p - refBar.high
        val r2 = p + (refBar.high - refBar.low)
        val s2 = p - (refBar.high - refBar.low)
        return PivotLevels(pivot = p, r1 = r1, r2 = r2, s1 = s1, s2 = s2)
    }

    fun computeFibonacciLevels(bars: List<OhlcvBar>, currentPrice: Double): FibonacciLevels {
        val window = bars.takeLast(60)
        val swingHigh = window.maxOfOrNull { it.high } ?: (currentPrice * 1.08)
        val swingLow = window.minOfOrNull { it.low } ?: (currentPrice * 0.92)
        val diff = (swingHigh - swingLow).coerceAtLeast(0.01)
        return FibonacciLevels(
            swingHigh = swingHigh,
            swingLow = swingLow,
            level236 = swingHigh - 0.236 * diff,
            level382 = swingHigh - 0.382 * diff,
            level500 = swingHigh - 0.500 * diff,
            level618 = swingHigh - 0.618 * diff,
            level786 = swingHigh - 0.786 * diff
        )
    }

    fun detectChartTradeMarkers(
        bars: List<OhlcvBar>,
        ema9Series: List<Double?>,
        ema21Series: List<Double?>,
        rsiSeries: List<Double?>,
        macdHistSeries: List<Double?>,
        supertrendBullSeries: List<Boolean?>
    ): List<ChartTradeMarker> {
        if (bars.size < 6) return emptyList()
        val markers = mutableListOf<ChartTradeMarker>()
        var lastBuyIdx = -10
        var lastSellIdx = -10

        for (i in 2 until bars.size) {
            val stPrev = supertrendBullSeries.getOrNull(i - 1)
            val stCur = supertrendBullSeries.getOrNull(i)
            val e9Prev = ema9Series.getOrNull(i - 1)
            val e9Cur = ema9Series.getOrNull(i)
            val e21Prev = ema21Series.getOrNull(i - 1)
            val e21Cur = ema21Series.getOrNull(i)
            val rsiPrev = rsiSeries.getOrNull(i - 1)
            val rsiCur = rsiSeries.getOrNull(i)
            val macdH = macdHistSeries.getOrNull(i)

            val stFlipBuy = stPrev == false && stCur == true
            val emaCrossBuy = e9Prev != null && e9Cur != null && e21Prev != null && e21Cur != null &&
                e9Prev <= e21Prev && e9Cur > e21Cur && (macdH == null || macdH >= 0.0) && (rsiCur == null || rsiCur < 70.0)
            val rsiBounceBuy = rsiPrev != null && rsiCur != null && rsiPrev < 33.0 && rsiCur >= 33.0 && bars[i].close > bars[i].open

            val stFlipSell = stPrev == true && stCur == false
            val emaCrossSell = e9Prev != null && e9Cur != null && e21Prev != null && e21Cur != null &&
                e9Prev >= e21Prev && e9Cur < e21Cur && (macdH == null || macdH <= 0.0)
            val rsiRejectSell = rsiPrev != null && rsiCur != null && rsiPrev > 74.0 && rsiCur <= 74.0 && bars[i].close < bars[i].open

            if ((stFlipBuy || emaCrossBuy || rsiBounceBuy) && (i - lastBuyIdx >= 4)) {
                val reason = when {
                    stFlipBuy -> "Supertrend Bullish Flip"
                    emaCrossBuy -> "EMA 9/21 Golden Cross"
                    else -> "RSI Oversold Reversal"
                }
                markers.add(
                    ChartTradeMarker(
                        barIndex = i,
                        timestampSec = bars[i].timestampSec,
                        isBuy = true,
                        price = bars[i].low,
                        reason = reason
                    )
                )
                lastBuyIdx = i
            } else if ((stFlipSell || emaCrossSell || rsiRejectSell) && (i - lastSellIdx >= 4)) {
                val reason = when {
                    stFlipSell -> "Supertrend Bearish Flip"
                    emaCrossSell -> "EMA 9/21 Death Cross"
                    else -> "RSI Overbought Rejection"
                }
                markers.add(
                    ChartTradeMarker(
                        barIndex = i,
                        timestampSec = bars[i].timestampSec,
                        isBuy = false,
                        price = bars[i].high,
                        reason = reason
                    )
                )
                lastSellIdx = i
            }
        }
        return markers
    }
}


