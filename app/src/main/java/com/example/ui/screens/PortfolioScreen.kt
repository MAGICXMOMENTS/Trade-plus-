package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AlertSeverity
import com.example.data.model.Position
import com.example.data.model.PositionStatus
import com.example.data.model.TradingViewQuote
import com.example.data.model.UiState
import com.example.data.model.WatchlistStock
import com.example.data.remote.currencyFor
import com.example.ui.components.ErrorCard
import com.example.ui.components.LoadingCard
import com.example.ui.components.PositionEditorDialog
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearCoral
import com.example.ui.theme.BearCoralDim
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.BullEmeraldDim
import com.example.ui.theme.MonoNumericLarge
import com.example.ui.theme.MonoNumericMedium
import com.example.ui.theme.MonoNumericSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.RiskAmberDim
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradePulseViewModel
import java.util.Locale

private data class PortfolioSummaryMetrics(
    val totalValue: Double,
    val totalCost: Double,
    val unrealizedPnl: Double,
    val unrealizedPnlPct: Double,
    val profitableCount: Int,
    val totalCount: Int,
    val bestSymbol: String?,
    val bestReturnPct: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: TradePulseViewModel,
    onOpenStock: (symbol: String, exchange: String) -> Unit,
    onOpenAlertsTab: () -> Unit,
    onOpenMarketsTab: () -> Unit
) {
    val positionsUi by viewModel.positionsState.collectAsStateWithLifecycle()
    val watchlistUi by viewModel.watchlistState.collectAsStateWithLifecycle()
    val liveQuotes by viewModel.liveQuoteMap.collectAsStateWithLifecycle()
    val riskProfile by viewModel.riskProfileState.collectAsStateWithLifecycle()
    val triggeredAlerts by viewModel.triggeredRiskAlerts.collectAsStateWithLifecycle()
    val auditState by viewModel.portfolioAuditState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var prefillWatchlistStock by remember { mutableStateOf<WatchlistStock?>(null) }
    var editingPosition by remember { mutableStateOf<Position?>(null) }
    var showAuditSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ObsidianBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    prefillWatchlistStock = null
                    showAddDialog = true
                },
                containerColor = BullEmerald,
                contentColor = ObsidianBg,
                modifier = Modifier.testTag("add_position_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Tracked Stock")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .testTag("portfolio_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CLOUD PORTFOLIO TELEMETRY",
                                style = MaterialTheme.typography.labelMedium,
                                color = AiCyan
                            )
                            Text(
                                text = "My Portfolio",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onOpenMarketsTab,
                                modifier = Modifier.testTag("portfolio_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search Indian & Global Stocks",
                                    tint = AiCyan
                                )
                            }
                            IconButton(
                                onClick = { viewModel.refreshMarketData() },
                                modifier = Modifier.testTag("refresh_portfolio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh Market Prices",
                                    tint = TextSecondary
                                )
                            }
                            Button(
                                onClick = {
                                    showAuditSheet = true
                                    viewModel.runPortfolioRiskAudit()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AiCyanDim,
                                    contentColor = AiCyan
                                ),
                                border = BorderStroke(1.dp, AiCyan.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("ai_portfolio_audit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("AI Audit", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                when (val state = positionsUi) {
                    is UiState.Loading -> {
                        item { LoadingCard("Fetching tracked portfolio positions from Firestore...") }
                    }
                    is UiState.Error -> {
                        item { ErrorCard(state.message) }
                    }
                    is UiState.Success -> {
                        val activePositions = state.data.filter { it.statusEnum() == PositionStatus.ACTIVE }
                        val closedPositions = state.data.filter { it.statusEnum() == PositionStatus.CLOSED }

                        val metrics = computePortfolioMetrics(activePositions, liveQuotes)

                        item {
                            PortfolioPerformanceCard(
                                metrics = metrics,
                                riskTolerance = riskProfile.toleranceEnum().label,
                                alertCount = triggeredAlerts.size
                            )
                        }

                        if (triggeredAlerts.isNotEmpty()) {
                            item {
                                val topAlert = triggeredAlerts.first()
                                val isCritical = topAlert.severity == AlertSeverity.CRITICAL
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCritical) BearCoralDim.copy(alpha = 0.65f) else RiskAmberDim.copy(alpha = 0.65f)
                                    ),
                                    border = BorderStroke(1.dp, if (isCritical) BearCoral else RiskAmber),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenAlertsTab() }
                                        .testTag("triggered_alert_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.NotificationsActive,
                                            contentDescription = "Risk Alert",
                                            tint = if (isCritical) BearCoral else RiskAmber
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${triggeredAlerts.size} Active Risk Alert${if (triggeredAlerts.size > 1) "s" else ""}: ${topAlert.title}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = topAlert.detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Tracked Holdings (${activePositions.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (activePositions.isEmpty()) {
                            item {
                                EmptyHoldingsCard(
                                    onAddClick = {
                                        prefillWatchlistStock = null
                                        showAddDialog = true
                                    },
                                    onExploreMarkets = onOpenMarketsTab
                                )
                            }
                        } else {
                            items(activePositions, key = { it.id }) { pos ->
                                TrackedHoldingCard(
                                    position = pos,
                                    quote = liveQuotes[pos.symbol.uppercase()],
                                    totalPortfolioValue = metrics.totalValue,
                                    onInspect = { onOpenStock(pos.symbol, pos.exchange) },
                                    onEdit = { editingPosition = pos },
                                    onClosePosition = {
                                        viewModel.updatePosition(
                                            positionId = pos.id,
                                            shares = pos.shares,
                                            avgEntryPrice = pos.avgEntryPrice,
                                            stopLossPrice = pos.stopLossPrice,
                                            takeProfitPrice = pos.takeProfitPrice,
                                            maxRiskPercent = pos.maxRiskPercent,
                                            notes = pos.notes,
                                            status = PositionStatus.CLOSED
                                        )
                                    }
                                )
                            }
                        }

                        // User-Tracked Watchlist Stocks fetched from Firestore
                        when (val wState = watchlistUi) {
                            is UiState.Success -> {
                                val trackedWatchlist = wState.data
                                if (trackedWatchlist.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Watchlist Stocks (${trackedWatchlist.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }
                                    items(trackedWatchlist, key = { it.id.ifBlank { it.symbol } }) { stock ->
                                        WatchlistSummaryRow(
                                            stock = stock,
                                            quote = liveQuotes[stock.symbol.uppercase()],
                                            onInspect = { onOpenStock(stock.symbol, stock.exchange) },
                                            onTrackPosition = {
                                                prefillWatchlistStock = stock
                                                showAddDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                            is UiState.Error -> item { ErrorCard(wState.message) }
                            else -> Unit
                        }

                        if (closedPositions.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Closed Positions (${closedPositions.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextMuted,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(closedPositions, key = { it.id }) { pos ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                                    border = BorderStroke(1.dp, TerminalBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            val pCurrency = currencyFor(pos.exchange, pos.symbol)
                                            Text(
                                                text = "${pos.symbol} • ${fmtShares(pos.shares)} sh @ $pCurrency${fmtMoney(pos.avgEntryPrice)}",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "CLOSED",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deletePosition(pos.id) }) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Delete closed position",
                                                tint = TextMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                    else -> Unit
                }
            }
        }
    }

    if (showAddDialog) {
        val wStock = prefillWatchlistStock
        val prefillPrice = wStock?.let { liveQuotes[it.symbol.uppercase()]?.price }
        PositionEditorDialog(
            prefillSymbol = wStock?.symbol.orEmpty(),
            prefillExchange = wStock?.exchange.orEmpty(),
            prefillCompanyName = wStock?.companyName.orEmpty(),
            prefillPrice = prefillPrice,
            onDismiss = {
                showAddDialog = false
                prefillWatchlistStock = null
            },
            onSave = { symbol, exchange, companyName, shares, avgEntry, sl, tp, maxRisk, notes ->
                viewModel.addPosition(symbol, exchange, companyName, shares, avgEntry, sl, tp, maxRisk, notes)
                showAddDialog = false
                prefillWatchlistStock = null
            }
        )
    }

    editingPosition?.let { pos ->
        PositionEditorDialog(
            existingPosition = pos,
            onDismiss = { editingPosition = null },
            onSave = { _, _, _, shares, avgEntry, sl, tp, maxRisk, notes ->
                viewModel.updatePosition(pos.id, shares, avgEntry, sl, tp, maxRisk, notes)
                editingPosition = null
            }
        )
    }

    if (showAuditSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAuditSheet = false
                viewModel.clearPortfolioAudit()
            },
            containerColor = TerminalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AiCyan)
                    Text("Gemini Portfolio Risk Audit", style = MaterialTheme.typography.titleLarge)
                }
                when (val s = auditState) {
                    is UiState.Loading -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AiCyan)
                            Text("Analyzing live positions & TradingView risk telemetry...", color = TextSecondary)
                        }
                    }
                    is UiState.Streaming -> {
                        Text(s.partialText, style = MaterialTheme.typography.bodyMedium)
                    }
                    is UiState.Success -> {
                        Text(s.data, style = MaterialTheme.typography.bodyMedium)
                    }
                    is UiState.Error -> {
                        ErrorCard(s.message, onRetry = { viewModel.runPortfolioRiskAudit() })
                    }
                    else -> Unit
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun computePortfolioMetrics(
    positions: List<Position>,
    quotes: Map<String, TradingViewQuote>
): PortfolioSummaryMetrics {
    var totalCost = 0.0
    var totalValue = 0.0
    var profitable = 0
    var bestSym: String? = null
    var bestPct = Double.NEGATIVE_INFINITY

    for (pos in positions) {
        val live = quotes[pos.symbol.uppercase()]?.price ?: pos.avgEntryPrice
        val cost = pos.shares * pos.avgEntryPrice
        val value = pos.shares * live
        totalCost += cost
        totalValue += value

        val retPct = if (pos.avgEntryPrice > 0.0) {
            ((live - pos.avgEntryPrice) / pos.avgEntryPrice) * 100.0
        } else 0.0
        if (retPct > 0.0) profitable++
        if (retPct > bestPct) {
            bestPct = retPct
            bestSym = pos.symbol
        }
    }

    val pnl = totalValue - totalCost
    val pnlPct = if (totalCost > 0.0) (pnl / totalCost) * 100.0 else 0.0

    return PortfolioSummaryMetrics(
        totalValue = totalValue,
        totalCost = totalCost,
        unrealizedPnl = pnl,
        unrealizedPnlPct = pnlPct,
        profitableCount = profitable,
        totalCount = positions.size,
        bestSymbol = bestSym,
        bestReturnPct = if (bestSym != null) bestPct else 0.0
    )
}

@Composable
private fun PortfolioPerformanceCard(
    metrics: PortfolioSummaryMetrics,
    riskTolerance: String,
    alertCount: Int
) {
    val positive = metrics.unrealizedPnl >= 0.0
    val pnlColor = if (positive) BullEmerald else BearCoral

    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, TerminalBorder),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(TerminalSurfaceElevated, TerminalSurface)
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOTAL PORTFOLIO VALUE",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Surface(
                    color = AiCyanDim,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$riskTolerance • $alertCount Alerts",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (alertCount > 0) RiskAmber else AiCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "$${fmtMoney(metrics.totalValue)}",
                style = MonoNumericLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (positive) BullEmeraldDim else BearCoralDim,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${if (positive) "+" else ""}$${fmtMoney(metrics.unrealizedPnl)} (${String.format(Locale.US, "%+.2f", metrics.unrealizedPnlPct)}%)",
                        style = MonoNumericSmall,
                        color = pnlColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "Total Return",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PortfolioStatTile(
                    label = "COST BASIS",
                    value = "$${fmtMoney(metrics.totalCost)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                PortfolioStatTile(
                    label = "WINNERS",
                    value = "${metrics.profitableCount}/${metrics.totalCount}",
                    color = AiCyan,
                    modifier = Modifier.weight(1f)
                )
                PortfolioStatTile(
                    label = "TOP MOVER",
                    value = metrics.bestSymbol?.let { "$it ${String.format(Locale.US, "%+.1f%%", metrics.bestReturnPct)}" } ?: "—",
                    color = if (metrics.bestReturnPct >= 0.0) BullEmerald else BearCoral,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PortfolioStatTile(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianBg.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(text = value, style = MonoNumericSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun TrackedHoldingCard(
    position: Position,
    quote: TradingViewQuote?,
    totalPortfolioValue: Double,
    onInspect: () -> Unit,
    onEdit: () -> Unit,
    onClosePosition: () -> Unit
) {
    val currency = currencyFor(position.exchange, position.symbol)
    val livePrice = quote?.price ?: position.avgEntryPrice
    val marketValue = livePrice * position.shares
    val costBasis = position.avgEntryPrice * position.shares
    val pnl = marketValue - costBasis
    val pnlPct = if (costBasis > 0.0) (pnl / costBasis) * 100.0 else 0.0
    val allocationPct = if (totalPortfolioValue > 0.0) (marketValue / totalPortfolioValue) * 100.0 else 0.0
    val isUp = pnl >= 0.0
    val pnlColor = if (isUp) BullEmerald else BearCoral

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() }
            .testTag("position_card_${position.symbol}")
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
                            text = position.symbol,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = TerminalSurfaceElevated,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", allocationPct)}% weight",
                                style = MaterialTheme.typography.labelSmall,
                                color = AiCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${position.companyName} • ${fmtShares(position.shares)} sh",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$currency${fmtMoney(marketValue)}",
                        style = MonoNumericMedium
                    )
                    Text(
                        text = "${if (isUp) "+" else ""}$currency${fmtMoney(pnl)} (${String.format(Locale.US, "%+.2f", pnlPct)}%)",
                        style = MonoNumericSmall,
                        color = pnlColor
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Now $currency${fmtMoney(livePrice)}",
                    style = MonoNumericSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Avg $currency${fmtMoney(position.avgEntryPrice)}",
                    style = MonoNumericSmall,
                    color = TextSecondary
                )
                Text(
                    text = "24h ${quote?.let { String.format(Locale.US, "%+.2f%%", it.changePct) } ?: "—"}",
                    style = MonoNumericSmall,
                    color = if ((quote?.changePct ?: 0.0) >= 0.0) BullEmerald else BearCoral
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SL ${if (position.stopLossPrice > 0) "$currency${fmtMoney(position.stopLossPrice)}" else "—"}  •  TP ${if (position.takeProfitPrice > 0) "$currency${fmtMoney(position.takeProfitPrice)}" else "—"}",
                    style = MonoNumericSmall,
                    color = TextMuted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onInspect) {
                        Text("Analyze", color = AiCyan)
                    }
                    TextButton(onClick = onClosePosition) {
                        Text("Close", color = RiskAmber)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Position", tint = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistSummaryRow(
    stock: WatchlistStock,
    quote: TradingViewQuote?,
    onInspect: () -> Unit,
    onTrackPosition: () -> Unit
) {
    val chg = quote?.changePct ?: 0.0
    val isUp = chg >= 0.0

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() }
            .testTag("watchlist_card_${stock.symbol}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${stock.symbol} • ${stock.exchange.ifBlank { "US" }}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stock.companyName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            val wCurrency = currencyFor(stock.exchange, stock.symbol)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = quote?.let { "$wCurrency${fmtMoney(it.price)}" } ?: "Syncing...",
                        style = MonoNumericSmall
                    )
                    if (quote != null) {
                        Text(
                            text = String.format(Locale.US, "%+.2f%%", chg),
                            style = MonoNumericSmall,
                            color = if (isUp) BullEmerald else BearCoral
                        )
                    }
                }
                TextButton(onClick = onTrackPosition) {
                    Text("+ Hold", color = BullEmerald)
                }
            }
        }
    }
}

@Composable
private fun EmptyHoldingsCard(
    onAddClick: () -> Unit,
    onExploreMarkets: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BullEmeraldDim),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = BullEmerald,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "No Tracked Holdings Yet",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Add a stock position with your shares and entry cost to monitor real-time portfolio value, return %, and risk alerts.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = BullEmerald, contentColor = ObsidianBg)
                ) {
                    Text("Add Stock", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onExploreMarkets,
                    border = BorderStroke(1.dp, AiCyan)
                ) {
                    Text("Browse Markets", color = AiCyan)
                }
            }
        }
    }
}

private fun fmtMoney(v: Double): String = String.format(Locale.US, "%,.2f", v)
private fun fmtShares(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.2f", v)
