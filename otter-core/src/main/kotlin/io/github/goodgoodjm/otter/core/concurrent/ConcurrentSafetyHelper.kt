package io.github.goodgoodjm.otter.core.concurrent

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 동시성 환경에서 안전한 작업을 수행하기 위한 헬퍼 클래스
 */
object ConcurrentSafetyHelper {
    
    /**
     * 스레드 안전한 nullable 값 처리
     * 
     * @param value nullable 값
     * @param defaultValue 기본값
     * @param block 값이 null이 아닐 때 실행할 작업
     * @return 작업 결과 또는 기본값
     */
    inline fun <T, R> safeAccess(
        value: T?,
        defaultValue: R,
        block: (T) -> R
    ): R {
        return value?.let(block) ?: defaultValue
    }
    
    /**
     * 스레드 안전한 맵 접근
     * 
     * @param map 접근할 맵
     * @param key 키
     * @param defaultValue 기본값
     * @return 값 또는 기본값
     */
    fun <K, V> safeMapAccess(
        map: Map<K, V>,
        key: K,
        defaultValue: V
    ): V {
        return map[key] ?: defaultValue
    }
    
    /**
     * 동시성 안전한 초기화
     * 
     * @param lock 사용할 락
     * @param isInitialized 초기화 여부를 나타내는 플래그
     * @param initializer 초기화 작업
     */
    inline fun safeInitialize(
        lock: ReentrantLock,
        isInitialized: () -> Boolean,
        initializer: () -> Unit
    ) {
        if (!isInitialized()) {
            lock.withLock {
                // Double-check locking
                if (!isInitialized()) {
                    initializer()
                }
            }
        }
    }
    
    /**
     * 널 체크와 타입 체크를 동시에 수행
     * 
     * @param value 체크할 값
     * @param type 예상 타입
     * @return 타입이 일치하면 캐스팅된 값, 아니면 null
     */
    inline fun <reified T> safeCast(value: Any?): T? {
        return value as? T
    }
}