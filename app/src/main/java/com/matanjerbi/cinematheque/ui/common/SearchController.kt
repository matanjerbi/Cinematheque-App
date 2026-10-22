package com.matanjerbi.cinematheque.ui.common

import com.matanjerbi.cinematheque.BuildConfig
import com.matanjerbi.cinematheque.data.repository.SearchResult
import com.matanjerbi.cinematheque.data.repository.TitleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import retrofit2.HttpException
import java.io.IOException

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val items: List<SearchResult>) : SearchState
    data class Error(val message: String) : SearchState
}

/**
 * Debounced TMDB search shared by the search screen and past-watches mode.
 * Typing cancels the previous request (flatMapLatest), so only the last query hits the network.
 */
class SearchController(private val repository: TitleRepository, scope: CoroutineScope) {
    val query = MutableStateFlow("")

    /** Bumped to force the current query to run again (retry, or refresh "in library" marks). */
    private val refreshKey = MutableStateFlow(0)

    val state: StateFlow<SearchState> = query
        .debounce(300)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q -> refreshKey.map { q } }
        .flatMapLatest { q ->
            if (q.length < TitleRepository.MIN_QUERY_LENGTH) flowOf(SearchState.Idle)
            else flow {
                emit(SearchState.Loading)
                emit(
                    repository.search(q).fold(
                        onSuccess = { SearchState.Results(it) },
                        onFailure = { SearchState.Error(it.toUserMessage()) },
                    )
                )
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)

    fun retry() { refreshKey.value++ }
}

fun Throwable.toUserMessage(): String = when {
    BuildConfig.TMDB_API_KEY.isBlank() ->
        "חסר מפתח TMDB. צריך להוסיף אותו ב־GitHub (Secrets → TMDB_API_KEY) ולבנות מחדש."
    this is HttpException && code() == 401 -> "מפתח ה־TMDB לא תקין."
    this is IOException -> "אין חיבור לאינטרנט."
    else -> "משהו השתבש: ${message ?: javaClass.simpleName}"
}
