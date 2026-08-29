package io.github.goodgoodjm.otter.core.adapter.strategy

import io.github.goodgoodjm.otter.core.adapter.model.ColumnDefinition
import io.github.goodgoodjm.otter.core.adapter.model.ColumnModifier
import io.github.goodgoodjm.otter.core.adapter.model.ColumnType
import java.util.UUID

/**
 * Primary Key 전략 인터페이스
 *
 * 다양한 Primary Key 타입과 생성 전략을 지원합니다.
 * 각 데이터베이스 어댑터는 자신만의 전략 구현체를 제공해야 합니다.
 */
interface PrimaryKeyStrategy {

    /**
     * Primary Key DDL 생성
     *
     * @param column 컬럼 정의
     * @return 생성된 DDL 문자열 (예: "id SERIAL PRIMARY KEY")
     */
    fun generateDDL(column: ColumnDefinition): String

    /**
     * 지원하는 타입 확인
     *
     * @param type 컬럼 타입
     * @return 해당 타입의 Primary Key 지원 여부
     */
    fun supportsType(type: ColumnType): Boolean

    /**
     * 복합 Primary Key DDL 생성
     *
     * @param columns 컬럼 이름 목록
     * @return PRIMARY KEY 제약조건 DDL (예: "PRIMARY KEY (user_id, role_id)")
     */
    fun generateCompositeDDL(columns: List<String>): String

    /**
     * Primary Key 생성 방식
     */
    fun getGenerationStrategy(): PrimaryKeyGeneration
}

/**
 * Primary Key 생성 방식
 */
enum class PrimaryKeyGeneration {
    /** 자동 증가 (AUTO_INCREMENT, SERIAL) */
    AUTO_INCREMENT,

    /** UUID 자동 생성 */
    UUID,

    /** 시퀀스 기반 */
    SEQUENCE,

    /** IDENTITY 컬럼 (SQL Server) */
    IDENTITY,

    /** 사용자 제공 (Natural Key) */
    MANUAL,

    /** 타임스탬프 기반 (Twitter Snowflake 등) */
    TIMESTAMP_BASED
}

/**
 * PostgreSQL Primary Key 전략
 */
class PostgreSQLPrimaryKeyStrategy : PrimaryKeyStrategy {

    override fun generateDDL(column: ColumnDefinition): String {
        return when (column.type) {
            is ColumnType.Integer -> {
                if (column.modifiers.any { it == ColumnModifier.AUTO_INCREMENT }) {
                    "${column.name} SERIAL PRIMARY KEY"
                } else {
                    "${column.name} INT PRIMARY KEY"
                }
            }
            is ColumnType.BigInt -> {
                if (column.modifiers.any { it == ColumnModifier.AUTO_INCREMENT }) {
                    "${column.name} BIGSERIAL PRIMARY KEY"
                } else {
                    "${column.name} BIGINT PRIMARY KEY"
                }
            }
            is ColumnType.Uuid -> {
                val defaultClause = if (column.modifiers.any { it == ColumnModifier.GENERATED_BY_DEFAULT || it == ColumnModifier.GENERATED_ALWAYS }) {
                    " DEFAULT gen_random_uuid()"
                } else {
                    ""
                }
                "${column.name} UUID PRIMARY KEY$defaultClause"
            }
            is ColumnType.Varchar -> {
                "${column.name} VARCHAR(${column.type.length}) PRIMARY KEY"
            }
            else -> {
                throw UnsupportedOperationException(
                    "PostgreSQL does not support ${column.type} as primary key"
                )
            }
        }
    }

    override fun supportsType(type: ColumnType): Boolean {
        return when (type) {
            is ColumnType.Integer,
            is ColumnType.BigInt,
            is ColumnType.Uuid,
            is ColumnType.Varchar,
            is ColumnType.Char -> true
            else -> false
        }
    }

