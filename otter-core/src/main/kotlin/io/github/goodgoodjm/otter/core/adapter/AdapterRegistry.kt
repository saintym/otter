package io.github.goodgoodjm.otter.core.adapter

import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for database adapters
 */
object AdapterRegistry {
    private val adapters = ConcurrentHashMap<String, DatabaseAdapter>()
    private var autoLoadExecuted = false

    /**
     * Register a database adapter
     */
    fun register(adapter: DatabaseAdapter) {
        adapters[adapter.name.lowercase()] = adapter
    }

    /**
     * Unregister a database adapter
     */
    fun unregister(name: String) {
        adapters.remove(name.lowercase())
    }

    /**
     * Get an adapter by name
     */
    fun get(name: String): DatabaseAdapter? {
        ensureAutoLoaded()
        return adapters[name.lowercase()]
    }

    /**
     * Get an adapter by JDBC URL
     */
    fun getByUrl(jdbcUrl: String): DatabaseAdapter? {
        ensureAutoLoaded()
        return when {
            jdbcUrl.startsWith("jdbc:postgresql:") -> get("postgresql")
            jdbcUrl.startsWith("jdbc:mysql:") -> get("mysql")
            jdbcUrl.startsWith("jdbc:mariadb:") -> get("mariadb")
            jdbcUrl.startsWith("jdbc:sqlite:") -> get("sqlite")
            jdbcUrl.startsWith("jdbc:h2:") -> get("h2")
            jdbcUrl.startsWith("jdbc:sqlserver:") -> get("sqlserver")
            jdbcUrl.startsWith("jdbc:oracle:") -> get("oracle")
            else -> null
        }
    }

    /**
     * List all registered adapter names
     */
    fun list(): List<String> {
        ensureAutoLoaded()
        return adapters.keys.toList()
    }

    /**
     * Clear all registered adapters
     */
    fun clear() {
        adapters.clear()
        autoLoadExecuted = false
    }

    /**
     * Auto-load adapters using ServiceLoader
     */
    fun autoLoad() {
        if (autoLoadExecuted) return
        
        try {
            val loader = ServiceLoader.load(DatabaseAdapter::class.java)
            loader.forEach { adapter ->
                register(adapter)
            }
            autoLoadExecuted = true
        } catch (e: Exception) {
            // Ignore errors during auto-loading
        }
    }

    private fun ensureAutoLoaded() {
        if (!autoLoadExecuted) {
            autoLoad()
        }
    }
}