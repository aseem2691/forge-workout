package com.forge.workout.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.forge.workout.R

private const val CHANNEL_ID = "forge_rest"
private const val NOTIFICATION_ID = 1001

/**
 * Audible and heads-up cues so a set can be run with the phone face-down.
 *
 * Tones use a short-lived [ToneGenerator] rather than bundled audio assets — the cue only has to
 * cut through, and this keeps the APK free of media it would otherwise carry for three beeps.
 */
class Alerts(private val context: Context) {

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rest timer",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Tells you when a rest period is over"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Fired when rest ends while the app is not on screen. */
    fun restFinished(nextExercise: String) {
        if (!canNotify()) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = launch?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_forge)
            .setContentTitle("Rest done")
            .setContentText("Up next: $nextExercise")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun clear() {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    /** A short tick for the 3-2-1 countdown. */
    fun tick() = tone(ToneGenerator.TONE_PROP_BEEP, 120)

    /** A brighter double for "go" — rest over, or a work interval finished. */
    fun go() = tone(ToneGenerator.TONE_PROP_BEEP2, 260)

    private fun tone(type: Int, durationMs: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            generator.startTone(type, durationMs)
            // ToneGenerator holds an AudioTrack; release it once the tone has played.
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ runCatching { generator.release() } }, (durationMs + 250).toLong())
        }
    }
}
