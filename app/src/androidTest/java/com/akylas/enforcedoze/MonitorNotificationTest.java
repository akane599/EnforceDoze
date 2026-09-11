package com.akylas.enforcedoze;

import android.app.Notification;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MonitorNotificationTest {
    @Test public void routineScreenCyclesDoNotRepostTheDefaultNotification() {
        Context context = ApplicationProvider.getApplicationContext();
        MonitorNotification monitor = new MonitorNotification(context, 12345L);
        Notification first = monitor.update("STARTING", "", false);
        assertNotNull(first);
        for (String state : new String[]{"WAITING", "DELAYED", "ENTERING", "ACTIVE", "MAINTENANCE", "RESTORING", "WAITING"})
            assertNull("Normal state change would repost the monitor: " + state,
                    monitor.update(state, "A new session summary", false));
        assertEquals(12345L, first.when);
        assertEquals(Notification.CATEGORY_SERVICE, first.category);
        assertEquals(Notification.PRIORITY_LOW, first.priority);
        assertTrue((first.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
        assertTrue((first.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertTrue((first.flags & Notification.FLAG_LOCAL_ONLY) != 0);
        assertFalse(first.extras.getBoolean(Notification.EXTRA_SHOW_WHEN));
        assertNull(first.sound);
        assertNull(first.vibrate);
    }

    @Test public void accessFailuresStayVisibleAndDetailedUpdatesKeepTheOriginalTimestamp() {
        Context context = ApplicationProvider.getApplicationContext();
        MonitorNotification monitor = new MonitorNotification(context, 56789L);
        monitor.update("WAITING", "", false);
        Notification failure = monitor.update("NEEDS_ACCESS", "", false);
        assertNotNull(failure);
        assertEquals(UiSupport.statusText(context, "NEEDS_ACCESS"),
                failure.extras.getCharSequence(Notification.EXTRA_TEXT));
        assertNull(monitor.update("NEEDS_ACCESS", "", false));
        Notification recovered = monitor.update("WAITING", "", false);
        assertNotNull(recovered);
        Notification active = monitor.update("ACTIVE", "Last session", true);
        assertNotNull(active);
        assertEquals(UiSupport.statusText(context, "ACTIVE"), active.extras.getCharSequence(Notification.EXTRA_TEXT));
        assertTrue(active.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Last session"));
        assertEquals(failure.when, recovered.when);
        assertEquals(failure.when, active.when);
        assertNotNull(monitor.update("WAITING", "New summary", true));
        assertNull(monitor.update("WAITING", "New summary", true));
        monitor.invalidate(); // For example, notification permission was granted after startup.
        Notification granted = monitor.update("WAITING", "New summary", true);
        assertNotNull(granted);
        assertEquals(failure.when, granted.when);
    }
}
