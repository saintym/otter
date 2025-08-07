package io.github.goodgoodjm.otter.core.util

import io.github.goodgoodjm.otter.core.Logger
import io.github.goodgoodjm.otter.core.exception.DatabaseVendorDetectionException
import org.jetbrains.exposed.sql.transactions.TransactionManager

enum class DatabaseVendor(val identifier: String, val urlPrefix: String) {
    POSTGRESQL("postgresql", "jdbc:postgresql"),
    MYSQL("mysql", "jdbc:mysql"),
    H2("h2", "jdbc:h2"),
    MARIADB("mariadb", "jdbc:mariadb"),
    SQLITE("sqlite", "jdbc:sqlite"),
    UNKNOWN("unknown", "");
    
    companion object {
        fun fromUrl(url: String): DatabaseVendor {
            return values().find { url.startsWith(it.urlPrefix) } ?: UNKNOWN
        }
        
        fun fromIdentifier(identifier: String): DatabaseVendor {
            return values().find { it.identifier.equals(identifier, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

object DatabaseVendorDetector : Logger {
    
    fun detectVendor(): DatabaseVendor {
        return try {
            // 먼저 트랜잭션 매니저를 통한 감지 시도
            val transactionVendor = TransactionManager.current().db.vendor
            val vendor = DatabaseVendor.fromIdentifier(transactionVendor)
            
            if (vendor != DatabaseVendor.UNKNOWN) {
                logger.debug("Database vendor detected from transaction: $transactionVendor")
                return vendor
            }
            
            // 트랜잭션 매니저로 감지되지 않으면 URL로 감지
            detectFromUrl()
        } catch (e: Exception) {
            logger.debug("Failed to detect vendor from transaction manager: ${e.message}")
            detectFromUrl()
        }
    }
    
    private fun detectFromUrl(): DatabaseVendor {
        return try {
            val db = TransactionManager.current().db
            val url = db.url
                
            val vendor = DatabaseVendor.fromUrl(url)
            logger.debug("Database vendor detected from URL: $url -> $vendor")
            
            if (vendor == DatabaseVendor.UNKNOWN) {
                logger.warn("Unknown database vendor detected from URL: $url")
            }
            
            vendor
        } catch (e: DatabaseVendorDetectionException) {
            throw e
        } catch (e: Exception) {
            throw DatabaseVendorDetectionException("URL detection failed: ${e.message}", e)
        }
    }
    
    /**
     * 안전한 벤더 감지 - 예외 발생 시 UNKNOWN 반환
     */
    fun detectVendorSafely(): DatabaseVendor {
        return try {
            detectVendor()
        } catch (e: DatabaseVendorDetectionException) {
            logger.warn("Database vendor detection failed, using UNKNOWN: ${e.message}")
            DatabaseVendor.UNKNOWN
        }
    }
    
    fun isPostgreSQL(): Boolean = detectVendor() == DatabaseVendor.POSTGRESQL
    fun isMySQL(): Boolean = detectVendor() == DatabaseVendor.MYSQL
    fun isH2(): Boolean = detectVendor() == DatabaseVendor.H2
}