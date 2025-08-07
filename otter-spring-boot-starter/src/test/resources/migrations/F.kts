import io.github.goodgoodjm.otter.core.Migration

object : Migration() {
    override val comment = "Modify 기능 검증용 - age 컬럼이 이미 변경되었는지 확인"

    override fun up() {
        // E.kts에서 Test.age가 NOT NULL CHECK(age >= 0)로 변경되었으므로
        // 음수를 넣으면 오류가 발생해야 함
        rawQuery("INSERT INTO test (name, description, status, score) VALUES ('modify test', 'test desc', 'active', 85.5)")
        
        // Simple test insert
        rawQuery("INSERT INTO products (name, customer_id) VALUES ('Test Product', 1)")
    }

    override fun down() {
    }
}