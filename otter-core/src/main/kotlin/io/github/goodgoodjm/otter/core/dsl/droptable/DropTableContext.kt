package io.github.goodgoodjm.otter.core.dsl.droptable

import io.github.goodgoodjm.otter.core.dsl.SchemaContext
import org.jetbrains.exposed.sql.Table

class DropTableContext(val tableName: String) : SchemaContext {
    override fun resolve(): List<String> {
        // 임시로 Exposed 사용, 추후 자체 SQL 생성으로 대체
        return Table(tableName).dropStatement()
    }
}