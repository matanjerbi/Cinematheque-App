package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matanjerbi.cinematheque.AppContainer
import com.matanjerbi.cinematheque.data.local.entity.CollectionEntity
import com.matanjerbi.cinematheque.data.local.entity.CollectionSummary
import com.matanjerbi.cinematheque.data.local.entity.EntryWithDetails
import com.matanjerbi.cinematheque.data.local.entity.PersonCredit
import com.matanjerbi.cinematheque.data.local.entity.PersonRole
import com.matanjerbi.cinematheque.data.local.entity.TitleWithDetails
import com.matanjerbi.cinematheque.data.local.entity.WatchlistItemEntity
import com.matanjerbi.cinematheque.ui.common.BackButton
import com.matanjerbi.cinematheque.ui.common.Poster
import com.matanjerbi.cinematheque.ui.common.RatingBadge
import com.matanjerbi.cinematheque.ui.common.SectionHeader
import com.matanjerbi.cinematheque.ui.common.SmallTags
import com.matanjerbi.cinematheque.ui.common.TextInputDialog
import com.matanjerbi.cinematheque.ui.common.dateLabel
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import com.matanjerbi.cinematheque.ui.common.subtitle
import com.matanjerbi.cinematheque.ui.common.toUserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TitleDetailViewModel(private val container: AppContainer, private val titleId: Long) : ViewModel() {
    private val db = container.database
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val title: StateFlow<TitleWithDetails?> = db.titleDao().observeWithDetails(titleId).state(null)
    val credits: StateFlow<List<PersonCredit>> = db.titleDao().observeCredits(titleId).state(emptyList())
    val entries: StateFlow<List<EntryWithDetails>> = db.watchEntryDao().observeForTitle(titleId).state(emptyList())
    val inWatchlist: StateFlow<Boolean> = db.watchlistDao().observeContains(titleId).state(false)
    val collections: StateFlow<List<CollectionSummary>> = db.collectionDao().observeSummaries().state(emptyList())
    val memberOf: StateFlow<List<Long>> = db.collectionDao().observeCollectionIdsFor(titleId).state(emptyList())
    val refreshing = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 2)

    fun toggleWatchlist() = viewModelScope.launch {
        if (inWatchlist.value) db.watchlistDao().remove(titleId)
        else db.watchlistDao().upsert(WatchlistItemEntity(titleId))
    }

    fun toggleCollection(collectionId: Long) = viewModelScope.launch {
        if (collectionId in memberOf.value) db.collectionDao().removeItem(collectionId, titleId)
        else db.collectionDao().addItem(collectionId, titleId)
    }

    fun createCollectionAndAdd(name: String) = viewModelScope.launch {
        val id = db.collectionDao().create(CollectionEntity(name = name))
        db.collectionDao().addItem(id, titleId)
    }

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        container.titleRepository.refresh(titleId)
            .onSuccess { messages.emit("הפרטים עודכנו") }
            .onFailure { messages.emit(it.toUserMessage()) }
        refreshing.value = false
    }
}

@Composable
fun TitleDetailScreen(
    titleId: Long,
    onWatchAgain: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val container = rememberContainer()
    val vm: TitleDetailViewModel = viewModel { TitleDetailViewModel(container, titleId) }
    val details by vm.title.collectAsStateWithLifecycle()
    val credits by vm.credits.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val inWatchlist by vm.inWatchlist.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val memberOf by vm.memberOf.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var showCollections by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { vm.toggleWatchlist() }) {
                        Icon(
                            if (inWatchlist) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            if (inWatchlist) "הסר מרשימת הצפייה" else "הוסף לרשימת הצפייה",
                        )
                    }
                    if (refreshing) CircularProgressIndicator(Modifier.padding(12.dp).width(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { vm.refresh() }) { Icon(Icons.Outlined.Refresh, "רענון מ־TMDB") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val d = details ?: return@Scaffold
        val t = d.title
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Poster(t.posterLocalPath, width = 120.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.title, style = MaterialTheme.typography.headlineMedium)
                    if (t.originalTitle.isNotBlank() && t.originalTitle != t.title) {
                        Text(t.originalTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(t.subtitle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (d.genres.isNotEmpty()) {
                        Text(d.genres.joinToString(" · ") { it.genre }, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        t.tmdbRating?.let { Text("TMDB %.1f".format(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        val mine = entries.map { it.entry.rating }.average().takeIf { !it.isNaN() }
                        mine?.let { RatingBadge(it) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                Button(onClick = onWatchAgain) { Text(if (entries.isEmpty()) "צפיתי" else "צפיתי שוב") }
                OutlinedButton(onClick = { showCollections = true }) {
                    Text(if (memberOf.isEmpty()) "הוסף לאוסף" else "באוספים (${memberOf.size})")
                }
            }

            if (t.overview.isNotBlank()) {
                SectionHeader("תקציר")
                Text(t.overview, style = MaterialTheme.typography.bodyLarge)
            }

            val makers = credits.filter { it.role != PersonRole.CAST }
            val cast = credits.filter { it.role == PersonRole.CAST }
            if (makers.isNotEmpty()) {
                SectionHeader(if (makers.first().role == PersonRole.DIRECTOR) "בימוי" else "יצירה")
                Text(makers.joinToString(", ") { it.person.name })
            }
            if (cast.isNotEmpty()) {
                SectionHeader("שחקנים")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cast.forEach { AssistChip(onClick = {}, label = { Text(it.person.name) }) }
                }
            }

            SectionHeader(if (entries.size > 1) "הצפיות שלי (${entries.size})" else "הצפייה שלי")
            if (entries.isEmpty()) {
                Text("עוד לא תיעדת צפייה בכותר הזה.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entries.forEach { e -> WatchCard(e) { onOpenEntry(e.entry.id) } }

            if (memberOf.isNotEmpty()) {
                SectionHeader("באוספים")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    collections.filter { it.collection.id in memberOf }.forEach {
                        AssistChip(onClick = { showCollections = true }, label = { Text(it.collection.name) })
                    }
                }
            }
            Text(
                "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }

    if (showCollections) {
        CollectionsSheet(
            collections = collections,
            memberOf = memberOf,
            onToggle = { vm.toggleCollection(it) },
            onCreate = { vm.createCollectionAndAdd(it) },
            onDismiss = { showCollections = false },
        )
    }
}

@Composable
private fun WatchCard(e: EntryWithDetails, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.entry.dateLabel() + if (e.entry.isRewatch) " · צפייה חוזרת" else "",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RatingBadge(e.entry.rating)
            }
            SmallTags(e.tags.map { it.name })
            if (e.entry.freeText.isNotBlank()) {
                Text(e.entry.freeText, style = MaterialTheme.typography.bodyLarge, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CollectionsSheet(
    collections: List<CollectionSummary>,
    memberOf: List<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("אוספים") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (collections.isEmpty()) Text("עוד אין אוספים.")
                collections.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(c.collection.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c.collection.id in memberOf, onCheckedChange = { onToggle(c.collection.id) })
                        Text(c.collection.name)
                    }
                }
                TextButton(onClick = { creating = true }) { Text("+ אוסף חדש") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
    )
    if (creating) {
        TextInputDialog(
            title = "אוסף חדש",
            label = "שם האוסף",
            confirmText = "צור והוסף",
            onDismiss = { creating = false },
            onConfirm = { onCreate(it); creating = false },
        )
    }
}
