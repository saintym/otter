import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.createtable.foreignKey
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "고급 ALTER TABLE 기능 테스트"

    override fun up() {
        // 새로운 테이블 생성 (참조용)
        createTable("categories") {
            "id" - INT constraints PRIMARY and AUTO_INCREMENT
            "name" - VARCHAR(100) constraints NOT_NULL and UNIQUE
            "parent_id" - INT foreignKey "categories(id)"
        }
        
        // 외래키와 복잡한 제약조건 추가
        alterTable("Test") {
            add("category_id") - INT foreignKey "categories(id)"
            add("metadata") - TEXT() constraints COMMENT("JSON 형식의 메타데이터")
            modify("age") - INT constraints (NOT_NULL and CHECK("age >= 0"))
        }
        
        // 생성된 컬럼 추가
        alterTable("person") {
            add("full_info") - VARCHAR(500) constraints GENERATED(BY_DEFAULT)
            add("updated_at") - TIMESTAMP() constraints (NOT_NULL and DEFAULT("CURRENT_TIMESTAMP"))
        }
        
        // COLLATE와 REFERENCES 제약조건 테스트
        alterTable("user") {
            add("display_name") - VARCHAR(100) constraints COLLATE("utf8mb4_unicode_ci")
            add("manager_id") - INT constraints REFERENCES("user", "id", onDelete = CASCADE)
        }
    }

    override fun down() {
        alterTable("user") {
            drop("manager_id")
            drop("display_name")
        }
        
        alterTable("person") {
            drop("updated_at")
            drop("full_info")
        }
        
        alterTable("Test") {
            drop("metadata")
            drop("category_id")
            modify("age") - INT
        }
        
        dropTable("categories")
    }
}