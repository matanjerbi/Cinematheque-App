package com.matanjerbi.cinematheque.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class IsraelShowKindTest {
    private val today = LocalDate.of(2026, 11, 23)

    @Test fun premieredLastWeekIsNewSeries() =
        assertEquals(IsraelShowKind.NEW_SERIES, kindOf(today.minusDays(7), 1, null, today))

    @Test fun oldShowAiringSeasonThreeIsNewSeason() =
        assertEquals(IsraelShowKind.NEW_SEASON, kindOf(LocalDate.of(2021, 3, 1), 3, null, today))

    @Test fun firstAirDateInFutureIsComingSoon() =
        assertEquals(IsraelShowKind.COMING_SOON, kindOf(today.plusDays(5), 1, null, today))

    @Test fun nextSeasonPremiereIsNewSeason() =
        assertEquals(
            IsraelShowKind.NEW_SEASON,
            kindOf(LocalDate.of(2023, 1, 1), 2, EpisodeInfo(2, 1, today.plusDays(3), null), today),
        )

    @Test fun parsesDates() {
        assertEquals(LocalDate.of(2026, 11, 12), "2026-11-12".toDateOrNull())
        assertEquals(null, "".toDateOrNull())
    }
}
