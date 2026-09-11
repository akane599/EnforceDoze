package com.akylas.enforcedoze;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

/** One foreground notification identity and timestamp for the lifetime of the monitor. */
final class MonitorNotification {
    private static final String CHANNEL = "enforcedoze_service_v2";
    private final Context context;
    private final long startedAt;
    private String lastText;
    private String lastSummary;

    MonitorNotification(Context context, long startedAt) {
        this.context = context;
        this.startedAt = startedAt;
    }

    void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    context.getString(R.string.notification_channel_silent_name), NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setShowBadge(false);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    /** A notification permission grant requires posting again even if content is unchanged. */
    void invalidate() {
        lastText = null;
        lastSummary = null;
    }

    /** Null means the visible notification has not changed, so do not post it again. */
    Notification update(String state, String summary, boolean detailed) {
        boolean attention = "NEEDS_ACCESS".equals(state) || "RECOVERY".equals(state)
                || "ERROR".equals(state) || "OFF".equals(state);
        String text = detailed || attention ? UiSupport.statusText(context, state)
                : context.getString(R.string.monitor_notification_stable);
        String visibleSummary = detailed ? summary : "";
        if (text.equals(lastText) && visibleSummary.equals(lastSummary)) return null;
        lastText = text;
        lastSummary = visibleSummary;

        PendingIntent content = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getBroadcast(context, 10,
                new Intent(context, NotificationActionReceiver.class).setAction("com.akylas.enforcedoze.DISABLE_FORCEDOZE"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_power_settings_new_white_24dp)
                .setContentTitle(context.getString(R.string.app_name)).setContentText(text)
                .setContentIntent(content)
                .setStyle(visibleSummary.isEmpty() ? null
                        : new NotificationCompat.BigTextStyle().bigText(text + "\n" + visibleSummary))
                // Hiding the clock alone does not stop a newly built notification from getting
                // a new ranking timestamp. Preserve the actual 'when' as well.
                .setWhen(startedAt).setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE).setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true).setOngoing(true).setSilent(true).setLocalOnly(true)
                .addAction(0, context.getString(R.string.dashboard_stop), stop).build();
    }
}
