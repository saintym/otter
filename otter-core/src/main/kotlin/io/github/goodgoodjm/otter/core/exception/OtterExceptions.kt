package io.github.goodgoodjm.otter.core.exception

/**
 * Otter 관련 예외의 기본 클래스
 */
abstract class OtterException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * 데이터베이스 벤더 감지 실패 시 발생하는 예외
 */
class DatabaseVendorDetectionException(message: String, cause: Throwable? = null) 
    : OtterException("Failed to detect database vendor: $message", cause)

/**
 * 마이그레이션 실행 실패 시 발생하는 예외
 */
class MigrationExecutionException(message: String, cause: Throwable? = null)
    : OtterException("Migration execution failed: $message", cause)

/**
 * Down 마이그레이션 관련 예외
 */
class DownMigrationException(message: String, cause: Throwable? = null)
    : OtterException("Down migration failed: $message", cause)

/**
 * 컬럼 타입 변환 실패 시 발생하는 예외
 */
class ColumnTypeConversionException(originalType: String, targetType: String, cause: Throwable? = null)
    : OtterException("Failed to convert column type from $originalType to $targetType", cause)

/**
 * 락 획득 실패 시 발생하는 예외
 */
class LockException(message: String, cause: Throwable? = null)
    : OtterException("Lock operation failed: $message", cause)

/**
 * 마이그레이션 검증 실패 시 발생하는 예외
 */
class MigrationValidationException(message: String, errors: List<String> = emptyList())
    : OtterException(buildValidationMessage(message, errors)) {
    
    companion object {
        private fun buildValidationMessage(message: String, errors: List<String>): String {
            return if (errors.isEmpty()) {
                message
            } else {
                "$message\nValidation errors:\n${errors.joinToString("\n") { "- $it" }}"
            }
        }
    }
}