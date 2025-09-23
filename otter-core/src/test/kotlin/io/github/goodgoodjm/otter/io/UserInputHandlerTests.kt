package io.github.goodgoodjm.otter.io

import io.github.goodgoodjm.otter.core.io.TestUserInputHandler
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserInputHandlerTests {
    
    @Test
    fun `TestUserInputHandler는 사전 정의된 응답을 반환한다`() {
        val handler = TestUserInputHandler()
        handler.addResponse("yes")
        handler.addResponse("no")
        
        assertEquals("yes", handler.readInput("Continue? "))
        assertEquals("no", handler.readInput("Really? "))
        assertNull(handler.readInput("More? ")) // 응답이 모두 소진됨
    }
    
    @Test
    fun `TestUserInputHandler는 모든 메시지를 기록한다`() {
        val handler = TestUserInputHandler()
        handler.addResponse("yes")
        
        handler.showMessage("Warning!")
        handler.readInput("Continue? ")
        handler.showMessage("Done.")
        
        val messages = handler.getMessages()
        assertEquals(3, messages.size)
        assertEquals("Warning!", messages[0])
        assertEquals("Continue? ", messages[1])
        assertEquals("Done.", messages[2])
    }
    
    @Test
    fun `reset 메서드는 상태를 초기화한다`() {
        val handler = TestUserInputHandler()
        handler.addResponse("yes")
        handler.showMessage("Test")
        
        handler.reset()
        
        assertEquals(0, handler.getMessages().size)
        assertNull(handler.readInput("Continue? "))
    }
}