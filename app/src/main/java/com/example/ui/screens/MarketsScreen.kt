package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TradingViewQuote
import com.example.data.model.UiState
import com.example.data.remote.currencyFor
import com.example.data.remote.isIndianStock
import com.example.ui.components.ErrorCard
import com.example.ui.components.LoadingCard
import com.example.ui.components.SentimentBar
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearCoral
import com.example.ui.theme.BearCoralDim
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.BullEmeraldDim
import com.example.ui.theme.MonoNumericMedium
import com.example.ui.theme.MonoNumericSmall
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradePulseViewModel
import java.util.Locale

private enum class MarketFilter(val label: String) {
    ALL("All Markets"),
    INDIA("India NSE"),
    US("US Markets"),
    WATCHLIST("Watchlist"),
    BULLISH("Bullish Consensus"),
    OVERSOLD("RSI < 38"),
    MOMENTUM("High Volume"),
    BREAKOUT("Near 52W High"),
    HIGH_VOL("High Volatility")
}

private val QuickStockPresets = listOf(
    "RELIANCE" to "NSE",
    "TCS" to "NSE",
    "HDFCBANK" to "NSE",
    "INFY" to "NSE",
    "ICICIBANK" to "NSE",
    "SBIN" to "NSE",
    "TATAMOTORS" to "NSE",
    "NVDA" to "NASDAQ",
    "AAPL" to "NASDAQ",
    "MSFT" to "NASDAQ",
    "TSLA" to "NASDAQ",
    "SPY" to "AMEX"
)

