package io.github.goodgoodjm.otter.core.adapter

import io.github.goodgoodjm.otter.core.adapter.connection.ConnectionProvider
import io.github.goodgoodjm.otter.core.adapter.ddl.DDLProvider
import io.github.goodgoodjm.otter.core.adapter.lock.LockProvider
import io.github.goodgoodjm.otter.core.adapter.type.TypeMapper
import javax.sql.DataSource
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * Test DatabaseAdapter for testing
 */
class TestDatabaseAdapter : DatabaseAdapter {
    private var dataSource: DataSource? = null
    private lateinit var connectionProvider: ConnectionProvider
    private lateinit var ddlProvider: DDLProvider
    private lateinit var lockProvider: LockProvider
    private lateinit var typeMapper: TypeMapper

    override val name: String = "H2"
    override val version: String = "2.1.0"

    override fun initialize(config: DatabaseConfig) {
        // Initialize with real H2 database for testing
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
            driverClassName = "org.h2.Driver"
            username = "sa"
            password = ""
            maximumPoolSize = 5
            minimumIdle = 2
        }

        dataSource = HikariDataSource(hikariConfig)
        connectionProvider = H2ConnectionProvider(dataSource as HikariDataSource)
        ddlProvider = H2DDLProvider()
        lockProvider = MockLockProvider()
        typeMapper = H2TypeMapper()
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
            supportsReturning = false,
            supportsJson = false,
            supportsArrays = false,
            supportsUuid = false,
            supportsAdvisoryLocks = false,
            supportsGeneratedColumns = false,
            maxIdentifierLength = 255,
            defaultSchema = "PUBLIC"
        )
    }

    override fun validate(): ValidationResult {
        return ValidationResult.Success
    }

    override fun close() {
        (dataSource as? HikariDataSource)?.close()
    }
}

/**
 * H2 Connection Provider for testing
 */
class H2ConnectionProvider(private val dataSource: HikariDataSource) : ConnectionProvider {
    override fun getConnection() = dataSource.connection

    override fun <T> useConnection(block: (java.sql.Connection) -> T): T {
        return dataSource.connection.use { conn ->
            block(conn)
        }
    }

    override fun <T> useTransaction(
        isolationLevel: io.github.goodgoodjm.otter.core.adapter.connection.IsolationLevel,
        readOnly: Boolean,
        block: (io.github.goodgoodjm.otter.core.adapter.connection.TransactionContext) -> T
    ): T {
        return useConnection { conn ->
            val previousAutoCommit = conn.autoCommit
            val previousIsolationLevel = conn.transactionIsolation

            try {
                conn.autoCommit = false
                conn.transactionIsolation = when (isolationLevel) {
                    io.github.goodgoodjm.otter.core.adapter.connection.IsolationLevel.READ_UNCOMMITTED -> java.sql.Connection.TRANSACTION_READ_UNCOMMITTED
                    io.github.goodgoodjm.otter.core.adapter.connection.IsolationLevel.READ_COMMITTED -> java.sql.Connection.TRANSACTION_READ_COMMITTED
                    io.github.goodgoodjm.otter.core.adapter.connection.IsolationLevel.REPEATABLE_READ -> java.sql.Connection.TRANSACTION_REPEATABLE_READ
                    io.github.goodgoodjm.otter.core.adapter.connection.IsolationLevel.SERIALIZABLE -> java.sql.Connection.TRANSACTION_SERIALIZABLE
                }
                conn.isReadOnly = readOnly

                val context = H2TransactionContext(conn)
                val result = block(context)
                conn.commit()
                result
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = previousAutoCommit
                conn.transactionIsolation = previousIsolationLevel
            }
        }
    }

    override fun close() {
        dataSource.close()
    }
}

/**
 * H2 Transaction Context for testing
 */
class H2TransactionContext(override val connection: java.sql.Connection) : io.github.goodgoodjm.otter.core.adapter.connection.TransactionContext {

    override fun execute(sql: String, params: List<Any?>): Int {
        return connection.prepareStatement(sql).use { stmt ->
            params.forEachIndexed { index, param ->
                stmt.setObject(index + 1, param)
            }
            stmt.executeUpdate()
        }
    }

    override fun <T> query(sql: String, params: List<Any?>, mapper: (java.sql.ResultSet) -> T): List<T> {
        return connection.prepareStatement(sql).use { stmt ->
            params.forEachIndexed { index, param ->
                stmt.setObject(index + 1, param)
            }
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<T>()
                while (rs.next()) {
                    results.add(mapper(rs))
                }
                results
            }
        }
    }

    override fun <T> queryOne(sql: String, params: List<Any?>, mapper: (java.sql.ResultSet) -> T): T? {
        return query(sql, params, mapper).firstOrNull()
    }

    override fun setSavepoint(name: String): java.sql.Savepoint {
        return connection.setSavepoint(name)
    }

    override fun rollbackToSavepoint(savepoint: java.sql.Savepoint) {
        connection.rollback(savepoint)
    }

    override fun releaseSavepoint(savepoint: java.sql.Savepoint) {
        connection.releaseSavepoint(savepoint)
    }

    override fun commit() {
        connection.commit()
    }

    override fun rollback() {
        connection.rollback()
    }
}