package io.github.goodgoodjm.otter.core.io

import io.github.goodgoodjm.otter.core.Logger

/**
 * 사용자 입력을 처리하는 인터페이스
 * 테스트 시 모킹 가능하도록 추상화
 */
interface UserInputHandler {
    /**
     * 사용자에게 메시지를 표시하고 입력을 받습니다.
     * 
     * @param prompt 표시할 메시지
     * @return 사용자 입력 (null if EOF)
     */
    fun readInput(prompt: String): String?
    
    /**
     * 사용자에게 메시지를 표시합니다.
     * 
     * @param message 표시할 메시지
     */
    fun showMessage(message: String)
}

/**
 * 콘솔 기반 사용자 입력 핸들러
 */
class ConsoleUserInputHandler : UserInputHandler, Logger {
    override fun readInput(prompt: String): String? {
        print(prompt)
        return readLine()
    }
    
    override fun showMessage(message: String) {
        println(message)
    }
}

/**
 * 테스트용 사용자 입력 핸들러
 * 미리 정의된 응답을 반환합니다.
 */
class TestUserInputHandler(
    private val responses: MutableList<String> = mutableListOf()
) : UserInputHandler {
    private val messages = mutableListOf<String>()
    private var responseIndex = 0
    
    override fun readInput(prompt: String): String? {
        messages.add(prompt)
        return if (responseIndex < responses.size) {
            responses[responseIndex++]
        } else {
            null
        }
    }
    
    override fun showMessage(message: String) {
        messages.add(message)
    }
    
    /**
     * 테스트용: 응답 추가
     */
    fun addResponse(response: String) {
        responses.add(response)
    }
    
    /**
     * 테스트용: 표시된 메시지 목록 반환
     */
    fun getMessages(): List<String> = messages.toList()
    
    /**
     * 테스트용: 상태 초기화
     */
    fun reset() {
        messages.clear()
        responses.clear()
        responseIndex = 0
    }
}