package io.github.anthodingo.onyxfilter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StatsFormatTest {

    @Test
    fun `counts and percentages follow the locale`() {
        assertEquals("12,345", StatsFormat.count(12_345, Locale.US))
        assertEquals("4.5%", StatsFormat.percent(0.0454, Locale.US))
        assertEquals("0.0%", StatsFormat.percent(0.0, Locale.US))
        assertEquals("100.0%", StatsFormat.percent(1.7, Locale.US))

        // Français : espace insécable (fine, U+202F, selon les versions) comme séparateur de milliers
        // et avant le signe %. \s ne reconnaît pas ces espaces-là.
        val space = "[ \\u00A0\\u202F]"
        assertTrue(StatsFormat.count(12_345, Locale.FRANCE).matches(Regex("12${space}345")))
        assertTrue(StatsFormat.percent(0.0454, Locale.FRANCE).matches(Regex("4,5$space%")))
    }

    @Test
    fun `columns share the width and grow from the baseline`() {
        val columns = ColumnChart.layout(listOf(10, 20, 5, 0), width = 400f, height = 100f, gap = 2f, maxWidth = 24f, minHeight = 2f)

        assertEquals(3, columns.size)
        // Largeur plafonnée (créneau de 100 px, colonne de 24 px), centrée dans son créneau.
        assertEquals(Column(38f, 50f, 62f, 100f), columns[0])
        assertEquals(Column(138f, 0f, 162f, 100f), columns[1])
        assertEquals(Column(238f, 75f, 262f, 100f), columns[2])
    }

    @Test
    fun `blocked share is stacked at the bottom of each column`() {
        val columns = ColumnChart.layout(
            values = listOf(10, 20, 4),
            blocked = listOf(5, 0, 9),
            width = 300f,
            height = 100f,
            gap = 2f,
            maxWidth = 24f,
            minHeight = 2f,
        )

        assertEquals(75f, columns[0].blockedTop) // Moitié bloquée d'une colonne de 50 px.
        assertEquals(100f, columns[1].blockedTop) // Rien de bloqué.
        assertEquals(columns[2].top, columns[2].blockedTop) // Bloquées plafonnées au total.
    }

    @Test
    fun `narrow slots keep a gap and small values stay visible`() {
        val columns = ColumnChart.layout(listOf(1000, 1), width = 20f, height = 50f, gap = 2f, maxWidth = 24f, minHeight = 2f)

        assertEquals(8f, columns[0].right - columns[0].left)
        assertEquals(48f, columns[1].top)
        assertTrue(columns[1].left - columns[0].right >= 2f)
    }

    @Test
    fun `no columns without traffic`() {
        assertTrue(ColumnChart.layout(listOf(0, 0, 0), 100f, 50f, 2f, 24f, 2f).isEmpty())
        assertTrue(ColumnChart.layout(emptyList(), 100f, 50f, 2f, 24f, 2f).isEmpty())
        assertNull(ColumnChart.peakIndex(listOf(0, 0)))
    }

    @Test
    fun `peak is the most recent highest value`() {
        assertEquals(3, ColumnChart.peakIndex(listOf(5, 9, 1, 9, 2)))
    }
}
