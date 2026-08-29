import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.createtable.foreignKey
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "외래키가 있는 테이블 생성 - comments, tags, post_tags"

    override fun up() {
        // 댓글 테이블 (posts 참조)
        createTable("comments") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "post_id" - INT foreignKey "posts(id)"
            "user_id" - INT foreignKey "users(id)"
            "content" - TEXT constraints Constraint.NOT_NULL
            "created_at" - TIMESTAMP constraints Constraint.NOT_NULL
        }

        // 태그 테이블
        createTable("tags") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "name" - VARCHAR(50) constraints Constraint.UNIQUE and Constraint.NOT_NULL
        }

        // 게시글-태그 다대다 관계 테이블
        createTable("post_tags") {
            "post_id" - INT foreignKey "posts(id)"
            "tag_id" - INT foreignKey "tags(id)"
        }
    }

    override fun down() {
        dropTable("post_tags")
        dropTable("tags")
        dropTable("comments")
    }
}