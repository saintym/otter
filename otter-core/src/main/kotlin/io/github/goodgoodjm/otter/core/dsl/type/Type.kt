package io.github.goodgoodjm.otter.core.dsl.type

import io.github.goodgoodjm.otter.core.dsl.Constraint

/**
 * Otter Type System - 불변 타입 시스템
 *
 * 기존 시스템의 문제점:
 * 1. Mutable ColumnSchema
 * 2. 싱글톤 공유로 인한 상태 오염
 * 3. getter 남발로 인한 메모리 낭비
 *
 * 새로운 접근:
 * - 모든 타입은 불변
 * - Fluent API로 체이닝
 * - Copy-on-write 패턴
 */

/**
 * 불변 컬럼 정의 (ColumnSchema 별칭 추가)
 */
typealias ColumnSchema = Column

data class Column(
    val type: ColumnType,
    val constraints: Set<Constraint> = emptySet(),
    val defaultValue: DefaultValue? = null,
    val references: ForeignKeyReference? = null
) {
    // Fluent API methods
    fun notNull() = withConstraint(Constraint.NOT_NULL)
    fun unique() = withConstraint(Constraint.UNIQUE)
    fun primaryKey() = withConstraint(Constraint.PRIMARY)
    fun autoIncrement() = withConstraint(Constraint.AUTO_INCREMENT)

    fun default(value: String) = copy(defaultValue = DefaultValue.Literal(value))
    fun defaultCurrentTimestamp() = copy(defaultValue = DefaultValue.CurrentTimestamp)
    fun defaultNull() = copy(defaultValue = DefaultValue.Null)

    fun references(table: String, column: String = "id") =
        copy(references = ForeignKeyReference(table, column))

    fun withConstraint(constraint: Constraint) =
        copy(constraints = constraints + constraint)

    fun withConstraints(vararg newConstraints: Constraint) =
        copy(constraints = constraints + newConstraints.toSet())

    // DSL 연산자
    operator fun plus(constraint: Constraint) = withConstraint(constraint)
}

/**
 * 컬럼 타입 (sealed class로 타입 안전성 보장)
 */
sealed class ColumnType {
    // 숫자 타입
    object TinyInt : ColumnType()
    object SmallInt : ColumnType()
    object Integer : ColumnType()
    object BigInt : ColumnType()
    data class Decimal(val precision: Int, val scale: Int) : ColumnType()
    object Float : ColumnType()
    object Double : ColumnType()

    // 문자열 타입
    data class Char(val length: Int) : ColumnType()
    data class Varchar(val length: Int) : ColumnType()
    object Text : ColumnType()

    // 날짜/시간 타입
    object Date : ColumnType()
    object Time : ColumnType()
    object DateTime : ColumnType()
    object Timestamp : ColumnType()

    // 기타 타입
    object Boolean : ColumnType()
    object Uuid : ColumnType()
    object Json : ColumnType()
    object Blob : ColumnType()
    data class Custom(val sqlType: String) : ColumnType()
}

/**
 * 기본값 타입
 */
sealed class DefaultValue {
    object Null : DefaultValue()
    data class Literal(val value: String) : DefaultValue()
    object CurrentTimestamp : DefaultValue()
    object CurrentDate : DefaultValue()
    object CurrentTime : DefaultValue()
    data class Expression(val sql: String) : DefaultValue()
}

/**
 * 외래키 참조
 */
data class ForeignKeyReference(
    val table: String,
    val column: String,
    val onDelete: ReferentialAction = ReferentialAction.RESTRICT,
    val onUpdate: ReferentialAction = ReferentialAction.RESTRICT
)

enum class ReferentialAction {
    CASCADE, RESTRICT, SET_NULL, SET_DEFAULT, NO_ACTION
}

/**
 * Type Factory - 타입 생성 함수들
 *
 * 사용 예:
 * val column = integer().notNull().primaryKey().autoIncrement()
 * val email = varchar(255).notNull().unique()
 */
