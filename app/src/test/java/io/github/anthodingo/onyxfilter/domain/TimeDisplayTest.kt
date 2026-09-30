package io.github.anthodingo.onyxfilter.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TimeDisplayTest {

    private val paris = ZoneId.of("Europe/Paris")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(paris)

    @Test
    fun `relative day of the automatic re-enabling`() {
        val now = at("2026-09-30T22:00:00")

        assertEquals(RelativeDay.Today, relativeDay(at("2026-09-30T23:59:00"), now))
        assertEquals(RelativeDay.Tomorrow, relativeDay(at("2026-10-01T00:00:00"), now))
        assertEquals(RelativeDay.Later, relativeDay(at("2026-10-02T09:00:00"), now))
    }

    @Test
    fun `relative day uses the zone of now`() {
        val now = at("2026-09-30T22:00:00")
        // 23:30 UTC le 30 septembre = 01:30 le 1er octobre à Paris.
        val moment = LocalDateTime.parse("2026-09-30T23:30:00").atZone(ZoneOffset.UTC)

        assertEquals(RelativeDay.Tomorrow, relativeDay(moment, now))
    }

    @Test
    fun `duration parts`() {
        assertEquals(DurationParts(0, 0, 0, 0), DurationParts.of(-5))
        assertEquals(DurationParts(0, 0, 0, 42), DurationParts.of(42))
        assertEquals(DurationParts(0, 0, 9, 12), DurationParts.of(552))
        assertEquals(DurationParts(0, 1, 0, 0), DurationParts.of(3_600))
        assertEquals(DurationParts(2, 3, 4, 5), DurationParts.of(2 * 86_400 + 3 * 3_600 + 4 * 60 + 5L))
    }
}
