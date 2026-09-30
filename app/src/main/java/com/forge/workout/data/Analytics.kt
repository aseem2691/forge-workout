package com.forge.workout.data

import com.forge.workout.watch.BodyFatReading
import com.forge.workout.watch.WeighIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The one filter that scopes every chart on the progress tab. */
enum class Range(val label: String, val days: Long) {
    Week("7D", 7),
    Month("30D", 30),
    HalfYear("6M", 182),
    Year("1Y", 365);

    /** Buckets stay readable: days for short ranges, weeks then months for long ones. */
    val bucket: Bucket
        get() = when (this) {
            Week, Month -> Bucket.Day
            HalfYear -> Bucket.Week
            Year -> Bucket.Month
        }
}

enum class Bucket { Day, Week, Month }

/** One column of the progress table and one bar of the volume chart. */
data class Period(
    val start: LocalDate,
    val label: String,
    val sessions: Int,
    val volumeKg: Int,
    val minutes: Int,
    val reps: Int,
    val avgHr: Int?,
    /** Last weigh-in that falls in this bucket, if any. */
    val weightKg: Float?,
    /** Last body-fat reading that falls in this bucket, if any. */
    val bodyFatPct: Float?,
)

data class Progress(
    val periods: List<Period>,
    val weighIns: List<WeighIn>,
    val bodyFat: List<BodyFatReading>,
    val sessions: Int,
    val volumeKg: Int,
    val minutes: Int,
    val reps: Int,
    val avgHr: Int?,
    val weightChange: Float?,
    val bodyFatChange: Float?,
    val streakWeeks: Int,
)

private fun bucketStart(date: LocalDate, bucket: Bucket): LocalDate = when (bucket) {
    Bucket.Day -> date
    Bucket.Week -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Bucket.Month -> date.withDayOfMonth(1)
}

private fun label(date: LocalDate, bucket: Bucket): String = when (bucket) {
    Bucket.Day -> "${date.dayOfMonth}/${date.monthValue}"
    Bucket.Week -> "${date.dayOfMonth}/${date.monthValue}"
    Bucket.Month -> date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Rolls session history and weigh-ins into evenly spaced buckets for the range.
 *
 * Empty buckets are kept so gaps in training read as gaps rather than being closed up —
 * a chart that silently compresses missed weeks flatters the user and hides the story.
 */
fun buildProgress(
    history: List<SessionRecord>,
    weighIns: List<WeighIn>,
    bodyFat: List<BodyFatReading> = emptyList(),
    range: Range,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Progress {
    val from = today.minusDays(range.days - 1)
    val bucket = range.bucket

    val inRange = history.filter { LocalDate.ofEpochDay(it.epochDay) >= from }
    val weighInsInRange = weighIns
        .filter { !Instant.ofEpochMilli(it.atMs).atZone(zone).toLocalDate().isBefore(from) }
        .sortedBy { it.atMs }
    val bodyFatInRange = bodyFat
        .filter { !Instant.ofEpochMilli(it.atMs).atZone(zone).toLocalDate().isBefore(from) }
        .sortedBy { it.atMs }

    // Every bucket start between `from` and today, inclusive.
    val starts = buildList {
        var cursor = bucketStart(from, bucket)
        val last = bucketStart(today, bucket)
        while (!cursor.isAfter(last)) {
            add(cursor)
            cursor = when (bucket) {
                Bucket.Day -> cursor.plusDays(1)
                Bucket.Week -> cursor.plusWeeks(1)
                Bucket.Month -> cursor.plusMonths(1)
            }
        }
    }

    val sessionsByBucket = inRange.groupBy { bucketStart(LocalDate.ofEpochDay(it.epochDay), bucket) }
    val weightByBucket = weighInsInRange.groupBy {
        bucketStart(Instant.ofEpochMilli(it.atMs).atZone(zone).toLocalDate(), bucket)
    }
    val fatByBucket = bodyFatInRange.groupBy {
        bucketStart(Instant.ofEpochMilli(it.atMs).atZone(zone).toLocalDate(), bucket)
    }

    val periods = starts.map { start ->
        val rows = sessionsByBucket[start].orEmpty()
        val heartRates = rows.mapNotNull { it.avgHr }
        Period(
            start = start,
            label = label(start, bucket),
            sessions = rows.count { !it.isMobility },
            volumeKg = rows.sumOf { it.volume },
            minutes = rows.sumOf { it.seconds } / 60,
            reps = rows.sumOf { it.reps },
            avgHr = heartRates.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            weightKg = weightByBucket[start]?.lastOrNull()?.kg,
            bodyFatPct = fatByBucket[start]?.lastOrNull()?.percent,
        )
    }

    val allHr = inRange.mapNotNull { it.avgHr }
    return Progress(
        periods = periods,
        weighIns = weighInsInRange,
        bodyFat = bodyFatInRange,
        sessions = inRange.count { !it.isMobility },
        volumeKg = inRange.sumOf { it.volume },
        minutes = inRange.sumOf { it.seconds } / 60,
        reps = inRange.sumOf { it.reps },
        avgHr = allHr.takeIf { it.isNotEmpty() }?.average()?.toInt(),
        weightChange = weighInsInRange.takeIf { it.size >= 2 }
            ?.let { it.last().kg - it.first().kg },
        bodyFatChange = bodyFatInRange.takeIf { it.size >= 2 }
            ?.let { it.last().percent - it.first().percent },
        streakWeeks = streak(history, today),
    )
}

/** Consecutive Monday-start weeks, ending this week, with at least one session. */
private fun streak(history: List<SessionRecord>, today: LocalDate): Int {
    if (history.isEmpty()) return 0
    val trained = history
        .map { LocalDate.ofEpochDay(it.epochDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSet()
    var week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    // This week not being logged yet shouldn't break a streak that is otherwise alive.
    if (week !in trained) week = week.minusWeeks(1)
    var count = 0
    while (week in trained) {
        count++
        week = week.minusWeeks(1)
    }
    return count
}
