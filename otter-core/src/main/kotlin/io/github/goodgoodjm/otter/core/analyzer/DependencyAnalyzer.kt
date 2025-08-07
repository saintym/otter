package io.github.goodgoodjm.otter.core.analyzer

import io.github.goodgoodjm.otter.core.dsl.SchemaContext
import io.github.goodgoodjm.otter.core.dsl.createtable.CreateTableContext
import io.github.goodgoodjm.otter.core.dsl.altertable.AlterTableContext
import io.github.goodgoodjm.otter.core.dsl.altertable.AddColumnOperation
import io.github.goodgoodjm.otter.core.dsl.droptable.DropTableContext
import io.github.goodgoodjm.otter.core.Logger

data class TableDependency(
    val tableName: String,
    val dependsOn: Set<String> = emptySet(),
    val operation: OperationType
)

enum class OperationType {
    CREATE, ALTER, DROP
}

class CircularDependencyException(message: String) : Exception(message)

class DependencyAnalyzer {
    companion object : Logger
    
    fun analyzeAndSort(
        contexts: List<SchemaContext>,
        forRollback: Boolean = false
    ): List<SchemaContext> {
        val dependencies = extractDependencies(contexts)
        val sortedOrder = topologicalSort(dependencies)
        
        return if (forRollback) {
            // 롤백 시 역순으로 정렬
            sortContextsByOrder(contexts, sortedOrder.reversed())
        } else {
            sortContextsByOrder(contexts, sortedOrder)
        }
    }
    
    private fun extractDependencies(contexts: List<SchemaContext>): Map<String, TableDependency> {
        val dependencies = mutableMapOf<String, TableDependency>()
        
        contexts.forEach { context ->
            when (context) {
                is CreateTableContext -> {
                    val tableName = context.tableSchema.name
                    val refs = extractTableReferences(context)
                    dependencies[tableName] = TableDependency(
                        tableName = tableName,
                        dependsOn = refs,
                        operation = OperationType.CREATE
                    )
                }
                is AlterTableContext -> {
                    val tableName = context.tableSchema.name
                    val refs = extractAlterReferences(context)
                    dependencies[tableName] = dependencies[tableName]?.copy(
                        dependsOn = dependencies[tableName]!!.dependsOn + refs
                    ) ?: TableDependency(
                        tableName = tableName,
                        dependsOn = refs,
                        operation = OperationType.ALTER
                    )
                }
                is DropTableContext -> {
                    val tableName = context.tableName
                    dependencies[tableName] = TableDependency(
                        tableName = tableName,
                        dependsOn = emptySet(),
                        operation = OperationType.DROP
                    )
                }
            }
        }
        
        return dependencies
    }
    
    private fun extractTableReferences(context: CreateTableContext): Set<String> {
        val tableName = context.tableSchema.name
        return context.tableSchema.columnSchemaMap.values
            .mapNotNull { column ->
                column.foreignKey?.let { fk ->
                    // "table(column)" -> "table"
                    val referencedTable = fk.substringBefore("(")
                    // 자기 참조는 제외
                    if (referencedTable == tableName) null else referencedTable
                }
            }
            .toSet()
    }
    
    private fun extractAlterReferences(context: AlterTableContext): Set<String> {
        return context.tableSchema.operations
            .filterIsInstance<AddColumnOperation>()
            .mapNotNull { operation ->
                operation.columnSchema?.foreignKey?.let { fk ->
                    fk.substringBefore("(")
                }
            }
            .toSet()
    }
    
    private fun topologicalSort(dependencies: Map<String, TableDependency>): List<String> {
        val sorted = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        
        fun visit(table: String, path: List<String> = emptyList()) {
            if (table in visiting) {
                val cycle = path.dropWhile { it != table } + table
                throw CircularDependencyException(
                    "Circular dependency detected: ${cycle.joinToString(" → ")}"
                )
            }
            
            if (table in visited) return
            
            visiting.add(table)
            
            // 먼저 의존성을 방문
            dependencies[table]?.dependsOn?.forEach { dep ->
                // 의존하는 테이블이 dependencies에 존재하는 경우만 방문
                if (dep in dependencies) {
                    visit(dep, path + table)
                }
            }
            
            visiting.remove(table)
            visited.add(table)
            sorted.add(table)
        }
        
        // 모든 테이블을 방문
        dependencies.keys.forEach { table ->
            if (table !in visited) {
                visit(table)
            }
        }
        
        return sorted
    }
    
    private fun sortContextsByOrder(
        contexts: List<SchemaContext>,
        order: List<String>
    ): List<SchemaContext> {
        val contextMap = contexts.associateBy { context ->
            when (context) {
                is CreateTableContext -> context.tableSchema.name
                is AlterTableContext -> context.tableSchema.name
                is DropTableContext -> context.tableName
                else -> null
            }
        }
        
        val sorted = mutableListOf<SchemaContext>()
        
        // 정렬된 순서대로 추가
        order.forEach { tableName ->
            contextMap[tableName]?.let { sorted.add(it) }
        }
        
        // 테이블과 관련 없는 작업들은 마지막에 추가
        contexts.forEach { context ->
            if (context !in sorted) {
                sorted.add(context)
            }
        }
        
        // 순서가 변경되었으면 로그
        if (contexts != sorted) {
            logger.info("✅ Operations reordered for dependency safety")
            logger.debug("Original: ${contexts.map { getContextName(it) }.joinToString(" → ")}")
            logger.debug("Reordered: ${sorted.map { getContextName(it) }.joinToString(" → ")}")
        }
        
        return sorted
    }
    
    private fun getContextName(context: SchemaContext): String {
        return when (context) {
            is CreateTableContext -> "CREATE ${context.tableSchema.name}"
            is AlterTableContext -> "ALTER ${context.tableSchema.name}"
            is DropTableContext -> "DROP ${context.tableName}"
            else -> context.javaClass.simpleName
        }
    }
}