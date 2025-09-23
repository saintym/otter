package io.github.goodgoodjm.otter.adapter.postgresql

import io.github.goodgoodjm.otter.core.adapter.*
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgreSQLAdapterTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("test_db")
            .withUsername("test_user")
            .withPassword("test_pass")
    }

    private lateinit var adapter: PostgreSQLAdapter
    private lateinit var config: DatabaseConfig

    @BeforeAll
    fun setUpAll() {
        postgres.start()
    }

    @BeforeEach
    fun setUp() {
        config = DatabaseConfig(
            url = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password,
            driverClassName = "org.postgresql.Driver"
        )

        adapter = PostgreSQLAdapter()
        adapter.initialize(config)
    }

    @AfterEach
    fun tearDown() {
        adapter.close()
    }

    @AfterAll
    fun tearDownAll() {
        postgres.stop()
    }

    @Test
    fun `adapter should have correct name and version`() {
        assertEquals("PostgreSQL", adapter.name)
        assertEquals("9.6+", adapter.version)
    }

    @Test
    fun `adapter should provide all required providers`() {
        assertNotNull(adapter.getConnectionProvider())
        assertNotNull(adapter.getDDLProvider())
        assertNotNull(adapter.getLockProvider())
        assertNotNull(adapter.getTypeMapper())
    }

    @Test
    fun `adapter should report PostgreSQL capabilities`() {
        val capabilities = adapter.getCapabilities()
        
        assertTrue(capabilities.supportsTransactions)
        assertTrue(capabilities.supportsSavepoints)
        assertTrue(capabilities.supportsSchemas)
        assertTrue(capabilities.supportsCascadeDelete)
        assertTrue(capabilities.supportsIfNotExists)
        assertTrue(capabilities.supportsReturning)
        assertTrue(capabilities.supportsJson)
        assertTrue(capabilities.supportsArrays)
        assertTrue(capabilities.supportsUuid)
        assertTrue(capabilities.supportsAdvisoryLocks)
        assertEquals(63, capabilities.maxIdentifierLength)
        assertEquals("public", capabilities.defaultSchema)
    }

    @Test
    fun `adapter should validate connection successfully`() {
        val result = adapter.validate()
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `adapter should handle invalid connection`() {
        val invalidConfig = DatabaseConfig(
            url = "jdbc:postgresql://invalid:5432/test",
            username = "user",
            password = "pass"
        )

        val invalidAdapter = PostgreSQLAdapter()
        invalidAdapter.initialize(invalidConfig)
        
        val result = invalidAdapter.validate()
        assertTrue(result is ValidationResult.Error)
    }

    @Test
    fun `adapter should register with AdapterRegistry`() {
        AdapterRegistry.clear()
        AdapterRegistry.register(adapter)
        
        val retrieved = AdapterRegistry.get("PostgreSQL")
        assertNotNull(retrieved)
        assertEquals("PostgreSQL", retrieved.name)
    }

    @Test
    fun `adapter should be retrieved by JDBC URL`() {
        AdapterRegistry.clear()
        AdapterRegistry.register(adapter)
        
        val retrieved = AdapterRegistry.getByUrl("jdbc:postgresql://localhost/db")
        assertNotNull(retrieved)
        assertEquals("PostgreSQL", retrieved.name)
    }
}