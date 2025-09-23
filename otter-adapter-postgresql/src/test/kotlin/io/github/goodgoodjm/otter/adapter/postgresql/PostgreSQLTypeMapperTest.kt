package io.github.goodgoodjm.otter.adapter.postgresql

import io.github.goodgoodjm.otter.core.adapter.model.ColumnModifier
import io.github.goodgoodjm.otter.core.adapter.model.ColumnType
import io.github.goodgoodjm.otter.core.adapter.type.DefaultValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgreSQLTypeMapperTest {

    private lateinit var typeMapper: PostgreSQLTypeMapper

    @BeforeEach
    fun setUp() {
        typeMapper = PostgreSQLTypeMapper()
    }

    @Test
    fun `should map numeric types`() {
        assertEquals("SMALLINT", typeMapper.mapType(ColumnType.SmallInt))
        assertEquals("INTEGER", typeMapper.mapType(ColumnType.Integer))
        assertEquals("BIGINT", typeMapper.mapType(ColumnType.BigInt))
        assertEquals("DECIMAL(10, 2)", typeMapper.mapType(ColumnType.Decimal(10, 2)))
        assertEquals("REAL", typeMapper.mapType(ColumnType.Float))
        assertEquals("DOUBLE PRECISION", typeMapper.mapType(ColumnType.Double))
    }

    @Test
    fun `should map string types`() {
        assertEquals("CHAR(10)", typeMapper.mapType(ColumnType.Char(10)))
        assertEquals("VARCHAR(255)", typeMapper.mapType(ColumnType.Varchar(255)))
        assertEquals("TEXT", typeMapper.mapType(ColumnType.Text))
    }

    @Test
    fun `should map date and time types`() {
        assertEquals("DATE", typeMapper.mapType(ColumnType.Date))
        assertEquals("TIME", typeMapper.mapType(ColumnType.Time))
        assertEquals("TIMESTAMP", typeMapper.mapType(ColumnType.Timestamp))
        assertEquals("TIMESTAMP WITH TIME ZONE", typeMapper.mapType(ColumnType.TimestampWithTimeZone))
    }

    @Test
    fun `should map PostgreSQL specific types`() {
        assertEquals("JSON", typeMapper.mapType(ColumnType.Json))
        assertEquals("JSONB", typeMapper.mapType(ColumnType.JsonBinary))
        assertEquals("UUID", typeMapper.mapType(ColumnType.Uuid))
        assertEquals("BOOLEAN", typeMapper.mapType(ColumnType.Boolean))
    }

    @Test
    fun `should map array types`() {
        assertEquals("INTEGER[]", typeMapper.mapType(ColumnType.Array(ColumnType.Integer)))
        assertEquals("VARCHAR(100)[]", typeMapper.mapType(ColumnType.Array(ColumnType.Varchar(100))))
        assertEquals("JSONB[]", typeMapper.mapType(ColumnType.Array(ColumnType.JsonBinary)))
    }

    @Test
    fun `should map types with AUTO_INCREMENT modifier to SERIAL`() {
        val modifiers = setOf(ColumnModifier.AUTO_INCREMENT)
        
        assertEquals("SMALLSERIAL", typeMapper.mapTypeWithModifiers(ColumnType.SmallInt, modifiers))
        assertEquals("SERIAL", typeMapper.mapTypeWithModifiers(ColumnType.Integer, modifiers))
        assertEquals("BIGSERIAL", typeMapper.mapTypeWithModifiers(ColumnType.BigInt, modifiers))
        
        // Non-integer types should not be affected
        assertEquals("VARCHAR(100)", typeMapper.mapTypeWithModifiers(ColumnType.Varchar(100), modifiers))
    }

    @Test
    fun `should check type support`() {
        assertTrue(typeMapper.supportsType(ColumnType.Integer))
        assertTrue(typeMapper.supportsType(ColumnType.Varchar(255)))
        assertTrue(typeMapper.supportsType(ColumnType.Json))
        assertTrue(typeMapper.supportsType(ColumnType.Uuid))
        assertTrue(typeMapper.supportsType(ColumnType.Array(ColumnType.Integer)))
    }

    @Test
    fun `should get default value expressions`() {
        assertEquals("NULL", typeMapper.getDefaultValueExpression(DefaultValue.Null))
        assertEquals("'test'", typeMapper.getDefaultValueExpression(DefaultValue.Literal("test")))
        assertEquals("42", typeMapper.getDefaultValueExpression(DefaultValue.Literal(42)))
        assertEquals("CURRENT_TIMESTAMP", typeMapper.getDefaultValueExpression(DefaultValue.CurrentTimestamp))
        assertEquals("CURRENT_DATE", typeMapper.getDefaultValueExpression(DefaultValue.CurrentDate))
        assertEquals("CURRENT_TIME", typeMapper.getDefaultValueExpression(DefaultValue.CurrentTime))
        assertEquals("NOW()", typeMapper.getDefaultValueExpression(DefaultValue.Expression("NOW()")))
    }

    @Test
    fun `should get SQL literals`() {
        assertEquals("NULL", typeMapper.getSqlLiteral(null))
        assertEquals("'hello'", typeMapper.getSqlLiteral("hello"))
        assertEquals("42", typeMapper.getSqlLiteral(42))
        assertEquals("3.14", typeMapper.getSqlLiteral(3.14))
        assertEquals("TRUE", typeMapper.getSqlLiteral(true))
        assertEquals("FALSE", typeMapper.getSqlLiteral(false))
    }

    @Test
    fun `should escape strings properly`() {
        assertEquals("'O''Reilly'", typeMapper.getSqlLiteral("O'Reilly"))
        assertEquals("'Path\\\\to\\\\file'", typeMapper.getSqlLiteral("Path\\to\\file"))
    }

    @Test
    fun `should handle PostgreSQL arrays`() {
        val array = listOf(1, 2, 3)
        assertEquals("ARRAY[1, 2, 3]", typeMapper.getSqlLiteral(array))
        
        val stringArray = listOf("a", "b", "c")
        assertEquals("ARRAY['a', 'b', 'c']", typeMapper.getSqlLiteral(stringArray))
    }

    @Test
    fun `should handle UUID values`() {
        val uuid = java.util.UUID.randomUUID()
        val literal = typeMapper.getSqlLiteral(uuid)
        assertTrue(literal.endsWith("::UUID"))
    }
}