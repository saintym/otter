package io.github.goodgoodjm.otter.adapter.postgresql

import io.github.goodgoodjm.otter.core.adapter.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgreSQLDDLProviderTest {

    private lateinit var ddlProvider: PostgreSQLDDLProvider

    @BeforeEach
    fun setUp() {
        ddlProvider = PostgreSQLDDLProvider()
    }

    @Test
    fun `should create simple table`() {
        val table = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition(
                    name = "id",
                    type = ColumnType.Integer,
                    modifiers = setOf(ColumnModifier.PRIMARY_KEY)
                ),
                ColumnDefinition(
                    name = "name",
                    type = ColumnType.Varchar(100),
                    modifiers = setOf(ColumnModifier.NOT_NULL)
                ),
                ColumnDefinition(
                    name = "email",
                    type = ColumnType.Varchar(255),
                    modifiers = setOf(ColumnModifier.UNIQUE)
                )
            ),
            primaryKeys = listOf("id")
        )

        val statements = ddlProvider.createTable(table)
        assertEquals(1, statements.size)
        
        val sql = statements[0]
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS users"))
        assertTrue(sql.contains("id INTEGER NOT NULL"))
        assertTrue(sql.contains("name VARCHAR(100) NOT NULL"))
        assertTrue(sql.contains("email VARCHAR(255) UNIQUE"))
        assertTrue(sql.contains("CONSTRAINT users_pkey PRIMARY KEY (id)"))
    }

    @Test
    fun `should create table with auto increment`() {
        val table = TableDefinition(
            name = "posts",
            columns = listOf(
                ColumnDefinition(
                    name = "id",
                    type = ColumnType.Integer,
                    modifiers = setOf(ColumnModifier.PRIMARY_KEY, ColumnModifier.AUTO_INCREMENT)
                ),
                ColumnDefinition(
                    name = "title",
                    type = ColumnType.Text
                )
            ),
            primaryKeys = listOf("id")
        )

        val statements = ddlProvider.createTable(table)
        val sql = statements[0]
        
        // PostgreSQL uses SERIAL for AUTO_INCREMENT
        assertTrue(sql.contains("id SERIAL NOT NULL"))
    }

    @Test
    fun `should create table with foreign key`() {
        val table = TableDefinition(
            name = "posts",
            columns = listOf(
                ColumnDefinition(
                    name = "id",
                    type = ColumnType.Integer,
                    modifiers = setOf(ColumnModifier.PRIMARY_KEY)
                ),
                ColumnDefinition(
                    name = "user_id",
                    type = ColumnType.Integer,
                    references = ForeignKeyReference(
                        table = "users",
                        column = "id",
                        onDelete = ReferentialAction.CASCADE
                    )
                )
            ),
            primaryKeys = listOf("id")
        )

        val statements = ddlProvider.createTable(table)
        val sql = statements[0]
        
        assertTrue(sql.contains("user_id INTEGER REFERENCES users(id) ON DELETE CASCADE"))
    }

    @Test
    fun `should create table with comments`() {
        val table = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition(
                    name = "id",
                    type = ColumnType.Integer,
                    comment = "User identifier"
                ),
                ColumnDefinition(
                    name = "name",
                    type = ColumnType.Varchar(100),
                    comment = "User full name"
                )
            ),
            comment = "Users table"
        )

        val statements = ddlProvider.createTable(table)
        assertTrue(statements.size > 1)  // Table + comments
        
        val comments = statements.drop(1)
        assertTrue(comments.any { it.contains("COMMENT ON TABLE users IS 'Users table'") })
        assertTrue(comments.any { it.contains("COMMENT ON COLUMN users.id IS 'User identifier'") })
        assertTrue(comments.any { it.contains("COMMENT ON COLUMN users.name IS 'User full name'") })
    }

    @Test
    fun `should drop table`() {
        val sql = ddlProvider.dropTable("users")
        assertEquals("DROP TABLE IF EXISTS users", sql)
    }

    @Test
    fun `should drop table with cascade`() {
        val sql = ddlProvider.dropTable("users", cascade = true)
        assertEquals("DROP TABLE IF EXISTS users CASCADE", sql)
    }

    @Test
    fun `should alter table - add column`() {
        val alterations = listOf(
            TableAlteration.AddColumn(
                column = ColumnDefinition(
                    name = "age",
                    type = ColumnType.Integer,
                    modifiers = setOf(ColumnModifier.NOT_NULL),
                    defaultValue = 0
                )
            )
        )

        val statements = ddlProvider.alterTable("users", alterations)
        assertEquals(1, statements.size)
        assertTrue(statements[0].contains("ALTER TABLE users ADD COLUMN age INTEGER NOT NULL DEFAULT 0"))
    }

    @Test
    fun `should alter table - drop column`() {
        val alterations = listOf(
            TableAlteration.DropColumn("age")
        )

        val statements = ddlProvider.alterTable("users", alterations)
        assertEquals(1, statements.size)
        assertEquals("ALTER TABLE users DROP COLUMN age", statements[0])
    }

    @Test
    fun `should alter table - rename column`() {
        val alterations = listOf(
            TableAlteration.RenameColumn("old_name", "new_name")
        )

        val statements = ddlProvider.alterTable("users", alterations)
        assertEquals(1, statements.size)
        assertEquals("ALTER TABLE users RENAME COLUMN old_name TO new_name", statements[0])
    }

    @Test
    fun `should create index`() {
        val index = IndexDefinition(
            name = "idx_users_email",
            tableName = "users",
            columns = listOf("email"),
            unique = true
        )

        val sql = ddlProvider.createIndex(index)
        assertEquals("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users (email)", sql)
    }

    @Test
    fun `should create partial index`() {
        val index = IndexDefinition(
            name = "idx_active_users",
            tableName = "users",
            columns = listOf("id"),
            where = "is_active = true"
        )

        val sql = ddlProvider.createIndex(index)
        assertTrue(sql.contains("WHERE is_active = true"))
    }

    @Test
    fun `should check if table exists`() {
        val sql = ddlProvider.tableExists("users")
        assertTrue(sql.contains("information_schema.tables"))
        assertTrue(sql.contains("table_name = 'users'"))
    }
}