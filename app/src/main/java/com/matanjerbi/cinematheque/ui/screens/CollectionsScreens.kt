package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.matanjerbi.cinematheque.AppContainer
import com.matanjerbi.cinematheque.data.local.dao.CollectionDao
import com.matanjerbi.cinematheque.data.local.entity.CollectionEntity
import com.matanjerbi.cinematheque.data.local.entity.CollectionItemRow
import com.matanjerbi.cinematheque.data.local.entity.CollectionSummary
import com.matanjerbi.cinematheque.ui.common.BackButton
import com.matanjerbi.cinematheque.ui.common.ConfirmDialog
import com.matanjerbi.cinematheque.ui.common.EmptyState
import com.matanjerbi.cinematheque.ui.common.Poster
import com.matanjerbi.cinematheque.ui.common.TextInputDialog
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import com.matanjerbi.cinematheque.ui.common.subtitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

// ---------- Grid ----------

class CollectionsViewModel(container: AppContainer) : ViewModel() {
    val dao: CollectionDao = container.database.collectionDao()
    val collections: StateFlow<List<CollectionSummary>?> = dao.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String) = viewModelScope.launch { dao.create(CollectionEntity(name = name)) }
}

@Composable
fun CollectionsScreen(onOpenCollection: (Long) -> Unit) {
    val container = rememberContainer()
    val vm: CollectionsViewModel = viewModel { CollectionsViewModel(container) }
    val collections by vm.collections.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("אוספים") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("אוסף חדש") },
            )
        },
    ) { padding ->
        val list = collections ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                "עוד אין אוספים",
                "רשימות שאתה בונה בעצמך, למשל \"מד״ב שחובה לראות\" או \"קאנון בית ספר לקולנוע\". אפשר לכלול גם סרטים שעוד לא ראית.",
                Modifier.padding(padding),
            ) { Button(onClick = { creating = true }) { Text("צור אוסף") } }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(list, key = { it.collection.id }) { c ->
                    CollectionCard(c, vm.dao) { onOpenCollection(c.collection.id) }
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "אוסף חדש",
            label = "שם האוסף",
            confirmText = "צור",
            onDismiss = { creating = false },
            onConfirm = { vm.create(it); creating = false },
        )
    }
}

@Composable
private fun CollectionCard(summary: CollectionSummary, dao: CollectionDao, onClick: () -> Unit) {
    val postersFlow = remember(summary.collection.id) { dao.observeCollagePosters(summary.collection.id) }
    val posters by postersFlow.collectAsStateWithLifecycle(emptyList())
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (posters.isEmpty()) {
                Icon(Icons.Outlined.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // 2x2 collage; fewer than four posters just fill the first cells
                Column(Modifier.fillMaxSize()) {
                    for (r in 0..1) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            for (c in 0..1) {
                                val path = posters.getOrNull(r * 2 + c)
                                Box(Modifier.weight(1f).fillMaxSize()) {
                                    if (path != null) {
                                        AsyncImage(
                                            model = File(path),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            summary.collection.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            if (summary.itemCount == 1) "כותר אחד" else "${summary.itemCount} כותרים",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Detail ----------

class CollectionDetailViewModel(container: AppContainer, private val collectionId: Long) : ViewModel() {
    private val dao = container.database.collectionDao()
    val collection: StateFlow<CollectionEntity?> = dao.observe(collectionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val items: StateFlow<List<CollectionItemRow>> = dao.observeItems(collectionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun move(index: Int, delta: Int) {
        val ids = items.value.map { it.item.titleId }.toMutableList()
        val target = index + delta
        if (target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        viewModelScope.launch { dao.reorder(collectionId, ids) }
    }

    fun remove(titleId: Long) = viewModelScope.launch { dao.removeItem(collectionId, titleId) }
    fun rename(name: String) = viewModelScope.launch { collection.value?.let { dao.update(it.copy(name = name)) } }
    fun delete() = viewModelScope.launch { dao.delete(collectionId) }
}

@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onAddTitles: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val container = rememberContainer()
    val vm: CollectionDetailViewModel = viewModel { CollectionDetailViewModel(container, collectionId) }
    val collection by vm.collection.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val watched = items.count { it.watched }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(collection?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (items.isNotEmpty()) {
                            Text(
                                "ראית $watched מתוך ${items.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "שינוי שם") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "מחיקת האוסף") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddTitles, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("הוסף כותרים") })
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState("האוסף ריק", "הוסף סרטים וסדרות, גם כאלה שעוד לא ראית.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 8.dp, bottom = 96.dp)) {
            itemsIndexed(items, key = { _, row -> row.item.titleId }) { index, row ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenTitle(row.title.id) }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Poster(row.title.posterLocalPath, width = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(row.title.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.title.subtitle(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (row.watched) Icon(Icons.Outlined.CheckCircle, "ראיתי", tint = MaterialTheme.colorScheme.primary)
                    Column {
                        IconButton(onClick = { vm.move(index, -1) }, enabled = index > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "למעלה") }
                        IconButton(onClick = { vm.move(index, 1) }, enabled = index < items.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, "למטה") }
                    }
                    IconButton(onClick = { vm.remove(row.item.titleId) }) { Icon(Icons.Outlined.Close, "הסר מהאוסף") }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "שם האוסף",
            label = "שם",
            initial = collection?.name.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = { vm.rename(it); renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "למחוק את האוסף?",
            body = "הסרטים עצמם והצפיות שלך לא יימחקו.",
            confirmText = "מחק",
            onDismiss = { deleting = false },
            onConfirm = { deleting = false; vm.delete(); onBack() },
        )
    }
}
