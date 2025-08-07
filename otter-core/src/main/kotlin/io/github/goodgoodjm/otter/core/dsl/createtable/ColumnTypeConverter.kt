package io.github.goodgoodjm.otter.core.dsl.createtable

import io.github.goodgoodjm.otter.core.Logger
import io.github.goodgoodjm.otter.core.dsl.Constraint
import io.github.goodgoodjm.otter.core.dsl.type.CustomColumnType
import io.github.goodgoodjm.otter.core.exception.ColumnTypeConversionException
import io.github.goodgoodjm.otter.core.util.DatabaseVendor
import io.github.goodgoodjm.otter.core.util.DatabaseVendorDetector
import org.jetbrains.exposed.sql.ColumnType

interface ColumnTypeConverter {
    fun convertAutoIncrement(originalType: ColumnType, constraints: List<Constraint>): ColumnType
}

class PostgreSQLColumnTypeConverter : ColumnTypeConverter, Logger {
    
    override fun convertAutoIncrement(originalType: ColumnType, constraints: List<Constraint>): ColumnType {
        val hasAutoIncrement = constraints.any { it is Constraint.AUTO_INCREMENT }
        
        if (!hasAutoIncrement) {
            return originalType
        }
        
        return try {
            when (originalType.sqlType().uppercase()) {
                "INT", "INTEGER" -> {
                    logger.debug("Converting INT AUTO_INCREMENT to SERIAL for PostgreSQL")
                    CustomColumnType("SERIAL")
                }
                "BIGINT" -> {
                    logger.debug("Converting BIGINT AUTO_INCREMENT to BIGSERIAL for PostgreSQL")
                    CustomColumnType("BIGSERIAL")
                }
                "SMALLINT" -> {
                    logger.debug("Converting SMALLINT AUTO_INCREMENT to SMALLSERIAL for PostgreSQL")
                    CustomColumnType("SMALLSERIAL")
                }
                else -> {
                    logger.warn("Unsupported AUTO_INCREMENT type for PostgreSQL: ${originalType.sqlType()}, using original type")
                    originalType
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to convert AUTO_INCREMENT type: ${originalType.sqlType()}", e)
            throw ColumnTypeConversionException(originalType.sqlType(), "SERIAL", e)
        }
    }
}

class DefaultColumnTypeConverter : ColumnTypeConverter {
    override fun convertAutoIncrement(originalType: ColumnType, constraints: List<Constraint>): ColumnType {
        return originalType // 다른 DB는 기본 AUTO_INCREMENT 사용
    }
}

object ColumnTypeConverterFactory : Logger {
    
    fun createConverter(): ColumnTypeConverter {
        return try {
            when (DatabaseVendorDetector.detectVendorSafely()) {
                DatabaseVendor.POSTGRESQL -> {
                    logger.debug("Using PostgreSQL column type converter")
                    PostgreSQLColumnTypeConverter()
                }
                DatabaseVendor.UNKNOWN -> {
                    logger.warn("Unknown database vendor, using default column type converter")
                    DefaultColumnTypeConverter()
                }
                else -> {
                    logger.debug("Using default column type converter")
                    DefaultColumnTypeConverter()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to create column type converter, falling back to default", e)
            DefaultColumnTypeConverter()
        }
    }
}