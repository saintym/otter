package io.github.goodgoodjm.otter.core.adapter

import io.github.goodgoodjm.otter.core.adapter.ddl.DDLProvider
import io.github.goodgoodjm.otter.core.adapter.model.*
import io.github.goodgoodjm.otter.core.adapter.type.DefaultValue
import io.github.goodgoodjm.otter.core.adapter.type.TypeMapper

/**
 * 테스트용 실제 H2 DDL Provider.
 *
 * DSL이 생성한 TableDefinition/ColumnDefinition을 H2가 실행 가능한 SQL로 변환한다.
 * (PRIMARY KEY / UNIQUE는 테이블 레벨 제약으로만 선언하여 중복을 피한다)
 */
class H2DDLProvider : DDLProvider {

    override fun createTable(table: TableDefinition): List<String> {
        val statements = mutableListOf<String>()

        val columns = table.columns.joinToString(",\n    ") { buildColumnDefinition(it) }

        val pk = if (table.primaryKeys.isNotEmpty())
            ",\n    CONSTRAINT ${table.name}_pkey PRIMARY KEY (${table.primaryKeys.joinToString(", ")})" else ""

        val fks = table.foreignKeys.joinToString("") { fk ->
            ",\n    CONSTRAINT ${fk.name} FOREIGN KEY (${fk.columns.joinToString(", ")}) " +
                "REFERENCES ${fk.referencedTable} (${fk.referencedColumns.joinToString(", ")})"
        }

        val uniques = table.uniqueConstraints.joinToString("") { uc ->
            ",\n    CONSTRAINT ${uc.name} UNIQUE (${uc.columns.joinToString(", ")})"
        }

        val checks = table.checkConstraints.joinToString("") { cc ->
            ",\n    CONSTRAINT ${cc.name} CHECK (${cc.expression})"
        }

        statements.add("CREATE TABLE IF NOT EXISTS ${table.name} (\n    $columns$pk$fks$uniques$checks\n)")
        table.indexes.forEach { statements.add(createIndex(table.name, it)) }
        return statements
    }

    private fun buildColumnDefinition(col: ColumnDefinition): String {
        val sb = StringBuilder()
        sb.append(col.name).append(' ').append(mapType(col.type))
        if (ColumnModifier.AUTO_INCREMENT in col.modifiers) sb.append(" AUTO_INCREMENT")
        if (ColumnModifier.NOT_NULL in col.modifiers || ColumnModifier.PRIMARY_KEY in col.modifiers) sb.append(" NOT NULL")
        col.defaultValue?.let { sb.append(" DEFAULT ").append(renderDefault(it)) }
        return sb.toString()
    }

    override fun alterTable(tableName: String, alterations: List<TableAlteration>): List<String> {
        return alterations.mapNotNull { alteration ->
            when (alteration) {
                is TableAlteration.AddColumn -> alterTableAddColumn(tableName, alteration.column)
                is TableAlteration.DropColumn -> alterTableDropColumn(tableName, alteration.columnName)
                is TableAlteration.RenameColumn -> alterTableRenameColumn(tableName, alteration.oldName, alteration.newName)
                else -> null
            }
        }
    }

    override fun dropTable(tableName: String, cascade: Boolean): String =
        "DROP TABLE IF EXISTS $tableName" + if (cascade) " CASCADE" else ""

    override fun alterTableAddColumn(tableName: String, column: ColumnDefinition): String =
        "ALTER TABLE $tableName ADD COLUMN ${buildColumnDefinition(column)}"

    override fun alterTableModifyColumn(tableName: String, column: ColumnDefinition): String =
        "ALTER TABLE $tableName ALTER COLUMN ${buildColumnDefinition(column)}"

    override fun alterTableDropColumn(tableName: String, columnName: String, cascade: Boolean): String =
        "ALTER TABLE $tableName DROP COLUMN $columnName"

    override fun alterTableRenameColumn(tableName: String, oldName: String, newName: String): String =
        "ALTER TABLE $tableName ALTER COLUMN $oldName RENAME TO $newName"

    override fun alterTableAddPrimaryKey(tableName: String, columns: List<String>): String =
        "ALTER TABLE $tableName ADD PRIMARY KEY (${columns.joinToString(", ")})"

    override fun alterTableDropPrimaryKey(tableName: String): String =
        "ALTER TABLE $tableName DROP PRIMARY KEY"

    override fun alterTableAddForeignKey(tableName: String, foreignKey: ForeignKeyConstraint): String =
        "ALTER TABLE $tableName ADD CONSTRAINT ${foreignKey.name} FOREIGN KEY (${foreignKey.columns.joinToString(", ")}) " +
            "REFERENCES ${foreignKey.referencedTable} (${foreignKey.referencedColumns.joinToString(", ")})"

    override fun alterTableDropForeignKey(tableName: String, constraintName: String): String =
        "ALTER TABLE $tableName DROP CONSTRAINT $constraintName"

    override fun createIndex(tableName: String, index: IndexDefinition): String {
        val unique = if (index.unique) "UNIQUE " else ""
        return "CREATE ${unique}INDEX ${index.name} ON $tableName (${index.columns.joinToString(", ")})"
    }

