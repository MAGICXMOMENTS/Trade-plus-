package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AlertType
import com.example.data.model.Position
import com.example.data.model.PositionStatus
import com.example.data.model.RiskAlertRule
import com.example.data.model.RiskProfile
import com.example.data.model.RiskTolerance
import com.example.data.model.WatchlistStock
import com.example.data.model.sanitizeFirestoreId
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PortfolioRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePositions(userId: String): Flow<List<Position>> = flow {
        require(userId.isNotBlank()) { "User ID required" }
        val path = "users/$userId/positions"
        emitAll(
            db.collection("users").document(userId).collection("positions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Position::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeWatchlist(userId: String): Flow<List<WatchlistStock>> = flow {
        require(userId.isNotBlank()) { "User ID required" }
        val path = "users/$userId/watchlist"
        emitAll(
            db.collection("users").document(userId).collection("watchlist")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WatchlistStock::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.symbol }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRiskAlerts(userId: String): Flow<List<RiskAlertRule>> = flow {
        require(userId.isNotBlank()) { "User ID required" }
        val path = "users/$userId/riskAlerts"
        emitAll(
            db.collection("users").document(userId).collection("riskAlerts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RiskAlertRule::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRiskProfile(userId: String): Flow<RiskProfile> = flow {
        require(userId.isNotBlank()) { "User ID required" }
        val path = "users/$userId/profile/settings"
        emitAll(
            db.collection("users").document(userId).collection("profile").document("settings")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            RiskProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: RiskProfile(userId = userId)
                    } else {
                        RiskProfile(userId = userId)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addPosition(
        userId: String,
        symbol: String,
        exchange: String,
        companyName: String,
        shares: Double,
        avgEntryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double,
        maxRiskPercent: Double,
        notes: String
    ): Result<Unit> {
        var docPath = "users/$userId/positions"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val cleanSym = symbol.trim().uppercase()
            require(cleanSym.length in 1..20) { "Ticker symbol must be 1-20 characters" }
            require(shares > 0.0 && shares <= 100_000_000.0) { "Shares must be positive" }
            require(avgEntryPrice > 0.0 && avgEntryPrice <= 10_000_000.0) { "Entry price must be positive" }
            require(stopLossPrice in 0.0..10_000_000.0) { "Invalid stop-loss price" }
            require(takeProfitPrice in 0.0..10_000_000.0) { "Invalid take-profit price" }
            require(maxRiskPercent in 0.1..100.0) { "Max risk % must be between 0.1 and 100" }

            val docRef = db.collection("users").document(uid).collection("positions").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "symbol" to cleanSym,
                "exchange" to exchange.trim().take(30),
                "companyName" to companyName.trim().ifBlank { cleanSym }.take(80),
                "shares" to shares,
                "avgEntryPrice" to avgEntryPrice,
                "stopLossPrice" to stopLossPrice,
                "takeProfitPrice" to takeProfitPrice,
                "maxRiskPercent" to maxRiskPercent,
                "notes" to notes.trim().take(300),
                "status" to PositionStatus.ACTIVE.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePosition(
        userId: String,
        positionId: String,
        shares: Double,
        avgEntryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double,
        maxRiskPercent: Double,
        notes: String,
        status: PositionStatus = PositionStatus.ACTIVE
    ): Result<Unit> {
        var docPath = "users/$userId/positions/$positionId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(positionId.isNotBlank()) { "Position ID required" }
            require(shares > 0.0 && shares <= 100_000_000.0) { "Shares must be positive" }
            require(avgEntryPrice > 0.0 && avgEntryPrice <= 10_000_000.0) { "Entry price must be positive" }
            require(stopLossPrice in 0.0..10_000_000.0) { "Invalid stop-loss price" }
            require(takeProfitPrice in 0.0..10_000_000.0) { "Invalid take-profit price" }
            require(maxRiskPercent in 0.1..100.0) { "Max risk % must be between 0.1 and 100" }

            val docRef = db.collection("users").document(uid).collection("positions").document(positionId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "shares" to shares,
                    "avgEntryPrice" to avgEntryPrice,
                    "stopLossPrice" to stopLossPrice,
                    "takeProfitPrice" to takeProfitPrice,
                    "maxRiskPercent" to maxRiskPercent,
                    "notes" to notes.trim().take(300),
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePosition(userId: String, positionId: String): Result<Unit> {
        var docPath = "users/$userId/positions/$positionId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(uid).collection("positions").document(positionId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addToWatchlist(
        userId: String,
        symbol: String,
        exchange: String,
        companyName: String,
        sector: String
    ): Result<Unit> {
        val docId = sanitizeFirestoreId(symbol)
        var docPath = "users/$userId/watchlist/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val cleanSym = symbol.trim().uppercase()
            require(cleanSym.length in 1..20) { "Ticker symbol invalid" }

            val docRef = db.collection("users").document(uid).collection("watchlist").document(docId)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "exchange" to exchange.trim().take(30),
                        "companyName" to companyName.trim().ifBlank { cleanSym }.take(80),
                        "sector" to sector.trim().take(50),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "symbol" to cleanSym,
                        "exchange" to exchange.trim().take(30),
                        "companyName" to companyName.trim().ifBlank { cleanSym }.take(80),
                        "sector" to sector.trim().take(50),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeFromWatchlist(userId: String, symbol: String): Result<Unit> {
        val docId = sanitizeFirestoreId(symbol)
        var docPath = "users/$userId/watchlist/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(uid).collection("watchlist").document(docId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addRiskAlert(
        userId: String,
        symbol: String,
        alertType: AlertType,
        thresholdValue: Double,
        notes: String
    ): Result<Unit> {
        var docPath = "users/$userId/riskAlerts"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val cleanSym = symbol.trim().uppercase()
            require(cleanSym.length in 1..20) { "Symbol invalid" }
            require(thresholdValue in 0.0..10_000_000.0) { "Threshold out of bounds" }

            val docRef = db.collection("users").document(uid).collection("riskAlerts").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "symbol" to cleanSym,
                    "alertType" to alertType.name,
                    "thresholdValue" to thresholdValue,
                    "active" to true,
                    "notes" to notes.trim().take(200),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleRiskAlert(userId: String, alertId: String, active: Boolean): Result<Unit> {
        var docPath = "users/$userId/riskAlerts/$alertId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(uid).collection("riskAlerts").document(alertId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRiskAlert(userId: String, alertId: String): Result<Unit> {
        var docPath = "users/$userId/riskAlerts/$alertId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(uid).collection("riskAlerts").document(alertId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveRiskProfile(
        userId: String,
        riskTolerance: RiskTolerance,
        accountCapital: Double,
        defaultRiskPerTradePct: Double,
        maxPortfolioDrawdownPct: Double,
        preferredTimeframe: String
    ): Result<Unit> {
        var docPath = "users/$userId/profile/settings"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(accountCapital in 100.0..1_000_000_000.0) { "Account capital must be >= $100" }
            require(defaultRiskPerTradePct in 0.1..25.0) { "Risk per trade must be 0.1% - 25%" }
            require(maxPortfolioDrawdownPct in 1.0..80.0) { "Max drawdown must be 1% - 80%" }
            require(preferredTimeframe in setOf("15m", "1h", "1D", "1W", "1M", "3M", "1Y", "5Y")) {
                "Preferred timeframe must be 15m, 1h, 1D, 1W, 1M, 3M, 1Y, or 5Y"
            }

            val docRef = db.collection("users").document(uid).collection("profile").document("settings")
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "riskTolerance" to riskTolerance.name,
                        "accountCapital" to accountCapital,
                        "defaultRiskPerTradePct" to defaultRiskPerTradePct,
                        "maxPortfolioDrawdownPct" to maxPortfolioDrawdownPct,
                        "preferredTimeframe" to preferredTimeframe,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "riskTolerance" to riskTolerance.name,
                        "accountCapital" to accountCapital,
                        "defaultRiskPerTradePct" to defaultRiskPerTradePct,
                        "maxPortfolioDrawdownPct" to maxPortfolioDrawdownPct,
                        "preferredTimeframe" to preferredTimeframe,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
