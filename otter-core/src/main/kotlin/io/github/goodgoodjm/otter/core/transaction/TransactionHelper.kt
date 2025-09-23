package io.github.goodgoodjm.otter.core.transaction

import io.github.goodgoodjm.otter.core.Logger
import org.jetbrains.exposed.sql.Transaction

/**
 * 트랜잭션 작업을 안전하게 실행하기 위한 헬퍼 클래스
 * 
 * 자동 롤백과 일관된 트랜잭션 관리를 제공합니다.
 */
object TransactionHelper : Logger {
    
    /**
     * 트랜잭션 내에서 작업을 실행합니다.
     * 실패 시 자동으로 롤백됩니다.
     * 
     * @param transaction 현재 트랜잭션
     * @param description 작업 설명 (로깅용)
     * @param block 실행할 작업
     * @return 작업 성공 여부
     */
    fun <T> executeInTransaction(
        transaction: Transaction,
        description: String,
        block: Transaction.() -> T
    ): Result<T> {
        return try {
            logger.debug("트랜잭션 작업 시작: $description")
            
            val result = transaction.block()
            
            // 명시적 커밋은 하지 않음 - 상위 트랜잭션 스코프에서 관리
            logger.debug("트랜잭션 작업 완료: $description")
            
            Result.success(result)
        } catch (e: Exception) {
            logger.error("트랜잭션 작업 실패 ($description): ${e.message}", e)
            
            // 트랜잭션 롤백
            try {
                transaction.rollback()
                logger.debug("트랜잭션 롤백 완료")
            } catch (rollbackError: Exception) {
                logger.error("트랜잭션 롤백 실패: ${rollbackError.message}", rollbackError)
            }
            
            Result.failure(e)
        }
    }
    
    /**
     * 여러 SQL 문을 트랜잭션 내에서 실행합니다.
     * 하나라도 실패하면 전체가 롤백됩니다.
     */
    fun executeSqlStatements(
        transaction: Transaction,
        statements: List<String>,
        showSql: Boolean = false
    ): Result<Unit> {
        return executeInTransaction(transaction, "SQL 문 실행 (${statements.size}개)") {
            statements.forEach { sql ->
                if (showSql) {
                    logger.info(sql)
                }
                exec(sql)
            }
        }
    }
    
    /**
     * 트랜잭션 세이브포인트를 생성하고 작업을 실행합니다.
     * 실패 시 세이브포인트로 롤백됩니다.
     * 
     * 주의: H2 데이터베이스는 세이브포인트를 제한적으로 지원하므로,
     * 테스트 환경에서는 일반 트랜잭션 실행으로 대체됩니다.
     */
    fun <T> executeWithSavepoint(
        transaction: Transaction,
        savepointName: String,
        block: Transaction.() -> T
    ): Result<T> {
        // H2나 테스트 환경에서는 세이브포인트 대신 일반 실행
        val vendorName = transaction.db.vendor.lowercase()
        if (vendorName.contains("h2") || vendorName.contains("test")) {
            return executeInTransaction(transaction, savepointName, block)
        }
        
        // 세이브포인트 이름 정규화 (특수문자 제거)
        val sanitizedName = savepointName.replace(Regex("[^a-zA-Z0-9_]"), "_")
        
        return try {
            // 세이브포인트 생성
            transaction.exec("SAVEPOINT $sanitizedName")
            logger.debug("세이브포인트 생성: $sanitizedName")
            
            val result = transaction.block()
            
            logger.debug("세이브포인트 작업 완료: $sanitizedName")
            Result.success(result)
        } catch (e: Exception) {
            logger.error("세이브포인트 작업 실패 ($sanitizedName): ${e.message}", e)
            
            // 세이브포인트로 롤백
            try {
                transaction.exec("ROLLBACK TO SAVEPOINT $sanitizedName")
                logger.debug("세이브포인트 롤백 완료: $sanitizedName")
            } catch (rollbackError: Exception) {
                logger.error("세이브포인트 롤백 실패: ${rollbackError.message}", rollbackError)
            }
            
            Result.failure(e)
        }
    }
}