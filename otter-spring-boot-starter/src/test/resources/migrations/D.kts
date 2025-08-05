import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.createtable.foreignKey
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "ALTER TABLE 테스트"

    override fun up() {
        // person 테이블에 컬럼 추가
        alterTable("person") {
            add("email") - VARCHAR(255) constraints UNIQUE
            add("created_at") - TIMESTAMP() constraints NOT_NULL
            add("bio") - TEXT()
        }
        
        // user 테이블 수정
        alterTable("user") {
            add("username") - VARCHAR(50) constraints NOT_NULL and UNIQUE
            add("password") - VARCHAR(255) constraints NOT_NULL
            add("is_active") - BOOL constraints (NOT_NULL and DEFAULT(true))
        }
        
        // Test 테이블에 제약조건이 있는 컬럼 추가
        alterTable("Test") {
            add("description") - TEXT() constraints NULLABLE
            add("status") - VARCHAR(20) constraints (NOT_NULL and DEFAULT("active") and CHECK("status IN ('active', 'inactive', 'pending')"))
            add("score") - DECIMAL(5, 2) constraints CHECK("score >= 0 AND score <= 100")
        }
    }

    override fun down() {
        // 역순으로 컬럼 제거
        alterTable("Test") {
            drop("score")
            drop("status")
            drop("description")
        }
        
        alterTable("user") {
            drop("is_active")
            drop("password")
            drop("username")
        }
        
        alterTable("person") {
            drop("bio")
            drop("created_at")
            drop("email")
        }
    }
}