import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.*
import io.github.goodgoodjm.otter.core.dsl.type.*
import io.github.goodgoodjm.otter.core.dsl.Constraint

object : Migration() {
    override val comment = "Create comments table"
    
    override fun up() {
        createTable("comments") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "content" - TEXT() constraints Constraint.NOT_NULL
            "post_id" - INT foreignKey "posts(id)"
        }
    }
    
    override fun down() {
        dropTable("comments")
    }
}