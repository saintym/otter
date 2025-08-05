package io.github.goodgoodjm.otter.core.dsl.altertable

import io.github.goodgoodjm.otter.core.dsl.createtable.ColumnSchema

data class AlterTableSchema(
    val name: String,
) {
    val operations = mutableListOf<AlterOperation>()
    
    fun add(columnName: String) = AddColumnOperation(columnName).also { 
        operations.add(it) 
    }
    
    fun modify(columnName: String) = ModifyColumnOperation(columnName).also { 
        operations.add(it) 
    }
    
    fun drop(columnName: String) {
        operations.add(DropColumnOperation(columnName))
    }
}

sealed class AlterOperation(val columnName: String)

class AddColumnOperation(columnName: String) : AlterOperation(columnName) {
    var columnSchema: ColumnSchema? = null
    
    operator fun minus(schema: ColumnSchema): ColumnSchema {
        columnSchema = schema
        return schema
    }
}

class ModifyColumnOperation(columnName: String) : AlterOperation(columnName) {
    var columnSchema: ColumnSchema? = null
    
    operator fun minus(schema: ColumnSchema): ColumnSchema {
        columnSchema = schema
        return schema
    }
}

class DropColumnOperation(columnName: String) : AlterOperation(columnName)
