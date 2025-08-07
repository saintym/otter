package io.github.goodgoodjm.otter.core

import io.github.goodgoodjm.otter.core.Constants.Lock.DEFAULT_TIMEOUT_SECONDS
import io.github.goodgoodjm.otter.core.Constants.Lock.RETRY_INTERVAL_MILLIS
import io.github.goodgoodjm.otter.core.process.DownProcess
import io.github.goodgoodjm.otter.core.resourceresolver.ResourceResolver
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
import javax.script.ScriptEngineManager

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

        transaction(database) {
            block()
        }

        logger.info("Success migration.")
        TransactionManager.closeAndUnregister(database)
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
            val lockedBy = with(LockTable) { select { isLocked eq true }.first()[lockedBy] }
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
        val isLocked = LockTable.select { LockTable.id eq 1 }.first()[LockTable.isLocked]

        if (isLocked) return false
        val affectedCount = with(LockTable) {
            update({ (id eq 1) and (this@with.isLocked eq false) }) {
                it[this.isLocked] = true
                it[grantedAt] = CurrentDateTime
                it[lockedBy] = InetAddress.getLocalHost().run { "$hostName ($hostAddress)" }
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

        migration.down()
        migration.contexts.flatMap { it.resolve() }.forEach {
            if (showSql) logger.info(it)
            transaction.exec(it)
        }
        transaction.commit()

        MigrationTable.deleteWhere { MigrationTable.filename eq name }
    }

    private fun handleMigration(name: String, migration: Migration) {
        logger.debug("Executing migration: $name")
        migration.up()
        logger.debug("Migration contexts count: ${migration.contexts.size}")
        
        migration.contexts.flatMap { it.resolve() }.forEach {
            if (showSql) logger.info(it)
            logger.debug("Executing SQL: $it")
            transaction.exec(it)
        }
        transaction.commit()

        MigrationTable.insert {
            it[filename] = name
            it[comment] = migration.comment
        }
        logger.debug("Migration $name completed")
    }

    private fun loadMigrations(): Map<String, Migration> = ResourceResolver().resolveEntries(migrationPath)
        .sortedBy { it }
        .also { logger.debug("Target files : $it") }
        .associateWith { this::class.java.classLoader.getResource(it) }
        .filterValues { it != null }
        .mapKeys { it.key.split("/").last() }
        .mapValues { evalMigration(it.value!!.openStream().reader()) }

    private fun evalMigration(reader: Reader): Migration {
        val engine = ScriptEngineManager().getEngineByExtension("kts")
        return engine.eval(reader) as Migration
    }
}

