package io.github.anthodingo.onyxfilter.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OnyxFilterApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: OnyxFilterApi
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
        api = OnyxFilterApi(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getProtection sends the api token and parses the state`() = runTest {
        server.enqueue(json(200, """{"enabled":false,"disabledUntil":"2026-09-30T13:53:50.6685652+00:00","remainingSeconds":600}"""))

        val state = api.getProtection(baseUrl, TOKEN)

        assertFalse(state.enabled)
        assertEquals(600L, state.remainingSeconds)
        assertEquals("2026-09-30T13:53:50.6685652+00:00", state.disabledUntil)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/protection", request.path)
        assertEquals("Bearer $TOKEN", request.getHeader("Authorization"))
    }

    @Test
    fun `temporary disable posts the duration`() = runTest {
        server.enqueue(json(200, """{"enabled":false,"disabledUntil":"2026-09-30T12:00:00+00:00","remainingSeconds":600}"""))

        api.disableProtection(baseUrl, TOKEN, durationSeconds = 600)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/protection/disable", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals(600L, jsonBody(request.body.readUtf8())["durationSeconds"]!!.jsonPrimitive.long)
    }

    @Test
    fun `indefinite disable posts no duration, enable posts to its own endpoint`() = runTest {
        server.enqueue(json(200, """{"enabled":false,"disabledUntil":null,"remainingSeconds":null}"""))
        server.enqueue(json(200, """{"enabled":true,"disabledUntil":null,"remainingSeconds":null}"""))

        val disabled = api.disableProtection(baseUrl, TOKEN, durationSeconds = null)
        val enabled = api.enableProtection(baseUrl, TOKEN)

        assertFalse(disabled.enabled)
        assertNull(disabled.remainingSeconds)
        assertTrue(enabled.enabled)

        val disable = server.takeRequest()
        assertEquals("/api/v1/protection/disable", disable.path)
        assertFalse(jsonBody(disable.body.readUtf8()).containsKey("durationSeconds"))

        val enable = server.takeRequest()
        assertEquals("POST", enable.method)
        assertEquals("/api/v1/protection/enable", enable.path)
    }

    @Test
    fun `getStats parses totals, top blocked domains and hourly series`() = runTest {
        server.enqueue(json(200, STATS))

        val stats = api.getStats(baseUrl, TOKEN)

        assertEquals(1234L, stats.totalQueries)
        assertEquals(56L, stats.blockedQueries)
        assertEquals(0.0454, stats.blockedRatio, 1e-9)
        assertEquals("ads.example", stats.topBlockedDomains.first().name)
        assertEquals(listOf(10L, 20L), stats.hourly.map { it.totalQueries })
        assertEquals("/api/v1/stats", server.takeRequest().path)
    }

    @Test
    fun `rejected token reports Unauthorized with the server message`() = runTest {
        server.enqueue(error(401, "unauthorized", "Jeton d'API manquant, invalide ou révoqué."))

        val error = expect<OnyxFilterException.Unauthorized> { api.getProtection(baseUrl, "onyx_revoked") }

        assertEquals("Jeton d'API manquant, invalide ou révoqué.", error.serverMessage)
    }

    @Test
    fun `json 404 from a recent server reports ApiNotAvailable`() = runTest {
        server.enqueue(error(404, "not_found", "Point d'accès inconnu."))

        expect<OnyxFilterException.ApiNotAvailable> { api.getStats(baseUrl, TOKEN) }
    }

    @Test
    fun `html errors from a server without the api report ApiNotAvailable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "text/html").setBody("<html>Introuvable</html>"))
        server.enqueue(MockResponse().setResponseCode(400).setHeader("Content-Type", "text/html").setBody("The request has an incorrect Content-type."))

        assertEquals(404, expect<OnyxFilterException.ApiNotAvailable> { api.getProtection(baseUrl, TOKEN) }.code)
        assertEquals(400, expect<OnyxFilterException.ApiNotAvailable> { api.enableProtection(baseUrl, TOKEN) }.code)
    }

    @Test
    fun `json validation error reports Http with the server message`() = runTest {
        server.enqueue(error(400, "invalid_duration", "durationSeconds doit être compris entre 1 et 31622400."))

        val error = expect<OnyxFilterException.Http> { api.disableProtection(baseUrl, TOKEN, durationSeconds = 1) }

        assertEquals(400, error.code)
        assertEquals("durationSeconds doit être compris entre 1 et 31622400.", error.serverMessage)
    }

    @Test
    fun `html answer instead of json reports InvalidResponse`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>Box internet</html>"))

        expect<OnyxFilterException.InvalidResponse> { api.getProtection(baseUrl, TOKEN) }
    }

    @Test
    fun `unreachable server reports Network`() = runTest {
        server.shutdown()

        expect<OnyxFilterException.Network> { api.getProtection(baseUrl, TOKEN) }
    }

    @Test
    fun `cancelling the coroutine cancels the http call`() = runBlocking {
        // Le serveur ne répond jamais : sans annulation, l'appel attendrait le délai d'OkHttp.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val started = System.nanoTime()
        val result = withTimeoutOrNull(300) { api.getProtection(baseUrl, TOKEN) }
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertNull(result)
        assertTrue("Appel non annulé ($elapsedMillis ms)", elapsedMillis < 3_000)
    }

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(body)

    private fun error(code: Int, error: String, message: String) =
        json(code, """{"error":"$error","message":"$message"}""")

    private fun jsonBody(body: String) = Json.parseToJsonElement(body) as JsonObject

    private inline fun <reified T : OnyxFilterException> expect(block: () -> Unit): T {
        try {
            block()
        } catch (e: OnyxFilterException) {
            if (e is T) return e
            fail("${T::class.simpleName} attendue, ${e::class.simpleName} reçue : $e")
        }
        fail("${T::class.simpleName} attendue, aucune exception levée")
        throw AssertionError()
    }

    companion object {
        const val TOKEN = "onyx_test"

        const val STATS = """{"generatedAt":"2026-09-30T13:43:50.7365507+00:00","windowStart":"2026-09-29T14:00:00+00:00",
            "totalQueries":1234,"blockedQueries":56,"allowedQueries":1178,"blockedRatio":0.0454,"averageProcessingTimeMs":3,
            "topQueriedDomains":[{"name":"example.com","count":300}],"topBlockedDomains":[{"name":"ads.example","count":40}],
            "topClients":[],"upstreams":[{"server":"1.1.1.1","queries":1178,"averageResponseTimeMs":12}],
            "hourly":[{"hourStart":"2026-09-30T12:00:00+00:00","totalQueries":10,"blockedQueries":1},
                      {"hourStart":"2026-09-30T13:00:00+00:00","totalQueries":20,"blockedQueries":2}]}"""
    }
}
