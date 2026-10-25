package com.matanjerbi.cinematheque.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.matanjerbi.cinematheque.ui.screens.AiringScreen
import com.matanjerbi.cinematheque.ui.screens.CollectionDetailScreen
import com.matanjerbi.cinematheque.ui.screens.CollectionsScreen
import com.matanjerbi.cinematheque.ui.screens.DiaryScreen
import com.matanjerbi.cinematheque.ui.screens.EntryEditorScreen
import com.matanjerbi.cinematheque.ui.screens.PastWatchesScreen
import com.matanjerbi.cinematheque.ui.screens.SearchScreen
import com.matanjerbi.cinematheque.ui.screens.StatsScreen
import com.matanjerbi.cinematheque.ui.screens.TagsScreen
import com.matanjerbi.cinematheque.ui.screens.TitleDetailScreen
import com.matanjerbi.cinematheque.ui.screens.WatchlistScreen

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(DiaryRoute, "יומן", Icons.Outlined.Movie),
    Tab(WatchlistRoute, "לצפייה", Icons.Outlined.Bookmarks),
    Tab(AiringRoute, "משודרות", Icons.Outlined.LiveTv),
    Tab(CollectionsRoute, "אוספים", Icons.Outlined.CollectionsBookmark),
    Tab(StatsRoute, "סטטיסטיקות", Icons.Outlined.BarChart),
)

@Composable
fun CinemathequeNavHost(openTitleId: Long? = null, onOpenedTitle: () -> Unit = {}) {
    val nav = rememberNavController()

    // Tapping a new-episode notification opens that series' page.
    LaunchedEffect(openTitleId) {
        val id = openTitleId ?: return@LaunchedEffect
        nav.navigate(TitleDetailRoute(id)) { launchSingleTop = true }
        onOpenedTitle()
    }
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTab = tabs.any { tab -> destination?.hasRoute(tab.route::class) == true }

    // Outer scaffold only reserves room for the bottom bar; each screen's own
    // Scaffold handles the status bar, so insets aren't applied twice.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hasRoute(tab.route::class) == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = DiaryRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<DiaryRoute> {
                DiaryScreen(
                    onAdd = { nav.navigate(SearchRoute(SearchPurpose.WATCHED)) },
                    onAddPast = { nav.navigate(PastWatchesRoute) },
                    onOpenEntry = { titleId, entryId -> nav.navigate(EntryEditorRoute(titleId, entryId)) },
                    onOpenTitle = { nav.navigate(TitleDetailRoute(it)) },
                    onManageTags = { nav.navigate(TagsRoute) },
                )
            }
            composable<WatchlistRoute> {
                WatchlistScreen(
                    onAdd = { nav.navigate(SearchRoute(SearchPurpose.WATCHLIST)) },
                    onOpenTitle = { nav.navigate(TitleDetailRoute(it)) },
                    onMarkWatched = { nav.navigate(EntryEditorRoute(it)) },
                )
            }
            composable<AiringRoute> {
                AiringScreen(
                    onAdd = { nav.navigate(SearchRoute(SearchPurpose.FOLLOW)) },
                    onOpenTitle = { nav.navigate(TitleDetailRoute(it)) },
                )
            }
            composable<CollectionsRoute> {
                CollectionsScreen(onOpenCollection = { nav.navigate(CollectionDetailRoute(it)) })
            }
            composable<StatsRoute> {
                StatsScreen(onOpenTitle = { nav.navigate(TitleDetailRoute(it)) })
            }
            composable<SearchRoute> { entry ->
                val route = entry.toRoute<SearchRoute>()
                SearchScreen(
                    purpose = route.purpose,
                    collectionId = route.collectionId,
                    onWatched = { titleId ->
                        if (route.purpose == SearchPurpose.FOLLOW) {
                            nav.navigate(TitleDetailRoute(titleId, openFollow = true)) { popUpTo<SearchRoute> { inclusive = true } }
                        } else {
                            nav.navigate(EntryEditorRoute(titleId)) { popUpTo<SearchRoute> { inclusive = true } }
                        }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<PastWatchesRoute> {
                PastWatchesScreen(onDone = { nav.popBackStack() })
            }
            composable<EntryEditorRoute> { entry ->
                val route = entry.toRoute<EntryEditorRoute>()
                EntryEditorScreen(
                    titleId = route.titleId,
                    entryId = route.entryId,
                    onDone = { nav.popBackStack() },
                )
            }
            composable<TitleDetailRoute> { entry ->
                val route = entry.toRoute<TitleDetailRoute>()
                TitleDetailScreen(
                    titleId = route.titleId,
                    openFollow = route.openFollow,
                    onWatchAgain = { nav.navigate(EntryEditorRoute(route.titleId)) },
                    onOpenEntry = { entryId -> nav.navigate(EntryEditorRoute(route.titleId, entryId)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<CollectionDetailRoute> { entry ->
                val route = entry.toRoute<CollectionDetailRoute>()
                CollectionDetailScreen(
                    collectionId = route.collectionId,
                    onAddTitles = { nav.navigate(SearchRoute(SearchPurpose.ADD_TO_COLLECTION, route.collectionId)) },
                    onOpenTitle = { nav.navigate(TitleDetailRoute(it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<TagsRoute> {
                TagsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
