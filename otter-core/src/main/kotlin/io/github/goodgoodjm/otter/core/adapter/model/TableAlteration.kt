package io.github.goodgoodjm.otter.core.adapter.model

/**
 * Represents alterations to a table structure
 */
sealed class TableAlteration {
    /**
     * Add a new column
     */
    data class AddColumn(
        val column: ColumnDefinition,
        val position: ColumnPosition = ColumnPosition.Last
    ) : TableAlteration()

    /**
     * Drop an existing column
     */
    data class DropColumn(
        val columnName: String,
        val cascade: Boolean = false
    ) : TableAlteration()

    /**
     * Rename a column
     */
    data class RenameColumn(
        val oldName: String,
        val newName: String
    ) : TableAlteration()

    /**
     * Modify column type
     */
    data class ModifyColumnType(
        val columnName: String,
        val newType: ColumnType,
        val using: String? = null  // PostgreSQL USING clause
    ) : TableAlteration()

    /**
     * Set or remove column default
     */
    data class SetColumnDefault(
        val columnName: String,
        val defaultValue: Any?
    ) : TableAlteration()

    /**
     * Set or remove NOT NULL constraint
     */
    data class SetColumnNotNull(
        val columnName: String,
        val notNull: Boolean
    ) : TableAlteration()

    /**
     * Add a constraint
     */
    data class AddConstraint(
        val constraint: Constraint
    ) : TableAlteration()

    /**
     * Drop a constraint
     */
    data class DropConstraint(
        val constraintName: String,
        val cascade: Boolean = false
    ) : TableAlteration()

    /**
     * Add an index
     */
    data class AddIndex(
        val index: IndexDefinition
    ) : TableAlteration()

    /**
     * Drop an index
     */
    data class DropIndex(
        val indexName: String
    ) : TableAlteration()
}

/**
 * Column position for adding columns
 */
sealed class ColumnPosition {
    object First : ColumnPosition()
    object Last : ColumnPosition()
    data class After(val columnName: String) : ColumnPosition()
}

/**
 * Base class for constraints
 */
sealed class Constraint {
    abstract val name: String

    data class PrimaryKey(
        override val name: String,
        val columns: List<String>
    ) : Constraint()

    data class ForeignKey(
        override val name: String,
        val columns: List<String>,
        val referencedTable: String,
        val referencedColumns: List<String>,
        val onUpdate: ReferentialAction = ReferentialAction.NO_ACTION,
        val onDelete: ReferentialAction = ReferentialAction.NO_ACTION
    ) : Constraint()

    data class Unique(
        override val name: String,
        val columns: List<String>
    ) : Constraint()

    data class Check(
        override val name: String,
        val expression: String
    ) : Constraint()
}