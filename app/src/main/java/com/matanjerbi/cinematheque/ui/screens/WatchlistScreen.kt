package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.matanjerbi.cinematheque.data.local.entity.WatchlistItemEntity
import com.matanjerbi.cinematheque.data.local.entity.WatchlistItemWithTitle
import com.matanjerbi.cinematheque.ui.common.EmptyState
import com.matanjerbi.cinematheque.ui.common.Poster
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import com.matanjerbi.cinematheque.ui.common.subtitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchlistViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.database.watchlistDao()
    val items: StateFlow<List<WatchlistItemWithTitle>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(item: WatchlistItemEntity) = viewModelScope.launch { dao.upsert(item) }
    fun remove(titleId: Long) = viewModelScope.launch { dao.remove(titleId) }
}

@Composable
fun WatchlistScreen(
    onAdd: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onMarkWatched: (Long) -> Unit,
) {
    val container = rememberContainer()
    val vm: WatchlistViewModel = viewModel { WatchlistViewModel(container) }
    val items by vm.items.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WatchlistItemEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("רשימת צפייה") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("הוסף") })
        },
    ) { padding ->
        val list = items ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                "אין כלום ברשימה",
                "שמור כאן סרטים וסדרות שהמליצו לך עליהם, עם הערה ומי המליץ.",
                Modifier.padding(padding),
            ) { Button(onClick = onAdd) { Text("הוסף לרשימה") } }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            items(list, key = { it.item.titleId }) { row ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenTitle(row.title.id) }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Poster(row.title.posterLocalPath, width = 52.dp)
                    Column(Modifier.weight(1f)) {
                        Text(row.title.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.title.subtitle(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (row.item.recommendedBy.isNotBlank()) {
                            Text("המליץ/ה: ${row.item.recommendedBy}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (row.item.note.isNotBlank()) {
                            Text(row.item.note, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledTonalButton(onClick = { onMarkWatched(row.title.id) }) { Text("צפיתי") }
                        Row {
                            IconButton(onClick = { editing = row.item }) { Icon(Icons.Outlined.Edit, "עריכת הערה") }
                            IconButton(onClick = { vm.remove(row.item.titleId) }) { Icon(Icons.Outlined.Delete, "הסרה") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        var note by remember(item) { mutableStateOf(item.note) }
        var recommender by remember(item) { mutableStateOf(item.recommendedBy) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("הערה") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = recommender, onValueChange = { recommender = it }, label = { Text("מי המליץ") }, singleLine = true)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("למה / הערה") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.save(item.copy(note = note.trim(), recommendedBy = recommender.trim()))
                    editing = null
                }) { Text("שמור") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("ביטול") } },
        )
    }
}
