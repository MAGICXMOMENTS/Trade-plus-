package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PositionStatus {
    ACTIVE,
    CLOSED;

    companion object {
        fun safeValueOf(raw: String?): PositionStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class AlertType(val label: String, val unitHint: String) {
    STOP_LOSS("Stop-Loss Price", "$"),
    TAKE_PROFIT("Take-Profit Price", "$"),
    RSI_OVERBOUGHT("RSI Overbought", "RSI"),
    RSI_OVERSOLD("RSI Oversold", "RSI"),
    DRAWDOWN_LIMIT("Position Drawdown %", "%"),
    SIGNAL_FLIP("Technical/RL Signal Flip", "Score");

    companion object {
        fun safeValueOf(raw: String?): AlertType =
            entries.firstOrNull { it.name == raw } ?: STOP_LOSS
    }
}

enum class RiskTolerance(val label: String, val defaultRiskPct: Double, val maxDrawdownPct: Double) {
    CONSERVATIVE("Conservative", 0.75, 6.0),
    MODERATE("Moderate", 1.5, 10.0),
    AGGRESSIVE("Aggressive", 3.0, 18.0);

    companion object {
        fun safeValueOf(raw: String?): RiskTolerance =
            entries.firstOrNull { it.name == raw } ?: MODERATE
    }
}

@Keep
@IgnoreExtraProperties
data class Position(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val exchange: String = "",
    val companyName: String = "",
    val shares: Double = 0.0,
    val avgEntryPrice: Double = 0.0,
    val stopLossPrice: Double = 0.0,
    val takeProfitPrice: Double = 0.0,
    val maxRiskPercent: Double = 2.0,
    val notes: String = "",
    val status: String = PositionStatus.ACTIVE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): PositionStatus = PositionStatus.safeValueOf(status)
}

@Keep
@IgnoreExtraProperties
data class WatchlistStock(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val exchange: String = "",
    val companyName: String = "",
    val sector: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RiskAlertRule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val alertType: String = AlertType.STOP_LOSS.name,
    val thresholdValue: Double = 0.0,
    val active: Boolean = true,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun alertTypeEnum(): AlertType = AlertType.safeValueOf(alertType)
}

@Keep
@IgnoreExtraProperties
data class RiskProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val riskTolerance: String = RiskTolerance.MODERATE.name,
    val accountCapital: Double = 25000.0,
    val defaultRiskPerTradePct: Double = 1.5,
    val maxPortfolioDrawdownPct: Double = 10.0,
    val preferredTimeframe: String = "3M",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toleranceEnum(): RiskTolerance = RiskTolerance.safeValueOf(riskTolerance)
}

fun sanitizeFirestoreId(rawSymbol: String): String {
    val sanitized = rawSymbol.trim().uppercase().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    return sanitized.take(128).ifBlank { "SYMBOL" }
}
