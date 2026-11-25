package com.example.data.remote

import com.example.data.model.ChartTimeframe
import com.example.data.model.OhlcvBar
import com.example.data.model.TradingViewQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class HistoricalChartPayload(
    val symbol: String,
    val regularMarketPrice: Double,
    val previousClose: Double,
    val companyName: String,
    val exchangeName: String,
    val bars: List<OhlcvBar>
)

class YahooFinanceApiService(private val client: OkHttpClient) {

    suspend fun fetchHistoricalChart(
        symbol: String,
        exchange: String = "",
        timeframe: ChartTimeframe
    ): HistoricalChartPayload = withContext(Dispatchers.IO) {
        val yahooSymbol = toYahooSymbol(symbol, exchange)
        val encoded = URLEncoder.encode(yahooSymbol, "UTF-8")
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=${timeframe.yahooRange}&interval=${timeframe.yahooInterval}&includePrePost=false"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Historical market data request failed (HTTP ${response.code}) for $yahooSymbol")
            }
            val raw = response.body?.string()
                ?: throw IllegalStateException("Empty historical data response for $yahooSymbol")
            val root = JSONObject(raw)
            val chart = root.optJSONObject("chart")
                ?: throw IllegalStateException("Invalid chart payload for $yahooSymbol")
            val errorObj = chart.optJSONObject("error")
            if (errorObj != null) {
                throw IllegalStateException(errorObj.optString("description", "Symbol lookup error"))
            }
            val resultArr = chart.optJSONArray("result")
            if (resultArr == null || resultArr.length() == 0) {
                throw IllegalStateException("No historical OHLCV data available for $yahooSymbol")
            }

            val result = resultArr.getJSONObject(0)
            val meta = result.getJSONObject("meta")
            val regularPrice = meta.optDouble("regularMarketPrice", 0.0)
            val prevClose = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", regularPrice))
            val longName = meta.optString("longName", meta.optString("shortName", symbol))
            val exchangeName = meta.optString("exchangeName", exchange)

            val timestamps = result.optJSONArray("timestamp")
            val indicators = result.optJSONObject("indicators")
            val quoteArr = indicators?.optJSONArray("quote")
            val quoteObj = if (quoteArr != null && quoteArr.length() > 0) quoteArr.getJSONObject(0) else null

            val opens = quoteObj?.optJSONArray("open")
            val highs = quoteObj?.optJSONArray("high")
            val lows = quoteObj?.optJSONArray("low")
            val closes = quoteObj?.optJSONArray("close")
            val volumes = quoteObj?.optJSONArray("volume")

            val bars = mutableListOf<OhlcvBar>()
            if (timestamps != null && opens != null && highs != null && lows != null && closes != null) {
                for (i in 0 until timestamps.length()) {
                    if (closes.isNull(i) || opens.isNull(i) || highs.isNull(i) || lows.isNull(i)) continue
                    val close = closes.optDouble(i)
                    val open = opens.optDouble(i)
                    val high = highs.optDouble(i)
                    val low = lows.optDouble(i)
                    if (close.isNaN() || open.isNaN() || high.isNaN() || low.isNaN() || close <= 0.0) continue
                    val vol = if (volumes != null && !volumes.isNull(i)) volumes.optLong(i, 0L) else 0L
                    bars.add(
                        OhlcvBar(
                            timestampSec = timestamps.getLong(i),
                            open = open,
                            high = high,
                            low = low,
                            close = close,
                            volume = vol
                        )
                    )
                }
            }

            if (bars.isEmpty()) {
                throw IllegalStateException("Historical bars empty for $yahooSymbol")
            }

            HistoricalChartPayload(
                symbol = symbol.uppercase(),
                regularMarketPrice = if (regularPrice > 0.0) regularPrice else bars.last().close,
                previousClose = if (prevClose > 0.0) prevClose else bars.first().open,
                companyName = longName.ifBlank { symbol },
                exchangeName = exchangeName,
                bars = bars
            )
        }
    }

    fun fallbackQuoteFromChart(payload: HistoricalChartPayload): TradingViewQuote {
        val last = payload.bars.last()
        val changePct = if (payload.previousClose > 0.0) {
            ((payload.regularMarketPrice - payload.previousClose) / payload.previousClose) * 100.0
        } else 0.0
        return TradingViewQuote(
            symbol = payload.symbol,
            exchange = payload.exchangeName,
            companyName = payload.companyName,
            sector = "Equities",
            price = payload.regularMarketPrice,
            changePct = changePct,
            volume = last.volume,
            avgVolume10d = payload.bars.takeLast(10).map { it.volume }.average().toLong(),
            marketCap = 0.0,
            tvRecommendAll = 0.0,
            tvRecommendMa = 0.0,
            tvRecommendOsc = 0.0,
            tvRsi = null,
            tvMacd = null,
            tvMacdSignal = null,
            tvEma20 = null,
            tvSma50 = null,
            tvSma200 = null,
            tvAdx = null,
            tvAtr = null,
            high52w = payload.bars.maxOfOrNull { it.high },
            low52w = payload.bars.minOfOrNull { it.low },
            sentimentScore = (changePct * 5.0).toInt().coerceIn(-100, 100),
            sentimentLabel = if (changePct >= 0.5) "Bullish" else if (changePct <= -0.5) "Bearish" else "Neutral"
        )
    }

    companion object {
        fun toYahooSymbol(symbol: String, exchange: String): String {
            val clean = symbol.trim().uppercase()
            val ex = exchange.trim().uppercase()
            val base = clean.replace('.', '-')
            return when {
                clean.contains('.') && (clean.endsWith(".NS") || clean.endsWith(".BO") || clean.endsWith(".L") || clean.endsWith(".TO")) -> clean
                ex == "NSE" -> "$clean.NS"
                ex == "BSE" -> "$clean.BO"
                ex == "LSE" -> "$clean.L"
                ex == "TSX" -> "$clean.TO"
                ex == "ASX" -> "$clean.AX"
                ex == "XETR" || ex == "FWB" -> "$clean.DE"
                else -> base
            }
        }
    }
}