    override fun dropIndex(indexName: String, tableName: String?): String =
        "DROP INDEX IF EXISTS $indexName"

    override fun addForeignKey(constraint: ForeignKeyConstraint): String =
        alterTableAddForeignKey(constraint.tableName, constraint)

    override fun dropForeignKey(constraintName: String, tableName: String): String =
        "ALTER TABLE $tableName DROP CONSTRAINT $constraintName"

    override fun tableExists(tableName: String, schema: String?): String =
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = UPPER('$tableName')"

    override fun getTableColumns(tableName: String, schema: String?): String =
        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = UPPER('$tableName')"

    override fun renameTable(oldName: String, newName: String): String =
        "ALTER TABLE $oldName RENAME TO $newName"

    override fun addColumnComment(tableName: String, columnName: String, comment: String): String =
        "COMMENT ON COLUMN $tableName.$columnName IS '${comment.replace("'", "''")}'"

    override fun addTableComment(tableName: String, comment: String): String =
        "COMMENT ON TABLE $tableName IS '${comment.replace("'", "''")}'"

    private fun mapType(type: ColumnType): String = when (type) {
        is ColumnType.SmallInt -> "SMALLINT"
        is ColumnType.Integer -> "INT"
        is ColumnType.BigInt -> "BIGINT"
        is ColumnType.Decimal -> "DECIMAL(${type.precision}, ${type.scale})"
        is ColumnType.Float -> "REAL"
        is ColumnType.Double -> "DOUBLE PRECISION"
        is ColumnType.Char -> "CHAR(${type.length})"
        is ColumnType.Varchar -> "VARCHAR(${type.length})"
        is ColumnType.Text -> "VARCHAR"
        is ColumnType.Date -> "DATE"
        is ColumnType.Time -> "TIME"
        is ColumnType.Timestamp -> "TIMESTAMP"
        is ColumnType.Boolean -> "BOOLEAN"
        is ColumnType.Uuid -> "UUID"
        is ColumnType.Json -> "VARCHAR"
        is ColumnType.Blob -> "BLOB"
        else -> "VARCHAR"
    }

    private fun renderDefault(value: Any): String = when (value) {
        is Boolean -> if (value) "TRUE" else "FALSE"
        is Number -> value.toString()
        is String -> {
            val t = value.trim()
            if (t.uppercase() in NON_LITERAL_DEFAULTS || FUNCTION_CALL.matches(t)) t
            else "'${t.replace("'", "''")}'"
        }
        else -> "'$value'"
    }

    companion object {
        private val NON_LITERAL_DEFAULTS = setOf(
            "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "TRUE", "FALSE", "NULL"
        )
        private val FUNCTION_CALL = Regex("^[A-Za-z_][A-Za-z0-9_.]*\\s*\\(.*\\)$")
    }
}

/**
 * 테스트용 실제 H2 TypeMapper.
 */
class H2TypeMapper : TypeMapper {
    override fun mapType(type: ColumnType): String = when (type) {
        is ColumnType.SmallInt -> "SMALLINT"
        is ColumnType.Integer -> "INT"
        is ColumnType.BigInt -> "BIGINT"
        is ColumnType.Decimal -> "DECIMAL(${type.precision}, ${type.scale})"
        is ColumnType.Float -> "REAL"
        is ColumnType.Double -> "DOUBLE PRECISION"
        is ColumnType.Char -> "CHAR(${type.length})"
        is ColumnType.Varchar -> "VARCHAR(${type.length})"
        is ColumnType.Text -> "VARCHAR"
        is ColumnType.Date -> "DATE"
        is ColumnType.Time -> "TIME"
        is ColumnType.Timestamp -> "TIMESTAMP"
        is ColumnType.Boolean -> "BOOLEAN"
        is ColumnType.Uuid -> "UUID"
        is ColumnType.Json -> "VARCHAR"
        is ColumnType.Blob -> "BLOB"
        else -> "VARCHAR"
    }

    override fun mapTypeWithModifiers(type: ColumnType, modifiers: Set<ColumnModifier>): String {
        val base = mapType(type)
        return if (ColumnModifier.AUTO_INCREMENT in modifiers) "$base AUTO_INCREMENT" else base
    }

    override fun supportsType(type: ColumnType): Boolean = true

    override fun getDefaultValueExpression(value: DefaultValue): String = when (value) {
        is DefaultValue.Null -> "NULL"
        is DefaultValue.Literal -> getSqlLiteral(value.value)
        is DefaultValue.CurrentTimestamp -> "CURRENT_TIMESTAMP"
        is DefaultValue.CurrentDate -> "CURRENT_DATE"
        is DefaultValue.CurrentTime -> "CURRENT_TIME"
        is DefaultValue.Expression -> value.sql
    }

    override fun getSqlLiteral(value: Any?): String = when (value) {
        null -> "NULL"
        is Boolean -> if (value) "TRUE" else "FALSE"
        is Number -> value.toString()
        else -> "'${value.toString().replace("'", "''")}'"
    }
}
