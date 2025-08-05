package io.github.goodgoodjm.otter.core.dsl.createtable

import io.github.goodgoodjm.otter.core.dsl.Constraint
import io.github.goodgoodjm.otter.core.dsl.SchemaMaker
import io.github.goodgoodjm.otter.core.dsl.type.CustomColumnType
import org.jetbrains.exposed.sql.ColumnType

class TableSchema(val name: String) {
    val columnSchemaMap: Map<String, ColumnSchema> get() = _columnSchemaMap
    private val _columnSchemaMap = mutableMapOf<String, ColumnSchema>()

    private fun pair(key: String, value: ColumnSchema) {
        _columnSchemaMap[key] = value
    }

    operator fun String.minus(value: ColumnSchema): ColumnSchema {
        pair(this, value)
        return value
    }
}

data class ColumnSchema(
    val columnType: ColumnType,
    var constraints: List<Constraint> = listOf(),
    var foreignKey: String? = null,
) {
    constructor (type: String, constraints: List<Constraint> = listOf()) : this(CustomColumnType(type), constraints)
}


@SchemaMaker
infix fun ColumnSchema.constraints(constraint: Constraint): ColumnSchema {
    constraints += constraint
    return this
}

@SchemaMaker
infix fun ColumnSchema.and(constraint: Constraint): ColumnSchema = constraints(constraint)

@SchemaMaker
infix fun ColumnSchema.constraints(constraintList: List<Constraint>): ColumnSchema {
    constraints += constraintList
    return this
}

@SchemaMaker  
infix fun Constraint.and(constraint: Constraint): List<Constraint> = listOf(this, constraint)

@SchemaMaker
infix fun List<Constraint>.and(constraint: Constraint): List<Constraint> = this + constraint

@SchemaMaker
infix fun ColumnSchema.foreignKey(value: String): ColumnSchema {
    require(value.isNotEmpty())
    foreignKey = value
    return this
}
