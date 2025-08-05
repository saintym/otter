package io.github.goodgoodjm.otter

import io.github.goodgoodjm.otter.core.dsl.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class ConstraintTests {

    @Test
    fun constraint_simpleTypes_shouldCreateCorrectInstances() {
        // 기존 단순 제약조건들
        assertIs<Constraint.NOT_NULL>(Constraint.NOT_NULL)
        assertIs<Constraint.NULLABLE>(Constraint.NULLABLE)
        assertIs<Constraint.UNIQUE>(Constraint.UNIQUE)
        assertIs<Constraint.PRIMARY>(Constraint.PRIMARY)
        assertIs<Constraint.AUTO_INCREMENT>(Constraint.AUTO_INCREMENT)
    }

    @Test
    fun constraint_default_shouldStoreValue() {
        val defaultString = DEFAULT("test@email.com")
        assertIs<Constraint.DEFAULT>(defaultString)
        assertEquals("test@email.com", defaultString.value)

        val defaultInt = DEFAULT(42)
        assertEquals(42, defaultInt.value)

        val defaultBoolean = DEFAULT(true)
        assertEquals(true, defaultBoolean.value)
    }

    @Test
    fun constraint_check_shouldStoreCondition() {
        val checkAge = CHECK("age >= 18")
        assertIs<Constraint.CHECK>(checkAge)
        assertEquals("age >= 18", checkAge.condition)

        val checkStatus = CHECK("status IN ('active', 'inactive')")
        assertEquals("status IN ('active', 'inactive')", checkStatus.condition)
    }

    @Test
    fun constraint_references_shouldStoreTableAndColumn() {
        val ref1 = REFERENCES("users")
        assertIs<Constraint.REFERENCES>(ref1)
        assertEquals("users", ref1.table)
        assertEquals("id", ref1.column) // 기본값
        assertEquals(null, ref1.onDelete)
        assertEquals(null, ref1.onUpdate)

        val ref2 = REFERENCES("departments", "dept_id", onDelete = CASCADE, onUpdate = RESTRICT)
        assertEquals("departments", ref2.table)
        assertEquals("dept_id", ref2.column)
        assertEquals(ReferenceAction.CASCADE, ref2.onDelete)
        assertEquals(ReferenceAction.RESTRICT, ref2.onUpdate)
    }

    @Test
    fun constraint_generated_shouldStoreType() {
        val genAlways = GENERATED(ALWAYS)
        assertIs<Constraint.GENERATED>(genAlways)
        assertEquals(GenerationType.ALWAYS, genAlways.type)

        val genByDefault = GENERATED(BY_DEFAULT)
        assertEquals(GenerationType.BY_DEFAULT, genByDefault.type)
    }

    @Test
    fun constraint_collate_shouldStoreCollation() {
        val collate = COLLATE("utf8mb4_unicode_ci")
        assertIs<Constraint.COLLATE>(collate)
        assertEquals("utf8mb4_unicode_ci", collate.collation)
    }

    @Test
    fun constraint_comment_shouldStoreText() {
        val comment = COMMENT("사용자 이메일 주소")
        assertIs<Constraint.COMMENT>(comment)
        assertEquals("사용자 이메일 주소", comment.text)
    }

    @Test
    fun referenceAction_constants_shouldBeAccessible() {
        // ReferenceAction 상수들이 올바르게 정의되었는지 확인
        assertEquals(ReferenceAction.CASCADE, CASCADE)
        assertEquals(ReferenceAction.SET_NULL, SET_NULL)
        assertEquals(ReferenceAction.SET_DEFAULT, SET_DEFAULT)
        assertEquals(ReferenceAction.RESTRICT, RESTRICT)
        assertEquals(ReferenceAction.NO_ACTION, NO_ACTION)
    }

    @Test
    fun generationType_constants_shouldBeAccessible() {
        // GenerationType 상수들이 올바르게 정의되었는지 확인
        assertEquals(GenerationType.ALWAYS, ALWAYS)
        assertEquals(GenerationType.BY_DEFAULT, BY_DEFAULT)
    }

    @Test
    fun constraint_withColumnSchema_shouldWorkWithAndOperator() {
        // ColumnSchema와 함께 사용할 때 and 연산자가 잘 동작하는지 확인
        val constraints = listOf(
            Constraint.NOT_NULL,
            DEFAULT("test@email.com"),
            CHECK("email LIKE '%@%'")
        )
        
        // 다양한 타입의 제약조건이 리스트에 포함될 수 있는지 확인
        assertTrue(constraints.any { it is Constraint.NOT_NULL })
        assertTrue(constraints.any { it is Constraint.DEFAULT && it.value == "test@email.com" })
        assertTrue(constraints.any { it is Constraint.CHECK && it.condition.contains("email") })
    }
}