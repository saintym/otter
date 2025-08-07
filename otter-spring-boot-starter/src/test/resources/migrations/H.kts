import io.github.goodgoodjm.otter.core.Migration

object : Migration() {
    override val comment = "최종 테스트 - 간단한 로그 테이블 추가"

    override fun up() {
        rawQuery("CREATE TABLE logs (id SERIAL PRIMARY KEY, message TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")
    }

    override fun down() {
        rawQuery("DROP TABLE logs")
    }
}