package io.github.goodgoodjm.otter.core

import io.github.goodgoodjm.otter.core.Constants.Lock.DEFAULT_TIMEOUT_SECONDS
import io.github.goodgoodjm.otter.core.Constants.Lock.RETRY_INTERVAL_MILLIS
import io.github.goodgoodjm.otter.core.process.DownProcess
import io.github.goodgoodjm.otter.core.resourceresolver.ResourceResolver
import io.github.goodgoodjm.otter.core.security.SecureMigrationScriptEngine
import io.github.goodgoodjm.otter.core.transaction.TransactionHelper
import io.github.goodgoodjm.otter.core.concurrent.ConcurrentSafetyHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.Reader
import java.net.InetAddress

class Otter(
    private val config: OtterConfig
) {
    companion object : Logger {
        fun from(config: OtterConfig) = Otter(config)
    }

    private fun migrationScope(block: Transaction.() -> Unit) {
        logger.info("Start migration.")
        val database = Database.connect(
            config.url,
            config.driverClassName,
            config.user,
            config.password
        )

        try {
            transaction(database) {
                try {
                    block()
                    // 모든 작업이 성공하면 커밋
                    commit()
                    logger.info("Success migration.")
                } catch (e: Exception) {
                    logger.error("Migration failed: ${e.message}", e)
                    // 트랜잭션은 자동으로 롤백됨
                    throw e
                }
            }
        } finally {
            TransactionManager.closeAndUnregister(database)
        }
    }

    fun up() = migrationScope {
        MigrationProcess(this, config.migrationPath, config.showSql, config.version, config.testMode).exec()
    }
    
    fun down(steps: Int = 1, force: Boolean = false) = migrationScope {
        DownProcess(
            transaction = this,
            migrationPath = config.migrationPath,
            steps = steps,
            showSql = config.showSql,
            force = force
        ).exec()
    }
}

object MigrationTable : IntIdTable("otter_migration") {
    val filename = varchar("filename", 255).uniqueIndex()
    val comment = varchar("comment", 255)
    private val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    fun last() = selectAll().orderBy(createdAt to SortOrder.DESC).firstOrNull()
}

object LockTable : IntIdTable("otter_lock") {
    val isLocked = bool("is_locked")
    val grantedAt = datetime("granted_at").nullable()
    val lockedBy = varchar("locked_by", 255)
}

