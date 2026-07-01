package io.github.goodgoodjm.otter.core.migration

import io.github.goodgoodjm.otter.core.adapter.DatabaseAdapter
import io.github.goodgoodjm.otter.core.adapter.connection.TransactionContext
import java.time.LocalDateTime

/**
 * 마이그레이션 추적 관리
 *
 * otter_migration 테이블을 관리하며 실행된 마이그레이션을 추적합니다.
 */
class MigrationTracker(
    private val adapter: DatabaseAdapter
) {
    companion object {
        const val MIGRATION_TABLE = "otter_migration"
    }

    /**
     * 마이그레이션 추적 테이블 초기화
     *
     * CREATE TABLE IF NOT EXISTS로 멱등하게 생성한다. 존재 여부를
     * information_schema로 별도 조회하면 스키마를 구분하지 못해
     * 다른 스키마의 동명 테이블을 오탐할 수 있으므로 사용하지 않는다.
     */
    fun initialize(context: TransactionContext) {
        createMigrationTable(context)
    }

    /**
     * 적용된 마이그레이션 목록 조회
     */
    fun getAppliedMigrations(context: TransactionContext): List<String> {
        val sql = "SELECT filename FROM $MIGRATION_TABLE ORDER BY created_at"
        return context.query(sql) { rs ->
            rs.getString("filename")
        }
    }

    /**
     * 마이그레이션 기록
     */
    fun recordMigration(name: String, context: TransactionContext) {
        val sql = buildInsertSql()
        context.execute(sql, listOf(
            name,
            "", // comment는 Migration 객체에서 가져와야 하지만 일단 빈 값
            LocalDateTime.now()
        ))
    }

    /**
     * 마이그레이션 기록 삭제 (롤백 시)
     */
    fun removeMigration(name: String, context: TransactionContext) {
        val sql = "DELETE FROM $MIGRATION_TABLE WHERE filename = ?"
        context.execute(sql, listOf(name))
    }

    /**
     * 마이그레이션 테이블 생성
     */
    private fun createMigrationTable(context: TransactionContext) {
        // DB별로 다른 ID 타입 사용
        val idColumnSql = when (adapter.name.lowercase()) {
            "postgresql" -> "id SERIAL PRIMARY KEY"
            "mysql", "mariadb" -> "id INT AUTO_INCREMENT PRIMARY KEY"
            "sqlite" -> "id INTEGER PRIMARY KEY AUTOINCREMENT"
            "h2" -> "id INT AUTO_INCREMENT PRIMARY KEY"
            else -> "id INT AUTO_INCREMENT PRIMARY KEY"
        }

        // DB별 타임스탬프 기본값
        val timestampDefault = when (adapter.name.lowercase()) {
            "postgresql" -> "DEFAULT CURRENT_TIMESTAMP"
            "mysql", "mariadb" -> "DEFAULT CURRENT_TIMESTAMP"
            "sqlite" -> "DEFAULT CURRENT_TIMESTAMP"
            "h2" -> "DEFAULT CURRENT_TIMESTAMP"
            else -> ""
        }

        val sql = """
            CREATE TABLE IF NOT EXISTS $MIGRATION_TABLE (
                $idColumnSql,
                filename VARCHAR(255) NOT NULL UNIQUE,
                comment VARCHAR(255),
                created_at TIMESTAMP NOT NULL $timestampDefault
            )
        """.trimIndent()

        context.execute(sql)
    }

    /**
     * INSERT SQL 생성
     *
     * 삽입된 id를 사용하지 않으므로 RETURNING 절은 두지 않는다.
     * (RETURNING을 붙이면 결과셋이 반환되어 executeUpdate 경로에서 오류가 발생한다)
     */
    private fun buildInsertSql(): String {
        return """
            INSERT INTO $MIGRATION_TABLE (filename, comment, created_at)
            VALUES (?, ?, ?)
        """.trimIndent()
    }
}