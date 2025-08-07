package io.github.goodgoodjm.otter.core

/**
 * Otter 프레임워크 전역 상수
 */
object Constants {
    
    object Lock {
        const val DEFAULT_TIMEOUT_SECONDS = 60L
        const val RETRY_INTERVAL_MILLIS = 1000L
        const val TABLE_NAME = "otter_lock"
    }
    
    object Migration {
        const val TABLE_NAME = "otter_migration"
        const val DEFAULT_VERSION = ""
        const val DEFAULT_PATH = "migrations"
    }
    
    object Database {
        const val DEFAULT_SHOW_SQL = false
        const val DEFAULT_TEST_MODE = false
    }
    
    object ColumnTypes {
        const val INT = "INT"
        const val INTEGER = "INTEGER"
        const val BIGINT = "BIGINT"
        const val SMALLINT = "SMALLINT"
        
        const val SERIAL = "SERIAL"
        const val BIGSERIAL = "BIGSERIAL"
        const val SMALLSERIAL = "SMALLSERIAL"
    }
    
    object TestEnvironment {
        val TEST_PROFILE_INDICATORS = listOf("test", "testing", "ci")
        val TEST_ENV_VARS = listOf("TEST_ENV", "CI", "TESTING")
        val TEST_PROPERTIES = listOf("test.environment", "spring.profiles.active")
    }
}