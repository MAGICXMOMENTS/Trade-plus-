package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.model.Position
import com.example.data.model.PositionStatus
import com.example.data.model.RiskAlertRule
import com.example.data.model.RiskProfile
import com.example.data.remote.MarketDataRepository
import com.example.notifications.RiskAlertNotifier
import com.example.quant.RiskAlertEvaluator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.IOException

class PriceAlertCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val forceNotify = inputData.getBoolean(KEY_FORCE_NOTIFY, false)
        val user = Firebase.auth.currentUser
        if (user == null) {
            RiskAlertNotifier.recordCheckStatus(applicationContext, "Paused: sign in to monitor alerts")
            return Result.success()
        }

        return try {
            val uid = user.uid
            val dbId = applicationContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)

            val positionsSnap = db.collection("users").document(uid).collection("positions").get().await()
            val rulesSnap = db.collection("users").document(uid).collection("riskAlerts").get().await()
            val profileSnap = db.collection("users").document(uid).collection("profile").document("settings").get().await()

            val positions = positionsSnap.toObjects(Position::class.java)
            val rules = rulesSnap.toObjects(RiskAlertRule::class.java)
            val profile = if (profileSnap.exists()) {
                profileSnap.toObject(RiskProfile::class.java) ?: RiskProfile(userId = uid)
            } else {
                RiskProfile(userId = uid)
            }

            val monitoredSymbols = (
                positions.filter { it.statusEnum() == PositionStatus.ACTIVE }.map { it.symbol } +
                    rules.filter { it.active }.map { it.symbol }
                )
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .distinct()

            if (monitoredSymbols.isEmpty()) {
                RiskAlertNotifier.recordCheckStatus(applicationContext, "No active alert rules or positions")
                return Result.success()
            }

            val quotes = MarketDataRepository().getQuotesForSymbols(monitoredSymbols)
            val triggered = RiskAlertEvaluator.evaluate(
                positions = positions,
                rules = rules,
                quotes = quotes,
                profile = profile
            )

            val sentCount = RiskAlertNotifier.notifyTriggeredAlerts(
                context = applicationContext,
                alerts = triggered,
                forceNotify = forceNotify
            )

            val summary = buildString {
                append("Scanned ${quotes.size}/${monitoredSymbols.size} tickers • ")
                append("${triggered.size} triggered")
                if (sentCount > 0) append(" ($sentCount notified)")
            }
            RiskAlertNotifier.recordCheckStatus(applicationContext, summary)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Network error during background price check", e)
            RiskAlertNotifier.recordCheckStatus(applicationContext, "Network unavailable; will retry")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Background price alert check failed", e)
            RiskAlertNotifier.recordCheckStatus(
                applicationContext,
                "Check failed: ${e.localizedMessage ?: "unknown error"}"
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_FORCE_NOTIFY = "force_notify"
        private const val TAG = "PriceAlertWorker"
    }
}
