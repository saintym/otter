package io.github.goodgoodjm.otter.core

import io.github.goodgoodjm.otter.core.adapter.DatabaseAdapter
import io.github.goodgoodjm.otter.core.adapter.DatabaseConfig
import io.github.goodgoodjm.otter.core.migration.MigrationExecutor
import io.github.goodgoodjm.otter.core.migration.MigrationTracker
import io.github.goodgoodjm.otter.core.resourceresolver.ResourceResolver
import io.github.goodgoodjm.otter.core.security.SecureMigrationScriptEngine
import io.github.goodgoodjm.otter.core.io.UserInputHandler
import io.github.goodgoodjm.otter.core.io.ConsoleUserInputHandler
import kotlinx.coroutines.runBlocking
import java.io.Reader
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDateTime

/**
 * Otter - DB Migration Tool
 *
 * Exposed 의존성 없이 DatabaseAdapter 패턴을 사용하여
 * 다양한 데이터베이스를 지원합니다.
 */
class Otter(
    private val config: OtterConfig
) {
    companion object : Logger {
        fun from(config: OtterConfig) = Otter(config)
    }

    private val adapter: DatabaseAdapter by lazy {
        val adapterFactory = AdapterFactory()
        val dbConfig = DatabaseConfig(
            url = config.url,
            username = config.user,
            password = config.password,
            driverClassName = config.driverClassName
        )

        val adapter = adapterFactory.create(dbConfig)
        adapter.initialize(dbConfig)

        val validationResult = adapter.validate()
        when (validationResult) {
            is io.github.goodgoodjm.otter.core.adapter.ValidationResult.Error -> {
                throw IllegalStateException("Database validation failed: ${validationResult.message}")
            }
            is io.github.goodgoodjm.otter.core.adapter.ValidationResult.Warning -> {
                logger.warn("Database validation warning: ${validationResult.message}")
            }
            else -> {
                logger.info("Database validation successful for ${adapter.name}")
            }
        }

        adapter
    }

    private val migrationTracker = MigrationTracker(adapter)
    private val migrationExecutor = MigrationExecutor(adapter, config)

    /**
     * 마이그레이션 실행 (UP)
     */
    fun up() {
        logger.info("Starting migration with ${adapter.name}")

        runBlocking {
            val lockProvider = adapter.getLockProvider()
            if (!lockProvider.acquireLock("otter_migration")) {
                throw IllegalStateException("Could not acquire migration lock")
            }

            try {
                adapter.getConnectionProvider().useTransaction { context ->
                    // 마이그레이션 추적 테이블 초기화
                    migrationTracker.initialize(context)

                    // 마이그레이션 파일 로드
                    val migrations = loadMigrations()

                    // 이미 실행된 마이그레이션 조회
                    val appliedMigrations = migrationTracker.getAppliedMigrations(context)

                    // 실행할 마이그레이션 필터링
                    val pendingMigrations = migrations
                        .filter { it.name !in appliedMigrations }
                        .filter { config.version.isEmpty() || it.name <= config.version }
                        .sortedBy { it.name }

                    if (pendingMigrations.isEmpty()) {
                        logger.info("No pending migrations found")
                        return@useTransaction
                    }

                    logger.info("Found ${pendingMigrations.size} pending migrations")

                    // 마이그레이션 실행
                    for (migration in pendingMigrations) {
                        logger.info("Applying migration: ${migration.name}")

                        try {
                            migrationExecutor.execute(migration, context)
                            migrationTracker.recordMigration(migration.name, context)
                            logger.info("Successfully applied: ${migration.name}")
                        } catch (e: Exception) {
                            logger.error("Failed to apply migration ${migration.name}: ${e.message}", e)
                            throw MigrationException("Migration failed at ${migration.name}", e)
                        }
                    }

                    logger.info("Migration completed successfully")
                }
            } finally {
                lockProvider.releaseLock("otter_migration")
            }
        }
    }

    /**
     * 마이그레이션 롤백 (DOWN)
     *
     * @param steps 롤백할 마이그레이션 개수
     * @param force 강제 롤백 (에러 무시)
     * @param confirmRollback 사용자 확인 필요 여부
     * @param showSql SQL 문 출력 여부
     */
    fun down(
        steps: Int = 1,
        force: Boolean = false,
        confirmRollback: Boolean = false,
        showSql: Boolean = false
    ) {
        logger.info("Starting rollback of $steps steps with ${adapter.name}")

        runBlocking {
            val lockProvider = adapter.getLockProvider()
            if (!lockProvider.acquireLock("otter_migration")) {
                throw IllegalStateException("Could not acquire migration lock")
            }

            try {
                adapter.getConnectionProvider().useTransaction { context ->
                    val appliedMigrations = migrationTracker.getAppliedMigrations(context)
                        .sortedDescending()
                        .take(steps)

                    if (appliedMigrations.isEmpty()) {
                        logger.info("No migrations to rollback")
                        return@useTransaction
                    }

                    // 사용자 확인
                    if (confirmRollback && !force) {
                        val inputHandler = ConsoleUserInputHandler()
                        inputHandler.showMessage("⚠️  WARNING: You are about to rollback ${appliedMigrations.size} migration(s)")
                        inputHandler.showMessage("This may result in data loss!")
                        inputHandler.showMessage("")
                        inputHandler.showMessage("Migrations to be rolled back:")
                        appliedMigrations.forEach { name ->
                            inputHandler.showMessage("  - $name")
                        }
                        inputHandler.showMessage("")
                        val response = inputHandler.readInput("Do you want to continue? (yes/no): ")
                            ?.trim()?.lowercase()

                        if (response != "yes" && response != "y") {
                            logger.info("Rollback cancelled by user")
                            return@useTransaction
                        }
                    }

                    for (migrationName in appliedMigrations) {
                        logger.info("Rolling back migration: $migrationName")

                        if (showSql) {
                            logger.info("Executing rollback for: $migrationName")
                        }

                        try {
                            val migration = loadMigration(migrationName)
                            migrationExecutor.rollback(migration, context)
                            migrationTracker.removeMigration(migrationName, context)
                            logger.info("Successfully rolled back: $migrationName")
                        } catch (e: Exception) {
                            if (!force) {
                                logger.error("Failed to rollback migration $migrationName: ${e.message}", e)
                                throw MigrationException("Rollback failed at $migrationName", e)
                            } else {
                                logger.warn("Force rollback: ignoring error for $migrationName: ${e.message}")
                                migrationTracker.removeMigration(migrationName, context)
                            }
                        }
                    }

                    logger.info("Rollback completed successfully")
                }
            } finally {
                lockProvider.releaseLock("otter_migration")
            }
        }
    }

    /**
     * 마이그레이션 상태 확인
     */
    fun status(): MigrationStatus {
        return adapter.getConnectionProvider().useTransaction(readOnly = true) { context ->
            val applied = migrationTracker.getAppliedMigrations(context)
            val available = loadMigrations().map { it.name }
            val pending = available - applied.toSet()

            MigrationStatus(
                applied = applied.sorted(),
                pending = pending.sorted(),
                current = applied.maxOrNull()
            )
        }
    }

    private fun loadMigrations(): List<MigrationFile> {
        val resolver = ResourceResolver()
        val entries = resolver.resolveEntries(config.migrationPath)

        return entries.map { entry ->
            val name = entry.substringAfterLast("/").substringBeforeLast(".")
            val content = this::class.java.classLoader.getResource(entry)?.readText()
                ?: throw MigrationException("Cannot read migration file: $entry")
            MigrationFile(
                name = name,
                content = content.reader()
            )
        }.sortedBy { it.name }
    }

    private fun loadMigration(name: String): MigrationFile {
        return loadMigrations().find { it.name == name }
            ?: throw MigrationException("Migration not found: $name")
    }

    /**
     * 리소스 정리
     */
    fun close() {
        adapter.close()
    }
}

