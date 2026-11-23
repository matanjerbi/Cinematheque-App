package com.matanjerbi.cinematheque.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matanjerbi.cinematheque.AppContainer
import com.matanjerbi.cinematheque.airing.AirSchedule
import com.matanjerbi.cinematheque.airing.AiringSuggestion
import com.matanjerbi.cinematheque.airing.airedAt
import com.matanjerbi.cinematheque.airing.nextAiringAfter
import com.matanjerbi.cinematheque.airing.notStartedAt
import com.matanjerbi.cinematheque.airing.nowHere
import com.matanjerbi.cinematheque.airing.schedule
import com.matanjerbi.cinematheque.airing.seasonFinishedAt
import com.matanjerbi.cinematheque.data.local.entity.FollowedShowEntity
import com.matanjerbi.cinematheque.data.local.entity.FollowedShowWithTitle
import com.matanjerbi.cinematheque.ui.common.EmptyState
import com.matanjerbi.cinematheque.ui.common.Poster
import com.matanjerbi.cinematheque.ui.common.StatBar
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ---------- labels ----------

private val dayNames = mapOf(
    DayOfWeek.SUNDAY to "ראשון", DayOfWeek.MONDAY to "שני", DayOfWeek.TUESDAY to "שלישי",
    DayOfWeek.WEDNESDAY to "רביעי", DayOfWeek.THURSDAY to "חמישי", DayOfWeek.FRIDAY to "שישי",
    DayOfWeek.SATURDAY to "שבת",
)
private val dayLetters = mapOf(
    DayOfWeek.SUNDAY to "א׳", DayOfWeek.MONDAY to "ב׳", DayOfWeek.TUESDAY to "ג׳",
    DayOfWeek.WEDNESDAY to "ד׳", DayOfWeek.THURSDAY to "ה׳", DayOfWeek.FRIDAY to "ו׳",
    DayOfWeek.SATURDAY to "ש׳",
)
private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private val shortDate = DateTimeFormatter.ofPattern("d.M")

private fun minuteLabel(minuteOfDay: Int) = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/** "ימי ראשון ורביעי ב־21:00" / "כל יום ב־21:00" / "יום שבת ב־04:00" */
fun FollowedShowEntity.scheduleLabel(): String {
    val days = AirSchedule.WEEK.filter { it in schedule.days }.map { dayNames.getValue(it) }
    val time = "ב־${minuteLabel(airMinuteOfDay)}"
    return when {
        days.isEmpty() -> "בלי ימי שידור"
        days.size == 7 -> "כל יום $time"
        days.size == 1 -> "יום ${days[0]} $time"
        else -> "ימי " + days.dropLast(1).joinToString(", ") + " ו" + days.last() + " " + time
    }
}

fun FollowedShowEntity.countLabel(now: ZonedDateTime): String {
    val aired = airedAt(now)
    val total = seasonEpisodeCount
    if (notStartedAt(now)) {
        val start = seasonStart!!
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), start)
        val whenLabel = when {
            days == 1L -> "מחר"
            days < 7L -> "ביום ${dayNames.getValue(start.dayOfWeek)} (${start.format(shortDate)})"
            else -> "ב־${start.format(shortDate)}"
        }
        return "עונה $season · עוד לא עלתה, מתחילה $whenLabel" + (total?.let { " · $it פרקים" } ?: "")
    }
    return "עונה $season · " + when {
        total != null && aired >= total -> "העונה הסתיימה, כל $total הפרקים בחוץ"
        aired == 0 -> "עוד לא יצא אף פרק" + (total?.let { " (מתוך $it)" } ?: "")
        total != null -> if (aired == 1) "יצא פרק 1 מתוך $total" else "יצאו $aired מתוך $total פרקים"
        aired == 1 -> "יצא פרק אחד"
        else -> "יצאו $aired פרקים"
    }
}

fun FollowedShowEntity.nextLabel(now: ZonedDateTime): String? {
    val next = nextAiringAfter(now) ?: return null
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate())
    val time = next.format(hhmm)
    val day = when {
        days == 0L -> "היום"
        days == 1L -> "מחר"
        days < 7L -> "ביום ${dayNames.getValue(next.dayOfWeek)}"
        else -> "ב־${next.format(shortDate)}"
    }
    return "פרק ${airedAt(now) + 1}: $day ב־$time"
}

/** Recomposes every 30 seconds so counts and "next episode" stay current while a screen is open. */
@Composable
fun rememberNow(): ZonedDateTime {
    val now by produceState(nowHere()) {
        while (true) {
            delay(30_000)
            value = nowHere()
        }
    }
    return now
}

// ---------- "airing now" tab ----------

class AiringViewModel(private val container: AppContainer) : ViewModel() {
    val shows: StateFlow<List<FollowedShowWithTitle>?> = container.database.followedShowDao().observeAllWithTitle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { container.followRepository.syncStale() }
    }

    fun setReminders(titleId: Long, on: Boolean) = viewModelScope.launch {
        container.followRepository.setReminders(titleId, on)
    }

    fun rescheduleAll() = viewModelScope.launch { container.episodeReminders.rescheduleAll() }
}

