package io.github.goodgoodjm.otter.core.adapter

import io.github.goodgoodjm.otter.core.adapter.ddl.DDLProvider
import io.github.goodgoodjm.otter.core.adapter.model.*

/**
 * Mock DDLProvider for testing
 */
class MockDDLProvider : DDLProvider {
    override fun createTable(table: TableDefinition): List<String> {
        val columns = table.columns.joinToString(", ") { col ->
            val parts = mutableListOf<String>()
            parts.add(col.name)
            parts.add(col.type.toString())

            if (col.modifiers.contains(ColumnModifier.PRIMARY_KEY)) parts.add("PRIMARY KEY")
            if (col.modifiers.contains(ColumnModifier.NOT_NULL)) parts.add("NOT NULL")
            if (col.modifiers.contains(ColumnModifier.UNIQUE)) parts.add("UNIQUE")
            if (col.modifiers.contains(ColumnModifier.AUTO_INCREMENT)) parts.add("AUTO_INCREMENT")
            col.defaultValue?.let { parts.add("DEFAULT $it") }

            parts.joinToString(" ")
        }

        val foreignKeys = table.foreignKeys.map { fk ->
            "FOREIGN KEY (${fk.columns.joinToString(", ")}) REFERENCES ${fk.referencedTable}(${fk.referencedColumns.joinToString(", ")})"
        }

        val allClauses = if (foreignKeys.isEmpty()) {
            columns
        } else {
            "$columns, ${foreignKeys.joinToString(", ")}"
        }

        return listOf("CREATE TABLE ${table.name} ($allClauses)")
    }

    override fun alterTable(tableName: String, alterations: List<TableAlteration>): List<String> {
        return alterations.map { "ALTER TABLE $tableName MOCK" }
    }

    override fun dropTable(tableName: String, cascade: Boolean): String {
        return "DROP TABLE $tableName" + if (cascade) " CASCADE" else ""
    }

    override fun alterTableAddColumn(tableName: String, column: ColumnDefinition): String {
        val colDef = buildColumnDefinition(column)
        return "ALTER TABLE $tableName ADD COLUMN $colDef"
    }

    override fun alterTableModifyColumn(tableName: String, column: ColumnDefinition): String {
        val colDef = buildColumnDefinition(column)
        return "ALTER TABLE $tableName MODIFY COLUMN $colDef"
    }

    private fun buildColumnDefinition(col: ColumnDefinition): String {
        val parts = mutableListOf<String>()
        parts.add(col.name)
        parts.add(col.type.toString())

        if (col.modifiers.contains(ColumnModifier.PRIMARY_KEY)) parts.add("PRIMARY KEY")
        if (col.modifiers.contains(ColumnModifier.NOT_NULL)) parts.add("NOT NULL")
        if (col.modifiers.contains(ColumnModifier.UNIQUE)) parts.add("UNIQUE")
        if (col.modifiers.contains(ColumnModifier.AUTO_INCREMENT)) parts.add("AUTO_INCREMENT")
        col.defaultValue?.let { parts.add("DEFAULT $it") }

        return parts.joinToString(" ")
    }

    override fun alterTableDropColumn(tableName: String, columnName: String, cascade: Boolean): String {
        return "ALTER TABLE $tableName DROP COLUMN $columnName" + if (cascade) " CASCADE" else ""
    }

    override fun alterTableRenameColumn(tableName: String, oldName: String, newName: String): String {
        return "ALTER TABLE $tableName RENAME COLUMN $oldName TO $newName"
    }

    override fun alterTableAddPrimaryKey(tableName: String, columns: List<String>): String {
        return "ALTER TABLE $tableName ADD PRIMARY KEY (${columns.joinToString(", ")})"
    }

    override fun alterTableDropPrimaryKey(tableName: String): String {
        return "ALTER TABLE $tableName DROP PRIMARY KEY"
    }

    override fun alterTableAddForeignKey(tableName: String, foreignKey: ForeignKeyConstraint): String {
        return "ALTER TABLE $tableName ADD CONSTRAINT mock_fk FOREIGN KEY"
    }

    override fun alterTableDropForeignKey(tableName: String, constraintName: String): String {
        return "ALTER TABLE $tableName DROP CONSTRAINT $constraintName"
    }

    override fun createIndex(tableName: String, index: IndexDefinition): String {
        return "CREATE INDEX ${index.name} ON $tableName"
    }

    override fun dropIndex(indexName: String, tableName: String?): String {
        return "DROP INDEX $indexName"
    }

    override fun addForeignKey(constraint: ForeignKeyConstraint): String {
        return "ALTER TABLE ${constraint.tableName} ADD CONSTRAINT ${constraint.name} FOREIGN KEY"
    }

    override fun dropForeignKey(constraintName: String, tableName: String): String {
        return "ALTER TABLE $tableName DROP CONSTRAINT $constraintName"
    }

    override fun tableExists(tableName: String, schema: String?): String {
        return "SELECT 1 FROM $tableName LIMIT 0"
    }

    override fun getTableColumns(tableName: String, schema: String?): String {
        return "SELECT * FROM $tableName WHERE 1=0"
    }

    override fun renameTable(oldName: String, newName: String): String {
        return "ALTER TABLE $oldName RENAME TO $newName"
    }

    override fun addColumnComment(tableName: String, columnName: String, comment: String): String? {
        return null  // Not supported in mock
    }

    override fun addTableComment(tableName: String, comment: String): String? {
        return null  // Not supported in mock
    }
}