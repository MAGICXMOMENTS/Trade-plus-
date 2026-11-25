package com.example

import com.example.data.model.OhlcvBar
import com.example.quant.PredictionEngine
import com.example.quant.RlTradingAgent
import com.example.quant.TechnicalIndicatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class QuantEngineUnitTest {

    private fun generateDeterministicUptrendBars(count: Int = 120): List<OhlcvBar> {
        var price = 100.0
        return (0 until count).map { i ->
            val drift = 0.45 + 1.2 * sin(i / 6.0)
            val open = price
            val close = (price + drift).coerceAtLeast(10.0)
            val high = maxOf(open, close) + 1.1
            val low = minOf(open, close) - 0.9
            price = close
            OhlcvBar(
                timestampSec = 1_700_000_000L + i * 86_400L,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = 1_500_000L + (i * 10_000L)
            )
        }
    }

    @Test
    fun technicalIndicatorsAndTradeLevelsAreValid() {
        val bars = generateDeterministicUptrendBars()
        val res = TechnicalIndicatorEngine.analyze("NVDA", bars)

        assertEquals("NVDA", res.symbol)
        assertTrue(res.rsi14 in 0.0..100.0)
        assertTrue(res.mfi14 in 0.0..100.0)
        assertTrue(res.williamsR14 in -100.0..0.0)
        assertTrue(res.vwap > 0.0)
        assertTrue(res.supertrend > 0.0)
        assertTrue(res.atr14 > 0.0)
        assertTrue(res.stopLossLevel < res.currentPrice)
        assertTrue(res.takeProfit1 > res.currentPrice)
        assertTrue(res.takeProfit2 > res.takeProfit1)
        assertTrue(res.pivotLevels.r1 > res.pivotLevels.s1)
        assertTrue(res.fibLevels.swingHigh > res.fibLevels.swingLow)
        assertTrue(res.riskRewardRatio > 0.0)
        assertTrue(res.breakdown.size >= 10)
        assertTrue(res.tradeMarkers.all { it.barIndex in bars.indices && it.price > 0.0 && it.reason.isNotBlank() })
    }

    @Test
    fun rlTradingAgentAndPredictionEngineProduceValidOutputs() {
        val bars = generateDeterministicUptrendBars()
        val tech = TechnicalIndicatorEngine.analyze("RELIANCE", bars, currencySymbol = "₹")
        val policy = RlTradingAgent.evaluatePolicy("RELIANCE", bars, tech)
        val prediction = PredictionEngine.predict("RELIANCE", bars, tech, policy, currencySymbol = "₹")

        assertEquals("RELIANCE", policy.symbol)
        assertTrue(policy.confidencePct in 1..100)
        assertTrue(policy.strategyEquityCurve.size >= 8)
        assertEquals(policy.strategyEquityCurve.size, policy.benchmarkEquityCurve.size)
        assertTrue(policy.historicalWinRatePct in 0.0..100.0)
        assertTrue(policy.maxDrawdownPct >= 0.0)

        assertEquals("RELIANCE", prediction.symbol)
        assertTrue(prediction.upProbabilityPct in 1..99)
        assertTrue(prediction.convictionScore in 1..100)
        assertTrue(prediction.stopLossPrice < tech.currentPrice)
        assertTrue(prediction.whenToBuy.isNotBlank())
        assertTrue(prediction.whenToSell.isNotBlank())
        assertTrue(prediction.suggestions.isNotEmpty())
    }

    private fun sampleQuote(
        symbol: String,
        exchange: String,
        price: Double,
        tvRecommendAll: Double,
        tvRsi: Double?
    ) = com.example.data.model.TradingViewQuote(
        symbol = symbol,
        exchange = exchange,
        companyName = symbol,
        sector = "Equities",
        price = price,
        changePct = 1.0,
        volume = 10_000_000L,
        avgVolume10d = 9_000_000L,
        marketCap = 100_000_000_000.0,
        tvRecommendAll = tvRecommendAll,
        tvRecommendMa = tvRecommendAll,
        tvRecommendOsc = tvRecommendAll,
        tvRsi = tvRsi,
        tvMacd = null,
        tvMacdSignal = null,
        tvEma20 = null,
        tvSma50 = null,
        tvSma200 = null,
        tvAdx = 24.0,
        tvAtr = 2.5,
        high52w = price * 1.15,
        low52w = price * 0.75,
        sentimentScore = 50,
        sentimentLabel = "Neutral"
    )

    @Test
    fun riskAlertEvaluatorDetectsThresholdBreachesAndFormatsCurrency() {
        val quotes = mapOf(
            "NVDA" to sampleQuote("NVDA", "NASDAQ", price = 112.0, tvRecommendAll = -0.45, tvRsi = 74.5),
            "RELIANCE" to sampleQuote("RELIANCE", "NSE", price = 2980.0, tvRecommendAll = 0.55, tvRsi = 62.0)
        )
        val positions = listOf(
            com.example.data.model.Position(
                id = "p1",
                symbol = "NVDA",
                exchange = "NASDAQ",
                shares = 10.0,
                avgEntryPrice = 125.0,
                stopLossPrice = 115.0,
                takeProfitPrice = 150.0,
                maxRiskPercent = 4.0
            )
        )
        val rules = listOf(
            com.example.data.model.RiskAlertRule(
                id = "r1",
                symbol = "NVDA",
                alertType = com.example.data.model.AlertType.RSI_OVERBOUGHT.name,
                thresholdValue = 70.0,
                active = true
            ),
            com.example.data.model.RiskAlertRule(
                id = "r2",
                symbol = "RELIANCE",
                alertType = com.example.data.model.AlertType.TAKE_PROFIT.name,
                thresholdValue = 2950.0,
                active = true
            )
        )

        val triggered = com.example.quant.RiskAlertEvaluator.evaluate(
            positions = positions,
            rules = rules,
            quotes = quotes,
            profile = com.example.data.model.RiskProfile()
        )

        assertTrue(triggered.any { it.symbol == "NVDA" && it.title.contains("Stop-Loss Breached") })
        assertTrue(triggered.any { it.symbol == "NVDA" && it.title.contains("RSI Overbought") })
        val relianceTp = triggered.firstOrNull { it.symbol == "RELIANCE" && it.id == "rule_r2" }
        assertTrue(relianceTp != null && relianceTp.title.contains("₹"))
    }
}

