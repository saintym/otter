package io.github.goodgoodjm.otter.adapter.postgresql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.goodgoodjm.otter.core.adapter.DatabaseConfig
import io.github.goodgoodjm.otter.core.adapter.connection.ConnectionProvider
import io.github.goodgoodjm.otter.core.adapter.connection.IsolationLevel
import io.github.goodgoodjm.otter.core.adapter.connection.TransactionContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Savepoint
import javax.sql.DataSource

/**
 * PostgreSQL connection provider using HikariCP connection pool
 */
class PostgreSQLConnectionProvider(config: DatabaseConfig) : ConnectionProvider {

    private val dataSource: DataSource

    init {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            driverClassName = config.driverClassName ?: "org.postgresql.Driver"
            maximumPoolSize = config.connectionPoolSize
            connectionTimeout = config.connectionTimeout
            // 풀 생성 시점에 즉시 실패하지 않도록 하여, 연결 검증은 validate()에서 처리한다
            initializationFailTimeout = -1

            // PostgreSQL specific settings
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }
        dataSource = HikariDataSource(hikariConfig)
    }

    override fun getConnection(): Connection {
        return dataSource.connection
    }

    override fun <T> useConnection(block: (Connection) -> T): T {
        return getConnection().use(block)
    }

    override fun <T> useTransaction(
        isolationLevel: IsolationLevel,
        readOnly: Boolean,
        block: (TransactionContext) -> T
    ): T {
        return useConnection { connection ->
            val previousAutoCommit = connection.autoCommit
            val previousIsolation = connection.transactionIsolation
            val previousReadOnly = connection.isReadOnly

            try {
                connection.autoCommit = false
                connection.transactionIsolation = isolationLevel.value
                connection.isReadOnly = readOnly

                val context = PostgreSQLTransactionContext(connection)
                val result = block(context)
                
                if (!connection.autoCommit) {
                    connection.commit()
                }
                
                result
            } catch (e: Exception) {
                if (!connection.autoCommit) {
                    connection.rollback()
                }
                throw e
            } finally {
                connection.autoCommit = previousAutoCommit
                connection.transactionIsolation = previousIsolation
                connection.isReadOnly = previousReadOnly
            }
        }
    }

    override fun close() {
        if (dataSource is HikariDataSource) {
            dataSource.close()
        }
    }
}

/**
 * PostgreSQL transaction context implementation
 */
class PostgreSQLTransactionContext(
    override val connection: Connection
) : TransactionContext {

    override fun execute(sql: String, params: List<Any?>): Int {
        return connection.prepareStatement(sql).use { stmt ->
            setParameters(stmt, params)
            stmt.executeUpdate()
        }
    }

    override fun <T> query(sql: String, params: List<Any?>, mapper: (ResultSet) -> T): List<T> {
        return connection.prepareStatement(sql).use { stmt ->
            setParameters(stmt, params)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<T>()
                while (rs.next()) {
                    results.add(mapper(rs))
                }
                results
            }
        }
    }

    override fun <T> queryOne(sql: String, params: List<Any?>, mapper: (ResultSet) -> T): T? {
        return connection.prepareStatement(sql).use { stmt ->
            setParameters(stmt, params)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapper(rs) else null
            }
        }
    }

    override fun setSavepoint(name: String): Savepoint {
        return connection.setSavepoint(name)
    }

    override fun rollbackToSavepoint(savepoint: Savepoint) {
        connection.rollback(savepoint)
    }

    override fun releaseSavepoint(savepoint: Savepoint) {
        connection.releaseSavepoint(savepoint)
    }

    override fun commit() {
        connection.commit()
    }

    override fun rollback() {
        connection.rollback()
    }

    private fun setParameters(stmt: PreparedStatement, params: List<Any?>) {
        params.forEachIndexed { index, param ->
            when (param) {
                null -> stmt.setNull(index + 1, java.sql.Types.NULL)
                is String -> stmt.setString(index + 1, param)
                is Int -> stmt.setInt(index + 1, param)
                is Long -> stmt.setLong(index + 1, param)
                is Double -> stmt.setDouble(index + 1, param)
                is Float -> stmt.setFloat(index + 1, param)
                is Boolean -> stmt.setBoolean(index + 1, param)
                is ByteArray -> stmt.setBytes(index + 1, param)
                is java.sql.Date -> stmt.setDate(index + 1, param)
                is java.sql.Time -> stmt.setTime(index + 1, param)
                is java.sql.Timestamp -> stmt.setTimestamp(index + 1, param)
                else -> stmt.setObject(index + 1, param)
            }
        }
    }
}