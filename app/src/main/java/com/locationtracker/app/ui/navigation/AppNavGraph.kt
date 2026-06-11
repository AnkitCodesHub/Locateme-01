package com.locationtracker.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.locationtracker.app.ui.screen.AuthScreen
import com.locationtracker.app.ui.screen.FriendProfileScreen
import com.locationtracker.app.ui.screen.FriendsListScreen
import com.locationtracker.app.ui.screen.MapScreen
import com.locationtracker.app.ui.screen.ProfileScreen
import com.locationtracker.app.ui.screen.TimelineScreen
import com.locationtracker.app.ui.viewmodel.AuthViewModel
import com.locationtracker.app.ui.viewmodel.FriendViewModel
import com.locationtracker.app.ui.viewmodel.MapViewModel

// ── Route constants ──────────────────────────────────────────────────────────

object Routes {
    const val AUTH = "auth"
    const val MAIN = "main"
    const val MAP = "map"
    const val FRIENDS = "friends"
    const val TIMELINE = "timeline"
    const val PROFILE = "profile"
    const val FRIEND_PROFILE = "friend_profile/{friendId}"

    fun friendProfile(friendId: String) = "friend_profile/$friendId"
}

// ── Bottom nav tab model ─────────────────────────────────────────────────────

data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavTabs = listOf(
    BottomNavTab(
        route = Routes.MAP,
        label = "Map",
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map
    ),
    BottomNavTab(
        route = Routes.FRIENDS,
        label = "Friends",
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People
    ),
    BottomNavTab(
        route = Routes.TIMELINE,
        label = "Timeline",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History
    ),
    BottomNavTab(
        route = Routes.PROFILE,
        label = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
)

// ── Root nav graph (Auth → Main Shell) ──────────────────────────────────────

@Composable
fun AppNavGraph(
    startDestination: String = Routes.AUTH,
    authViewModel: AuthViewModel = viewModel()
) {
    val rootNavController = rememberNavController()

    NavHost(
        navController = rootNavController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        }
    ) {
        // Auth screen
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthSuccess = {
                    rootNavController.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }

        // Main shell with bottom nav
        composable(Routes.MAIN) {
            MainShell(
                onSignOut = {
                    rootNavController.navigate(Routes.AUTH) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }
    }
}

// ── Main shell with nested navigation and bottom bar ────────────────────────

@Composable
fun MainShell(
    onSignOut: () -> Unit,
    authViewModel: AuthViewModel
) {
    val mainNavController = rememberNavController()
    val mapViewModel: MapViewModel = viewModel()
    val friendViewModel: FriendViewModel = viewModel()

    Scaffold(
        containerColor = Color(0xFF0D1B2A),
        bottomBar = {
            val navBackStackEntry by mainNavController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            // Hide bottom bar on friend profile screen
            val showBottomBar = currentDestination?.route != Routes.FRIEND_PROFILE

            if (showBottomBar) {
                LocateMeBottomBar(
                    navController = mainNavController,
                    tabs = bottomNavTabs,
                    currentDestination = currentDestination
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = mainNavController,
            startDestination = Routes.MAP,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.MAP) {
                MapScreen(mapViewModel = mapViewModel)
            }
            composable(Routes.FRIENDS) {
                FriendsListScreen(
                    onNavigateToProfile = { friendId ->
                        mainNavController.navigate(Routes.friendProfile(friendId))
                    },
                    friendViewModel = friendViewModel
                )
            }
            composable(Routes.TIMELINE) {
                TimelineScreen()
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onSignOut = onSignOut,
                    authViewModel = authViewModel
                )
            }
            composable(
                route = Routes.FRIEND_PROFILE,
                arguments = listOf(navArgument("friendId") { type = NavType.StringType })
            ) { backStackEntry ->
                val friendId = backStackEntry.arguments?.getString("friendId") ?: ""
                FriendProfileScreen(
                    friendId = friendId,
                    onNavigateBack = { mainNavController.popBackStack() },
                    friendViewModel = friendViewModel
                )
            }
        }
    }
}

// ── Bottom Navigation Bar ────────────────────────────────────────────────────

@Composable
fun LocateMeBottomBar(
    navController: NavHostController,
    tabs: List<BottomNavTab>,
    currentDestination: androidx.navigation.NavDestination?
) {
    NavigationBar(
        containerColor = Color(0xFF0A1520),
        tonalElevation = 0.dp
    ) {
        tabs.forEach { tab ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF4FC3F7),
                    unselectedIconColor = Color(0xFF334455),
                    selectedTextColor = Color(0xFF4FC3F7),
                    unselectedTextColor = Color(0xFF334455),
                    indicatorColor = Color(0xFF0D2840)
                )
            )
        }
    }
}
