package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.firebase.AiForecastRepository
import com.example.data.firebase.FirebaseReadiness
import com.example.data.firebase.PortfolioRepository
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.evaluateFirebaseReadiness
import com.example.data.firebase.safeFirebaseAuth
import com.example.data.firebase.safeFirebaseFirestore
import com.example.data.remote.MarketDataRepository
import com.example.notifications.RiskAlertNotifier
import com.example.worker.AlertMonitorScheduler
import com.example.ui.auth.AuthScreen
import com.example.ui.components.ErrorCard
import com.example.ui.screens.AiLabScreen
import com.example.ui.screens.PortfolioScreen
import com.example.ui.screens.MarketsScreen
import com.example.ui.screens.RiskAlertsScreen
import com.example.ui.screens.StockDetailScreen
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearCoral
import com.example.ui.theme.BearCoralDim
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradePulseViewModel
import com.google.firebase.auth.FirebaseAuth

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_MARKETS = "markets"
const val ROUTE_AI_LAB = "ai_lab"
const val ROUTE_ALERTS = "alerts"
const val ROUTE_STOCK_DETAIL = "stock_detail"

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelTabs = listOf(
    NavTab(
        route = ROUTE_DASHBOARD,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_dashboard"
    ),
    NavTab(
        route = ROUTE_MARKETS,
        labelRes = R.string.nav_markets,
        selectedIcon = Icons.Filled.CandlestickChart,
        unselectedIcon = Icons.Outlined.CandlestickChart,
        testTag = "nav_markets"
    ),
    NavTab(
        route = ROUTE_AI_LAB,
        labelRes = R.string.nav_ai_lab,
        selectedIcon = Icons.Filled.Psychology,
        unselectedIcon = Icons.Outlined.Psychology,
        testTag = "nav_ai_lab"
    ),
    NavTab(
        route = ROUTE_ALERTS,
        labelRes = R.string.nav_alerts,
        selectedIcon = Icons.Filled.NotificationsActive,
        unselectedIcon = Icons.Outlined.NotificationsActive,
        testTag = "nav_alerts"
    )
)

@Composable
fun TradePulseApp(
    launchSymbol: String? = null,
    launchOpenAlerts: Boolean = false,
    onConsumeLaunchTarget: () -> Unit = {}
) {
    val context = LocalContext.current
    var firebaseReadiness by remember(context) {
        mutableStateOf(evaluateFirebaseReadiness(context))
    }

    if (!firebaseReadiness.isReady) {
        FirebaseSetupRequiredScreen(
            readiness = firebaseReadiness,
            onRetry = {
                configureAppCheck(context, null)
                firebaseReadiness = evaluateFirebaseReadiness(context)
            }
        )
        return
    }

    val firebaseAuth = remember(firebaseReadiness) { safeFirebaseAuth() }
    if (firebaseAuth == null) {
        FirebaseSetupRequiredScreen(
            readiness = FirebaseReadiness(
                isReady = false,
                missingItems = listOf("Firebase Authentication instance"),
                detailMessage = "Firebase Authentication could not be initialized."
            ),
            onRetry = {
                configureAppCheck(context, null)
                firebaseReadiness = evaluateFirebaseReadiness(context)
            }
        )
        return
    }

    var currentUser by remember(firebaseAuth) { mutableStateOf(firebaseAuth.currentUser) }

    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = safeFirebaseAuth()?.currentUser
            }
        )
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedShell(
                currentUserId = uid,
                launchSymbol = launchSymbol,
                launchOpenAlerts = launchOpenAlerts,
                onConsumeLaunchTarget = onConsumeLaunchTarget
            )
        }
    }
}

