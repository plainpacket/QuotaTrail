package app.quotatrail.sync

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountOperationGateTest {
    @Test
    fun `same account serializes while another provider can proceed`() = runTest {
        val gate = AccountOperationGate()
        val release = CompletableDeferred<Unit>()
        val first = async { gate.withAccount(ProviderId("claude"), LocalAccountId("one")) { release.await() } }
        runCurrent()
        val second = async { gate.withAccount(ProviderId("claude"), LocalAccountId("one")) { "second" } }
        val other = async { gate.withAccount(ProviderId("codex"), LocalAccountId("one")) { "other" } }
        runCurrent()
        assertFalse(second.isCompleted)
        assertEquals("other", other.await())
        release.complete(Unit)
        first.await()
        assertEquals("second", second.await())
    }

    @Test
    fun `cancelled waiter and failed owner do not strand the lock`() = runTest {
        val gate = AccountOperationGate()
        val release = CompletableDeferred<Unit>()
        val first = async { gate.withAccount(ProviderId("claude"), LocalAccountId("one")) { release.await() } }
        runCurrent()
        val waiter = async { gate.withAccount(ProviderId("claude"), LocalAccountId("one")) { fail("Cancelled waiter ran") } }
        runCurrent()
        waiter.cancelAndJoin()
        release.complete(Unit)
        first.await()
        assertTrue(runCatching {
            gate.withAccount(ProviderId("claude"), LocalAccountId("one")) { error("synthetic failure") }
        }.isFailure)
        assertEquals("recovered", gate.withAccount(ProviderId("claude"), LocalAccountId("one")) { "recovered" })
    }
}
