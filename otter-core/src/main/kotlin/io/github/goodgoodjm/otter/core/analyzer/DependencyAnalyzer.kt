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
        val sortedOrder = topologicalSort(dependencies, forRollback)

        return sortContextsByOrder(contexts, sortedOrder)
    }
    
    private fun extractDependencies(contexts: List<SchemaContext>): Map<String, TableDependency> {
        val dependencies = mutableMapOf<String, TableDependency>()
        
        contexts.forEach { context ->
            when (context) {
                is CreateTableContext -> {
                    val tableName = context.tableName
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
                    val existing = dependencies[tableName]
                    dependencies[tableName] = if (existing != null) {
                        existing.copy(dependsOn = existing.dependsOn + refs)
                    } else {
                        TableDependency(
                            tableName = tableName,
                            dependsOn = refs,
                            operation = OperationType.ALTER
                        )
                    }
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
        return context.getReferencedTables()
    }
    
    private fun extractAlterReferences(context: AlterTableContext): Set<String> {
        return context.tableSchema.operations
            .filterIsInstance<AddColumnOperation>()
            .mapNotNull { operation ->
                operation.column?.references?.let { ref ->
                    ref.table
                }
            }
            .toSet()
    }
    
    private fun topologicalSort(dependencies: Map<String, TableDependency>, forRollback: Boolean = false): List<String> {
        // DFS 기반 위상 정렬
        val sorted = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()

        fun visit(table: String) {
            if (table in visited) return

            if (table in visiting) {
                // 순환 의존성 감지
                val cycle = mutableListOf<String>()
                var current = table
                cycle.add(current)

                // 간단한 순환 경로 표시
                throw CircularDependencyException(
                    "Circular dependency detected involving table: $table"
                )
            }

            visiting.add(table)

            // 이 테이블이 의존하는 테이블들을 먼저 방문
            dependencies[table]?.dependsOn?.forEach { dep ->
                // 자기 참조는 순환 의존성이 아님
                if (dep != table && dependencies.containsKey(dep)) {
                    visit(dep)
                }
            }

            visiting.remove(table)
            visited.add(table)
            sorted.add(table)
        }

        // 모든 테이블에 대해 DFS 수행
        dependencies.keys.forEach { table ->
            visit(table)
        }

        // 롤백 시에는 역순으로
        return if (forRollback) {
            sorted.reversed()
        } else {
            sorted
        }
    }
    
    private fun sortContextsByOrder(
        contexts: List<SchemaContext>,
        order: List<String>
    ): List<SchemaContext> {
        val contextMap = contexts.associateBy { context ->
            when (context) {
                is CreateTableContext -> context.tableName
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
            is CreateTableContext -> "CREATE ${context.tableName}"
            is AlterTableContext -> "ALTER ${context.tableSchema.name}"
            is DropTableContext -> "DROP ${context.tableName}"
            else -> context.javaClass.simpleName
        }
    }
}