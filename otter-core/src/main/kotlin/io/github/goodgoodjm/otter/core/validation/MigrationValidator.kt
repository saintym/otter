package io.github.goodgoodjm.otter.core.validation

import io.github.goodgoodjm.otter.core.Logger
import io.github.goodgoodjm.otter.core.Migration

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}

class MigrationValidator : Logger {
    
    fun validateDownMigration(migration: Migration): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // down() 메서드가 구현되어 있는지 확인
            migration.down()
            val downContexts = migration.contexts
            
            if (downContexts.isEmpty()) {
                warnings.add("Down migration is empty. This migration cannot be rolled back.")
            }
            
            // 컨텍스트를 정리하여 메모리 누수 방지
            migration.clearContexts()
            
            logger.debug("Down migration validation completed for ${migration::class.simpleName}")
            
        } catch (e: NotImplementedError) {
            errors.add("Down migration is not implemented")
        } catch (e: Exception) {
            errors.add("Down migration validation failed: ${e.message}")
            logger.error("Down migration validation error", e)
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    fun validateRollbackSafety(migrationsToRollback: List<Migration>, force: Boolean = false): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        if (!force) {
            // 의존성 체크 로직 (간단한 버전)
            migrationsToRollback.forEachIndexed { index, migration ->
                val validationResult = validateDownMigration(migration)
                if (validationResult.hasErrors) {
                    errors.addAll(validationResult.errors.map { "Migration ${index + 1}: $it" })
                }
                if (validationResult.hasWarnings) {
                    warnings.addAll(validationResult.warnings.map { "Migration ${index + 1}: $it" })
                }
            }
            
            if (migrationsToRollback.size > 1) {
                warnings.add("Rolling back ${migrationsToRollback.size} migrations. This operation cannot be undone without re-applying the migrations.")
            }
        } else {
            warnings.add("Force rollback is enabled. Dependency checks are bypassed.")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}