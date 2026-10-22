package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matanjerbi.cinematheque.AppContainer
import com.matanjerbi.cinematheque.data.local.dao.TagWithUsage
import com.matanjerbi.cinematheque.ui.common.BackButton
import com.matanjerbi.cinematheque.ui.common.ConfirmDialog
import com.matanjerbi.cinematheque.ui.common.TextInputDialog
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.database.tagDao()
    val tags: StateFlow<List<TagWithUsage>> = dao.observeAllWithUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) = viewModelScope.launch { dao.getOrCreate(name) }

    /** Renaming onto an existing name merges the two instead of failing on the unique index. */
    fun rename(tag: TagWithUsage, name: String) = viewModelScope.launch {
        val other = dao.findByName(name)
        if (other != null && other.id != tag.id) dao.merge(tag.id, other.id) else dao.rename(tag.id, name)
    }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
    fun merge(fromId: Long, intoId: Long) = viewModelScope.launch { dao.merge(fromId, intoId) }
}

@Composable
fun TagsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: TagsViewModel = viewModel { TagsViewModel(container) }
    val tags by vm.tags.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<TagWithUsage?>(null) }
    var deleting by remember { mutableStateOf<TagWithUsage?>(null) }
    var merging by remember { mutableStateOf<TagWithUsage?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("תגיות") }, navigationIcon = { BackButton(onBack) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("תגית חדשה") })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 8.dp, bottom = 96.dp)) {
            items(tags, key = { it.id }) { tag ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tag.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            (if (tag.uses == 1) "צפייה אחת" else "${tag.uses} צפיות") + if (tag.isDefault) " · ברירת מחדל" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { renaming = tag }) { Icon(Icons.Outlined.Edit, "שינוי שם") }
                    IconButton(onClick = { merging = tag }) { Icon(Icons.Outlined.CallMerge, "מיזוג") }
                    IconButton(onClick = { deleting = tag }) { Icon(Icons.Outlined.Delete, "מחיקה") }
                }
            }
        }
    }

    if (creating) {
        TextInputDialog("תגית חדשה", "שם התגית", onDismiss = { creating = false }, onConfirm = { vm.create(it); creating = false })
    }
    renaming?.let { tag ->
        TextInputDialog(
            "שינוי שם", "שם חדש", initial = tag.name,
            onDismiss = { renaming = null },
            onConfirm = { vm.rename(tag, it); renaming = null },
        )
    }
    deleting?.let { tag ->
        ConfirmDialog(
            title = "למחוק את \"${tag.name}\"?",
            body = "התגית תוסר מכל הצפיות שלה. הצפיות עצמן לא יימחקו.",
            confirmText = "מחק",
            onDismiss = { deleting = null },
            onConfirm = { vm.delete(tag.id); deleting = null },
        )
    }
    merging?.let { from ->
        AlertDialog(
            onDismissRequest = { merging = null },
            title = { Text("למזג את \"${from.name}\" לתוך…") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    tags.filter { it.id != from.id }.forEach { into ->
                        Text(
                            into.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.merge(from.id, into.id); merging = null }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { merging = null }) { Text("ביטול") } },
        )
    }
}
