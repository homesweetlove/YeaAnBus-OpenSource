package kr.yeaan.bus.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kr.yeaan.bus.ui.settings.*
import kr.yeaan.bus.ui.theme.appColors
import org.junit.Assert.*
import org.junit.Test

class SettingsTest {
    @Test fun saverSlowsFrequentPollingButPreservesErrorBackoff() {
        val saver = UserSettings(dataSaver = true)
        assertEquals(30_000L, saver.refreshInterval(3_000L))
        assertEquals(30_000L, saver.refreshInterval(10_000L))
        assertEquals(120_000L, saver.refreshInterval(120_000L))
        assertEquals(3_000L, UserSettings().refreshInterval(3_000L))
    }

    @Test fun themeTextRemainsReadableInEveryPaletteAndMode() {
        fun contrast(a: Color, b: Color): Double {
            val x = a.luminance().toDouble()
            val y = b.luminance().toDouble()
            return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
        }
        Accent.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val c = appColors(accent, dark)
                listOf(c.onSurface to c.surface, c.onBackground to c.background,
                    c.primary to c.surface, c.onPrimary to c.primary,
                    c.onPrimaryContainer to c.primaryContainer, c.onSurfaceVariant to c.surface,
                    c.secondary to c.surface).forEach { (text, background) ->
                    assertTrue("Low text contrast: $accent dark=$dark", contrast(text, background) >= 4.5)
                }
            }
        }
    }
}
