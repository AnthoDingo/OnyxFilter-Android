package io.github.anthodingo.onyxfilter.widget

import io.github.anthodingo.onyxfilter.domain.DisableDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetCommandTest {

    @Test
    fun `commands survive encoding`() {
        val commands = listOf(
            WidgetCommand.Refresh,
            WidgetCommand.Enable,
            WidgetCommand.RefreshStats,
            WidgetCommand.Disable(DisableDuration.Indefinitely),
            WidgetCommand.Disable(DisableDuration.UntilTomorrow),
            WidgetCommand.Disable(DisableDuration.Fixed(600)),
            WidgetCommand.Disable(DisableDuration.Fixed(DisableDuration.MAX_SECONDS)),
        ) + WidgetCommand.QuickDisable

        commands.forEach { command ->
            assertEquals(command, WidgetCommand.decode(command.encode()))
        }
    }

    @Test
    fun `encoded form`() {
        assertEquals("refresh", WidgetCommand.Refresh.encode())
        assertEquals("disable/indefinitely", WidgetCommand.Disable(DisableDuration.Indefinitely).encode())
        assertEquals("disable/3600", WidgetCommand.Disable(DisableDuration.Fixed(3600)).encode())
    }

    @Test
    fun `invalid values are rejected`() {
        listOf(null, "", "disable", "disable/", "disable/0", "disable/-5", "disable/abc", "disable/2592001", "reboot")
            .forEach { assertNull(it, WidgetCommand.decode(it)) }
    }

    @Test
    fun `quick durations are 10 minutes, 1 hour and until tomorrow`() {
        assertEquals(
            listOf(DisableDuration.Fixed(600), DisableDuration.Fixed(3600), DisableDuration.UntilTomorrow),
            WidgetCommand.QuickDisable.map { it.duration },
        )
    }
}
