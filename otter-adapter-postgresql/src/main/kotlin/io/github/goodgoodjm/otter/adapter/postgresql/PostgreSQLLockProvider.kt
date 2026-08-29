package io.github.goodgoodjm.otter.adapter.postgresql

import io.github.goodgoodjm.otter.core.adapter.connection.ConnectionProvider
import io.github.goodgoodjm.otter.core.adapter.lock.LockInfo
import io.github.goodgoodjm.otter.core.adapter.lock.LockProvider
import java.net.InetAddress
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.time.Instant

/**
 * PostgreSQL lock provider using advisory locks.
 *
 * PostgreSQL의 세션 레벨 advisory lock은 획득한 커넥션(세션)에 종속되므로,
 * 커넥션 풀에서 매번 커넥션을 반납하면 락이 유지되지 않는다.
 * 따라서 락 보유 동안 하나의 전용 커넥션을 잡아 두고, 보유 중인 락을 자체적으로
 * 추적하여 (PostgreSQL 세션 락의 재진입 특성과 무관하게) 계약을 일관되게 지킨다.
 */
class PostgreSQLLockProvider(
    private val connectionProvider: ConnectionProvider
) : LockProvider {

    companion object {
        // Use a fixed namespace for Otter locks
        private const val OTTER_NAMESPACE = 0x4F747465L  // "Otte" in hex
    }

    /** 락 보유용 전용 커넥션 (세션 유지) */
    private var dedicatedConnection: Connection? = null

    /** 이 프로바이더가 보유 중인 락 ID */
    private val heldLocks = mutableSetOf<String>()

    private fun connection(): Connection {
        var conn = dedicatedConnection
        if (conn == null || conn.isClosed) {
            conn = connectionProvider.getConnection().apply { autoCommit = true }
            dedicatedConnection = conn
        }
        return conn
    }

    @Synchronized
    override fun acquireLock(lockId: String, timeout: Duration): Boolean {
        // 이미 보유 중이면 재획득 실패로 처리 (세션 락의 재진입 방지)
        if (lockId in heldLocks) return false

        val lockKey = getLockKey(lockId)
        return try {
            val conn = connection()
            val endTime = System.currentTimeMillis() + maxOf(0L, timeout.toMillis())
            var acquired = false

            do {
                conn.prepareStatement("SELECT pg_try_advisory_lock(?) AS acquired").use { stmt ->
                    stmt.setLong(1, lockKey)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) acquired = rs.getBoolean("acquired")
                    }
                }
                if (!acquired && System.currentTimeMillis() < endTime) {
                    Thread.sleep(100)  // Wait 100ms before retry
                }
            } while (!acquired && System.currentTimeMillis() < endTime)

            if (acquired) heldLocks.add(lockId)
            acquired
        } catch (e: SQLException) {
            false
        }
    }

    @Synchronized
    override fun releaseLock(lockId: String): Boolean {
        if (lockId !in heldLocks) return false

        val lockKey = getLockKey(lockId)
        return try {
            val conn = connection()
            var released = false
            conn.prepareStatement("SELECT pg_advisory_unlock(?) AS released").use { stmt ->
                stmt.setLong(1, lockKey)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) released = rs.getBoolean("released")
                }
            }
            if (released) heldLocks.remove(lockId)
            released
        } catch (e: SQLException) {
            false
        }
    }

    override fun isLocked(lockId: String): Boolean {
        val lockKey = getLockKey(lockId)

        return connectionProvider.useConnection { conn ->
            try {
                // Check if lock is held by querying pg_locks (전 세션 대상 조회).
                // 단일 키 advisory lock은 classid(상위 32비트)/objid(하위 32비트)/objsubid=1로 저장되므로
                // 이를 다시 bigint 키로 조합해 비교한다.
                val stmt = conn.prepareStatement("""
                    |SELECT EXISTS (
                    |    SELECT 1 FROM pg_locks
                    |    WHERE locktype = 'advisory'
                    |    AND objsubid = 1
                    |    AND ((classid::bigint << 32) | objid::bigint) = ?
                    |) AS is_locked
                """.trimMargin())

                stmt.setLong(1, lockKey)

                stmt.executeQuery().use { rs ->
                    rs.next() && rs.getBoolean("is_locked")
                }
            } catch (e: SQLException) {
                false
            }
        }
    }

    override fun getLockInfo(lockId: String): LockInfo? {
        val lockKey = getLockKey(lockId)

        return connectionProvider.useConnection { conn ->
            try {
                val stmt = conn.prepareStatement("""
                    |SELECT
                    |    l.pid,
                    |    l.granted,
                    |    a.application_name,
                    |    a.client_addr,
                    |    a.client_hostname
                    |FROM pg_locks l
                    |JOIN pg_stat_activity a ON l.pid = a.pid
                    |WHERE l.locktype = 'advisory'
                    |    AND l.objsubid = 1
                    |    AND ((l.classid::bigint << 32) | l.objid::bigint) = ?
                    |    AND l.granted = true
                """.trimMargin())

                stmt.setLong(1, lockKey)

                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        LockInfo(
                            lockId = lockId,
                            holder = rs.getString("application_name") ?: "unknown",
                            acquiredAt = Instant.now(),  // PostgreSQL doesn't track acquisition time
                            expiresAt = null,  // Advisory locks don't expire
                            processId = rs.getLong("pid"),
                            hostname = rs.getString("client_hostname") ?:
                                      rs.getString("client_addr") ?:
                                      InetAddress.getLocalHost().hostName
                        )
                    } else {
                        null
                    }
                }
            } catch (e: SQLException) {
                null
            }
        }
    }

    @Synchronized
    override fun releaseAllLocks() {
        val conn = dedicatedConnection ?: run {
            heldLocks.clear()
            return
        }
        try {
            conn.prepareStatement("SELECT pg_advisory_unlock_all()").use { it.execute() }
        } catch (e: SQLException) {
            // Ignore errors
        } finally {
            heldLocks.clear()
            try { conn.close() } catch (e: SQLException) { /* ignore */ }
            dedicatedConnection = null
        }
    }

    override fun supportsAdvisoryLocks(): Boolean {
        return true
    }

    private fun getLockKey(lockId: String): Long {
        // namespace(상위 32비트) + lockId 해시(하위 32비트)를 하나의 bigint 키로 결합한다.
        // 단일 인자 pg_advisory_lock(bigint) 형식을 사용하기 위함.
        return (OTTER_NAMESPACE shl 32) or (lockId.hashCode().toLong() and 0xFFFFFFFFL)
    }
}
