package io.github.goodgoodjm.otter.core.migration

import io.github.goodgoodjm.otter.core.Logger
import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.MigrationFile
import io.github.goodgoodjm.otter.core.OtterConfig
import io.github.goodgoodjm.otter.core.adapter.DatabaseAdapter
import io.github.goodgoodjm.otter.core.adapter.connection.TransactionContext
import io.github.goodgoodjm.otter.core.security.SecureMigrationScriptEngine
import javax.script.ScriptEngine

/**
 * 마이그레이션 실행 담당
 *
 * 마이그레이션 스크립트를 실행하고 DSL을 SQL로 변환합니다.
 */
class MigrationExecutor(
    private val adapter: DatabaseAdapter,
    private val config: OtterConfig
) : Logger {

    private val scriptEngine by lazy {
        SecureMigrationScriptEngine()
    }

    /**
     * 마이그레이션 UP 실행
     */
    fun execute(migration: MigrationFile, context: TransactionContext) {
        val migrationObj = loadMigrationObject(migration)

        // 새로운 컨텍스트 패턴 사용
        MigrationContext.runInContext(adapter, context, config) {
            migrationObj.up()
            applyContexts(migrationObj, context)

            if (config.showSql) {
                logger.info("Migration ${migration.name} executed successfully")
            }
        }
    }

    /**
     * DSL로 수집된 스키마 컨텍스트를 SQL로 변환하여 실제로 실행한다.
     *
     * createTable/alterTable/dropTable/rawQuery는 up()/down() 내에서 컨텍스트를
     * 수집하기만 하므로, 여기서 resolve()하여 트랜잭션에 실행해야 실제 반영된다.
     */
    private fun applyContexts(migrationObj: Migration, context: TransactionContext) {
        migrationObj.contexts.forEach { schemaContext ->
            schemaContext.resolve().forEach { sql ->
                if (config.showSql) {
                    logger.info("Executing SQL: $sql")
                }
                context.execute(sql)
            }
        }
        migrationObj.clearContexts()
    }

    /**
     * 마이그레이션 DOWN 실행 (롤백)
     */
    fun rollback(migration: MigrationFile, context: TransactionContext) {
        val migrationObj = loadMigrationObject(migration)

        // 새로운 컨텍스트 패턴 사용
        MigrationContext.runInContext(adapter, context, config) {
            migrationObj.down()
            applyContexts(migrationObj, context)

            if (config.showSql) {
                logger.info("Migration ${migration.name} rolled back successfully")
            }
        }
    }

    /**
     * 스크립트에서 Migration 객체 로드
     */
    private fun loadMigrationObject(migration: MigrationFile): Migration {
        try {
            return scriptEngine.evalMigration(migration.content)
        } catch (e: Exception) {
            throw MigrationExecutionException(
                "Failed to load migration ${migration.name}: ${e.message}",
                e
            )
        }
    }
}

/**
 * 마이그레이션 실행 예외
 */
class MigrationExecutionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 개선된 마이그레이션 컨텍스트 - ThreadLocal 기반
 *
 * 글로벌 상태 제거, 스레드 안전성 보장, 중첩 컨텍스트 지원
 */
object MigrationContext {
    private val contextHolder = ThreadLocal<Context>()

    data class Context(
        val adapter: DatabaseAdapter,
        val context: TransactionContext,
        val config: OtterConfig
    )

    /**
     * 컨텍스트 스코프 내에서 실행
     * 중첩 컨텍스트를 지원하여 이전 컨텍스트를 자동 복원
     */
    fun <T> runInContext(
        adapter: DatabaseAdapter,
        context: TransactionContext,
        config: OtterConfig,
        block: () -> T
    ): T {
        val previousContext = contextHolder.get()
        return try {
            contextHolder.set(Context(adapter, context, config))
            block()
        } finally {
            if (previousContext != null) {
                contextHolder.set(previousContext)
            } else {
                contextHolder.remove()
            }
        }
    }

    /**
     * 레거시 API - deprecated
     */
    @Deprecated("Use runInContext instead", ReplaceWith("runInContext(adapter, context, config) { }"))
    fun set(adapter: DatabaseAdapter, context: TransactionContext, config: OtterConfig) {
        contextHolder.set(Context(adapter, context, config))
    }

    fun get(): Context {
        return contextHolder.get()
            ?: throw IllegalStateException(
                "MigrationContext not initialized. " +
                "Use MigrationContext.runInContext { } to establish context."
            )
    }

    fun clear() {
        contextHolder.remove()
    }

    fun getAdapter(): DatabaseAdapter = get().adapter
    fun getTransactionContext(): TransactionContext = get().context
    fun getConfig(): OtterConfig = get().config

    /**
     * 현재 컨텍스트 존재 여부 확인
     */
    fun isInitialized(): Boolean = contextHolder.get() != null
}