package io.github.goodgoodjm.otter

import io.github.goodgoodjm.otter.core.adapter.DatabaseConfig
import io.github.goodgoodjm.otter.core.adapter.MockAdapter
import io.github.goodgoodjm.otter.core.dsl.createtable.createTable
import io.github.goodgoodjm.otter.core.dsl.type.*
import io.github.goodgoodjm.otter.core.migration.MigrationContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertTrue

class CreateTableContextTests {

    private lateinit var adapter: MockAdapter

    @BeforeEach
    fun setup() {
        adapter = MockAdapter()
        adapter.initialize(DatabaseConfig("jdbc:h2:mem:test", "sa", ""))
    }

    @AfterEach
    fun teardown() {
        MigrationContext.clear()
    }

    @Test
    fun `createTable generates correct SQL with various column types`() {
        adapter.getConnectionProvider().useTransaction { context ->
            MigrationContext.runInContext(adapter, context, TestDatabaseConfig.createH2OtterConfig()) {
                val sql = createTable("person") {
                    "id" - Types.integer().primaryKey().autoIncrement()
                    "name" - Types.varchar(255)
                    "address_id" - Types.integer().references("address", "id")
                    "lat" - Types.bigInt().unique()
                    "is_active" - Types.boolean().default("true")
                }

                assertTrue(sql.isNotEmpty())
                val fullSql = sql.joinToString(" ")

                // Check table creation
                assertTrue(fullSql.contains("CREATE TABLE"))
                assertTrue(fullSql.contains("person"))

                // Check columns
                assertTrue(fullSql.contains("id"))
                assertTrue(fullSql.contains("name"))
                assertTrue(fullSql.contains("address_id"))
                assertTrue(fullSql.contains("lat"))
                assertTrue(fullSql.contains("is_active"))

                // Check constraints
                assertTrue(fullSql.contains("PRIMARY KEY") || fullSql.contains("primary key"))
                assertTrue(fullSql.contains("FOREIGN KEY") || fullSql.contains("REFERENCES"))
                assertTrue(fullSql.contains("UNIQUE") || fullSql.contains("unique"))
            }
        }
    }

    @Test
    fun `createTable supports all column types`() {
        adapter.getConnectionProvider().useTransaction { context ->
            MigrationContext.runInContext(adapter, context, TestDatabaseConfig.createH2OtterConfig()) {
                val sql = createTable("test_types") {
                    "id" - Types.serial()
                    "tiny" - Types.tinyInt()
                    "small" - Types.smallInt()
                    "int" - Types.integer()
                    "big" - Types.bigInt()
                    "dec" - Types.decimal(10, 2)
                    "flt" - Types.float()
                    "dbl" - Types.double()
                    "chr" - Types.char(10)
                    "vchr" - Types.varchar(100)
                    "txt" - Types.text()
                    "dt" - Types.date()
                    "tm" - Types.time()
                    "dttm" - Types.datetime()
                    "ts" - Types.timestamp()
                    "bool" - Types.boolean()
                    "uid" - Types.uuid()
                    "jsn" - Types.json()
                    "blb" - Types.blob()
                }

                assertTrue(sql.isNotEmpty())
                val fullSql = sql.joinToString(" ")
                assertTrue(fullSql.contains("CREATE TABLE test_types"))
            }
        }
    }

    @Test
    fun `createTable handles composite constraints`() {
        adapter.getConnectionProvider().useTransaction { context ->
            MigrationContext.runInContext(adapter, context, TestDatabaseConfig.createH2OtterConfig()) {
                val sql = createTable("users") {
                    "id" - Types.serial()
                    "email" - Types.varchar(255).notNull().unique()
                    "username" - Types.varchar(50).notNull().unique()
                    "created_at" - Types.timestamp().defaultCurrentTimestamp()
                    "updated_at" - Types.timestamp().defaultNull()
                    "is_verified" - Types.boolean().default("false")
                }

                assertTrue(sql.isNotEmpty())
                val fullSql = sql.joinToString(" ")

                // Should have multiple unique constraints
                val uniqueCount = fullSql.split("UNIQUE", ignoreCase = true).size - 1
                assertTrue(uniqueCount >= 2)

                // Should have NOT NULL constraints
                assertTrue(fullSql.contains("NOT NULL") || fullSql.contains("not null"))
            }
        }
    }

    @Test
    fun `createTable handles foreign key relationships`() {
        adapter.getConnectionProvider().useTransaction { context ->
            MigrationContext.runInContext(adapter, context, TestDatabaseConfig.createH2OtterConfig()) {
                val sql = createTable("posts") {
                    "id" - Types.serial()
                    "title" - Types.varchar(200).notNull()
                    "content" - Types.text()
                    "author_id" - Types.integer().notNull().references("users", "id")
                    "category_id" - Types.integer().references("categories", "id")
                }

                assertTrue(sql.isNotEmpty())
                val fullSql = sql.joinToString(" ")

                // Check foreign key definitions
                assertTrue(fullSql.contains("FOREIGN KEY") || fullSql.contains("REFERENCES"))
                assertTrue(fullSql.contains("users"))
                assertTrue(fullSql.contains("categories"))
            }
        }
    }

    @Test
    fun `createTable uses global type constants`() {
        adapter.getConnectionProvider().useTransaction { context ->
            MigrationContext.runInContext(adapter, context, TestDatabaseConfig.createH2OtterConfig()) {
                // Test using global constants
                val sql = createTable("simple_table") {
                    "id" - SERIAL
                    "name" - VARCHAR(100)
                    "count" - INT
                    "amount" - BIGINT
                    "created" - TIMESTAMP
                    "active" - BOOLEAN
                    "data" - TEXT
                    "uid" - UUID
                }

                assertTrue(sql.isNotEmpty())
                val fullSql = sql.joinToString(" ")
                assertTrue(fullSql.contains("CREATE TABLE simple_table"))
                assertTrue(fullSql.contains("id"))
                assertTrue(fullSql.contains("name"))
            }
        }
    }
}