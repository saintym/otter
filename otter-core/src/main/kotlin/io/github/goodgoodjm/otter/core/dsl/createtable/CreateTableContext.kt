package io.github.goodgoodjm.otter.core.dsl.createtable

import io.github.goodgoodjm.otter.core.Logger
import io.github.goodgoodjm.otter.core.adapter.model.ColumnDefinition
import io.github.goodgoodjm.otter.core.adapter.model.ColumnModifier
import io.github.goodgoodjm.otter.core.adapter.model.TableDefinition
import io.github.goodgoodjm.otter.core.adapter.model.UniqueConstraint
import io.github.goodgoodjm.otter.core.adapter.model.ForeignKeyConstraint
import io.github.goodgoodjm.otter.core.adapter.model.CheckConstraint
import io.github.goodgoodjm.otter.core.dsl.SchemaContext
import io.github.goodgoodjm.otter.core.dsl.type.*
import io.github.goodgoodjm.otter.core.migration.MigrationContext
import io.github.goodgoodjm.otter.core.adapter.model.ColumnType as AdapterColumnType
import io.github.goodgoodjm.otter.core.adapter.model.ReferentialAction as AdapterReferentialAction

/**
 * CREATE TABLE 컨텍스트 - 불변 타입 시스템
 *
 * 불변 Column 객체를 사용하여 thread-safe 보장
 */
class CreateTableContext(val tableName: String) : SchemaContext {

    companion object : Logger

    private val columns = mutableMapOf<String, Column>()

    /**
     * Get foreign key references for dependency analysis
     */
    fun getReferencedTables(): Set<String> {
        return columns.values
            .mapNotNull { column -> column.references?.table }
            .toSet()
    }

    /**
     * DSL 연산자 - 컬럼 추가
     */
    operator fun String.minus(column: Column): Column {
        columns[this] = column
        return column
    }

    override fun resolve(): List<String> {
        val tableDefinition = buildTableDefinition()

        return try {
            val adapter = MigrationContext.getAdapter()
            val ddlProvider = adapter.getDDLProvider()

            val statements = ddlProvider.createTable(tableDefinition)
            logger.debug("Generated CREATE TABLE SQL for '$tableName': $statements")
            statements
        } catch (e: Exception) {
            logger.error("Failed to generate CREATE TABLE SQL", e)
            throw e
        }
    }

    private fun buildTableDefinition(): TableDefinition {
        val columnDefinitions = mutableListOf<ColumnDefinition>()
        val primaryKeys = mutableListOf<String>()
        val uniqueConstraints = mutableListOf<UniqueConstraint>()
        val foreignKeys = mutableListOf<ForeignKeyConstraint>()
        val checkConstraints = mutableListOf<CheckConstraint>()

        columns.forEach { (name, column) ->
            // 컬럼 타입 변환
            val columnType = convertColumnType(column.type)
            val modifiers = convertModifiers(column)

            columnDefinitions.add(
                ColumnDefinition(
                    name = name,
                    type = columnType,
                    modifiers = modifiers,
                    defaultValue = convertDefaultValue(column.defaultValue)
                )
            )

            // Primary Key 수집
            if (column.constraints.any { it is io.github.goodgoodjm.otter.core.dsl.Constraint.PRIMARY }) {
                primaryKeys.add(name)
            }

            // Unique 제약조건
            if (column.constraints.any { it is io.github.goodgoodjm.otter.core.dsl.Constraint.UNIQUE }) {
                uniqueConstraints.add(
                    UniqueConstraint(
                        name = "${tableName}_${name}_unique",
                        columns = listOf(name)
                    )
                )
            }

            // Foreign Key 처리
            column.references?.let { ref ->
                foreignKeys.add(
                    ForeignKeyConstraint(
                        name = "${tableName}_${name}_fk",
                        tableName = tableName,
                        columns = listOf(name),
                        referencedTable = ref.table,
                        referencedColumns = listOf(ref.column),
                        onDelete = convertReferentialAction(ref.onDelete),
                        onUpdate = convertReferentialAction(ref.onUpdate)
                    )
                )
            }

            // Check 제약조건
            column.constraints.filterIsInstance<io.github.goodgoodjm.otter.core.dsl.Constraint.CHECK>()
                .forEach { check ->
                    checkConstraints.add(
                        CheckConstraint(
                            name = "${tableName}_${name}_check",
                            expression = check.condition
                        )
                    )
                }
        }

        return TableDefinition(
            name = tableName,
            columns = columnDefinitions,
            primaryKeys = primaryKeys,
            uniqueConstraints = uniqueConstraints,
            foreignKeys = foreignKeys,
            checkConstraints = checkConstraints
        )
    }

