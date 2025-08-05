package io.github.goodgoodjm.otter.core.dsl.createtable

import io.github.goodgoodjm.otter.core.Logger
import io.github.goodgoodjm.otter.core.dsl.Constraint
import io.github.goodgoodjm.otter.core.dsl.SchemaContext
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

class CreateTableContext constructor(val tableSchema: TableSchema) : SchemaContext {
    override fun resolve(): List<String> {
        return DynamicPrimaryKeyTable.with(tableSchema).resolve()
    }
}

class DynamicPrimaryKeyTable(name: String) : Table(name) {
    companion object : Logger {
        val REGEX = """([\w]+)\(([\w]+)\)""".toRegex()

        fun with(tableSchema: TableSchema): DynamicPrimaryKeyTable = DynamicPrimaryKeyTable(tableSchema.name).apply {
            tableSchema.columnSchemaMap.map { (key, value) -> addColumn(key, value) }
        }
    }

    private var primaryKeys: Array<Column<*>> = arrayOf()

    override val primaryKey: PrimaryKey?
        get() = if (primaryKeys.isEmpty()) {
            null
        } else {
            PrimaryKey(primaryKeys)
        }


    private fun addColumn(name: String, columnSchema: ColumnSchema) {
        var column = registerColumn<Comparable<Any>>(name, columnSchema.columnType)
        columnSchema.constraints.forEach { constraint ->
            when (constraint) {
                is Constraint.PRIMARY, is Constraint.NOT_NULL -> null
                is Constraint.NULLABLE -> column.columnType.nullable = true
                is Constraint.AUTO_INCREMENT -> column = column.autoIncrement()
                is Constraint.UNIQUE -> column = column.uniqueIndex()
                is Constraint.DEFAULT -> {}
                is Constraint.CHECK -> {}
                is Constraint.REFERENCES -> {}
                is Constraint.GENERATED -> {}
                is Constraint.COLLATE -> {}
                is Constraint.COMMENT -> {}
                is Constraint.NONE -> null
            }
        }

        columnSchema.foreignKey?.let { expression ->
            val result = REGEX.find(expression) ?: throw Exception("Wrong foreignKey expression.")
            val (tableName, columnName) = result.destructured
            val target = Table(tableName).registerColumn<Comparable<Any>>(columnName, column.columnType)
            column.references(target)
        }

        if (columnSchema.constraints.any { it is Constraint.PRIMARY }) {
            primaryKeys += column
        }
    }

    fun resolve(): List<String> = ddl + indices.flatMap { it.createStatement() }
}