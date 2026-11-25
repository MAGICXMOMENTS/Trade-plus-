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
    return ex == "NSE" || ex == "BSE" || sym.endsWith(".NS") || sym.endsWith(".BO")
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

    suspend fun getMarketOverviewQuotes(extraSymbols: List<String> = emptyList()): List<TradingViewQuote> =
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
            val existingLeaders = (usLeaders + indiaLeaders).map { it.symbol.uppercase() }.toSet()
            val requested = (defaultCore + extraSymbols).map { it.trim().uppercase() }.distinct()
            val missingSymbols = requested.filter { it.removeSuffix(".NS").removeSuffix(".BO") !in existingLeaders }

            val extraQuotes = if (missingSymbols.isNotEmpty()) {
                try {
                    tradingViewApi.fetchQuotes(missingSymbols)
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

            combined.forEach { quoteCache[it.symbol.uppercase()] = it }

            if (combined.isEmpty()) {
                throw IllegalStateException("Unable to reach TradingView market scanner. Check internet connectivity and retry.")
            }
            combined
        }

    suspend fun getQuotesForSymbols(symbols: List<String>): Map<String, TradingViewQuote> =
        withContext(Dispatchers.IO) {
            val clean = symbols.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
            if (clean.isEmpty()) return@withContext emptyMap()

            val tvQuotes = try {
                tradingViewApi.fetchQuotes(clean)
            } catch (e: Exception) {
                Log.w(TAG, "TradingView batch quote error: ${e.message}")
                emptyList()
            }

            val resultMap = linkedMapOf<String, TradingViewQuote>()
            tvQuotes.forEach {
                resultMap[it.symbol.uppercase()] = it
                quoteCache[it.symbol.uppercase()] = it
            }

            for (sym in clean) {
                val baseKey = sym.removeSuffix(".NS").removeSuffix(".BO")
                if (sym !in resultMap && baseKey !in resultMap) {
                    val cached = quoteCache[sym] ?: quoteCache[baseKey]
                    if (cached != null && System.currentTimeMillis() - cached.fetchedAtMs < 60_000L) {
                        resultMap[sym] = cached
                    } else {
                        try {
                            val chart = yahooApi.fetchHistoricalChart(sym, "", ChartTimeframe.M1)
                            val q = yahooApi.fallbackQuoteFromChart(chart)
                            resultMap[sym] = q
                            quoteCache[sym] = q
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

        val chartPayload = (
            if (!forceRefresh) {
                chartCache[cacheKey]?.takeIf { now - it.first < 90_000L }?.second
            } else null
        ) ?: yahooApi.fetchHistoricalChart(cleanSymbol, exchange, timeframe).also {
            chartCache[cacheKey] = now to it
        }

        val analysisBars = if (chartPayload.bars.size < 60 && (timeframe == ChartTimeframe.D1 || timeframe == ChartTimeframe.W1)) {
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

        val tvQuote = try {
            val lookupSym = cleanSymbol.removeSuffix(".NS").removeSuffix(".BO")
            val fetched = tradingViewApi.fetchQuotes(listOf(lookupSym))
            fetched.firstOrNull {
                it.symbol.equals(lookupSym, ignoreCase = true) &&
                    (exchange.isBlank() || it.exchange.equals(exchange, ignoreCase = true))
            }
                ?: fetched.firstOrNull { it.symbol.equals(lookupSym, ignoreCase = true) }
                ?: quoteCache[cleanSymbol]
                ?: yahooApi.fallbackQuoteFromChart(chartPayload)
        } catch (_: Exception) {
            quoteCache[cleanSymbol] ?: yahooApi.fallbackQuoteFromChart(chartPayload)
        }.also {
            quoteCache[cleanSymbol] = it
        }

        val curr = currencyFor(tvQuote.exchange.ifBlank { exchange }, cleanSymbol)
        val chartTechnicals = TechnicalIndicatorEngine.analyze(cleanSymbol, chartPayload.bars, tvQuote, curr)
        val deepTechnicals = if (analysisBars !== chartPayload.bars) {
            TechnicalIndicatorEngine.analyze(cleanSymbol, analysisBars, tvQuote, curr)
        } else {
            chartTechnicals
        }

        val mergedTechnicals = chartTechnicals.copy(
            rsi14 = deepTechnicals.rsi14,
            macd = deepTechnicals.macd,
            macdSignal = deepTechnicals.macdSignal,
            macdHistogram = deepTechnicals.macdHistogram,
            ema50 = deepTechnicals.ema50,
            ema200 = deepTechnicals.ema200,
            sma50 = deepTechnicals.sma50,
            sma200 = deepTechnicals.sma200,
            adx14 = deepTechnicals.adx14,
            atr14 = deepTechnicals.atr14,
            vwap = deepTechnicals.vwap,
            supertrend = deepTechnicals.supertrend,
            supertrendBullish = deepTechnicals.supertrendBullish,
            mfi14 = deepTechnicals.mfi14,
            cci20 = deepTechnicals.cci20,
            williamsR14 = deepTechnicals.williamsR14,
            obvTrendBullish = deepTechnicals.obvTrendBullish,
            obvChangePct = deepTechnicals.obvChangePct,
            pivotLevels = deepTechnicals.pivotLevels,
            fibLevels = deepTechnicals.fibLevels,
            entryZoneLow = deepTechnicals.entryZoneLow,
            entryZoneHigh = deepTechnicals.entryZoneHigh,
            stopLossLevel = deepTechnicals.stopLossLevel,
            takeProfit1 = deepTechnicals.takeProfit1,
            takeProfit2 = deepTechnicals.takeProfit2,
            riskRewardRatio = deepTechnicals.riskRewardRatio,
            compositeScore = deepTechnicals.compositeScore,
            signalTier = deepTechnicals.signalTier,
            breakdown = deepTechnicals.breakdown
        )

        val rlPolicy = RlTradingAgent.evaluatePolicy(
            symbol = cleanSymbol,
            bars = analysisBars,
            analysis = mergedTechnicals
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
