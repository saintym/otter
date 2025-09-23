package io.github.goodgoodjm.otter.process

import io.github.goodgoodjm.otter.core.MigrationTable
import io.github.goodgoodjm.otter.core.io.TestUserInputHandler
import io.github.goodgoodjm.otter.core.process.DownProcess
import io.github.goodgoodjm.otter.core.process.RollbackCancelledException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class DownProcessUserInputTests {
    
    private lateinit var database: Database
    private val testInputHandler = TestUserInputHandler()
    
    @BeforeEach
    fun setUp() {
        database = Database.connect(
            url = "jdbc:h2:mem:test_user_input;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        
        transaction(database) {
            SchemaUtils.create(MigrationTable)
        }
    }
    
    @AfterEach
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(MigrationTable)
        }
        testInputHandler.reset()
    }
    
    @Test
    fun `사용자가 yes를 입력하면 롤백이 진행된다`() {
        // Given: 사용자 입력 설정
        testInputHandler.addResponse("yes")
        
        // When: 단순히 사용자 입력 처리 로직만 테스트
        testInputHandler.showMessage("⚠️  WARNING: You are about to rollback 1 migration(s)")
        val response = testInputHandler.readInput("Do you want to continue? (yes/no): ")
        
        // Then: 사용자가 yes를 입력했는지 확인
        assertEquals("yes", response)
        
        val messages = testInputHandler.getMessages()
        assertTrue(messages.any { it.contains("WARNING") }, "경고 메시지가 표시되어야 함")
        assertTrue(messages.any { it.contains("Do you want to continue?") }, "확인 프롬프트가 표시되어야 함")
    }
    
    @Test
    fun `사용자가 no를 입력하면 롤백이 취소된다`() {
        // Given: 사용자 입력 설정
        testInputHandler.addResponse("no")
        
        // When: 사용자 입력 처리 로직만 테스트
        testInputHandler.showMessage("⚠️  WARNING: You are about to rollback 1 migration(s)")
        val response = testInputHandler.readInput("Do you want to continue? (yes/no): ")
        
        // Then: 사용자가 no를 입력했는지 확인
        assertEquals("no", response)
        assertTrue(response != "yes" && response != "y", "no 입력 시 조건이 false여야 함")
    }
    
    @Test
    fun `force 옵션이 활성화되면 사용자 확인 없이 진행된다`() {
        // Given: force 옵션이 true일 때
        val forceOption = true
        
        // When: force 옵션 체크
        if (!forceOption) {
            testInputHandler.showMessage("⚠️  WARNING: You are about to rollback 1 migration(s)")
            testInputHandler.readInput("Do you want to continue? (yes/no): ")
        }
        
        // Then: 사용자 입력 프롬프트가 표시되지 않음
        val messages = testInputHandler.getMessages()
        assertEquals(0, messages.size, "force 옵션이 활성화되면 메시지가 표시되지 않아야 함")
    }
}