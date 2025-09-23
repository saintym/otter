package io.github.goodgoodjm.otter.core.adapter.ddl

import io.github.goodgoodjm.otter.core.adapter.model.*

/**
 * Provides DDL (Data Definition Language) operations for database schema management
 */
interface DDLProvider {
    /**
     * Generate SQL statements to create a table
     */
    fun createTable(table: TableDefinition): List<String>

    /**
     * Generate SQL statements to alter a table
     */
    fun alterTable(tableName: String, alterations: List<TableAlteration>): List<String>

    /**
     * Generate SQL to drop a table
     */
    fun dropTable(tableName: String, cascade: Boolean = false): String

    /**
     * Generate SQL to create an index
     */
    fun createIndex(index: IndexDefinition): String

    /**
     * Generate SQL to drop an index
     */
    fun dropIndex(indexName: String, tableName: String? = null): String

    /**
     * Generate SQL to add a foreign key constraint
     */
    fun addForeignKey(constraint: ForeignKeyConstraint): String

    /**
     * Generate SQL to drop a foreign key constraint
     */
    fun dropForeignKey(constraintName: String, tableName: String): String

    /**
     * Generate SQL to check if a table exists
     */
    fun tableExists(tableName: String, schema: String? = null): String

    /**
     * Generate SQL to get table columns information
     */
    fun getTableColumns(tableName: String, schema: String? = null): String

    /**
     * Generate SQL to rename a table
     */
    fun renameTable(oldName: String, newName: String): String

    /**
     * Generate SQL to add a column comment
     */
    fun addColumnComment(tableName: String, columnName: String, comment: String): String?

    /**
     * Generate SQL to add a table comment
     */
    fun addTableComment(tableName: String, comment: String): String?
}