@Composable
fun AiringScreen(
    onAdd: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onDiscoverIsrael: () -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val vm: AiringViewModel = viewModel { AiringViewModel(container) }
    val shows by vm.shows.collectAsStateWithLifecycle()
    val now = rememberNow()

    // Re-checked on every return to the screen (e.g. after granting a permission in Settings).
    var canNotify by remember { mutableStateOf(container.episodeReminders.canNotify()) }
    var canExact by remember { mutableStateOf(container.episodeReminders.canScheduleExact()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = container.episodeReminders.canNotify()
        val exactNow = container.episodeReminders.canScheduleExact()
        if (exactNow && !canExact) vm.rescheduleAll()
        canExact = exactNow
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("משודרות עכשיו") },
                actions = {
                    TextButton(onClick = onDiscoverIsrael) {
                        Icon(Icons.Outlined.Explore, null, Modifier.size(18.dp))
                        Text("  חדש בישראל")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("סדרה") })
        },
    ) { padding ->
        val list = shows ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                "אין סדרות במעקב",
                "סדרה שאתה צופה בה עכשיו? סמן באילו ימים ובאיזו שעה היא משודרת, ותקבל תזכורת על כל פרק חדש ותראה כמה פרקים כבר יצאו.",
                Modifier.padding(padding),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAdd) { Text("הוסף סדרה") }
                    OutlinedButton(onClick = onDiscoverIsrael) { Text("מה חדש בישראל?") }
                }
            }
            return@Scaffold
        }
        val sorted = list.sortedWith(
            compareBy<FollowedShowWithTitle> { it.show.nextAiringAfter(now) == null }
                .thenBy { it.show.nextAiringAfter(now)?.toEpochSecond() ?: Long.MAX_VALUE }
        )
        val anyReminders = list.any { it.show.remindersOn }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            if (anyReminders && !canNotify && Build.VERSION.SDK_INT >= 33) {
                item {
                    NoticeCard(
                        "ההתראות של האפליקציה כבויות, אז התזכורות לא יופיעו.",
                        "הפעל התראות",
                    ) { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
            } else if (anyReminders && !canExact && Build.VERSION.SDK_INT >= 31) {
                item {
                    NoticeCard(
                        "התזכורות עלולות להגיע באיחור של כמה דקות. כדי שיגיעו בדיוק בשעת השידור, אשר \"התראות ותזכורות\".",
                        "לאישור",
                    ) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        )
                    }
                }
            }
            items(sorted, key = { it.show.titleId }) { row ->
                AiringRow(row, now, onClick = { onOpenTitle(row.title.id) }) { on ->
                    vm.setReminders(row.show.titleId, on)
                    if (on && !canNotify && Build.VERSION.SDK_INT >= 33) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

@Composable
private fun AiringRow(row: FollowedShowWithTitle, now: ZonedDateTime, onClick: () -> Unit, onReminders: (Boolean) -> Unit) {
    val show = row.show
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(row.title.posterLocalPath, width = 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(show.countLabel(now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            show.seasonEpisodeCount?.let { total ->
                StatBar(show.airedAt(now).toFloat() / total, Modifier.padding(vertical = 2.dp))
            }
            Text(show.scheduleLabel(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            show.nextLabel(now)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val finished = show.seasonFinishedAt(now)
        IconButton(onClick = { onReminders(!show.remindersOn) }, enabled = !finished) {
            Icon(
                if (show.remindersOn && !finished) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                if (show.remindersOn) "כבה תזכורות" else "הפעל תזכורות",
                tint = if (show.remindersOn && !finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

// ---------- title page section ----------

/** The "airing now" block on a series page: either an invitation to follow, or the live status. */
@Composable
fun AiringSection(
    show: FollowedShowEntity?,
    onFollow: () -> Unit,
    onEdit: () -> Unit,
    onReminders: (Boolean) -> Unit,
) {
    val now = rememberNow()
    if (show == null) {
        OutlinedButton(onClick = onFollow, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
            Text("  אני צופה בה עכשיו — מעקב פרקים")
        }
        return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    show.countLabel(now),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "עריכת לוח השידורים") }
            }
            show.seasonEpisodeCount?.let { StatBar(show.airedAt(now).toFloat() / it) }
            Text(show.scheduleLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            show.nextLabel(now)?.let { Text("הבא — $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!show.seasonFinishedAt(now)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("תזכורת כשיוצא פרק", modifier = Modifier.weight(1f))
                    Switch(checked = show.remindersOn, onCheckedChange = onReminders)
                }
            }
        }
    }
}

// ---------- follow / edit dialog ----------

@Composable
fun FollowDialog(
    existing: FollowedShowEntity?,
    titleId: Long,
    suggestion: AiringSuggestion?,
    loadingSuggestion: Boolean,
    suggestionError: String?,
    onFetchSuggestion: () -> Unit,
    onSave: (FollowedShowEntity) -> Unit,
    onUnfollow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val now = remember { nowHere() }
    var days by remember { mutableStateOf(existing?.schedule?.days ?: emptySet()) }
    var minute by remember { mutableIntStateOf(existing?.airMinuteOfDay ?: (21 * 60)) }
    var season by remember { mutableStateOf(existing?.season?.toString() ?: "1") }
    var aired by remember { mutableStateOf(existing?.airedAt(now)?.toString() ?: "0") }
    var total by remember { mutableStateOf(existing?.seasonEpisodeCount?.toString() ?: "") }
    var reminders by remember { mutableStateOf(existing?.remindersOn ?: true) }
    var start by remember { mutableStateOf(existing?.seasonStart?.takeIf { it.isAfter(now.toLocalDate()) }) }
    var confirmUnfollow by remember { mutableStateOf(false) }

    // Prefill from TMDB when it arrives (and again on every "update from TMDB").
    LaunchedEffect(suggestion) {
        val s = suggestion ?: return@LaunchedEffect
        season = s.season.toString()
        aired = s.aired.toString()
        total = s.seasonEpisodes?.toString() ?: ""
        if (days.isEmpty()) s.airDay?.let { days = setOf(it) }
        s.seasonStart?.let { start = it }
    }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val seasonNum = season.toIntOrNull()?.takeIf { it > 0 }
    val airedNum = aired.toIntOrNull()?.takeIf { it >= 0 }
    val totalNum = total.takeIf { it.isNotBlank() }?.toIntOrNull()?.takeIf { it > 0 }
    val totalValid = total.isBlank() || totalNum != null
    val futureStart = start?.takeIf { it.isAfter(now.toLocalDate()) }
    val valid = days.isNotEmpty() && seasonNum != null && airedNum != null && totalValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "מעקב פרקים" else "לוח שידורים") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("באילו ימים יוצא פרק?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AirSchedule.WEEK.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(dayLetters.getValue(day)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("שעה (אצלך)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> minute = h * 60 + m }, minute / 60, minute % 60, true).show()
                    }) { Text(minuteLabel(minute)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("עונה", season, { season = it }, Modifier.weight(1f), isError = seasonNum == null)
                    NumberField("יצאו עד עכשיו", aired, { aired = it }, Modifier.weight(1.4f), isError = airedNum == null)
                }
                NumberField(
                    "פרקים בעונה (לא חובה)", total, { total = it }, Modifier.fillMaxWidth(), isError = !totalValid,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("העונה מתחילה", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (futureStart == null) "כבר משודרת" else "עד אז לא נספרים פרקים",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = {
                        val initial = futureStart ?: now.toLocalDate().plusDays(1)
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                start = LocalDate.of(y, m + 1, d)
                                aired = "0"
                            },
                            initial.year, initial.monthValue - 1, initial.dayOfMonth,
                        ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
                    }) { Text(futureStart?.format(DateTimeFormatter.ofPattern("d.M.yyyy")) ?: "בחר תאריך") }
                    if (futureStart != null) {
                        TextButton(onClick = { start = null }) { Text("נקה") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        loadingSuggestion -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else -> TextButton(onClick = onFetchSuggestion) { Text("עדכן לפי TMDB") }
                    }
                    Text(
                        suggestionError ?: when {
                            suggestion?.ended == true -> "לפי TMDB הסדרה הסתיימה"
                            suggestion != null -> "יום השידור לפי TMDB הוא לפי ארה״ב, אולי יום אחרי אצלך"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (suggestionError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("תזכורת כשיוצא פרק חדש", modifier = Modifier.weight(1f))
                    Switch(checked = reminders, onCheckedChange = { reminders = it })
                }
                if (existing != null) {
                    TextButton(onClick = { confirmUnfollow = true }) {
                        Text("הפסק לעקוב", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val nowMs = System.currentTimeMillis()
                onSave(
                    FollowedShowEntity(
                        titleId = titleId,
                        airDays = AirSchedule.toMask(days),
                        airMinuteOfDay = minute,
                        remindersOn = reminders,
                        season = seasonNum ?: 1,
                        // A season that hasn't started has nothing out yet.
                        airedCount = if (futureStart != null) 0 else (airedNum ?: 0).let { a -> totalNum?.let { minOf(a, it) } ?: a },
                        airedCountAsOf = nowMs,
                        seasonEpisodeCount = totalNum,
                        tmdbSyncedAt = if (suggestion != null) nowMs else existing?.tmdbSyncedAt,
                        createdAt = existing?.createdAt ?: nowMs,
                        seasonStart = futureStart,
                    )
                )
                if (reminders && Build.VERSION.SDK_INT >= 33) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )

    if (confirmUnfollow) {
        AlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            title = { Text("להפסיק לעקוב?") },
            text = { Text("לוח השידורים והתזכורות יימחקו. הצפיות שתיעדת נשארות.") },
            confirmButton = { TextButton(onClick = { confirmUnfollow = false; onUnfollow() }) { Text("הפסק") } },
            dismissButton = { TextButton(onClick = { confirmUnfollow = false }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, isError: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
