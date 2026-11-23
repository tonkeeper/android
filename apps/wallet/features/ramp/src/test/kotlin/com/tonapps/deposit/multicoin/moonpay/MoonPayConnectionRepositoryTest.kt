package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.moonpay.crypto.encryptMoonPayCredentials
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayConnectionResult
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayConnectionStatus
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrame
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCompletion
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameError
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameEvent
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrames
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme
import com.tonapps.test.silenceLog
import com.tonapps.wallet.api.API
import io.exchangeapi.models.MoonPaySession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class MoonPayConnectionRepositoryTest {

    private val api = mockk<API> {
        every { exchange.moonpay.createMoonPaySession(any()) } returns MoonPaySession(SESSION_TOKEN)
    }

    private var sandbox = false

    private val frames = MoonPayFrames()

    private val timeSource = TestTimeSource()

    private var wallClockMs = NOW_MS

    private val repository = MoonPayConnectionRepository(
        api = api,
        environmentProvider = { sandbox },
        frames = frames,
        timeSource = timeSource,
        wallClockMs = { wallClockMs },
        ioContext = EmptyCoroutineContext,
    )

    @Before
    fun setUp() {
        silenceLog()
        frames.attachHost(Any())
    }

    @Test
    fun `an active connection returns the decrypted credentials`() = runTest {
        val connection = connect()
        val frame = nextFrame() as MoonPayFrame.CheckConnection
        assertEquals(SESSION_TOKEN, frame.sessionToken)

        complete(MoonPayConnectionStatus.Active, credentials("access-1", expiresIn = 10.minutes), frame.publicKeyHex)

        val result = connection.await().getOrThrow()
        assertEquals(MoonPayEnvironment.Production, result.environment)
        assertEquals("access-1", result.credentials.accessToken)
        verify { api.exchange.moonpay.createMoonPaySession(sandbox = false) }
        assertTrue(frames.state.value.entries.isEmpty())
    }

    @Test
    fun `credentials are reused until a minute before they expire`() = runTest {
        connectActive("access-1", expiresIn = 10.minutes)

        timeSource += 8.minutes
        assertEquals("access-1", connect().await().getOrThrow().credentials.accessToken)

        timeSource += 1.minutes + 1.seconds
        val again = connect()
        connectFrame(again, "access-2", expiresIn = 10.minutes)
        assertEquals("access-2", again.await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `invalidated credentials are not reused`() = runTest {
        connectActive("access-1", expiresIn = 10.minutes)

        repository.invalidate()
        val again = connect()
        connectFrame(again, "access-2", expiresIn = 10.minutes)

        assertEquals("access-2", again.await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `refused credentials are dropped, newer ones are kept`() = runTest {
        connectActive("access-1", expiresIn = 10.minutes)
        val refused = connect().await().getOrThrow()

        repository.invalidate(refused)
        val again = connect()
        connectFrame(again, "access-2", expiresIn = 10.minutes)
        assertEquals("access-2", again.await().getOrThrow().credentials.accessToken)

        repository.invalidate(refused)
        assertEquals("access-2", connect().await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `dropping refused credentials keeps another caller's connect running`() = runTest {
        connectActive("access-1", expiresIn = 10.minutes)
        val refused = connect().await().getOrThrow()
        timeSource += 9.minutes + 1.seconds

        val running = connect()
        repository.invalidate(refused)
        val joined = connect()
        connectFrame(running, "access-2", expiresIn = 10.minutes)

        assertTrue(joined.isCompleted)
        assertEquals("access-2", joined.await().getOrThrow().credentials.accessToken)
        assertEquals("access-2", connect().await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `the wall clock is read once, when the credentials arrive`() = runTest {
        connectActive("access-1", expiresIn = 10.minutes)

        wallClockMs += 1.minutes.inWholeMilliseconds * 60

        assertEquals("access-1", connect().await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `credentials with an unreadable expiry are not reused`() = runTest {
        val first = connect()
        complete(MoonPayConnectionStatus.Active, credentials("access-1", expiresAt = "soon"), checkKey())
        assertEquals("access-1", first.await().getOrThrow().credentials.accessToken)

        val second = connect()
        connectFrame(second, "access-2", expiresIn = 10.minutes)
        assertEquals("access-2", second.await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `callers during a connection share it`() = runTest {
        val first = connect()
        val second = connect()

        complete(MoonPayConnectionStatus.Active, credentials("access-1", expiresIn = 10.minutes), checkKey())

        assertEquals(first.await(), second.await())
        verify(exactly = 1) { api.exchange.moonpay.createMoonPaySession(any()) }
    }

    @Test
    fun `callers during a connection share its failure`() = runTest {
        val first = connect()
        val second = connect()
        complete(MoonPayConnectionStatus.ConnectionRequired, credentials("none"), checkKey())
        nextFrame()

        frames.state.value.entries.single().onClose()

        assertFailure<MoonPayConnectionException.Cancelled>(first)
        assertFailure<MoonPayConnectionException.Cancelled>(second)
        assertTrue(frames.state.value.entries.isEmpty())
        verify(exactly = 1) { api.exchange.moonpay.createMoonPaySession(any()) }
    }

    @Test
    fun `a caller that may not sign in is rejected instead of the connect frame`() = runTest {
        val connection = connect(interactive = false)

        complete(MoonPayConnectionStatus.ConnectionRequired, credentials("none"), checkKey())

        assertEquals(
            MoonPayConnectionStatus.ConnectionRequired,
            assertFailure<MoonPayConnectionException.Rejected>(connection).status,
        )
        assertTrue(frames.state.value.entries.isEmpty())
    }

    @Test
    fun `a check that needs a sign-in but gives no client token says no sign-in is offered`() = runTest {
        val silent = connect(interactive = false)
        complete(MoonPayConnectionStatus.ConnectionRequired, null, checkKey())
        val offered = connect(interactive = false)
        complete(MoonPayConnectionStatus.ConnectionRequired, credentials("none"), checkKey())

        val rejected = assertFailure<MoonPayConnectionException.Rejected>(silent)
        assertEquals(MoonPayConnectionStatus.ConnectionRequired, rejected.status)
        assertFalse(rejected.signInOffered)
        assertTrue(assertFailure<MoonPayConnectionException.Rejected>(offered).signInOffered)
        assertTrue(frames.state.value.entries.isEmpty())
    }

    @Test
    fun `a caller that may sign in does not take the rejection of one that may not`() = runTest {
        val silent = connect(interactive = false)
        val interactive = connect(interactive = true)

        complete(MoonPayConnectionStatus.ConnectionRequired, credentials("none"), checkKey())
        assertFailure<MoonPayConnectionException.Rejected>(silent)

        complete(MoonPayConnectionStatus.ConnectionRequired, credentials("none", clientToken = "client-2"), checkKey())
        val frame = nextFrame() as MoonPayFrame.Connect
        assertEquals("client-2", frame.clientToken)
        complete(MoonPayConnectionStatus.Active, credentials("access-1", expiresIn = 10.minutes), frame.publicKeyHex)
        assertEquals("access-1", interactive.await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `a cancelled caller leaves the connection to the next one`() = runTest {
        val first = connect()
        val second = connect()
        val firstKey = checkKey()

        first.cancel()
        val secondKey = checkKey()
        complete(MoonPayConnectionStatus.Active, credentials("access-2", expiresIn = 10.minutes), secondKey)

        assertTrue(firstKey != secondKey)
        assertEquals("access-2", second.await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `credentials of a connection invalidated while it ran are not reused`() = runTest {
        val first = connect()
        val key = checkKey()

        repository.invalidate()
        complete(MoonPayConnectionStatus.Active, credentials("access-1", expiresIn = 10.minutes), key)
        assertEquals("access-1", first.await().getOrThrow().credentials.accessToken)

        val second = connect()
        connectFrame(second, "access-2", expiresIn = 10.minutes)
        assertEquals("access-2", second.await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `a caller after an invalidation does not join the stale connection`() = runTest {
        val stale = connect()
        val staleKey = checkKey()

        repository.invalidate()
        val fresh = connect()
        val state = frames.state.first { it.entries.size == 2 }
        val freshKey = state.entries.map { (it.frame as MoonPayFrame.CheckConnection).publicKeyHex }.single { it != staleKey }
        complete(MoonPayConnectionStatus.Active, credentials("access-1", expiresIn = 10.minutes), staleKey)
        complete(MoonPayConnectionStatus.Active, credentials("access-2", expiresIn = 10.minutes), freshKey)

        assertEquals("access-1", stale.await().getOrThrow().credentials.accessToken)
        assertEquals("access-2", fresh.await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `a caller in another environment does not join the running connection`() = runTest {
        val production = connect()
        val productionKey = checkKey()

        sandbox = true
        val sandboxed = connect()
        val state = frames.state.first { it.entries.size == 2 }
        val sandboxKey = state.entries.map { (it.frame as MoonPayFrame.CheckConnection).publicKeyHex }
            .single { it != productionKey }
        complete(MoonPayConnectionStatus.Active, credentials("access-1", expiresIn = 10.minutes), productionKey)
        complete(MoonPayConnectionStatus.Active, credentials("access-2", expiresIn = 10.minutes), sandboxKey)

        assertEquals(MoonPayEnvironment.Production, production.await().getOrThrow().environment)
        assertEquals(MoonPayEnvironment.Sandbox, sandboxed.await().getOrThrow().environment)
    }

    @Test
    fun `switching to the sandbox connects again`() = runTest {
        connectActive("access-1", expiresIn = 10.minutes)

        sandbox = true
        val again = connect()
        connectFrame(again, "access-2", expiresIn = 10.minutes)

        assertEquals(MoonPayEnvironment.Sandbox, again.await().getOrThrow().environment)
        verify { api.exchange.moonpay.createMoonPaySession(sandbox = true) }
    }

    @Test
    fun `a required connection opens the connect frame with the check's client token`() = runTest {
        val connection = connect(theme = MoonPayTheme.Dark)
        complete(MoonPayConnectionStatus.ConnectionRequired, credentials("none", clientToken = "client-1"), checkKey())

        val frame = nextFrame() as MoonPayFrame.Connect
        assertEquals("client-1", frame.clientToken)
        assertEquals(MoonPayTheme.Dark, frame.theme)
        complete(MoonPayConnectionStatus.Active, credentials("access-1", expiresIn = 10.minutes), frame.publicKeyHex)

        assertEquals("access-1", connection.await().getOrThrow().credentials.accessToken)
    }

    @Test
    fun `closing the connect frame cancels the connection`() = runTest {
        val connection = connect()
        complete(MoonPayConnectionStatus.TermsAcceptanceRequired, credentials("none"), checkKey())
        nextFrame()

        frames.state.value.entries.single().onClose()

        assertFailure<MoonPayConnectionException.Cancelled>(connection)
    }

    @Test
    fun `a connect frame that ends inactive is rejected`() = runTest {
        val connection = connect()
        complete(MoonPayConnectionStatus.ConnectionRequired, credentials("none"), checkKey())
        val frame = nextFrame() as MoonPayFrame.Connect

        complete(MoonPayConnectionStatus.Pending, null, frame.publicKeyHex)

        assertEquals(
            MoonPayConnectionStatus.Pending,
            assertFailure<MoonPayConnectionException.Rejected>(connection).status,
        )
    }

    @Test
    fun `a pending customer is rejected`() = runTest {
        val connection = connect()

        complete(MoonPayConnectionStatus.Pending, null, checkKey())

        assertEquals(
            MoonPayConnectionStatus.Pending,
            assertFailure<MoonPayConnectionException.Rejected>(connection).status,
        )
    }

    @Test
    fun `a frame error fails the connection`() = runTest {
        val connection = connect()
        nextFrame()

        frames.state.value.entries.single().onEvent(MoonPayFrameEvent.Error(MoonPayFrameError("code", "message")))

        assertEquals("code", assertFailure<MoonPayConnectionException.Remote>(connection).error.code)
    }

    @Test
    fun `a silent check times out and leaves no frame`() = runTest {
        val connection = connect()
        nextFrame()

        advanceTimeBy(20.seconds + 1.seconds)

        assertFailure<MoonPayConnectionException.TimedOut>(connection)
        assertTrue(frames.state.value.entries.isEmpty())
    }

    @Test
    fun `a failed session is reported and retried by the next call`() = runTest {
        every { api.exchange.moonpay.createMoonPaySession(any()) } throws IOException("offline")

        assertFailure<MoonPayConnectionException.Session>(connect())
        assertTrue(frames.state.value.entries.isEmpty())

        every { api.exchange.moonpay.createMoonPaySession(any()) } returns MoonPaySession(SESSION_TOKEN)
        connectActive("access-1", expiresIn = 10.minutes)
    }

    private fun TestScope.connect(
        theme: MoonPayTheme? = null,
        interactive: Boolean = true,
    ): Deferred<Result<MoonPayConnection>> {
        return async(UnconfinedTestDispatcher(testScheduler)) {
            repository.ensureConnected(theme, interactive)
        }
    }

    private suspend fun TestScope.connectActive(accessToken: String, expiresIn: Duration) {
        val connection = connect()
        connectFrame(connection, accessToken, expiresIn)
        assertEquals(accessToken, connection.await().getOrThrow().credentials.accessToken)
    }

    private suspend fun TestScope.connectFrame(
        connection: Deferred<Result<MoonPayConnection>>,
        accessToken: String,
        expiresIn: Duration,
    ) {
        complete(MoonPayConnectionStatus.Active, credentials(accessToken, expiresIn = expiresIn), checkKey())
        runCurrent()
        assertTrue(connection.isCompleted)
    }

    private suspend fun nextFrame(): MoonPayFrame {
        return frames.state.first { it.entries.isNotEmpty() }.entries.single().frame
    }

    private suspend fun checkKey(): String = (nextFrame() as MoonPayFrame.CheckConnection).publicKeyHex

    private suspend fun complete(status: MoonPayConnectionStatus, plaintext: String?, publicKeyHex: String) {
        val state = frames.state.first { state -> state.entries.any { it.frame.publicKey() == publicKeyHex } }
        val entry = state.entries.single { it.frame.publicKey() == publicKeyHex }
        val result = MoonPayConnectionResult(
            status = status,
            encryptedCredentials = plaintext?.let { encryptMoonPayCredentials(it, to = publicKeyHex) },
        )
        entry.onEvent(MoonPayFrameEvent.Complete(MoonPayFrameCompletion.Connection(result)))
    }

    private fun MoonPayFrame.publicKey(): String? {
        return when (this) {
            is MoonPayFrame.CheckConnection -> publicKeyHex
            is MoonPayFrame.Connect -> publicKeyHex
            is MoonPayFrame.Widget,
            is MoonPayFrame.Buy,
            is MoonPayFrame.BuyButton,
            is MoonPayFrame.AddCard,
            is MoonPayFrame.Challenge,
            -> null
        }
    }

    private fun credentials(
        accessToken: String,
        clientToken: String = "client",
        expiresIn: Duration = 10.minutes,
        expiresAt: String = Instant.ofEpochMilli(wallClockMs + expiresIn.inWholeMilliseconds).toString(),
    ): String {
        return """{"accessToken":"$accessToken","clientToken":"$clientToken","expiresAt":"$expiresAt"}"""
    }

    private suspend inline fun <reified T : Throwable> assertFailure(deferred: Deferred<Result<*>>): T {
        val failure = deferred.await().exceptionOrNull()
        assertTrue("expected ${T::class.simpleName}, got $failure", failure is T)
        return failure as T
    }

    private companion object {
        const val SESSION_TOKEN = "session-1"
        const val NOW_MS = 1_800_000_000_000L
    }
}
