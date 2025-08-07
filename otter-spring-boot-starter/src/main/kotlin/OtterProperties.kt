package io.github.goodgoodjm.otter

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "otter")
data class OtterProperties(
    val enabled: Boolean = true,
    val migrationPath: String = "",
    val driverClassName: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val showSql: Boolean = false,
    val version: String = "",
    val testMode: Boolean = false
) {
    @Deprecated("Use testMode instead", ReplaceWith("testMode"))
    val disableLock: Boolean get() = testMode
}