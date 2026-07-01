package io.github.goodgoodjm.otter.core.migration

import io.github.goodgoodjm.otter.core.adapter.DatabaseAdapter
import java.util.concurrent.ConcurrentHashMap

/**
 * 개선된 MigrationContext - 글로벌 상태 제거
 *
 * ThreadLocal과 Context 패턴을 사용하여 스레드 안전성과 테스트 격리 보장
 */
class ImprovedMigrationContext private constructor(
    val adapter: DatabaseAdapter,
    val contextId: String = generateContextId()
) {

    companion object {
        // ThreadLocal로 각 스레드별 독립적인 컨텍스트
        private val threadLocalContext = ThreadLocal<ImprovedMigrationContext>()

        // 테스트를 위한 명시적 컨텍스트 저장소
        private val testContexts = ConcurrentHashMap<String, ImprovedMigrationContext>()

        /**
         * 컨텍스트 스코프 내에서 실행
         *
         * @param adapter 사용할 DatabaseAdapter
         * @param block 실행할 코드 블록
         * @return 블록 실행 결과
         */
        fun <T> withContext(adapter: DatabaseAdapter, block: () -> T): T {
            val previousContext = threadLocalContext.get()
            val newContext = ImprovedMigrationContext(adapter)

            return try {
                threadLocalContext.set(newContext)
                block()
            } finally {
                // 이전 컨텍스트 복원 (중첩 컨텍스트 지원)
                if (previousContext != null) {
                    threadLocalContext.set(previousContext)
                } else {
                    threadLocalContext.remove()
                }
            }
        }

        /**
         * 현재 컨텍스트 획득
         *
         * @throws IllegalStateException 컨텍스트가 설정되지 않은 경우
         */
        fun current(): ImprovedMigrationContext {
            return threadLocalContext.get()
                ?: throw IllegalStateException(
                    "MigrationContext not initialized. " +
                    "Use MigrationContext.withContext { } to establish context."
                )
        }

        /**
         * 현재 어댑터 획득 (편의 메서드)
         */
        fun currentAdapter(): DatabaseAdapter = current().adapter

        /**
         * 테스트용 명시적 컨텍스트 생성
         *
         * @param testName 테스트 이름
         * @param adapter 테스트용 어댑터
         * @return 테스트 컨텍스트 ID
         */
        fun createTestContext(testName: String, adapter: DatabaseAdapter): String {
            val contextId = "test_${testName}_${System.nanoTime()}"
            val context = ImprovedMigrationContext(adapter, contextId)
            testContexts[contextId] = context
            threadLocalContext.set(context)
            return contextId
        }

        /**
         * 테스트 컨텍스트 정리
         *
         * @param contextId 테스트 컨텍스트 ID
         */
        fun cleanupTestContext(contextId: String) {
            testContexts.remove(contextId)
            val currentContext = threadLocalContext.get()
            if (currentContext?.contextId == contextId) {
                threadLocalContext.remove()
            }
        }

        /**
         * 모든 테스트 컨텍스트 정리
         */
        fun cleanupAllTestContexts() {
            testContexts.clear()
            threadLocalContext.remove()
        }

        /**
         * 컨텍스트 ID 생성
         */
        private fun generateContextId(): String {
            return "ctx_${System.currentTimeMillis()}_${Thread.currentThread().id}"
        }

        /**
         * 현재 컨텍스트 존재 여부 확인
         */
        fun hasContext(): Boolean = threadLocalContext.get() != null
    }
}

/**
 * DSL에서 사용할 확장 함수
 */
fun <T> runInMigrationContext(adapter: DatabaseAdapter, block: () -> T): T {
    return ImprovedMigrationContext.withContext(adapter, block)
}

/**
 * 사용 예제:
 *
 * // 일반 사용
 * runInMigrationContext(postgresAdapter) {
 *     createTable("users") {
 *         "id" - SERIAL PRIMARY KEY
 *     }
 * }
 *
 * // 테스트
 * class MyTest {
 *     @Before
 *     fun setup() {
 *         val contextId = ImprovedMigrationContext.createTestContext("myTest", mockAdapter)
 *     }
 *
 *     @After
 *     fun teardown() {
 *         ImprovedMigrationContext.cleanupAllTestContexts()
 *     }
 *
 *     @Test
 *     fun testMigration() {
 *         // 각 테스트는 독립적인 컨텍스트에서 실행
 *         createTable("test") { ... }
 *     }
 * }
 */