// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.akylas.enforcedoze.R
import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.design.Palette
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The window behind the Compose UI is the palette's ground in both themes, so a cold start shows no other colour. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ThemeTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `the light window is the light ground`() {
        assertThat(app.getColor(R.color.window_ground)).isEqualTo(Palette.Light.ground.toArgb())
    }

    @Test
    @Config(qualifiers = "night")
    fun `the dark window is the dark ground`() {
        assertThat(app.getColor(R.color.window_ground)).isEqualTo(Palette.Dark.ground.toArgb())
    }
}
