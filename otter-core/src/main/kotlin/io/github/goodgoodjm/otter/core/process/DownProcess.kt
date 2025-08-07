package io.github.goodgoodjm.otter.core.process

import io.github.goodgoodjm.otter.core.Logger
import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.MigrationTable
import io.github.goodgoodjm.otter.core.analyzer.DependencyAnalyzer
import io.github.goodgoodjm.otter.core.resourceresolver.ResourceResolver
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import java.io.Reader
import javax.script.ScriptEngineManager

class DownProcess(
    private val transaction: Transaction,
    private val migrationPath: String,
    private val steps: Int = 1,
    private val showSql: Boolean = false,
    private val force: Boolean = false
) {
    companion object : Logger
    
    private val dependencyAnalyzer = DependencyAnalyzer()
    
    fun exec() {
        // 테이블이 없으면 롤백할 것도 없음
        if (!MigrationTable.exists()) {
            logger.info("No migration table found. Nothing to rollback.")
            return
        }
        
        val recentMigrations = loadRecentMigrations()
        
        if (recentMigrations.isEmpty()) {
            logger.info("No migrations to rollback")
            return
        }
        
        if (!force) {
            confirmRollback(recentMigrations)
        }
        
        recentMigrations.forEach { (filename, migration) ->
            try {
                executeRollback(filename, migration)
            } catch (e: Exception) {
                logger.error("Failed to rollback $filename: ${e.message}")
                throw RollbackException("Rollback failed at $filename", e)
            }
        }
        
        logger.info("Successfully rolled back ${recentMigrations.size} migration(s)")
    }
    
    private fun loadRecentMigrations(): List<Pair<String, Migration>> {
        // 최근 적용된 마이그레이션 조회
        val recentFilenames = MigrationTable
            .selectAll()
            .orderBy(MigrationTable.id to SortOrder.DESC)
            .limit(steps)
            .map { it[MigrationTable.filename] }
            .reversed() // 롤백은 역순으로
        
        if (recentFilenames.isEmpty()) {
            return emptyList()
        }
        
        logger.debug("Migrations to rollback: $recentFilenames")
        
        // 파일 로드 및 평가
        return recentFilenames.mapNotNull { filename ->
            try {
                val migration = loadMigration(filename)
                filename to migration
            } catch (e: Exception) {
                logger.error("Failed to load migration $filename: ${e.message}")
                null
            }
        }
    }
    
    private fun loadMigration(filename: String): Migration {
        val resource = ResourceResolver()
            .resolveEntries(migrationPath)
            .find { it.endsWith(filename) }
            ?.let { this::class.java.classLoader.getResource(it) }
            ?: throw IllegalStateException("Migration file not found: $filename")
        
        return evalMigration(resource.openStream().reader())
    }
    
    private fun evalMigration(reader: Reader): Migration {
        val engine = ScriptEngineManager().getEngineByExtension("kts")
        return engine.eval(reader) as Migration
    }
    
    private fun confirmRollback(migrations: List<Pair<String, Migration>>) {
        logger.warn("⚠️  WARNING: You are about to rollback ${migrations.size} migration(s)")
        logger.warn("This may result in data loss!")
        logger.warn("")
        logger.warn("Migrations to be rolled back:")
        
        migrations.forEach { (filename, migration) ->
            logger.warn("  - $filename: ${migration.comment}")
            if (migration.contexts.isEmpty()) {
                logger.warn("    ⚠️  Empty down() implementation - nothing will be rolled back")
            }
        }
        
        logger.warn("")
        print("Do you want to continue? (yes/no): ")
        
        val response = readLine()?.trim()?.lowercase()
        if (response != "yes" && response != "y") {
            throw RollbackCancelledException("Rollback cancelled by user")
        }
    }
    
    private fun executeRollback(filename: String, migration: Migration) {
        logger.info("Rolling back: $filename")
        
        try {
            migration.down()
            
            if (migration.contexts.isEmpty()) {
                logger.warn("  └─ No down operations defined")
            } else {
                // 의존성 분석 및 정렬 (Always-On)
                val sortedContexts = dependencyAnalyzer.analyzeAndSort(
                    migration.contexts,
                    forRollback = true
                )
                
                sortedContexts.forEach { context ->
                    context.resolve().forEach { sql ->
                        if (showSql) logger.info("  └─ $sql")
                        transaction.exec(sql)
                    }
                }
            }
            
            transaction.commit()
            
            // 마이그레이션 테이블에서 제거
            MigrationTable.deleteWhere { MigrationTable.filename eq filename }
            
            logger.info("  └─ ✅ Rolled back successfully")
        } catch (e: Exception) {
            logger.error("  └─ ❌ Rollback failed: ${e.message}")
            throw e
        }
    }
}

class RollbackException(message: String, cause: Throwable? = null) : Exception(message, cause)
class RollbackCancelledException(message: String) : Exception(message)