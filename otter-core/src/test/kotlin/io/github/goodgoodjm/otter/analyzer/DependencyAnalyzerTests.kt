package io.github.goodgoodjm.otter.analyzer

import io.github.goodgoodjm.otter.core.analyzer.CircularDependencyException
import io.github.goodgoodjm.otter.core.analyzer.DependencyAnalyzer
import io.github.goodgoodjm.otter.core.dsl.createtable.CreateTableContext
import io.github.goodgoodjm.otter.core.dsl.createtable.foreignKey
import io.github.goodgoodjm.otter.core.dsl.droptable.DropTableContext
import io.github.goodgoodjm.otter.core.dsl.type.INT
import io.github.goodgoodjm.otter.core.dsl.type.VARCHAR
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DependencyAnalyzerTests {
    
    private val analyzer = DependencyAnalyzer()
    
    @Test
    fun `단순 의존성 정렬`() {
        // Given: users -> posts -> comments 의존성
        val contexts = listOf(
            // 순서를 섞어서 생성
            createTable("comments") {
                "id" - INT
                "post_id" - (INT foreignKey "posts(id)")
            },
            createTable("users") {
                "id" - INT
            },
            createTable("posts") {
                "id" - INT
                "user_id" - (INT foreignKey "users(id)")
            }
        )

        // When: 의존성 분석 및 정렬
        val sorted = analyzer.analyzeAndSort(contexts)

        // Then: 의존성이 올바른 순서로 처리됨
        // users는 의존성이 없으므로 맨 처음
        val tableNames = sorted.map { (it as CreateTableContext).tableName }

        val usersIndex = tableNames.indexOf("users")
        val postsIndex = tableNames.indexOf("posts")
        val commentsIndex = tableNames.indexOf("comments")

        // users는 posts보다 먼저
        assert(usersIndex < postsIndex) { "users should come before posts (users: $usersIndex, posts: $postsIndex)" }
        // posts는 comments보다 먼저
        assert(postsIndex < commentsIndex) { "posts should come before comments (posts: $postsIndex, comments: $commentsIndex)" }
    }
    
    @Test
    fun `롤백 시 역순 정렬`() {
        // Given
        val contexts = listOf(
            createTable("users") { "id" - INT },
            createTable("posts") {
                "id" - INT
                "user_id" - (INT foreignKey "users(id)")
            }
        )
        
        // When: 롤백용 정렬
        val sorted = analyzer.analyzeAndSort(contexts, forRollback = true)
        
        // Then: 역순으로 정렬됨 (posts -> users)
        assertEquals("posts", (sorted[0] as CreateTableContext).tableName)
        assertEquals("users", (sorted[1] as CreateTableContext).tableName)
    }
    
    @Test
    fun `순환 의존성 감지`() {
        // Given: A -> B -> C -> A 순환 의존성
        val contexts = listOf(
            createTable("table_a") {
                "id" - INT
                "b_id" - (INT foreignKey "table_b(id)")
            },
            createTable("table_b") {
                "id" - INT
                "c_id" - (INT foreignKey "table_c(id)")
            },
            createTable("table_c") {
                "id" - INT
                "a_id" - (INT foreignKey "table_a(id)")
            }
        )

        // When & Then: 순환 의존성 예외 발생
        val exception = assertFailsWith<CircularDependencyException> {
            analyzer.analyzeAndSort(contexts)
        }

        // 순환 경로가 메시지에 포함됨
        assert(exception.message?.contains("Circular dependency detected") == true ||
               exception.message?.contains("table_") == true)
    }
    
    @Test
    fun `자기 참조 테이블 처리`() {
        // Given: 자기 참조하는 테이블
        val contexts = listOf(
            createTable("categories") {
                "id" - INT
                "parent_id" - (INT foreignKey "categories(id)")
            }
        )
        
        // When: 정렬 (자기 참조는 문제없음)
        val sorted = analyzer.analyzeAndSort(contexts)
        
        // Then: 정상적으로 처리됨
        assertEquals(1, sorted.size)
        assertEquals("categories", (sorted[0] as CreateTableContext).tableName)
    }
    
    @Test
    fun `DROP 작업 포함 정렬`() {
        // Given: CREATE와 DROP이 섞인 작업들
        val contexts = listOf(
            createTable("users") { "id" - INT },
            DropTableContext("old_table"),
            createTable("posts") {
                "id" - INT
                "user_id" - (INT foreignKey "users(id)")
            }
        )
        
        // When
        val sorted = analyzer.analyzeAndSort(contexts)
        
        // Then: DROP도 정렬에 포함됨
        assertEquals(3, sorted.size)
    }
    
    @Test
    fun `독립적인 테이블들 순서 유지`() {
        // Given: 서로 독립적인 테이블들
        val contexts = listOf(
            createTable("table1") { "id" - INT },
            createTable("table2") { "id" - INT },
            createTable("table3") { "id" - INT }
        )
        
        // When: 정렬
        val sorted = analyzer.analyzeAndSort(contexts)
        
        // Then: 원래 순서 유지 (의존성이 없으므로)
        assertEquals("table1", (sorted[0] as CreateTableContext).tableName)
        assertEquals("table2", (sorted[1] as CreateTableContext).tableName)
        assertEquals("table3", (sorted[2] as CreateTableContext).tableName)
    }
    
    private fun createTable(name: String, block: CreateTableContext.() -> Unit): CreateTableContext {
        return CreateTableContext(name).apply(block)
    }
}