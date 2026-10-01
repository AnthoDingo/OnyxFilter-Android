package io.github.anthodingo.onyxfilter.data

import io.github.anthodingo.onyxfilter.domain.DisableDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class OnyxFilterRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private val store = InMemorySessionStore()
    private val clock = MutableClock(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository() = OnyxFilterRepository(
        api = OnyxFilterApi(OkHttpClient(), Dispatchers.Unconfined),
        sessionStore = store,
        clock = clock,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun session(token: String = "onyx_stored") = Session(serverUrl = baseUrl, apiToken = token)

    @Test
    fun `restores stored session, or logs out when there is none`() = runTest {
        val empty = repository()
        empty.restoreSession()
        assertEquals(AuthState.LoggedOut(), empty.authState.value)

        store.save(session("onyx_stored"))
        val restored = repository()
        assertEquals(AuthState.Restoring, restored.authState.value)
        restored.restoreSession()
        assertEquals("onyx_stored", (restored.authState.value as AuthState.LoggedIn).session.apiToken)
    }

    @Test
    fun `login checks the token, stores the session and publishes the state`() = runTest {
        val repository = repository()
        repository.restoreSession()
        server.enqueue(json("""{"enabled":true,"disabledUntil":null,"remainingSeconds":null}"""))

        repository.login(baseUrl, "onyx_new")

        assertEquals("Bearer onyx_new", server.takeRequest().getHeader("Authorization"))
        assertEquals(Session(baseUrl, "onyx_new"), store.load())
        assertTrue(repository.authState.value is AuthState.LoggedIn)
        assertEquals(true, repository.protectionStatus.value?.enabled)
    }

    @Test
    fun `rejected token at login keeps the user logged out`() = runTest {
        val repository = repository()
        repository.restoreSession()
        server.enqueue(error401())

        try {
            repository.login(baseUrl, "onyx_wrong")
            fail("Unauthorized attendue")
        } catch (e: OnyxFilterException.Unauthorized) {
            // attendu
        }

        assertEquals(AuthState.LoggedOut(), repository.authState.value)
        assertNull(store.load())
    }

    @Test
    fun `logout clears the token but keeps the server address`() = runTest {
        store.save(session())
        val repository = repository()
        repository.restoreSession()

        repository.logout()

        assertEquals(AuthState.LoggedOut(tokenRejected = false), repository.authState.value)
        assertNull(store.load())
        assertEquals(LoginHint(baseUrl), repository.loginHint())
    }

    @Test
    fun `token revoked during the session ends it`() = runTest {
        store.save(session())
        val repository = repository()
        repository.restoreSession()
        server.enqueue(error401())

        try {
            repository.getProtection()
            fail("SessionEnded attendue")
        } catch (e: OnyxFilterException.SessionEnded) {
            // attendu
        }

        assertEquals(AuthState.LoggedOut(tokenRejected = true), repository.authState.value)
        assertNull(store.load())
    }

    @Test
    fun `network failure keeps the session`() = runTest {
        store.save(session())
        val repository = repository()
        repository.restoreSession()
        server.shutdown()

        try {
            repository.getProtection()
            fail("Network attendue")
        } catch (e: OnyxFilterException.Network) {
            // attendu
        }

        assertTrue(repository.authState.value is AuthState.LoggedIn)
        assertNotNull(store.load())
    }

    @Test
    fun `temporary disable uses the remaining time from the server`() = runTest {
        store.save(session())
        val repository = repository()
        repository.restoreSession()
        // Horloge du serveur en avance d'une heure sur celle du téléphone : seule la durée restante compte.
        server.enqueue(json("""{"enabled":false,"disabledUntil":"2026-09-30T11:10:00+00:00","remainingSeconds":600}"""))

        val status = repository.disableProtection(DisableDuration.Fixed(600))

        assertTrue(status.isDisabledTemporarily)
        assertEquals(Instant.parse("2026-09-30T10:10:00Z"), status.disabledUntil)
        assertEquals(status, repository.protectionStatus.value)
    }

    @Test
    fun `until tomorrow is computed from the phone local time`() = runTest {
        clock.timeZone = ZoneId.of("Europe/Paris")
        // 23:30 à Paris (UTC+2 en septembre).
        clock.instant = Instant.parse("2026-09-30T21:30:00Z")
        store.save(session())
        val repository = repository()
        repository.restoreSession()
        server.enqueue(json("""{"enabled":false,"remainingSeconds":1800}"""))

        repository.disableProtection(DisableDuration.UntilTomorrow)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"durationSeconds\":1800"))
    }

    @Test
    fun `stats are published and cleared on logout`() = runTest {
        store.save(session())
        val repository = repository()
        repository.restoreSession()
        server.enqueue(json(OnyxFilterApiTest.STATS))

        val stats = repository.getStats()

        assertEquals(1234L, stats.totalQueries)
        assertEquals("ads.example", stats.topBlockedDomain?.name)
        assertEquals(listOf(10L, 20L), stats.hourlyQueries)
        assertEquals(listOf(1L, 2L), stats.hourlyBlocked)
        assertEquals(Instant.parse("2026-09-30T13:00:00Z"), stats.lastHourStart)
        assertEquals(stats, repository.stats.value)

        repository.logout()
        assertNull(repository.stats.value)
        assertNull(repository.protectionStatus.value)
    }

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun error401() = MockResponse()
        .setResponseCode(401)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"error":"unauthorized","message":"Jeton d'API manquant, invalide ou révoqué."}""")
}

private class InMemorySessionStore : SessionStore {
    private var session: Session? = null
    private var hint: LoginHint? = null

    override fun load(): Session? = session

    override fun save(session: Session) {
        this.session = session
        hint = LoginHint(session.serverUrl)
    }

    override fun clear() {
        session = null
    }

    override fun loginHint(): LoginHint? = hint
}

private class MutableClock(var instant: Instant, var timeZone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = timeZone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

    override fun instant(): Instant = instant
}
