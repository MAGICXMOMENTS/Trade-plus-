package com.example

import com.example.data.firebase.isPlaceholderFirebaseValue
import com.example.data.model.ChartTimeframe
import com.example.data.remote.YahooFinanceApiService
import org.junit.Assert.*
import org.junit.Test

/**
 * Local JVM unit tests for TradePulse configuration guards and utilities.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun placeholderFirebaseValues_areRejected() {
    assertTrue(isPlaceholderFirebaseValue(null))
    assertTrue(isPlaceholderFirebaseValue(""))
    assertTrue(isPlaceholderFirebaseValue("   "))
    assertTrue(isPlaceholderFirebaseValue("remixed-project-id"))
    assertTrue(isPlaceholderFirebaseValue("YOUR_API_KEY_HERE"))
    assertTrue(isPlaceholderFirebaseValue("placeholder-client-id"))
    assertTrue(isPlaceholderFirebaseValue("default"))
  }

  @Test
  fun validFirebaseValues_areAccepted() {
    assertFalse(isPlaceholderFirebaseValue("1:439496533950:android:a6483eac4cbf57bf08bbef"))
    assertFalse(isPlaceholderFirebaseValue("ai-studio-android-tradepul-3243eaa1-2106-450c-ac8a-d21995dab803"))
    assertFalse(isPlaceholderFirebaseValue("439496533950-9nbk4daot16qgqlbgo7l7ipri5oqcsr1.apps.googleusercontent.com"))
  }

  @Test
  fun chartTimeframes_includeAllUseCasesAndIntervals() {
    assertEquals(15, ChartTimeframe.entries.size)

    val tf1m = ChartTimeframe.fromLabel("1m")
    assertEquals(ChartTimeframe.MIN1, tf1m)
    assertEquals("1m", tf1m.yahooInterval)
    assertTrue(tf1m.isIntraday)

    val tf15m = ChartTimeframe.fromLabel("15m")
    assertEquals(ChartTimeframe.MIN15, tf15m)
    assertEquals("15m", tf15m.yahooInterval)
    assertTrue(tf15m.isIntraday)

    val tf1h = ChartTimeframe.fromLabel("1h")
    assertEquals(ChartTimeframe.H1, tf1h)
    assertEquals("60m", tf1h.yahooInterval)
    assertTrue(tf1h.isIntraday)

    val tf4h = ChartTimeframe.fromLabel("4h")
    assertEquals(ChartTimeframe.H4, tf4h)
    assertEquals(4, tf4h.aggregateGroupSize)
    assertTrue(tf4h.useCase.isNotBlank())

    assertEquals(ChartTimeframe.YTD, ChartTimeframe.fromLabel("YTD"))
    assertEquals(ChartTimeframe.MAX, ChartTimeframe.fromLabel("MAX"))
    assertFalse(ChartTimeframe.M3.isIntraday)
  }

  @Test
  fun aggregateBars_builds4hCandlesWithoutCrossingSessionGaps() {
    val base = 1_700_000_000L
    val hourBars = listOf(
      com.example.data.model.OhlcvBar(base, 100.0, 102.0, 99.0, 101.0, 100L),
      com.example.data.model.OhlcvBar(base + 3600L, 101.0, 105.0, 100.0, 104.0, 200L),
      com.example.data.model.OhlcvBar(base + 7200L, 104.0, 106.0, 103.0, 105.0, 150L),
      com.example.data.model.OhlcvBar(base + 10_800L, 105.0, 107.0, 102.0, 106.0, 250L),
      // Overnight 14h session gap -> starts new aggregated bar
      com.example.data.model.OhlcvBar(base + 61_200L, 110.0, 112.0, 109.0, 111.0, 300L)
    )
    val aggregated = YahooFinanceApiService.aggregateBars(hourBars, 4)
    assertEquals(2, aggregated.size)
    assertEquals(100.0, aggregated[0].open, 0.001)
    assertEquals(107.0, aggregated[0].high, 0.001)
    assertEquals(99.0, aggregated[0].low, 0.001)
    assertEquals(106.0, aggregated[0].close, 0.001)
    assertEquals(700L, aggregated[0].volume)
    assertEquals(110.0, aggregated[1].open, 0.001)
    assertEquals(300L, aggregated[1].volume)
  }

  @Test
  fun proIndicators_computeValidSeriesAndVolumeProfile() {
    val bars = (0 until 70).map { i ->
      val p = 100.0 + i * 0.6 + if (i % 5 == 0) 2.2 else -0.4
      com.example.data.model.OhlcvBar(
        timestampSec = 1_700_000_000L + i * 86_400L,
        open = p - 0.8,
        high = p + 1.5,
        low = p - 1.4,
        close = p + 0.5,
        volume = 1_000_000L + i * 10_000L
      )
    }
    val result = com.example.quant.TechnicalIndicatorEngine.analyze("TEST", bars)
    assertEquals(bars.size, result.ichimokuTenkanSeries.size)
    assertEquals(bars.size, result.ichimokuKijunSeries.size)
    assertEquals(bars.size, result.ichimokuSenkouASeries.size)
    assertEquals(bars.size, result.ichimokuSenkouBSeries.size)
    assertNotNull(result.ichimokuTenkanSeries.last())
    assertNotNull(result.ichimokuKijunSeries.last())
    assertNotNull(result.squeezeMomentumSeries.last())

    val vp = com.example.quant.TechnicalIndicatorEngine.computeVolumeProfile(bars, 20)
    assertNotNull(vp)
    val nonNullVp = vp!!
    assertEquals(20, nonNullVp.bins.size)
    assertTrue(nonNullVp.pocPrice in nonNullVp.valPrice..nonNullVp.vahPrice || nonNullVp.vahPrice >= nonNullVp.valPrice)
    assertTrue(nonNullVp.maxBucketVolume > 0.0)
  }

  @Test
  fun yahooSymbol_resolvesIndianStocksWithoutExchange() {
    assertEquals("RELIANCE.NS", YahooFinanceApiService.toYahooSymbol("RELIANCE", ""))
    assertEquals("TCS.NS", YahooFinanceApiService.toYahooSymbol("TCS", ""))
    assertEquals("HDFCBANK.NS", YahooFinanceApiService.toYahooSymbol("HDFCBANK", ""))
    assertEquals("NVDA", YahooFinanceApiService.toYahooSymbol("NVDA", ""))
    assertEquals("RELIANCE.BO", YahooFinanceApiService.toYahooSymbol("RELIANCE", "BSE"))
  }

  @Test
  fun intradayVwap_resetsAcrossTradingSessions() {
    val bars = listOf(
      com.example.data.model.OhlcvBar(1_700_000_000L, 100.0, 100.0, 100.0, 100.0, 1000L),
      com.example.data.model.OhlcvBar(1_700_000_900L, 110.0, 110.0, 110.0, 110.0, 1000L),
      // 16 hours later -> new session should reset VWAP to 200.0
      com.example.data.model.OhlcvBar(1_700_058_500L, 200.0, 200.0, 200.0, 200.0, 1000L)
    )
    val intradayVwap = com.example.quant.TechnicalIndicatorEngine.vwapSeries(bars, isIntraday = true)
    assertEquals(100.0, intradayVwap[0]!!, 0.001)
    assertEquals(105.0, intradayVwap[1]!!, 0.001)
    assertEquals(200.0, intradayVwap[2]!!, 0.001)
  }

  @Test
  fun multiAiEnsemble_comparesPredictorsAndProducesUnifiedConsensus() {
    val bars = (0 until 70).map { i ->
      val p = 100.0 + i * 0.5
      com.example.data.model.OhlcvBar(
        timestampSec = 1_700_000_000L + i * 86_400L,
        open = p - 0.6,
        high = p + 1.2,
        low = p - 1.1,
        close = p + 0.4,
        volume = 1_200_000L
      )
    }
    val quote = com.example.data.model.TradingViewQuote(
      symbol = "NVDA",
      exchange = "NASDAQ",
      companyName = "NVIDIA Corp",
      sector = "Technology",
      price = 135.0,
      changePct = 1.4,
      volume = 2_000_000L,
      avgVolume10d = 1_800_000L,
      marketCap = 3_000_000_000_000.0,
      tvRecommendAll = 0.55,
      tvRecommendMa = 0.7,
      tvRecommendOsc = 0.3,
      tvRsi = 61.0,
      tvMacd = 1.5,
      tvMacdSignal = 1.1,
      tvEma20 = 130.0,
      tvSma50 = 122.0,
      tvSma200 = 110.0,
      tvAdx = 28.0,
      tvAtr = 2.6,
      high52w = 142.0,
      low52w = 90.0,
      sentimentScore = 62,
      sentimentLabel = "Bullish"
    )
    val tech = com.example.quant.TechnicalIndicatorEngine.analyze("NVDA", bars, quote)
    val rl = com.example.quant.RlTradingAgent.evaluatePolicy("NVDA", bars, tech)
    val quantPred = com.example.quant.PredictionEngine.predict("NVDA", bars, tech, rl, quote)

    val cloudOutputs = listOf(
      com.example.quant.AiPredictorOutput(
        agentId = "tech_strategist",
        agentName = "AI Technical Strategist",
        specialty = "Chart Structure",
        weight = 0.34,
        shortTermBias = "Bullish",
        mediumTermBias = "Uptrend",
        directionVote = com.example.data.model.PredictionDirectionVote.BULLISH,
        upProbabilityPct = 72,
        confidencePercent = 82,
        sentimentScore = 65,
        targetLow = 128.0,
        targetBase = 148.0,
        targetHigh = 160.0,
        keySignal = "Supertrend & FVG Bullish",
        thesis = "Clean higher lows above VWAP.",
        catalysts = listOf("Volume expansion", "Squeeze firing up"),
        riskFactors = listOf("Lose 128 support"),
        entryExitGuidance = "Accumulate 133-135.",
        riskManagementAdvice = "Keep stop at 128."
      ),
      com.example.quant.AiPredictorOutput(
        agentId = "quant_rl_analyst",
        agentName = "AI Quant & RL Analyst",
        specialty = "Probability Math",
        weight = 0.26,
        shortTermBias = "Constructive",
        mediumTermBias = "Positive Edge",
        directionVote = com.example.data.model.PredictionDirectionVote.BULLISH,
        upProbabilityPct = 66,
        confidencePercent = 76,
        sentimentScore = 50,
        targetLow = 126.0,
        targetBase = 144.0,
        targetHigh = 155.0,
        keySignal = "Positive Sharpe & Q(Long)",
        thesis = "Walk-forward expectancy remains positive.",
        catalysts = listOf("Strong Sharpe > 1.0"),
        riskFactors = listOf("ATR volatility spike"),
        entryExitGuidance = "Scale with ATR stops.",
        riskManagementAdvice = "Risk 1.5% max."
      ),
      com.example.quant.AiPredictorOutput(
        agentId = "risk_sentinel",
        agentName = "AI Macro & Risk Sentinel",
        specialty = "Downside Guard",
        weight = 0.22,
        shortTermBias = "Cautious",
        mediumTermBias = "Range",
        directionVote = com.example.data.model.PredictionDirectionVote.NEUTRAL,
        upProbabilityPct = 52,
        confidencePercent = 68,
        sentimentScore = 15,
        targetLow = 124.0,
        targetBase = 138.0,
        targetHigh = 149.0,
        keySignal = "Near 52W High Resistance",
        thesis = "Approaching 52W highs; tighten trailing stop.",
        catalysts = listOf("Institutional inflow"),
        riskFactors = listOf("52W resistance rejection"),
        entryExitGuidance = "Wait pullback to EMA21.",
        riskManagementAdvice = "Respect 10% portfolio DD cap."
      )
    )

    val result = com.example.quant.EnsemblePredictionAggregator.synthesizeEnsemble(
      quote = quote,
      technicals = tech,
      rlPolicy = rl,
      riskProfile = com.example.data.model.RiskProfile(),
      cloudPredictions = cloudOutputs,
      quantPrediction = quantPred,
      currencySymbol = "$"
    )

    assertEquals(4, result.agentPredictions.size)
    assertEquals(1.0, result.agentPredictions.sumOf { it.weight }, 0.001)
    assertTrue(result.projectedTargetLow <= result.projectedTargetBase)
    assertTrue(result.projectedTargetBase <= result.projectedTargetHigh)
    assertTrue(result.consensusVerdict.contains("BULLISH"))
    assertTrue(result.agreementPercent in 25..99)
    assertTrue(result.bullishVotes >= 2)
    assertTrue(result.ensembleSpreadPct >= 0.0)
    assertTrue(result.comparisonSummary.contains("spread"))
  }
}