@Composable
fun MarketsScreen(
    viewModel: TradePulseViewModel,
    onOpenStock: (symbol: String, exchange: String) -> Unit
) {
    val quotesUi by viewModel.marketQuotesState.collectAsStateWithLifecycle()
    val watchlistUi by viewModel.watchlistState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchUi by viewModel.searchResultsState.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf(MarketFilter.ALL) }

    val watchlistSet = remember(watchlistUi) {
        (watchlistUi as? UiState.Success)?.data?.map { it.symbol.uppercase() }?.toSet().orEmpty()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TRADINGVIEW GLOBAL & INDIA NSE SCANNER",
                            style = MaterialTheme.typography.labelMedium,
                            color = AiCyan
                        )
                        Text(
                            text = "Markets & Signals",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshMarketData() },
                        modifier = Modifier.testTag("refresh_markets_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Scanner", tint = TextSecondary)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search Indian & US stocks (e.g. RELIANCE, TCS, NVDA)...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("market_search_input")
                )
            }

            // Quick-pick Indian NSE & US Tickers for 1-tap Chart & Search
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Quick Open Chart (India NSE & US)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickStockPresets.forEach { (sym, exch) ->
                            val isInd = exch == "NSE"
                            AssistChip(
                                onClick = {
                                    viewModel.onSearchQueryChanged("")
                                    onOpenStock(sym, exch)
                                },
                                label = { Text(if (isInd) "$sym (NSE)" else sym) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = TerminalSurfaceElevated,
                                    labelColor = if (isInd) BullEmerald else AiCyan
                                ),
                                border = BorderStroke(1.dp, TerminalBorder),
                                modifier = Modifier.testTag("quick_chip_$sym")
                            )
                        }
                    }
                }
            }

            if (searchQuery.isNotBlank()) {
                when (val s = searchUi) {
                    is UiState.Loading -> item { LoadingCard("Searching TradingView India & Global symbols...") }
                    is UiState.Error -> item { ErrorCard(s.message) }
                    is UiState.Success -> {
                        if (s.data.isEmpty()) {
                            item {
                                Text(
                                    text = "No matching symbols found on TradingView.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            items(s.data, key = { "${it.exchange}:${it.symbol}" }) { hit ->
                                val inWatchlist = hit.symbol.uppercase() in watchlistSet
                                val isInd = isIndianStock(hit.exchange, hit.symbol)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = TerminalSurfaceElevated),
                                    border = BorderStroke(1.dp, TerminalBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.onSearchQueryChanged("")
                                            onOpenStock(hit.symbol, hit.exchange)
                                        }
                                        .testTag("search_result_${hit.symbol}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${hit.symbol} • ${hit.exchange}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (isInd) {
                                                    Surface(
                                                        color = BullEmeraldDim,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "INR",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = BullEmerald,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = hit.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.toggleWatchlist(
                                                    hit.symbol,
                                                    hit.exchange,
                                                    hit.description,
                                                    "Equities"
                                                )
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (inWatchlist) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                                contentDescription = "Toggle Watchlist",
                                                tint = if (inWatchlist) RiskAmber else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MarketFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AiCyanDim,
                                    selectedLabelColor = AiCyan
                                ),
                                modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                when (val qState = quotesUi) {
                    is UiState.Loading -> item { LoadingCard("Collecting live TradingView US & India NSE telemetry...") }
                    is UiState.Error -> item {
                        ErrorCard(qState.message, onRetry = { viewModel.refreshMarketData() })
                    }
                    is UiState.Success -> {
                        val suggestions = buildMarketSuggestions(qState.data)
                        if (suggestions.isNotEmpty() && selectedFilter in setOf(MarketFilter.ALL, MarketFilter.INDIA, MarketFilter.US)) {
                            val scopedSuggestions = suggestions.filter { s ->
                                when (selectedFilter) {
                                    MarketFilter.INDIA -> isIndianStock(s.quote.exchange, s.quote.symbol)
                                    MarketFilter.US -> !isIndianStock(s.quote.exchange, s.quote.symbol)
                                    else -> true
                                }
                            }
                            if (scopedSuggestions.isNotEmpty()) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AiCyan, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "Top AI & Quant Suggestions",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(scopedSuggestions, key = { "sug_${it.quote.exchange}_${it.quote.symbol}" }) { item ->
                                                MarketSuggestionCard(
                                                    suggestion = item,
                                                    onClick = { onOpenStock(item.quote.symbol, item.quote.exchange) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val filtered = qState.data.filter { q ->
                            when (selectedFilter) {
                                MarketFilter.ALL -> true
                                MarketFilter.INDIA -> isIndianStock(q.exchange, q.symbol)
                                MarketFilter.US -> !isIndianStock(q.exchange, q.symbol)
                                MarketFilter.WATCHLIST -> q.symbol.uppercase() in watchlistSet
                                MarketFilter.BULLISH -> q.tvRecommendAll >= 0.1
                                MarketFilter.OVERSOLD -> (q.tvRsi ?: 50.0) < 38.0
                                MarketFilter.MOMENTUM -> q.avgVolume10d > 0 && q.volume > q.avgVolume10d
                                MarketFilter.BREAKOUT -> q.high52w != null && q.high52w > 0 && q.price >= q.high52w * 0.94
                                MarketFilter.HIGH_VOL -> q.tvAtr != null && q.price > 0 && (q.tvAtr / q.price) >= 0.025
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                                    border = BorderStroke(1.dp, TerminalBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (selectedFilter == MarketFilter.WATCHLIST)
                                            "Your watchlist is empty. Tap the star icon on any stock or search above to select stocks."
                                        else
                                            "No stocks match this screener filter right now.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            }
                        } else {
                            items(filtered, key = { "${it.exchange}:${it.symbol}" }) { quote ->
                                MarketStockCard(
                                    quote = quote,
                                    inWatchlist = quote.symbol.uppercase() in watchlistSet,
                                    onToggleWatchlist = {
                                        viewModel.toggleWatchlist(
                                            quote.symbol,
                                            quote.exchange,
                                            quote.companyName,
                                            quote.sector
                                        )
                                    },
                                    onClick = { onOpenStock(quote.symbol, quote.exchange) }
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun MarketStockCard(
    quote: TradingViewQuote,
    inWatchlist: Boolean,
    onToggleWatchlist: () -> Unit,
    onClick: () -> Unit
) {
    val isUp = quote.changePct >= 0.0
    val chgColor = if (isUp) BullEmerald else BearCoral
    val currency = currencyFor(quote.exchange, quote.symbol)
    val tvRatingLabel = when {
        quote.tvRecommendAll >= 0.5 -> "TV: STRONG BUY"
        quote.tvRecommendAll >= 0.1 -> "TV: BUY"
        quote.tvRecommendAll <= -0.5 -> "TV: STRONG SELL"
        quote.tvRecommendAll <= -0.1 -> "TV: SELL"
        else -> "TV: NEUTRAL"
    }
    val tvColor = when {
        quote.tvRecommendAll >= 0.1 -> BullEmerald
        quote.tvRecommendAll <= -0.1 -> BearCoral
        else -> RiskAmber
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("market_card_${quote.symbol}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = quote.symbol,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = TerminalSurfaceElevated,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = quote.exchange.ifBlank { "US" },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isIndianStock(quote.exchange, quote.symbol)) BullEmerald else TextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${quote.companyName} • ${quote.sector}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$currency${String.format(Locale.US, "%,.2f", quote.price)}",
                            style = MonoNumericMedium
                        )
                        Surface(
                            color = if (isUp) BullEmeraldDim else BearCoralDim,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%+.2f%%", quote.changePct),
                                style = MonoNumericSmall,
                                color = chgColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    IconButton(onClick = onToggleWatchlist) {
                        Icon(
                            imageVector = if (inWatchlist) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (inWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                            tint = if (inWatchlist) RiskAmber else TextSecondary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = tvColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, tvColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = tvRatingLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = tvColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "RSI: ${quote.tvRsi?.let { String.format(Locale.US, "%.1f", it) } ?: "—"}  •  ADX: ${quote.tvAdx?.let { String.format(Locale.US, "%.1f", it) } ?: "—"}",
                    style = MonoNumericSmall,
                    color = TextSecondary
                )
            }

            SentimentBar(
                score = quote.sentimentScore,
                label = quote.sentimentLabel
            )
        }
    }
}

private data class MarketSuggestionItem(
    val quote: TradingViewQuote,
    val isUp: Boolean,
    val badgeText: String,
    val actionHint: String,
    val stopLoss: Double,
    val targetPrice: Double,
    val rankScore: Double
)

private fun buildMarketSuggestions(quotes: List<TradingViewQuote>): List<MarketSuggestionItem> {
    return quotes
        .filter { it.price > 0.0 }
        .map { q ->
            val atr = (q.tvAtr ?: (q.price * 0.02)).coerceAtLeast(q.price * 0.005)
            val rsi = q.tvRsi ?: 50.0
            val adx = q.tvAdx ?: 18.0
            val maBonus = if (q.tvEma20 != null && q.price > q.tvEma20) 0.18 else -0.18
            val rsiBonus = when {
                rsi < 35.0 -> 0.22
                rsi in 48.0..66.0 -> 0.15
                rsi > 74.0 -> -0.25
                else -> 0.0
            }
            val score = q.tvRecommendAll + maBonus + rsiBonus + (if (adx >= 25.0) 0.12 else 0.0)
            val isUp = score >= 0.0
            val badgeText = when {
                score >= 0.45 -> "STRONG UP"
                isUp -> "BUY SETUP"
                score <= -0.45 -> "STRONG DOWN"
                else -> "BEAR RISK"
            }
            val actionHint = when {
                rsi < 35.0 -> "Oversold bounce setup"
                score >= 0.45 -> "Trend + momentum aligned"
                isUp -> "Bullish above EMA20"
                else -> "Protect capital / stay flat"
            }
            val sl = (q.price - 1.8 * atr).coerceAtLeast(0.01)
            val target = if (isUp) q.price + 2.4 * atr else (q.price - 2.0 * atr).coerceAtLeast(0.01)
            MarketSuggestionItem(
                quote = q,
                isUp = isUp,
                badgeText = badgeText,
                actionHint = actionHint,
                stopLoss = sl,
                targetPrice = target,
                rankScore = kotlin.math.abs(score) + (if (q.avgVolume10d > 0 && q.volume > q.avgVolume10d) 0.15 else 0.0)
            )
        }
        .sortedByDescending { it.rankScore }
        .take(10)
}

@Composable
private fun MarketSuggestionCard(
    suggestion: MarketSuggestionItem,
    onClick: () -> Unit
) {
    val q = suggestion.quote
    val currency = currencyFor(q.exchange, q.symbol)
    val dirColor = if (suggestion.isUp) BullEmerald else BearCoral
    val dirBg = if (suggestion.isUp) BullEmeraldDim else BearCoralDim

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurfaceElevated),
        border = BorderStroke(1.dp, dirColor.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .width(215.dp)
            .clickable { onClick() }
            .testTag("suggestion_card_${q.symbol}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = q.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = dirBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (suggestion.isUp) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = dirColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = suggestion.badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = dirColor
                        )
                    }
                }
            }
            Text(
                text = "$currency${String.format(Locale.US, "%,.2f", q.price)}  (${String.format(Locale.US, "%+.2f%%", q.changePct)})",
                style = MonoNumericSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = suggestion.actionHint,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TGT $currency${String.format(Locale.US, "%.0f", suggestion.targetPrice)}",
                    style = MonoNumericSmall,
                    color = dirColor
                )
                Text(
                    text = "SL $currency${String.format(Locale.US, "%.0f", suggestion.stopLoss)}",
                    style = MonoNumericSmall,
                    color = BearCoral
                )
            }
        }
    }
}

