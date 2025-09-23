package io.github.goodgoodjm.otter.core.adapter.model

/**
 * Database-agnostic column types
 */
sealed class ColumnType {
    // Numeric types
    object SmallInt : ColumnType()
    object Integer : ColumnType()
    object BigInt : ColumnType()
    data class Decimal(val precision: Int, val scale: Int) : ColumnType()
    object Float : ColumnType()
    object Double : ColumnType()

    // String types
    data class Char(val length: Int) : ColumnType()
    data class Varchar(val length: Int) : ColumnType()
    object Text : ColumnType()

    // Binary types
    data class Binary(val length: Int) : ColumnType()
    object Blob : ColumnType()

    // Date/Time types
    object Date : ColumnType()
    object Time : ColumnType()
    object Timestamp : ColumnType()
    object TimestampWithTimeZone : ColumnType()

    // Boolean
    object Boolean : ColumnType()

    // JSON (optional support)
    object Json : ColumnType()
    object JsonBinary : ColumnType()

    // UUID (optional support)
    object Uuid : ColumnType()

    // Array (optional support)
    data class Array(val elementType: ColumnType) : ColumnType()

    // Custom type
    data class Custom(val typeName: String) : ColumnType()
}