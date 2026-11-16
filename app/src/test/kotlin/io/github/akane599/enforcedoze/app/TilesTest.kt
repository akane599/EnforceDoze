// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import com.google.common.truth.Truth.assertThat
import io.github.akane599.enforcedoze.app.TileRules.PlanClick
import io.github.akane599.enforcedoze.app.TileRules.PlanLook
import io.github.akane599.enforcedoze.app.TileRules.SensorsClick
import io.github.akane599.enforcedoze.doze.Mode
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Parity row 32: the "Doze plan" tile replaces upstream's Doze tile, with "Sensors off" beside it (SPEC 9.2, 11.2). */
class TilesTest {
    @Test
    fun `the plan tile is on only while the engine runs the plan`() {
        assertThat(TileRules.planLook(running = true, Mode.MANAGED, "everyday")).isEqualTo(PlanLook.ON)
        assertThat(TileRules.planLook(running = true, Mode.PAUSED, "everyday")).isEqualTo(PlanLook.OFF)
        assertThat(TileRules.planLook(running = false, Mode.MANAGED, "everyday")).isEqualTo(PlanLook.OFF)
        assertThat(TileRules.planLook(running = true, Mode.MANAGED, null)).isEqualTo(PlanLook.NO_PLAN)
    }

    @Test
    fun `a tap pauses a running plan, resumes a stopped one, and sets one up when there is none`() {
        assertThat(TileRules.planClick(running = true, Mode.MANAGED, "night")).isEqualTo(PlanClick.PAUSE)
        assertThat(TileRules.planClick(running = true, Mode.PAUSED, "night")).isEqualTo(PlanClick.RESUME)
        assertThat(TileRules.planClick(running = false, Mode.PAUSED, "night")).isEqualTo(PlanClick.RESUME)
        assertThat(TileRules.planClick(running = false, Mode.MANAGED, null)).isEqualTo(PlanClick.SET_UP)
    }

    @Test
    fun `sensors off asks first, sensors back on does not, and an unread state opens the app`() {
        assertThat(TileRules.sensorsClick(sensorsOff = false)).isEqualTo(SensorsClick.CONFIRM_OFF)
        assertThat(TileRules.sensorsClick(sensorsOff = true)).isEqualTo(SensorsClick.TURN_ON_SENSORS)
        assertThat(TileRules.sensorsClick(sensorsOff = null)).isEqualTo(SensorsClick.OPEN_APP)
    }

    @Test
    fun `both tiles are declared for the system alone, active and toggleable`() {
        val android = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val services = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).getElementsByTagName("service")
        val tiles = (0 until services.length).map { services.item(it) as Element }
            .filter { it.getAttributeNS(android, "name").endsWith("TileService") }
        assertThat(tiles.map { it.getAttributeNS(android, "name").substringAfterLast('.') })
            .containsExactly("PlanTileService", "SensorsTileService")
        for (tile in tiles) {
            assertThat(tile.getAttributeNS(android, "permission")).isEqualTo("android.permission.BIND_QUICK_SETTINGS_TILE")
            val meta = tile.getElementsByTagName("meta-data")
            val values = (0 until meta.length).map { meta.item(it) as Element }
                .associate { it.getAttributeNS(android, "name") to it.getAttributeNS(android, "value") }
            assertThat(values).containsEntry("android.service.quicksettings.ACTIVE_TILE", "true")
            assertThat(values).containsEntry("android.service.quicksettings.TOGGLEABLE_TILE", "true")
        }
    }
}
