package com.example.quant

import com.example.data.model.AlertSeverity
import com.example.data.model.AlertType
import com.example.data.model.Position
import com.example.data.model.PositionStatus
import com.example.data.model.RiskAlertRule
import com.example.data.model.RiskProfile
import com.example.data.model.TradingViewQuote
import com.example.data.model.TriggeredRiskAlert
import com.example.data.remote.currencyFor
import java.util.Locale

object RiskAlertEvaluator {

    fun evaluate(
        positions: List<Position>,
        rules: List<RiskAlertRule>,
        quotes: Map<String, TradingViewQuote>,
        profile: RiskProfile
    ): List<TriggeredRiskAlert> {
        val alerts = mutableListOf<TriggeredRiskAlert>()

        val activePositions = positions.filter { it.statusEnum() == PositionStatus.ACTIVE }
        var totalCost = 0.0
        var totalValue = 0.0

        for (pos in activePositions) {
            val quote = quotes[pos.symbol.uppercase()] ?: continue
            val curr = currencyFor(quote.exchange.ifBlank { pos.exchange }, pos.symbol)
            val livePrice = quote.price
            val cost = pos.shares * pos.avgEntryPrice
            val value = pos.shares * livePrice
            totalCost += cost
            totalValue += value

            val pnlPct = if (pos.avgEntryPrice > 0.0) {
                ((livePrice - pos.avgEntryPrice) / pos.avgEntryPrice) * 100.0
            } else 0.0

            if (pos.stopLossPrice > 0.0 && livePrice <= pos.stopLossPrice) {
                alerts.add(
                    TriggeredRiskAlert(
                        id = "pos_sl_${pos.id}",
                        symbol = pos.symbol,
                        title = "${pos.symbol} Stop-Loss Breached",
                        detail = "Live $curr${fmt(livePrice)} fell below stop-loss $curr${fmt(pos.stopLossPrice)}. Consider capital protection exit.",
                        severity = AlertSeverity.CRITICAL
                    )
                )
            } else if (pos.stopLossPrice > 0.0 && livePrice <= pos.stopLossPrice * 1.02) {
                alerts.add(
                    TriggeredRiskAlert(
                        id = "pos_sl_near_${pos.id}",
                        symbol = pos.symbol,
                        title = "${pos.symbol} Approaching Stop-Loss",
                        detail = "Live $curr${fmt(livePrice)} is within 2% of your $curr${fmt(pos.stopLossPrice)} stop-loss.",
                        severity = AlertSeverity.WARNING
                    )
                )
            }

            if (pos.takeProfitPrice > 0.0 && livePrice >= pos.takeProfitPrice) {
                alerts.add(
                    TriggeredRiskAlert(
                        id = "pos_tp_${pos.id}",
                        symbol = pos.symbol,
                        title = "${pos.symbol} Take-Profit Target Reached",
                        detail = "Live $curr${fmt(livePrice)} hit target $curr${fmt(pos.takeProfitPrice)} (${fmtSigned(pnlPct)}%). Consider scaling out.",
                        severity = AlertSeverity.INFO
                    )
                )
            }

            if (pnlPct <= -pos.maxRiskPercent * 2.0) {
                alerts.add(
                    TriggeredRiskAlert(
                        id = "pos_dd_${pos.id}",
                        symbol = pos.symbol,
                        title = "${pos.symbol} Exceeds Risk Allocation",
                        detail = "Unrealized loss ${fmtSigned(pnlPct)}% exceeds 2x max risk threshold (${fmt(pos.maxRiskPercent)}%).",
                        severity = AlertSeverity.CRITICAL
                    )
                )
            }
        }

        if (totalCost > 0.0) {
            val portfolioPnlPct = ((totalValue - totalCost) / totalCost) * 100.0
            if (portfolioPnlPct <= -profile.maxPortfolioDrawdownPct) {
                alerts.add(
                    TriggeredRiskAlert(
                        id = "port_dd",
                        symbol = "PORTFOLIO",
                        title = "Portfolio Drawdown Limit Triggered",
                        detail = "Portfolio unrealized return ${fmtSigned(portfolioPnlPct)}% breached your -${fmt(profile.maxPortfolioDrawdownPct)}% ceiling.",
                        severity = AlertSeverity.CRITICAL
                    )
                )
            }
        }

        for (rule in rules) {
            if (!rule.active) continue
            val quote = quotes[rule.symbol.uppercase()] ?: continue
            val curr = currencyFor(quote.exchange, rule.symbol)
            val pos = activePositions.firstOrNull { it.symbol.equals(rule.symbol, ignoreCase = true) }

            when (rule.alertTypeEnum()) {
                AlertType.STOP_LOSS -> if (quote.price <= rule.thresholdValue) {
                    alerts.add(
                        TriggeredRiskAlert(
                            id = "rule_${rule.id}",
                            symbol = rule.symbol,
                            title = "${rule.symbol} Price <= $curr${fmt(rule.thresholdValue)}",
                            detail = "Triggered at $curr${fmt(quote.price)}. ${rule.notes}".trim(),
                            severity = AlertSeverity.CRITICAL
                        )
                    )
                }
                AlertType.TAKE_PROFIT -> if (quote.price >= rule.thresholdValue) {
                    alerts.add(
                        TriggeredRiskAlert(
                            id = "rule_${rule.id}",
                            symbol = rule.symbol,
                            title = "${rule.symbol} Target >= $curr${fmt(rule.thresholdValue)}",
                            detail = "Reached $curr${fmt(quote.price)}. ${rule.notes}".trim(),
                            severity = AlertSeverity.INFO
                        )
                    )
                }
                AlertType.RSI_OVERBOUGHT -> {
                    val rsi = quote.tvRsi
                    if (rsi != null && rsi >= rule.thresholdValue) {
                        alerts.add(
                            TriggeredRiskAlert(
                                id = "rule_${rule.id}",
                                symbol = rule.symbol,
                                title = "${rule.symbol} RSI Overbought (${fmt(rsi)})",
                                detail = "RSI crossed above ${fmt(rule.thresholdValue)}. Trim or tighten trailing stops.",
                                severity = AlertSeverity.WARNING
                            )
                        )
                    }
                }
                AlertType.RSI_OVERSOLD -> {
                    val rsi = quote.tvRsi
                    if (rsi != null && rsi <= rule.thresholdValue) {
                        alerts.add(
                            TriggeredRiskAlert(
                                id = "rule_${rule.id}",
                                symbol = rule.symbol,
                                title = "${rule.symbol} RSI Oversold (${fmt(rsi)})",
                                detail = "RSI dropped below ${fmt(rule.thresholdValue)}. Monitor for reversal entry.",
                                severity = AlertSeverity.INFO
                            )
                        )
                    }
                }
                AlertType.DRAWDOWN_LIMIT -> if (pos != null && pos.avgEntryPrice > 0.0) {
                    val dd = ((pos.avgEntryPrice - quote.price) / pos.avgEntryPrice) * 100.0
                    if (dd >= rule.thresholdValue) {
                        alerts.add(
                            TriggeredRiskAlert(
                                id = "rule_${rule.id}",
                                symbol = rule.symbol,
                                title = "${rule.symbol} Drawdown >= ${fmt(rule.thresholdValue)}%",
                                detail = "Current drawdown is ${fmt(dd)}% from avg entry $curr${fmt(pos.avgEntryPrice)}.",
                                severity = AlertSeverity.CRITICAL
                            )
                        )
                    }
                }
                AlertType.SIGNAL_FLIP -> {
                    if (quote.tvRecommendAll <= -0.25) {
                        alerts.add(
                            TriggeredRiskAlert(
                                id = "rule_${rule.id}",
                                symbol = rule.symbol,
                                title = "${rule.symbol} Bearish Consensus Flip",
                                detail = "TradingView composite rating shifted bearish (${fmtSigned(quote.tvRecommendAll * 100)}).",
                                severity = AlertSeverity.WARNING
                            )
                        )
                    }
                }
            }
        }

        return alerts.sortedBy { it.severity.ordinal }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
    private fun fmtSigned(v: Double): String = String.format(Locale.US, "%+.2f", v)
}
