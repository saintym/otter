import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "ALTER TABLE - 컬럼 추가 테스트"

    override fun up() {
        // users 테이블에 컬럼 추가
        alterTable("users") {
            add("phone") - VARCHAR(20)
            add("bio") - TEXT
            add("is_active") - BOOLEAN constraints Constraint.DEFAULT(true)
            add("last_login") - TIMESTAMP
        }

        // posts 테이블에 컬럼 추가
        alterTable("posts") {
            add("view_count") - INT constraints Constraint.DEFAULT(0)
            add("updated_at") - TIMESTAMP
            add("category") - VARCHAR(50)
        }
    }

    override fun down() {
        // users 테이블 컬럼 제거
        alterTable("users") {
            drop("last_login")
            drop("is_active")
            drop("bio")
            drop("phone")
        }

        // posts 테이블 컬럼 제거
        alterTable("posts") {
            drop("category")
            drop("updated_at")
            drop("view_count")
        }
    }
}