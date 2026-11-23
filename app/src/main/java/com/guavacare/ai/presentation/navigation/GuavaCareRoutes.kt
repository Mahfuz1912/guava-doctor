package com.guavacare.ai.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.guavacare.ai.core.common.AppStrings

object GuavaCareRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_SCANNER = "scanner"
    const val ROUTE_DIAGNOSIS = "diagnosis"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_INSIGHTS = "insights"
    const val ROUTE_ABOUT = "about"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_RESEARCH = "research"
    const val ROUTE_PRIVACY_POLICY = "privacy_policy"
    const val ROUTE_LOGIN = "login"
    const val ROUTE_REGISTER = "register"
    const val ROUTE_PROFILE = "profile"
}

data class TopLevelDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val labelProvider: (AppStrings) -> String,
    val testTag: String
)

val topLevelDestinations = listOf(
    TopLevelDestination(
        route = GuavaCareRoutes.ROUTE_HOME,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        labelProvider = { it.navHome },
        testTag = "nav_home"
    ),
    TopLevelDestination(
        route = GuavaCareRoutes.ROUTE_SCANNER,
        selectedIcon = Icons.Filled.CameraAlt,
        unselectedIcon = Icons.Outlined.CameraAlt,
        labelProvider = { it.navScan },
        testTag = "nav_scan"
    ),
    TopLevelDestination(
        route = GuavaCareRoutes.ROUTE_HISTORY,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        labelProvider = { it.navHistory },
        testTag = "nav_history"
    ),
    TopLevelDestination(
        route = GuavaCareRoutes.ROUTE_PROFILE,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        labelProvider = { it.navProfile },
        testTag = "nav_profile"
    ),
    TopLevelDestination(
        route = GuavaCareRoutes.ROUTE_ABOUT,
        selectedIcon = Icons.Filled.Info,
        unselectedIcon = Icons.Outlined.Info,
        labelProvider = { it.navAbout },
        testTag = "nav_about"
    )
)

/**
 * History and Profile tabs are visible only when the user is signed in.
 * Insights is accessed via the dedicated Orchard Insights card on the Home Page.
 */
fun visibleTopLevelDestinations(isAuthenticated: Boolean): List<TopLevelDestination> =
    if (isAuthenticated) {
        topLevelDestinations
    } else {
        topLevelDestinations.filterNot {
            it.route == GuavaCareRoutes.ROUTE_HISTORY || it.route == GuavaCareRoutes.ROUTE_PROFILE
        }
    }
