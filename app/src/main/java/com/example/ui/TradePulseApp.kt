package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
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
import com.example.data.firebase.PortfolioRepository
import com.example.data.remote.MarketDataRepository
import com.example.notifications.RiskAlertNotifier
import com.example.worker.AlertMonitorScheduler
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.AiLabScreen
import com.example.ui.screens.PortfolioScreen
import com.example.ui.screens.MarketsScreen
import com.example.ui.screens.RiskAlertsScreen
import com.example.ui.screens.StockDetailScreen
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearCoral
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.TradePulseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

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
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
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
private fun AuthenticatedShell(
    currentUserId: String,
    launchSymbol: String?,
    launchOpenAlerts: Boolean,
    onConsumeLaunchTarget: () -> Unit
) {
    val viewModel: TradePulseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TradePulseViewModel(
                    portfolioRepo = PortfolioRepository(db),
                    marketRepo = MarketDataRepository(),
                    aiRepo = AiForecastRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val context = LocalContext.current
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
