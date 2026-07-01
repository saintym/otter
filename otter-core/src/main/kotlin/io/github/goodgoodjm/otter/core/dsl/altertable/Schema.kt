package io.github.goodgoodjm.otter.core.dsl.altertable

import io.github.goodgoodjm.otter.core.dsl.Constraint
import io.github.goodgoodjm.otter.core.dsl.ConstraintGroup
import io.github.goodgoodjm.otter.core.dsl.type.Column

data class AlterTableSchema(
    val name: String,
) {
    val operations = mutableListOf<AlterOperation>()

    fun add(columnName: String) = AddColumnOperation(columnName).also {
        operations.add(it)
    }

    fun modify(columnName: String) = ModifyColumnOperation(columnName).also {
        operations.add(it)
    }

    fun drop(columnName: String) {
        operations.add(DropColumnOperation(columnName))
    }
}

sealed class AlterOperation(val columnName: String)

class AddColumnOperation(columnName: String) : AlterOperation(columnName) {
    var column: Column? = null

    operator fun minus(column: Column): AddColumnOperation {
        this.column = column
        return this
    }

    infix fun constraints(constraint: Constraint): AddColumnOperation {
        column?.let {
            column = when (constraint) {
                is ConstraintGroup -> it.withConstraints(*constraint.constraints.toTypedArray())
                else -> it.withConstraint(constraint)
            }
        }
        return this
    }

    infix fun and(constraint: Constraint): AddColumnOperation {
        column?.let {
            column = it.withConstraint(constraint)
        }
        return this
    }
}

class ModifyColumnOperation(columnName: String) : AlterOperation(columnName) {
    var column: Column? = null

    operator fun minus(column: Column): ModifyColumnOperation {
        this.column = column
        return this
    }

    infix fun constraints(constraint: Constraint): ModifyColumnOperation {
        column?.let {
            column = when (constraint) {
                is ConstraintGroup -> it.withConstraints(*constraint.constraints.toTypedArray())
                else -> it.withConstraint(constraint)
            }
        }
        return this
    }

    infix fun and(constraint: Constraint): ModifyColumnOperation {
        column?.let {
            column = it.withConstraint(constraint)
        }
        return this
    }
}

class DropColumnOperation(columnName: String) : AlterOperation(columnName)
