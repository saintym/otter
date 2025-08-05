package io.github.goodgoodjm.otter.core.dsl

class AlterColumnSchema : SchemaContext {
    enum class Type {
        NONE, ADD, DROP, MODIFY
    }

    var alterType: Type = Type.NONE
    var table: String = ""
    var name: String = ""
    var type: String = ""

    val constraints: List<Constraint> get() = _constraints
    private val _constraints = mutableListOf<Constraint>()

    fun setConstraint(vararg constraints: Constraint) {
        val items = constraints.filter { it !is Constraint.NONE }.toList()
        _constraints.clear()
        _constraints.addAll(items)
    }

    override fun resolve(): List<String> {
        return emptyList()
    }

    override fun toString(): String {
        return "AlterColumnSchema(alterType=$alterType, table='$table', name='$name', type='$type', constraints=$_constraints)"
    }
}