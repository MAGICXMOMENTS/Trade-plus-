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
  fun chartTimeframes_include15mAnd1h() {
    val tf15m = ChartTimeframe.fromLabel("15m")
    assertEquals(ChartTimeframe.MIN15, tf15m)
    assertEquals("15m", tf15m.yahooInterval)
    assertTrue(tf15m.isIntraday)

    val tf1h = ChartTimeframe.fromLabel("1h")
    assertEquals(ChartTimeframe.H1, tf1h)
    assertEquals("1h", tf1h.yahooInterval)
    assertTrue(tf1h.isIntraday)

    assertFalse(ChartTimeframe.M3.isIntraday)
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
}
