package com.matanjerbi.cinematheque.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matanjerbi.cinematheque.ui.navigation.SearchPurpose

/*
 * Placeholder screens: the navigation graph and every callback are wired,
 * each screen gets its real UI + ViewModel in the next steps (see SPEC.md).
 */

@Composable
private fun Placeholder(
    title: String,
    description: String,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actions.forEach { (label, onClick) ->
            OutlinedButton(onClick = onClick) { Text(label) }
        }
    }
}

@Composable
fun DiaryScreen(
    onAdd: () -> Unit,
    onAddPast: () -> Unit,
    onOpenEntry: (titleId: Long, entryId: Long) -> Unit,
    onOpenTitle: (Long) -> Unit,
    onManageTags: () -> Unit,
) = Placeholder(
    "יומן",
    "רשימת הצפיות לפי תאריך, עם סינון לפי תגית, סוג ודירוג. צפיות בלי תאריך בסעיף \"מהעבר\".",
    listOf("+ צפייה חדשה" to onAdd, "סרטים שראיתי בעבר" to onAddPast, "ניהול תגיות" to onManageTags),
)

@Composable
fun WatchlistScreen(
    onAdd: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onMarkWatched: (Long) -> Unit,
) = Placeholder(
    "רשימת צפייה",
    "מה שמחכה לך, עם הערה ומי המליץ. סימון \"צפיתי\" פותח רשומת צפייה.",
    listOf("+ הוסף לרשימה" to onAdd),
)

@Composable
fun CollectionsScreen(onOpenCollection: (Long) -> Unit) = Placeholder(
    "אוספים",
    "רשת אוספים עם קולאז' של 4 פוסטרים.",
)

@Composable
fun StatsScreen(onOpenTitle: (Long) -> Unit) = Placeholder(
    "סטטיסטיקות",
    "בורר תקופה (חודש / שנה / הכל): צפיות ושעות, במאים ושחקנים מובילים, תגיות, ממוצע לפי ז'אנר ועשור, ואיפה חלקת על TMDB.",
)

@Composable
fun SearchScreen(
    purpose: SearchPurpose,
    collectionId: Long,
    onWatched: (titleId: Long) -> Unit,
    onDone: () -> Unit,
) = Placeholder(
    "חיפוש",
    "חיפוש ב־TMDB (debounce של 300ms, מינימום 2 תווים). מטרה: $purpose",
    listOf("חזרה" to onDone),
)

@Composable
fun PastWatchesScreen(onDone: () -> Unit) = Placeholder(
    "סרטים שראיתי בעבר",
    "מצב מהיר: חיפוש → דירוג → תגיות ושנה (אופציונלי) → \"שמור והבא\".",
    listOf("סיום" to onDone),
)

@Composable
fun EntryEditorScreen(titleId: Long, entryId: Long, onDone: () -> Unit) = Placeholder(
    if (entryId == 0L) "צפייה חדשה" else "עריכת צפייה",
    "תאריך, סליידר 1–10, צ'יפים של תגיות עם \"+ תגית חדשה\", וטקסט חופשי.",
    listOf("שמור" to onDone),
)

@Composable
fun TitleDetailScreen(
    titleId: Long,
    onWatchAgain: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onBack: () -> Unit,
) = Placeholder(
    "דף כותר",
    "פוסטר ופרטים, במאי ושחקנים, כל הצפיות שלך, אוספים, רענון מ־TMDB.",
    listOf("צפיתי שוב" to onWatchAgain, "חזרה" to onBack),
)

@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onAddTitles: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onBack: () -> Unit,
) = Placeholder(
    "אוסף",
    "פריטים בסדר ידני (גרירה), עם סימון מה ראית ומה עוד לא.",
    listOf("+ הוסף כותרים" to onAddTitles, "חזרה" to onBack),
)

@Composable
fun TagsScreen(onBack: () -> Unit) = Placeholder(
    "תגיות",
    "שינוי שם, מחיקה ומיזוג תגיות.",
    listOf("חזרה" to onBack),
)
