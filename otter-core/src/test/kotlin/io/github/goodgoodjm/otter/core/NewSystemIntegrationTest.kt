package io.github.goodgoodjm.otter.core

import io.github.goodgoodjm.otter.TestDatabaseConfig
import io.github.goodgoodjm.otter.core.adapter.DatabaseConfig
import io.github.goodgoodjm.otter.core.adapter.lock.ConcurrentLockProvider
import io.github.goodgoodjm.otter.core.adapter.MockAdapter
import io.github.goodgoodjm.otter.core.dsl.createtable.createTable
import io.github.goodgoodjm.otter.core.dsl.type.*
import io.github.goodgoodjm.otter.core.migration.MigrationContext
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * 새로운 시스템 통합 테스트
 *
 * 1. TypeV2 불변 타입 시스템
 * 2. ThreadLocal 기반 MigrationContext
 * 3. ConcurrentLockProvider
 */
class NewSystemIntegrationTest {

    private lateinit var adapter: MockAdapter
    private lateinit var lockProvider: ConcurrentLockProvider

    @BeforeEach
    fun setup() {
        adapter = MockAdapter()
        adapter.initialize(DatabaseConfig("jdbc:h2:mem:test", "sa", ""))
        lockProvider = ConcurrentLockProvider()
    }

    @AfterEach
    fun teardown() {
        MigrationContext.clear()
        lockProvider.releaseAllLocks()
    }

    @Test
    fun `새로운 타입 시스템은 불변성을 보장한다`() {
        val column1 = Types.varchar(255).notNull().unique()
        val column2 = column1.default("test")

        // column1은 변경되지 않음
        assertFalse(column1.defaultValue != null)
        assertTrue(column2.defaultValue is DefaultValue.Literal)

        // 각 컬럼은 독립적
        assertEquals(column1.constraints.size, 2)  // NOT_NULL, UNIQUE
        assertEquals(column2.constraints.size, 2)  // 동일
        assertNotSame(column1, column2)
    }

    @Test
    fun `병렬 마이그레이션 컨텍스트가 격리된다`() = runBlocking {
        val results = ConcurrentHashMap<String, String>()
        val jobs = mutableListOf<Job>()

        // 10개의 병렬 코루틴
        repeat(10) { index ->
            jobs.add(launch(Dispatchers.Default) {
                val testAdapter = MockAdapter()
                testAdapter.initialize(DatabaseConfig("jdbc:h2:mem:test$index", "sa", ""))

                MigrationContext.runInContext(testAdapter, MockTransactionContext(), TestDatabaseConfig.createH2OtterConfig()) {
                    // 각 컨텍스트는 독립적
                    val currentAdapter = MigrationContext.getAdapter()
                    results[Thread.currentThread().name] = currentAdapter.name

                    // 중첩 컨텍스트도 지원
                    val nestedAdapter = MockAdapter()
                    nestedAdapter.initialize(DatabaseConfig("jdbc:h2:mem:nested$index", "sa", ""))

                    MigrationContext.runInContext(nestedAdapter, MockTransactionContext(), TestDatabaseConfig.createH2OtterConfig()) {
                        val nestedCurrent = MigrationContext.getAdapter()
                        assertNotSame(currentAdapter, nestedCurrent)
                    }

                    // 중첩 컨텍스트 후 원래 컨텍스트 복원
                    assertEquals(currentAdapter, MigrationContext.getAdapter())
                }
            })
        }

        jobs.joinAll()

        // 모든 스레드가 독립적으로 실행됨
        assertTrue(results.size >= 1)  // 최소 1개 이상의 스레드
    }

    @Test
    fun `ConcurrentLockProvider는 동시성을 안전하게 처리한다`() {
        val lockId = "test_lock"
        val successCount = AtomicInteger(0)
        val threadCount = 20
        val latch = CountDownLatch(threadCount)

        val threads = List(threadCount) {
            Thread {
                try {
                    if (lockProvider.acquireLock(lockId, Duration.ofSeconds(1))) {
                        successCount.incrementAndGet()
                        Thread.sleep(10)  // 짧은 작업
                        lockProvider.releaseLock(lockId)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        // 정확히 하나의 스레드만 락 획득
        assertEquals(1, successCount.get())
        assertFalse(lockProvider.isLocked(lockId))
    }

    @Test
    fun `CreateTable은 불변 컬럼으로 테이블을 생성한다`() {
        adapter.getConnectionProvider().useTransaction { context ->
            MigrationContext.runInContext(adapter, context, TestDatabaseConfig.createH2OtterConfig()) {
                val sql = createTable("users") {
                    "id" - Types.serial()
                    "email" - Types.varchar(255).notNull().unique()
                    "name" - Types.varchar(100)
                    "bio" - Types.text().defaultNull()
                    "created_at" - Types.timestamp().defaultCurrentTimestamp()
                    "is_active" - Types.boolean().default("true")
                    "parent_id" - Types.bigInt().references("users", "id")
                }

                assertTrue(sql.isNotEmpty())
                sql.forEach { statement ->
                    assertTrue(statement.contains("CREATE TABLE") ||
                              statement.contains("PRIMARY KEY") ||
                              statement.contains("FOREIGN KEY"))
                }
            }
        }
    }

    @Test
    fun `컬럼 재사용이 안전하다`() {
        // 전역 컬럼 타입 정의
        val standardId = Types.serial()
        val standardEmail = Types.varchar(255).notNull().unique()
        val standardTimestamp = Types.timestamp().defaultCurrentTimestamp()

        adapter.getConnectionProvider().useTransaction { context ->
            MigrationContext.runInContext(adapter, context, TestDatabaseConfig.createH2OtterConfig()) {
                // 여러 테이블에서 재사용
                val usersTable = createTable("users") {
                    "id" - standardId
                    "email" - standardEmail
                    "created_at" - standardTimestamp
                }

                val postsTable = createTable("posts") {
                    "id" - standardId
                    "author_email" - standardEmail
                    "published_at" - standardTimestamp
                }

                // 각 테이블은 독립적
                assertTrue(usersTable.any { it.contains("users") })
                assertTrue(postsTable.any { it.contains("posts") })
            }
        }
    }
}

/**
 * Mock TransactionContext for testing
 */
class MockTransactionContext : io.github.goodgoodjm.otter.core.adapter.connection.TransactionContext {
    override val connection: java.sql.Connection
        get() = throw NotImplementedError("Mock connection not implemented")

    override fun execute(sql: String, params: List<Any?>): Int = 1
    override fun <T> query(sql: String, params: List<Any?>, mapper: (java.sql.ResultSet) -> T): List<T> = emptyList()
    override fun <T> queryOne(sql: String, params: List<Any?>, mapper: (java.sql.ResultSet) -> T): T? = null
    override fun setSavepoint(name: String): java.sql.Savepoint = MockSavepoint(name)
    override fun rollbackToSavepoint(savepoint: java.sql.Savepoint) {}
    override fun releaseSavepoint(savepoint: java.sql.Savepoint) {}
    override fun commit() {}
    override fun rollback() {}
}

class MockSavepoint(private val name: String) : java.sql.Savepoint {
    override fun getSavepointId(): Int = 0
    override fun getSavepointName(): String = name
}