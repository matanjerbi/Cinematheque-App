package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matanjerbi.cinematheque.AppContainer
import com.matanjerbi.cinematheque.data.local.entity.WatchlistItemEntity
import com.matanjerbi.cinematheque.data.repository.SearchResult
import com.matanjerbi.cinematheque.ui.common.BackButton
import com.matanjerbi.cinematheque.ui.common.Poster
import com.matanjerbi.cinematheque.ui.common.RatingBadge
import com.matanjerbi.cinematheque.ui.common.SearchController
import com.matanjerbi.cinematheque.ui.common.SearchState
import com.matanjerbi.cinematheque.ui.common.label
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import com.matanjerbi.cinematheque.ui.common.toUserMessage
import com.matanjerbi.cinematheque.ui.navigation.SearchPurpose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val container: AppContainer,
    private val purpose: SearchPurpose,
    private val collectionId: Long,
) : ViewModel() {
    val search = SearchController(container.titleRepository, viewModelScope)

    /** tmdbId of the result currently being saved, to show a spinner on its button. */
    val busyId = MutableStateFlow<Int?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val openEntryFor = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    fun pick(result: SearchResult) {
        if (busyId.value != null) return
        viewModelScope.launch {
            busyId.value = result.tmdbId
            container.titleRepository.ensureSaved(result.tmdbId, result.type)
                .onSuccess { titleId ->
                    when (purpose) {
                        SearchPurpose.WATCHED -> openEntryFor.emit(titleId)
                        SearchPurpose.WATCHLIST -> {
                            container.database.watchlistDao().upsert(WatchlistItemEntity(titleId))
                            messages.emit("${result.title} נוסף לרשימת הצפייה")
                        }
                        SearchPurpose.ADD_TO_COLLECTION -> {
                            container.database.collectionDao().addItem(collectionId, titleId)
                            messages.emit("${result.title} נוסף לאוסף")
                        }
                    }
                    search.retry() // refresh "in library" marks
                }
                .onFailure { messages.emit(it.toUserMessage()) }
            busyId.value = null
        }
    }
}

@Composable
fun SearchScreen(
    purpose: SearchPurpose,
    collectionId: Long,
    onWatched: (titleId: Long) -> Unit,
    onBack: () -> Unit,
) {
    val container = rememberContainer()
    val vm: SearchViewModel = viewModel { SearchViewModel(container, purpose, collectionId) }
    val query by vm.search.query.collectAsStateWithLifecycle()
    val state by vm.search.state.collectAsStateWithLifecycle()
    val busyId by vm.busyId.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        launch { vm.messages.collect { snackbar.showSnackbar(it) } }
        vm.openEntryFor.collect(onWatched)
    }

    val title = when (purpose) {
        SearchPurpose.WATCHED -> "מה ראית?"
        SearchPurpose.WATCHLIST -> "הוספה לרשימת הצפייה"
        SearchPurpose.ADD_TO_COLLECTION -> "הוספה לאוסף"
    }
    val actionLabel = when (purpose) {
        SearchPurpose.WATCHED -> "צפיתי"
        SearchPurpose.WATCHLIST -> "לרשימה"
        SearchPurpose.ADD_TO_COLLECTION -> "הוסף"
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.search.query.value = it },
                placeholder = { Text("שם של סרט או סדרה") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus),
            )
            SearchResultsList(
                state = state,
                busyId = busyId,
                actionLabel = actionLabel,
                onPick = vm::pick,
                onRetry = vm.search::retry,
            )
        }
    }
}

/** Results list shared with past-watches mode. */
@Composable
fun SearchResultsList(
    state: SearchState,
    busyId: Int?,
    actionLabel: String,
    onPick: (SearchResult) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SearchState.Idle -> Hint("הקלד לפחות 2 אותיות כדי לחפש", modifier)
        SearchState.Loading -> Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is SearchState.Error -> Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("נסה שוב") }
        }
        is SearchState.Results -> if (state.items.isEmpty()) {
            Hint("לא נמצאו תוצאות", modifier)
        } else {
            LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.items, key = { "${it.type}_${it.tmdbId}" }) { item ->
                    SearchResultRow(item, busy = busyId == item.tmdbId, actionLabel, onPick)
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: SearchResult, busy: Boolean, actionLabel: String, onPick: (SearchResult) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { onPick(item) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Poster(localPath = null, remoteUrl = item.posterUrl, width = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            val meta = listOfNotNull(
                item.year?.toString(),
                item.type.label(),
                item.tmdbRating?.takeIf { it > 0 }?.let { "TMDB %.1f".format(it) },
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.originalTitle.isNotBlank() && item.originalTitle != item.title) {
                Text(item.originalTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.localId != null) {
                Text("בספרייה שלך", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            FilledTonalButton(onClick = { onPick(item) }) { Text(actionLabel) }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}
