package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AiForecastRepository
import com.example.data.firebase.PortfolioRepository
import com.example.data.model.AiForecastResult
import com.example.data.model.AlertType
import com.example.data.model.ChartTimeframe
import com.example.data.model.Position
import com.example.data.model.PositionStatus
import com.example.data.model.RiskAlertRule
import com.example.data.model.RiskProfile
import com.example.data.model.RiskTolerance
import com.example.data.model.SymbolSearchResult
import com.example.data.model.TradingViewQuote
import com.example.data.model.TriggeredRiskAlert
import com.example.data.model.UiState
import com.example.data.model.WatchlistStock
import com.example.data.remote.MarketDataRepository
import com.example.data.remote.StockIntelligenceBundle
import com.example.quant.RiskAlertEvaluator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TradePulseViewModel(
    private val portfolioRepo: PortfolioRepository,
    private val marketRepo: MarketDataRepository,
    private val aiRepo: AiForecastRepository,
    private val currentUserId: String
) : ViewModel() {

    val positionsState: StateFlow<UiState<List<Position>>> =
        portfolioRepo.observePositions(currentUserId)
            .map<List<Position>, UiState<List<Position>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError("positions", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val watchlistState: StateFlow<UiState<List<WatchlistStock>>> =
        portfolioRepo.observeWatchlist(currentUserId)
            .map<List<WatchlistStock>, UiState<List<WatchlistStock>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError("watchlist", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val riskAlertsState: StateFlow<UiState<List<RiskAlertRule>>> =
        portfolioRepo.observeRiskAlerts(currentUserId)
            .map<List<RiskAlertRule>, UiState<List<RiskAlertRule>>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamError("riskAlerts", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val riskProfileState: StateFlow<RiskProfile> =
        portfolioRepo.observeRiskProfile(currentUserId)
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing risk profile", fsErr ?: error)
                emit(RiskProfile(userId = currentUserId))
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                RiskProfile(userId = currentUserId)
            )

    private val _marketQuotesState = MutableStateFlow<UiState<List<TradingViewQuote>>>(UiState.Loading)
    val marketQuotesState: StateFlow<UiState<List<TradingViewQuote>>> = _marketQuotesState.asStateFlow()

    private val _liveQuoteMap = MutableStateFlow<Map<String, TradingViewQuote>>(emptyMap())
    val liveQuoteMap: StateFlow<Map<String, TradingViewQuote>> = _liveQuoteMap.asStateFlow()

    val triggeredRiskAlerts: StateFlow<List<TriggeredRiskAlert>> =
        combine(positionsState, riskAlertsState, _liveQuoteMap, riskProfileState) { posUi, ruleUi, quotes, profile ->
            val posList = (posUi as? UiState.Success)?.data.orEmpty()
            val ruleList = (ruleUi as? UiState.Success)?.data.orEmpty()
            RiskAlertEvaluator.evaluate(posList, ruleList, quotes, profile)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResultsState = MutableStateFlow<UiState<List<SymbolSearchResult>>>(UiState.Idle)
    val searchResultsState: StateFlow<UiState<List<SymbolSearchResult>>> = _searchResultsState.asStateFlow()

    private val _selectedSymbol = MutableStateFlow("NVDA")
    val selectedSymbol: StateFlow<String> = _selectedSymbol.asStateFlow()

    private val _selectedExchange = MutableStateFlow("NASDAQ")
    val selectedExchange: StateFlow<String> = _selectedExchange.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(ChartTimeframe.M3)
    val selectedTimeframe: StateFlow<ChartTimeframe> = _selectedTimeframe.asStateFlow()

    private val _stockIntelligenceState = MutableStateFlow<UiState<StockIntelligenceBundle>>(UiState.Loading)
    val stockIntelligenceState: StateFlow<UiState<StockIntelligenceBundle>> = _stockIntelligenceState.asStateFlow()

    private val _aiForecastState = MutableStateFlow<UiState<AiForecastResult>>(UiState.Idle)
    val aiForecastState: StateFlow<UiState<AiForecastResult>> = _aiForecastState.asStateFlow()

    private val _portfolioAuditState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val portfolioAuditState: StateFlow<UiState<String>> = _portfolioAuditState.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private var searchJob: Job? = null
    private var intelligenceJob: Job? = null
    private var forecastJob: Job? = null
    private var auditJob: Job? = null

    init {
        refreshMarketData()
        loadStockIntelligence("NVDA", "NASDAQ", ChartTimeframe.M3)
    }

    fun refreshMarketData() {
        viewModelScope.launch {
            if (_marketQuotesState.value !is UiState.Success) {
                _marketQuotesState.value = UiState.Loading
            }
            try {
                val posList = (positionsState.value as? UiState.Success)?.data.orEmpty()
                val watchList = (watchlistState.value as? UiState.Success)?.data.orEmpty()
                val posSymbols = posList.map { it.symbol }
                val watchSymbols = watchList.map { it.symbol }
                val ruleSymbols = (riskAlertsState.value as? UiState.Success)?.data?.map { it.symbol }.orEmpty()
                val extra = (posSymbols + watchSymbols + ruleSymbols).distinct()
                val preferredExchanges = buildMap {
                    watchList.forEach { if (it.exchange.isNotBlank()) put(it.symbol.uppercase(), it.exchange) }
                    posList.forEach { if (it.exchange.isNotBlank()) put(it.symbol.uppercase(), it.exchange) }
                }

                val overview = marketRepo.getMarketOverviewQuotes(extra, preferredExchanges)
                val map = mutableMapOf<String, TradingViewQuote>()
                for (q in overview) {
                    val symKey = q.symbol.uppercase()
                    val prefEx = preferredExchanges[symKey]
                        ?: preferredExchanges["$symKey.NS"]
                        ?: preferredExchanges["$symKey.BO"]
                    val existing = map[symKey]
                    val newMatchesPref = !prefEx.isNullOrBlank() && q.exchange.equals(prefEx, ignoreCase = true)
                    val existingMatchesPref = !prefEx.isNullOrBlank() && existing?.exchange.equals(prefEx, ignoreCase = true)
                    if (existing == null || (newMatchesPref && !existingMatchesPref)) {
                        map[symKey] = q
                    }
                }

                val missing = extra.filter { sym ->
                    val bare = sym.trim().uppercase().removeSuffix(".NS").removeSuffix(".BO")
                    val existing = map[bare] ?: map[sym.trim().uppercase()]
                    val prefEx = preferredExchanges[sym.uppercase()] ?: preferredExchanges[bare]
                    existing == null || (!prefEx.isNullOrBlank() && !existing.exchange.equals(prefEx, ignoreCase = true))
                }
                if (missing.isNotEmpty()) {
                    map.putAll(marketRepo.getQuotesForSymbols(missing, preferredExchanges))
                }

                _liveQuoteMap.value = map
                _marketQuotesState.value = UiState.Success(overview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load TradingView market data", e)
                if (_marketQuotesState.value !is UiState.Success) {
                    _marketQuotesState.value = UiState.Error(
                        e.localizedMessage ?: "Failed to load live TradingView market quotes."
                    )
                } else {
                    _feedbackMessage.value = e.localizedMessage ?: "Refresh failed"
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.trim().isEmpty()) {
            _searchResultsState.value = UiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(280L)
            _searchResultsState.value = UiState.Loading
            try {
                val results = marketRepo.searchStocks(query)
                _searchResultsState.value = UiState.Success(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "TradingView symbol search error", e)
                _searchResultsState.value = UiState.Error(e.localizedMessage ?: "Search failed")
            }
        }
    }

    fun selectStock(symbol: String, exchange: String = "", timeframe: ChartTimeframe = _selectedTimeframe.value) {
        val clean = symbol.trim().uppercase()
        if (clean.isBlank()) return
        val resolvedExchange = exchange.ifBlank {
            _liveQuoteMap.value[clean]?.exchange
                ?: (_marketQuotesState.value as? UiState.Success)?.data
                    ?.firstOrNull { it.symbol.equals(clean, ignoreCase = true) }
                    ?.exchange
                ?: ""
        }
        val changedSymbol = clean != _selectedSymbol.value
        _selectedSymbol.value = clean
        _selectedExchange.value = resolvedExchange
        _selectedTimeframe.value = timeframe
        if (changedSymbol) {
            _aiForecastState.value = UiState.Idle
        }
        loadStockIntelligence(clean, resolvedExchange, timeframe)
    }

    fun selectTimeframe(timeframe: ChartTimeframe) {
        _selectedTimeframe.value = timeframe
        loadStockIntelligence(_selectedSymbol.value, _selectedExchange.value, timeframe)
    }

    fun loadStockIntelligence(
        symbol: String = _selectedSymbol.value,
        exchange: String = _selectedExchange.value,
        timeframe: ChartTimeframe = _selectedTimeframe.value,
        forceRefresh: Boolean = false
    ) {
        intelligenceJob?.cancel()
        intelligenceJob = viewModelScope.launch {
            _stockIntelligenceState.value = UiState.Loading
            try {
                val bundle = marketRepo.loadStockIntelligence(symbol, exchange, timeframe, forceRefresh)
                if (_selectedExchange.value.isBlank() && bundle.quote.exchange.isNotBlank()) {
                    _selectedExchange.value = bundle.quote.exchange
                }
                _stockIntelligenceState.value = UiState.Success(bundle)
                _liveQuoteMap.value = _liveQuoteMap.value.toMutableMap().apply {
                    put(bundle.quote.symbol.uppercase(), bundle.quote)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load intelligence for $symbol", e)
                _stockIntelligenceState.value = UiState.Error(
                    e.localizedMessage ?: "Unable to fetch historical data for $symbol."
                )
            }
        }
    }

    fun runAiForecast() {
        val intel = (_stockIntelligenceState.value as? UiState.Success)?.data
        if (intel == null) {
            _aiForecastState.value = UiState.Error("Load a stock's historical market telemetry first.")
            return
        }
        forecastJob?.cancel()
        forecastJob = viewModelScope.launch {
            _aiForecastState.value = UiState.Loading
            try {
                val forecast = aiRepo.generatePredictiveForecast(
                    quote = intel.quote,
                    technicals = intel.technicals,
                    rlPolicy = intel.rlPolicy,
                    riskProfile = riskProfileState.value
                )
                _aiForecastState.value = UiState.Success(forecast)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI forecast timed out", e)
                _aiForecastState.value = UiState.Error("AI forecast timed out after 60s. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI forecast failed", e)
                _aiForecastState.value = UiState.Error(e.localizedMessage ?: "AI forecast generation failed.")
            }
        }
    }

    fun runPortfolioRiskAudit() {
        val positions = (positionsState.value as? UiState.Success)?.data
            ?.filter { it.statusEnum() == PositionStatus.ACTIVE }.orEmpty()
        auditJob?.cancel()
        auditJob = viewModelScope.launch {
            _portfolioAuditState.value = UiState.Loading
            try {
                var finalText = ""
                aiRepo.streamPortfolioRiskAudit(positions, _liveQuoteMap.value, riskProfileState.value)
                    .collect { partial ->
                        finalText = partial
                        _portfolioAuditState.value = UiState.Streaming(partial)
                    }
                if (finalText.isNotBlank()) {
                    _portfolioAuditState.value = UiState.Success(finalText)
                } else {
                    _portfolioAuditState.value = UiState.Error("AI risk audit returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                _portfolioAuditState.value = UiState.Error("Portfolio risk audit timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Portfolio audit failed", e)
                _portfolioAuditState.value = UiState.Error(e.localizedMessage ?: "Portfolio audit failed.")
            }
        }
    }

    fun clearPortfolioAudit() {
        auditJob?.cancel()
        _portfolioAuditState.value = UiState.Idle
    }

    fun addPosition(
        symbol: String,
        exchange: String,
        companyName: String,
        shares: Double,
        avgEntryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double,
        maxRiskPercent: Double,
        notes: String
    ) {
        viewModelScope.launch {
            val res = portfolioRepo.addPosition(
                userId = currentUserId,
                symbol = symbol,
                exchange = exchange,
                companyName = companyName,
                shares = shares,
                avgEntryPrice = avgEntryPrice,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                maxRiskPercent = maxRiskPercent,
                notes = notes
            )
            _feedbackMessage.value = res.fold(
                onSuccess = {
                    refreshMarketData()
                    "Added $symbol position"
                },
                onFailure = { it.localizedMessage ?: "Failed to add position" }
            )
        }
    }

    fun updatePosition(
        positionId: String,
        shares: Double,
        avgEntryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double,
        maxRiskPercent: Double,
        notes: String,
        status: PositionStatus = PositionStatus.ACTIVE
    ) {
        viewModelScope.launch {
            val res = portfolioRepo.updatePosition(
                userId = currentUserId,
                positionId = positionId,
                shares = shares,
                avgEntryPrice = avgEntryPrice,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                maxRiskPercent = maxRiskPercent,
                notes = notes,
                status = status
            )
            _feedbackMessage.value = res.fold(
                onSuccess = { if (status == PositionStatus.CLOSED) "Position closed" else "Position updated" },
                onFailure = { it.localizedMessage ?: "Failed to update position" }
            )
        }
    }

    fun deletePosition(positionId: String) {
        viewModelScope.launch {
            val res = portfolioRepo.deletePosition(currentUserId, positionId)
            _feedbackMessage.value = res.fold(
                onSuccess = { "Position removed" },
                onFailure = { it.localizedMessage ?: "Failed to delete position" }
            )
        }
    }

    fun toggleWatchlist(symbol: String, exchange: String, companyName: String, sector: String) {
        viewModelScope.launch {
            val currentList = (watchlistState.value as? UiState.Success)?.data.orEmpty()
            val exists = currentList.any { it.symbol.equals(symbol, ignoreCase = true) }
            val res = if (exists) {
                portfolioRepo.removeFromWatchlist(currentUserId, symbol)
            } else {
                portfolioRepo.addToWatchlist(currentUserId, symbol, exchange, companyName, sector)
            }
            _feedbackMessage.value = res.fold(
                onSuccess = {
                    refreshMarketData()
                    if (exists) "Removed $symbol from watchlist" else "Added $symbol to watchlist"
                },
                onFailure = { it.localizedMessage ?: "Watchlist update failed" }
            )
        }
    }

    fun addRiskAlert(symbol: String, alertType: AlertType, thresholdValue: Double, notes: String) {
        viewModelScope.launch {
            val res = portfolioRepo.addRiskAlert(currentUserId, symbol, alertType, thresholdValue, notes)
            _feedbackMessage.value = res.fold(
                onSuccess = {
                    refreshMarketData()
                    "Risk alert created for $symbol"
                },
                onFailure = { it.localizedMessage ?: "Failed to create risk alert" }
            )
        }
    }

    fun toggleRiskAlert(alertId: String, active: Boolean) {
        viewModelScope.launch {
            val res = portfolioRepo.toggleRiskAlert(currentUserId, alertId, active)
            res.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to toggle alert"
            }
        }
    }

    fun deleteRiskAlert(alertId: String) {
        viewModelScope.launch {
            val res = portfolioRepo.deleteRiskAlert(currentUserId, alertId)
            _feedbackMessage.value = res.fold(
                onSuccess = { "Alert deleted" },
                onFailure = { it.localizedMessage ?: "Failed to delete alert" }
            )
        }
    }

    fun saveRiskProfile(
        tolerance: RiskTolerance,
        accountCapital: Double,
        defaultRiskPerTradePct: Double,
        maxPortfolioDrawdownPct: Double,
        preferredTimeframe: String
    ) {
        viewModelScope.launch {
            val res = portfolioRepo.saveRiskProfile(
                userId = currentUserId,
                riskTolerance = tolerance,
                accountCapital = accountCapital,
                defaultRiskPerTradePct = defaultRiskPerTradePct,
                maxPortfolioDrawdownPct = maxPortfolioDrawdownPct,
                preferredTimeframe = preferredTimeframe
            )
            _feedbackMessage.value = res.fold(
                onSuccess = { "Risk profile saved" },
                onFailure = { it.localizedMessage ?: "Failed to save risk profile" }
            )
        }
    }

    fun consumeFeedback() {
        _feedbackMessage.value = null
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private fun <T> handleStreamError(streamName: String, error: Throwable): UiState<T> {
        val fsError = unwrapFirestoreError(error)
        if (fsError == null && error is CancellationException) throw error
        val root = fsError ?: error
        Log.w(TAG, "Error observing $streamName", root)
        return UiState.Error(root.message ?: "Failed to observe $streamName")
    }

    companion object {
        private const val TAG = "TradePulseVM"
        private const val STOP_TIMEOUT_MS = 5000L
    }
}
