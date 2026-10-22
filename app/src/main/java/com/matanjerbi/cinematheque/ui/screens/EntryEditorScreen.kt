package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.matanjerbi.cinematheque.data.local.entity.TitleEntity
import com.matanjerbi.cinematheque.data.local.entity.WatchEntryEntity
import com.matanjerbi.cinematheque.ui.common.BackButton
import com.matanjerbi.cinematheque.ui.common.ConfirmDialog
import com.matanjerbi.cinematheque.ui.common.Poster
import com.matanjerbi.cinematheque.ui.common.RatingPicker
import com.matanjerbi.cinematheque.ui.common.SectionHeader
import com.matanjerbi.cinematheque.ui.common.TagPicker
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import com.matanjerbi.cinematheque.ui.common.subtitle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class EntryForm(
    val loaded: Boolean = false,
    val title: TitleEntity? = null,
    val precision: DatePrecision = DatePrecision.EXACT,
    val date: LocalDate = LocalDate.now(),
    val year: Int = LocalDate.now().year,
    val rating: Int = 7,
    val tagIds: Set<Long> = emptySet(),
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isRewatch: Boolean = false,
)

class EntryEditorViewModel(
    private val container: AppContainer,
    private val titleId: Long,
    private val entryId: Long,
) : ViewModel() {
    private val db = container.database
    val form = MutableStateFlow(EntryForm())
    val tags: StateFlow<List<TagWithUsage>> = db.tagDao().observeAllWithUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            val title = db.titleDao().getById(titleId)
            val existing = if (entryId != 0L) db.watchEntryDao().getWithDetails(entryId) else null
            form.value = if (existing == null) {
                EntryForm(loaded = true, title = title)
            } else {
                val e = existing.entry
                EntryForm(
                    loaded = true,
                    title = title,
                    precision = e.datePrecision,
                    date = e.watchedDate ?: LocalDate.now(),
                    year = e.watchedDate?.year ?: LocalDate.now().year,
                    rating = e.rating,
                    tagIds = existing.tags.map { it.id }.toSet(),
                    text = e.freeText,
                    createdAt = e.createdAt,
                    isRewatch = e.isRewatch,
                )
            }
        }
    }

    fun update(block: (EntryForm) -> EntryForm) = form.update(block)

    fun toggleTag(id: Long) = form.update { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }

    fun createTag(name: String) {
        viewModelScope.launch {
            val id = db.tagDao().getOrCreate(name)
            form.update { it.copy(tagIds = it.tagIds + id) }
        }
    }

    fun save() {
        val f = form.value
        val date = when (f.precision) {
            DatePrecision.EXACT -> f.date
            DatePrecision.YEAR -> LocalDate.of(f.year, 1, 1)
            DatePrecision.UNKNOWN -> null
        }
        viewModelScope.launch {
            db.watchEntryDao().save(
                WatchEntryEntity(
                    id = entryId,
                    titleId = titleId,
                    watchedDate = date,
                    datePrecision = f.precision,
                    rating = f.rating,
                    freeText = f.text.trim(),
                    isRewatch = f.isRewatch,
                    createdAt = f.createdAt,
                ),
                f.tagIds.toList(),
            )
            done.emit(Unit)
        }
    }

    fun delete() {
        viewModelScope.launch {
            db.watchEntryDao().delete(entryId)
            done.emit(Unit)
        }
    }
}

@Composable
fun EntryEditorScreen(titleId: Long, entryId: Long, onDone: () -> Unit) {
    val container = rememberContainer()
    val vm: EntryEditorViewModel = viewModel { EntryEditorViewModel(container, titleId, entryId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.done.collect { onDone() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entryId == 0L) "צפייה חדשה" else "עריכת צפייה") },
                navigationIcon = { BackButton(onDone) },
                actions = {
                    if (entryId != 0L) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "מחיקה") }
                    }
                },
            )
        },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            form.title?.let { t ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Poster(t.posterLocalPath, width = 64.dp)
                    Column {
                        Text(t.title, style = MaterialTheme.typography.titleLarge)
                        Text(t.subtitle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionHeader("מתי")
            DateSection(form, vm::update)

            Spacer(Modifier.height(8.dp))
            RatingPicker(form.rating) { r -> vm.update { it.copy(rating = r) } }

            SectionHeader("תגיות")
            TagPicker(tags, form.tagIds, vm::toggleTag, vm::createTag)

            SectionHeader("מה חשבתי")
            OutlinedTextField(
                value = form.text,
                onValueChange = { t -> vm.update { it.copy(text = t) } },
                placeholder = { Text("תמה, סצנה שנשארה איתך, מה הסרט אומר...") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            )

            Button(
                onClick = vm::save,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            ) { Text("שמור") }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "למחוק את הצפייה?",
            body = "הכותר יישאר בספרייה, רק הצפייה הזאת תימחק.",
            confirmText = "מחק",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete() },
        )
    }
}

/** Exact date / year only / don't remember — shared with past-watches mode. */
@Composable
fun DateSection(form: EntryForm, update: ((EntryForm) -> EntryForm) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            DatePrecision.EXACT to "תאריך",
            DatePrecision.YEAR to "רק שנה",
            DatePrecision.UNKNOWN to "לא זוכר",
        ).forEach { (p, label) ->
            FilterChip(selected = form.precision == p, onClick = { update { it.copy(precision = p) } }, label = { Text(label) })
        }
    }
    when (form.precision) {
        DatePrecision.EXACT -> OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text(form.date.format(java.time.format.DateTimeFormatter.ofPattern("d.M.yyyy")))
        }
        DatePrecision.YEAR -> YearStepper(form.year) { y -> update { it.copy(year = y) } }
        DatePrecision.UNKNOWN -> {}
    }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        update { it.copy(date = d) }
                    }
                    showPicker = false
                }) { Text("אישור") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("ביטול") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun YearStepper(year: Int, onChange: (Int) -> Unit) {
    val max = LocalDate.now().year
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        IconButton(onClick = { onChange((year - 1).coerceAtLeast(1920)) }) { Icon(Icons.Outlined.Remove, "שנה קודמת") }
        Text("$year", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = { onChange((year + 1).coerceAtMost(max)) }) { Icon(Icons.Outlined.Add, "שנה הבאה") }
    }
}
