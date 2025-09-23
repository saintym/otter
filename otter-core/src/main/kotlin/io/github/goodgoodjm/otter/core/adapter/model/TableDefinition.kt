package io.github.goodgoodjm.otter.core.adapter.model

/**
 * Definition of a database table
 */
data class TableDefinition(
    val name: String,
    val columns: List<ColumnDefinition>,
    val primaryKeys: List<String> = emptyList(),
    val indexes: List<IndexDefinition> = emptyList(),
    val foreignKeys: List<ForeignKeyConstraint> = emptyList(),
    val uniqueConstraints: List<UniqueConstraint> = emptyList(),
    val checkConstraints: List<CheckConstraint> = emptyList(),
    val comment: String? = null,
    val schema: String? = null
)

/**
 * Definition of a table column
 */
data class ColumnDefinition(
    val name: String,
    val type: ColumnType,
    val modifiers: Set<ColumnModifier> = emptySet(),
    val defaultValue: Any? = null,
    val comment: String? = null,
    val references: ForeignKeyReference? = null
)

/**
 * Column modifiers
 */
enum class ColumnModifier {
    NOT_NULL,
    UNIQUE,
    PRIMARY_KEY,
    AUTO_INCREMENT,
    GENERATED_ALWAYS,
    GENERATED_BY_DEFAULT
}

/**
 * Foreign key reference
 */
data class ForeignKeyReference(
    val table: String,
    val column: String,
    val onUpdate: ReferentialAction = ReferentialAction.NO_ACTION,
    val onDelete: ReferentialAction = ReferentialAction.NO_ACTION
)

/**
 * Referential actions for foreign keys
 */
enum class ReferentialAction {
    NO_ACTION,
    RESTRICT,
    CASCADE,
    SET_NULL,
    SET_DEFAULT
}

/**
 * Index definition
 */
data class IndexDefinition(
    val name: String,
    val tableName: String,
    val columns: List<String>,
    val unique: Boolean = false,
    val type: IndexType = IndexType.BTREE,
    val where: String? = null
)

/**
 * Index types
 */
enum class IndexType {
    BTREE,
    HASH,
    GIN,
    GIST,
    BRIN
}

/**
 * Foreign key constraint
 */
data class ForeignKeyConstraint(
    val name: String,
    val tableName: String,
    val columns: List<String>,
    val referencedTable: String,
    val referencedColumns: List<String>,
    val onUpdate: ReferentialAction = ReferentialAction.NO_ACTION,
    val onDelete: ReferentialAction = ReferentialAction.NO_ACTION
)

/**
 * Unique constraint
 */
data class UniqueConstraint(
    val name: String,
    val columns: List<String>
)

/**
 * Check constraint
 */
data class CheckConstraint(
    val name: String,
    val expression: String
)