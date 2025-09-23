package io.github.goodgoodjm.otter.core.adapter.type

import io.github.goodgoodjm.otter.core.adapter.model.ColumnModifier
import io.github.goodgoodjm.otter.core.adapter.model.ColumnType

/**
 * Maps database-agnostic column types to database-specific SQL types
 */
interface TypeMapper {
    /**
     * Map a column type to its SQL representation
     */
    fun mapType(type: ColumnType): String

    /**
     * Map a column type with modifiers (e.g., AUTO_INCREMENT)
     */
    fun mapTypeWithModifiers(type: ColumnType, modifiers: Set<ColumnModifier>): String

    /**
     * Check if a column type is supported
     */
    fun supportsType(type: ColumnType): Boolean

    /**
     * Get the SQL expression for a default value
     */
    fun getDefaultValueExpression(value: DefaultValue): String

    /**
     * Get the SQL literal for a value
     */
    fun getSqlLiteral(value: Any?): String
}

/**
 * Default values for columns
 */
sealed class DefaultValue {
    object Null : DefaultValue()
    data class Literal(val value: Any) : DefaultValue()
    object CurrentTimestamp : DefaultValue()
    object CurrentDate : DefaultValue()
    object CurrentTime : DefaultValue()
    data class Expression(val sql: String) : DefaultValue()
}