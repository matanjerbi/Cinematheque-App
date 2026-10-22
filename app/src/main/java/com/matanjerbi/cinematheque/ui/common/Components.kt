package com.matanjerbi.cinematheque.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.matanjerbi.cinematheque.AppContainer
import com.matanjerbi.cinematheque.appContainer
import com.matanjerbi.cinematheque.data.local.dao.TagWithUsage
import com.matanjerbi.cinematheque.data.local.entity.DatePrecision
import com.matanjerbi.cinematheque.data.local.entity.MediaType
import com.matanjerbi.cinematheque.data.local.entity.TitleEntity
import com.matanjerbi.cinematheque.data.local.entity.WatchEntryEntity
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun rememberContainer(): AppContainer {
    val context = LocalContext.current
    return remember { context.appContainer }
}

private val dateFormatter = DateTimeFormatter.ofPattern("d בMMMM yyyy", Locale("he"))

fun WatchEntryEntity.dateLabel(): String = when (datePrecision) {
    DatePrecision.EXACT -> watchedDate?.format(dateFormatter) ?: "בלי תאריך"
    DatePrecision.YEAR -> watchedDate?.year?.let { "ב־$it" } ?: "בלי תאריך"
    DatePrecision.UNKNOWN -> "מהעבר"
}

fun MediaType.label() = if (this == MediaType.MOVIE) "סרט" else "סדרה"

fun TitleEntity.subtitle(): String = listOfNotNull(
    year?.toString(),
    type.label(),
    runtimeMinutes?.let { "${it / 60}:${"%02d".format(it % 60)} ש׳" },
    seasonsCount?.let { if (it == 1) "עונה אחת" else "$it עונות" },
).joinToString(" · ")

/** Local poster file when cached, otherwise a remote URL (search results), otherwise a film icon. */
@Composable
fun Poster(localPath: String?, remoteUrl: String? = null, width: Dp = 56.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val model: Any? = localPath?.let(::File)?.takeIf { it.exists() } ?: remoteUrl
    Box(
        modifier = modifier
            .size(width, width * 1.5f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Outlined.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RatingBadge(rating: Number, modifier: Modifier = Modifier) {
    val text = if (rating is Double) "%.1f".format(rating) else rating.toString()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun SmallTags(names: List<String>) {
    if (names.isEmpty()) return
    Text(
        names.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        action()
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
}

/** Big number + slider, 1..10. */
@Composable
fun RatingPicker(rating: Int, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("דירוג", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "$rating",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(" / 10", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = rating.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(1, 10)) },
            valueRange = 1f..10f,
            steps = 8,
        )
    }
}

/** All tags as toggle chips, plus a "+ new tag" chip that creates one and selects it. */
@Composable
fun TagPicker(
    tags: List<TagWithUsage>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        tags.forEach { tag ->
            FilterChip(
                selected = tag.id in selected,
                onClick = { onToggle(tag.id) },
                label = { Text(tag.name) },
            )
        }
        SuggestionChip(
            onClick = { showNew = true },
            label = { Text("תגית חדשה") },
            icon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
        )
    }
    if (showNew) {
        TextInputDialog(
            title = "תגית חדשה",
            label = "שם התגית",
            onDismiss = { showNew = false },
            onConfirm = { onCreate(it); showNew = false },
        )
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirmText: String = "שמור",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirmText: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** Horizontal bar for stats rows; fraction 0..1. */
@Composable
fun StatBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .height(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
                .height(8.dp)
        )
    }
}
