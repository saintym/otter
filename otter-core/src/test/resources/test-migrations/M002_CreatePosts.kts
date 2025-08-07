import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.*
import io.github.goodgoodjm.otter.core.dsl.type.*
import io.github.goodgoodjm.otter.core.dsl.Constraint

object : Migration() {
    override val comment = "Create posts table"
    
    override fun up() {
        createTable("posts") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "title" - VARCHAR(200) constraints Constraint.NOT_NULL
            "user_id" - INT foreignKey "users(id)"
        }
    }
    
    override fun down() {
        dropTable("posts")
    }
}