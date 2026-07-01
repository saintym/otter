package io.github.goodgoodjm.otter

import io.github.goodgoodjm.otter.core.OtterConfig

object TestDatabaseConfig {
    const val DB_URL = "jdbc:postgresql://localhost:5433/otter_test"
    const val DB_USER = "postgres"
    const val DB_PASSWORD = "postgres"
    const val DB_DRIVER = "org.postgresql.Driver"

    // H2 test configuration
    const val H2_DB_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    const val H2_DB_USER = "sa"
    const val H2_DB_PASSWORD = ""
    const val H2_DB_DRIVER = "org.h2.Driver"

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

    fun createH2OtterConfig(migrationPath: String = "test-migrations", version: String = ""): OtterConfig {
        return OtterConfig(
            driverClassName = H2_DB_DRIVER,
            url = H2_DB_URL,
            user = H2_DB_USER,
            password = H2_DB_PASSWORD,
            migrationPath = migrationPath,
            showSql = true,
            version = version
        )
    }
}