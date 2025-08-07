package io.github.goodgoodjm.otter.core.transaction

import io.github.goodgoodjm.otter.core.Logger
import io.github.goodgoodjm.otter.core.exception.MigrationExecutionException
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager

/**
 * 트랜잭션 관리를 위한 유틸리티 클래스
 */
object OtterTransactionManager : Logger {
    
    /**
     * 각 마이그레이션을 개별 트랜잭션으로 실행
     */
    fun executeInTransaction(operation: String, block: Transaction.() -> Unit) {
        logger.debug("Starting transaction for: $operation")
        
        try {
            TransactionManager.current().commit()
            TransactionManager.current().apply {
                block()
                commit()
            }
            logger.debug("Transaction completed successfully for: $operation")
        } catch (e: Exception) {
            logger.error("Transaction failed for: $operation", e)
            try {
                TransactionManager.current().rollback()
                logger.debug("Transaction rolled back for: $operation")
            } catch (rollbackException: Exception) {
                logger.error("Failed to rollback transaction for: $operation", rollbackException)
            }
            throw MigrationExecutionException("Transaction failed for $operation", e)
        }
    }
    
    /**
     * 여러 작업을 하나의 트랜잭션으로 실행
     */
    fun executeInSingleTransaction(operations: List<Pair<String, Transaction.() -> Unit>>) {
        logger.debug("Starting single transaction for ${operations.size} operations")
        
        try {
            TransactionManager.current().apply {
                operations.forEach { (name, operation) ->
                    logger.debug("Executing operation: $name")
                    operation()
                }
                commit()
            }
            logger.debug("Single transaction completed successfully")
        } catch (e: Exception) {
            logger.error("Single transaction failed", e)
            try {
                TransactionManager.current().rollback()
                logger.debug("Single transaction rolled back")
            } catch (rollbackException: Exception) {
                logger.error("Failed to rollback single transaction", rollbackException)
            }
            throw MigrationExecutionException("Single transaction failed", e)
        }
    }
}