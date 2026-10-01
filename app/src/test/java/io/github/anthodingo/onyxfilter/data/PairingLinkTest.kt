package io.github.anthodingo.onyxfilter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PairingLinkTest {

    @Test
    fun `parses server and token`() {
        assertEquals(
            PairingLink("https://dns.example/onyx", "onyx_abc123"),
            PairingLink.parse("onyxfilter://pair?server=https%3A%2F%2Fdns.example%2Fonyx&token=onyx_abc123"),
        )
    }

    @Test
    fun `normalizes server address and ignores parameter order and unknown parameters`() {
        assertEquals(
            PairingLink("http://192.168.1.10:5259", "onyx_x"),
            PairingLink.parse("ONYXFILTER://PAIR?v=1&token=onyx_x&server=http%3A%2F%2F192.168.1.10%3A5259%2F"),
        )
    }

    @Test
    fun `rejects incomplete or foreign links`() {
        assertNull(PairingLink.parse(null))
        assertNull(PairingLink.parse(""))
        assertNull(PairingLink.parse("onyxfilter://pair"))
        assertNull(PairingLink.parse("onyxfilter://pair?server=https%3A%2F%2Fdns.example"))
        assertNull(PairingLink.parse("onyxfilter://pair?token=onyx_abc"))
        assertNull(PairingLink.parse("onyxfilter://pair?server=&token=onyx_abc"))
        assertNull(PairingLink.parse("onyxfilter://pair?server=ftp%3A%2F%2Fdns.example&token=onyx_abc"))
        assertNull(PairingLink.parse("onyxfilter://pair?server=https%3A%2F%2Fdns.example&token=abc"))
        assertNull(PairingLink.parse("onyxfilter://pair?server=https%3A%2F%2Fdns.example&token=onyx_"))
        assertNull(PairingLink.parse("onyxfilter://other?server=https%3A%2F%2Fdns.example&token=onyx_abc"))
        assertNull(PairingLink.parse("https://pair?server=https%3A%2F%2Fdns.example&token=onyx_abc"))
        assertNull(PairingLink.parse("onyxfilter://pair?server=%zz&token=onyx_abc"))
        assertNull(PairingLink.parse("pas un lien"))
    }

    @Test
    fun `never prints the token`() {
        assertFalse(PairingLink("https://dns.example", "onyx_secret").toString().contains("onyx_secret"))
    }
}
