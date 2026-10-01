package io.github.anthodingo.onyxfilter.widget

import io.github.anthodingo.onyxfilter.domain.DisableDuration
import io.github.anthodingo.onyxfilter.domain.ProtectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WidgetModelTest {

    private val enabled = ProtectionStatus(enabled = true, disabledUntil = null)
    private val disabledIndefinitely = ProtectionStatus(enabled = false, disabledUntil = null)
    private val disabledTemporarily = ProtectionStatus(enabled = false, disabledUntil = Instant.parse("2026-09-30T12:00:00Z"))

    @Test
    fun `logged out widget opens the app`() {
        val model = WidgetModel(loggedIn = false, status = enabled)

        assertEquals(WidgetTone.Neutral, model.tone)
        assertNull(model.toggleCommand)
        assertNull(model.primaryCommand)
    }

    @Test
    fun `unknown status is refreshed first`() {
        val model = WidgetModel(loggedIn = true, status = null)

        assertEquals(WidgetTone.Neutral, model.tone)
        assertEquals(WidgetCommand.Refresh, model.toggleCommand)
        assertNull(model.primaryCommand)
    }

    @Test
    fun `toggle inverts the displayed state`() {
        assertEquals(WidgetTone.Enabled, WidgetModel(loggedIn = true, status = enabled).tone)
        assertEquals(
            WidgetCommand.Disable(DisableDuration.Indefinitely),
            WidgetModel(loggedIn = true, status = enabled).toggleCommand,
        )

        assertEquals(WidgetTone.Disabled, WidgetModel(loggedIn = true, status = disabledTemporarily).tone)
        assertEquals(WidgetCommand.Enable, WidgetModel(loggedIn = true, status = disabledTemporarily).toggleCommand)
        assertEquals(WidgetCommand.Enable, WidgetModel(loggedIn = true, status = disabledIndefinitely).toggleCommand)
    }

    @Test
    fun `after an error the toggle and the control buttons still act on the last known state`() {
        val model = WidgetModel(loggedIn = true, status = enabled, hasError = true)

        assertEquals(WidgetCommand.Disable(DisableDuration.Indefinitely), model.toggleCommand)
        assertEquals(WidgetCommand.Disable(DisableDuration.Indefinitely), model.primaryCommand)
    }
}
