import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "복잡한 데이터 타입 테스트"

    override fun up() {
        // 다양한 데이터 타입을 가진 테이블
        createTable("products") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "name" - VARCHAR(100) constraints Constraint.NOT_NULL
            "price" - DECIMAL(10, 2) constraints Constraint.CHECK("price > 0")
            "description" - TEXT
            "is_available" - BOOLEAN constraints Constraint.DEFAULT(true)
            "stock_quantity" - INT constraints Constraint.DEFAULT(0)
            "created_date" - DATE constraints Constraint.NOT_NULL
            "last_updated" - TIMESTAMP
        }

        // 설정 테이블 (JSON 타입 등 - DB 지원 시)
        createTable("settings") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "key" - VARCHAR(100) constraints Constraint.UNIQUE and Constraint.NOT_NULL
            "value" - TEXT constraints Constraint.NOT_NULL
            "metadata" - TEXT  // JSON 대체
        }
    }

    override fun down() {
        dropTable("settings")
        dropTable("products")
    }
}