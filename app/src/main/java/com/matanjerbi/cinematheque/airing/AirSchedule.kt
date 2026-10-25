package com.matanjerbi.cinematheque.airing

import com.matanjerbi.cinematheque.data.local.entity.FollowedShowEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Weekly airing slots in the phone's time zone: some weekdays, one time of day. */
data class AirSchedule(val days: Set<DayOfWeek>, val time: LocalTime) {

    /** The first slot strictly after [after], or null when no day is selected. */
    fun nextAfter(after: ZonedDateTime): ZonedDateTime? {
        if (days.isEmpty()) return null
        val start = after.toLocalDate()
        for (offset in 0L..7L) {
            val date = start.plusDays(offset)
            if (date.dayOfWeek !in days) continue
            val slot = ZonedDateTime.of(date, time, after.zone)
            if (slot.isAfter(after)) return slot
        }
        return null
    }

    /** Slots in (from, to]. */
    fun countBetween(from: ZonedDateTime, to: ZonedDateTime): Int {
        if (days.isEmpty() || !to.isAfter(from)) return 0
        var count = 0
        var date = from.toLocalDate()
        val last = to.toLocalDate()
        while (!date.isAfter(last)) {
            if (date.dayOfWeek in days) {
                val slot = ZonedDateTime.of(date, time, from.zone)
                if (slot.isAfter(from) && !slot.isAfter(to)) count++
            }
            date = date.plusDays(1)
        }
        return count
    }

    companion object {
        /** Sunday first, as on an Israeli calendar. */
        val WEEK: List<DayOfWeek> = listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
        )

        fun bit(day: DayOfWeek): Int = 1 shl (day.value % 7) // Sunday = 0

        fun toMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or bit(d) }

        fun fromMask(mask: Int): Set<DayOfWeek> = WEEK.filter { mask and bit(it) != 0 }.toSet()

        fun of(mask: Int, minuteOfDay: Int) = AirSchedule(
            fromMask(mask),
            LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59)),
        )
    }
}

val FollowedShowEntity.schedule: AirSchedule get() = AirSchedule.of(airDays, airMinuteOfDay)

/** Episodes out now: the anchored count plus every slot since, capped at the season length. */
fun FollowedShowEntity.airedAt(now: ZonedDateTime): Int {
    val since = ZonedDateTime.ofInstant(Instant.ofEpochMilli(airedCountAsOf), now.zone)
    val total = airedCount + schedule.countBetween(since, now)
    return seasonEpisodeCount?.let { minOf(total, it) } ?: total
}

fun FollowedShowEntity.seasonFinishedAt(now: ZonedDateTime): Boolean =
    seasonEpisodeCount?.let { airedAt(now) >= it } ?: false

/** When the next episode comes out, or null if the season is done or no day is set. */
fun FollowedShowEntity.nextAiringAfter(now: ZonedDateTime): ZonedDateTime? =
    if (seasonFinishedAt(now)) null else schedule.nextAfter(now)

fun nowHere(): ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
