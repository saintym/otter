import io.github.goodgoodjm.otter.core.Migration

object : Migration() {
    override val comment = "Raw SQL 쿼리 테스트"

    override fun up() {
        // 인덱스 생성
        rawQuery("CREATE INDEX idx_users_email ON users(email)")
        rawQuery("CREATE INDEX idx_posts_author ON posts(author_id)")
        rawQuery("CREATE INDEX idx_posts_published ON posts(published)")

        // 뷰 생성 (H2 지원)
        rawQuery("""
            CREATE VIEW active_users AS
            SELECT u.id, u.username, u.email, COUNT(p.id) as post_count
            FROM users u
            LEFT JOIN posts p ON u.id = p.author_id
            WHERE u.is_active = true
            GROUP BY u.id, u.username, u.email
        """)

        // 복합 인덱스
        rawQuery("CREATE INDEX idx_posts_author_published ON posts(author_id, published)")
    }

    override fun down() {
        rawQuery("DROP VIEW IF EXISTS active_users")
        rawQuery("DROP INDEX IF EXISTS idx_posts_author_published")
        rawQuery("DROP INDEX IF EXISTS idx_posts_published")
        rawQuery("DROP INDEX IF EXISTS idx_posts_author")
        rawQuery("DROP INDEX IF EXISTS idx_users_email")
    }
}