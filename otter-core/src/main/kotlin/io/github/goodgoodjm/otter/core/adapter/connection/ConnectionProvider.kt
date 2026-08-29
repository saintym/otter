package io.github.goodgoodjm.otter.core.adapter.connection

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Savepoint

/**
 * Provides database connections and transaction management
 */
interface ConnectionProvider {
    /**
     * Get a raw database connection
     */
    fun getConnection(): Connection

    /**
     * Execute a block with a connection, automatically closing it afterwards
     */
    fun <T> useConnection(block: (Connection) -> T): T

    /**
     * Execute a block within a transaction
     */
    fun <T> useTransaction(
        isolationLevel: IsolationLevel = IsolationLevel.READ_COMMITTED,
        readOnly: Boolean = false,
        block: (TransactionContext) -> T
    ): T

    /**
     * Close all connections and cleanup resources
     */
    fun close()
}

/**
 * Transaction isolation levels
 */
enum class IsolationLevel(val value: Int) {
    READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE)
}

/**
 * Transaction context for executing operations within a transaction
 */
interface TransactionContext {
    val connection: Connection

    /**
     * Execute a SQL statement with optional parameters
     * @return number of affected rows
     */
    fun execute(sql: String, params: List<Any?> = emptyList()): Int

    /**
     * Execute a query and map results
     */
    fun <T> query(sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): List<T>

    /**
     * Execute a query and return single result
     */
    fun <T> queryOne(sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): T?

    /**
     * Create a savepoint
     */
    fun setSavepoint(name: String): Savepoint

    /**
     * Rollback to a savepoint
     */
    fun rollbackToSavepoint(savepoint: Savepoint)

    /**
     * Release a savepoint
     */
    fun releaseSavepoint(savepoint: Savepoint)

    /**
     * Commit the current transaction
     */
    fun commit()

    /**
     * Rollback the current transaction
     */
    fun rollback()
}