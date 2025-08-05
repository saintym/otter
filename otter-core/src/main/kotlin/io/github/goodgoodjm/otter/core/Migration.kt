package io.github.goodgoodjm.otter.core

import io.github.goodgoodjm.otter.core.dsl.SchemaContext
import io.github.goodgoodjm.otter.core.dsl.SchemaMaker
import io.github.goodgoodjm.otter.core.dsl.createtable.CreateTableContext
import io.github.goodgoodjm.otter.core.dsl.createtable.TableSchema
import io.github.goodgoodjm.otter.core.dsl.altertable.AlterTableContext
import io.github.goodgoodjm.otter.core.dsl.altertable.AlterTableSchema
import org.jetbrains.exposed.sql.Table

abstract class Migration {
    companion object : Logger

    val contexts: List<SchemaContext> get() = _contexts
    private val _contexts = mutableListOf<SchemaContext>()

    open val comment: String = ""

    abstract fun up()
    abstract fun down()


    @SchemaMaker
    fun createTable(name: String, block: TableSchema.() -> Unit) {
        val tableSchema = TableSchema(name).apply(block)
        val table = CreateTableContext(tableSchema)
        _contexts.add(table)
    }

    fun dropTable(name: String) {
        _contexts.add(object : SchemaContext {
            override fun resolve(): List<String> = Table(name).dropStatement()
        })
    }

    fun rawQuery(sql: String) {
        _contexts.add(object : SchemaContext {
            override fun resolve(): List<String> = listOf(sql)
        })
    }

    @SchemaMaker
    fun alterTable(name: String, block: AlterTableSchema.() -> Unit) {
        val tableSchema = AlterTableSchema(name).apply(block)
        val context = AlterTableContext(tableSchema)
        _contexts.add(context)
    }
}