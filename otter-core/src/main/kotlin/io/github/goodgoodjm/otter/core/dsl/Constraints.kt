package io.github.goodgoodjm.otter.core.dsl

sealed class Constraint {
    object NONE : Constraint()
    object AUTO_INCREMENT : Constraint()
    object NOT_NULL : Constraint()
    object NULLABLE : Constraint()
    object UNIQUE : Constraint()
    object PRIMARY : Constraint()
    
    data class DEFAULT(val value: Any) : Constraint()
    data class CHECK(val condition: String) : Constraint()
    data class REFERENCES(
        val table: String, 
        val column: String = "id",
        val onDelete: ReferenceAction? = null,
        val onUpdate: ReferenceAction? = null
    ) : Constraint()
    data class GENERATED(val type: GenerationType) : Constraint()
    data class COLLATE(val collation: String) : Constraint()
    data class COMMENT(val text: String) : Constraint()
}

enum class ReferenceAction {
    CASCADE,
    SET_NULL,
    SET_DEFAULT,
    RESTRICT,
    NO_ACTION
}

enum class GenerationType {
    ALWAYS,
    BY_DEFAULT
}

@SchemaMaker
fun DEFAULT(value: Any) = Constraint.DEFAULT(value)

@SchemaMaker
fun CHECK(condition: String) = Constraint.CHECK(condition)

@SchemaMaker
fun REFERENCES(
    table: String, 
    column: String = "id", 
    onDelete: ReferenceAction? = null, 
    onUpdate: ReferenceAction? = null
) = Constraint.REFERENCES(table, column, onDelete, onUpdate)

@SchemaMaker
fun GENERATED(type: GenerationType) = Constraint.GENERATED(type)

@SchemaMaker
fun COLLATE(collation: String) = Constraint.COLLATE(collation)

@SchemaMaker
fun COMMENT(text: String) = Constraint.COMMENT(text)

val CASCADE = ReferenceAction.CASCADE
val SET_NULL = ReferenceAction.SET_NULL
val SET_DEFAULT = ReferenceAction.SET_DEFAULT
val RESTRICT = ReferenceAction.RESTRICT
val NO_ACTION = ReferenceAction.NO_ACTION

val ALWAYS = GenerationType.ALWAYS
val BY_DEFAULT = GenerationType.BY_DEFAULT