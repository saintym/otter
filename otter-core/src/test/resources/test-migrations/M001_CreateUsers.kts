import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.*
import io.github.goodgoodjm.otter.core.dsl.type.*
import io.github.goodgoodjm.otter.core.dsl.Constraint

object : Migration() {
    override val comment = "Create users table"
    
    override fun up() {
        createTable("users") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "name" - VARCHAR(100) constraints Constraint.NOT_NULL
            "email" - VARCHAR(255) constraints Constraint.UNIQUE
        }
    }
    
    override fun down() {
        dropTable("users")
    }
}