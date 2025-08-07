import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.createtable.foreignKey
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "고급 테이블 생성 및 외래키 테스트"

    override fun up() {
        // 새로운 테이블 생성 (참조용)
        createTable("e_table") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "name" - VARCHAR(100) constraints Constraint.NOT_NULL and Constraint.UNIQUE
            "parent_id" - INT
        }
        
        // 또 다른 테이블 생성 (외래키 테스트)
        createTable("f_table") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "e_table_id" - INT foreignKey "e_table(id)"
            "data" - VARCHAR(255)
        }
    }

    override fun down() {
        dropTable("f_table")
        dropTable("e_table")
    }
}