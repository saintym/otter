package io.github.goodgoodjm.otter.core.dsl.altertable

import io.github.goodgoodjm.otter.core.Logger
import io.github.goodgoodjm.otter.core.adapter.model.ColumnDefinition
import io.github.goodgoodjm.otter.core.adapter.model.ColumnModifier
import io.github.goodgoodjm.otter.core.adapter.model.ColumnType
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.type.*
import io.github.goodgoodjm.otter.core.migration.MigrationContext

/**
 * ALTER TABLE 컨텍스트
 *
 * Exposed 의존성 없이 DatabaseAdapter를 사용하여 테이블을 수정합니다.
 */
class AlterTableContext(val tableSchema: AlterTableSchema) : SchemaContext {

    companion object : Logger

    override fun resolve(): List<String> {
        logger.debug("AlterTableContext.resolve() called for table: ${tableSchema.name}")
        logger.debug("Operations count: ${tableSchema.operations.size}")

        return try {
            val adapter = MigrationContext.getAdapter()
            val ddlProvider = adapter.getDDLProvider()

            tableSchema.operations.mapNotNull { operation ->
                val sql = when (operation) {
                    is AddColumnOperation -> {
                        logger.debug("Processing ADD operation for column: ${operation.columnName}")
                        operation.column?.let { column ->
                            val columnDef = convertToColumnDefinition(operation.columnName, column)
                            ddlProvider.alterTableAddColumn(tableSchema.name, columnDef)
                        }
                    }
                    is ModifyColumnOperation -> {
                        logger.debug("Processing MODIFY operation for column: ${operation.columnName}")
                        operation.column?.let { column ->
                            val columnDef = convertToColumnDefinition(operation.columnName, column)
                            ddlProvider.alterTableModifyColumn(tableSchema.name, columnDef)
                        }
                    }
                    is DropColumnOperation -> {
                        logger.debug("Processing DROP operation for column: ${operation.columnName}")
                        ddlProvider.alterTableDropColumn(tableSchema.name, operation.columnName)
                    }
                }
                logger.debug("Generated SQL: $sql")
                sql
            }
        } catch (e: IllegalStateException) {
            // MigrationContext가 초기화되지 않은 경우 (테스트 등)
            logger.warn("MigrationContext not initialized, generating default SQL")
            generateDefaultSql()
        }
    }

    private fun convertToColumnDefinition(name: String, column: Column): ColumnDefinition {
        val columnType = convertColumnType(column)
        val modifiers = convertModifiers(column)
        val defaultValue = convertDefaultValue(column.defaultValue)
        return ColumnDefinition(name, columnType, modifiers, defaultValue)
    }

    private fun convertColumnType(column: Column): ColumnType {
        return when (val type = column.type) {
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.TinyInt -> ColumnType.Integer
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.SmallInt -> ColumnType.SmallInt
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Integer -> ColumnType.Integer
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.BigInt -> ColumnType.BigInt
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Decimal -> ColumnType.Decimal(type.precision, type.scale)
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Float -> ColumnType.Float
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Double -> ColumnType.Double
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Char -> ColumnType.Char(type.length)
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Varchar -> ColumnType.Varchar(type.length)
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Text -> ColumnType.Text
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Date -> ColumnType.Date
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Time -> ColumnType.Time
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.DateTime -> ColumnType.Timestamp
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Timestamp -> ColumnType.Timestamp
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Boolean -> ColumnType.Boolean
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Uuid -> ColumnType.Uuid
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Json -> ColumnType.Json
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Blob -> ColumnType.Blob
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Custom -> ColumnType.Text
        }
    }

