import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "ALTER TABLE - 컬럼 수정 테스트 (MODIFY)"

    override fun up() {
        // users 테이블 컬럼 수정
        alterTable("users") {
            // email 컬럼 크기 변경 및 UNIQUE 추가
            modify("email") - VARCHAR(500) constraints Constraint.UNIQUE and Constraint.NOT_NULL

            // age 컬럼 타입 변경 및 CHECK 제약조건 추가
            modify("age") - BIGINT constraints Constraint.CHECK("age >= 0 AND age <= 150")

            // phone 컬럼에 NOT NULL 추가
            modify("phone") - VARCHAR(20) constraints Constraint.NOT_NULL
        }

        // posts 테이블 컬럼 수정
        alterTable("posts") {
            // title 컬럼 크기 증가
            modify("title") - VARCHAR(500) constraints Constraint.NOT_NULL

            // view_count에 CHECK 제약조건 추가
            modify("view_count") - INT constraints Constraint.CHECK("view_count >= 0")
        }
    }

    override fun down() {
        // 원래 상태로 복구
        alterTable("users") {
            modify("email") - VARCHAR(255) constraints Constraint.NOT_NULL
            modify("age") - INT
            modify("phone") - VARCHAR(20)
        }

        alterTable("posts") {
            modify("title") - VARCHAR(200) constraints Constraint.NOT_NULL
            modify("view_count") - INT constraints Constraint.DEFAULT(0)
        }
    }
}