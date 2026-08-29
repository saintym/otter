package io.github.goodgoodjm.otter.core.adapter

import io.github.goodgoodjm.otter.core.adapter.connection.*
import io.github.goodgoodjm.otter.core.adapter.ddl.DDLProvider
import io.github.goodgoodjm.otter.core.adapter.lock.LockInfo
import io.github.goodgoodjm.otter.core.adapter.lock.LockProvider
import io.github.goodgoodjm.otter.core.adapter.model.*
import io.github.goodgoodjm.otter.core.dsl.ForeignKeyDefinition
import io.github.goodgoodjm.otter.core.adapter.type.DefaultValue
import io.github.goodgoodjm.otter.core.adapter.type.TypeMapper
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Savepoint
import java.time.Duration
import java.time.Instant

/**
 * Mock ConnectionProvider for testing
 */
class MockConnectionProvider : ConnectionProvider {
    var connectionCount = 0
    var transactionCount = 0

    override fun getConnection(): Connection {
        connectionCount++
        throw NotImplementedError("Mock connection not implemented")
    }

    override fun <T> useConnection(block: (Connection) -> T): T {
        connectionCount++
        throw NotImplementedError("Mock connection not implemented")
    }

    override fun <T> useTransaction(
        isolationLevel: IsolationLevel,
        readOnly: Boolean,
        block: (TransactionContext) -> T
    ): T {
        transactionCount++
        val context = MockTransactionContext()
        return block(context)
    }

    override fun close() {
        // Mock close
    }
}

/**
 * Mock TransactionContext for testing
 */
class MockTransactionContext : TransactionContext {
    override val connection: Connection
        get() = throw NotImplementedError("Mock connection not implemented")

    var executeCount = 0
    var queryCount = 0
    var commitCount = 0
    var rollbackCount = 0
    val savepoints = mutableMapOf<String, MockSavepoint>()

    override fun execute(sql: String, params: List<Any?>): Int {
        executeCount++
        return 1  // Assume 1 row affected
    }

    override fun <T> query(sql: String, params: List<Any?>, mapper: (ResultSet) -> T): List<T> {
        queryCount++
        return emptyList()
    }

    override fun <T> queryOne(sql: String, params: List<Any?>, mapper: (ResultSet) -> T): T? {
        queryCount++
        return null
    }

    override fun setSavepoint(name: String): Savepoint {
        val savepoint = MockSavepoint(name)
        savepoints[name] = savepoint
        return savepoint
    }

    override fun rollbackToSavepoint(savepoint: Savepoint) {
        rollbackCount++
    }

    override fun releaseSavepoint(savepoint: Savepoint) {
        if (savepoint is MockSavepoint) {
            savepoints.remove(savepoint.name)
        }
    }

    override fun commit() {
        commitCount++
    }

    override fun rollback() {
        rollbackCount++
    }
}

/**
 * Mock Savepoint for testing
 */
class MockSavepoint(val name: String) : Savepoint {
    override fun getSavepointId(): Int = name.hashCode()
    override fun getSavepointName(): String = name
}

// MockDDLProvider는 별도 파일로 이동됨
// import io.github.goodgoodjm.otter.core.adapter.MockDDLProvider

/**
 * Thread-Safe LockProvider for testing
 * ConcurrentLockProvider를 직접 사용
 */
typealias MockLockProvider = io.github.goodgoodjm.otter.core.adapter.lock.ConcurrentLockProvider

/**
 * Mock TypeMapper for testing
 */
class MockTypeMapper : TypeMapper {
    override fun mapType(type: ColumnType): String {
        return when (type) {
            is ColumnType.Integer -> "INTEGER"
            is ColumnType.Varchar -> "VARCHAR(${type.length})"
            is ColumnType.Text -> "TEXT"
            is ColumnType.Boolean -> "BOOLEAN"
            is ColumnType.Timestamp -> "TIMESTAMP"
            else -> "UNKNOWN"
        }
    }

    override fun mapTypeWithModifiers(type: ColumnType, modifiers: Set<ColumnModifier>): String {
        val baseType = mapType(type)
        return if (ColumnModifier.AUTO_INCREMENT in modifiers) {
            "$baseType AUTO_INCREMENT"
        } else {
            baseType
        }
    }

    override fun supportsType(type: ColumnType): Boolean {
        return when (type) {
            is ColumnType.Integer,
            is ColumnType.Varchar,
            is ColumnType.Text,
            is ColumnType.Boolean,
            is ColumnType.Timestamp -> true
            else -> false
        }
    }

    override fun getDefaultValueExpression(value: DefaultValue): String {
        return when (value) {
            is DefaultValue.Null -> "NULL"
            is DefaultValue.Literal -> "'${value.value}'"
            is DefaultValue.CurrentTimestamp -> "CURRENT_TIMESTAMP"
            is DefaultValue.CurrentDate -> "CURRENT_DATE"
            is DefaultValue.CurrentTime -> "CURRENT_TIME"
            is DefaultValue.Expression -> value.sql
        }
    }

    override fun getSqlLiteral(value: Any?): String {
        return when (value) {
            null -> "NULL"
            is String -> "'$value'"
            is Number -> value.toString()
            is Boolean -> if (value) "TRUE" else "FALSE"
            else -> "'$value'"
        }
    }
}