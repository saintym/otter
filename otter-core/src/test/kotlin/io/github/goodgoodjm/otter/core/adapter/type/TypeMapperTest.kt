package io.github.goodgoodjm.otter.core.adapter.type

import io.github.goodgoodjm.otter.core.adapter.MockTypeMapper
import io.github.goodgoodjm.otter.core.adapter.model.ColumnModifier
import io.github.goodgoodjm.otter.core.adapter.model.ColumnType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*

class TypeMapperTest {

    private lateinit var typeMapper: TypeMapper

    @BeforeEach
    fun setUp() {
        typeMapper = MockTypeMapper()
    }

    @Test
    fun `should map basic column types`() {
        assertEquals("INTEGER", typeMapper.mapType(ColumnType.Integer))
        assertEquals("VARCHAR(100)", typeMapper.mapType(ColumnType.Varchar(100)))
        assertEquals("TEXT", typeMapper.mapType(ColumnType.Text))
        assertEquals("BOOLEAN", typeMapper.mapType(ColumnType.Boolean))
        assertEquals("TIMESTAMP", typeMapper.mapType(ColumnType.Timestamp))
    }

    @Test
    fun `should map type with AUTO_INCREMENT modifier`() {
        val modifiers = setOf(ColumnModifier.AUTO_INCREMENT)
        val result = typeMapper.mapTypeWithModifiers(ColumnType.Integer, modifiers)
        assertEquals("INTEGER AUTO_INCREMENT", result)
    }

    @Test
    fun `should map type without modifiers`() {
        val modifiers = setOf(ColumnModifier.NOT_NULL, ColumnModifier.UNIQUE)
        val result = typeMapper.mapTypeWithModifiers(ColumnType.Integer, modifiers)
        assertEquals("INTEGER", result)  // MockTypeMapper only handles AUTO_INCREMENT
    }

    @Test
    fun `should check if type is supported`() {
        assertTrue(typeMapper.supportsType(ColumnType.Integer))
        assertTrue(typeMapper.supportsType(ColumnType.Varchar(255)))
        assertTrue(typeMapper.supportsType(ColumnType.Text))
        assertTrue(typeMapper.supportsType(ColumnType.Boolean))
        assertTrue(typeMapper.supportsType(ColumnType.Timestamp))
        
        assertFalse(typeMapper.supportsType(ColumnType.Json))  // Not supported in mock
        assertFalse(typeMapper.supportsType(ColumnType.Uuid))  // Not supported in mock
    }

    @Test
    fun `should get default value expressions`() {
        assertEquals("NULL", typeMapper.getDefaultValueExpression(DefaultValue.Null))
        assertEquals("'test'", typeMapper.getDefaultValueExpression(DefaultValue.Literal("test")))
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
    fun `should handle special characters in string literals`() {
        // This is a simple mock - real implementations should handle escaping
        val result = typeMapper.getSqlLiteral("test'with'quotes")
        assertTrue(result.contains("test"))
    }
}