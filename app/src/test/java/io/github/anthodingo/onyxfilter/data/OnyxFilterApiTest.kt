package io.github.anthodingo.onyxfilter.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
    fun `login posts credentials and returns tokens`() = runTest {
        server.enqueue(json(200, TOKENS))

        val tokens = api.login(baseUrl, "admin", "secret")

        assertEquals("access-1", tokens.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
        assertEquals(3600, tokens.expiresIn)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/login", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals("admin", body["username"]!!.jsonPrimitive.content)
        assertEquals("secret", body["password"]!!.jsonPrimitive.content)
    }

    @Test
    fun `login rejected by server reports the server message`() = runTest {
        server.enqueue(problem(401, "Identifiants invalides."))

        val error = expect<OnyxFilterException.InvalidCredentials> { api.login(baseUrl, "admin", "wrong") }

        assertEquals("Identifiants invalides.", error.serverMessage)
    }

    @Test
    fun `missing api reports ApiNotAvailable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Introuvable</html>"))

        val error = expect<OnyxFilterException.ApiNotAvailable> { api.login(baseUrl, "admin", "secret") }

        assertEquals(404, error.code)
    }

    @Test
    fun `html bad request from a server without the api reports ApiNotAvailable`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("The request has an incorrect Content-type."),
        )

        val error = expect<OnyxFilterException.ApiNotAvailable> { api.login(baseUrl, "admin", "secret") }

        assertEquals(400, error.code)
    }

    @Test
    fun `html answer instead of json reports InvalidResponse`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>Box internet</html>"))

        expect<OnyxFilterException.InvalidResponse> { api.login(baseUrl, "admin", "secret") }
    }

    @Test
    fun `unreachable server reports Network`() = runTest {
        server.shutdown()

        expect<OnyxFilterException.Network> { api.login(baseUrl, "admin", "secret") }
    }

    @Test
    fun `refresh rejected reports SessionExpired`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        expect<OnyxFilterException.SessionExpired> { api.refresh(baseUrl, "refresh-1") }

        val request = server.takeRequest()
        assertEquals("/api/auth/refresh", request.path)
        assertEquals("refresh-1", jsonBody(request.body.readUtf8())["refreshToken"]!!.jsonPrimitive.content)
    }

    @Test
    fun `getProtection sends bearer token and parses state`() = runTest {
        server.enqueue(json(200, """{"enabled":false,"disabledUntilUtc":"2026-09-30T11:38:43.1690948Z","remainingSeconds":90}"""))

        val state = api.getProtection(baseUrl, "access-1")

        assertFalse(state.enabled)
        assertEquals(90L, state.remainingSeconds)
        assertEquals("2026-09-30T11:38:43.1690948Z", state.disabledUntilUtc)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/protection", request.path)
        assertEquals("Bearer access-1", request.getHeader("Authorization"))
    }

    @Test
    fun `getProtection with rejected token reports Unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        expect<OnyxFilterException.Unauthorized> { api.getProtection(baseUrl, "expired") }
    }

    @Test
    fun `temporary disable sends the duration`() = runTest {
        server.enqueue(json(200, """{"enabled":false,"disabledUntilUtc":"2026-09-30T12:00:00Z","remainingSeconds":600}"""))

        api.setProtection(baseUrl, "access-1", enabled = false, durationSeconds = 600)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/protection", request.path)
        val body = jsonBody(request.body.readUtf8())
        assertFalse(body["enabled"]!!.jsonPrimitive.boolean)
        assertEquals(600L, body["durationSeconds"]!!.jsonPrimitive.long)
    }

    @Test
    fun `indefinite disable and enable send no duration`() = runTest {
        server.enqueue(json(200, """{"enabled":false,"disabledUntilUtc":null,"remainingSeconds":null}"""))
        server.enqueue(json(200, """{"enabled":true,"disabledUntilUtc":null,"remainingSeconds":null}"""))

        val disabled = api.setProtection(baseUrl, "access-1", enabled = false, durationSeconds = null)
        val enabled = api.setProtection(baseUrl, "access-1", enabled = true, durationSeconds = 600)

        assertFalse(disabled.enabled)
        assertNull(disabled.remainingSeconds)
        assertTrue(enabled.enabled)

        val disableBody = jsonBody(server.takeRequest().body.readUtf8())
        assertFalse(disableBody["enabled"]!!.jsonPrimitive.boolean)
        assertFalse(disableBody.containsKey("durationSeconds"))

        val enableBody = jsonBody(server.takeRequest().body.readUtf8())
        assertTrue(enableBody["enabled"]!!.jsonPrimitive.boolean)
        assertFalse(enableBody.containsKey("durationSeconds"))
    }

    @Test
    fun `validation error reports Http with server message`() = runTest {
        server.enqueue(problem(400, "La durée doit être comprise entre 1 et 2592000 secondes."))

        val error = expect<OnyxFilterException.Http> {
            api.setProtection(baseUrl, "access-1", enabled = false, durationSeconds = 1)
        }

        assertEquals(400, error.code)
        assertEquals("La durée doit être comprise entre 1 et 2592000 secondes.", error.serverMessage)
    }

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(body)

    private fun problem(code: Int, detail: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"https://tools.ietf.org/html/rfc9110","title":"Erreur","status":$code,"detail":"$detail"}""")

    private fun jsonBody(body: String) = Json.parseToJsonElement(body) as JsonObject

    private suspend inline fun <reified T : OnyxFilterException> expect(block: () -> Unit): T {
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
        const val TOKENS = """{"tokenType":"Bearer","accessToken":"access-1","expiresIn":3600,"refreshToken":"refresh-1"}"""
    }
}
