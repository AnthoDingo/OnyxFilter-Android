package io.github.anthodingo.onyxfilter.domain

import io.github.anthodingo.onyxfilter.data.ClientLocationDto
import io.github.anthodingo.onyxfilter.data.QueryLogItemDto
import io.github.anthodingo.onyxfilter.data.RankedItemDto
import io.github.anthodingo.onyxfilter.data.StatsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class QueryLogTest {

    @Test
    fun `reasons are read regardless of case, unknown ones are kept apart`() {
        assertEquals(QueryLogReason.Filtered, QueryLogReason.parse("Filtered"))
        assertEquals(QueryLogReason.SafeSearch, QueryLogReason.parse("safesearch"))
        assertEquals(QueryLogReason.Unknown, QueryLogReason.parse("SomethingNew"))
        assertEquals(QueryLogReason.Unknown, QueryLogReason.parse("Unknown"))
        assertEquals(QueryLogReason.Unknown, QueryLogReason.parse(""))
    }

    @Test
    fun `entries keep the client location and drop blank details`() {
        val entry = QueryLogEntry.fromDto(
            QueryLogItemDto(
                time = "2026-09-30T13:43:50+00:00",
                domain = "example.com",
                type = "A",
                client = "203.0.113.7",
                clientLocation = ClientLocationDto(country = "FR", asn = 3215, provider = "Orange"),
                reason = "Resolved",
                reasonDetail = " ",
                upstream = "1.1.1.1",
                processingTimeMs = -3,
            ),
        )

        assertEquals(Instant.parse("2026-09-30T13:43:50Z"), entry.time)
        assertEquals("FR · Orange", entry.clientDetail)
        assertNull(entry.reasonDetail)
        assertEquals("1.1.1.1", entry.upstream)
        assertEquals(0L, entry.processingTimeMs)
    }

    @Test
    fun `unreadable time and missing location give null`() {
        val entry = QueryLogEntry.fromDto(QueryLogItemDto(time = "hier", domain = "example.com"))

        assertNull(entry.time)
        assertNull(entry.clientDetail)
    }

    @Test
    fun `client location shows what is known`() {
        assertEquals("FR", ClientLocationDto(country = "FR").describe())
        assertEquals("Orange", ClientLocationDto(country = null, provider = "Orange").describe())
        assertNull(ClientLocationDto(country = "", provider = "").describe())
    }

    @Test
    fun `rankings keep ten entries at most`() {
        val dto = StatsDto(topQueriedDomains = List(12) { RankedItemDto("d$it.example", 100L - it) })

        val stats = DnsStats.fromDto(dto)

        assertEquals(DnsStats.TOP_SIZE, stats.topQueriedDomains.size)
        assertEquals("d0.example", stats.topQueriedDomains.first().name)
        assertEquals(emptyList<DnsStats.RankedItem>(), stats.topClients)
    }
}
