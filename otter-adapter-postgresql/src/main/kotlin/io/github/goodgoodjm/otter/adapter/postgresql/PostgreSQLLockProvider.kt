package io.github.goodgoodjm.otter.adapter.postgresql

import io.github.goodgoodjm.otter.core.adapter.connection.ConnectionProvider
import io.github.goodgoodjm.otter.core.adapter.lock.LockInfo
import io.github.goodgoodjm.otter.core.adapter.lock.LockProvider
import java.net.InetAddress
import java.sql.SQLException
import java.time.Duration
import java.time.Instant

/**
 * PostgreSQL lock provider using advisory locks
 */
class PostgreSQLLockProvider(
    private val connectionProvider: ConnectionProvider
) : LockProvider {

    companion object {
        // Use a fixed namespace for Otter locks
        private const val OTTER_NAMESPACE = 0x4F747465L  // "Otte" in hex
    }

    override fun acquireLock(lockId: String, timeout: Duration): Boolean {
        val lockKey = getLockKey(lockId)
        
        return connectionProvider.useConnection { conn ->
            try {
                // Try to acquire the lock with timeout
                val sql = if (timeout.seconds > 0) {
                    // pg_try_advisory_lock doesn't support timeout, so we use a loop
                    val endTime = System.currentTimeMillis() + timeout.toMillis()
                    var acquired = false
                    
                    while (System.currentTimeMillis() < endTime && !acquired) {
                        val stmt = conn.prepareStatement("SELECT pg_try_advisory_lock(?, ?) AS acquired")
                        stmt.setLong(1, OTTER_NAMESPACE)
                        stmt.setLong(2, lockKey)
                        
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                acquired = rs.getBoolean("acquired")
                            }
                        }
                        
                        if (!acquired) {
                            Thread.sleep(100)  // Wait 100ms before retry
                        }
                    }
                    
                    acquired
                } else {
                    // No timeout, try once
                    val stmt = conn.prepareStatement("SELECT pg_try_advisory_lock(?, ?) AS acquired")
                    stmt.setLong(1, OTTER_NAMESPACE)
                    stmt.setLong(2, lockKey)
                    
                    stmt.executeQuery().use { rs ->
                        rs.next() && rs.getBoolean("acquired")
                    }
                }
                
                sql
            } catch (e: SQLException) {
                false
            }
        }
    }

    override fun releaseLock(lockId: String): Boolean {
        val lockKey = getLockKey(lockId)
        
        return connectionProvider.useConnection { conn ->
            try {
                val stmt = conn.prepareStatement("SELECT pg_advisory_unlock(?, ?) AS released")
                stmt.setLong(1, OTTER_NAMESPACE)
                stmt.setLong(2, lockKey)
                
                stmt.executeQuery().use { rs ->
                    rs.next() && rs.getBoolean("released")
                }
            } catch (e: SQLException) {
                false
            }
        }
    }

    override fun isLocked(lockId: String): Boolean {
        val lockKey = getLockKey(lockId)
        
        return connectionProvider.useConnection { conn ->
            try {
                // Check if lock is held by querying pg_locks
                val stmt = conn.prepareStatement("""
                    |SELECT EXISTS (
                    |    SELECT 1 FROM pg_locks 
                    |    WHERE locktype = 'advisory' 
                    |    AND classid = ? 
                    |    AND objid = ?
                    |) AS is_locked
                """.trimMargin())
                
                stmt.setLong(1, OTTER_NAMESPACE)
                stmt.setLong(2, lockKey)
                
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
                    |    l.granted_at,
                    |    a.application_name,
                    |    a.client_addr,
                    |    a.client_hostname
                    |FROM pg_locks l
                    |JOIN pg_stat_activity a ON l.pid = a.pid
                    |WHERE l.locktype = 'advisory' 
                    |    AND l.classid = ? 
                    |    AND l.objid = ?
                    |    AND l.granted = true
                """.trimMargin())
                
                stmt.setLong(1, OTTER_NAMESPACE)
                stmt.setLong(2, lockKey)
                
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

    override fun releaseAllLocks() {
        connectionProvider.useConnection { conn ->
            try {
                // Release all advisory locks for the current session
                val stmt = conn.prepareStatement("SELECT pg_advisory_unlock_all()")
                stmt.execute()
            } catch (e: SQLException) {
                // Ignore errors
            }
        }
    }

    override fun supportsAdvisoryLocks(): Boolean {
        return true
    }

    private fun getLockKey(lockId: String): Long {
        // Convert string lock ID to a long hash
        return lockId.hashCode().toLong() and 0x7FFFFFFFFFFFFFFFL  // Ensure positive
    }
}