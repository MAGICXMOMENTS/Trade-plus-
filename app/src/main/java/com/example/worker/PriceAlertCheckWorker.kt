package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.firebase.ensureFirebaseAppInitialized
import com.example.data.firebase.safeFirebaseAuth
import com.example.data.firebase.safeFirebaseFirestore
import com.example.data.model.Position
import com.example.data.model.PositionStatus
import com.example.data.model.RiskAlertRule
import com.example.data.model.RiskProfile
import com.example.data.model.WatchlistStock
import com.example.data.remote.MarketDataRepository
import com.example.notifications.RiskAlertNotifier
import com.example.quant.RiskAlertEvaluator
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.IOException

class PriceAlertCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!ensureFirebaseAppInitialized(applicationContext)) {
            RiskAlertNotifier.recordCheckStatus(applicationContext, "Paused: Firebase not configured")
            return Result.success()
        }

        val forceNotify = inputData.getBoolean(KEY_FORCE_NOTIFY, false)
        val user = safeFirebaseAuth()?.currentUser
        if (user == null) {
            RiskAlertNotifier.recordCheckStatus(applicationContext, "Paused: sign in to monitor alerts")
            return Result.success()
        }

        return try {
            val uid = user.uid
            val dbId = applicationContext.getString(R.string.firestore_database_id)
            val db = safeFirebaseFirestore(dbId)
                ?: run {
                    RiskAlertNotifier.recordCheckStatus(applicationContext, "Paused: Firestore unavailable")
                    return Result.failure()
                }

            val positionsSnap = db.collection("users").document(uid).collection("positions").get().await()
            val rulesSnap = db.collection("users").document(uid).collection("riskAlerts").get().await()
            val watchlistSnap = db.collection("users").document(uid).collection("watchlist").get().await()
            val profileSnap = db.collection("users").document(uid).collection("profile").document("settings").get().await()

            val positions = positionsSnap.toObjects(
                Position::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val rules = rulesSnap.toObjects(
                RiskAlertRule::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val watchlist = watchlistSnap.toObjects(
                WatchlistStock::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val profile = if (profileSnap.exists()) {
                profileSnap.toObject(
                    RiskProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: RiskProfile(userId = uid)
            } else {
                RiskProfile(userId = uid)
            }

            val activePositions = positions.filter { it.statusEnum() == PositionStatus.ACTIVE }
            val activeRules = rules.filter { it.active }
            val monitoredSymbols = (
                activePositions.map { it.symbol } +
                    activeRules.map { it.symbol }
                )
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .distinct()

            if (monitoredSymbols.isEmpty()) {
                RiskAlertNotifier.recordCheckStatus(applicationContext, "No active alert rules or positions")
                return Result.success()
            }

            val preferredExchanges = buildMap {
                watchlist.forEach { if (it.exchange.isNotBlank()) put(it.symbol.uppercase(), it.exchange) }
                activePositions.forEach { if (it.exchange.isNotBlank()) put(it.symbol.uppercase(), it.exchange) }
            }

            val quotes = MarketDataRepository().getQuotesForSymbols(monitoredSymbols, preferredExchanges)
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
        } catch (e: FirebaseFirestoreException) {
            val isTransient = e.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
            Log.w(TAG, "Firestore error during background price check: ${e.code}", e)
            RiskAlertNotifier.recordCheckStatus(
                applicationContext,
                if (isTransient) "Firestore temporarily unavailable; retrying" else "Check failed: ${e.localizedMessage}"
            )
            if (isTransient && runAttemptCount < 3) Result.retry() else Result.failure()
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
