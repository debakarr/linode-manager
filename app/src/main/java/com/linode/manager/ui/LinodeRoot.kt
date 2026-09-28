package com.linode.manager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.linode.manager.AppContainer
import com.linode.manager.ui.components.LoadingState
import com.linode.manager.ui.screens.account.AccountScreen
import com.linode.manager.ui.screens.account.EventsScreen
import com.linode.manager.ui.screens.auth.LoginScreen
import com.linode.manager.ui.screens.dashboard.DashboardScreen
import com.linode.manager.ui.screens.linodes.CreateLinodeScreen
import com.linode.manager.ui.screens.linodes.LinodeDetailScreen
import com.linode.manager.ui.screens.linodes.LinodeListScreen
import com.linode.manager.ui.screens.network.FirewallDetailScreen
import com.linode.manager.ui.screens.network.NetworkScreen
import com.linode.manager.ui.screens.ssh.SshScreen
import com.linode.manager.ui.screens.volumes.VolumesScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val LINODES = "linodes"
    const val LINODE_DETAIL = "linode/{id}"
    const val LINODE_CREATE = "linode-create"
    const val VOLUMES = "volumes"
    const val NETWORK = "network"
    const val FIREWALL_DETAIL = "firewall/{id}"
    const val SSH = "ssh/{id}"
    const val EVENTS = "events"
    const val ACCOUNT = "account"

    fun detail(id: Int) = "linode/$id"

    fun firewall(id: Int) = "firewall/$id"

    fun ssh(id: Int) = "ssh/$id"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val TABS =
    listOf(
        Tab(Routes.DASHBOARD, "Home", Icons.Outlined.SpaceDashboard, Icons.Filled.SpaceDashboard),
        Tab(Routes.LINODES, "Linodes", Icons.Outlined.Dns, Icons.Filled.Dns),
        Tab(Routes.VOLUMES, "Volumes", Icons.Outlined.Storage, Icons.Filled.Storage),
        Tab(Routes.NETWORK, "Network", Icons.Outlined.Hub, Icons.Filled.Hub),
        Tab(Routes.ACCOUNT, "Account", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
    )

@Composable
fun LinodeRoot(container: AppContainer) {
    val session: SessionViewModel = viewModel { SessionViewModel(container) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var crash by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            com.linode.manager.data.CrashLog
                .read(context),
        )
    }
    crash?.let { report ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                com.linode.manager.data.CrashLog
                    .clear(context)
                crash = null
            },
            title = { Text("The app closed unexpectedly") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("Copy this report and send it along so it can be fixed.")
                    androidx.compose.foundation.layout
                        .Spacer(Modifier.padding(4.dp))
                    androidx.compose.foundation.layout.Box(
                        Modifier.heightIn(max = 280.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) {
                        Text(
                            report,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.linode.manager.ui.components
                        .copyToClipboard(context, "Crash report", report)
                }) { Text("Copy") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.linode.manager.data.CrashLog
                        .clear(context)
                    crash = null
                }) { Text("Dismiss") }
            },
        )
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when {
            session.checking -> LoadingState("Signing in…")
            !session.loggedIn -> LoginScreen(container = container, onLoggedIn = { session.onLoggedIn() })
            else ->
                AppShell(
                    container = container,
                    onLogout = {
                        container.sshSessions.closeAll()
                        session.logout()
                    },
                    onUnauthorized = {
                        container.sshSessions.closeAll()
                        session.handleUnauthorized()
                    },
                )
        }
    }
}

