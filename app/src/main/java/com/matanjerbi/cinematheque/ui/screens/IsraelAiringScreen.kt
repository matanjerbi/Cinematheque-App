package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matanjerbi.cinematheque.AppContainer
import com.matanjerbi.cinematheque.data.local.dao.SeriesLibraryStatus
import com.matanjerbi.cinematheque.data.local.entity.MediaType
import com.matanjerbi.cinematheque.data.local.entity.WatchlistItemEntity
import com.matanjerbi.cinematheque.data.repository.EpisodeInfo
import com.matanjerbi.cinematheque.data.repository.IsraelAiringRepository
import com.matanjerbi.cinematheque.data.repository.IsraelShow
import com.matanjerbi.cinematheque.data.repository.IsraelShowKind
import com.matanjerbi.cinematheque.ui.common.BackButton
import com.matanjerbi.cinematheque.ui.common.EmptyState
import com.matanjerbi.cinematheque.ui.common.Poster
import com.matanjerbi.cinematheque.ui.common.RatingBadge
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import com.matanjerbi.cinematheque.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ---------- state ----------

sealed interface IsraelListState {
    data object Loading : IsraelListState
    data class Error(val message: String) : IsraelListState
    data class Ready(val shows: List<IsraelShow>) : IsraelListState
}

class IsraelAiringViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow<IsraelListState>(IsraelListState.Loading)
    val refreshing = MutableStateFlow(false)

    /** tmdbIds the user marked as "interesting". */
    val selected = MutableStateFlow<Set<Int>>(emptySet())

    /** tmdbId of a series being saved for "starting to watch", to show a spinner. */
    val busyId = MutableStateFlow<Int?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val openFollowFor = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    val library: StateFlow<Map<Int, SeriesLibraryStatus>> = container.database.titleDao().observeSeriesLibraryStatus()
        .map { list -> list.associateBy { it.tmdbId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        load(force = false)
    }

    fun load(force: Boolean) = viewModelScope.launch {
        if (state.value !is IsraelListState.Ready) state.value = IsraelListState.Loading
        refreshing.value = true
        container.israelAiringRepository.load(force)
            .onSuccess { state.value = IsraelListState.Ready(it) }
            .onFailure { e ->
                if (state.value is IsraelListState.Ready) messages.emit(e.toUserMessage())
                else state.value = IsraelListState.Error(e.toUserMessage())
            }
        refreshing.value = false
    }

    fun toggle(tmdbId: Int) = selected.update { if (tmdbId in it) it - tmdbId else it + tmdbId }

    fun clearSelection() {
        selected.value = emptySet()
    }

    /** Saves every selected series and adds it to the watchlist. */
    fun addSelectedToWatchlist() = viewModelScope.launch {
        val ids = selected.value.toList()
        if (ids.isEmpty()) return@launch
        refreshing.value = true
        var added = 0
        var failed = 0
        ids.forEach { tmdbId ->
            container.titleRepository.ensureSaved(tmdbId, MediaType.TV)
                .onSuccess { titleId ->
                    container.database.watchlistDao().upsert(WatchlistItemEntity(titleId, recommendedBy = "חדש בישראל"))
                    added++
                }
                .onFailure { failed++ }
        }
        selected.value = emptySet()
        refreshing.value = false
        messages.emit(
            when {
                failed == 0 && added == 1 -> "נוספה סדרה אחת לרשימת הצפייה"
                failed == 0 -> "נוספו $added סדרות לרשימת הצפייה"
                else -> "נוספו $added, ו־$failed לא נשמרו (אין חיבור?)"
            }
        )
    }

    /** "Starting to watch": save the series and open its page with the episode-tracking form. */
    fun startWatching(show: IsraelShow) {
        if (busyId.value != null) return
        viewModelScope.launch {
            busyId.value = show.tmdbId
            container.titleRepository.ensureSaved(show.tmdbId, MediaType.TV)
                .onSuccess { openFollowFor.emit(it) }
                .onFailure { messages.emit(it.toUserMessage()) }
            busyId.value = null
        }
    }
}

// ---------- labels ----------

private val dayMonth = DateTimeFormatter.ofPattern("d.M")

private enum class KindFilter(val label: String) { ALL("הכל"), NEW("סדרות חדשות"), SEASON("עונות חדשות"), SOON("בקרוב") }

private fun IsraelShowKind.label() = when (this) {
    IsraelShowKind.NEW_SERIES -> "סדרה חדשה"
    IsraelShowKind.NEW_SEASON -> "עונה חדשה"
    IsraelShowKind.COMING_SOON -> "בקרוב"
}

private fun KindFilter.matches(kind: IsraelShowKind) = when (this) {
    KindFilter.ALL -> true
    KindFilter.NEW -> kind == IsraelShowKind.NEW_SERIES
    KindFilter.SEASON -> kind == IsraelShowKind.NEW_SEASON
    KindFilter.SOON -> kind == IsraelShowKind.COMING_SOON
}

private fun relativeDay(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when (days) {
        0L -> "היום"
        1L -> "מחר"
        -1L -> "אתמול"
        else -> "ב־${date.format(dayMonth)}"
    }
}