object Types {
    // 숫자 타입 팩토리
    fun tinyInt() = Column(ColumnType.TinyInt)
    fun smallInt() = Column(ColumnType.SmallInt)
    fun integer() = Column(ColumnType.Integer)
    fun bigInt() = Column(ColumnType.BigInt)
    fun decimal(precision: Int, scale: Int) = Column(ColumnType.Decimal(precision, scale))
    fun float() = Column(ColumnType.Float)
    fun double() = Column(ColumnType.Double)

    // 문자열 타입 팩토리
    fun char(length: Int = 1) = Column(ColumnType.Char(length))
    fun varchar(length: Int = 255) = Column(ColumnType.Varchar(length))
    fun text() = Column(ColumnType.Text)

    // 날짜/시간 타입 팩토리
    fun date() = Column(ColumnType.Date)
    fun time() = Column(ColumnType.Time)
    fun datetime() = Column(ColumnType.DateTime)
    fun timestamp() = Column(ColumnType.Timestamp)

    // 기타 타입 팩토리
    fun boolean() = Column(ColumnType.Boolean)
    fun uuid() = Column(ColumnType.Uuid)
    fun json() = Column(ColumnType.Json)
    fun blob() = Column(ColumnType.Blob)
    fun custom(sqlType: String) = Column(ColumnType.Custom(sqlType))

    // 특수 타입 (PostgreSQL SERIAL 등)
    fun serial() = integer().primaryKey().autoIncrement()
    fun bigSerial() = bigInt().primaryKey().autoIncrement()
}

/**
 * 전역 상수 - 기존 코드 호환성을 위해
 */
object Type {
    // 숫자 타입
    val TINYINT get() = Types.tinyInt()
    val SMALLINT get() = Types.smallInt()
    val INT get() = Types.integer()
    val INTEGER get() = Types.integer()
    val BIGINT get() = Types.bigInt()
    val FLOAT get() = Types.float()
    val DOUBLE get() = Types.double()

    // 문자열 타입
    val TEXT get() = Types.text()
    fun VARCHAR(length: Int = 255) = Types.varchar(length)
    fun CHAR(length: Int = 1) = Types.char(length)
    fun DECIMAL(precision: Int, scale: Int) = Types.decimal(precision, scale)

    // 날짜/시간 타입
    val DATE get() = Types.date()
    val TIME get() = Types.time()
    val DATETIME get() = Types.datetime()
    val TIMESTAMP get() = Types.timestamp()

    // 기타 타입
    val BOOLEAN get() = Types.boolean()
    val UUID get() = Types.uuid()
    val JSON get() = Types.json()
    val JSONB get() = Types.json()
    val BLOB get() = Types.blob()

    // 특수 타입
    val SERIAL get() = Types.serial()
    val BIGSERIAL get() = Types.bigSerial()
}

// 전역 변수로 직접 사용 가능
val TINYINT get() = Type.TINYINT
val SMALLINT get() = Type.SMALLINT
val INT get() = Type.INT
val INTEGER get() = Type.INTEGER
val BIGINT get() = Type.BIGINT
val FLOAT get() = Type.FLOAT
val DOUBLE get() = Type.DOUBLE
val TEXT get() = Type.TEXT
val DATE get() = Type.DATE
val TIME get() = Type.TIME
val DATETIME get() = Type.DATETIME
val TIMESTAMP get() = Type.TIMESTAMP
val BOOLEAN get() = Type.BOOLEAN
val UUID get() = Type.UUID
val JSON get() = Type.JSON
val JSONB get() = Type.JSONB
val BLOB get() = Type.BLOB
val SERIAL get() = Type.SERIAL
val BIGSERIAL get() = Type.BIGSERIAL

fun VARCHAR(length: Int = 255) = Type.VARCHAR(length)
fun CHAR(length: Int = 1) = Type.CHAR(length)
fun DECIMAL(precision: Int, scale: Int) = Type.DECIMAL(precision, scale)

/**
 * 사용 예제:
 *
 * createTable("users") {
 *     "id" - serial()
 *     "email" - varchar(255).notNull().unique()
 *     "name" - varchar(100)
 *     "bio" - text().defaultNull()
 *     "created_at" - timestamp().defaultCurrentTimestamp()
 *     "is_active" - bool().default("true")
 *     "parent_id" - bigint().references("users", "id")
 * }
 */