import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.createtable.foreignKey
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "Create customers and products"

    override fun up() {
        createTable("customers") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "name" - VARCHAR(255) constraints Constraint.UNIQUE
            "email" - VARCHAR(255)
        }

        createTable("products") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "name" - VARCHAR(255) constraints Constraint.NOT_NULL
            "customer_id" - INT foreignKey "customers(id)"
        }
    }

    override fun down() {
        dropTable("products")
        dropTable("customers")
    }
}

