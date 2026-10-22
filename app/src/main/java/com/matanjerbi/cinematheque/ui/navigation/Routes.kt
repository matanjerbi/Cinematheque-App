package com.matanjerbi.cinematheque.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe routes (Navigation Compose 2.8+).

// Bottom-bar tabs
@Serializable data object DiaryRoute
@Serializable data object WatchlistRoute
@Serializable data object CollectionsRoute
@Serializable data object StatsRoute

/** What the search screen does with a picked result. */
@Serializable
enum class SearchPurpose { WATCHED, WATCHLIST, ADD_TO_COLLECTION }

@Serializable
data class SearchRoute(
    val purpose: SearchPurpose = SearchPurpose.WATCHED,
    /** Set when purpose is ADD_TO_COLLECTION. */
    val collectionId: Long = 0L,
)

/** Rapid entry of past watches: search → rate → optional tags/year → save & next. */
@Serializable data object PastWatchesRoute

/** New watch when entryId == 0, otherwise edit an existing one. */
@Serializable data class EntryEditorRoute(val titleId: Long, val entryId: Long = 0L)

@Serializable data class TitleDetailRoute(val titleId: Long)

@Serializable data class CollectionDetailRoute(val collectionId: Long)

@Serializable data object TagsRoute
