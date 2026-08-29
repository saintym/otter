package io.github.goodgoodjm.otter.core.adapter.lock

import java.time.Duration
import java.time.Instant

/**
 * Provides database locking mechanisms for concurrent migration control
 */
interface LockProvider {
    /**
     * Attempt to acquire a lock
     * @param lockId Unique identifier for the lock
     * @param timeout Maximum time to wait for the lock
     * @return true if lock was acquired, false otherwise
     */
    fun acquireLock(lockId: String, timeout: Duration = Duration.ofSeconds(60)): Boolean

    /**
     * Release a previously acquired lock
     * @param lockId Unique identifier for the lock
     * @return true if lock was released, false if lock was not held
     */
    fun releaseLock(lockId: String): Boolean

    /**
     * Check if a lock is currently held
     * @param lockId Unique identifier for the lock
     * @return true if lock is currently held
     */
    fun isLocked(lockId: String): Boolean

    /**
     * Get information about a lock
     * @param lockId Unique identifier for the lock
     * @return Lock information if available
     */
    fun getLockInfo(lockId: String): LockInfo?

    /**
     * Force release all locks (for cleanup)
     */
    fun releaseAllLocks()

    /**
     * Check if the database supports advisory locks
     */
    fun supportsAdvisoryLocks(): Boolean
}

/**
 * Information about a lock
 */
data class LockInfo(
    val lockId: String,
    val holder: String,
    val acquiredAt: Instant,
    val expiresAt: Instant?,
    val processId: Long? = null,
    val hostname: String? = null
)