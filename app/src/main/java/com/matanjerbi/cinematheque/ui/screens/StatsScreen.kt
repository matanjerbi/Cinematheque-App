package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matanjerbi.cinematheque.AppContainer
import com.matanjerbi.cinematheque.data.local.dao.DisagreementStat
import com.matanjerbi.cinematheque.data.local.dao.GroupRatingStat
import com.matanjerbi.cinematheque.data.local.dao.PersonStat
import com.matanjerbi.cinematheque.data.local.dao.StatsPeriod
import com.matanjerbi.cinematheque.data.local.dao.StatsTotals
import com.matanjerbi.cinematheque.data.local.dao.TagStat
import com.matanjerbi.cinematheque.data.local.dao.toArgs
import com.matanjerbi.cinematheque.ui.common.EmptyState
import com.matanjerbi.cinematheque.ui.common.Poster
import com.matanjerbi.cinematheque.ui.common.RatingBadge
import com.matanjerbi.cinematheque.ui.common.StatBar
import com.matanjerbi.cinematheque.ui.common.rememberContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class StatsViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.database.statsDao()
    val period = MutableStateFlow<StatsPeriod>(StatsPeriod.All)

    private fun <T> query(initial: T, block: (Long?, Long?, Boolean) -> Flow<T>): StateFlow<T> =
        period.flatMapLatest { p -> p.toArgs().let { block(it.fromDay, it.toDay, it.includeYearPrecision) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val totals = query<StatsTotals?>(null) { f, t, y -> dao.observeTotals(f, t, y) }
    val directors = query(emptyList<PersonStat>()) { f, t, y -> dao.observeTopDirectors(f, t, y) }
    val cast = query(emptyList<PersonStat>()) { f, t, y -> dao.observeTopCast(f, t, y) }
    val tags = query(emptyList<TagStat>()) { f, t, y -> dao.observeTopTags(f, t, y) }
    val genres = query(emptyList<GroupRatingStat>()) { f, t, y -> dao.observeRatingByGenre(f, t, y) }
    val decades = query(emptyList<GroupRatingStat>()) { f, t, y -> dao.observeRatingByDecade(f, t, y) }
    val disagreements = query(emptyList<DisagreementStat>()) { f, t, y -> dao.observeDisagreements(f, t, y) }
}

private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("he"))

@Composable
fun StatsScreen(onOpenTitle: (Long) -> Unit) {
    val container = rememberContainer()
    val vm: StatsViewModel = viewModel { StatsViewModel(container) }
    val period by vm.period.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val directors by vm.directors.collectAsStateWithLifecycle()
    val cast by vm.cast.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val decades by vm.decades.collectAsStateWithLifecycle()
    val disagreements by vm.disagreements.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("סטטיסטיקות") }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeriodPicker(period) { vm.period.value = it }

            val t = totals
            if (t == null || t.watchCount == 0) {
                EmptyState("אין עדיין נתונים", "ברגע שתתעד צפיות, כאן תראה את הטעם שלך במספרים.")
                return@Column
            }

            StatCard("במספרים") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigNumber("${t.watchCount}", "צפיות")
                    BigNumber("${t.movieCount}", "סרטים")
                    BigNumber("${t.seriesCount}", "סדרות")
                    BigNumber("${t.movieMinutes / 60}", "שעות סרטים")
                }
                t.avgRating?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        Text("דירוג ממוצע", modifier = Modifier.weight(1f))
                        RatingBadge(it)
                    }
                }
            }

            if (directors.isNotEmpty()) StatCard("במאים ויוצרים שחוזרים") { PeopleList(directors) }
            if (cast.isNotEmpty()) StatCard("שחקנים שחוזרים") { PeopleList(cast) }

            if (tags.isNotEmpty()) StatCard("התגיות שלך") {
                tags.forEach { tag ->
                    LabeledBar(tag.name, "${(tag.share * 100).toInt()}%", tag.share.toFloat())
                }
            }

            if (genres.isNotEmpty()) StatCard("ממוצע לפי ז'אנר") {
                genres.forEach { g -> LabeledBar("${g.label} (${g.watchCount})", "%.1f".format(g.avgRating), (g.avgRating / 10).toFloat()) }
            }

            if (decades.isNotEmpty()) StatCard("ממוצע לפי עשור יציאה") {
                decades.forEach { d -> LabeledBar("שנות ה־${d.label.takeLast(2)}' (${d.watchCount})", "%.1f".format(d.avgRating), (d.avgRating / 10).toFloat()) }
            }

            if (disagreements.isNotEmpty()) StatCard("איפה חלקת על TMDB") {
                disagreements.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenTitle(d.titleId) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Poster(d.posterLocalPath, width = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "אתה %.1f · TMDB %.1f".format(d.myRating, d.tmdbRating),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            (if (d.diff > 0) "+" else "") + "%.1f".format(d.diff),
                            color = if (d.diff > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            Text(" ", Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun PeriodPicker(period: StatsPeriod, onChange: (StatsPeriod) -> Unit) {
    val now = LocalDate.now()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        FilterChip(selected = period is StatsPeriod.All, onClick = { onChange(StatsPeriod.All) }, label = { Text("הכל") })
        FilterChip(selected = period is StatsPeriod.Year, onClick = { onChange(StatsPeriod.Year(now.year)) }, label = { Text("שנה") })
        FilterChip(selected = period is StatsPeriod.Month, onClick = { onChange(StatsPeriod.Month(YearMonth.now())) }, label = { Text("חודש") })
    }
    val (label, prev, next) = when (period) {
        StatsPeriod.All -> return
        is StatsPeriod.Year -> Triple(
            "${period.year}",
            StatsPeriod.Year(period.year - 1),
            StatsPeriod.Year(period.year + 1).takeIf { period.year < now.year },
        )
        is StatsPeriod.Month -> Triple(
            period.month.format(monthFormat),
            StatsPeriod.Month(period.month.minusMonths(1)),
            StatsPeriod.Month(period.month.plusMonths(1)).takeIf { period.month < YearMonth.now() },
        )
    }
    // RTL: the "previous" arrow sits on the right, pointing right.
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(prev) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "הקודם") }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(140.dp), maxLines = 1)
        IconButton(onClick = { next?.let(onChange) }, enabled = next != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "הבא")
        }
    }
}

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun BigNumber(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PeopleList(people: List<PersonStat>) {
    people.forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${p.titleCount} כותרים · %.1f".format(p.avgRating),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabeledBar(label: String, value: String, fraction: Float) {
    Column {
        Row {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        StatBar(fraction, Modifier.padding(top = 4.dp))
    }
}
