package io.github.anthodingo.onyxfilter.domain

import io.github.anthodingo.onyxfilter.data.ProtectionStateDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProtectionStatusTest {

    private val now = Instant.parse("2026-09-30T10:00:00Z")

    @Test
    fun `enabled protection has no end`() {
        val status = ProtectionStatus.fromDto(ProtectionStateDto(enabled = true, remainingSeconds = 42), now)

        assertTrue(status.enabled)
        assertNull(status.disabledUntil)
        assertFalse(status.isDisabledIndefinitely)
        assertFalse(status.isDisabledTemporarily)
    }

    @Test
    fun `disabled without end is indefinite`() {
        val status = ProtectionStatus.fromDto(ProtectionStateDto(enabled = false), now)

        assertTrue(status.isDisabledIndefinitely)
        assertEquals(0, status.remainingSeconds(now))
    }

    @Test
    fun `remaining seconds take precedence over server date`() {
        val dto = ProtectionStateDto(enabled = false, disabledUntil = "2030-01-01T00:00:00+00:00", remainingSeconds = 90)

        val status = ProtectionStatus.fromDto(dto, now)

        assertTrue(status.isDisabledTemporarily)
        assertEquals(now.plusSeconds(90), status.disabledUntil)
    }

    @Test
    fun `server date with offset is used when remaining seconds are missing`() {
        val dto = ProtectionStateDto(enabled = false, disabledUntil = "2026-09-30T12:05:00.1234567+02:00")

        val status = ProtectionStatus.fromDto(dto, now)

        assertEquals(Instant.parse("2026-09-30T10:05:00.1234567Z"), status.disabledUntil)
    }

    @Test
    fun `unreadable server date is treated as no end`() {
        val status = ProtectionStatus.fromDto(ProtectionStateDto(enabled = false, disabledUntil = "demain"), now)

        assertTrue(status.isDisabledIndefinitely)
    }

    @Test
    fun `remaining seconds are rounded up and never negative`() {
        val status = ProtectionStatus(enabled = false, disabledUntil = now.plusMillis(1_500))

        assertEquals(2, status.remainingSeconds(now))
        assertEquals(1, status.remainingSeconds(now.plusMillis(1_000)))
        assertEquals(0, status.remainingSeconds(now.plusSeconds(5)))
    }
}
