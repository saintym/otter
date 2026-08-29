package io.github.goodgoodjm.otter.adapter.postgresql

import io.github.goodgoodjm.otter.core.adapter.model.ColumnModifier
import io.github.goodgoodjm.otter.core.adapter.model.ColumnType
import io.github.goodgoodjm.otter.core.adapter.type.DefaultValue
import io.github.goodgoodjm.otter.core.adapter.type.TypeMapper

/**
 * PostgreSQL type mapper implementation
 */
class PostgreSQLTypeMapper : TypeMapper {

    override fun mapType(type: ColumnType): String {
        return when (type) {
            // Numeric types
            is ColumnType.SmallInt -> "SMALLINT"
            is ColumnType.Integer -> "INTEGER"
            is ColumnType.BigInt -> "BIGINT"
            is ColumnType.Decimal -> "DECIMAL(${type.precision}, ${type.scale})"
            is ColumnType.Float -> "REAL"
            is ColumnType.Double -> "DOUBLE PRECISION"

            // String types
            is ColumnType.Char -> "CHAR(${type.length})"
            is ColumnType.Varchar -> "VARCHAR(${type.length})"
            is ColumnType.Text -> "TEXT"

            // Binary types
            is ColumnType.Binary -> "BYTEA"
            is ColumnType.Blob -> "BYTEA"

            // Date/Time types
            is ColumnType.Date -> "DATE"
            is ColumnType.Time -> "TIME"
            is ColumnType.Timestamp -> "TIMESTAMP"
            is ColumnType.TimestampWithTimeZone -> "TIMESTAMP WITH TIME ZONE"

            // Boolean
            is ColumnType.Boolean -> "BOOLEAN"

            // JSON types (PostgreSQL specific)
            is ColumnType.Json -> "JSON"
            is ColumnType.JsonBinary -> "JSONB"

            // UUID (PostgreSQL specific)
            is ColumnType.Uuid -> "UUID"

            // Array (PostgreSQL specific)
            is ColumnType.Array -> "${mapType(type.elementType)}[]"

            // Custom type
            is ColumnType.Custom -> type.typeName
        }
    }

    override fun mapTypeWithModifiers(type: ColumnType, modifiers: Set<ColumnModifier>): String {
        // Handle AUTO_INCREMENT with SERIAL types in PostgreSQL
        return if (ColumnModifier.AUTO_INCREMENT in modifiers) {
            when (type) {
                is ColumnType.SmallInt -> "SMALLSERIAL"
                is ColumnType.Integer -> "SERIAL"
                is ColumnType.BigInt -> "BIGSERIAL"
                else -> mapType(type)  // Other types don't support AUTO_INCREMENT
            }
        } else {
            mapType(type)
        }
    }

    override fun supportsType(type: ColumnType): Boolean {
        return when (type) {
            is ColumnType.SmallInt,
            is ColumnType.Integer,
            is ColumnType.BigInt,
            is ColumnType.Decimal,
            is ColumnType.Float,
            is ColumnType.Double,
            is ColumnType.Char,
            is ColumnType.Varchar,
            is ColumnType.Text,
            is ColumnType.Binary,
            is ColumnType.Blob,
            is ColumnType.Date,
            is ColumnType.Time,
            is ColumnType.Timestamp,
            is ColumnType.TimestampWithTimeZone,
            is ColumnType.Boolean,
            is ColumnType.Json,
            is ColumnType.JsonBinary,
            is ColumnType.Uuid -> true
            is ColumnType.Array -> supportsType(type.elementType)
            is ColumnType.Custom -> true  // Assume custom types are valid
        }
    }

    override fun getDefaultValueExpression(value: DefaultValue): String {
        return when (value) {
            is DefaultValue.Null -> "NULL"
            is DefaultValue.Literal -> getSqlLiteral(value.value)
            is DefaultValue.CurrentTimestamp -> "CURRENT_TIMESTAMP"
            is DefaultValue.CurrentDate -> "CURRENT_DATE"
            is DefaultValue.CurrentTime -> "CURRENT_TIME"
            is DefaultValue.Expression -> value.sql
        }
    }

    override fun getSqlLiteral(value: Any?): String {
        return when (value) {
            null -> "NULL"
            is String -> "'${escapeString(value)}'"
            is Number -> value.toString()
            is Boolean -> if (value) "TRUE" else "FALSE"
            is java.sql.Date -> "'$value'::DATE"
            is java.sql.Time -> "'$value'::TIME"
            is java.sql.Timestamp -> "'$value'::TIMESTAMP"
            is java.time.LocalDate -> "'$value'::DATE"
            is java.time.LocalTime -> "'$value'::TIME"
            is java.time.LocalDateTime -> "'$value'::TIMESTAMP"
            is java.time.OffsetDateTime -> "'$value'::TIMESTAMP WITH TIME ZONE"
            is java.util.UUID -> "'$value'::UUID"
            is ByteArray -> "E'\\\\x${value.joinToString("") { "%02x".format(it) }}'"
            is List<*> -> "ARRAY[${value.joinToString(", ") { getSqlLiteral(it) }}]"
            is Map<*, *> -> "'${value.entries.joinToString(", ") { "\"${it.key}\":\"${it.value}\"" }}'::JSONB"
            else -> "'$value'"
        }
    }

    private fun escapeString(value: String): String {
        return value
            .replace("'", "''")
            .replace("\\", "\\\\")
    }
}