@Composable
private fun FirebaseSetupRequiredScreen(
    readiness: FirebaseReadiness,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, BearCoral.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(BearCoralDim, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "Firebase configuration required",
                                tint = BearCoral
                            )
                        }
                        Column {
                            Text(
                                text = "Firebase Setup Incomplete",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "TradePulse needs valid Firebase config before sign-in",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Text(
                        text = readiness.detailMessage
                            ?: "Required Firebase configuration files or values are missing from this build.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    if (readiness.missingItems.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianBg, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "MISSING OR PLACEHOLDER ITEMS",
                                style = MaterialTheme.typography.labelSmall,
                                color = BearCoral,
                                fontWeight = FontWeight.Bold
                            )
                            readiness.missingItems.forEach { item ->
                                Text(
                                    text = "• $item",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BullEmerald,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("retry_firebase_setup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Retry Initialization",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedShell(
    currentUserId: String,
    launchSymbol: String?,
    launchOpenAlerts: Boolean,
    onConsumeLaunchTarget: () -> Unit
) {
    val context = LocalContext.current
    val databaseId = remember(context) {
        try {
            context.applicationContext.getString(R.string.firestore_database_id)
        } catch (_: Throwable) {
            ""
        }
    }
    var dbInitNonce by remember { mutableStateOf(0) }
    val firestoreDb = remember(databaseId, dbInitNonce) {
        safeFirebaseFirestore(databaseId)
    }
    val firebaseAuth = remember(dbInitNonce) {
        safeFirebaseAuth()
    }

    if (firestoreDb == null || firebaseAuth == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            ErrorCard(
                message = "Unable to initialize Cloud Firestore ($databaseId). Verify app/google-services.json and firebase_applet_config.xml.",
                onRetry = { dbInitNonce++ },
                modifier = Modifier.widthIn(max = 520.dp)
            )
        }
        return
    }

    val viewModel: TradePulseViewModel = viewModel(
        key = "$currentUserId:$databaseId",
        factory = viewModelFactory {
            initializer {
                TradePulseViewModel(
                    portfolioRepo = PortfolioRepository(firestoreDb, firebaseAuth),
                    marketRepo = MarketDataRepository(),
                    aiRepo = AiForecastRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    )
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTopLevelNav = currentRoute != ROUTE_STOCK_DETAIL

    val triggeredAlerts by viewModel.triggeredRiskAlerts.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(currentUserId) {
        AlertMonitorScheduler.ensureScheduledIfEnabled(context)
    }

    LaunchedEffect(triggeredAlerts) {
        RiskAlertNotifier.notifyTriggeredAlerts(
            context = context,
            alerts = triggeredAlerts,
            forceNotify = false
        )
    }

    LaunchedEffect(launchSymbol, launchOpenAlerts) {
        if (!launchSymbol.isNullOrBlank()) {
            viewModel.selectStock(launchSymbol)
            navController.navigate(ROUTE_STOCK_DETAIL) {
                launchSingleTop = true
            }
            onConsumeLaunchTarget()
        } else if (launchOpenAlerts) {
            navController.navigate(ROUTE_ALERTS) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            onConsumeLaunchTarget()
        }
    }

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeFeedback()
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar(containerColor = TerminalSurface) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val badgeCount = if (tab.route == ROUTE_ALERTS) triggeredAlerts.size else 0
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge(containerColor = BearCoral) {
                                                    Text(badgeCount.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AiCyan,
                                    selectedTextColor = AiCyan,
                                    indicatorColor = AiCyanDim,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail(containerColor = TerminalSurface) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val badgeCount = if (tab.route == ROUTE_ALERTS) triggeredAlerts.size else 0
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge(containerColor = BearCoral) {
                                                    Text(badgeCount.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = AiCyan,
                                    selectedTextColor = AiCyan,
                                    indicatorColor = AiCyanDim,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            PortfolioScreen(
                                viewModel = viewModel,
                                onOpenStock = { sym, exch ->
                                    viewModel.selectStock(sym, exch)
                                    navController.navigate(ROUTE_STOCK_DETAIL)
                                },
                                onOpenAlertsTab = { navigateToTab(ROUTE_ALERTS) },
                                onOpenMarketsTab = { navigateToTab(ROUTE_MARKETS) }
                            )
                        }
                        composable(ROUTE_MARKETS) {
                            MarketsScreen(
                                viewModel = viewModel,
                                onOpenStock = { sym, exch ->
                                    viewModel.selectStock(sym, exch)
                                    navController.navigate(ROUTE_STOCK_DETAIL)
                                }
                            )
                        }
                        composable(ROUTE_AI_LAB) {
                            AiLabScreen(
                                viewModel = viewModel,
                                onOpenMarkets = { navigateToTab(ROUTE_MARKETS) }
                            )
                        }
                        composable(ROUTE_ALERTS) {
                            RiskAlertsScreen(viewModel = viewModel)
                        }
                        composable(ROUTE_STOCK_DETAIL) {
                            StockDetailScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenAiLab = {
                                    navController.popBackStack()
                                    navigateToTab(ROUTE_AI_LAB)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