@Composable
private fun AppShell(
    container: AppContainer,
    onLogout: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val isTopLevel = TABS.any { t -> destination?.hierarchy?.any { it.route == t.route } == true }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val liveSessions = container.sshSessions.liveCount

    fun select(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    @Composable
    fun TabIcon(
        tab: Tab,
        selected: Boolean,
    ) {
        val icon = if (selected) tab.selectedIcon else tab.icon
        if (tab.route == Routes.LINODES && liveSessions > 0) {
            BadgedBox(badge = { Badge { Text("$liveSessions") } }) { Icon(icon, contentDescription = tab.label) }
        } else {
            Icon(icon, contentDescription = tab.label)
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            AnimatedVisibility(isTopLevel) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    TABS.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { select(tab.route) },
                            icon = { TabIcon(tab, selected) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
            AppNavHost(nav, container, onLogout, onUnauthorized, Modifier.weight(1f))
        }
    } else {
        // contentWindowInsets = 0: the NavigationBar pads the gesture/nav
        // area itself, and consumeWindowInsets tells nested screen
        // scaffolds that the bottom inset is already handled — so screens
        // never double-pad (the old inconsistent gaps) or underlap the bar.
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                AnimatedVisibility(isTopLevel, enter = fadeIn(), exit = fadeOut()) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        TABS.forEach { tab ->
                            val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { select(tab.route) },
                                icon = { TabIcon(tab, selected) },
                                label = { Text(tab.label, maxLines = 1) },
                            )
                        }
                    }
                }
            },
        ) { pad ->
            AppNavHost(nav, container, onLogout, onUnauthorized, Modifier.padding(pad).consumeWindowInsets(pad))
        }
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    container: AppContainer,
    onLogout: () -> Unit,
    onUnauthorized: () -> Unit,
    modifier: Modifier,
) {
    NavHost(
        navController = nav,
        startDestination = Routes.DASHBOARD,
        modifier = modifier,
        enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
    ) {
        composable(Routes.DASHBOARD, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
            DashboardScreen(
                container = container,
                onUnauthorized = onUnauthorized,
                onOpenLinodes = { nav.navigate(Routes.LINODES) { launchSingleTop = true } },
                onOpenLinode = { nav.navigate(Routes.detail(it)) },
                onOpenSsh = { nav.navigate(Routes.ssh(it)) },
                onOpenEvents = { nav.navigate(Routes.EVENTS) },
            )
        }
        composable(Routes.LINODES, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
            LinodeListScreen(
                container = container,
                onUnauthorized = onUnauthorized,
                onOpen = { nav.navigate(Routes.detail(it)) },
                onOpenSsh = { nav.navigate(Routes.ssh(it)) },
                onCreate = { nav.navigate(Routes.LINODE_CREATE) },
            )
        }
        composable(Routes.LINODE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            val id = entry.arguments?.getInt("id") ?: 0
            LinodeDetailScreen(
                container = container,
                linodeId = id,
                onBack = { nav.popBackStack() },
                onDeleted = { nav.popBackStack(Routes.LINODES, false) },
                onOpenSsh = { nav.navigate(Routes.ssh(id)) },
                onUnauthorized = onUnauthorized,
            )
        }
        composable(Routes.LINODE_CREATE) {
            CreateLinodeScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate(Routes.detail(id)) { popUpTo(Routes.LINODES) } },
                onUnauthorized = onUnauthorized,
            )
        }
        composable(Routes.VOLUMES, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
            VolumesScreen(container = container, onUnauthorized = onUnauthorized)
        }
        composable(Routes.NETWORK, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
            NetworkScreen(
                container = container,
                onUnauthorized = onUnauthorized,
                onOpenFirewall = { nav.navigate(Routes.firewall(it)) },
            )
        }
        composable(Routes.FIREWALL_DETAIL, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            val id = entry.arguments?.getInt("id") ?: 0
            FirewallDetailScreen(
                container = container,
                firewallId = id,
                onBack = { nav.popBackStack() },
                onDeleted = { nav.popBackStack(Routes.NETWORK, false) },
                onUnauthorized = onUnauthorized,
            )
        }
        composable(Routes.SSH, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            val id = entry.arguments?.getInt("id") ?: 0
            SshScreen(
                container = container,
                linodeId = id,
                onBack = { nav.popBackStack() },
                onUnauthorized = onUnauthorized,
            )
        }
        composable(Routes.EVENTS) {
            EventsScreen(container = container, onBack = { nav.popBackStack() }, onUnauthorized = onUnauthorized)
        }
        composable(Routes.ACCOUNT, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
            AccountScreen(container = container, onUnauthorized = onUnauthorized, onLogout = onLogout)
        }
    }
}
