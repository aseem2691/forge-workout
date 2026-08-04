package com.forge.workout.watch

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** What the watch recorded for a session, once the Zepp app has pushed it to Health Connect. */
data class WatchSummary(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val avgHr: Int?,
    val maxHr: Int?,
    val calories: Int?,
)

/** A single scale reading. */
data class WeighIn(val atMs: Long, val kg: Float)

/** A body-fat percentage reading from the scale. */
data class BodyFatReading(val atMs: Long, val percent: Float)

enum class HcStatus { Unavailable, UpdateRequired, NeedsPermission, Ready }

/**
 * Reads watch workouts out of Health Connect and publishes Forge's own sessions into it.
 *
 * The Zepp app writes to Health Connect but never reads from it, so this is strictly a
 * one-way pull from the watch — Forge sessions we write show up in Health Connect and other
 * apps, but will not appear back inside Zepp.
 */
class HealthConnectRepo(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    /** Every scale reading in the window, oldest first. */
    suspend fun weights(from: Instant, to: Instant): List<WeighIn> {
        val hc = client ?: return emptyList()
        if (!hasPermissions()) return emptyList()
        return runCatching {
            hc.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records
                .map { WeighIn(it.time.toEpochMilli(), it.weight.inKilograms.toFloat()) }
                .sortedBy { it.atMs }
        }.getOrDefault(emptyList())
    }

    /** Most recent scale reading, looking back far enough to survive a quiet spell. */
    suspend fun latestWeight(lookbackDays: Long = 400): WeighIn? =
        weights(Instant.now().minusSeconds(lookbackDays * 86_400), Instant.now()).lastOrNull()

    /** Body-fat percentage readings from the same scale, oldest first. */
    suspend fun bodyFat(from: Instant, to: Instant): List<BodyFatReading> {
        val hc = client ?: return emptyList()
        if (!hasPermissions()) return emptyList()
        return runCatching {
            hc.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records
                .map { BodyFatReading(it.time.toEpochMilli(), it.percentage.value.toFloat()) }
                .sortedBy { it.atMs }
        }.getOrDefault(emptyList())
    }

    private val sdkStatus: Int get() = HealthConnectClient.getSdkStatus(context)

    val installed: Boolean get() = sdkStatus == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient? get() = runCatching {
        if (installed) HealthConnectClient.getOrCreate(context) else null
    }.getOrNull()

    suspend fun status(): HcStatus = when (sdkStatus) {
        HealthConnectClient.SDK_UNAVAILABLE -> HcStatus.Unavailable
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcStatus.UpdateRequired
        else -> if (hasPermissions()) HcStatus.Ready else HcStatus.NeedsPermission
    }

    suspend fun hasPermissions(): Boolean = runCatching {
        val granted = client?.permissionController?.getGrantedPermissions() ?: return false
        granted.containsAll(permissions)
    }.getOrDefault(false)

    /**
     * Finds the watch workout that best overlaps a Forge session and summarises it.
     * The window is padded because watch and phone clocks — and when you actually hit start —
     * rarely line up exactly.
     */
    suspend fun summaryFor(startMs: Long, endMs: Long, padMinutes: Long = 20): WatchSummary? {
        val hc = client ?: return null
        if (!hasPermissions()) return null
        val from = Instant.ofEpochMilli(startMs).minusSeconds(padMinutes * 60)
        val to = Instant.ofEpochMilli(endMs).plusSeconds(padMinutes * 60)

        val sessions = runCatching {
            hc.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records
        }.getOrNull().orEmpty()
            // Forge publishes its own session into the same window — never report that back
            // to the user as if the watch had recorded it.
            .filter { it.metadata.dataOrigin.packageName != context.packageName }

        // Best = largest genuine overlap with the Forge session.
        val best = sessions
            .filter {
                min(it.endTime.toEpochMilli(), endMs) - max(it.startTime.toEpochMilli(), startMs) > 0
            }
            .maxByOrNull {
                min(it.endTime.toEpochMilli(), endMs) - max(it.startTime.toEpochMilli(), startMs)
            }

        // With no matching workout, fall back to whatever the watch logged across the session —
        // it records heart rate continuously even when you never start a workout on it.
        val window = if (best != null) {
            TimeRangeFilter.between(best.startTime, best.endTime)
        } else {
            TimeRangeFilter.between(Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs))
        }
        val aggregate: AggregationResult? = runCatching {
            hc.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MAX,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    ),
                    timeRangeFilter = window,
                ),
            )
        }.getOrNull()

        val avgHr = aggregate?.get(HeartRateRecord.BPM_AVG)?.toInt()
        val maxHr = aggregate?.get(HeartRateRecord.BPM_MAX)?.toInt()
        val calories = aggregate?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.toInt()
        if (best == null && avgHr == null && maxHr == null && calories == null) return null

        return WatchSummary(
            title = best?.title
                ?: best?.exerciseType?.let { exerciseName(it) }
                ?: if (best != null) "Watch workout" else "Recorded during session",
            startMs = best?.startTime?.toEpochMilli() ?: startMs,
            endMs = best?.endTime?.toEpochMilli() ?: endMs,
            avgHr = avgHr,
            maxHr = maxHr,
            calories = calories,
        )
    }

    /** Publishes a finished Forge session so it shows up in Health Connect and downstream apps. */
    suspend fun publish(title: String, startMs: Long, endMs: Long, hiit: Boolean): Boolean {
        val hc = client ?: return false
        if (!hasPermissions() || endMs <= startMs) return false
        val zone = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(startMs))
        return runCatching {
            hc.insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        startTime = Instant.ofEpochMilli(startMs),
                        startZoneOffset = zone,
                        endTime = Instant.ofEpochMilli(endMs),
                        endZoneOffset = zone,
                        exerciseType = if (hiit) {
                            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
                        } else {
                            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
                        },
                        title = title,
                        // Forge times the session itself on the phone.
                        metadata = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE)),
                    ),
                ),
            )
            true
        }.getOrDefault(false)
    }

    private fun exerciseName(type: Int): String? = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Calisthenics"
        else -> null
    }
}
