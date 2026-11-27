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
        val primaryYahooSymbol = toYahooSymbol(symbol, exchange)
        val candidates = buildList {
            add(primaryYahooSymbol)
            val clean = symbol.trim().uppercase()
            if (exchange.isBlank() && !clean.contains('.')) {
                val nse = "$clean.NS"
                val bse = "$clean.BO"
                if (nse != primaryYahooSymbol) add(nse)
                if (bse != primaryYahooSymbol) add(bse)
            }
        }

        var lastError: Exception? = null
        for ((index, candidate) in candidates.withIndex()) {
            try {
                var payload = executeChartRequest(
                    yahooSymbol = candidate,
                    displaySymbol = symbol,
                    exchange = exchange,
                    timeframe = timeframe,
                    rangeOverride = null
                )
                if (timeframe == ChartTimeframe.D1 && payload.bars.size < 6) {
                    try {
                        val twoDay = executeChartRequest(
                            yahooSymbol = candidate,
                            displaySymbol = symbol,
                            exchange = exchange,
                            timeframe = timeframe,
                            rangeOverride = "2d"
                        )
                        if (twoDay.bars.size > payload.bars.size) {
                            payload = twoDay.copy(bars = twoDay.bars.takeLast(78))
                        }
                    } catch (_: Exception) {
                        // Keep initial D1 payload if 2d fallback fails
                    }
                }
                return@withContext payload
            } catch (e: Exception) {
                lastError = e
                val isLast = index == candidates.lastIndex
                val msg = e.message.orEmpty()
                val retryableNotFound = msg.contains("HTTP 404") ||
                    msg.contains("No historical OHLCV data", ignoreCase = true) ||
                    msg.contains("Historical bars empty", ignoreCase = true) ||
                    msg.contains("No data found", ignoreCase = true)
                if (isLast || !retryableNotFound) {
                    throw e
                }
            }
        }
        throw lastError ?: IllegalStateException("Historical market data request failed for $symbol")
    }

    private fun executeChartRequest(
        yahooSymbol: String,
        displaySymbol: String,
        exchange: String,
        timeframe: ChartTimeframe,
        rangeOverride: String? = null
    ): HistoricalChartPayload {
        val encoded = URLEncoder.encode(yahooSymbol, "UTF-8")
        val range = rangeOverride ?: timeframe.yahooRange
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=$range&interval=${timeframe.yahooInterval}&includePrePost=false"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "application/json")
            .build()

        return client.newCall(request).execute().use { response ->
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
            val longName = meta.optString("longName", meta.optString("shortName", displaySymbol))
            val rawExchange = meta.optString("exchangeName", exchange).trim()
            val exchangeName = when (rawExchange.uppercase()) {
                "NSI" -> "NSE"
                "BOM" -> "BSE"
                else -> rawExchange.ifBlank {
                    when {
                        yahooSymbol.endsWith(".NS", ignoreCase = true) -> "NSE"
                        yahooSymbol.endsWith(".BO", ignoreCase = true) -> "BSE"
                        else -> exchange
                    }
                }
            }

            val timestamps = result.optJSONArray("timestamp")
            val indicators = result.optJSONObject("indicators")
            val quoteArr = indicators?.optJSONArray("quote")
            val quoteObj = if (quoteArr != null && quoteArr.length() > 0) quoteArr.getJSONObject(0) else null

            val opens = quoteObj?.optJSONArray("open")
            val highs = quoteObj?.optJSONArray("high")
            val lows = quoteObj?.optJSONArray("low")
            val closes = quoteObj?.optJSONArray("close")
            val volumes = quoteObj?.optJSONArray("volume")

            val dedupedBars = LinkedHashMap<Long, OhlcvBar>()
            if (timestamps != null && opens != null && highs != null && lows != null && closes != null) {
                for (i in 0 until timestamps.length()) {
                    if (closes.isNull(i) || opens.isNull(i) || highs.isNull(i) || lows.isNull(i)) continue
                    val close = closes.optDouble(i)
                    val open = opens.optDouble(i)
                    val high = highs.optDouble(i)
                    val low = lows.optDouble(i)
                    if (!close.isFinite() || !open.isFinite() || !high.isFinite() || !low.isFinite()) continue
                    if (close <= 0.0 || open <= 0.0 || high <= 0.0 || low <= 0.0) continue
                    val ts = timestamps.optLong(i, 0L)
                    if (ts <= 0L) continue
                    val vol = if (volumes != null && !volumes.isNull(i)) volumes.optLong(i, 0L).coerceAtLeast(0L) else 0L
                    val validHigh = maxOf(high, open, close)
                    val validLow = minOf(low, open, close)
                    dedupedBars[ts] = OhlcvBar(
                        timestampSec = ts,
                        open = open,
                        high = validHigh,
                        low = validLow,
                        close = close,
                        volume = vol
                    )
                }
            }
            val bars = dedupedBars.values.sortedBy { it.timestampSec }

            if (bars.isEmpty()) {
                throw IllegalStateException("Historical bars empty for $yahooSymbol")
            }

            HistoricalChartPayload(
                symbol = displaySymbol.uppercase(),
                regularMarketPrice = if (regularPrice.isFinite() && regularPrice > 0.0) regularPrice else bars.last().close,
                previousClose = if (prevClose.isFinite() && prevClose > 0.0) prevClose else bars.first().open,
                companyName = longName.ifBlank { displaySymbol },
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
        private val KNOWN_NSE_SYMBOLS = setOf(
            "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "SBIN", "TATAMOTORS",
            "BHARTIARTL", "ITC", "LT", "AXISBANK", "KOTAKBANK", "WIPRO", "MARUTI",
            "SUNPHARMA", "TITAN", "BAJFINANCE", "ASIANPAINT", "ADANIENT", "NTPC",
            "ONGC", "POWERGRID", "ULTRACEMCO", "HINDUNILVR", "HCLTECH", "TECHM",
            "JSWSTEEL", "TATASTEEL", "BAJAJFINSV", "NESTLEIND", "DRREDDY", "COALINDIA",
            "GRASIM", "INDUSINDBK", "CIPLA", "EICHERMOT", "APOLLOHOSP", "DIVISLAB", "BPCL"
        )

        fun toYahooSymbol(symbol: String, exchange: String): String {
            val clean = symbol.trim().uppercase()
            val ex = exchange.trim().uppercase()
            val base = clean.replace('.', '-')
            return when {
                clean.contains('.') && (clean.endsWith(".NS") || clean.endsWith(".BO") || clean.endsWith(".L") || clean.endsWith(".TO") || clean.endsWith(".AX") || clean.endsWith(".DE")) -> clean
                ex == "NSE" -> "$clean.NS"
                ex == "BSE" -> "$clean.BO"
                ex == "LSE" -> "$clean.L"
                ex == "TSX" -> "$clean.TO"
                ex == "ASX" -> "$clean.AX"
                ex == "XETR" || ex == "FWB" -> "$clean.DE"
                ex.isBlank() && clean in KNOWN_NSE_SYMBOLS -> "$clean.NS"
                else -> base
            }
        }
    }
}
