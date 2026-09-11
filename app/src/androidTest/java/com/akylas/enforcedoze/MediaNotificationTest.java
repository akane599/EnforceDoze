package com.akylas.enforcedoze;

import android.app.Notification;
import android.content.Context;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MediaNotificationTest {
    @Test public void malformedMediaNotificationCannotHideAPlayingSession() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        MediaSession session = new MediaSession(context, "enforcedoze-media-regression");
        try {
            Notification malformed = new Notification();
            malformed.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, new Bundle());
            Notification valid = new Notification();
            valid.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, session.getSessionToken());
            StatusBarNotification[] notifications = {notification(context, 1, malformed), notification(context, 2, valid)};
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1).build());
            TestUi.await("Malformed media notification suppressed the valid playing app", () ->
                    context.getPackageName().equals(NotificationService.findPlayingPackage(context, notifications)));
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0).build());
            TestUi.await("Paused playback must not retain an unnecessary network exemption", () ->
                    NotificationService.findPlayingPackage(context, notifications) == null);
        } finally { session.release(); }
    }

    @SuppressWarnings("deprecation")
    private StatusBarNotification notification(Context context, int id, Notification notification) {
        return new StatusBarNotification(context.getPackageName(), context.getPackageName(), id, null,
                Process.myUid(), Process.myPid(), 0, notification, Process.myUserHandle(), System.currentTimeMillis());
    }
}
