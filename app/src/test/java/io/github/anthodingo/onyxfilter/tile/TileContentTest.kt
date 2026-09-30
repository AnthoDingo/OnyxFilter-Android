package io.github.anthodingo.onyxfilter.tile

import io.github.anthodingo.onyxfilter.domain.ProtectionStatus
import io.github.anthodingo.onyxfilter.widget.WidgetModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TileContentTest {

    private val until = Instant.parse("2026-09-30T12:00:00Z")
    private val enabled = ProtectionStatus(enabled = true, disabledUntil = null)
    private val disabledTemporarily = ProtectionStatus(enabled = false, disabledUntil = until)

    @Test
    fun `active only while filtering is on`() {
        assertEquals(TileContent(true, TileSubtitle.Enabled), WidgetModel(loggedIn = true, status = enabled).toTileContent())
        assertEquals(
            TileContent(false, TileSubtitle.DisabledIndefinitely),
            WidgetModel(loggedIn = true, status = ProtectionStatus(enabled = false, disabledUntil = null)).toTileContent(),
        )
        assertEquals(
            TileContent(false, TileSubtitle.DisabledUntil, until),
            WidgetModel(loggedIn = true, status = disabledTemporarily).toTileContent(),
        )
    }

    @Test
    fun `session, progress and errors take precedence over the state`() {
        assertEquals(TileContent(false, TileSubtitle.LoggedOut), WidgetModel(loggedIn = false, status = enabled).toTileContent())
        assertEquals(TileContent(false, TileSubtitle.Loading), WidgetModel(loggedIn = true).toTileContent())
        assertEquals(
            TileContent(true, TileSubtitle.Updating),
            WidgetModel(loggedIn = true, status = enabled, isUpdating = true).toTileContent(),
        )
        // Hors ligne : la tuile garde la couleur du dernier état connu.
        assertEquals(
            TileContent(false, TileSubtitle.Offline),
            WidgetModel(loggedIn = true, status = disabledTemporarily, hasError = true).toTileContent(),
        )
        assertEquals(TileContent(false, TileSubtitle.Offline), WidgetModel(loggedIn = true, hasError = true).toTileContent())
    }
}
