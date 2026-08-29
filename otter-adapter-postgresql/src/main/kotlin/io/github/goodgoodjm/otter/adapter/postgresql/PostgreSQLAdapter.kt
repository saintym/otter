package io.github.goodgoodjm.otter.adapter.postgresql

import io.github.goodgoodjm.otter.core.adapter.*
import io.github.goodgoodjm.otter.core.adapter.connection.ConnectionProvider
import io.github.goodgoodjm.otter.core.adapter.ddl.DDLProvider
import io.github.goodgoodjm.otter.core.adapter.lock.LockProvider
import io.github.goodgoodjm.otter.core.adapter.type.TypeMapper
import java.sql.SQLException

/**
 * PostgreSQL database adapter implementation
 */
class PostgreSQLAdapter : DatabaseAdapter {
    override val name = "PostgreSQL"
    override val version = "9.6+"

    private lateinit var config: DatabaseConfig
    private lateinit var connectionProvider: PostgreSQLConnectionProvider
    private lateinit var ddlProvider: PostgreSQLDDLProvider
    private lateinit var lockProvider: PostgreSQLLockProvider
    private lateinit var typeMapper: PostgreSQLTypeMapper

    override fun initialize(config: DatabaseConfig) {
        this.config = config
        this.connectionProvider = PostgreSQLConnectionProvider(config)
        this.ddlProvider = PostgreSQLDDLProvider()
        this.lockProvider = PostgreSQLLockProvider(connectionProvider)
        this.typeMapper = PostgreSQLTypeMapper()
    }

    override fun getConnectionProvider(): ConnectionProvider = connectionProvider

    override fun getDDLProvider(): DDLProvider = ddlProvider

    override fun getLockProvider(): LockProvider = lockProvider

    override fun getTypeMapper(): TypeMapper = typeMapper

    override fun getCapabilities(): DatabaseCapabilities {
        return DatabaseCapabilities(
            supportsTransactions = true,
            supportsSavepoints = true,
            supportsSchemas = true,
            supportsCascadeDelete = true,
            supportsIfNotExists = true,
            supportsReturning = true,
            supportsJson = true,
            supportsArrays = true,
            supportsUuid = true,
            supportsAdvisoryLocks = true,
            supportsGeneratedColumns = true,
            maxIdentifierLength = 63,
            defaultSchema = "public"
        )
    }

    override fun validate(): ValidationResult {
        return try {
            connectionProvider.useConnection { conn ->
                val metaData = conn.metaData
                val version = metaData.databaseProductVersion
                val majorVersion = metaData.databaseMajorVersion
                val minorVersion = metaData.databaseMinorVersion

                when {
                    majorVersion < 9 || (majorVersion == 9 && minorVersion < 6) ->
                        ValidationResult.Error("PostgreSQL version $version is not supported. Minimum version is 9.6")
                    majorVersion == 9 ->
                        ValidationResult.Warning("PostgreSQL $version is supported but consider upgrading to version 10 or later")
                    else ->
                        ValidationResult.Success
                }
            }
        } catch (e: SQLException) {
            ValidationResult.Error("Failed to connect to PostgreSQL: ${e.message}")
        } catch (e: Exception) {
            ValidationResult.Error("Unexpected error during validation: ${e.message}")
        }
    }

    override fun close() {
        if (::connectionProvider.isInitialized) {
            connectionProvider.close()
        }
    }
}