    private fun convertModifiers(column: Column): Set<ColumnModifier> {
        val modifiers = mutableSetOf<ColumnModifier>()

        column.constraints.forEach { constraint ->
            when (constraint) {
                is Constraint.PRIMARY -> modifiers.add(ColumnModifier.PRIMARY_KEY)
                is Constraint.NOT_NULL -> modifiers.add(ColumnModifier.NOT_NULL)
                is Constraint.UNIQUE -> modifiers.add(ColumnModifier.UNIQUE)
                is Constraint.AUTO_INCREMENT -> modifiers.add(ColumnModifier.AUTO_INCREMENT)
                is Constraint.NULLABLE -> {} // nullable은 NOT_NULL이 없으면 자동
                else -> {} // 다른 constraint는 별도 처리
            }
        }

        return modifiers
    }

    private fun convertDefaultValue(defaultValue: DefaultValue?): Any? {
        return when (defaultValue) {
            null -> null
            is DefaultValue.Null -> null
            is DefaultValue.Literal -> defaultValue.value
            is DefaultValue.CurrentTimestamp -> "CURRENT_TIMESTAMP"
            is DefaultValue.CurrentDate -> "CURRENT_DATE"
            is DefaultValue.CurrentTime -> "CURRENT_TIME"
            is DefaultValue.Expression -> defaultValue.sql
        }
    }

    private fun generateDefaultSql(): List<String> {
        return tableSchema.operations.mapNotNull { operation ->
            when (operation) {
                is AddColumnOperation -> {
                    operation.column?.let { column ->
                        val columnDef = buildColumnDefinitionString(operation.columnName, column)
                        "ALTER TABLE ${tableSchema.name} ADD COLUMN $columnDef"
                    }
                }
                is ModifyColumnOperation -> {
                    operation.column?.let { column ->
                        val columnDef = buildColumnDefinitionString(operation.columnName, column)
                        "ALTER TABLE ${tableSchema.name} ALTER COLUMN $columnDef"
                    }
                }
                is DropColumnOperation -> {
                    "ALTER TABLE ${tableSchema.name} DROP COLUMN ${operation.columnName}"
                }
            }
        }
    }

    private fun getConstraintString(constraint: Constraint): String {
        return when (constraint) {
            is Constraint.NOT_NULL -> "NOT NULL"
            is Constraint.UNIQUE -> "UNIQUE"
            is Constraint.PRIMARY -> "PRIMARY KEY"
            is Constraint.AUTO_INCREMENT -> "AUTO_INCREMENT"
            is Constraint.DEFAULT -> {
                val value = when (constraint.value) {
                    is String -> "'${constraint.value}'"
                    else -> constraint.value.toString()
                }
                "DEFAULT $value"
            }
            is Constraint.CHECK -> "CHECK (${constraint.condition})"
            is Constraint.REFERENCES -> {
                // REFERENCES는 column.references에서 처리하므로 여기서는 스킵
                ""
            }
            is Constraint.COLLATE -> "COLLATE ${constraint.collation}"
            is Constraint.COMMENT -> "COMMENT '${constraint.text}'"
            is Constraint.GENERATED -> {
                when (constraint.type) {
                    GenerationType.BY_DEFAULT -> "GENERATED BY DEFAULT AS IDENTITY"
                    GenerationType.ALWAYS -> "GENERATED ALWAYS AS IDENTITY"
                }
            }
            else -> ""
        }
    }

