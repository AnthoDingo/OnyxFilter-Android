package io.github.anthodingo.onyxfilter.data

import io.github.anthodingo.onyxfilter.domain.DisableDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `restores stored session, or logs out when there is none`() = runTest {
        val empty = repository()
        empty.restoreSession()
        assertEquals(AuthState.LoggedOut(), empty.authState.value)

        store.save(session(accessToken = "stored"))
        val restored = repository()
        assertEquals(AuthState.Restoring, restored.authState.value)
        restored.restoreSession()
        assertEquals("stored", (restored.authState.value as AuthState.LoggedIn).session.accessToken)
    }

    @Test
    fun `login stores the session`() = runTest {
        val repository = repository()
        repository.restoreSession()
        server.enqueue(tokens("access-1", "refresh-1"))

        repository.login(baseUrl, "admin", "secret")

        val stored = requireNotNull(store.load())
        assertEquals("access-1", stored.accessToken)
        assertEquals("refresh-1", stored.refreshToken)
        // Expiration anticipée d'une minute.
        assertEquals(clock.millis() + 3_540_000, stored.accessTokenExpiresAtMillis)
        assertTrue(repository.authState.value is AuthState.LoggedIn)
    }

    @Test
    fun `logout clears tokens but keeps the login hint`() = runTest {
        store.save(session())
        val repository = repository()
        repository.restoreSession()

        repository.logout()

        assertEquals(AuthState.LoggedOut(sessionExpired = false), repository.authState.value)
        assertNull(store.load())
        assertEquals(LoginHint(baseUrl, "admin"), repository.loginHint())
    }

    @Test
    fun `expired access token is refreshed before the call`() = runTest {
        store.save(session(accessToken = "old", expiresAt = clock.millis() - 1))
        val repository = repository()
        repository.restoreSession()
        server.enqueue(tokens("new", "refresh-2"))
        server.enqueue(protection("""{"enabled":true}"""))

        val status = repository.getProtection()

        assertTrue(status.enabled)
        assertEquals("/api/auth/refresh", server.takeRequest().path)
        assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
        assertEquals("refresh-2", store.load()!!.refreshToken)
    }

    @Test
    fun `rejected access token is refreshed and the call replayed`() = runTest {
        store.save(session(accessToken = "revoked"))
        val repository = repository()
        repository.restoreSession()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(tokens("new", "refresh-2"))
        server.enqueue(protection("""{"enabled":false,"disabledUntilUtc":null,"remainingSeconds":null}"""))

        val status = repository.disableProtection(DisableDuration.Indefinitely)

        assertTrue(status.isDisabledIndefinitely)
        assertEquals("Bearer revoked", server.takeRequest().getHeader("Authorization"))
        assertEquals("/api/auth/refresh", server.takeRequest().path)
        val replay = server.takeRequest()
        assertEquals("PUT", replay.method)
        assertEquals("Bearer new", replay.getHeader("Authorization"))
    }

    @Test
    fun `rejected refresh token ends the session`() = runTest {
        store.save(session(accessToken = "revoked"))
        val repository = repository()
        repository.restoreSession()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        try {
            repository.getProtection()
            fail("SessionExpired attendue")
        } catch (e: OnyxFilterException.SessionExpired) {
            // attendu
        }

        assertEquals(AuthState.LoggedOut(sessionExpired = true), repository.authState.value)
        assertNull(store.load())
    }

    @Test
    fun `network failure during refresh keeps the session`() = runTest {
        store.save(session(accessToken = "old", expiresAt = clock.millis() - 1))
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
        server.enqueue(protection("""{"enabled":false,"disabledUntilUtc":"2026-09-30T11:10:00Z","remainingSeconds":600}"""))

        val status = repository.disableProtection(DisableDuration.Fixed(600))

        assertTrue(status.isDisabledTemporarily)
        assertEquals(Instant.parse("2026-09-30T10:10:00Z"), status.disabledUntil)
        assertEquals(600, status.remainingSeconds(clock.instant()))
        assertFalse(status.enabled)
    }

    @Test
    fun `until tomorrow is computed from the phone local time`() = runTest {
        clock.timeZone = ZoneId.of("Europe/Paris")
        // 23:30 à Paris (UTC+2 en septembre).
        clock.instant = Instant.parse("2026-09-30T21:30:00Z")
        store.save(session())
        val repository = repository()
        repository.restoreSession()
        server.enqueue(protection("""{"enabled":false,"remainingSeconds":1800}"""))

        repository.disableProtection(DisableDuration.UntilTomorrow)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"durationSeconds\":1800"))
    }

    private fun session(accessToken: String = "access", expiresAt: Long = clock.millis() + 3_600_000) = Session(
        serverUrl = baseUrl,
        username = "admin",
        accessToken = accessToken,
        refreshToken = "refresh",
        accessTokenExpiresAtMillis = expiresAt,
    )

    private fun tokens(access: String, refresh: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"tokenType":"Bearer","accessToken":"$access","expiresIn":3600,"refreshToken":"$refresh"}""")

    private fun protection(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}

private class InMemorySessionStore : SessionStore {
    private var session: Session? = null
    private var hint: LoginHint? = null

    override fun load(): Session? = session

    override fun save(session: Session) {
        this.session = session
        hint = LoginHint(session.serverUrl, session.username)
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
