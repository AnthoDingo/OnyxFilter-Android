package io.github.anthodingo.onyxfilter.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class ObtainiumTest {

    @Test
    fun `import link is encoded like encodeURIComponent`() {
        // Même lien que le badge du README.
        assertEquals(
            "obtainium://app/%7B%22id%22%3A%22io.github.anthodingo.onyxfilter%22%2C%22url%22%3A%22https%3A%2F%2F" +
                "github.com%2FAnthoDingo%2FOnyxFilter-Android%22%2C%22author%22%3A%22AnthoDingo%22%2C%22name%22%3A" +
                "%22OnyxFilter%22%7D",
            Obtainium.importLink(),
        )
    }

    @Test
    fun `import link carries the app configuration`() {
        val json = URLDecoder.decode(Obtainium.importLink().removePrefix("obtainium://app/"), "UTF-8")
        assertEquals(
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("io.github.anthodingo.onyxfilter"),
                    "url" to JsonPrimitive("https://github.com/AnthoDingo/OnyxFilter-Android"),
                    "author" to JsonPrimitive("AnthoDingo"),
                    "name" to JsonPrimitive("OnyxFilter"),
                ),
            ),
            Json.parseToJsonElement(json),
        )
    }

    @Test
    fun `recognizes Obtainium as installer`() {
        assertTrue(Obtainium.isInstaller("dev.imranr.obtainium"))
        assertTrue(Obtainium.isInstaller("dev.imranr.obtainium.fdroid"))
        assertFalse(Obtainium.isInstaller(null))
        assertFalse(Obtainium.isInstaller("com.android.chrome"))
        assertFalse(Obtainium.isInstaller("com.google.android.packageinstaller"))
        assertFalse(Obtainium.isInstaller("com.android.vending"))
    }
}
