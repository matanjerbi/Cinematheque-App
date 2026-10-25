package com.matanjerbi.cinematheque.airing

import com.matanjerbi.cinematheque.data.local.entity.FollowedShowEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AirScheduleTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(LocalDateTime.of(y, m, d, h, min), zone)

    // 2026-10-25 is a Sunday.
    private val sunWed21 = AirSchedule(setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY), LocalTime.of(21, 0))

    @Test fun maskRoundTrip() {
        val days = setOf(DayOfWeek.SUNDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        assertEquals(days, AirSchedule.fromMask(AirSchedule.toMask(days)))
        assertEquals(1, AirSchedule.bit(DayOfWeek.SUNDAY))
        assertEquals(64, AirSchedule.bit(DayOfWeek.SATURDAY))
    }

    @Test fun nextIsLaterTodayWhenBeforeAirTime() {
        assertEquals(at(2026, 10, 25, 21), sunWed21.nextAfter(at(2026, 10, 25, 9)))
    }

    @Test fun nextSkipsToFollowingDayAfterAirTime() {
        assertEquals(at(2026, 10, 28, 21), sunWed21.nextAfter(at(2026, 10, 25, 21)))
    }

    @Test fun weeklyWrapsAround() {
        val sat = AirSchedule(setOf(DayOfWeek.SATURDAY), LocalTime.of(4, 0))
        assertEquals(at(2026, 11, 7, 4), sat.nextAfter(at(2026, 10, 31, 4, 1)))
    }

    @Test fun noDaysMeansNoNext() {
        assertNull(AirSchedule(emptySet(), LocalTime.NOON).nextAfter(at(2026, 10, 25, 9)))
    }

    @Test fun countsSlotsInHalfOpenRange() {
        // From Sun 21:00 (excluded) to Sun next week 21:00 (included): Wed + Sun = 2
        assertEquals(2, sunWed21.countBetween(at(2026, 10, 25, 21), at(2026, 11, 1, 21)))
        assertEquals(0, sunWed21.countBetween(at(2026, 10, 25, 22), at(2026, 10, 28, 20, 59)))
        assertEquals(1, sunWed21.countBetween(at(2026, 10, 25, 22), at(2026, 10, 28, 21)))
    }

    @Test fun airedCountGrowsAndCapsAtSeasonLength() {
        val show = FollowedShowEntity(
            titleId = 1,
            airDays = AirSchedule.toMask(setOf(DayOfWeek.WEDNESDAY)),
            airMinuteOfDay = 21 * 60,
            airedCount = 5,
            airedCountAsOf = at(2026, 10, 25, 12).toInstant().toEpochMilli(),
            seasonEpisodeCount = 7,
        )
        assertEquals(5, show.airedAt(at(2026, 10, 28, 20)))
        assertEquals(6, show.airedAt(at(2026, 10, 28, 21)))
        assertEquals(7, show.airedAt(at(2026, 11, 4, 22)))
        assertEquals(7, show.airedAt(at(2026, 12, 30, 22)))
        assertFalse(show.seasonFinishedAt(at(2026, 10, 30, 0)))
        assertTrue(show.seasonFinishedAt(at(2026, 11, 4, 21)))
        assertNull(show.nextAiringAfter(at(2026, 11, 4, 21)))
        assertEquals(at(2026, 10, 28, 21), show.nextAiringAfter(at(2026, 10, 26, 0)))
    }
}