    private fun convertColumnType(type: ColumnType): AdapterColumnType {
        return when (type) {
            is ColumnType.TinyInt -> AdapterColumnType.Integer
            is ColumnType.SmallInt -> AdapterColumnType.SmallInt
            is ColumnType.Integer -> AdapterColumnType.Integer
            is ColumnType.BigInt -> AdapterColumnType.BigInt
            is ColumnType.Decimal -> AdapterColumnType.Decimal(type.precision, type.scale)
            is ColumnType.Float -> AdapterColumnType.Float
            is ColumnType.Double -> AdapterColumnType.Double
            is ColumnType.Char -> AdapterColumnType.Char(type.length)
            is ColumnType.Varchar -> AdapterColumnType.Varchar(type.length)
            is ColumnType.Text -> AdapterColumnType.Text
            is ColumnType.Date -> AdapterColumnType.Date
            is ColumnType.Time -> AdapterColumnType.Time
            is ColumnType.DateTime -> AdapterColumnType.Timestamp
            is ColumnType.Timestamp -> AdapterColumnType.Timestamp
            is ColumnType.Boolean -> AdapterColumnType.Boolean
            is ColumnType.Uuid -> AdapterColumnType.Uuid
            is ColumnType.Json -> AdapterColumnType.Json
            is ColumnType.Blob -> AdapterColumnType.Blob
            is ColumnType.Custom -> AdapterColumnType.Text  // Fallback
        }
    }

    private fun convertModifiers(column: Column): Set<ColumnModifier> {
        val modifiers = mutableSetOf<ColumnModifier>()

        column.constraints.forEach { constraint ->
            when (constraint) {
                is io.github.goodgoodjm.otter.core.dsl.Constraint.PRIMARY ->
                    modifiers.add(ColumnModifier.PRIMARY_KEY)
                is io.github.goodgoodjm.otter.core.dsl.Constraint.NOT_NULL ->
                    modifiers.add(ColumnModifier.NOT_NULL)
                is io.github.goodgoodjm.otter.core.dsl.Constraint.UNIQUE ->
                    modifiers.add(ColumnModifier.UNIQUE)
                is io.github.goodgoodjm.otter.core.dsl.Constraint.AUTO_INCREMENT ->
                    modifiers.add(ColumnModifier.AUTO_INCREMENT)
                else -> {}  // 다른 constraint는 별도 처리
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

    private fun convertReferentialAction(action: ReferentialAction): AdapterReferentialAction {
        return when (action) {
            ReferentialAction.CASCADE -> AdapterReferentialAction.CASCADE
            ReferentialAction.RESTRICT -> AdapterReferentialAction.RESTRICT
            ReferentialAction.SET_NULL -> AdapterReferentialAction.SET_NULL
            ReferentialAction.SET_DEFAULT -> AdapterReferentialAction.SET_DEFAULT
            ReferentialAction.NO_ACTION -> AdapterReferentialAction.NO_ACTION
        }
    }
}

/**
 * DSL 빌더 함수
 */
fun createTable(tableName: String, init: CreateTableContext.() -> Unit): List<String> {
    val context = CreateTableContext(tableName)
    context.init()
    return context.resolve()
}

/**
 * 사용 예제:
 *
 * createTableV2("users") {
 *     "id" - serial()
 *     "email" - varchar(255).notNull().unique()
 *     "name" - varchar(100)
 *     "bio" - text().defaultNull()
 *     "created_at" - timestamp().defaultCurrentTimestamp()
 *     "parent_id" - bigint().references("users", "id")
 * }
 */