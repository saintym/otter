package io.github.goodgoodjm.otter.core.adapter

import io.github.goodgoodjm.otter.core.adapter.connection.ConnectionProvider
import io.github.goodgoodjm.otter.core.adapter.ddl.DDLProvider
import io.github.goodgoodjm.otter.core.adapter.lock.LockProvider
import io.github.goodgoodjm.otter.core.adapter.type.TypeMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import kotlin.test.*

class DatabaseAdapterTest {

    private lateinit var mockAdapter: MockDatabaseAdapter

    @BeforeEach
    fun setUp() {
        mockAdapter = MockDatabaseAdapter()
    }

    @AfterEach
    fun tearDown() {
        mockAdapter.close()
    }

    @Test
    fun `adapter should have name and version`() {
        assertEquals("MockDB", mockAdapter.name)
        assertEquals("1.0", mockAdapter.version)
    }

    @Test
    fun `adapter should be initialized before use`() {
        val config = DatabaseConfig(
            url = "jdbc:mock://localhost/test",
            username = "user",
            password = "pass"
        )

        mockAdapter.initialize(config)
        assertTrue(mockAdapter.isInitialized)
    }

    @Test
    fun `adapter should provide all required providers`() {
        val config = DatabaseConfig(
            url = "jdbc:mock://localhost/test",
            username = "user",
            password = "pass"
        )
        mockAdapter.initialize(config)

        assertNotNull(mockAdapter.getConnectionProvider())
        assertNotNull(mockAdapter.getDDLProvider())
        assertNotNull(mockAdapter.getLockProvider())
        assertNotNull(mockAdapter.getTypeMapper())
        assertNotNull(mockAdapter.getCapabilities())
    }

    @Test
    fun `adapter should validate connection`() {
        val config = DatabaseConfig(
            url = "jdbc:mock://localhost/test",
            username = "user",
            password = "pass"
        )
        mockAdapter.initialize(config)

        val result = mockAdapter.validate()
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `adapter should report capabilities`() {
        val capabilities = mockAdapter.getCapabilities()

        assertTrue(capabilities.supportsTransactions)
        assertTrue(capabilities.supportsSavepoints)
        assertEquals(63, capabilities.maxIdentifierLength)
    }

    @Test
    fun `adapter should handle invalid configuration`() {
        val config = DatabaseConfig(
            url = "invalid-url",
            username = "user",
            password = "pass"
        )
        mockAdapter.initialize(config)
        mockAdapter.shouldFailValidation = true

        val result = mockAdapter.validate()
        assertTrue(result is ValidationResult.Error)
    }
}

/**
 * Mock implementation of DatabaseAdapter for testing
 */
class MockDatabaseAdapter : DatabaseAdapter {
    override val name = "MockDB"
    override val version = "1.0"
    
    var isInitialized = false
    var isClosed = false
    var shouldFailValidation = false

    private lateinit var connectionProvider: ConnectionProvider
    private lateinit var ddlProvider: DDLProvider
    private lateinit var lockProvider: LockProvider
    private lateinit var typeMapper: TypeMapper

    override fun initialize(config: DatabaseConfig) {
        isInitialized = true
        // Initialize mock providers
        connectionProvider = MockConnectionProvider()
        ddlProvider = MockDDLProvider()
        lockProvider = MockLockProvider()
        typeMapper = MockTypeMapper()
    }

    override fun getConnectionProvider(): ConnectionProvider {
        require(isInitialized) { "Adapter must be initialized" }
        return connectionProvider
    }

    override fun getDDLProvider(): DDLProvider {
        require(isInitialized) { "Adapter must be initialized" }
        return ddlProvider
    }

    override fun getLockProvider(): LockProvider {
        require(isInitialized) { "Adapter must be initialized" }
        return lockProvider
    }

    override fun getTypeMapper(): TypeMapper {
        require(isInitialized) { "Adapter must be initialized" }
        return typeMapper
    }

    override fun getCapabilities(): DatabaseCapabilities {
        return DatabaseCapabilities(
            supportsTransactions = true,
            supportsSavepoints = true,
            supportsSchemas = false,
            supportsJson = false,
            supportsArrays = false,
            maxIdentifierLength = 63
        )
    }

    override fun validate(): ValidationResult {
        return if (shouldFailValidation) {
            ValidationResult.Error("Mock validation failed")
        } else {
            ValidationResult.Success
        }
    }

    override fun close() {
        isClosed = true
    }
}