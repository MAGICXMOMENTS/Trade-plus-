package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiForecastResult
import com.example.data.model.ChartTimeframe
import com.example.data.model.OscillatorType
import com.example.data.model.UiState
import com.example.data.remote.currencyFor
import com.example.quant.StrongPredictionResult
import com.example.quant.TradeSuggestion
import com.example.ui.components.AddRiskAlertDialog
import com.example.ui.components.ErrorCard
import com.example.ui.components.InteractivePositionCalculatorCard
import com.example.ui.components.KeyLevelsAndPivotsCard
import com.example.ui.components.LoadingCard
import com.example.ui.components.OscillatorSubChart
import com.example.ui.components.PriceCandlestickChart
import com.example.ui.components.RlActionBadge
import com.example.ui.components.SentimentBar
import com.example.ui.components.SignalBadge
import com.example.ui.components.TrailingStopAndVolatilityCard
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
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradePulseViewModel
import com.example.worker.AlertMonitorScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StockDetailScreen(
    viewModel: TradePulseViewModel,
    onBack: () -> Unit,
    onOpenAiLab: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val selectedSymbol by viewModel.selectedSymbol.collectAsStateWithLifecycle()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsStateWithLifecycle()
    val intelState by viewModel.stockIntelligenceState.collectAsStateWithLifecycle()
    val forecastState by viewModel.aiForecastState.collectAsStateWithLifecycle()
    val watchlistUi by viewModel.watchlistState.collectAsStateWithLifecycle()
    val riskProfile by viewModel.riskProfileState.collectAsStateWithLifecycle()

    var selectedOscillator by remember { mutableStateOf(OscillatorType.RSI) }
    var showBollinger by remember { mutableStateOf(true) }
    var showVwap by remember { mutableStateOf(true) }
    var showSupertrend by remember { mutableStateOf(true) }
    var showFibonacci by remember { mutableStateOf(false) }
    var showBuySellMarkers by remember { mutableStateOf(true) }
    var showFullChart by remember { mutableStateOf(false) }
    var selectedChartBarIdx by remember(selectedSymbol, selectedTimeframe) { mutableStateOf<Int?>(null) }

    var customPrefillAlertStop by remember { mutableStateOf<Double?>(null) }
    var showAlertDialog by remember { mutableStateOf(false) }

    val inWatchlist = remember(watchlistUi, selectedSymbol) {
        (watchlistUi as? UiState.Success)?.data?.any { it.symbol.equals(selectedSymbol, ignoreCase = true) } == true
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                        Text(
                            text = selectedSymbol,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val quote = (intelState as? UiState.Success)?.data?.quote
                    Row {
                        IconButton(
                            onClick = {
                                viewModel.toggleWatchlist(
                                    symbol = selectedSymbol,
                                    exchange = quote?.exchange.orEmpty(),
                                    companyName = quote?.companyName ?: selectedSymbol,
                                    sector = quote?.sector ?: "Equities"
                                )
                            }
                        ) {
                            Icon(
                                imageVector = if (inWatchlist) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Toggle Watchlist",
                                tint = if (inWatchlist) RiskAmber else TextSecondary
                            )
                        }
                        IconButton(onClick = {
                            customPrefillAlertStop = (intelState as? UiState.Success)?.data?.technicals?.stopLossLevel
                            showAlertDialog = true
                        }) {
                            Icon(Icons.Filled.AddAlert, contentDescription = "Add Risk Alert", tint = RiskAmber)
                        }
                    }
                }
            }

            // Timeframe selector
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChartTimeframe.entries.forEach { tf ->
                        FilterChip(
                            selected = selectedTimeframe == tf,
                            onClick = { viewModel.selectTimeframe(tf) },
                            label = { Text(tf.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AiCyanDim,
                                selectedLabelColor = AiCyan
                            ),
                            modifier = Modifier.testTag("timeframe_${tf.name.lowercase()}")
                        )
                    }
                }
            }

            when (val state = intelState) {
                is UiState.Loading -> {
                    item { LoadingCard("Fetching high-fidelity historical OHLCV & computing indicators...") }
                }
                is UiState.Error -> {
                    item { ErrorCard(state.message, onRetry = { viewModel.loadStockIntelligence(forceRefresh = true) }) }
                }
                is UiState.Success -> {
                    val bundle = state.data
                    val q = bundle.quote
                    val tech = bundle.technicals
                    val rl = bundle.rlPolicy
                    val pred = bundle.prediction
                    val isUp = q.changePct >= 0.0
                    val currency = currencyFor(q.exchange, q.symbol)
                    val aiForecast = (forecastState as? UiState.Success)?.data
                    val predictedUp = if (aiForecast != null) {
                        aiForecast.projectedTargetBase >= q.price
                    } else {
                        pred.isUp
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                            border = BorderStroke(1.dp, TerminalBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = q.companyName,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${q.exchange.ifBlank { "US" }} • ${q.sector}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "$currency${fmt(q.price)}",
                                            style = MonoNumericLarge
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%+.2f%% today", q.changePct),
                                            style = MonoNumericSmall,
                                            color = if (isUp) BullEmerald else BearCoral
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SignalBadge(tech.signalTier)
                                        RlActionBadge(rl.optimalAction)
                                    }
                                }
                                SentimentBar(score = q.sentimentScore, label = q.sentimentLabel)
                            }
                        }
                    }

                    // Prominent Strong Chart Direction Prediction, Buy/Sell Timing & Stop-Loss Card
                    item {
                        DirectionPredictionCard(
                            currency = currency,
                            currentPrice = q.price,
                            predictedUp = predictedUp,
                            prediction = pred,
                            aiForecast = aiForecast,
                            aiError = (forecastState as? UiState.Error)?.message,
                            isAiLoading = forecastState is UiState.Loading,
                            onVerifyWithAi = { viewModel.runAiForecast() },
                            onExpandChart = { showFullChart = true },
                            onSetStopLossAlert = {
                                customPrefillAlertStop = pred.stopLossPrice
                                showAlertDialog = true
                            }
                        )
                    }

                    // Interactive Price Candlestick Chart (Tap expand icon to open Full Screen)
                    item {
                        PriceCandlestickChart(
                            bars = bundle.bars,
                            technicals = tech,
                            currencySymbol = currency,
                            timeframe = bundle.timeframe,
                            predictedUp = predictedUp,
                            showBollinger = showBollinger,
                            showVwap = showVwap,
                            showSupertrend = showSupertrend,
                            showFibonacci = showFibonacci,
                            showBuySellMarkers = showBuySellMarkers,
                            showTradeLevels = true,
                            selectedIndex = selectedChartBarIdx,
                            onSelectedIndexChange = { selectedChartBarIdx = it },
                            onOpenFullChart = { showFullChart = true }
                        )
                    }

                    // Chart Overlay & Oscillator Switcher Chips
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = showBuySellMarkers,
                                    onClick = { showBuySellMarkers = !showBuySellMarkers },
                                    label = { Text("Buy/Sell Marks") },
                                    modifier = Modifier.testTag("toggle_buy_sell_markers"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BullEmeraldDim,
                                        selectedLabelColor = BullEmerald
                                    )
                                )
                                FilterChip(
                                    selected = showSupertrend,
                                    onClick = { showSupertrend = !showSupertrend },
                                    label = { Text("Supertrend") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AiCyanDim,
                                        selectedLabelColor = AiCyan
                                    )
                                )
                                FilterChip(
                                    selected = showVwap,
                                    onClick = { showVwap = !showVwap },
                                    label = { Text("VWAP") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AiCyanDim,
                                        selectedLabelColor = AiCyan
                                    )
                                )
                                FilterChip(
                                    selected = showBollinger,
                                    onClick = { showBollinger = !showBollinger },
                                    label = { Text("Bollinger") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AiCyanDim,
                                        selectedLabelColor = AiCyan
                                    )
                                )
                                FilterChip(
                                    selected = showFibonacci,
                                    onClick = { showFibonacci = !showFibonacci },
                                    label = { Text("Fib Levels") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AiCyanDim,
                                        selectedLabelColor = AiCyan
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OscillatorType.entries.forEach { osc ->
                                    FilterChip(
                                        selected = selectedOscillator == osc,
                                        onClick = { selectedOscillator = osc },
                                        label = { Text(osc.label) },
                                        modifier = Modifier.testTag("osc_${osc.name.lowercase()}")
                                    )
                                }
                            }
                            OscillatorSubChart(
                                technicals = tech,
                                oscillatorType = selectedOscillator,
                                hasForecastGap = true,
                                selectedIndex = selectedChartBarIdx
                            )
                        }
                    }

                    // Marked Buy & Sell Chart Signal Log
                    if (tech.tradeMarkers.isNotEmpty()) {
                        item {
                            ChartSignalHistoryCard(
                                currency = currency,
                                markers = tech.tradeMarkers,
                                isIntraday = selectedTimeframe.isIntraday
                            )
                        }
                    }

                    // Precise Entry & Exit Signals Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                            border = BorderStroke(1.dp, TerminalBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Precision Entry & Exit Signals", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "R:R 1:${fmt(tech.riskRewardRatio)}",
                                        style = MonoNumericSmall,
                                        color = AiCyan
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TradeLevelBox(
                                        label = "ENTRY ZONE",
                                        value = "$currency${fmt(tech.entryZoneLow)}–$currency${fmt(tech.entryZoneHigh)}",
                                        color = AiCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TradeLevelBox(
                                        label = "STOP-LOSS",
                                        value = "$currency${fmt(tech.stopLossLevel)}",
                                        color = BearCoral,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TradeLevelBox(
                                        label = "TAKE PROFIT 1",
                                        value = "$currency${fmt(tech.takeProfit1)}",
                                        color = BullEmerald,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TradeLevelBox(
                                        label = "TAKE PROFIT 2",
                                        value = "$currency${fmt(tech.takeProfit2)}",
                                        color = BullEmerald,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Useful Trading Tools: Volatility & Trailing Stop Suite
                    item {
                        TrailingStopAndVolatilityCard(
                            currencySymbol = currency,
                            technicals = tech
                        )
                    }

                    // Useful Trading Tools: Pivot Points & Fibonacci Support/Resistance
                    item {
                        KeyLevelsAndPivotsCard(
                            currencySymbol = currency,
                            technicals = tech
                        )
                    }

                    // Useful Trading Tools: Interactive Position Size & R:R Calculator
                    item {
                        InteractivePositionCalculatorCard(
                            symbol = q.symbol,
                            currencySymbol = currency,
                            defaultCapital = riskProfile.accountCapital,
                            defaultRiskPct = riskProfile.defaultRiskPerTradePct,
                            technicals = tech
                        )
                    }

                    // Multi-Indicator Breakdown (12 indicators)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                            border = BorderStroke(1.dp, TerminalBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Multi-Indicator Confluence (Score ${tech.compositeScore}/100)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                tech.breakdown.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(TerminalSurfaceElevated)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                item.stance,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = when (item.bullish) {
                                                    true -> BullEmerald
                                                    false -> BearCoral
                                                    null -> TextSecondary
                                                }
                                            )
                                        }
                                        Text(
                                            item.valueFormatted,
                                            style = MonoNumericSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = onOpenAiLab,
                            colors = ButtonDefaults.buttonColors(containerColor = AiCyan, contentColor = ObsidianBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("AI & RL Lab", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    // Full-Screen Interactive Chart Modal
    val currentSuccess = (intelState as? UiState.Success)?.data
    if (showFullChart && currentSuccess != null) {
        val q = currentSuccess.quote
        val tech = currentSuccess.technicals
        val pred = currentSuccess.prediction
        val currency = currencyFor(q.exchange, q.symbol)
        val aiForecast = (forecastState as? UiState.Success)?.data
        val predictedUp = if (aiForecast != null) {
            aiForecast.projectedTargetBase >= q.price
        } else {
            pred.isUp
        }

        Dialog(
            onDismissRequest = { showFullChart = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = ObsidianBg,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${q.symbol} • ${q.exchange.ifBlank { "US" }}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$currency${fmt(q.price)}  •  SL $currency${fmt(tech.stopLossLevel)}",
                                style = MonoNumericSmall,
                                color = BearCoral
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (predictedUp) BullEmeraldDim else BearCoralDim,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (predictedUp) "PREDICT: UP ▲" else "PREDICT: DOWN ▼",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (predictedUp) BullEmerald else BearCoral,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { showFullChart = false },
                                modifier = Modifier.testTag("close_full_chart_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close Full Chart")
                            }
                        }
                    }

                    // Buy/Sell timing & Stop-Loss strip inside Full Chart
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TerminalSurface)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "WHEN TO BUY: ${pred.whenToBuy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BullEmerald
                        )
                        Text(
                            text = "WHEN TO SELL: ${pred.whenToSell}",
                            style = MaterialTheme.typography.bodySmall,
                            color = RiskAmber
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChartTimeframe.entries.forEach { tf ->
                            FilterChip(
                                selected = selectedTimeframe == tf,
                                onClick = { viewModel.selectTimeframe(tf) },
                                label = { Text(tf.label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = showBuySellMarkers,
                            onClick = { showBuySellMarkers = !showBuySellMarkers },
                            label = { Text("Buy/Sell Marks") }
                        )
                        FilterChip(
                            selected = showSupertrend,
                            onClick = { showSupertrend = !showSupertrend },
                            label = { Text("Supertrend") }
                        )
                        FilterChip(
                            selected = showVwap,
                            onClick = { showVwap = !showVwap },
                            label = { Text("VWAP") }
                        )
                        FilterChip(
                            selected = showBollinger,
                            onClick = { showBollinger = !showBollinger },
                            label = { Text("Bollinger") }
                        )
                        FilterChip(
                            selected = showFibonacci,
                            onClick = { showFibonacci = !showFibonacci },
                            label = { Text("Fib Levels") }
                        )
                    }

                    PriceCandlestickChart(
                        bars = currentSuccess.bars,
                        technicals = tech,
                        currencySymbol = currency,
                        timeframe = currentSuccess.timeframe,
                        predictedUp = predictedUp,
                        showBollinger = showBollinger,
                        showVwap = showVwap,
                        showSupertrend = showSupertrend,
                        showFibonacci = showFibonacci,
                        showBuySellMarkers = showBuySellMarkers,
                        showTradeLevels = true,
                        selectedIndex = selectedChartBarIdx,
                        onSelectedIndexChange = { selectedChartBarIdx = it },
                        chartHeight = 330.dp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OscillatorType.entries.forEach { osc ->
                            FilterChip(
                                selected = selectedOscillator == osc,
                                onClick = { selectedOscillator = osc },
                                label = { Text(osc.label) }
                            )
                        }
                    }
                    OscillatorSubChart(
                        technicals = tech,
                        oscillatorType = selectedOscillator,
                        hasForecastGap = true,
                        selectedIndex = selectedChartBarIdx
                    )
                }
            }
        }
    }

    if (showAlertDialog) {
        val bundle = (intelState as? UiState.Success)?.data
        AddRiskAlertDialog(
            prefillSymbol = selectedSymbol,
            prefillPrice = bundle?.quote?.price,
            prefillStopLoss = customPrefillAlertStop ?: bundle?.technicals?.stopLossLevel,
            onDismiss = { showAlertDialog = false },
            onSave = { sym, type, threshold, notes ->
                viewModel.addRiskAlert(sym, type, threshold, notes)
                AlertMonitorScheduler.runImmediateCheck(context, forceNotify = false)
                showAlertDialog = false
            }
        )
    }
}

