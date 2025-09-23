package io.github.goodgoodjm.otter.core.adapter

import io.github.goodgoodjm.otter.core.adapter.connection.ConnectionProvider
import io.github.goodgoodjm.otter.core.adapter.ddl.DDLProvider
import io.github.goodgoodjm.otter.core.adapter.lock.LockProvider
import io.github.goodgoodjm.otter.core.adapter.type.TypeMapper

/**
 * Database adapter interface for database-agnostic operations.
 * Each database implementation should provide its own adapter.
 */
interface DatabaseAdapter {
    /**
     * Name of the database (e.g., "PostgreSQL", "MySQL")
     */
    val name: String

    /**
     * Supported version range (e.g., "9.6+", "5.7-8.0")
     */
    val version: String

    /**
     * Get the connection provider for this database
     */
    fun getConnectionProvider(): ConnectionProvider

    /**
     * Get the DDL provider for this database
     */
    fun getDDLProvider(): DDLProvider

    /**
     * Get the lock provider for this database
     */
    fun getLockProvider(): LockProvider

    /**
     * Get the type mapper for this database
     */
    fun getTypeMapper(): TypeMapper

    /**
     * Get the capabilities of this database
     */
    fun getCapabilities(): DatabaseCapabilities

    /**
     * Initialize the adapter with configuration
     */
    fun initialize(config: DatabaseConfig)

    /**
     * Validate the database connection and version
     */
    fun validate(): ValidationResult

    /**
     * Close all resources
     */
    fun close()
}

/**
 * Database configuration
 */
data class DatabaseConfig(
    val url: String,
    val username: String,
    val password: String,
    val driverClassName: String? = null,
    val connectionPoolSize: Int = 10,
    val connectionTimeout: Long = 30000
)

/**
 * Database capabilities
 */
data class DatabaseCapabilities(
    val supportsTransactions: Boolean = true,
    val supportsSavepoints: Boolean = true,
    val supportsSchemas: Boolean = true,
    val supportsCascadeDelete: Boolean = true,
    val supportsIfNotExists: Boolean = true,
    val supportsReturning: Boolean = false,
    val supportsJson: Boolean = false,
    val supportsArrays: Boolean = false,
    val supportsUuid: Boolean = false,
    val supportsAdvisoryLocks: Boolean = false,
    val supportsGeneratedColumns: Boolean = false,
    val maxIdentifierLength: Int = 63,
    val defaultSchema: String? = null
)

/**
 * Validation result for database connection
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}