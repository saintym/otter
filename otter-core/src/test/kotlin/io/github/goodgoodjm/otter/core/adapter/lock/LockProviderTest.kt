package io.github.goodgoodjm.otter.core.adapter.lock

import io.github.goodgoodjm.otter.core.adapter.MockLockProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.time.Duration
import kotlin.test.*

class LockProviderTest {

    private lateinit var lockProvider: LockProvider

    @BeforeEach
    fun setUp() {
        lockProvider = MockLockProvider()
    }

    @Test
    fun `should acquire and release lock`() {
        val lockId = "test-lock"
        
        // Acquire lock
        assertTrue(lockProvider.acquireLock(lockId))
        assertTrue(lockProvider.isLocked(lockId))
        
        // Release lock
        assertTrue(lockProvider.releaseLock(lockId))
        assertFalse(lockProvider.isLocked(lockId))
    }

    @Test
    fun `should not acquire already locked lock`() {
        val lockId = "test-lock"
        
        // First acquire should succeed
        assertTrue(lockProvider.acquireLock(lockId))
        
        // Second acquire should fail
        assertFalse(lockProvider.acquireLock(lockId))
    }

    @Test
    fun `should return false when releasing non-existent lock`() {
        assertFalse(lockProvider.releaseLock("non-existent"))
    }

    @Test
    fun `should provide lock information`() {
        val lockId = "test-lock"
        
        assertNull(lockProvider.getLockInfo(lockId))
        
        lockProvider.acquireLock(lockId)
        
        val info = lockProvider.getLockInfo(lockId)
        assertNotNull(info)
        assertEquals(lockId, info.lockId)
        assertEquals("test", info.holder)
        assertNotNull(info.acquiredAt)
    }

    @Test
    fun `should release all locks`() {
        lockProvider.acquireLock("lock1")
        lockProvider.acquireLock("lock2")
        lockProvider.acquireLock("lock3")
        
        assertTrue(lockProvider.isLocked("lock1"))
        assertTrue(lockProvider.isLocked("lock2"))
        assertTrue(lockProvider.isLocked("lock3"))
        
        lockProvider.releaseAllLocks()
        
        assertFalse(lockProvider.isLocked("lock1"))
        assertFalse(lockProvider.isLocked("lock2"))
        assertFalse(lockProvider.isLocked("lock3"))
    }

    @Test
    fun `should handle lock with timeout`() {
        val lockId = "test-lock"
        val timeout = Duration.ofSeconds(5)
        
        assertTrue(lockProvider.acquireLock(lockId, timeout))
        assertTrue(lockProvider.isLocked(lockId))
    }

    @Test
    fun `should report advisory lock support`() {
        // MockLockProvider doesn't support advisory locks
        assertFalse(lockProvider.supportsAdvisoryLocks())
    }

    @Test
    fun `should handle concurrent lock attempts`() {
        val lockId = "concurrent-lock"
        val results = mutableListOf<Boolean>()
        
        val threads = (1..5).map {
            Thread {
                val acquired = lockProvider.acquireLock(lockId)
                synchronized(results) {
                    results.add(acquired)
                }
                if (acquired) {
                    Thread.sleep(10)  // Hold lock briefly
                    lockProvider.releaseLock(lockId)
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Only one thread should have acquired the lock
        assertEquals(1, results.count { it })
        assertEquals(4, results.count { !it })
        
        // Lock should be released at the end
        assertFalse(lockProvider.isLocked(lockId))
    }
}