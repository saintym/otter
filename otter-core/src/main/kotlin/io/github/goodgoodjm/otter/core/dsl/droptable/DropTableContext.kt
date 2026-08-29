package io.github.goodgoodjm.otter.core.dsl.droptable

import io.github.goodgoodjm.otter.core.Logger
import io.github.goodgoodjm.otter.core.dsl.SchemaContext
import io.github.goodgoodjm.otter.core.migration.MigrationContext

/**
 * DROP TABLE 컨텍스트
 *
 * Exposed 의존성 없이 DatabaseAdapter를 사용하여 테이블을 삭제합니다.
 */
class DropTableContext(val tableName: String) : SchemaContext {

    companion object : Logger

    override fun resolve(): List<String> {
        return try {
            val adapter = MigrationContext.getAdapter()
            val ddlProvider = adapter.getDDLProvider()

            val sql = ddlProvider.dropTable(tableName)
            logger.debug("Generated DROP TABLE SQL for '$tableName': $sql")
            listOf(sql)
        } catch (e: IllegalStateException) {
            // MigrationContext가 초기화되지 않은 경우 (테스트 등)
            logger.warn("MigrationContext not initialized, generating default SQL")
            listOf("DROP TABLE IF EXISTS $tableName")
        }
    }
}