    private fun buildColumnDefinitionString(name: String, column: Column): String {
        val parts = mutableListOf<String>()
        parts.add(name)

        // 타입 변환
        val typeStr = getColumnTypeString(column.type)
        parts.add(typeStr)

        // foreignKey 참조 처리
        column.references?.let { ref ->
            var refStr = "REFERENCES ${ref.table}(${ref.column})"
            if (ref.onDelete != io.github.goodgoodjm.otter.core.dsl.type.ReferentialAction.RESTRICT) {
                refStr += " ON DELETE ${ref.onDelete}"
            }
            if (ref.onUpdate != io.github.goodgoodjm.otter.core.dsl.type.ReferentialAction.RESTRICT) {
                refStr += " ON UPDATE ${ref.onUpdate}"
            }
            parts.add(refStr)
        }

        // 제약조건 추가
        column.constraints.forEach { constraint ->
            when (constraint) {
                is ConstraintGroup -> {
                    // ConstraintGroup 내부의 constraint들을 재귀적으로 처리
                    constraint.constraints.forEach { innerConstraint ->
                        val constraintStr = getConstraintString(innerConstraint)
                        if (constraintStr.isNotEmpty()) {
                            parts.add(constraintStr)
                        }
                    }
                }
                is Constraint.NOT_NULL -> parts.add("NOT NULL")
                is Constraint.UNIQUE -> parts.add("UNIQUE")
                is Constraint.PRIMARY -> parts.add("PRIMARY KEY")
                is Constraint.AUTO_INCREMENT -> parts.add("AUTO_INCREMENT")
                is Constraint.DEFAULT -> {
                    val value = when (constraint.value) {
                        is String -> "'${constraint.value}'"
                        else -> constraint.value.toString()
                    }
                    parts.add("DEFAULT $value")
                }
                is Constraint.CHECK -> parts.add("CHECK (${constraint.condition})")
                is Constraint.REFERENCES -> {
                    // REFERENCES는 이미 column.references에서 처리되므로 여기서는 스킵
                    // 단, column.references가 없을 때만 처리
                    if (column.references == null) {
                        var refStr = "REFERENCES ${constraint.table}(${constraint.column})"
                        constraint.onDelete?.let { refStr += " ON DELETE $it" }
                        constraint.onUpdate?.let { refStr += " ON UPDATE $it" }
                        parts.add(refStr)
                    }
                }
                is Constraint.COLLATE -> parts.add("COLLATE ${constraint.collation}")
                is Constraint.COMMENT -> parts.add("COMMENT '${constraint.text}'")
                is Constraint.GENERATED -> {
                    val genStr = when (constraint.type) {
                        GenerationType.BY_DEFAULT -> "GENERATED BY DEFAULT AS IDENTITY"
                        GenerationType.ALWAYS -> "GENERATED ALWAYS AS IDENTITY"
                    }
                    if (genStr.isNotEmpty()) parts.add(genStr)
                }
                else -> {} // 기타 제약조건은 무시
            }
        }

        // Foreign key 처리
        column.references?.let { ref ->
            parts.add("REFERENCES ${ref.table}(${ref.column})")
        }

        // Default value 처리
        column.defaultValue?.let { default ->
            val defaultStr = when (default) {
                is DefaultValue.Null -> "DEFAULT NULL"
                is DefaultValue.Literal -> "DEFAULT '${default.value}'"
                is DefaultValue.CurrentTimestamp -> "DEFAULT CURRENT_TIMESTAMP"
                is DefaultValue.CurrentDate -> "DEFAULT CURRENT_DATE"
                is DefaultValue.CurrentTime -> "DEFAULT CURRENT_TIME"
                is DefaultValue.Expression -> "DEFAULT ${default.sql}"
            }
            parts.add(defaultStr)
        }

        return parts.joinToString(" ")
    }

    private fun getColumnTypeString(type: io.github.goodgoodjm.otter.core.dsl.type.ColumnType): String {
        return when (type) {
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.TinyInt -> "TINYINT"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.SmallInt -> "SMALLINT"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Integer -> "INT"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.BigInt -> "BIGINT"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Decimal -> "DECIMAL(${type.precision}, ${type.scale})"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Float -> "FLOAT"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Double -> "DOUBLE"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Char -> "CHAR(${type.length})"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Varchar -> "VARCHAR(${type.length})"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Text -> "TEXT"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Date -> "DATE"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Time -> "TIME"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.DateTime -> "DATETIME"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Timestamp -> "TIMESTAMP"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Boolean -> "BOOLEAN"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Uuid -> "UUID"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Json -> "JSON"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Blob -> "BLOB"
            is io.github.goodgoodjm.otter.core.dsl.type.ColumnType.Custom -> type.sqlType
        }
    }
}