import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "기본 테이블 생성 - users, posts"

    override fun up() {
        // 사용자 테이블 생성
        createTable("users") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "username" - VARCHAR(50) constraints Constraint.UNIQUE and Constraint.NOT_NULL
            "email" - VARCHAR(255) constraints Constraint.NOT_NULL
            "age" - INT
            "created_at" - TIMESTAMP constraints Constraint.NOT_NULL
        }

        // 게시글 테이블 생성
        createTable("posts") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "title" - VARCHAR(200) constraints Constraint.NOT_NULL
            "content" - TEXT
            "author_id" - INT constraints Constraint.NOT_NULL
            "published" - BOOLEAN constraints Constraint.DEFAULT(false)
            "created_at" - TIMESTAMP constraints Constraint.NOT_NULL
        }
    }

    override fun down() {
        dropTable("posts")
        dropTable("users")
    }
}