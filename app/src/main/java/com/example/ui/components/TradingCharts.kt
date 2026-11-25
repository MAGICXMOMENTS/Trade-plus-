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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PriceCandlestickChart(
    bars: List<OhlcvBar>,
    technicals: TechnicalAnalysisResult,
    currencySymbol: String = "$",
    predictedUp: Boolean? = null,
    showBollinger: Boolean = true,
    showVwap: Boolean = false,
    showSupertrend: Boolean = true,
    showFibonacci: Boolean = false,
    showBuySellMarkers: Boolean = true,
    showTradeLevels: Boolean = true,
    chartHeight: Dp = 240.dp,
    onOpenFullChart: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return

    val visibleCount = min(bars.size, 90)
    val startIndex = bars.size - visibleCount
    val visibleBars = bars.subList(startIndex, bars.size)
    val ema21 = technicals.ema21Series.subList(startIndex, bars.size)
    val ema50 = technicals.ema50Series.subList(startIndex, bars.size)
    val bbUpper = technicals.bollingerUpperSeries.subList(startIndex, bars.size)
    val bbLower = technicals.bollingerLowerSeries.subList(startIndex, bars.size)
    val vwapSeries = technicals.vwapSeries.subList(startIndex, bars.size)
    val stSeries = technicals.supertrendSeries.subList(startIndex, bars.size)
    val stBullSeries = technicals.supertrendBullSeries.subList(startIndex, bars.size)
    val visibleMarkers = remember(technicals.tradeMarkers, startIndex, bars.size) {
        technicals.tradeMarkers.filter { it.barIndex in startIndex until bars.size }
    }
    val markerByLocalIdx = remember(visibleMarkers, startIndex) {
        visibleMarkers.associateBy { it.barIndex - startIndex }
    }

    var selectedIndex by remember(bars) { mutableStateOf<Int?>(null) }
    val activeLocalIdx = selectedIndex ?: visibleBars.lastIndex
    val activeBar = visibleBars[activeLocalIdx]
    val activeMarker = markerByLocalIdx[activeLocalIdx]

    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.US) }
    val markerTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ObsidianBg.toArgb()
            textSize = 22f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${dateFormatter.format(Date(activeBar.timestampSec * 1000L))}  O:$currencySymbol${fmt(activeBar.open)} H:$currencySymbol${fmt(activeBar.high)} L:$currencySymbol${fmt(activeBar.low)} C:$currencySymbol${fmt(activeBar.close)}",
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
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            var rawMin = visibleBars.minOf { it.low }
            var rawMax = visibleBars.maxOf { it.high }
            if (showBollinger) {
                bbLower.filterNotNull().minOrNull()?.let { rawMin = min(rawMin, it) }
                bbUpper.filterNotNull().maxOrNull()?.let { rawMax = max(rawMax, it) }
            }
            if (showSupertrend) {
                stSeries.filterNotNull().minOrNull()?.let { rawMin = min(rawMin, it) }
                stSeries.filterNotNull().maxOrNull()?.let { rawMax = max(rawMax, it) }
            }
            if (showTradeLevels) {
                rawMin = min(rawMin, technicals.stopLossLevel)
                rawMax = max(rawMax, technicals.takeProfit1)
            }
            val rawSpan = (rawMax - rawMin).coerceAtLeast(0.01)
            val minPrice = rawMin - rawSpan * 0.06
            val maxPrice = rawMax + rawSpan * 0.06
            val range = (maxPrice - minPrice).coerceAtLeast(0.01)

            val stopLossPct = ((technicals.stopLossLevel - technicals.currentPrice) / technicals.currentPrice.coerceAtLeast(0.01)) * 100.0

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(visibleBars) {
                        detectTapGestures { offset ->
                            val idx = ((offset.x / (size.width * 0.88f)) * visibleBars.size)
                                .roundToInt()
                                .coerceIn(0, visibleBars.lastIndex)
                            selectedIndex = idx
                        }
                    }
                    .pointerInput(visibleBars) {
                        detectDragGestures(
                            onDragEnd = { selectedIndex = null },
                            onDragCancel = { selectedIndex = null }
                        ) { change, _ ->
                            val idx = ((change.position.x / (size.width * 0.88f)) * visibleBars.size)
                                .roundToInt()
                                .coerceIn(0, visibleBars.lastIndex)
                            selectedIndex = idx
                        }
                    }
            ) {
                val w = size.width
                val candleAreaW = w * 0.88f
                val h = size.height
                val topPad = 24f
                val bottomPad = 24f
                val chartH = (h - topPad - bottomPad).coerceAtLeast(10f)

                fun yFor(price: Double): Float {
                    return topPad + chartH * (1f - ((price - minPrice) / range).toFloat())
                }

                // Subtle horizontal grid
                val gridEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                for (step in 0..3) {
                    val y = topPad + (chartH / 3f) * step
                    drawLine(
                        color = TerminalBorder.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                        pathEffect = gridEffect
                    )
                }

                // Optional Fibonacci Retracement levels
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
                                color = RiskAmber.copy(alpha = 0.45f),
                                start = Offset(0f, fy),
                                end = Offset(candleAreaW, fy),
                                strokeWidth = 1.5f,
                                pathEffect = fibDash
                            )
                        }
                    }
                }

                // Marked Buy Entry Zone, Sell Take-Profit Target & Stop-Loss Floor
                if (showTradeLevels) {
                    val buyHighY = yFor(technicals.entryZoneHigh.coerceIn(minPrice, maxPrice))
                    val buyLowY = yFor(technicals.entryZoneLow.coerceIn(minPrice, maxPrice))
                    val buyBandTop = min(buyHighY, buyLowY)
                    val buyBandHeight = max(3f, kotlin.math.abs(buyLowY - buyHighY))
                    drawRect(
                        color = AiCyan.copy(alpha = 0.11f),
                        topLeft = Offset(0f, buyBandTop),
                        size = Size(w, buyBandHeight)
                    )
                    drawLine(
                        color = AiCyan.copy(alpha = 0.7f),
                        start = Offset(0f, buyBandTop),
                        end = Offset(w, buyBandTop),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    val slY = yFor(technicals.stopLossLevel)
                    drawRect(
                        color = BearCoral.copy(alpha = 0.10f),
                        topLeft = Offset(0f, slY),
                        size = Size(w, (h - slY).coerceAtLeast(0f))
                    )
                    val levelDash = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    drawLine(
                        color = BearCoral.copy(alpha = 0.92f),
                        start = Offset(0f, slY),
                        end = Offset(w, slY),
                        strokeWidth = 2.6f,
                        pathEffect = levelDash
                    )

                    val tpY = yFor(technicals.takeProfit1)
                    drawLine(
                        color = BullEmerald.copy(alpha = 0.90f),
                        start = Offset(0f, tpY),
                        end = Offset(w, tpY),
                        strokeWidth = 2.4f,
                        pathEffect = levelDash
                    )
                }

                val stepX = candleAreaW / visibleBars.size.coerceAtLeast(1)
                val candleW = (stepX * 0.62f).coerceIn(2f, 16f)

                // Bollinger envelope
                if (showBollinger) {
                    val upperPath = Path()
                    val lowerPath = Path()
                    var uStarted = false
                    var lStarted = false
                    for (i in visibleBars.indices) {
                        val x = stepX * i + stepX * 0.5f
                        bbUpper[i]?.let {
                            val y = yFor(it)
                            if (!uStarted) { upperPath.moveTo(x, y); uStarted = true } else upperPath.lineTo(x, y)
                        }
                        bbLower[i]?.let {
                            val y = yFor(it)
                            if (!lStarted) { lowerPath.moveTo(x, y); lStarted = true } else lowerPath.lineTo(x, y)
                        }
                    }
                    drawPath(upperPath, color = AiCyan.copy(alpha = 0.35f), style = Stroke(1.5f))
                    drawPath(lowerPath, color = AiCyan.copy(alpha = 0.35f), style = Stroke(1.5f))
                }

                // Supertrend trailing line segments
                if (showSupertrend) {
                    for (i in 1 until visibleBars.size) {
                        val prevVal = stSeries[i - 1] ?: continue
                        val curVal = stSeries[i] ?: continue
                        val curBull = stBullSeries[i] ?: true
                        val prevBull = stBullSeries[i - 1] ?: curBull
                        if (curBull == prevBull) {
                            val x1 = stepX * (i - 1) + stepX * 0.5f
                            val x2 = stepX * i + stepX * 0.5f
                            drawLine(
                                color = if (curBull) BullEmerald.copy(alpha = 0.78f) else BearCoral.copy(alpha = 0.78f),
                                start = Offset(x1, yFor(prevVal)),
                                end = Offset(x2, yFor(curVal)),
                                strokeWidth = 2.4f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // Candlesticks
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
                        strokeWidth = 2f
                    )

                    val bodyTop = min(yOpen, yClose)
                    val bodyHeight = max(2f, kotlin.math.abs(yClose - yOpen))
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
                        vwapSeries[i]?.let {
                            val y = yFor(it)
                            if (!vStarted) { vwapPath.moveTo(x, y); vStarted = true } else vwapPath.lineTo(x, y)
                        }
                    }
                    drawPath(vwapPath, color = VwapViolet, style = Stroke(2.4f, cap = StrokeCap.Round))
                }

                // EMA 21 & EMA 50 overlays
                val ema21Path = Path()
                var e21Started = false
                val ema50Path = Path()
                var e50Started = false
                for (i in visibleBars.indices) {
                    val x = stepX * i + stepX * 0.5f
                    ema21[i]?.let {
                        val y = yFor(it)
                        if (!e21Started) { ema21Path.moveTo(x, y); e21Started = true } else ema21Path.lineTo(x, y)
                    }
                    ema50[i]?.let {
                        val y = yFor(it)
                        if (!e50Started) { ema50Path.moveTo(x, y); e50Started = true } else ema50Path.lineTo(x, y)
                    }
                }
                drawPath(ema21Path, color = AiCyan, style = Stroke(2.5f, cap = StrokeCap.Round))
                drawPath(ema50Path, color = RiskAmber, style = Stroke(2.5f, cap = StrokeCap.Round))

                // Historical BUY ("B") & SELL ("S") Trade Markers directly on candles
                if (showBuySellMarkers) {
                    visibleMarkers.forEach { marker ->
                        val localIdx = marker.barIndex - startIndex
                        val bar = visibleBars.getOrNull(localIdx) ?: return@forEach
                        val cx = stepX * localIdx + stepX * 0.5f
                        if (marker.isBuy) {
                            val tipY = (yFor(bar.low) + 4f).coerceAtMost(h - 24f)
                            val circleCy = (tipY + 14f).coerceAtMost(h - 11f)
                            val tri = Path().apply {
                                moveTo(cx, tipY)
                                lineTo(cx - 5f, tipY + 7f)
                                lineTo(cx + 5f, tipY + 7f)
                                close()
                            }
                            drawPath(tri, color = BullEmerald)
                            drawCircle(color = BullEmerald, radius = 10f, center = Offset(cx, circleCy))
                            drawContext.canvas.nativeCanvas.drawText(
                                "B",
                                cx,
                                circleCy + 7.5f,
                                markerTextPaint
                            )
                        } else {
                            val tipY = (yFor(bar.high) - 4f).coerceAtLeast(24f)
                            val circleCy = (tipY - 14f).coerceAtLeast(11f)
                            val tri = Path().apply {
                                moveTo(cx, tipY)
                                lineTo(cx - 5f, tipY - 7f)
                                lineTo(cx + 5f, tipY - 7f)
                                close()
                            }
                            drawPath(tri, color = BearCoral)
                            drawCircle(color = BearCoral, radius = 10f, center = Offset(cx, circleCy))
                            drawContext.canvas.nativeCanvas.drawText(
                                "S",
                                cx,
                                circleCy + 7.5f,
                                markerTextPaint
                            )
                        }
                    }
                }

                // Forecast trajectory arrow extending right of the last candle
                if (predictedUp != null) {
                    val lastX = stepX * visibleBars.lastIndex + stepX * 0.5f
                    val lastY = yFor(visibleBars.last().close)
                    val targetPrice = if (predictedUp) technicals.takeProfit1 else technicals.stopLossLevel
                    val targetY = yFor(targetPrice)
                    val endX = w - 8f
                    val projColor = if (predictedUp) BullEmerald else BearCoral
                    val projDash = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
                    drawLine(
                        color = projColor,
                        start = Offset(lastX, lastY),
                        end = Offset(endX, targetY),
                        strokeWidth = 3.2f,
                        pathEffect = projDash,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = projColor.copy(alpha = 0.28f),
                        radius = 10f,
                        center = Offset(endX, targetY)
                    )
                    drawCircle(
                        color = projColor,
                        radius = 5.5f,
                        center = Offset(endX, targetY)
                    )
                }

                // Crosshair line
                selectedIndex?.let { idx ->
                    val cx = stepX * idx + stepX * 0.5f
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, h),
                        strokeWidth = 1.5f
                    )
                }
            }

            if (showTradeLevels) {
                Surface(
                    color = BullEmeraldDim.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "SELL TP $currencySymbol${fmt(technicals.takeProfit1)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BullEmerald,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = AiCyan.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "BUY ZONE $currencySymbol${fmt(technicals.entryZoneLow)}–$currencySymbol${fmt(technicals.entryZoneHigh)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AiCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = BearCoralDim.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "SL $currencySymbol${fmt(technicals.stopLossLevel)} (${String.format(Locale.US, "%.1f%%", stopLossPct)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = BearCoral,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
    modifier: Modifier = Modifier
) {
    val count = min(technicals.rsiSeries.size, 90)
    if (count == 0) return
    val start = technicals.rsiSeries.size - count

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(98.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TerminalSurface)
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stepX = w / count.coerceAtLeast(1)

            when (oscillatorType) {
                OscillatorType.RSI -> {
                    fun yRsi(v: Double): Float = h * (1f - (v.coerceIn(0.0, 100.0) / 100.0).toFloat())
                    val y70 = yRsi(70.0)
                    val y30 = yRsi(30.0)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    drawLine(BearCoral.copy(alpha = 0.5f), Offset(0f, y70), Offset(w, y70), 1.5f, pathEffect = dash)
                    drawLine(BullEmerald.copy(alpha = 0.5f), Offset(0f, y30), Offset(w, y30), 1.5f, pathEffect = dash)

                    val path = Path()
                    var started = false
                    for (i in 0 until count) {
                        val v = technicals.rsiSeries[start + i] ?: continue
                        val x = stepX * i + stepX * 0.5f
                        val y = yRsi(v)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                    drawPath(path, color = AiCyan, style = Stroke(2.5f, cap = StrokeCap.Round))
                }
                OscillatorType.MFI -> {
                    fun yMfi(v: Double): Float = h * (1f - (v.coerceIn(0.0, 100.0) / 100.0).toFloat())
                    val y80 = yMfi(80.0)
                    val y20 = yMfi(20.0)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    drawLine(BearCoral.copy(alpha = 0.5f), Offset(0f, y80), Offset(w, y80), 1.5f, pathEffect = dash)
                    drawLine(BullEmerald.copy(alpha = 0.5f), Offset(0f, y20), Offset(w, y20), 1.5f, pathEffect = dash)

                    val path = Path()
                    var started = false
                    for (i in 0 until count) {
                        val v = technicals.mfiSeries.getOrNull(start + i) ?: continue
                        val x = stepX * i + stepX * 0.5f
                        val y = yMfi(v)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                    drawPath(path, color = VwapViolet, style = Stroke(2.5f, cap = StrokeCap.Round))
                }
                OscillatorType.STOCHASTIC -> {
                    fun yStoch(v: Double): Float = h * (1f - (v.coerceIn(0.0, 100.0) / 100.0).toFloat())
                    val y80 = yStoch(80.0)
                    val y20 = yStoch(20.0)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    drawLine(BearCoral.copy(alpha = 0.5f), Offset(0f, y80), Offset(w, y80), 1.5f, pathEffect = dash)
                    drawLine(BullEmerald.copy(alpha = 0.5f), Offset(0f, y20), Offset(w, y20), 1.5f, pathEffect = dash)

                    val kPath = Path()
                    val dPath = Path()
                    var kStarted = false
                    var dStarted = false
                    for (i in 0 until count) {
                        val x = stepX * i + stepX * 0.5f
                        technicals.stochKSeries.getOrNull(start + i)?.let {
                            val y = yStoch(it)
                            if (!kStarted) { kPath.moveTo(x, y); kStarted = true } else kPath.lineTo(x, y)
                        }
                        technicals.stochDSeries.getOrNull(start + i)?.let {
                            val y = yStoch(it)
                            if (!dStarted) { dPath.moveTo(x, y); dStarted = true } else dPath.lineTo(x, y)
                        }
                    }
                    drawPath(kPath, color = AiCyan, style = Stroke(2.3f, cap = StrokeCap.Round))
                    drawPath(dPath, color = RiskAmber, style = Stroke(2.0f, cap = StrokeCap.Round))
                }
                OscillatorType.MACD -> {
                    val mSlice = technicals.macdSeries.subList(start, technicals.macdSeries.size)
                    val sSlice = technicals.macdSignalSeries.subList(start, technicals.macdSignalSeries.size)
                    val hSlice = technicals.macdHistSeries.subList(start, technicals.macdHistSeries.size)

                    val allVals = (mSlice + sSlice + hSlice).filterNotNull()
                    val maxAbs = allVals.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(0.01) ?: 1.0
                    val midY = h / 2f

                    fun yMacd(v: Double): Float = midY - ((v / maxAbs).toFloat() * (h * 0.44f))

                    drawLine(TerminalBorder, Offset(0f, midY), Offset(w, midY), 1f)

                    val barW = (stepX * 0.55f).coerceIn(2f, 12f)
                    for (i in 0 until count) {
                        val hv = hSlice[i] ?: continue
                        val cx = stepX * i + stepX * 0.5f
                        val barY = yMacd(hv)
                        val top = min(midY, barY)
                        val height = max(1.5f, kotlin.math.abs(barY - midY))
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
                        mSlice[i]?.let {
                            val y = yMacd(it)
                            if (!mStart) { macdPath.moveTo(x, y); mStart = true } else macdPath.lineTo(x, y)
                        }
                        sSlice[i]?.let {
                            val y = yMacd(it)
                            if (!sStart) { sigPath.moveTo(x, y); sStart = true } else sigPath.lineTo(x, y)
                        }
                    }
                    drawPath(macdPath, color = AiCyan, style = Stroke(2.2f))
                    drawPath(sigPath, color = RiskAmber, style = Stroke(2.0f))
                }
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
                val h = size.height
                val minVal = min(strategyCurve.min(), benchmarkCurve.min())
                val maxVal = max(strategyCurve.max(), benchmarkCurve.max())
                val range = (maxVal - minVal).coerceAtLeast(0.5)

                fun yFor(v: Double): Float = 10f + (h - 20f) * (1f - ((v - minVal) / range).toFloat())

                val benchPath = Path()
                val stratPath = Path()
                val fillPath = Path()
                val stepX = w / (strategyCurve.size - 1).coerceAtLeast(1)

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
                fillPath.lineTo(w, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(BullEmerald.copy(alpha = 0.24f), Color.Transparent)
                    )
                )
                drawPath(benchPath, color = TextMuted.copy(alpha = 0.8f), style = Stroke(2.2f))
                drawPath(stratPath, color = BullEmerald, style = Stroke(3.2f, cap = StrokeCap.Round))
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

private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
