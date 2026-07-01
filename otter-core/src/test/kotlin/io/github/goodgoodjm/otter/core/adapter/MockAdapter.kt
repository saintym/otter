package io.github.goodgoodjm.otter.core.adapter

import io.github.goodgoodjm.otter.core.adapter.connection.ConnectionProvider
import io.github.goodgoodjm.otter.core.adapter.ddl.DDLProvider
import io.github.goodgoodjm.otter.core.adapter.lock.LockProvider
import io.github.goodgoodjm.otter.core.adapter.lock.ConcurrentLockProvider
import io.github.goodgoodjm.otter.core.adapter.type.TypeMapper

/**
 * Mock DatabaseAdapter for testing
 */
class MockAdapter : DatabaseAdapter {
    override val name = "MockAdapter"
    override val version = "1.0.0"

    private lateinit var connectionProvider: ConnectionProvider
    private lateinit var ddlProvider: DDLProvider
    private val lockProvider = ConcurrentLockProvider()
    private lateinit var typeMapper: TypeMapper

    override fun initialize(config: DatabaseConfig) {
        connectionProvider = MockConnectionProvider()
        ddlProvider = MockDDLProvider()
        typeMapper = MockTypeMapper()
    }

    override fun getConnectionProvider(): ConnectionProvider = connectionProvider
    override fun getDDLProvider(): DDLProvider = ddlProvider
    override fun getLockProvider(): LockProvider = lockProvider
    override fun getTypeMapper(): TypeMapper = typeMapper

    override fun getCapabilities(): DatabaseCapabilities {
        return DatabaseCapabilities(
            supportsTransactions = true,
            supportsSchemas = false
        )
    }

    override fun validate(): ValidationResult {
        return ValidationResult.Success
    }

    override fun close() {
        // Mock close
    }
}