/**
 * 어댑터 팩토리
 */
class AdapterFactory {
    fun create(config: DatabaseConfig): DatabaseAdapter {
        val dbType = detectDatabaseType(config.url)

        return when (dbType) {
            "postgresql" -> {
                // PostgreSQL 어댑터 동적 로드
                val clazz = Class.forName("io.github.goodgoodjm.otter.adapter.postgresql.PostgreSQLAdapter")
                clazz.getDeclaredConstructor().newInstance() as DatabaseAdapter
            }
            "mysql" -> {
                // MySQL 어댑터 (추후 구현)
                throw UnsupportedDatabaseException("MySQL adapter not yet implemented")
            }
            "h2" -> {
                // H2 어댑터 - 테스트 환경에서 사용
                try {
                    val clazz = Class.forName("io.github.goodgoodjm.otter.core.adapter.TestDatabaseAdapter")
                    clazz.getDeclaredConstructor().newInstance() as DatabaseAdapter
                } catch (e: ClassNotFoundException) {
                    throw UnsupportedDatabaseException("H2 adapter not yet implemented for production use")
                }
            }
            else -> {
                throw UnsupportedDatabaseException("Unsupported database type: $dbType")
            }
        }
    }

    private fun detectDatabaseType(url: String): String {
        return when {
            url.startsWith("jdbc:postgresql:") -> "postgresql"
            url.startsWith("jdbc:mysql:") -> "mysql"
            url.startsWith("jdbc:h2:") -> "h2"
            url.startsWith("jdbc:sqlite:") -> "sqlite"
            else -> throw UnsupportedDatabaseException("Cannot detect database type from URL: $url")
        }
    }
}

/**
 * 마이그레이션 상태
 */
data class MigrationStatus(
    val applied: List<String>,
    val pending: List<String>,
    val current: String?
)

/**
 * 마이그레이션 파일
 */
data class MigrationFile(
    val name: String,
    val content: Reader
)

/**
 * 마이그레이션 예외
 */
class MigrationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 지원되지 않는 데이터베이스 예외
 */
class UnsupportedDatabaseException(message: String) : Exception(message)