package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AlertSeverity
import com.example.data.model.TriggeredRiskAlert

data class MonitorStatusSnapshot(
    val enabled: Boolean,
    val lastCheckTimestampMs: Long,
    val lastCheckSummary: String
)

object RiskAlertNotifier {

    const val CHANNEL_ID = "tradepulse_price_alerts"
    const val EXTRA_ALERT_SYMBOL = "extra_alert_symbol"
    const val EXTRA_OPEN_ALERTS = "extra_open_alerts"

    private const val PREFS_NAME = "tradepulse_alert_prefs"
    private const val KEY_BG_ENABLED = "bg_monitoring_enabled"
    private const val KEY_LAST_CHECK_MS = "last_bg_check_ms"
    private const val KEY_LAST_SUMMARY = "last_bg_summary"
    private const val KEY_PREFIX_NOTIFIED = "notified_at_"
    private const val COOLDOWN_MS = 4L * 60L * 60L * 1000L // 4 hours per alert rule

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stock Price & Risk Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when watched stocks or positions cross user-defined stop-loss, take-profit, RSI, or drawdown thresholds."
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isBackgroundMonitoringEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BG_ENABLED, true)
    }

    fun setBackgroundMonitoringEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BG_ENABLED, enabled)
            .apply()
    }

    fun getMonitorStatus(context: Context): MonitorStatusSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return MonitorStatusSnapshot(
            enabled = prefs.getBoolean(KEY_BG_ENABLED, true),
            lastCheckTimestampMs = prefs.getLong(KEY_LAST_CHECK_MS, 0L),
            lastCheckSummary = prefs.getString(KEY_LAST_SUMMARY, "Waiting for first price scan") ?: "Waiting for first price scan"
        )
    }

    fun recordCheckStatus(context: Context, summary: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis())
            .putString(KEY_LAST_SUMMARY, summary)
            .apply()
    }

    /**
     * Posts local notifications for newly triggered threshold alerts while suppressing repeat
     * notifications for the same alert ID within [COOLDOWN_MS] unless [forceNotify] is true.
     * Returns the count of notifications actually dispatched.
     */
    fun notifyTriggeredAlerts(
        context: Context,
        alerts: List<TriggeredRiskAlert>,
        forceNotify: Boolean = false
    ): Int {
        ensureChannel(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val activeIds = alerts.map { it.id }.toSet()

        // Clear cooldowns for alerts whose condition has resolved so re-triggers notify immediately
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(KEY_PREFIX_NOTIFIED)) {
                val id = key.removePrefix(KEY_PREFIX_NOTIFIED)
                if (id !in activeIds) {
                    editor.remove(key)
                }
            }
        }

        if (alerts.isEmpty() || !hasNotificationPermission(context)) {
            editor.apply()
            return 0
        }

        val nm = NotificationManagerCompat.from(context)
        var sentCount = 0

        for (alert in alerts) {
            val prefKey = "$KEY_PREFIX_NOTIFIED${alert.id}"
            val lastSent = prefs.getLong(prefKey, 0L)
            if (!forceNotify && now - lastSent < COOLDOWN_MS) {
                continue
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_ALERTS, true)
                if (alert.symbol.isNotBlank() && !alert.symbol.equals("PORTFOLIO", ignoreCase = true)) {
                    putExtra(EXTRA_ALERT_SYMBOL, alert.symbol)
                }
            }

            val notificationId = alert.id.hashCode()
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val priority = when (alert.severity) {
                AlertSeverity.CRITICAL -> NotificationCompat.PRIORITY_HIGH
                AlertSeverity.WARNING -> NotificationCompat.PRIORITY_DEFAULT
                AlertSeverity.INFO -> NotificationCompat.PRIORITY_DEFAULT
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setContentTitle(alert.title)
                .setContentText(alert.detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.detail))
                .setPriority(priority)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                nm.notify(notificationId, notification)
                editor.putLong(prefKey, now)
                sentCount++
            } catch (_: SecurityException) {
                // Permission revoked mid-flight; ignore safely
            }
        }

        editor.apply()
        return sentCount
    }
}
