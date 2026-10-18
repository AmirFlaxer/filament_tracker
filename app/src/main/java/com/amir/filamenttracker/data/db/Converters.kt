package com.amir.filamenttracker.data.db

import androidx.room.TypeConverter
import com.amir.filamenttracker.domain.model.SpoolStatus
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    // Stored by name, so SQL can compare against literals like 'IN_USE'.
    @TypeConverter fun statusToString(value: SpoolStatus?): String? = value?.name
    @TypeConverter fun stringToStatus(value: String?): SpoolStatus? = value?.let(SpoolStatus::valueOf)
}
