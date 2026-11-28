package com.example.data.remote

import android.util.Log
import com.example.data.model.ChartTimeframe
import com.example.data.model.OhlcvBar
import com.example.data.model.RlPolicyResult
import com.example.data.model.SymbolSearchResult
import com.example.data.model.TechnicalAnalysisResult
import com.example.data.model.TradingViewQuote
import com.example.quant.PredictionEngine
import com.example.quant.RlTradingAgent
import com.example.quant.StrongPredictionResult
import com.example.quant.TechnicalIndicatorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class StockIntelligenceBundle(
    val quote: TradingViewQuote,
    val bars: List<OhlcvBar>,
    val technicals: TechnicalAnalysisResult,
    val rlPolicy: RlPolicyResult,
    val prediction: StrongPredictionResult,
    val timeframe: ChartTimeframe
)

fun isIndianStock(exchange: String, symbol: String = ""): Boolean {
    val ex = exchange.trim().uppercase()
    val sym = symbol.trim().uppercase()
    return ex == "NSE" || ex == "BSE" || ex == "NSI" || ex == "BOM" || sym.endsWith(".NS") || sym.endsWith(".BO")
}

fun currencyFor(exchange: String, symbol: String = ""): String =
    if (isIndianStock(exchange, symbol)) "₹" else "$"

class MarketDataRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val tradingViewApi = TradingViewApiService(httpClient)
    private val yahooApi = YahooFinanceApiService(httpClient)

    private val quoteCache = ConcurrentHashMap<String, TradingViewQuote>()
    private val chartCache = ConcurrentHashMap<String, Pair<Long, HistoricalChartPayload>>()

    suspend fun getMarketOverviewQuotes(
        extraSymbols: List<String> = emptyList(),
        preferredExchanges: Map<String, String> = emptyMap()
    ): List<TradingViewQuote> =
        withContext(Dispatchers.IO) {
            val usDeferred = async {
                try {
                    tradingViewApi.fetchTopMarketLeaders(16)
                } catch (e: Exception) {
                    Log.w(TAG, "US leaders scan fallback: ${e.message}")
                    emptyList()
                }
            }
            val indiaDeferred = async {
                try {
                    tradingViewApi.fetchIndiaMarketLeaders(16)
                } catch (e: Exception) {
                    Log.w(TAG, "India NSE leaders scan fallback: ${e.message}")
                    emptyList()
                }
            }

            val usLeaders = usDeferred.await()
            val indiaLeaders = indiaDeferred.await()

            val defaultCore = listOf(
                "NVDA", "AAPL", "MSFT", "TSLA", "SPY",
                "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "SBIN", "TATAMOTORS"
            )
            val existingLeaders = (usLeaders + indiaLeaders).map { "${it.symbol.uppercase()}_${it.exchange.uppercase()}" }.toSet()
            val existingBare = (usLeaders + indiaLeaders).map { it.symbol.uppercase() }.toSet()
            val requested = (defaultCore + extraSymbols).map { it.trim().uppercase() }.distinct()
            val missingSymbols = requested.filter { sym ->
                val bare = sym.removeSuffix(".NS").removeSuffix(".BO")
                val prefEx = preferredExchanges[sym].orEmpty().ifBlank { preferredExchanges[bare].orEmpty() }.uppercase()
                if (prefEx.isNotBlank()) "${bare}_$prefEx" !in existingLeaders else bare !in existingBare
            }

            val extraQuotes = if (missingSymbols.isNotEmpty()) {
                try {
                    tradingViewApi.fetchQuotes(missingSymbols, preferredExchanges)
                } catch (e: Exception) {
                    Log.w(TAG, "Extra symbol scan failed: ${e.message}")
                    emptyList()
                }
            } else emptyList()

            val extraSet = extraSymbols.map { it.uppercase() }.toSet()
            val combined = (extraQuotes + indiaLeaders + usLeaders)
                .distinctBy { "${it.exchange}:${it.symbol}" }
                .sortedWith(
                    compareByDescending<TradingViewQuote> { it.symbol.uppercase() in extraSet }
                        .thenByDescending { it.volume }
                )

            combined.forEach {
                val symKey = it.symbol.uppercase()
                quoteCache[symKey] = it
                if (it.exchange.isNotBlank()) {
                    quoteCache["${symKey}_${it.exchange.uppercase()}"] = it
                }
            }

            if (combined.isEmpty()) {
                throw IllegalStateException("Unable to reach TradingView market scanner. Check internet connectivity and retry.")
            }
            combined
        }

    suspend fun getQuotesForSymbols(
        symbols: List<String>,
        preferredExchanges: Map<String, String> = emptyMap()
    ): Map<String, TradingViewQuote> =
        withContext(Dispatchers.IO) {
            val clean = symbols.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
            if (clean.isEmpty()) return@withContext emptyMap()

            val tvQuotes = try {
                tradingViewApi.fetchQuotes(clean, preferredExchanges)
            } catch (e: Exception) {
                Log.w(TAG, "TradingView batch quote error: ${e.message}")
                emptyList()
            }

            val resultMap = linkedMapOf<String, TradingViewQuote>()
            tvQuotes.forEach {
                val symKey = it.symbol.uppercase()
                resultMap[symKey] = it
                quoteCache[symKey] = it
                if (it.exchange.isNotBlank()) {
                    quoteCache["${symKey}_${it.exchange.uppercase()}"] = it
                }
            }

            for (sym in clean) {
                val baseKey = sym.removeSuffix(".NS").removeSuffix(".BO")
                val prefEx = preferredExchanges[sym].orEmpty().ifBlank { preferredExchanges[baseKey].orEmpty() }.uppercase()
                if (sym !in resultMap && baseKey !in resultMap) {
                    val cached = (if (prefEx.isNotBlank()) {
                        quoteCache["${baseKey}_$prefEx"] ?: quoteCache["${sym}_$prefEx"]
                    } else null) ?: quoteCache[sym] ?: quoteCache[baseKey]
                    if (cached != null &&
                        System.currentTimeMillis() - cached.fetchedAtMs < 60_000L &&
                        (prefEx.isBlank() || cached.exchange.equals(prefEx, ignoreCase = true))
                    ) {
                        resultMap[sym] = cached
                    } else {
                        try {
                            val chart = yahooApi.fetchHistoricalChart(sym, prefEx, ChartTimeframe.M1)
                            val q = yahooApi.fallbackQuoteFromChart(chart)
                            resultMap[sym] = q
                            quoteCache[sym] = q
                            if (q.exchange.isNotBlank()) {
                                quoteCache["${baseKey}_${q.exchange.uppercase()}"] = q
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Yahoo quote fallback failed for $sym: ${e.message}")
                        }
                    }
                } else if (sym !in resultMap && baseKey in resultMap) {
                    resultMap[sym] = resultMap.getValue(baseKey)
                }
            }
            resultMap
        }

    suspend fun searchStocks(query: String): List<SymbolSearchResult> {
        if (query.trim().isEmpty()) return emptyList()
        return tradingViewApi.searchSymbols(query)
    }

    suspend fun loadStockIntelligence(
        symbol: String,
        exchange: String = "",
        timeframe: ChartTimeframe = ChartTimeframe.M3,
        forceRefresh: Boolean = false
    ): StockIntelligenceBundle = withContext(Dispatchers.Default) {
        val cleanSymbol = symbol.trim().uppercase()
        val cacheKey = "${cleanSymbol}_${exchange}_${timeframe.label}"
        val now = System.currentTimeMillis()

        val chartTtlMs = if (timeframe.isIntraday) 4_000L else 60_000L
        val chartPayload = (
            if (!forceRefresh) {
                chartCache[cacheKey]?.takeIf { now - it.first < chartTtlMs }?.second
            } else null
        ) ?: yahooApi.fetchHistoricalChart(cleanSymbol, exchange, timeframe).also {
            chartCache[cacheKey] = now to it
        }

        val analysisBars = if (chartPayload.bars.size < 60 && timeframe.isIntraday) {
            val dailyKey = "${cleanSymbol}_${exchange}_1Y_AUX"
            val aux = chartCache[dailyKey]?.takeIf { now - it.first < 180_000L }?.second
                ?: try {
                    yahooApi.fetchHistoricalChart(cleanSymbol, exchange, ChartTimeframe.Y1).also {
                        chartCache[dailyKey] = now to it
                    }
                } catch (_: Exception) {
                    chartPayload
                }
            aux.bars
        } else {
            chartPayload.bars
        }

        val lookupSym = cleanSymbol.removeSuffix(".NS").removeSuffix(".BO")
        val targetEx = when {
            exchange.isNotBlank() -> exchange.trim().uppercase()
            chartPayload.exchangeName.isNotBlank() -> chartPayload.exchangeName.trim().uppercase()
            cleanSymbol.endsWith(".NS") -> "NSE"
            cleanSymbol.endsWith(".BO") -> "BSE"
            else -> quoteCache[cleanSymbol]?.exchange?.trim()?.uppercase().orEmpty()
        }
        val scopedCached = if (!forceRefresh) {
            if (targetEx.isNotBlank()) {
                quoteCache["${lookupSym}_$targetEx"]
                    ?: quoteCache[cleanSymbol]?.takeIf { it.exchange.equals(targetEx, ignoreCase = true) }
            } else {
                quoteCache[cleanSymbol] ?: quoteCache[lookupSym]
            }
        } else null

        val rawTvQuote = try {
            val prefMap = if (targetEx.isNotBlank()) mapOf(lookupSym to targetEx) else emptyMap()
            val fetched = tradingViewApi.fetchQuotes(listOf(lookupSym), prefMap)
            fetched.firstOrNull {
                it.symbol.equals(lookupSym, ignoreCase = true) &&
                    (targetEx.isBlank() || it.exchange.equals(targetEx, ignoreCase = true))
            }
                ?: fetched.firstOrNull {
                    it.symbol.equals(lookupSym, ignoreCase = true) &&
                        (targetEx.isBlank() || isIndianStock(it.exchange, it.symbol) == isIndianStock(targetEx, cleanSymbol))
                }
                ?: scopedCached
                ?: yahooApi.fallbackQuoteFromChart(chartPayload)
        } catch (_: Exception) {
            scopedCached ?: yahooApi.fallbackQuoteFromChart(chartPayload)
        }

        val chartRefPrice = chartPayload.regularMarketPrice.takeIf { it > 0.0 } ?: chartPayload.bars.last().close
        val priceDivergence = if (chartRefPrice > 0.0 && rawTvQuote.price > 0.0) {
            kotlin.math.abs(rawTvQuote.price - chartRefPrice) / chartRefPrice
        } else 0.0
        val tvQuote = (if (priceDivergence > 0.25) {
            yahooApi.fallbackQuoteFromChart(chartPayload)
        } else {
            rawTvQuote
        }).also {
            quoteCache[cleanSymbol] = it
            if (it.exchange.isNotBlank()) {
                quoteCache["${lookupSym}_${it.exchange.uppercase()}"] = it
            }
        }

        val curr = currencyFor(tvQuote.exchange.ifBlank { targetEx }, cleanSymbol)
        val chartTechnicals = TechnicalIndicatorEngine.analyze(
            symbol = cleanSymbol,
            bars = chartPayload.bars,
            tvQuote = tvQuote,
            currencySymbol = curr,
            isIntraday = timeframe.isIntraday
        )
        val deepTechnicals = if (analysisBars !== chartPayload.bars) {
            TechnicalIndicatorEngine.analyze(
                symbol = cleanSymbol,
                bars = analysisBars,
                tvQuote = tvQuote,
                currencySymbol = curr,
                isIntraday = false
            )
        } else {
            chartTechnicals
        }

        val keepChartLevels = chartPayload.bars.size >= 15
        val mergedTechnicals = if (deepTechnicals === chartTechnicals) {
            chartTechnicals
        } else {
            chartTechnicals.copy(
                rsi14 = if (keepChartLevels) chartTechnicals.rsi14 else deepTechnicals.rsi14,
                macd = if (keepChartLevels) chartTechnicals.macd else deepTechnicals.macd,
                macdSignal = if (keepChartLevels) chartTechnicals.macdSignal else deepTechnicals.macdSignal,
                macdHistogram = if (keepChartLevels) chartTechnicals.macdHistogram else deepTechnicals.macdHistogram,
                ema50 = deepTechnicals.ema50,
                ema200 = deepTechnicals.ema200,
                sma50 = deepTechnicals.sma50,
                sma200 = deepTechnicals.sma200,
                adx14 = deepTechnicals.adx14,
                atr14 = if (keepChartLevels) chartTechnicals.atr14 else deepTechnicals.atr14,
                vwap = chartTechnicals.vwap,
                supertrend = chartTechnicals.supertrend,
                supertrendBullish = chartTechnicals.supertrendBullish,
                mfi14 = if (keepChartLevels) chartTechnicals.mfi14 else deepTechnicals.mfi14,
                cci20 = if (keepChartLevels) chartTechnicals.cci20 else deepTechnicals.cci20,
                williamsR14 = if (keepChartLevels) chartTechnicals.williamsR14 else deepTechnicals.williamsR14,
                obvTrendBullish = deepTechnicals.obvTrendBullish,
                obvChangePct = deepTechnicals.obvChangePct,
                pivotLevels = deepTechnicals.pivotLevels,
                fibLevels = chartTechnicals.fibLevels,
                entryZoneLow = if (keepChartLevels) chartTechnicals.entryZoneLow else deepTechnicals.entryZoneLow,
                entryZoneHigh = if (keepChartLevels) chartTechnicals.entryZoneHigh else deepTechnicals.entryZoneHigh,
                stopLossLevel = if (keepChartLevels) chartTechnicals.stopLossLevel else deepTechnicals.stopLossLevel,
                takeProfit1 = if (keepChartLevels) chartTechnicals.takeProfit1 else deepTechnicals.takeProfit1,
                takeProfit2 = if (keepChartLevels) chartTechnicals.takeProfit2 else deepTechnicals.takeProfit2,
                riskRewardRatio = if (keepChartLevels) chartTechnicals.riskRewardRatio else deepTechnicals.riskRewardRatio,
                compositeScore = deepTechnicals.compositeScore,
                signalTier = deepTechnicals.signalTier,
                breakdown = deepTechnicals.breakdown
            )
        }

        val rlPolicy = RlTradingAgent.evaluatePolicy(
            symbol = cleanSymbol,
            bars = analysisBars,
            analysis = deepTechnicals
        )

        val prediction = PredictionEngine.predict(
            symbol = cleanSymbol,
            bars = analysisBars,
            tech = mergedTechnicals,
            rl = rlPolicy,
            quote = tvQuote,
            currencySymbol = curr
        )

        StockIntelligenceBundle(
            quote = tvQuote,
            bars = chartPayload.bars,
            technicals = mergedTechnicals,
            rlPolicy = rlPolicy,
            prediction = prediction,
            timeframe = timeframe
        )
    }

    companion object {
        private const val TAG = "MarketDataRepo"
    }
}
