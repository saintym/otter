package io.github.goodgoodjm.otter.core.adapter.lock

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Thread-Safe In-Memory LockProvider
 *
 * 동시성 문제를 해결한 LockProvider 구현
 */
class ConcurrentLockProvider : LockProvider {
    private val locks = ConcurrentHashMap<String, LockInfo>()
    private val mutexMap = ConcurrentHashMap<String, ReentrantLock>()

    /**
     * Thread-safe lock acquisition
     *
     * Double-checked locking 패턴 사용
     */
    override fun acquireLock(lockId: String, timeout: Duration): Boolean {
        val mutex = mutexMap.computeIfAbsent(lockId) { ReentrantLock() }

        try {
            // 타임아웃 동안 락 획득 시도
            if (!mutex.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return false
            }

            try {
                // 이미 락이 있는지 확인
                if (locks.containsKey(lockId)) {
                    return false
                }

                // 락 정보 저장
                locks[lockId] = LockInfo(
                    lockId = lockId,
                    holder = Thread.currentThread().name,
                    acquiredAt = Instant.now(),
                    expiresAt = if (timeout != Duration.ZERO) {
                        Instant.now().plus(timeout)
                    } else null,
                    hostname = "localhost",
                    processId = ProcessHandle.current().pid()
                )

                return true
            } finally {
                mutex.unlock()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
    }

    /**
     * Thread-safe lock release
     */
    override fun releaseLock(lockId: String): Boolean {
        val mutex = mutexMap[lockId] ?: return false

        mutex.lock()
        try {
            val lockInfo = locks[lockId] ?: return false

            // 현재 스레드가 락 소유자인지 확인
            if (lockInfo.holder != Thread.currentThread().name) {
                throw IllegalStateException(
                    "Lock $lockId is held by ${lockInfo.holder}, " +
                    "cannot be released by ${Thread.currentThread().name}"
                )
            }

            locks.remove(lockId)
            return true
        } finally {
            mutex.unlock()
        }
    }

    override fun isLocked(lockId: String): Boolean {
        val lockInfo = locks[lockId] ?: return false

        // 만료 시간 체크
        lockInfo.expiresAt?.let { expiresAt ->
            if (Instant.now().isAfter(expiresAt)) {
                // 만료된 락 자동 제거
                locks.remove(lockId)
                return false
            }
        }

        return true
    }

    override fun getLockInfo(lockId: String): LockInfo? {
        return locks[lockId]?.let { info ->
            // 만료 체크
            info.expiresAt?.let { expiresAt ->
                if (Instant.now().isAfter(expiresAt)) {
                    locks.remove(lockId)
                    return null
                }
            }
            info
        }
    }

    override fun releaseAllLocks() {
        mutexMap.values.forEach { mutex ->
            mutex.lock()
            try {
                // 각 mutex를 잡고 처리
            } finally {
                mutex.unlock()
            }
        }
        locks.clear()
        mutexMap.clear()
    }

    override fun supportsAdvisoryLocks(): Boolean {
        return false  // In-memory implementation
    }

    /**
     * 주기적으로 만료된 락을 정리
     */
    fun cleanupExpiredLocks() {
        val now = Instant.now()
        locks.entries.removeIf { entry ->
            entry.value.expiresAt?.let { it.isBefore(now) } ?: false
        }
    }
}