class MigrationProcess(
    private val transaction: Transaction,
    private val migrationPath: String,
    private val showSql: Boolean,
    private val version: String,
    private val testMode: Boolean = false
) {
    private var hasLock: Boolean = false

    companion object : Logger

    fun exec() {
        createMigrationTable()
        if (testMode) {
            logger.debug("Running in test mode - skipping migration lock")
            migration()
        } else {
            lock {
                migration()
            }
        }
    }

    private fun lock(block: () -> Unit) {
        waitForLock()
        runCatching(block)
            .also { releaseLock() }
    }

    private fun createMigrationTable() {
        with(LockTable) {
            if (!exists()) {
                SchemaUtils.create(this)
                if (selectAll().count().toInt() == 0) {
                    try {
                        transaction {
                            insert {
                                it[id] = 1
                                it[isLocked] = false
                                it[lockedBy] = ""
                                it[grantedAt] = null
                            }
                        }
                    } catch (e: ExposedSQLException) {
                        logger.error("Lock table initialize error - ", e)
                    }
                }
            }
        }

        if (!MigrationTable.exists()) {
            SchemaUtils.create(MigrationTable)
        }
    }

    private fun waitForLock() {
        var hasLock = false
        runBlocking {
            val loopLimit = DEFAULT_TIMEOUT_SECONDS.toInt()
            var count = 0
            while (!hasLock && count < loopLimit) {
                count++
                hasLock = acquireLock()
                if (!hasLock) {
                    logger.info("Waiting for lock...(${count})")
                    delay(RETRY_INTERVAL_MILLIS)
                }
            }
        }
        if (!hasLock) {
            val lockedBy = with(LockTable) { 
            select { isLocked eq true }.firstOrNull()?.get(lockedBy) ?: "unknown"
        }
            throw LockException("Could not get a database lock. Currently locked by $lockedBy")
        }
    }

    private fun releaseLock() {
        if (!hasLock) throw LockException("Unlocked process try to release lock")

        with(LockTable) {
            update({ id eq 1 }) {
                it[isLocked] = false
                it[lockedBy] = ""
                it[grantedAt] = null
            }
        }
    }

    private fun acquireLock(): Boolean {
        val lockRow = LockTable.select { LockTable.id eq 1 }.firstOrNull()
            ?: return false // 락 테이블이 초기화되지 않음
            
        val isLocked = lockRow[LockTable.isLocked]

        if (isLocked) return false
        
        val hostname = try {
            InetAddress.getLocalHost().run { "$hostName ($hostAddress)" }
        } catch (e: Exception) {
            logger.warn("Failed to get hostname: ${e.message}")
            "unknown-host"
        }
        
        val affectedCount = with(LockTable) {
            update({ (id eq 1) and (this@with.isLocked eq false) }) {
                it[this.isLocked] = true
                it[grantedAt] = CurrentDateTime
                it[lockedBy] = hostname
            }
        }
        hasLock = affectedCount != 0
        return hasLock
    }

    private fun migration() {
        val latestAppliedMigration = MigrationTable.last()
        val latestFilename = latestAppliedMigration?.get(MigrationTable.filename) ?: "".also {
            logger.debug("There is no applied migrations. All migrations would be applied.")
        }

        logger.debug("Version: '$version', Latest applied: '$latestFilename'")

        val migrations = loadMigrations()
        migrations.forEach { (name, migration) ->
            logger.debug("Checking migration: $name")
            val shouldRollback = shouldRollback(name, latestFilename)
            val shouldMigrate = shouldMigrate(name, latestFilename)
            logger.debug("  shouldRollback: $shouldRollback, shouldMigrate: $shouldMigrate")
            
            when {
                shouldRollback -> handleRollback(name, migration)
                shouldMigrate -> handleMigration(name, migration)
                else -> logger.debug("$name is already migrated or isn't included in the target version, will be skipped.")
            }
        }
    }

    private fun shouldRollback(name: String, latestFilename: String): Boolean {
        if(version.isEmpty())
            return false  // 버전이 지정되지 않으면 롤백하지 않음
        return name > version && name <= latestFilename
    }

    private fun shouldMigrate(name: String, latestFilename: String): Boolean {
        if(version.isEmpty())
            return name > latestFilename  // 버전이 지정되지 않으면 모든 새 마이그레이션 실행
        return name <= version && name > latestFilename
    }

    private fun handleRollback(name: String, migration: Migration) {
        logger.debug("$name is already migrated but is not suitable for the set version, will be rollback.")

        val result = TransactionHelper.executeWithSavepoint(transaction, "rollback_$name") {
            migration.down()
            val statements = migration.contexts.flatMap { it.resolve() }
            
            statements.forEach { sql ->
                if (showSql) logger.info(sql)
                exec(sql)
            }
            
            MigrationTable.deleteWhere { MigrationTable.filename eq name }
        }
        
        result.getOrThrow() // 실패 시 예외 발생
    }

    private fun handleMigration(name: String, migration: Migration) {
        logger.debug("Executing migration: $name")
        
        val result = TransactionHelper.executeWithSavepoint(transaction, "migration_$name") {
            migration.up()
            logger.debug("Migration contexts count: ${migration.contexts.size}")
            
            val statements = migration.contexts.flatMap { it.resolve() }
            
            statements.forEach { sql ->
                if (showSql) logger.info(sql)
                logger.debug("Executing SQL: $sql")
                exec(sql)
            }
            
            MigrationTable.insert {
                it[filename] = name
                it[comment] = migration.comment
            }
        }
        
        result.getOrThrow() // 실패 시 예외 발생
        logger.debug("Migration $name completed")
    }

    private fun loadMigrations(): Map<String, Migration> = ResourceResolver().resolveEntries(migrationPath)
        .sortedBy { it }
        .also { logger.debug("Target files : $it") }
        .mapNotNull { path ->
            this::class.java.classLoader.getResource(path)?.let { resource ->
                path.split("/").last() to resource
            }
        }
        .toMap()
        .mapValues { (_, resource) -> 
            evalMigration(resource.openStream().reader()) 
        }

    private val scriptEngine = SecureMigrationScriptEngine()
    
    private fun evalMigration(reader: Reader): Migration {
        return scriptEngine.evalMigration(reader)
    }
}

