import io.github.goodgoodjm.otter.core.Migration

object : Migration() {
    override val comment = "샘플 데이터 삽입"

    override fun up() {
        // 사용자 데이터 삽입
        rawQuery("INSERT INTO users (username, email, age, created_at) VALUES ('john_doe', 'john@example.com', 25, CURRENT_TIMESTAMP)")
        rawQuery("INSERT INTO users (username, email, age, created_at) VALUES ('jane_smith', 'jane@example.com', 30, CURRENT_TIMESTAMP)")
        rawQuery("INSERT INTO users (username, email, age, created_at) VALUES ('bob_wilson', 'bob@example.com', 35, CURRENT_TIMESTAMP)")

        // 게시글 데이터 삽입
        rawQuery("INSERT INTO posts (title, content, author_id, published, created_at) VALUES ('First Post', 'This is my first post', 1, true, CURRENT_TIMESTAMP)")
        rawQuery("INSERT INTO posts (title, content, author_id, published, created_at) VALUES ('Second Post', 'Another interesting post', 2, true, CURRENT_TIMESTAMP)")
        rawQuery("INSERT INTO posts (title, content, author_id, published, created_at) VALUES ('Draft Post', 'This is a draft', 1, false, CURRENT_TIMESTAMP)")

        // 태그 데이터 삽입
        rawQuery("INSERT INTO tags (name) VALUES ('technology')")
        rawQuery("INSERT INTO tags (name) VALUES ('programming')")
        rawQuery("INSERT INTO tags (name) VALUES ('database')")

        // 게시글-태그 관계 데이터
        rawQuery("INSERT INTO post_tags (post_id, tag_id) VALUES (1, 1)")
        rawQuery("INSERT INTO post_tags (post_id, tag_id) VALUES (1, 2)")
        rawQuery("INSERT INTO post_tags (post_id, tag_id) VALUES (2, 3)")
    }

    override fun down() {
        rawQuery("DELETE FROM post_tags")
        rawQuery("DELETE FROM tags")
        rawQuery("DELETE FROM comments")
        rawQuery("DELETE FROM posts")
        rawQuery("DELETE FROM users")
    }
}