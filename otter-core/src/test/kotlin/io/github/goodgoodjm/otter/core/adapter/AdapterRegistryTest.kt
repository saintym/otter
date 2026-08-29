package io.github.goodgoodjm.otter.core.adapter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import kotlin.test.*

class AdapterRegistryTest {

    @BeforeEach
    fun setUp() {
        AdapterRegistry.clear()
    }

    @AfterEach
    fun tearDown() {
        AdapterRegistry.clear()
    }

    @Test
    fun `should register and retrieve adapter by name`() {
        val adapter = MockDatabaseAdapter()
        AdapterRegistry.register(adapter)

        val retrieved = AdapterRegistry.get("MockDB")
        assertNotNull(retrieved)
        assertEquals("MockDB", retrieved.name)
    }

    @Test
    fun `should retrieve adapter by lowercase name`() {
        val adapter = MockDatabaseAdapter()
        AdapterRegistry.register(adapter)

        val retrieved = AdapterRegistry.get("mockdb")
        assertNotNull(retrieved)
        assertEquals("MockDB", retrieved.name)
    }

    @Test
    fun `should unregister adapter`() {
        val adapter = MockDatabaseAdapter()
        AdapterRegistry.register(adapter)
        
        assertNotNull(AdapterRegistry.get("MockDB"))
        
        AdapterRegistry.unregister("MockDB")
        assertNull(AdapterRegistry.get("MockDB"))
    }

    @Test
    fun `should list all registered adapters`() {
        val adapter1 = object : MockDatabaseAdapter() {
            override val name = "DB1"
        }
        val adapter2 = object : MockDatabaseAdapter() {
            override val name = "DB2"
        }

        AdapterRegistry.register(adapter1)
        AdapterRegistry.register(adapter2)

        val list = AdapterRegistry.list()
        assertEquals(2, list.size)
        assertTrue(list.contains("db1"))
        assertTrue(list.contains("db2"))
    }

    @Test
    fun `should get adapter by JDBC URL`() {
        // Register mock adapters for different databases
        val postgresAdapter = object : MockDatabaseAdapter() {
            override val name = "PostgreSQL"
        }
        val mysqlAdapter = object : MockDatabaseAdapter() {
            override val name = "MySQL"
        }
        val sqliteAdapter = object : MockDatabaseAdapter() {
            override val name = "SQLite"
        }

        AdapterRegistry.register(postgresAdapter)
        AdapterRegistry.register(mysqlAdapter)
        AdapterRegistry.register(sqliteAdapter)

        assertEquals("PostgreSQL", AdapterRegistry.getByUrl("jdbc:postgresql://localhost/db")?.name)
        assertEquals("MySQL", AdapterRegistry.getByUrl("jdbc:mysql://localhost/db")?.name)
        assertEquals("SQLite", AdapterRegistry.getByUrl("jdbc:sqlite:test.db")?.name)
        assertNull(AdapterRegistry.getByUrl("jdbc:unknown://localhost/db"))
    }

    @Test
    fun `should return null for unknown JDBC URL`() {
        assertNull(AdapterRegistry.getByUrl("jdbc:unknown://localhost/db"))
    }

    @Test
    fun `should clear all registered adapters`() {
        val adapter1 = object : MockDatabaseAdapter() {
            override val name = "DB1"
        }
        val adapter2 = object : MockDatabaseAdapter() {
            override val name = "DB2"
        }

        AdapterRegistry.register(adapter1)
        AdapterRegistry.register(adapter2)
        
        assertEquals(2, AdapterRegistry.list().size)
        
        AdapterRegistry.clear()
        assertEquals(0, AdapterRegistry.list().size)
    }

    @Test
    fun `should handle concurrent registration`() {
        val adapters = (1..10).map { i ->
            object : MockDatabaseAdapter() {
                override val name = "DB$i"
            }
        }

        // Register adapters from multiple threads
        val threads = adapters.map { adapter ->
            Thread { AdapterRegistry.register(adapter) }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(10, AdapterRegistry.list().size)
    }
}