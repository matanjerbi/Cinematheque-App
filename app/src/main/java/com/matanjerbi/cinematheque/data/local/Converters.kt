package com.matanjerbi.cinematheque.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/** Dates are stored as epoch days, so range queries in stats stay plain integer comparisons. */
class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}