@Composable
private fun DirectionPredictionCard(
    currency: String,
    currentPrice: Double,
    predictedUp: Boolean,
    prediction: StrongPredictionResult,
    aiForecast: AiForecastResult?,
    aiError: String? = null,
    isAiLoading: Boolean,
    onVerifyWithAi: () -> Unit,
    onExpandChart: () -> Unit,
    onSetStopLossAlert: () -> Unit
) {
    val dirColor = if (predictedUp) BullEmerald else BearCoral
    val dirBg = if (predictedUp) BullEmeraldDim.copy(alpha = 0.45f) else BearCoralDim.copy(alpha = 0.45f)
    val stopLossDistPct = ((prediction.stopLossPrice - currentPrice) / currentPrice.coerceAtLeast(0.01)) * 100.0
    val target7d = aiForecast?.projectedTargetBase ?: prediction.target7d
    val target7dDistPct = ((target7d - currentPrice) / currentPrice.coerceAtLeast(0.01)) * 100.0
    val target30dDistPct = ((prediction.target30d - currentPrice) / currentPrice.coerceAtLeast(0.01)) * 100.0
    val dirProb = if (predictedUp) prediction.upProbabilityPct else (100 - prediction.upProbabilityPct)

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, dirColor.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chart_prediction_card")
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
                Surface(
                    color = dirBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, dirColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (predictedUp) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = if (predictedUp) "Predicted Up" else "Predicted Down",
                            tint = dirColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${prediction.direction.label} ($dirProb% PROB)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = dirColor
                        )
                    }
                }

                TextButton(
                    onClick = onExpandChart,
                    modifier = Modifier.testTag("expand_chart_text_button")
                ) {
                    Icon(Icons.Filled.OpenInFull, contentDescription = null, tint = AiCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Open Full Chart", color = AiCyan)
                }
            }

            // Primary Action Suggestion Banner
            Surface(
                color = AiCyanDim.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AiCyan.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("SUGGESTED PLAY • CONVICTION ${prediction.convictionScore}/100", style = MaterialTheme.typography.labelSmall, color = AiCyan)
                    Text(prediction.primaryAction, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(prediction.headlineSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            // Explicit When to Buy & When to Sell Timing Boxes
            Surface(
                color = BullEmeraldDim.copy(alpha = 0.28f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BullEmerald.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WHEN TO BUY (CHART ENTRY)", style = MaterialTheme.typography.labelSmall, color = BullEmerald, fontWeight = FontWeight.Bold)
                        if (prediction.buyNowRecommended) {
                            Surface(
                                color = BullEmerald.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "BUY ZONE ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BullEmerald,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(prediction.whenToBuy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Surface(
                color = BearCoralDim.copy(alpha = 0.22f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RiskAmber.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("WHEN TO SELL (TAKE-PROFIT / EXIT)", style = MaterialTheme.typography.labelSmall, color = RiskAmber, fontWeight = FontWeight.Bold)
                    Text(prediction.whenToSell, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(TerminalSurfaceElevated)
                        .padding(10.dp)
                ) {
                    Text(if (aiForecast != null) "AI TARGET" else "7D TARGET", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = "$currency${fmt(target7d)} (${String.format(Locale.US, "%+.1f%%", target7dDistPct)})",
                        style = MonoNumericSmall,
                        color = dirColor
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(TerminalSurfaceElevated)
                        .padding(10.dp)
                ) {
                    Text("30D TARGET", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = "$currency${fmt(prediction.target30d)} (${String.format(Locale.US, "%+.1f%%", target30dDistPct)})",
                        style = MonoNumericSmall,
                        color = AiCyan
                    )
                }
            }

            // Stop-Loss Alert Strip with 1-tap Alert Trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BearCoralDim.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = BearCoral, modifier = Modifier.size(14.dp))
                        Text("RECOMMENDED STOP LOSS", style = MaterialTheme.typography.labelSmall, color = BearCoral)
                    }
                    Text(
                        text = "$currency${fmt(prediction.stopLossPrice)} (${String.format(Locale.US, "%.1f%%", stopLossDistPct)})",
                        style = MonoNumericSmall,
                        color = BearCoral,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = onSetStopLossAlert,
                    modifier = Modifier.testTag("set_sl_alert_button")
                ) {
                    Icon(Icons.Filled.AddAlert, contentDescription = null, tint = BearCoral, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Set SL Alert", color = BearCoral, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Actionable Suggestions List
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ACTIONABLE SUGGESTIONS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                prediction.suggestions.forEach { sug ->
                    SuggestionRow(sug)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (aiForecast != null) "AI Forecast: ${aiForecast.shortTermBias} (${aiForecast.confidencePercent}%)" else "Tap AI Verify for AI macro & thesis check",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onVerifyWithAi,
                    enabled = !isAiLoading,
                    border = BorderStroke(1.dp, AiCyan.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("verify_ai_prediction_button")
                ) {
                    if (isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AiCyan, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AiCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("AI Verify", color = AiCyan, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (!aiError.isNullOrBlank()) {
                ErrorCard(
                    message = aiError,
                    onRetry = onVerifyWithAi
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: TradeSuggestion) {
    val badgeColor = when (suggestion.bullish) {
        true -> BullEmerald
        false -> BearCoral
        null -> RiskAmber
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(suggestion.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Surface(
                color = badgeColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = suggestion.actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(suggestion.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun ChartSignalHistoryCard(
    currency: String,
    markers: List<com.example.data.model.ChartTradeMarker>,
    isIntraday: Boolean
) {
    val recent = remember(markers) { markers.takeLast(6).reversed() }
    val dateFormatter = remember(isIntraday) {
        SimpleDateFormat(if (isIntraday) "MMM d HH:mm" else "MMM d", Locale.US)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, TerminalBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chart_signal_history_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Marked Chart Buy & Sell Signals",
                style = MaterialTheme.typography.titleMedium
            )
            recent.forEach { marker ->
                val color = if (marker.isBuy) BullEmerald else BearCoral
                val bg = if (marker.isBuy) BullEmeraldDim else BearCoralDim
                val dateStr = dateFormatter.format(Date(marker.timestampSec * 1000L))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TerminalSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (marker.isBuy) "BUY" else "SELL",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        Column {
                            Text(
                                text = marker.reason,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                    Text(
                        text = "$currency${fmt(marker.price)}",
                        style = MonoNumericSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeLevelBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(value, style = MonoNumericMedium, color = color)
    }
}

private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
