// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.akane599.enforcedoze.R
import io.github.akane599.enforcedoze.app.ui.MainActivity

/**
 * The app's two notifications (SPEC 9.2): the engine's foreground-service line, low importance and silent, and the
 * night report on a channel of its own, so the owner can turn reports off and keep the engine.
 */
internal object Notifications {
    const val ENGINE_ID = 0x0D0E
    private const val REPORT_ID = 0x0D0F
    private const val STUCK_ID = 0x0D10
    private const val ENGINE = "engine"
    private const val REPORTS = "reports"

    /** Where an opened notification or a tile's long press leads, read by [MainActivity]. */
    const val EXTRA_OPEN = "io.github.akane599.enforcedoze.open"
    const val EXTRA_SESSION = "io.github.akane599.enforcedoze.session"
    const val OPEN_REPORT = "report"
    const val OPEN_PLAN = "plan"
    const val OPEN_SENSORS = "sensors"

    fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ENGINE, context.getString(R.string.engine_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.engine_channel_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(REPORTS, context.getString(R.string.report_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.report_channel_description)
            },
        )
    }

    /** One line: the profile in force, or Stock while the plan is paused. */
    fun engine(context: Context, line: String): Notification = Notification.Builder(context, ENGINE)
        .setSmallIcon(R.drawable.icon_monochrome)
        .setContentTitle(line)
        .setContentIntent(open(context, null, null))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(Notification.CATEGORY_SERVICE)
        .build()

    fun updateEngine(context: Context, line: String) {
        if (!allowed(context)) return
        context.getSystemService(NotificationManager::class.java).notify(ENGINE_ID, engine(context, line))
    }

    /** `POST_NOTIFICATIONS` (API 33+); without it nothing is posted, and the foreground service still runs. */
    fun allowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The night report (SPEC 8.5), posted at the first unlock after the night; it opens the report. */
    fun nightReport(context: Context, sessionId: Long, title: String, text: String) {
        if (!allowed(context)) return
        val notification = Notification.Builder(context, REPORTS)
            .setSmallIcon(R.drawable.icon_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open(context, OPEN_REPORT, sessionId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(REPORT_ID, notification)
    }

    /**
     * The engine holds an extra the tier cannot put back (ADR 0038), as after a reboot without root: [text] names the
     * system switch to use. Null cancels it.
     */
    fun extrasStuck(context: Context, text: String?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (text == null) {
            manager.cancel(STUCK_ID)
            return
        }
        if (!allowed(context)) return
        val notification = Notification.Builder(context, REPORTS)
            .setSmallIcon(R.drawable.icon_monochrome)
            .setContentTitle(context.getString(R.string.extras_stuck_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open(context, null, null))
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(STUCK_ID, notification)
    }

    private fun open(context: Context, target: String?, sessionId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        target?.let { intent.putExtra(EXTRA_OPEN, it) }
        sessionId?.let { intent.putExtra(EXTRA_SESSION, it) }
        val code = target?.hashCode() ?: 0
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
