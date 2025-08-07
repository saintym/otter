package io.github.goodgoodjm.otter

import io.github.goodgoodjm.otter.core.OtterConfig
import org.jetbrains.exposed.sql.Database

object TestDatabaseConfig {
    const val DB_URL = "jdbc:postgresql://localhost:5433/otter_test"
    const val DB_USER = "postgres"
    const val DB_PASSWORD = "postgres"
    const val DB_DRIVER = "org.postgresql.Driver"
    
    fun createDatabase(): Database {
        return Database.connect(
            url = DB_URL,
            driver = DB_DRIVER,
            user = DB_USER,
            password = DB_PASSWORD
        )
    }
    
    fun createOtterConfig(migrationPath: String = "test-migrations", version: String = ""): OtterConfig {
        return OtterConfig(
            driverClassName = DB_DRIVER,
            url = DB_URL,
            user = DB_USER,
            password = DB_PASSWORD,
            migrationPath = migrationPath,
            showSql = true,
            version = version
        )
    }
}