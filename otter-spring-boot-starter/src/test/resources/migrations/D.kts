import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.createtable.foreignKey
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "ALTER TABLE 테스트"

    override fun up() {
        // test 테이블에 컬럼 추가
        alterTable("test") {
            add("description") - TEXT()
            add("status") - VARCHAR(20) constraints Constraint.NOT_NULL
            add("score") - DECIMAL(5, 2)
        }
    }

    override fun down() {
        alterTable("test") {
            drop("score")
            drop("status")
            drop("description")
        }
    }
}