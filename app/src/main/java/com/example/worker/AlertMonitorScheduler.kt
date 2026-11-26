package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.notifications.RiskAlertNotifier
import java.util.concurrent.TimeUnit

object AlertMonitorScheduler {

    private const val PERIODIC_WORK_NAME = "tradepulse_periodic_price_alert_check"
    const val IMMEDIATE_WORK_NAME = "tradepulse_immediate_price_alert_check"

    fun ensureScheduledIfEnabled(context: Context) {
        RiskAlertNotifier.ensureChannel(context)
        if (RiskAlertNotifier.isBackgroundMonitoringEnabled(context)) {
            schedulePeriodicCheck(context)
        }
    }

    fun setBackgroundMonitoringEnabled(context: Context, enabled: Boolean) {
        RiskAlertNotifier.setBackgroundMonitoringEnabled(context, enabled)
        if (enabled) {
            RiskAlertNotifier.ensureChannel(context)
            schedulePeriodicCheck(context)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }

    private fun schedulePeriodicCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PriceAlertCheckWorker>(
            15,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun runImmediateCheck(context: Context, forceNotify: Boolean = true) {
        RiskAlertNotifier.ensureChannel(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<PriceAlertCheckWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(PriceAlertCheckWorker.KEY_FORCE_NOTIFY to forceNotify))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
