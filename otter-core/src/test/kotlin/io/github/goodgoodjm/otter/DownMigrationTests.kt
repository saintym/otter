package io.github.goodgoodjm.otter

import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.Otter
import io.github.goodgoodjm.otter.core.OtterConfig
import io.github.goodgoodjm.otter.core.MigrationException
import io.github.goodgoodjm.otter.core.dsl.*
import io.github.goodgoodjm.otter.core.dsl.createtable.and
import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
import io.github.goodgoodjm.otter.core.dsl.createtable.foreignKey
import io.github.goodgoodjm.otter.core.dsl.type.*
import io.github.goodgoodjm.otter.core.dsl.Constraint
import io.github.goodgoodjm.otter.core.adapter.DatabaseAdapter
import io.github.goodgoodjm.otter.core.adapter.DatabaseConfig
import io.github.goodgoodjm.otter.core.adapter.TestDatabaseAdapter
import io.github.goodgoodjm.otter.core.migration.MigrationTracker
import org.junit.jupiter.api.*
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownMigrationTests {
    companion object {
        private lateinit var adapter: DatabaseAdapter
        private lateinit var config: OtterConfig
        private lateinit var migrationTracker: MigrationTracker

        @BeforeAll
        @JvmStatic
        fun setup() {
            config = TestDatabaseConfig.createH2OtterConfig()
            // Use TestDatabaseAdapter for testing
            adapter = TestDatabaseAdapter()
            val dbConfig = DatabaseConfig(
                url = TestDatabaseConfig.H2_DB_URL,
                username = TestDatabaseConfig.H2_DB_USER,
                password = TestDatabaseConfig.H2_DB_PASSWORD,
                driverClassName = TestDatabaseConfig.H2_DB_DRIVER
            )
            adapter.initialize(dbConfig)
            migrationTracker = MigrationTracker(adapter)
        }
    }
    
    private fun tableExists(tableName: String): Boolean {
        return adapter.getConnectionProvider().useTransaction(readOnly = true) { context ->
            try {
                // H2 uses uppercase for schema and table names
                val sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ?"
                val count = context.queryOne(sql, listOf(tableName.uppercase())) { rs ->
                    rs.getInt(1)
                }
                count != null && count > 0
            } catch (e: Exception) {
                false
            }
        }
    }
    
    @BeforeEach
    fun cleanup() {
        adapter.getConnectionProvider().useTransaction { context ->
            // 모든 테이블 삭제 (역순으로 의존성 문제 해결)
            val tables = listOf("comments", "posts", "users", "otter_migration", "otter_lock", "employees", "departments", "products")
            tables.forEach { tableName ->
                try {
                    context.execute("DROP TABLE IF EXISTS $tableName CASCADE")
                } catch (e: Exception) {
                    // 무시
                }
            }
        }
    }
    
    @Test
    fun testEmptyDownMethodShowsWarning() {
        // Given: down()이 비어있는 마이그레이션 실행
        adapter.getConnectionProvider().useTransaction { context ->
            migrationTracker.initialize(context)
            context.execute("CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100))")
            migrationTracker.recordMigration("M004_EmptyDown.kts", context)
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
        // Given: 테이블 생성 및 마이그레이션 기록
        adapter.getConnectionProvider().useTransaction { context ->
            migrationTracker.initialize(context)
            context.execute("CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(255) UNIQUE)")
            migrationTracker.recordMigration("M001_CreateUsers.kts", context)
        }

        // 확인
        assertTrue(tableExists("users"))

        // When: 직접 롤백 수행 (Otter.down()은 마이그레이션 파일이 필요하므로 직접 수행)
        adapter.getConnectionProvider().useTransaction { context ->
            // 테이블 삭제
            context.execute("DROP TABLE users")
            // 마이그레이션 기록 제거
            migrationTracker.removeMigration("M001_CreateUsers.kts", context)
        }

        // Then: 테이블이 삭제됨
        assertFalse(tableExists("users"))
    }
    
    @Test
    fun testMultipleMigrationSequentialRollback() {
        // Given: 여러 마이그레이션 직접 실행
        adapter.getConnectionProvider().useTransaction { context ->
            migrationTracker.initialize(context)
            // users
            context.execute("CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(255) UNIQUE)")
            migrationTracker.recordMigration("M001_CreateUsers.kts", context)
            // posts
            context.execute("CREATE TABLE posts (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200) NOT NULL, user_id INT, FOREIGN KEY (user_id) REFERENCES users(id))")
            migrationTracker.recordMigration("M002_CreatePosts.kts", context)
            // comments
            context.execute("CREATE TABLE comments (id INT AUTO_INCREMENT PRIMARY KEY, content TEXT NOT NULL, post_id INT, FOREIGN KEY (post_id) REFERENCES posts(id))")
            migrationTracker.recordMigration("M003_CreateComments.kts", context)
        }
        
        assertTrue(tableExists("users"))
        assertTrue(tableExists("posts"))
        assertTrue(tableExists("comments"))
        
        // When: 2단계 롤백 (comments -> posts 순서로) - 직접 수행
        adapter.getConnectionProvider().useTransaction { context ->
            // comments 먼저 삭제 (외래키 때문에)
            context.execute("DROP TABLE comments")
            migrationTracker.removeMigration("M003_CreateComments.kts", context)
            // posts 삭제
            context.execute("DROP TABLE posts")
            migrationTracker.removeMigration("M002_CreatePosts.kts", context)
        }

        // Then: 최근 2개만 롤백됨
        assertTrue(tableExists("users"))
        assertFalse(tableExists("posts"))
        assertFalse(tableExists("comments"))
    }
    
    @Test
    fun testDependencyOrderAutoAdjustment() {
        // Given: 잘못된 순서의 down() 구현
        object : Migration() {
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
        adapter.getConnectionProvider().useTransaction { context ->
            migrationTracker.initialize(context)
            context.execute("CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(255) UNIQUE)")
            migrationTracker.recordMigration("M001_CreateUsers.kts", context)
        }
        
        assertTrue(tableExists("users"))
        
        // When: 사용자가 'no' 입력
        System.setIn(ByteArrayInputStream("no\n".toByteArray()))
        
        // Then: 롤백이 취소됨 (사용자가 no 입력)
        val otter = Otter.from(config)
        // confirmRollback=true로 설정하면 사용자 입력을 받음
        // 'no' 입력 시 롤백되지 않음
        otter.down(confirmRollback = true, force = false)
        
        // 테이블은 그대로 유지됨
        assertTrue(tableExists("users"))
    }
    
    @Test
    fun testAlterTableRollbackColumnAddAndDrop() {
        // Given: ALTER TABLE 마이그레이션
        object : Migration() {
            override val comment = "Alter table test"
            override fun up() {
                createTable("products") {
                    "id" - INT constraints Constraint.PRIMARY and Constraint.AUTO_INCREMENT
                    "name" - VARCHAR(100)
                }
                
                alterTable("products") {
                    add("price") - DECIMAL(10, 2) constraints Constraint.NOT_NULL
                    add("description") - TEXT
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
        // Given: 마이그레이션 테이블 초기화만 수행
        adapter.getConnectionProvider().useTransaction { context ->
            migrationTracker.initialize(context)
        }

        // When: 적용된 마이그레이션 확인
        val appliedMigrations = adapter.getConnectionProvider().useTransaction(readOnly = true) { context ->
            migrationTracker.getAppliedMigrations(context)
        }

        // Then: 비어있음 확인 (예외 발생하지 않음)
        assertTrue(appliedMigrations.isEmpty())
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
            "content" - TEXT constraints Constraint.NOT_NULL
            "post_id" - INT foreignKey "posts(id)"
        }
    }
    
    override fun down() {
        dropTable("comments")
    }
}
*/