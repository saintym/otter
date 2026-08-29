package io.github.goodgoodjm.otter

import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.foreignKey
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.type.INT
import io.github.goodgoodjm.otter.core.dsl.type.VARCHAR
import io.github.goodgoodjm.otter.core.dsl.type.TEXT
import io.github.goodgoodjm.otter.core.dsl.type.DECIMAL
import io.github.goodgoodjm.otter.core.dsl.altertable.AlterTableContext
import io.github.goodgoodjm.otter.core.dsl.altertable.AlterTableSchema
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains

class AlterTableTests {

    @Test
    fun alterTable_addColumn_shouldGenerateCorrectSQL() {
        val migration = object : Migration() {
            override fun up() {
                alterTable("users") {
                    add("email") - VARCHAR(255)
                }
            }
            override fun down() {}
        }

        migration.up()
        val context = migration.contexts.first() as AlterTableContext
        val sqls = context.resolve()
        
        assertEquals(1, sqls.size)
        assertContains(sqls[0], "ALTER TABLE users ADD COLUMN email VARCHAR(255)")
    }

    @Test
    fun alterTable_addColumnWithConstraints_shouldIncludeConstraints() {
        val migration = object : Migration() {
            override fun up() {
                alterTable("users") {
                    add("email") - VARCHAR(255) constraints Constraint.NOT_NULL and Constraint.UNIQUE
                    add("age") - INT constraints DEFAULT(18) and CHECK("age >= 0")
                }
            }
            override fun down() {}
        }

        migration.up()
        val context = migration.contexts.first() as AlterTableContext
        val sqls = context.resolve()

        assertEquals(2, sqls.size)
        assertContains(sqls[0], "email VARCHAR(255)")
        assertContains(sqls[0], "NOT NULL")
        assertContains(sqls[0], "UNIQUE")
        assertContains(sqls[1], "age INT")
        assertContains(sqls[1], "DEFAULT 18")
        assertContains(sqls[1], "CHECK (age >= 0)")
    }

    @Test
    fun alterTable_dropColumn_shouldGenerateDropStatement() {
        val migration = object : Migration() {
            override fun up() {
                alterTable("users") {
                    drop("old_column")
                    drop("unused_field")
                }
            }
            override fun down() {}
        }

        migration.up()
        val context = migration.contexts.first() as AlterTableContext
        val sqls = context.resolve()
        
        assertEquals(2, sqls.size)
        assertEquals("ALTER TABLE users DROP COLUMN old_column", sqls[0])
        assertEquals("ALTER TABLE users DROP COLUMN unused_field", sqls[1])
    }

    @Test
    fun alterTable_modifyColumn_shouldGenerateModifyStatement() {
        val migration = object : Migration() {
            override fun up() {
                alterTable("users") {
                    modify("name") - VARCHAR(100)
                    modify("status") - (VARCHAR(20) constraints DEFAULT("active"))
                }
            }
            override fun down() {}
        }

        migration.up()
        val context = migration.contexts.first() as AlterTableContext
        val sqls = context.resolve()

        assertEquals(2, sqls.size)
        assertTrue(sqls[0].contains("ALTER COLUMN name VARCHAR(100)"), "Expected 'ALTER COLUMN name VARCHAR(100)' in: ${sqls[0]}")
        assertTrue(sqls[1].contains("ALTER COLUMN status VARCHAR(20)") && sqls[1].contains("DEFAULT"), "Expected 'ALTER COLUMN status VARCHAR(20)' with DEFAULT in: ${sqls[1]}")
    }

    @Test
    fun alterTable_mixedOperations_shouldGenerateAllStatements() {
        val migration = object : Migration() {
            override fun up() {
                alterTable("products") {
                    add("description") - TEXT constraints Constraint.NULLABLE
                    add("price") - DECIMAL(10, 2) constraints (Constraint.NOT_NULL and DEFAULT(0.00))
                    modify("name") - VARCHAR(200) constraints Constraint.NOT_NULL
                    drop("deprecated_field")
                }
            }
            override fun down() {}
        }

        migration.up()
        val context = migration.contexts.first() as AlterTableContext
        val sqls = context.resolve()
        
        assertEquals(4, sqls.size)
        assertTrue(sqls.any { it.contains("ADD COLUMN description") })
        assertTrue(sqls.any { it.contains("ADD COLUMN price") })
        assertTrue(sqls.any { it.contains("name VARCHAR(200)") })
        assertTrue(sqls.any { it.contains("DROP COLUMN deprecated_field") })
    }

    @Test
    fun alterTable_withForeignKey_shouldIncludeReferences() {
        val migration = object : Migration() {
            override fun up() {
                alterTable("orders") {
                    add("customer_id") - (INT foreignKey "customers(id)")
                    add("product_id") - INT constraints REFERENCES("products", "id", onDelete = CASCADE)
                }
            }
            override fun down() {}
        }

        migration.up()
        val context = migration.contexts.first() as AlterTableContext
        val sqls = context.resolve()

        assertEquals(2, sqls.size)
        assertTrue(sqls[0].contains("REFERENCES customers(id)"), "Expected REFERENCES customers(id) in: ${sqls[0]}")
    }

    @Test
    fun alterTable_withNewConstraints_shouldUseCorrectSyntax() {
        val migration = object : Migration() {
            override fun up() {
                alterTable("articles") {
                    add("slug") - (VARCHAR(255) constraints COLLATE("utf8mb4_unicode_ci"))
                    add("metadata") - (TEXT constraints COMMENT("JSON 형식의 메타데이터"))
                    add("sequence_id") - (INT constraints GENERATED(BY_DEFAULT))
                }
            }
            override fun down() {}
        }

        migration.up()
        val context = migration.contexts.first() as AlterTableContext
        val sqls = context.resolve()
        
        assertEquals(3, sqls.size)
        assertTrue(sqls[0].contains("COLLATE utf8mb4_unicode_ci"))
        assertTrue(sqls[2].contains("GENERATED BY DEFAULT AS IDENTITY"))
    }

    @Test
    fun alterTableSchema_operations_shouldMaintainOrder() {
        val schema = AlterTableSchema("test_table")
        
        schema.add("col1")
        schema.modify("col2")
        schema.drop("col3")
        schema.add("col4")
        
        assertEquals(4, schema.operations.size)
        assertEquals("col1", schema.operations[0].columnName)
        assertEquals("col2", schema.operations[1].columnName)
        assertEquals("col3", schema.operations[2].columnName)
        assertEquals("col4", schema.operations[3].columnName)
    }
}