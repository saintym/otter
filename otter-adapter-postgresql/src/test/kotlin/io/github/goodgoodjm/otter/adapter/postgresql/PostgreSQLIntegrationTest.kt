package io.github.goodgoodjm.otter.adapter.postgresql

import io.github.goodgoodjm.otter.core.adapter.DatabaseConfig
import io.github.goodgoodjm.otter.core.adapter.model.*
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for PostgreSQL adapter with real database
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgreSQLIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("integration_test")
            .withUsername("test_user")
            .withPassword("test_pass")
    }

    private lateinit var adapter: PostgreSQLAdapter
    private lateinit var config: DatabaseConfig

    @BeforeAll
    fun setUpAll() {
        postgres.start()
        
        config = DatabaseConfig(
            url = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password
        )

        adapter = PostgreSQLAdapter()
        adapter.initialize(config)
    }

    @AfterAll
    fun tearDownAll() {
        adapter.close()
        postgres.stop()
    }

    @Test
    fun `should create and drop table`() {
        val connectionProvider = adapter.getConnectionProvider()
        val ddlProvider = adapter.getDDLProvider()

        connectionProvider.useTransaction { context ->
            // Create table
            val table = TableDefinition(
                name = "test_users",
                columns = listOf(
                    ColumnDefinition(
                        name = "id",
                        type = ColumnType.Integer,
                        modifiers = setOf(ColumnModifier.PRIMARY_KEY, ColumnModifier.AUTO_INCREMENT)
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
                    ),
                    ColumnDefinition(
                        name = "created_at",
                        type = ColumnType.Timestamp,
                        defaultValue = "CURRENT_TIMESTAMP"
                    )
                ),
                primaryKeys = listOf("id")
            )

            val createStatements = ddlProvider.createTable(table)
            createStatements.forEach { sql ->
                context.execute(sql)
            }

            // Verify table exists
            val existsSql = ddlProvider.tableExists("test_users")
            val exists = context.queryOne(existsSql) { rs ->
                rs.getBoolean("exists")
            }
            assertTrue(exists ?: false)

            // Insert test data
            context.execute(
                "INSERT INTO test_users (name, email) VALUES (?, ?)",
                listOf("John Doe", "john@example.com")
            )

            // Query data
            val users = context.query(
                "SELECT * FROM test_users WHERE name = ?",
                listOf("John Doe")
            ) { rs ->
                rs.getString("name") to rs.getString("email")
            }
            assertEquals(1, users.size)
            assertEquals("John Doe" to "john@example.com", users[0])

            // Drop table
            val dropSql = ddlProvider.dropTable("test_users", cascade = true)
            context.execute(dropSql)

            // Verify table doesn't exist
            val existsAfterDrop = context.queryOne(existsSql) { rs ->
                rs.getBoolean("exists")
            }
            assertFalse(existsAfterDrop ?: true)
        }
    }

    @Test
    fun `should handle PostgreSQL specific types`() {
        val connectionProvider = adapter.getConnectionProvider()
        val ddlProvider = adapter.getDDLProvider()

        connectionProvider.useTransaction { context ->
            // Create table with PostgreSQL specific types
            val table = TableDefinition(
                name = "pg_types_test",
                columns = listOf(
                    ColumnDefinition(
                        name = "id",
                        type = ColumnType.Uuid,
                        modifiers = setOf(ColumnModifier.PRIMARY_KEY)
                    ),
                    ColumnDefinition(
                        name = "data",
                        type = ColumnType.JsonBinary
                    ),
                    ColumnDefinition(
                        name = "tags",
                        type = ColumnType.Array(ColumnType.Varchar(50))
                    ),
                    ColumnDefinition(
                        name = "active",
                        type = ColumnType.Boolean,
                        defaultValue = true
                    )
                )
            )

            val createStatements = ddlProvider.createTable(table)
            createStatements.forEach { sql ->
                context.execute(sql)
            }

            // Insert data with PostgreSQL types
            context.execute("""
                INSERT INTO pg_types_test (id, data, tags, active) 
                VALUES (
                    gen_random_uuid(),
                    '{"name": "test", "value": 123}'::jsonb,
                    ARRAY['tag1', 'tag2', 'tag3'],
                    true
                )
            """)

            // Query and verify
            val result = context.queryOne("SELECT * FROM pg_types_test") { rs ->
                mapOf(
                    "id" to rs.getString("id"),
                    "data" to rs.getString("data"),
                    "tags" to rs.getArray("tags").array,
                    "active" to rs.getBoolean("active")
                )
            }

            assertNotNull(result)
            assertTrue(result["active"] as Boolean)
            
            // Clean up
            context.execute("DROP TABLE pg_types_test")
        }
    }

    @Test
    fun `should use advisory locks`() {
        val lockProvider = adapter.getLockProvider()
        
        // Verify PostgreSQL supports advisory locks
        assertTrue(lockProvider.supportsAdvisoryLocks())

        val lockId = "test_lock_${System.currentTimeMillis()}"

        // Acquire lock
        assertTrue(lockProvider.acquireLock(lockId, Duration.ofSeconds(1)))
        
        // Verify lock is held
        assertTrue(lockProvider.isLocked(lockId))

        // Try to acquire same lock (should fail)
        assertFalse(lockProvider.acquireLock(lockId, Duration.ofMillis(100)))

        // Get lock info
        val lockInfo = lockProvider.getLockInfo(lockId)
        assertNotNull(lockInfo)
        assertEquals(lockId, lockInfo.lockId)

        // Release lock
        assertTrue(lockProvider.releaseLock(lockId))
        
        // Verify lock is released
        assertFalse(lockProvider.isLocked(lockId))

        // Should be able to acquire again
        assertTrue(lockProvider.acquireLock(lockId))
        
        // Clean up
        lockProvider.releaseAllLocks()
    }

    @Test
    fun `should handle transactions with savepoints`() {
        val connectionProvider = adapter.getConnectionProvider()
        val ddlProvider = adapter.getDDLProvider()

        connectionProvider.useTransaction { context ->
            // Create test table
            val table = TableDefinition(
                name = "savepoint_test",
                columns = listOf(
                    ColumnDefinition(
                        name = "id",
                        type = ColumnType.Integer,
                        modifiers = setOf(ColumnModifier.PRIMARY_KEY)
                    ),
                    ColumnDefinition(
                        name = "value",
                        type = ColumnType.Varchar(100)
                    )
                ),
                primaryKeys = listOf("id")
            )

            val createStatements = ddlProvider.createTable(table)
            createStatements.forEach { sql ->
                context.execute(sql)
            }

            // Insert initial data
            context.execute("INSERT INTO savepoint_test (id, value) VALUES (1, 'initial')")

            // Create savepoint
            val savepoint = context.setSavepoint("test_savepoint")

            // Insert more data
            context.execute("INSERT INTO savepoint_test (id, value) VALUES (2, 'after_savepoint')")

            // Verify both rows exist
            val countBefore = context.queryOne("SELECT COUNT(*) as cnt FROM savepoint_test") { rs ->
                rs.getInt("cnt")
            }
            assertEquals(2, countBefore)

            // Rollback to savepoint
            context.rollbackToSavepoint(savepoint)

            // Verify only first row exists
            val countAfter = context.queryOne("SELECT COUNT(*) as cnt FROM savepoint_test") { rs ->
                rs.getInt("cnt")
            }
            assertEquals(1, countAfter)

            // Clean up
            context.execute("DROP TABLE savepoint_test")
        }
    }

    @Test
    fun `should alter table`() {
        val connectionProvider = adapter.getConnectionProvider()
        val ddlProvider = adapter.getDDLProvider()

        connectionProvider.useTransaction { context ->
            // Create initial table
            val table = TableDefinition(
                name = "alter_test",
                columns = listOf(
                    ColumnDefinition(
                        name = "id",
                        type = ColumnType.Integer,
                        modifiers = setOf(ColumnModifier.PRIMARY_KEY)
                    ),
                    ColumnDefinition(
                        name = "name",
                        type = ColumnType.Varchar(50)
                    )
                ),
                primaryKeys = listOf("id")
            )

            ddlProvider.createTable(table).forEach { sql ->
                context.execute(sql)
            }

            // Add column
            val addColumn = TableAlteration.AddColumn(
                column = ColumnDefinition(
                    name = "email",
                    type = ColumnType.Varchar(100),
                    modifiers = setOf(ColumnModifier.UNIQUE)
                )
            )

            ddlProvider.alterTable("alter_test", listOf(addColumn)).forEach { sql ->
                context.execute(sql)
            }

            // Rename column
            val renameColumn = TableAlteration.RenameColumn("name", "full_name")
            
            ddlProvider.alterTable("alter_test", listOf(renameColumn)).forEach { sql ->
                context.execute(sql)
            }

            // Verify changes
            val columns = context.query(
                ddlProvider.getTableColumns("alter_test")
            ) { rs ->
                rs.getString("column_name")
            }

            assertTrue(columns.contains("id"))
            assertTrue(columns.contains("full_name"))
            assertTrue(columns.contains("email"))
            assertFalse(columns.contains("name"))

            // Clean up
            context.execute("DROP TABLE alter_test")
        }
    }

    private fun assertNotNull(value: Any?) {
        kotlin.test.assertNotNull(value)
    }
}