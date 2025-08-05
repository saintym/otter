import io.github.goodgoodjm.otter.core.Migration

object : Migration() {
    override val comment = "Modify 기능 검증용 - age 컬럼이 이미 변경되었는지 확인"

    override fun up() {
        // E.kts에서 Test.age가 NOT NULL CHECK(age >= 0)로 변경되었으므로
        // 음수를 넣으면 오류가 발생해야 함
        rawQuery("INSERT INTO Test (name, age, description, status, score, category_id) VALUES ('modify test', 25, 'test desc', 'active', 85.5, 1)")
        
        // 이것은 CHECK 제약조건 때문에 실패해야 함 (E.kts에서 수정됨)
        // rawQuery("INSERT INTO Test (name, age) VALUES ('fail test', -10)")
    }

    override fun down() {
    }
}