/** "עונה 2 · 6 פרקים · פרק 3 יצא ב־18.11 · פרק 4 מחר" */
private fun IsraelShow.progressLabel(today: LocalDate): String {
    val parts = mutableListOf<String>()
    season?.let { s ->
        parts += "עונה $s" + (seasonEpisodes?.let { if (it == 1) " · פרק אחד" else " · $it פרקים" } ?: "")
    }
    // An "aired" date that is still ahead isn't aired.
    lastEpisode?.takeIf { it.season == season && it.airDate?.isAfter(today) != true }?.let { e ->
        parts += "פרק ${e.episode} יצא" + (e.airDate?.let { " ${relativeDay(it, today)}" } ?: "")
    }
    nextEpisode?.let { e: EpisodeInfo ->
        val prefix = if (e.season != season) "עונה ${e.season} פרק ${e.episode}" else "פרק ${e.episode}"
        parts += prefix + (e.airDate?.let { " ${relativeDay(it, today)}" } ?: " בקרוב")
    }
    if (parts.isEmpty() && firstAirDate != null) parts += "עלתה ${relativeDay(firstAirDate, today)}"
    return parts.joinToString(" · ")
}

// ---------- screen ----------

@Composable
fun IsraelAiringScreen(
    onBack: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onStartWatching: (Long) -> Unit,
) {
    val container = rememberContainer()
    val vm: IsraelAiringViewModel = viewModel { IsraelAiringViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val busyId by vm.busyId.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var filter by rememberSaveable { mutableStateOf(KindFilter.ALL) }
    var hideMine by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.openFollowFor.collect { onStartWatching(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("חדש בישראל") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { vm.load(force = true) }) { Icon(Icons.Outlined.Refresh, "רענון") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (selected.size == 1) "נבחרה סדרה אחת" else "נבחרו ${selected.size} סדרות",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = vm::clearSelection) { Text("נקה") }
                        Button(onClick = { vm.addSelectedToWatchlist() }, enabled = !refreshing) { Text("לרשימת הצפייה") }
                    }
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            IsraelListState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("אוסף את הסדרות הישראליות שמשודרות עכשיו…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is IsraelListState.Error -> EmptyState("לא הצלחתי לטעון", s.message, Modifier.padding(padding)) {
                Button(onClick = { vm.load(force = true) }) { Text("נסה שוב") }
            }
            is IsraelListState.Ready -> {
                val today = LocalDate.now()
                val visible = s.shows
                    .filter { filter.matches(it.kind) }
                    .filter { !hideMine || library[it.tmdbId]?.let { st -> st.inWatchlist || st.following || st.watched } != true }
                LazyColumn(
                    Modifier.padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "סדרות ישראליות שיצא להן פרק ב־${IsraelAiringRepository.DAYS_BACK} הימים האחרונים או שיעלו בשבועות הקרובים. " +
                                    "סמן מה מעניין אותך והוסף לרשימת הצפייה, או לחץ \"מתחיל לראות\" כדי לעקוב אחרי הפרקים.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                KindFilter.entries.forEach { f ->
                                    val count = s.shows.count { f.matches(it.kind) }
                                    FilterChip(
                                        selected = filter == f,
                                        onClick = { filter = f },
                                        label = { Text("${f.label} ($count)") },
                                    )
                                }
                                FilterChip(selected = hideMine, onClick = { hideMine = !hideMine }, label = { Text("הסתר מה שכבר אצלי") })
                            }
                        }
                    }
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                if (s.shows.isEmpty()) "TMDB לא מצא כרגע סדרות ישראליות משודרות. נסה לרענן מאוחר יותר."
                                else "אין סדרות בסינון הזה.",
                                modifier = Modifier.padding(vertical = 32.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(visible, key = { it.tmdbId }) { show ->
                        IsraelShowCard(
                            show = show,
                            today = today,
                            status = library[show.tmdbId],
                            selected = show.tmdbId in selected,
                            busy = busyId == show.tmdbId,
                            onToggle = { vm.toggle(show.tmdbId) },
                            onStart = { vm.startWatching(show) },
                            onOpen = onOpenTitle,
                        )
                    }
                    item {
                        Text(
                            "המידע מ־TMDB. לפעמים חסרות שם סדרות ישראליות או ערוץ השידור.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IsraelShowCard(
    show: IsraelShow,
    today: LocalDate,
    status: SeriesLibraryStatus?,
    selected: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onStart: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    var expanded by rememberSaveable(show.tmdbId) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.clickable { expanded = !expanded }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Poster(localPath = null, remoteUrl = show.posterUrl, width = 72.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            show.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        show.tmdbRating?.takeIf { show.voteCount >= 3 }?.let {
                            Spacer(Modifier.width(6.dp))
                            RatingBadge(it)
                        }
                    }
                    if (show.originalTitle.isNotBlank() && show.originalTitle != show.title) {
                        Text(show.originalTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Pill(show.kind.label(), highlighted = true)
                        show.whereToWatch.forEach { Pill(it) }
                    }
                    show.progressLabel(today).takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (show.genres.isNotEmpty()) {
                        Text(
                            show.genres.take(3).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                show.overview.ifBlank { "אין עדיין תקציר ב־TMDB." },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
                Text("מעניין אותי", modifier = Modifier.clickable(onClick = onToggle))
                Spacer(Modifier.weight(1f))
                when {
                    status?.following == true ->
                        TextButton(onClick = { onOpen(status.titleId) }) { Text("במעקב ✓") }
                    busy -> CircularProgressIndicator(Modifier.padding(horizontal = 24.dp).size(20.dp), strokeWidth = 2.dp)
                    else -> TextButton(onClick = onStart) { Text("מתחיל לראות") }
                }
            }
            if (status != null && !status.following && (status.inWatchlist || status.watched)) {
                Text(
                    listOfNotNull(
                        "ברשימת הצפייה".takeIf { status.inWatchlist },
                        "יש תיעוד צפייה ביומן".takeIf { status.watched },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, highlighted: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}