    override fun generateCompositeDDL(columns: List<String>): String {
        return "PRIMARY KEY (${columns.joinToString(", ")})"
    }

    override fun getGenerationStrategy(): PrimaryKeyGeneration {
        return PrimaryKeyGeneration.AUTO_INCREMENT
    }
}

/**
 * MySQL Primary Key 전략
 */
class MySQLPrimaryKeyStrategy : PrimaryKeyStrategy {

    override fun generateDDL(column: ColumnDefinition): String {
        return when (column.type) {
            is ColumnType.Integer -> {
                if (column.modifiers.any { it == ColumnModifier.AUTO_INCREMENT }) {
                    "${column.name} INT AUTO_INCREMENT PRIMARY KEY"
                } else {
                    "${column.name} INT PRIMARY KEY"
                }
            }
            is ColumnType.BigInt -> {
                if (column.modifiers.any { it == ColumnModifier.AUTO_INCREMENT }) {
                    "${column.name} BIGINT AUTO_INCREMENT PRIMARY KEY"
                } else {
                    "${column.name} BIGINT PRIMARY KEY"
                }
            }
            is ColumnType.Uuid -> {
                // MySQL은 네이티브 UUID 타입이 없어 CHAR(36) 또는 BINARY(16) 사용
                val defaultClause = if (column.modifiers.any { it == ColumnModifier.GENERATED_BY_DEFAULT || it == ColumnModifier.GENERATED_ALWAYS }) {
                    " DEFAULT (UUID())"
                } else {
                    ""
                }
                "${column.name} CHAR(36) PRIMARY KEY$defaultClause"
            }
            is ColumnType.Varchar -> {
                "${column.name} VARCHAR(${column.type.length}) PRIMARY KEY"
            }
            else -> {
                throw UnsupportedOperationException(
                    "MySQL does not support ${column.type} as primary key"
                )
            }
        }
    }

    override fun supportsType(type: ColumnType): Boolean {
        return when (type) {
            is ColumnType.Integer,
            is ColumnType.BigInt,
            is ColumnType.Uuid,
            is ColumnType.Varchar,
            is ColumnType.Char -> true
            else -> false
        }
    }

    override fun generateCompositeDDL(columns: List<String>): String {
        return "PRIMARY KEY (${columns.joinToString(", ")})"
    }

    override fun getGenerationStrategy(): PrimaryKeyGeneration {
        return PrimaryKeyGeneration.AUTO_INCREMENT
    }
}

/**
 * SQLite Primary Key 전략
 */
class SQLitePrimaryKeyStrategy : PrimaryKeyStrategy {

    override fun generateDDL(column: ColumnDefinition): String {
        return when (column.type) {
            is ColumnType.Integer -> {
                if (column.modifiers.any { it == ColumnModifier.AUTO_INCREMENT }) {
                    "${column.name} INTEGER PRIMARY KEY AUTOINCREMENT"
                } else {
                    "${column.name} INTEGER PRIMARY KEY"
                }
            }
            is ColumnType.Uuid -> {
                // SQLite는 TEXT로 UUID 저장
                "${column.name} TEXT PRIMARY KEY"
            }
            is ColumnType.Varchar,
            is ColumnType.Text -> {
                "${column.name} TEXT PRIMARY KEY"
            }
            else -> {
                throw UnsupportedOperationException(
                    "SQLite does not support ${column.type} as primary key"
                )
            }
        }
    }

    override fun supportsType(type: ColumnType): Boolean {
        return when (type) {
            is ColumnType.Integer,
            is ColumnType.Uuid,
            is ColumnType.Varchar,
            is ColumnType.Text -> true
            else -> false
        }
    }

    override fun generateCompositeDDL(columns: List<String>): String {
        return "PRIMARY KEY (${columns.joinToString(", ")})"
    }

    override fun getGenerationStrategy(): PrimaryKeyGeneration {
        return PrimaryKeyGeneration.AUTO_INCREMENT
    }
}