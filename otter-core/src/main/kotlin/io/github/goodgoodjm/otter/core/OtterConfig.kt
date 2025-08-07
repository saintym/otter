package io.github.goodgoodjm.otter.core

import io.github.goodgoodjm.otter.core.Constants.TestEnvironment

class OtterConfig(
    val migrationPath: String,
    val driverClassName: String,
    val url: String,
    val user: String,
    val password: String,
    val showSql: Boolean,
    val version: String = "",
    val testMode: Boolean = false
) {
    init {
        // 프로덕션 환경에서 testMode 사용 시 경고
        if (testMode && !isTestEnvironment()) {
            System.err.println("WARNING: testMode is enabled in non-test environment. This disables migration locking and should only be used in tests!")
        }
    }
    
    private fun isTestEnvironment(): Boolean {
        // 시스템 프로퍼티 확인
        val systemProperties = TestEnvironment.TEST_PROPERTIES.any { prop ->
            val value = System.getProperty(prop)
            value != null && TestEnvironment.TEST_PROFILE_INDICATORS.any { indicator ->
                value.contains(indicator, ignoreCase = true)
            }
        }
        
        // 환경 변수 확인
        val envVariables = TestEnvironment.TEST_ENV_VARS.any { env ->
            val value = System.getenv(env)
            value == "true" || value == "1" || TestEnvironment.TEST_PROFILE_INDICATORS.contains(value?.lowercase())
        }
        
        return systemProperties || envVariables
    }
    
    @Deprecated("Use testMode instead", ReplaceWith("testMode"))
    val disableLock: Boolean get() = testMode
}