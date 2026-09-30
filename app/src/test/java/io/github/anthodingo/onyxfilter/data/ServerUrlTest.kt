package io.github.anthodingo.onyxfilter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `adds https when no scheme is given`() {
        assertEquals("https://onyx.local:7037", ServerUrl.normalize("onyx.local:7037"))
    }

    @Test
    fun `keeps explicit http scheme and port`() {
        assertEquals("http://192.168.1.10:5259", ServerUrl.normalize("  http://192.168.1.10:5259/ "))
    }

    @Test
    fun `keeps reverse proxy path but drops query, fragment and trailing slashes`() {
        assertEquals(
            "https://maison.example/onyxfilter",
            ServerUrl.normalize("https://maison.example/onyxfilter//?x=1#top"),
        )
    }

    @Test
    fun `lowercases host and drops default port`() {
        assertEquals("https://onyx.example", ServerUrl.normalize("HTTPS://Onyx.Example:443"))
    }

    @Test
    fun `rejects blank and non http addresses`() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("   "))
        assertNull(ServerUrl.normalize("ftp://onyx.local"))
        assertNull(ServerUrl.normalize("https://"))
        assertNull(ServerUrl.normalize("pas une adresse"))
    }

    @Test
    fun `builds api endpoints below the base path`() {
        assertEquals(
            "https://maison.example/onyxfilter/api/protection",
            ServerUrl.endpoint("https://maison.example/onyxfilter", "api/protection").toString(),
        )
        assertEquals(
            "http://10.0.0.2:5259/api/auth/login",
            ServerUrl.endpoint("http://10.0.0.2:5259", "api/auth/login").toString(),
        )
    }

    @Test
    fun `detects cleartext addresses`() {
        assertTrue(ServerUrl.isCleartext("http://10.0.0.2"))
        assertFalse(ServerUrl.isCleartext("https://10.0.0.2"))
    }

    @Test
    fun `display name shows host, non default port and path`() {
        assertEquals("onyx.example", ServerUrl.displayName("https://onyx.example"))
        assertEquals("10.0.0.2:5259", ServerUrl.displayName("http://10.0.0.2:5259"))
        assertEquals("maison.example/onyxfilter", ServerUrl.displayName("https://maison.example/onyxfilter"))
    }
}
