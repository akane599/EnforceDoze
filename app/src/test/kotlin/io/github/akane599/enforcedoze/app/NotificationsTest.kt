// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Parity row 25: upstream's persistent notification, which could be hidden, is the engine's foreground-service line
 * on a low-importance channel of its own. The owner hides it by turning the channel off, and the engine keeps
 * running (SPEC 9.2). Robolectric 4.17 runs up to API 36 (ADR 0028); none of this changed in API 37.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class NotificationsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.channels(context)
    }

    @Test
    fun `the engine and the reports each have a quiet channel, so either can be turned off alone`() {
        val engine = manager.getNotificationChannel("engine")
        val reports = manager.getNotificationChannel("reports")
        assertThat(engine.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(reports.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(engine.canShowBadge()).isFalse()
    }

    @Test
    fun `the engine line is ongoing, alerts once and opens the app`() {
        val notification = Notifications.engine(context, "Everyday")
        assertThat(notification.channelId).isEqualTo("engine")
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_SERVICE)
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Everyday")
        assertThat(notification.contentIntent).isNotNull()
    }

    @Test
    fun `the night report opens its session and goes away when tapped`() {
        Notifications.nightReport(context, sessionId = 42, title = "Dozed 6 h 10 min", text = "Deep idle 81%")
        val posted = shadowOf(manager).allNotifications.single()
        assertThat(posted.channelId).isEqualTo("reports")
        assertThat(posted.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        val opened = shadowOf(posted.contentIntent).savedIntent
        assertThat(opened.getStringExtra(Notifications.EXTRA_OPEN)).isEqualTo(Notifications.OPEN_REPORT)
        assertThat(opened.getLongExtra(Notifications.EXTRA_SESSION, -1)).isEqualTo(42)
    }

    @Test
    fun `without the notification permission nothing is posted`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.updateEngine(context, "Stock")
        Notifications.nightReport(context, sessionId = 1, title = "t", text = "x")
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }
}
