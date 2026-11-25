package com.example.data.remote

import android.util.Log
import com.example.data.model.SymbolSearchResult
import com.example.data.model.TradingViewQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class TradingViewApiService(private val client: OkHttpClient) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val requestedColumns = listOf(
        "name",
        "exchange",
        "description",
        "sector",
        "close",
        "change",
        "volume",
        "average_volume_10d_calc",
        "market_cap_basic",
        "Recommend.All",
        "Recommend.MA",
        "Recommend.Other",
        "RSI",
        "MACD.macd",
        "MACD.signal",
        "EMA20",
        "SMA50",
        "SMA200",
        "ADX",
        "ATR",
        "price_52_week_high",
        "price_52_week_low"
    )

    suspend fun fetchQuotes(symbols: List<String>): List<TradingViewQuote> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) return@withContext emptyList()
        val cleanSymbols = symbols.map {
            it.trim().uppercase().removeSuffix(".NS").removeSuffix(".BO")
        }.distinct()
        val collected = linkedMapOf<String, TradingViewQuote>()

        for (region in listOf("america", "india", "global")) {
            val remaining = cleanSymbols.filter { it !in collected.keys }
            if (remaining.isEmpty()) break

            val payload = JSONObject().apply {
                put("filter", JSONArray().apply {
                    put(JSONObject().apply {
                        put("left", "name")
                        put("operation", "in_range")
                        put("right", JSONArray(remaining))
                    })
                })
                put("options", JSONObject().apply { put("lang", "en") })
                put("columns", JSONArray(requestedColumns))
                put("sort", JSONObject().apply {
                    put("sortBy", "market_cap_basic")
                    put("sortOrder", "desc")
                })
                put("range", JSONArray(listOf(0, remaining.size * 5)))
            }

            val request = Request.Builder()
                .url("https://scanner.tradingview.com/$region/scan")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) TradePulse/1.0")
                .header("Origin", "https://www.tradingview.com")
                .header("Referer", "https://www.tradingview.com/")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "TradingView $region scan HTTP ${response.code}")
                        return@use
                    }
                    val body = response.body?.string() ?: return@use
                    val parsed = parseScannerResponse(body)
                    for (quote in parsed) {
                        val existing = collected[quote.symbol]
                        val preferIndia = region == "india" && quote.exchange.equals("NSE", ignoreCase = true)
                        if (existing == null || (preferIndia && !existing.exchange.equals("NASDAQ", true) && !existing.exchange.equals("NYSE", true)) || quote.marketCap > existing.marketCap) {
                            collected[quote.symbol] = quote
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TradingView $region scan error: ${e.message}")
            }
        }

        collected.values.toList()
    }

    suspend fun fetchTopMarketLeaders(limit: Int = 18): List<TradingViewQuote> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("filter", JSONArray().apply {
                put(JSONObject().apply {
                    put("left", "type")
                    put("operation", "in_range")
                    put("right", JSONArray(listOf("stock", "dr", "fund")))
                })
                put(JSONObject().apply {
                    put("left", "market_cap_basic")
                    put("operation", "greater")
                    put("right", 25_000_000_000.0)
                })
            })
            put("options", JSONObject().apply { put("lang", "en") })
            put("columns", JSONArray(requestedColumns))
            put("sort", JSONObject().apply {
                put("sortBy", "volume")
                put("sortOrder", "desc")
            })
            put("range", JSONArray(listOf(0, limit)))
        }

        val request = Request.Builder()
            .url("https://scanner.tradingview.com/america/scan")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) TradePulse/1.0")
            .header("Origin", "https://www.tradingview.com")
            .header("Referer", "https://www.tradingview.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("TradingView US scanner returned HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty TradingView scanner response")
            parseScannerResponse(body)
        }
    }

    suspend fun fetchIndiaMarketLeaders(limit: Int = 18): List<TradingViewQuote> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("filter", JSONArray().apply {
                put(JSONObject().apply {
                    put("left", "exchange")
                    put("operation", "equal")
                    put("right", "NSE")
                })
                put(JSONObject().apply {
                    put("left", "type")
                    put("operation", "in_range")
                    put("right", JSONArray(listOf("stock", "dr")))
                })
                put(JSONObject().apply {
                    put("left", "market_cap_basic")
                    put("operation", "greater")
                    put("right", 50_000_000_000.0)
                })
            })
            put("options", JSONObject().apply { put("lang", "en") })
            put("columns", JSONArray(requestedColumns))
            put("sort", JSONObject().apply {
                put("sortBy", "volume")
                put("sortOrder", "desc")
            })
            put("range", JSONArray(listOf(0, limit)))
        }

        val request = Request.Builder()
            .url("https://scanner.tradingview.com/india/scan")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) TradePulse/1.0")
            .header("Origin", "https://www.tradingview.com")
            .header("Referer", "https://www.tradingview.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("TradingView India scanner returned HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty TradingView India scanner response")
            parseScannerResponse(body)
        }
    }

    suspend fun searchSymbols(query: String): List<SymbolSearchResult> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://symbol-search.tradingview.com/symbol_search/v3/?text=$encoded&hl=1&exchange=&lang=en&search_type=stocks&domain=production"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) TradePulse/1.0")
            .header("Origin", "https://www.tradingview.com")
            .header("Referer", "https://www.tradingview.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("TradingView symbol search HTTP ${response.code}")
            }
            val body = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(body)
            val symbolsArray = root.optJSONArray("symbols") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until symbolsArray.length()) {
                    val obj = symbolsArray.optJSONObject(i) ?: continue
                    val rawSym = obj.optString("symbol").replace(Regex("<[^>]*>"), "").trim()
                    val exchange = obj.optString("exchange").replace(Regex("<[^>]*>"), "").trim()
                    val desc = obj.optString("description").replace(Regex("<[^>]*>"), "").trim()
                    val type = obj.optString("type").trim()
                    if (rawSym.isNotBlank()) {
                        add(SymbolSearchResult(rawSym, exchange, desc.ifBlank { rawSym }, type))
                    }
                }
            }
                .sortedByDescending { it.exchange.equals("NSE", true) || it.exchange.equals("BSE", true) || it.exchange.equals("NASDAQ", true) || it.exchange.equals("NYSE", true) }
                .distinctBy { "${it.exchange}:${it.symbol}" }
                .take(24)
        }
    }

    private fun parseScannerResponse(rawJson: String): List<TradingViewQuote> {
        val root = JSONObject(rawJson)
        val dataArray = root.optJSONArray("data") ?: return emptyList()
        val quotes = mutableListOf<TradingViewQuote>()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(i) ?: continue
            val d = item.optJSONArray("d") ?: continue
            if (d.length() < 10) continue

            val symbol = d.optString(0).trim()
            val exchange = d.optString(1).trim()
            val description = d.optString(2).trim().ifBlank { symbol }
            val sector = d.optString(3).trim().ifBlank { "Equities" }
            val close = d.optDoubleOrNull(4) ?: continue
            val changePct = d.optDoubleOrNull(5) ?: 0.0
            val volume = d.optDoubleOrNull(6)?.toLong() ?: 0L
            val avgVol = d.optDoubleOrNull(7)?.toLong() ?: 0L
            val marketCap = d.optDoubleOrNull(8) ?: 0.0
            val recAll = d.optDoubleOrNull(9) ?: 0.0
            val recMa = d.optDoubleOrNull(10) ?: 0.0
            val recOsc = d.optDoubleOrNull(11) ?: 0.0
            val rsi = d.optDoubleOrNull(12)
            val macd = d.optDoubleOrNull(13)
            val macdSig = d.optDoubleOrNull(14)
            val ema20 = d.optDoubleOrNull(15)
            val sma50 = d.optDoubleOrNull(16)
            val sma200 = d.optDoubleOrNull(17)
            val adx = d.optDoubleOrNull(18)
            val atr = d.optDoubleOrNull(19)
            val high52w = d.optDoubleOrNull(20)
            val low52w = d.optDoubleOrNull(21)

            val volBoost = if (avgVol > 0 && volume > avgVol * 1.25) (if (changePct >= 0) 8.0 else -8.0) else 0.0
            val rawSentiment = (recAll * 60.0 + changePct * 4.5 + volBoost).coerceIn(-100.0, 100.0)
            val sentimentScore = rawSentiment.roundToInt()
            val sentimentLabel = when {
                sentimentScore >= 45 -> "Strongly Bullish"
                sentimentScore >= 15 -> "Bullish"
                sentimentScore <= -45 -> "Strongly Bearish"
                sentimentScore <= -15 -> "Bearish"
                else -> "Neutral"
            }

            quotes.add(
                TradingViewQuote(
                    symbol = symbol,
                    exchange = exchange,
                    companyName = description,
                    sector = sector,
                    price = close,
                    changePct = changePct,
                    volume = volume,
                    avgVolume10d = avgVol,
                    marketCap = marketCap,
                    tvRecommendAll = recAll,
                    tvRecommendMa = recMa,
                    tvRecommendOsc = recOsc,
                    tvRsi = rsi,
                    tvMacd = macd,
                    tvMacdSignal = macdSig,
                    tvEma20 = ema20,
                    tvSma50 = sma50,
                    tvSma200 = sma200,
                    tvAdx = adx,
                    tvAtr = atr,
                    high52w = high52w,
                    low52w = low52w,
                    sentimentScore = sentimentScore,
                    sentimentLabel = sentimentLabel
                )
            )
        }
        return quotes
    }

    private fun JSONArray.optDoubleOrNull(index: Int): Double? {
        if (index >= length() || isNull(index)) return null
        val value = optDouble(index)
        return if (value.isNaN() || value.isInfinite()) null else value
    }

    companion object {
        private const val TAG = "TradingViewApi"
    }
}
