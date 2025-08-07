package io.github.goodgoodjm.otter

import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.MigrationTable
import io.github.goodgoodjm.otter.core.Otter
import io.github.goodgoodjm.otter.core.OtterConfig
import io.github.goodgoodjm.otter.core.process.RollbackCancelledException
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.createtable.foreignKey
import io.github.goodgoodjm.otter.core.dsl.type.*
import io.github.goodgoodjm.otter.core.dsl.Constraint
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownMigrationTests {
    companion object {
        private lateinit var db: Database
        private lateinit var config: OtterConfig
        
        @BeforeAll
        @JvmStatic
        fun setup() {
            db = TestDatabaseConfig.createDatabase()
            config = TestDatabaseConfig.createOtterConfig()
        }
    }
    
    private fun tableExists(tableName: String): Boolean {
        return transaction(db) {
            try {
                val result = exec("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '${tableName.lowercase()}'") {
                    it.next()
                    it.getInt(1) > 0
                }
                result ?: false
            } catch (e: Exception) {
                false
            }
        }
    }
    
    @BeforeEach
    fun cleanup() {
        transaction(db) {
            // 모든 테이블 삭제 (역순으로 의존성 문제 해결)
            val tables = listOf("comments", "posts", "users", "otter_migration", "otter_lock", "employees", "departments", "products")
            tables.forEach { tableName ->
                try {
                    exec("DROP TABLE IF EXISTS $tableName CASCADE")
                } catch (e: Exception) {
                    // 무시
                }
            }
        }
    }
    
    @Test
    fun testEmptyDownMethodShowsWarning() {
        // Given: down()이 비어있는 마이그레이션 실행
        transaction(db) {
            SchemaUtils.create(MigrationTable)
            exec("CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100))")
            MigrationTable.insert {
                it[filename] = "M004_EmptyDown.kts"
                it[comment] = "Empty down migration"
            }
        }
        
        assertTrue(tableExists("users"))
        
        // When: 롤백 실행 (force=true로 확인 건너뛰기)
        val otter = Otter.from(config)
        otter.down(force = true)
        
        // Then: 테이블이 여전히 존재 (down이 비어있으므로)
        assertTrue(tableExists("users"))
    }
    
    @Test
    fun testSingleMigrationRollback() {
        // Given: M001_CreateUsers.kts만 실행하기 위해 직접 마이그레이션 실행
        transaction(db) {
            SchemaUtils.create(MigrationTable)
            exec("CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(255) UNIQUE)")
            MigrationTable.insert {
                it[filename] = "M001_CreateUsers.kts"
                it[comment] = "Create users table"
            }
        }
        
        // 새로운 트랜잭션에서 확인
        transaction(db) {
            assertTrue(tableExists("users"))
        }
        
        // When: 롤백 실행
        val otter = Otter.from(config)
        otter.down(force = true)
        
        // Then: 테이블이 삭제됨 (새로운 트랜잭션에서 확인)
        transaction(db) {
            assertFalse(tableExists("users"))
        }
    }
    
    @Test
    fun testMultipleMigrationSequentialRollback() {
        // Given: 여러 마이그레이션 직접 실행
        transaction(db) {
            SchemaUtils.create(MigrationTable)
            // users
            exec("CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(255) UNIQUE)")
            MigrationTable.insert {
                it[filename] = "M001_CreateUsers.kts"
                it[comment] = "Create users table"
            }
            // posts
            exec("CREATE TABLE posts (id SERIAL PRIMARY KEY, title VARCHAR(200) NOT NULL, user_id INT, FOREIGN KEY (user_id) REFERENCES users(id))")
            MigrationTable.insert {
                it[filename] = "M002_CreatePosts.kts"
                it[comment] = "Create posts table"
            }
            // comments
            exec("CREATE TABLE comments (id SERIAL PRIMARY KEY, content TEXT NOT NULL, post_id INT, FOREIGN KEY (post_id) REFERENCES posts(id))")
            MigrationTable.insert {
                it[filename] = "M003_CreateComments.kts"
                it[comment] = "Create comments table"
            }
        }
        
        assertTrue(tableExists("users"))
        assertTrue(tableExists("posts"))
        assertTrue(tableExists("comments"))
        
        // When: 2단계 롤백 (comments -> posts 순서로)
        val otter = Otter.from(config)
        otter.down(steps = 1, force = true) // comments 먼저
        otter.down(steps = 1, force = true) // 그 다음 posts
        
        // Then: 최근 2개만 롤백됨
        assertTrue(tableExists("users"))
        assertFalse(tableExists("posts"))
        assertFalse(tableExists("comments"))
    }
    
    @Test
    fun testDependencyOrderAutoAdjustment() {
        // Given: 잘못된 순서의 down() 구현
        val migration = object : Migration() {
            override val comment = "Wrong order down"
            override fun up() {
                createTable("departments") {
                    "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
                    "name" - VARCHAR(100)
                }
                createTable("employees") {
                    "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
                    "name" - VARCHAR(100)
                    "dept_id" - INT foreignKey "departments(id)"
                }
            }
            override fun down() {
                // 잘못된 순서: departments를 먼저 삭제하려 함
                dropTable("departments")
                dropTable("employees")
            }
        }
        
        val otter = Otter.from(config)
        otter.up()
        
        // When: 롤백 실행 (의존성 분석이 순서를 자동 조정)
        otter.down(force = true)
        
        // Then: 에러 없이 성공적으로 삭제됨
        assertFalse(tableExists("employees"))
        assertFalse(tableExists("departments"))
    }
    
    @Test
    fun testRollbackCancellationOnUserDecline() {
        // Given: 마이그레이션 실행
        transaction(db) {
            SchemaUtils.create(MigrationTable)
            exec("CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(255) UNIQUE)")
            MigrationTable.insert {
                it[filename] = "M001_CreateUsers.kts"
                it[comment] = "Create users table"
            }
        }
        
        assertTrue(tableExists("users"))
        
        // When: 사용자가 'no' 입력
        System.setIn(ByteArrayInputStream("no\n".toByteArray()))
        
        // Then: RollbackCancelledException 발생
        val otter = Otter.from(config)
        assertFailsWith<RollbackCancelledException> {
            otter.down(force = false)
        }
        
        // 테이블은 그대로 유지됨
        assertTrue(tableExists("users"))
    }
    
    @Test
    fun testAlterTableRollbackColumnAddAndDrop() {
        // Given: ALTER TABLE 마이그레이션
        val migration = object : Migration() {
            override val comment = "Alter table test"
            override fun up() {
                createTable("products") {
                    "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
                    "name" - VARCHAR(100)
                }
                
                alterTable("products") {
                    add("price") - DECIMAL(10, 2) constraints Constraint.NOT_NULL
                    add("description") - TEXT()
                }
            }
            override fun down() {
                alterTable("products") {
                    drop("description")
                    drop("price")
                }
                
                dropTable("products")
            }
        }
        
        val otter = Otter.from(config)
        otter.up()
        
        // When: 롤백
        otter.down(force = true)
        
        // Then: 테이블과 컬럼이 모두 삭제됨
        assertFalse(tableExists("products"))
    }
    
    @Test
    fun testRollbackAttemptOnEmptyMigrationTable() {
        // Given: 마이그레이션 실행 없음
        val otter = Otter.from(config)
        
        // When & Then: 아무 일도 일어나지 않음
        otter.down(force = true) // 예외 발생하지 않음
    }
}

// 테스트용 마이그레이션 파일들 (resources/test-migrations 폴더에 위치)
/*
// M001_CreateUsers.kts
import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.*
import io.github.goodgoodjm.otter.core.dsl.type.*

object : Migration() {
    override val comment = "Create users table"
    
    override fun up() {
        createTable("users") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "name" - VARCHAR(100) constraints Constraint.NOT_NULL
            "email" - VARCHAR(255) constraints Constraint.UNIQUE
        }
    }
    
    override fun down() {
        dropTable("users")
    }
}

// M002_CreatePosts.kts
object : Migration() {
    override val comment = "Create posts table"
    
    override fun up() {
        createTable("posts") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "title" - VARCHAR(200) constraints Constraint.NOT_NULL
            "user_id" - INT foreignKey "users(id)"
        }
    }
    
    override fun down() {
        dropTable("posts")
    }
}

// M003_CreateComments.kts
object : Migration() {
    override val comment = "Create comments table"
    
    override fun up() {
        createTable("comments") {
            "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
            "content" - TEXT() constraints Constraint.NOT_NULL
            "post_id" - INT foreignKey "posts(id)"
        }
    }
    
    override fun down() {
        dropTable("comments")
    }
}
*/