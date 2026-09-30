package io.github.anthodingo.onyxfilter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DisableDurationTest {

    private val paris = ZoneId.of("Europe/Paris")

    private fun at(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(paris)

    @Test
    fun `indefinitely has no duration`() {
        assertNull(DisableDuration.Indefinitely.toSeconds(at("2026-09-30T12:00:00")))
    }

    @Test
    fun `presets match the web dashboard options`() {
        val now = at("2026-09-30T12:00:00")
        assertEquals(
            listOf(30L, 60L, 600L, 3600L, 12 * 3600L),
            DisableDuration.Presets.map { it.toSeconds(now) },
        )
    }

    @Test
    fun `until tomorrow ends at local midnight`() {
        assertEquals(30L, DisableDuration.UntilTomorrow.toSeconds(at("2026-09-30T23:59:30")))
        assertEquals(24 * 3600L, DisableDuration.UntilTomorrow.toSeconds(at("2026-09-30T00:00:00")))
    }

    @Test
    fun `until tomorrow accounts for daylight saving time changes`() {
        // Passage à l'heure d'hiver dans la nuit du 24 au 25 octobre 2026 : la journée du 25 dure 25 h.
        assertEquals(25 * 3600L, DisableDuration.UntilTomorrow.toSeconds(at("2026-10-25T00:00:00")))
    }

    @Test
    fun `custom duration is built from hours and minutes`() {
        assertEquals(DisableDuration.Fixed(2 * 3600 + 15 * 60), DisableDuration.custom(2, 15))
        assertEquals(DisableDuration.Fixed(60), DisableDuration.custom(0, 1))
    }

    @Test
    fun `custom duration rejects zero, negative and too long values`() {
        assertNull(DisableDuration.custom(0, 0))
        assertNull(DisableDuration.custom(-1, 30))
        assertNull(DisableDuration.custom(0, -5))
        assertNull(DisableDuration.custom(30 * 24, 1))
        assertEquals(DisableDuration.Fixed(DisableDuration.MAX_SECONDS), DisableDuration.custom(30 * 24, 0))
    }
}
