package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.matanjerbi.cinematheque.data.local.dao.TagWithUsage
import com.matanjerbi.cinematheque.data.local.entity.DatePrecision
import com.matanjerbi.cinematheque.data.local.entity.EntryWithDetails
import com.matanjerbi.cinematheque.data.local.entity.MediaType
import com.matanjerbi.cinematheque.ui.common.EmptyState
import com.matanjerbi.cinematheque.ui.common.Poster
import com.matanjerbi.cinematheque.ui.common.RatingBadge
import com.matanjerbi.cinematheque.ui.common.SectionHeader
import com.matanjerbi.cinematheque.ui.common.SmallTags
import com.matanjerbi.cinematheque.ui.common.dateLabel
import com.matanjerbi.cinematheque.ui.common.label
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DiaryFilter(val type: MediaType? = null, val minRating: Int? = null, val tagId: Long? = null) {
    val active get() = type != null || minRating != null || tagId != null
}

class DiaryViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.database.watchEntryDao()
    val filter = MutableStateFlow(DiaryFilter())

    /** null = still loading. */
    val entries: StateFlow<List<EntryWithDetails>?> = filter
        .flatMapLatest { f -> dao.observeDiary(type = f.type, minRating = f.minRating, tagId = f.tagId).map { it } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tags: StateFlow<List<TagWithUsage>> = container.database.tagDao().observeAllWithUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private val monthHeader = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("he"))

/** Month for exact dates, year for year-only watches, "from the past" for undated ones. */
private fun EntryWithDetails.groupLabel(): String = when (entry.datePrecision) {
    DatePrecision.EXACT -> entry.watchedDate?.format(monthHeader) ?: "מהעבר"
    DatePrecision.YEAR -> entry.watchedDate?.year?.toString() ?: "מהעבר"
    DatePrecision.UNKNOWN -> "מהעבר"
}

@Composable
fun DiaryScreen(
    onAdd: () -> Unit,
    onAddPast: () -> Unit,
    onOpenEntry: (titleId: Long, entryId: Long) -> Unit,
    onOpenTitle: (Long) -> Unit,
    onManageTags: () -> Unit,
) {
    val container = rememberContainer()
    val vm: DiaryViewModel = viewModel { DiaryViewModel(container) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("היומן שלי") },
                actions = {
                    IconButton(onClick = onAddPast) { Icon(Icons.Outlined.History, "סרטים שראיתי בעבר") }
                    IconButton(onClick = onManageTags) { Icon(Icons.Outlined.Label, "ניהול תגיות") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("צפייה חדשה") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val list = entries ?: return@Column
            if (list.isEmpty() && !filter.active) {
                EmptyState(
                    title = "היומן עוד ריק",
                    body = "הוסף סרט או סדרה שראית עכשיו, או תתחיל מכל מה שראית בעבר.",
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAdd) { Text("צפייה חדשה") }
                        OutlinedButton(onClick = onAddPast) { Text("סרטים שראיתי בעבר") }
                    }
                }
                return@Column
            }

            FilterRow(filter, tags) { vm.filter.value = it }

            if (list.isEmpty()) {
                Text(
                    "אין צפיות שמתאימות לסינון",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
                var lastGroup: String? = null
                list.forEach { row ->
                    val group = row.groupLabel()
                    if (group != lastGroup) {
                        lastGroup = group
                        item(key = "h_$group") { SectionHeader(group) }
                    }
                    item(key = row.entry.id) {
                        DiaryRow(row, onClick = { onOpenTitle(row.title.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryRow(row: EntryWithDetails, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(row.title.posterLocalPath, width = 52.dp)
        Column(Modifier.weight(1f)) {
            Text(row.title.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                row.entry.dateLabel().takeIf { row.entry.datePrecision == DatePrecision.EXACT },
                row.title.type.label(),
                "צפייה חוזרת".takeIf { row.entry.isRewatch },
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SmallTags(row.tags.map { it.name })
            if (row.entry.freeText.isNotBlank()) {
                Text(
                    row.entry.freeText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RatingBadge(row.entry.rating)
    }
}

@Composable
private fun FilterRow(filter: DiaryFilter, tags: List<TagWithUsage>, onChange: (DiaryFilter) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = filter.type == MediaType.MOVIE, onClick = {
            onChange(filter.copy(type = if (filter.type == MediaType.MOVIE) null else MediaType.MOVIE))
        }, label = { Text("סרטים") })
        FilterChip(selected = filter.type == MediaType.TV, onClick = {
            onChange(filter.copy(type = if (filter.type == MediaType.TV) null else MediaType.TV))
        }, label = { Text("סדרות") })

        DropdownChip(
            label = filter.minRating?.let { "$it ומעלה" } ?: "דירוג",
            selected = filter.minRating != null,
            options = listOf<Pair<String, Int?>>("הכל" to null) + (9 downTo 5).map { "$it ומעלה" to it },
            onSelect = { onChange(filter.copy(minRating = it)) },
        )
        DropdownChip(
            label = tags.firstOrNull { it.id == filter.tagId }?.name ?: "תגית",
            selected = filter.tagId != null,
            options = listOf<Pair<String, Long?>>("הכל" to null) + tags.filter { it.uses > 0 }.map { it.name to it.id },
            onSelect = { onChange(filter.copy(tagId = it)) },
        )
    }
}

@Composable
fun <T> DropdownChip(label: String, selected: Boolean, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}
