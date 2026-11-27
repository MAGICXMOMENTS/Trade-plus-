package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChartTimeframe
import com.example.data.model.OhlcvBar
import com.example.data.model.OscillatorType
import com.example.data.model.TechnicalAnalysisResult
import com.example.ui.theme.AiCyan
import com.example.ui.theme.BearCoral
import com.example.ui.theme.BearCoralDim
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.BullEmeraldDim
import com.example.ui.theme.MonoNumericSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VwapViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val PLOT_AREA_FRACTION = 0.80f

@Composable
fun PriceCandlestickChart(
    bars: List<OhlcvBar>,
    technicals: TechnicalAnalysisResult,
    currencySymbol: String = "$",
    timeframe: ChartTimeframe? = null,
    predictedUp: Boolean? = null,
    showBollinger: Boolean = true,
    showVwap: Boolean = false,
    showSupertrend: Boolean = true,
    showFibonacci: Boolean = false,
    showBuySellMarkers: Boolean = true,
    showTradeLevels: Boolean = true,
    selectedIndex: Int? = null,
    onSelectedIndexChange: ((Int?) -> Unit)? = null,
    chartHeight: Dp = 248.dp,
    onOpenFullChart: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return

    val visibleBars = bars
    val ema21 = technicals.ema21Series
    val ema50 = technicals.ema50Series
    val bbUpper = technicals.bollingerUpperSeries
    val bbLower = technicals.bollingerLowerSeries
    val vwapSeries = technicals.vwapSeries
    val stSeries = technicals.supertrendSeries
    val stBullSeries = technicals.supertrendBullSeries
    val visibleMarkers = remember(technicals.tradeMarkers, bars.size) {
        technicals.tradeMarkers.filter { it.barIndex in bars.indices }
    }
    val markerByLocalIdx = remember(visibleMarkers) {
        visibleMarkers.associateBy { it.barIndex }
    }

    var internalSelectedIndex by remember(bars) { mutableStateOf<Int?>(null) }
    val resolvedSelectedIndex = (selectedIndex ?: internalSelectedIndex)?.coerceIn(0, visibleBars.lastIndex)
    val updateSelection: (Int?) -> Unit = { idx ->
        if (onSelectedIndexChange != null) {
            onSelectedIndexChange(idx)
        } else {
            internalSelectedIndex = idx
        }
    }

    val activeLocalIdx = resolvedSelectedIndex ?: visibleBars.lastIndex
    val activeBar = visibleBars[activeLocalIdx]
    val activeMarker = markerByLocalIdx[activeLocalIdx]

    val medianSpacingSec = remember(visibleBars) {
        if (visibleBars.size < 2) 86_400L
        else {
            val deltas = (1 until visibleBars.size)
                .map { visibleBars[it].timestampSec - visibleBars[it - 1].timestampSec }
                .filter { it > 0L }
                .sorted()
            if (deltas.isEmpty()) 86_400L else deltas[deltas.size / 2]
        }
    }
    val isIntradayBars = timeframe?.isIntraday ?: (medianSpacingSec < 43_200L)
    val spansMultipleDays = remember(visibleBars) {
        visibleBars.size >= 2 && (visibleBars.last().timestampSec - visibleBars.first().timestampSec) > 86_400L
    }

    val headerDateFormatter = remember(isIntradayBars, timeframe) {
        val pattern = when {
            timeframe == ChartTimeframe.Y5 -> "MMM d, yyyy"
            isIntradayBars -> "MMM d HH:mm"
            else -> "MMM d, yyyy"
        }
        SimpleDateFormat(pattern, Locale.US)
    }
    val axisDateFormatter = remember(isIntradayBars, spansMultipleDays, timeframe) {
        val pattern = when {
            timeframe == ChartTimeframe.Y5 -> "MMM ''yy"
            isIntradayBars && !spansMultipleDays -> "HH:mm"
            isIntradayBars -> "MM/dd HH:mm"
            timeframe == ChartTimeframe.Y1 -> "MMM ''yy"
            else -> "MMM d"
        }
        SimpleDateFormat(pattern, Locale.US)
    }

    val density = LocalDensity.current
    val axisLabelPx = with(density) { 10.sp.toPx() }
    val priceTagPx = with(density) { 10.sp.toPx() }
    val markerTextPx = with(density) { 9.sp.toPx() }
    val projReservePx = with(density) { if (predictedUp != null) 14.dp.toPx() else 0f }

    val markerTextPaint = remember(markerTextPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ObsidianBg.toArgb()
            textSize = markerTextPx
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val axisTextPaint = remember(axisLabelPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TextMuted.toArgb()
            textSize = axisLabelPx
            textAlign = Paint.Align.LEFT
            typeface = Typeface.MONOSPACE
        }
    }
    val xAxisTextPaint = remember(axisLabelPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TextMuted.toArgb()
            textSize = axisLabelPx
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }
    }
    val livePricePaint = remember(priceTagPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ObsidianBg.toArgb()
            textSize = priceTagPx
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TerminalSurface)
            .padding(12.dp)
    ) {
        // Crosshair Telemetry Header + Expand Chart Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${headerDateFormatter.format(Date(activeBar.timestampSec * 1000L))}  O:${formatCompact(activeBar.open)} H:${formatCompact(activeBar.high)} L:${formatCompact(activeBar.low)} C:${formatCompact(activeBar.close)}",
                    style = MonoNumericSmall,
                    color = if (activeBar.close >= activeBar.open) BullEmerald else BearCoral
                )
                if (activeMarker != null) {
                    Text(
                        text = "${if (activeMarker.isBuy) "BUY" else "SELL"} SIGNAL: ${activeMarker.reason} @ $currencySymbol${fmt(activeMarker.price)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeMarker.isBuy) BullEmerald else BearCoral
                    )
                }
            }
            if (onOpenFullChart != null) {
                IconButton(
                    onClick = onOpenFullChart,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("open_full_chart_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = "Open Full-Screen Chart",
                        tint = AiCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (showTradeLevels) {
            val stopLossPct = ((technicals.stopLossLevel - technicals.currentPrice) / technicals.currentPrice.coerceAtLeast(0.01)) * 100.0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(color = AiCyan.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "BUY ${formatAxisPrice(technicals.entryZoneLow, currencySymbol)}–${formatAxisPrice(technicals.entryZoneHigh, currencySymbol)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AiCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(color = BullEmeraldDim.copy(alpha = 0.9f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "TP1 ${formatAxisPrice(technicals.takeProfit1, currencySymbol)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BullEmerald,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(color = BearCoralDim.copy(alpha = 0.9f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "SL ${formatAxisPrice(technicals.stopLossLevel, currencySymbol)} (${String.format(Locale.US, "%.1f%%", stopLossPct)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = BearCoral,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        val barMin = visibleBars.minOf { it.low }
        val barMax = visibleBars.maxOf { it.high }
        val barSpan = (barMax - barMin).coerceAtLeast(barMax * 0.002).coerceAtLeast(0.01)

        var rawMin = barMin
        var rawMax = barMax
        if (showBollinger) {
            bbLower.filterNotNull()
                .filter { it >= barMin - barSpan * 0.6 && it <= barMax + barSpan * 0.6 }
                .minOrNull()?.let { rawMin = min(rawMin, it) }
            bbUpper.filterNotNull()
                .filter { it >= barMin - barSpan * 0.6 && it <= barMax + barSpan * 0.6 }
                .maxOrNull()?.let { rawMax = max(rawMax, it) }
        }
        if (showSupertrend) {
            stSeries.filterNotNull()
                .filter { it >= barMin - barSpan * 0.6 && it <= barMax + barSpan * 0.6 }
                .minOrNull()?.let { rawMin = min(rawMin, it) }
            stSeries.filterNotNull()
                .filter { it >= barMin - barSpan * 0.6 && it <= barMax + barSpan * 0.6 }
                .maxOrNull()?.let { rawMax = max(rawMax, it) }
        }
        if (showTradeLevels) {
            // Only expand Y-scale for SL/TP1 if they are close to the visible price range so
            // intraday (1D / 15m / 1W) candles never get squashed into a flat line.
            if (technicals.stopLossLevel in (barMin - barSpan * 0.25)..barMax) {
                rawMin = min(rawMin, technicals.stopLossLevel)
            }
            if (technicals.takeProfit1 in barMin..(barMax + barSpan * 0.25)) {
                rawMax = max(rawMax, technicals.takeProfit1)
            }
        }
        val rawSpan = (rawMax - rawMin).coerceAtLeast(0.01)
        val minPrice = rawMin - rawSpan * 0.06
        val maxPrice = rawMax + rawSpan * 0.06
        val range = (maxPrice - minPrice).coerceAtLeast(0.01)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(visibleBars, projReservePx) {
                        detectTapGestures { offset ->
                            val candlePlotW = (size.width * PLOT_AREA_FRACTION - projReservePx).coerceAtLeast(40f)
                            val idx = ((offset.x / candlePlotW) * visibleBars.size)
                                .toInt()
                                .coerceIn(0, visibleBars.lastIndex)
                            updateSelection(if (resolvedSelectedIndex == idx) null else idx)
                        }
                    }
                    .pointerInput(visibleBars, projReservePx) {
                        detectDragGestures(
                            onDragEnd = { updateSelection(null) },
                            onDragCancel = { updateSelection(null) }
                        ) { change, _ ->
                            val candlePlotW = (size.width * PLOT_AREA_FRACTION - projReservePx).coerceAtLeast(40f)
                            val idx = ((change.position.x / candlePlotW) * visibleBars.size)
                                .toInt()
                                .coerceIn(0, visibleBars.lastIndex)
                            updateSelection(idx)
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val plotW = w * PLOT_AREA_FRACTION
                val candlePlotW = (plotW - projReservePx).coerceAtLeast(40f)
                val topPad = 14f
                val bottomPad = with(density) { 18.dp.toPx() }
                val chartBottom = (h - bottomPad).coerceAtLeast(topPad + 20f)
                val chartH = (chartBottom - topPad).coerceAtLeast(10f)

                fun yFor(price: Double): Float {
                    return topPad + chartH * (1f - ((price - minPrice) / range).toFloat())
                }

                // Horizontal gridlines + Right Y-Axis price scale
                val gridEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                val gridSteps = 3
                for (step in 0..gridSteps) {
                    val frac = step.toFloat() / gridSteps
                    val y = topPad + chartH * frac
                    val gridPrice = maxPrice - range * frac
                    drawLine(
                        color = TerminalBorder.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(plotW, y),
                        strokeWidth = 1f,
                        pathEffect = gridEffect
                    )
                    val labelY = (y + axisLabelPx * 0.35f).coerceIn(topPad + axisLabelPx, chartBottom)
                    drawContext.canvas.nativeCanvas.drawText(
                        formatAxisPrice(gridPrice, currencySymbol),
                        plotW + 8f,
                        labelY,
                        axisTextPaint
                    )
                }

                // Vertical axis divider & bottom X-axis baseline
                drawLine(
                    color = TerminalBorder.copy(alpha = 0.7f),
                    start = Offset(plotW, topPad),
                    end = Offset(plotW, chartBottom),
                    strokeWidth = 1f
                )
                drawLine(
                    color = TerminalBorder.copy(alpha = 0.7f),
                    start = Offset(0f, chartBottom),
                    end = Offset(plotW, chartBottom),
                    strokeWidth = 1f
                )

                // X-Axis time/date labels
                val tickIndices = if (visibleBars.size <= 4) {
                    visibleBars.indices.toList()
                } else {
                    listOf(
                        0,
                        visibleBars.lastIndex / 3,
                        (visibleBars.lastIndex * 2) / 3,
                        visibleBars.lastIndex
                    ).distinct()
                }
                val stepX = candlePlotW / visibleBars.size.coerceAtLeast(1)
                tickIndices.forEach { tIdx ->
                    val label = axisDateFormatter.format(Date(visibleBars[tIdx].timestampSec * 1000L))
                    val rawX = stepX * tIdx + stepX * 0.5f
                    val halfText = xAxisTextPaint.measureText(label) / 2f
                    val clampedX = rawX.coerceIn(halfText + 2f, (plotW - halfText - 2f).coerceAtLeast(halfText + 2f))
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        clampedX,
                        h - 4f,
                        xAxisTextPaint
                    )
                }

                // Optional Fibonacci Retracement levels (only those inside visible range)
                if (showFibonacci) {
                    val fibDash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                    val fibs = listOf(
                        technicals.fibLevels.level382,
                        technicals.fibLevels.level500,
                        technicals.fibLevels.level618
                    )
                    fibs.forEach { lvl ->
                        if (lvl in minPrice..maxPrice) {
                            val fy = yFor(lvl)
                            drawLine(
                                color = RiskAmber.copy(alpha = 0.48f),
                                start = Offset(0f, fy),
                                end = Offset(plotW, fy),
                                strokeWidth = 1.4f,
                                pathEffect = fibDash
                            )
                        }
                    }
                }

                // Entry Zone, Take-Profit Target & Stop-Loss (drawn only when within visible Y-range)
                if (showTradeLevels) {
                    val entryHigh = technicals.entryZoneHigh
                    val entryLow = technicals.entryZoneLow
                    if (entryHigh >= minPrice && entryLow <= maxPrice) {
                        val bandTop = yFor(entryHigh.coerceIn(minPrice, maxPrice))
                        val bandBot = yFor(entryLow.coerceIn(minPrice, maxPrice))
                        val top = min(bandTop, bandBot)
                        val bandH = max(3f, abs(bandBot - bandTop))
                        drawRect(
                            color = AiCyan.copy(alpha = 0.11f),
                            topLeft = Offset(0f, top),
                            size = Size(plotW, bandH)
                        )
                        drawLine(
                            color = AiCyan.copy(alpha = 0.65f),
                            start = Offset(0f, top),
                            end = Offset(plotW, top),
                            strokeWidth = 1.4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    val levelDash = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    if (technicals.stopLossLevel in minPrice..maxPrice) {
                        val slY = yFor(technicals.stopLossLevel)
                        drawRect(
                            color = BearCoral.copy(alpha = 0.09f),
                            topLeft = Offset(0f, slY),
                            size = Size(plotW, (chartBottom - slY).coerceAtLeast(0f))
                        )
                        drawLine(
                            color = BearCoral.copy(alpha = 0.90f),
                            start = Offset(0f, slY),
                            end = Offset(plotW, slY),
                            strokeWidth = 2.2f,
                            pathEffect = levelDash
                        )
                    }

                    if (technicals.takeProfit1 in minPrice..maxPrice) {
                        val tpY = yFor(technicals.takeProfit1)
                        drawLine(
                            color = BullEmerald.copy(alpha = 0.88f),
                            start = Offset(0f, tpY),
                            end = Offset(plotW, tpY),
                            strokeWidth = 2.2f,
                            pathEffect = levelDash
                        )
                    }
                }

                val candleW = (stepX * 0.64f).coerceIn(1.3f, 16f)
                val wickW = (stepX * 0.18f).coerceIn(1f, 2.2f)

                // Bollinger envelope
                if (showBollinger) {
                    val upperPath = Path()
                    val lowerPath = Path()
                    var uStarted = false
                    var lStarted = false
                    for (i in visibleBars.indices) {
                        val x = stepX * i + stepX * 0.5f
                        bbUpper.getOrNull(i)?.let {
                            val y = yFor(it).coerceIn(topPad, chartBottom)
                            if (!uStarted) { upperPath.moveTo(x, y); uStarted = true } else upperPath.lineTo(x, y)
                        }
                        bbLower.getOrNull(i)?.let {
                            val y = yFor(it).coerceIn(topPad, chartBottom)
                            if (!lStarted) { lowerPath.moveTo(x, y); lStarted = true } else lowerPath.lineTo(x, y)
                        }
                    }
                    drawPath(upperPath, color = AiCyan.copy(alpha = 0.35f), style = Stroke(1.4f))
                    drawPath(lowerPath, color = AiCyan.copy(alpha = 0.35f), style = Stroke(1.4f))
                }

                // Supertrend trailing line segments
                if (showSupertrend) {
                    for (i in 1 until visibleBars.size) {
                        val prevVal = stSeries.getOrNull(i - 1) ?: continue
                        val curVal = stSeries.getOrNull(i) ?: continue
                        val curBull = stBullSeries.getOrNull(i) ?: true
                        val prevBull = stBullSeries.getOrNull(i - 1) ?: curBull
                        if (curBull == prevBull && prevVal in minPrice..maxPrice && curVal in minPrice..maxPrice) {
                            val x1 = stepX * (i - 1) + stepX * 0.5f
                            val x2 = stepX * i + stepX * 0.5f
                            drawLine(
                                color = if (curBull) BullEmerald.copy(alpha = 0.78f) else BearCoral.copy(alpha = 0.78f),
                                start = Offset(x1, yFor(prevVal)),
                                end = Offset(x2, yFor(curVal)),
                                strokeWidth = 2.2f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // Candlesticks (full OHLC accuracy)
                for (i in visibleBars.indices) {
                    val bar = visibleBars[i]
                    val cx = stepX * i + stepX * 0.5f
                    val isBull = bar.close >= bar.open
                    val color = if (isBull) BullEmerald else BearCoral

                    val yHigh = yFor(bar.high)
                    val yLow = yFor(bar.low)
                    val yOpen = yFor(bar.open)
                    val yClose = yFor(bar.close)

                    drawLine(
                        color = color,
                        start = Offset(cx, yHigh),
                        end = Offset(cx, yLow),
                        strokeWidth = wickW
                    )

                    val bodyTop = min(yOpen, yClose)
                    val bodyHeight = max(1.8f, abs(yClose - yOpen))
                    drawRect(
                        color = color,
                        topLeft = Offset(cx - candleW / 2f, bodyTop),
                        size = Size(candleW, bodyHeight)
                    )
                }

                // VWAP Overlay
                if (showVwap) {
                    val vwapPath = Path()
                    var vStarted = false
                    for (i in visibleBars.indices) {
                        val x = stepX * i + stepX * 0.5f
                        vwapSeries.getOrNull(i)?.let {
                            val y = yFor(it).coerceIn(topPad, chartBottom)
                            if (!vStarted) { vwapPath.moveTo(x, y); vStarted = true } else vwapPath.lineTo(x, y)
                        }
                    }
                    drawPath(vwapPath, color = VwapViolet, style = Stroke(2.2f, cap = StrokeCap.Round))
                }

                // EMA 21 & EMA 50 overlays
                val ema21Path = Path()
                var e21Started = false
                val ema50Path = Path()
                var e50Started = false
                for (i in visibleBars.indices) {
                    val x = stepX * i + stepX * 0.5f
                    ema21.getOrNull(i)?.let {
                        val y = yFor(it).coerceIn(topPad, chartBottom)
                        if (!e21Started) { ema21Path.moveTo(x, y); e21Started = true } else ema21Path.lineTo(x, y)
                    }
                    ema50.getOrNull(i)?.let {
                        val y = yFor(it).coerceIn(topPad, chartBottom)
                        if (!e50Started) { ema50Path.moveTo(x, y); e50Started = true } else ema50Path.lineTo(x, y)
                    }
                }
                drawPath(ema21Path, color = AiCyan, style = Stroke(2.2f, cap = StrokeCap.Round))
                drawPath(ema50Path, color = RiskAmber, style = Stroke(2.2f, cap = StrokeCap.Round))

                // Historical BUY ("B") & SELL ("S") Trade Markers cleanly inside plot bounds
                if (showBuySellMarkers) {
                    visibleMarkers.forEach { marker ->
                        val localIdx = marker.barIndex
                        val bar = visibleBars.getOrNull(localIdx) ?: return@forEach
                        val cx = stepX * localIdx + stepX * 0.5f
                        if (marker.isBuy) {
                            val tipY = (yFor(bar.low) + 4f).coerceAtMost(chartBottom - 22f)
                            val circleCy = (tipY + 13f).coerceAtMost(chartBottom - 10f)
                            val tri = Path().apply {
                                moveTo(cx, tipY)
                                lineTo(cx - 4.5f, tipY + 6f)
                                lineTo(cx + 4.5f, tipY + 6f)
                                close()
                            }
                            drawPath(tri, color = BullEmerald)
                            drawCircle(color = BullEmerald, radius = 9f, center = Offset(cx, circleCy))
                            drawContext.canvas.nativeCanvas.drawText(
                                "B",
                                cx,
                                circleCy + markerTextPx * 0.34f,
                                markerTextPaint
                            )
                        } else {
                            val tipY = (yFor(bar.high) - 4f).coerceAtLeast(topPad + 22f)
                            val circleCy = (tipY - 13f).coerceAtLeast(topPad + 10f)
                            val tri = Path().apply {
                                moveTo(cx, tipY)
                                lineTo(cx - 4.5f, tipY - 6f)
                                lineTo(cx + 4.5f, tipY - 6f)
                                close()
                            }
                            drawPath(tri, color = BearCoral)
                            drawCircle(color = BearCoral, radius = 9f, center = Offset(cx, circleCy))
                            drawContext.canvas.nativeCanvas.drawText(
                                "S",
                                cx,
                                circleCy + markerTextPx * 0.34f,
                                markerTextPaint
                            )
                        }
                    }
                }

                // Current/last bar horizontal price line & Y-axis pill tag
                val lastBar = visibleBars.last()
                val lastY = yFor(lastBar.close).coerceIn(topPad, chartBottom)
                val lastColor = if (lastBar.close >= lastBar.open) BullEmerald else BearCoral
                drawLine(
                    color = lastColor.copy(alpha = 0.65f),
                    start = Offset(0f, lastY),
                    end = Offset(plotW, lastY),
                    strokeWidth = 1.4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
                val pillH = priceTagPx + 10f
                val pillTop = (lastY - pillH / 2f).coerceIn(topPad, chartBottom - pillH)
                drawRoundRect(
                    color = lastColor,
                    topLeft = Offset(plotW + 4f, pillTop),
                    size = Size((w - plotW - 6f).coerceAtLeast(24f), pillH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    formatAxisPrice(lastBar.close, currencySymbol),
                    plotW + 8f,
                    pillTop + pillH / 2f + priceTagPx * 0.35f,
                    livePricePaint
                )

                // Forecast trajectory arrow inside plotW margin (never covers Y-axis labels)
                if (predictedUp != null) {
                    val lastX = stepX * visibleBars.lastIndex + stepX * 0.5f
                    val targetPrice = if (predictedUp) technicals.takeProfit1 else technicals.stopLossLevel
                    val targetY = yFor(targetPrice).coerceIn(topPad + 6f, chartBottom - 6f)
                    val endX = (plotW - 5f).coerceAtLeast(lastX + 6f)
                    val projColor = if (predictedUp) BullEmerald else BearCoral
                    val projDash = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    drawLine(
                        color = projColor,
                        start = Offset(lastX, lastY),
                        end = Offset(endX, targetY),
                        strokeWidth = 2.6f,
                        pathEffect = projDash,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = projColor.copy(alpha = 0.28f),
                        radius = 8f,
                        center = Offset(endX, targetY)
                    )
                    drawCircle(
                        color = projColor,
                        radius = 4.5f,
                        center = Offset(endX, targetY)
                    )
                }

                // Vertical + Horizontal Crosshair Lines
                resolvedSelectedIndex?.let { idx ->
                    val cx = stepX * idx + stepX * 0.5f
                    val cy = yFor(visibleBars[idx].close).coerceIn(topPad, chartBottom)
                    val crossDash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.55f),
                        start = Offset(cx, topPad),
                        end = Offset(cx, chartBottom),
                        strokeWidth = 1.4f,
                        pathEffect = crossDash
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(0f, cy),
                        end = Offset(plotW, cy),
                        strokeWidth = 1.2f,
                        pathEffect = crossDash
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.5f,
                        center = Offset(cx, cy)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showBuySellMarkers) {
                ChartLegendDot("B = Buy Signal", BullEmerald)
                ChartLegendDot("S = Sell Signal", BearCoral)
            }
            ChartLegendDot("EMA21", AiCyan)
            ChartLegendDot("EMA50", RiskAmber)
            if (showVwap) ChartLegendDot("VWAP", VwapViolet)
            if (showSupertrend) ChartLegendDot("Supertrend", if (technicals.supertrendBullish) BullEmerald else BearCoral)
            if (showTradeLevels) {
                ChartLegendDot("SELL TP1 $currencySymbol${fmt(technicals.takeProfit1)}", BullEmerald)
                ChartLegendDot("SL $currencySymbol${fmt(technicals.stopLossLevel)}", BearCoral)
            }
        }
    }
}

@Composable
fun OscillatorSubChart(
    technicals: TechnicalAnalysisResult,
    oscillatorType: OscillatorType,
    hasForecastGap: Boolean = true,
    selectedIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    val count = technicals.rsiSeries.size
    if (count == 0) return

    val density = LocalDensity.current
    val axisLabelPx = with(density) { 9.5.sp.toPx() }
    val projReservePx = with(density) { if (hasForecastGap) 14.dp.toPx() else 0f }
    val axisTextPaint = remember(axisLabelPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TextMuted.toArgb()
            textSize = axisLabelPx
            textAlign = Paint.Align.LEFT
            typeface = Typeface.MONOSPACE
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TerminalSurface)
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val plotW = w * PLOT_AREA_FRACTION
            val candlePlotW = (plotW - projReservePx).coerceAtLeast(40f)
            val stepX = candlePlotW / count.coerceAtLeast(1)

            drawLine(
                color = TerminalBorder.copy(alpha = 0.65f),
                start = Offset(plotW, 0f),
                end = Offset(plotW, h),
                strokeWidth = 1f
            )

            when (oscillatorType) {
                OscillatorType.RSI -> {
                    fun yRsi(v: Double): Float = h * (1f - (v.coerceIn(0.0, 100.0) / 100.0).toFloat())
                    val y70 = yRsi(70.0)
                    val y30 = yRsi(30.0)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    drawLine(BearCoral.copy(alpha = 0.5f), Offset(0f, y70), Offset(plotW, y70), 1.4f, pathEffect = dash)
                    drawLine(BullEmerald.copy(alpha = 0.5f), Offset(0f, y30), Offset(plotW, y30), 1.4f, pathEffect = dash)
                    drawContext.canvas.nativeCanvas.drawText("70", plotW + 8f, y70 + axisLabelPx * 0.35f, axisTextPaint)
                    drawContext.canvas.nativeCanvas.drawText("30", plotW + 8f, y30 + axisLabelPx * 0.35f, axisTextPaint)

                    val path = Path()
                    var started = false
                    for (i in 0 until count) {
                        val v = technicals.rsiSeries[i] ?: continue
                        val x = stepX * i + stepX * 0.5f
                        val y = yRsi(v)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                    drawPath(path, color = AiCyan, style = Stroke(2.2f, cap = StrokeCap.Round))
                }
                OscillatorType.MFI -> {
                    fun yMfi(v: Double): Float = h * (1f - (v.coerceIn(0.0, 100.0) / 100.0).toFloat())
                    val y80 = yMfi(80.0)
                    val y20 = yMfi(20.0)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    drawLine(BearCoral.copy(alpha = 0.5f), Offset(0f, y80), Offset(plotW, y80), 1.4f, pathEffect = dash)
                    drawLine(BullEmerald.copy(alpha = 0.5f), Offset(0f, y20), Offset(plotW, y20), 1.4f, pathEffect = dash)
                    drawContext.canvas.nativeCanvas.drawText("80", plotW + 8f, y80 + axisLabelPx * 0.35f, axisTextPaint)
                    drawContext.canvas.nativeCanvas.drawText("20", plotW + 8f, y20 + axisLabelPx * 0.35f, axisTextPaint)

                    val path = Path()
                    var started = false
                    for (i in 0 until count) {
                        val v = technicals.mfiSeries.getOrNull(i) ?: continue
                        val x = stepX * i + stepX * 0.5f
                        val y = yMfi(v)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                    drawPath(path, color = VwapViolet, style = Stroke(2.2f, cap = StrokeCap.Round))
                }
                OscillatorType.STOCHASTIC -> {
                    fun yStoch(v: Double): Float = h * (1f - (v.coerceIn(0.0, 100.0) / 100.0).toFloat())
                    val y80 = yStoch(80.0)
                    val y20 = yStoch(20.0)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    drawLine(BearCoral.copy(alpha = 0.5f), Offset(0f, y80), Offset(plotW, y80), 1.4f, pathEffect = dash)
                    drawLine(BullEmerald.copy(alpha = 0.5f), Offset(0f, y20), Offset(plotW, y20), 1.4f, pathEffect = dash)
                    drawContext.canvas.nativeCanvas.drawText("80", plotW + 8f, y80 + axisLabelPx * 0.35f, axisTextPaint)
                    drawContext.canvas.nativeCanvas.drawText("20", plotW + 8f, y20 + axisLabelPx * 0.35f, axisTextPaint)

                    val kPath = Path()
                    val dPath = Path()
                    var kStarted = false
                    var dStarted = false
                    for (i in 0 until count) {
                        val x = stepX * i + stepX * 0.5f
                        technicals.stochKSeries.getOrNull(i)?.let {
                            val y = yStoch(it)
                            if (!kStarted) { kPath.moveTo(x, y); kStarted = true } else kPath.lineTo(x, y)
                        }
                        technicals.stochDSeries.getOrNull(i)?.let {
                            val y = yStoch(it)
                            if (!dStarted) { dPath.moveTo(x, y); dStarted = true } else dPath.lineTo(x, y)
                        }
                    }
                    drawPath(kPath, color = AiCyan, style = Stroke(2.2f, cap = StrokeCap.Round))
                    drawPath(dPath, color = RiskAmber, style = Stroke(1.9f, cap = StrokeCap.Round))
                }
                OscillatorType.MACD -> {
                    val mSlice = technicals.macdSeries
                    val sSlice = technicals.macdSignalSeries
                    val hSlice = technicals.macdHistSeries

                    val allVals = (mSlice + sSlice + hSlice).filterNotNull()
                    val maxAbs = allVals.maxOfOrNull { abs(it) }?.coerceAtLeast(0.01) ?: 1.0
                    val midY = h / 2f

                    fun yMacd(v: Double): Float = midY - ((v / maxAbs).toFloat() * (h * 0.44f))

                    drawLine(TerminalBorder, Offset(0f, midY), Offset(plotW, midY), 1f)
                    drawContext.canvas.nativeCanvas.drawText("0.00", plotW + 8f, midY + axisLabelPx * 0.35f, axisTextPaint)

                    val barW = (stepX * 0.55f).coerceIn(1.2f, 12f)
                    for (i in 0 until count) {
                        val hv = hSlice.getOrNull(i) ?: continue
                        val cx = stepX * i + stepX * 0.5f
                        val barY = yMacd(hv)
                        val top = min(midY, barY)
                        val height = max(1.5f, abs(barY - midY))
                        drawRect(
                            color = if (hv >= 0) BullEmerald.copy(alpha = 0.65f) else BearCoral.copy(alpha = 0.65f),
                            topLeft = Offset(cx - barW / 2f, top),
                            size = Size(barW, height)
                        )
                    }

                    val macdPath = Path()
                    val sigPath = Path()
                    var mStart = false
                    var sStart = false
                    for (i in 0 until count) {
                        val x = stepX * i + stepX * 0.5f
                        mSlice.getOrNull(i)?.let {
                            val y = yMacd(it)
                            if (!mStart) { macdPath.moveTo(x, y); mStart = true } else macdPath.lineTo(x, y)
                        }
                        sSlice.getOrNull(i)?.let {
                            val y = yMacd(it)
                            if (!sStart) { sigPath.moveTo(x, y); sStart = true } else sigPath.lineTo(x, y)
                        }
                    }
                    drawPath(macdPath, color = AiCyan, style = Stroke(2.1f))
                    drawPath(sigPath, color = RiskAmber, style = Stroke(1.9f))
                }
            }

            selectedIndex?.takeIf { it in 0 until count }?.let { idx ->
                val cx = stepX * idx + stepX * 0.5f
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(cx, 0f),
                    end = Offset(cx, h),
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                )
            }
        }

        val headerText = when (oscillatorType) {
            OscillatorType.RSI -> "RSI(14): ${fmt(technicals.rsi14)}"
            OscillatorType.MACD -> "MACD: ${fmt(technicals.macd)} / Sig: ${fmt(technicals.macdSignal)}"
            OscillatorType.STOCHASTIC -> "Stoch %K/%D: ${fmt(technicals.stochK)} / ${fmt(technicals.stochD)}"
            OscillatorType.MFI -> "MFI(14): ${fmt(technicals.mfi14)} (Vol-Weighted)"
        }

        Text(
            text = headerText,
            style = MonoNumericSmall,
            color = TextSecondary,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

@Composable
fun RlEquityCurveChart(
    strategyCurve: List<Double>,
    benchmarkCurve: List<Double>,
    modifier: Modifier = Modifier
) {
    if (strategyCurve.size < 2 || benchmarkCurve.size < 2) return

    val density = LocalDensity.current
    val axisLabelPx = with(density) { 10.sp.toPx() }
    val axisTextPaint = remember(axisLabelPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TextMuted.toArgb()
            textSize = axisLabelPx
            textAlign = Paint.Align.LEFT
            typeface = Typeface.MONOSPACE
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TerminalSurface)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val plotW = w * PLOT_AREA_FRACTION
                val h = size.height
                val minVal = min(strategyCurve.min(), benchmarkCurve.min())
                val maxVal = max(strategyCurve.max(), benchmarkCurve.max())
                val range = (maxVal - minVal).coerceAtLeast(0.5)

                fun yFor(v: Double): Float = 10f + (h - 20f) * (1f - ((v - minVal) / range).toFloat())

                // 0% return baseline (equity = 100.0)
                if (100.0 in minVal..maxVal) {
                    val baseY = yFor(100.0)
                    drawLine(
                        color = TerminalBorder.copy(alpha = 0.7f),
                        start = Offset(0f, baseY),
                        end = Offset(plotW, baseY),
                        strokeWidth = 1.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                // Right Y-axis percentage return scale
                listOf(maxVal, (maxVal + minVal) / 2.0, minVal).forEach { lvl ->
                    val y = yFor(lvl)
                    val retPct = lvl - 100.0
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(Locale.US, "%+.1f%%", retPct),
                        plotW + 8f,
                        (y + axisLabelPx * 0.35f).coerceIn(axisLabelPx + 2f, h - 2f),
                        axisTextPaint
                    )
                }

                val benchPath = Path()
                val stratPath = Path()
                val fillPath = Path()
                val stepX = plotW / (strategyCurve.size - 1).coerceAtLeast(1)

                for (i in strategyCurve.indices) {
                    val x = stepX * i
                    val ys = yFor(strategyCurve[i])
                    val yb = yFor(benchmarkCurve[i])
                    if (i == 0) {
                        stratPath.moveTo(x, ys)
                        benchPath.moveTo(x, yb)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, ys)
                    } else {
                        stratPath.lineTo(x, ys)
                        benchPath.lineTo(x, yb)
                        fillPath.lineTo(x, ys)
                    }
                }
                fillPath.lineTo(plotW, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(BullEmerald.copy(alpha = 0.24f), Color.Transparent)
                    )
                )
                drawPath(benchPath, color = TextMuted.copy(alpha = 0.8f), style = Stroke(2.2f))
                drawPath(stratPath, color = BullEmerald, style = Stroke(3.0f, cap = StrokeCap.Round))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChartLegendDot("RL Q-Policy Equity", BullEmerald)
            ChartLegendDot("Buy & Hold Benchmark", TextMuted)
        }
    }
}

@Composable
private fun ChartLegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
                .padding(horizontal = 6.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

private fun formatAxisPrice(value: Double, currencySymbol: String): String {
    return when {
        value >= 10_000.0 -> "$currencySymbol${String.format(Locale.US, "%.0f", value)}"
        value >= 1000.0 -> "$currencySymbol${String.format(Locale.US, "%.1f", value)}"
        value < 10.0 -> "$currencySymbol${String.format(Locale.US, "%.3f", value)}"
        else -> "$currencySymbol${String.format(Locale.US, "%.2f", value)}"
    }
}

private fun formatCompact(v: Double): String =
    if (v < 10.0) String.format(Locale.US, "%.3f", v) else String.format(Locale.US, "%.2f", v)

private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
