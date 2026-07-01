package io.github.goodgoodjm.otter.core.dsl

import io.github.goodgoodjm.otter.core.adapter.model.ColumnDefinition

/**
 * 테이블 정의
 *
 * DB 중립적인 테이블 구조를 표현합니다.
 */
data class TableDefinition(
    val name: String,
    val columns: List<ColumnDefinition>,
    val primaryKey: PrimaryKeyDefinition? = null,
    val uniqueConstraints: List<UniqueConstraint> = emptyList(),
    val indexes: List<IndexDefinition> = emptyList(),
    val foreignKeys: List<ForeignKeyDefinition> = emptyList(),
    val checkConstraints: List<CheckConstraint> = emptyList()
)

/**
 * Primary Key 정의
 */
data class PrimaryKeyDefinition(
    val columns: List<String>,
    val name: String? = null
)

/**
 * Unique 제약조건
 */
data class UniqueConstraint(
    val columns: List<String>,
    val name: String? = null
)

/**
 * Index 정의
 */
data class IndexDefinition(
    val name: String,
    val columns: List<String>,
    val unique: Boolean = false
)

/**
 * Foreign Key 정의
 */
data class ForeignKeyDefinition(
    val column: String,
    val referenceTable: String,
    val referenceColumn: String,
    val onDelete: ForeignKeyAction,
    val onUpdate: ForeignKeyAction,
    val name: String? = null
)

/**
 * Foreign Key 액션
 */
enum class ForeignKeyAction {
    CASCADE,
    SET_NULL,
    RESTRICT,
    NO_ACTION,
    SET_DEFAULT
}

/**
 * Check 제약조건
 */
data class CheckConstraint(
    val name: String,
    val expression: String
)

/**
 * 테이블 빌더 인터페이스
 */
interface TableBuilder {

    /**
     * 컬럼 추가
     */
    fun column(name: String, type: io.github.goodgoodjm.otter.core.adapter.model.ColumnType, modifiers: Set<io.github.goodgoodjm.otter.core.adapter.model.ColumnModifier> = emptySet())

    /**
     * Primary Key 설정 (단일 컬럼)
     */
    fun primaryKey(column: String)

    /**
     * Primary Key 설정 (복합 키)
     */
    fun primaryKey(vararg columns: String)

    /**
     * Unique 제약조건
     */
    fun unique(vararg columns: String)

    /**
     * Index 생성
     */
    fun index(name: String, vararg columns: String)

    /**
     * Foreign Key 제약조건
     */
    fun foreignKey(
        column: String,
        referenceTable: String,
        referenceColumn: String,
        onDelete: ForeignKeyAction = ForeignKeyAction.RESTRICT,
        onUpdate: ForeignKeyAction = ForeignKeyAction.RESTRICT
    )

    /**
     * Check 제약조건
     */
    fun check(name: String, expression: String)

    /**
     * 테이블 정의 빌드
     */
    fun build(): TableDefinition
}

/**
 * 테이블 빌더 구현
 */
class TableBuilderImpl(private val tableName: String) : TableBuilder {

    private val columns = mutableListOf<ColumnDefinition>()
    private var primaryKey: PrimaryKeyDefinition? = null
    private val uniqueConstraints = mutableListOf<UniqueConstraint>()
    private val indexes = mutableListOf<IndexDefinition>()
    private val foreignKeys = mutableListOf<ForeignKeyDefinition>()
    private val checkConstraints = mutableListOf<CheckConstraint>()

    override fun column(name: String, type: io.github.goodgoodjm.otter.core.adapter.model.ColumnType, modifiers: Set<io.github.goodgoodjm.otter.core.adapter.model.ColumnModifier>) {
        columns.add(ColumnDefinition(name, type, modifiers))

        // PRIMARY_KEY modifier가 있으면 자동으로 primary key 설정
        if (io.github.goodgoodjm.otter.core.adapter.model.ColumnModifier.PRIMARY_KEY in modifiers) {
            primaryKey(name)
        }
    }

    override fun primaryKey(column: String) {
        primaryKey = PrimaryKeyDefinition(listOf(column))
    }

    override fun primaryKey(vararg columns: String) {
        primaryKey = PrimaryKeyDefinition(columns.toList())
    }

    override fun unique(vararg columns: String) {
        uniqueConstraints.add(UniqueConstraint(columns.toList()))
    }

    override fun index(name: String, vararg columns: String) {
        indexes.add(IndexDefinition(name, columns.toList()))
    }

    override fun foreignKey(
        column: String,
        referenceTable: String,
        referenceColumn: String,
        onDelete: ForeignKeyAction,
        onUpdate: ForeignKeyAction
    ) {
        foreignKeys.add(
            ForeignKeyDefinition(
                column, referenceTable, referenceColumn, onDelete, onUpdate
            )
        )
    }

    override fun check(name: String, expression: String) {
        checkConstraints.add(CheckConstraint(name, expression))
    }

    override fun build(): TableDefinition {
        return TableDefinition(
            name = tableName,
            columns = columns.toList(),
            primaryKey = primaryKey,
            uniqueConstraints = uniqueConstraints.toList(),
            indexes = indexes.toList(),
            foreignKeys = foreignKeys.toList(),
            checkConstraints = checkConstraints.toList()
        )
    }
}