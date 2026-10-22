package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matanjerbi.cinematheque.AppContainer
import com.matanjerbi.cinematheque.data.local.dao.TagWithUsage
import com.matanjerbi.cinematheque.data.local.entity.DatePrecision
import com.matanjerbi.cinematheque.data.local.entity.WatchEntryEntity
import com.matanjerbi.cinematheque.data.repository.SearchResult
import com.matanjerbi.cinematheque.ui.common.BackButton
import com.matanjerbi.cinematheque.ui.common.Poster
import com.matanjerbi.cinematheque.ui.common.RatingPicker
import com.matanjerbi.cinematheque.ui.common.SearchController
import com.matanjerbi.cinematheque.ui.common.TagPicker
import com.matanjerbi.cinematheque.ui.common.label
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import com.matanjerbi.cinematheque.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PastPick(
    val result: SearchResult,
    val rating: Int = 7,
    val knowsYear: Boolean = false,
    val year: Int = LocalDate.now().year - 1,
    val tagIds: Set<Long> = emptySet(),
)

class PastWatchesViewModel(private val container: AppContainer) : ViewModel() {
    private val db = container.database
    val search = SearchController(container.titleRepository, viewModelScope)
    val pick = MutableStateFlow<PastPick?>(null)
    val saving = MutableStateFlow(false)
    val savedCount = MutableStateFlow(0)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val tags: StateFlow<List<TagWithUsage>> = db.tagDao().observeAllWithUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(result: SearchResult) { pick.value = PastPick(result) }
    fun clear() { pick.value = null }
    fun update(block: (PastPick) -> PastPick) = pick.update { it?.let(block) }
    fun toggleTag(id: Long) = update { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }

    fun createTag(name: String) {
        viewModelScope.launch {
            val id = db.tagDao().getOrCreate(name)
            update { it.copy(tagIds = it.tagIds + id) }
        }
    }

    /** Save & next: caches the title, stores the watch, clears the search for the next one. */
    fun saveAndNext() {
        val p = pick.value ?: return
        if (saving.value) return
        viewModelScope.launch {
            saving.value = true
            container.titleRepository.ensureSaved(p.result.tmdbId, p.result.type)
                .onSuccess { titleId ->
                    db.watchEntryDao().save(
                        WatchEntryEntity(
                            titleId = titleId,
                            watchedDate = if (p.knowsYear) LocalDate.of(p.year, 1, 1) else null,
                            datePrecision = if (p.knowsYear) DatePrecision.YEAR else DatePrecision.UNKNOWN,
                            rating = p.rating,
                        ),
                        p.tagIds.toList(),
                    )
                    savedCount.value++
                    messages.emit("${p.result.title} נשמר (${p.rating})")
                    pick.value = null
                    search.query.value = ""
                }
                .onFailure { messages.emit(it.toUserMessage()) }
            saving.value = false
        }
    }
}

@Composable
fun PastWatchesScreen(onDone: () -> Unit) {
    val container = rememberContainer()
    val vm: PastWatchesViewModel = viewModel { PastWatchesViewModel(container) }
    val query by vm.search.query.collectAsStateWithLifecycle()
    val state by vm.search.state.collectAsStateWithLifecycle()
    val pick by vm.pick.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val count by vm.savedCount.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (count == 0) "סרטים שראיתי בעבר" else "סרטים מהעבר · $count נשמרו") },
                navigationIcon = { BackButton(onDone) },
                actions = { TextButton(onClick = onDone) { Text("סיום") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val current = pick
            if (current == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.search.query.value = it },
                    placeholder = { Text("חפש סרט או סדרה שראית") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "בחר כותר, תן דירוג ולחץ \"שמור והבא\". תגיות ושנה הם אופציונליים.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SearchResultsList(
                    state = state,
                    busyId = null,
                    actionLabel = "בחר",
                    onPick = vm::select,
                    onRetry = vm.search::retry,
                )
            } else {
                QuickRateCard(
                    pick = current,
                    tags = tags,
                    saving = saving,
                    onUpdate = vm::update,
                    onToggleTag = vm::toggleTag,
                    onCreateTag = vm::createTag,
                    onCancel = vm::clear,
                    onSave = vm::saveAndNext,
                )
            }
        }
    }
}

@Composable
private fun QuickRateCard(
    pick: PastPick,
    tags: List<TagWithUsage>,
    saving: Boolean,
    onUpdate: ((PastPick) -> PastPick) -> Unit,
    onToggleTag: (Long) -> Unit,
    onCreateTag: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Poster(localPath = null, remoteUrl = pick.result.posterUrl, width = 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(pick.result.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(pick.result.year?.toString(), pick.result.type.label()).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, "ביטול") }
            }

            RatingPicker(pick.rating) { r -> onUpdate { it.copy(rating = r) } }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(selected = !pick.knowsYear, onClick = { onUpdate { it.copy(knowsYear = false) } }, label = { Text("לא זוכר מתי") })
                FilterChip(selected = pick.knowsYear, onClick = { onUpdate { it.copy(knowsYear = true) } }, label = { Text("אני זוכר את השנה") })
            }
            if (pick.knowsYear) YearStepper(pick.year) { y -> onUpdate { it.copy(year = y) } }

            Text("תגיות", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            TagPicker(tags, pick.tagIds, onToggleTag, onCreateTag)

            Button(onClick = onSave, enabled = !saving, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                if (saving) CircularProgressIndicator(strokeWidth = 2.dp) else Text("שמור והבא")
            }
        }
    }
}
