package io.github.goodgoodjm.otter.core.adapter.lock

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * 개선된 LockProvider 동시성 테스트
 *
 * 레이스 컨디션을 확실하게 테스트하기 위한 개선된 테스트 스위트
 */
class ImprovedLockProviderTest {

    private lateinit var lockProvider: LockProvider

    @BeforeEach
    fun setup() {
        lockProvider = ConcurrentLockProvider()
    }

    @AfterEach
    fun teardown() {
        lockProvider.releaseAllLocks()
    }

    @Test
    fun `should handle concurrent lock attempts with proper synchronization`() {
        val lockId = "test_lock"
        val threadCount = 10
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // 모든 스레드가 동시에 시작하도록 보장
        val startBarrier = CyclicBarrier(threadCount + 1)
        // 모든 스레드가 완료될 때까지 대기
        val completionLatch = CountDownLatch(threadCount)

        val threads = List(threadCount) { threadIndex ->
            Thread {
                try {
                    // 모든 스레드가 준비될 때까지 대기
                    startBarrier.await()

                    // 락 획득 시도
                    val acquired = lockProvider.acquireLock(lockId, Duration.ofSeconds(1))

                    if (acquired) {
                        successCount.incrementAndGet()
                        try {
                            // 락을 잠시 유지 (다른 스레드들이 시도하도록)
                            Thread.sleep(50)
                        } finally {
                            // 반드시 락 해제
                            lockProvider.releaseLock(lockId)
                        }
                    } else {
                        failureCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    completionLatch.countDown()
                }
            }.apply {
                name = "TestThread-$threadIndex"
            }
        }

        // 모든 스레드 시작
        threads.forEach { it.start() }

        // 모든 스레드가 준비되면 동시에 시작
        startBarrier.await()

        // 모든 스레드 완료 대기 (타임아웃 포함)
        assertTrue(
            completionLatch.await(10, java.util.concurrent.TimeUnit.SECONDS),
            "Some threads did not complete in time"
        )

        // 검증: 정확히 하나의 스레드만 락 획득
        assertEquals(1, successCount.get(), "Exactly one thread should acquire the lock")
        assertEquals(threadCount - 1, failureCount.get(), "All other threads should fail")

        // 락이 해제되었는지 확인
        assertFalse(lockProvider.isLocked(lockId), "Lock should be released after test")
    }

    @Test
    fun `should handle lock timeout correctly`() {
        val lockId = "timeout_lock"
        val holder = Thread {
            lockProvider.acquireLock(lockId, Duration.ofSeconds(5))
            Thread.sleep(2000)  // 2초 동안 락 유지
            lockProvider.releaseLock(lockId)
        }

        holder.start()
        Thread.sleep(100)  // 첫 번째 스레드가 락을 잡도록 대기

        // 타임아웃으로 락 획득 실패
        val acquired = lockProvider.acquireLock(lockId, Duration.ofMillis(500))
        assertFalse(acquired, "Should not acquire lock due to timeout")

        holder.join()
    }

    @Test
    fun `should prevent double acquisition by same thread`() {
        val lockId = "double_lock"

        // 첫 번째 획득 성공
        assertTrue(lockProvider.acquireLock(lockId))

        // 같은 스레드에서 다시 획득 시도 - 실패해야 함
        assertFalse(lockProvider.acquireLock(lockId))

        // 해제
        assertTrue(lockProvider.releaseLock(lockId))

        // 해제 후 다시 획득 가능
        assertTrue(lockProvider.acquireLock(lockId))
        lockProvider.releaseLock(lockId)
    }

    @Test
    fun `should track lock holder information correctly`() {
        val lockId = "info_lock"

        val holderThread = Thread {
            lockProvider.acquireLock(lockId)
            Thread.sleep(1000)
            lockProvider.releaseLock(lockId)
        }.apply {
            name = "HolderThread"
        }

        holderThread.start()
        Thread.sleep(100)  // 락이 잡히도록 대기

        val lockInfo = lockProvider.getLockInfo(lockId)
        assertNotNull(lockInfo)
        assertEquals("HolderThread", lockInfo.holder)
        assertTrue(lockInfo.processId?.let { it > 0 } ?: false)

        holderThread.join()
    }

    @Test
    fun `should handle stress test with many threads`() {
        val lockCount = 5
        val threadsPerLock = 20
        val successCounts = Array(lockCount) { AtomicInteger(0) }
        val allThreads = mutableListOf<Thread>()

        for (lockIndex in 0 until lockCount) {
            val lockId = "stress_lock_$lockIndex"
            val barrier = CyclicBarrier(threadsPerLock)

            for (threadIndex in 0 until threadsPerLock) {
                allThreads.add(Thread {
                    barrier.await()  // 동시 시작
                    if (lockProvider.acquireLock(lockId, Duration.ofSeconds(1))) {
                        successCounts[lockIndex].incrementAndGet()
                        Thread.sleep(10)
                        lockProvider.releaseLock(lockId)
                    }
                })
            }
        }

        allThreads.forEach { it.start() }
        allThreads.forEach { it.join(5000) }  // 5초 타임아웃

        // 각 락에 대해 정확히 하나의 스레드만 성공
        successCounts.forEachIndexed { index, count ->
            assertEquals(
                1, count.get(),
                "Lock $index should be acquired exactly once, but was ${count.get()}"
            )
        }
    }
}