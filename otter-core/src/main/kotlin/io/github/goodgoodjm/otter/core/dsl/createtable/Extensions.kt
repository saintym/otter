package io.github.goodgoodjm.otter.core.dsl.createtable

import io.github.goodgoodjm.otter.core.dsl.Constraint
import io.github.goodgoodjm.otter.core.dsl.ConstraintGroup
import io.github.goodgoodjm.otter.core.dsl.type.Column
import io.github.goodgoodjm.otter.core.dsl.type.ForeignKeyReference

/**
 * DSL Extension Functions for backward compatibility
 *
 * 기존 DSL 형식을 유지하면서 새로운 불변 타입 시스템을 사용
 */

/**
 * constraints 연산자 - 제약조건 추가
 * 예: "email" - VARCHAR(255) constraints NOT_NULL
 */
infix fun Column.constraints(constraint: Constraint): Column {
    return when (constraint) {
        is ConstraintGroup -> this.withConstraints(*constraint.constraints.toTypedArray())
        else -> this.withConstraint(constraint)
    }
}

/**
 * and 연산자 - 제약조건 체이닝
 * 예: "id" - INT constraints PRIMARY and AUTO_INCREMENT
 */
infix fun Column.and(constraint: Constraint): Column {
    return this.withConstraint(constraint)
}

/**
 * foreignKey 연산자 - 외래키 설정
 * 예: "user_id" - INT foreignKey "users(id)"
 */
infix fun Column.foreignKey(reference: String): Column {
    val regex = Regex("^([a-zA-Z0-9_]+)\\(([a-zA-Z0-9_]+)\\)$")
    val result = regex.find(reference) ?: throw IllegalArgumentException("Invalid foreign key format: $reference")
    val table = result.groupValues[1]
    val column = result.groupValues[2]
    return this.references(table, column)
}