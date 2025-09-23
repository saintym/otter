package io.github.goodgoodjm.otter.core.security

import java.io.FilePermission
import java.net.SocketPermission
import java.security.Permission

/**
 * 마이그레이션 스크립트 실행 시 적용되는 제한적인 SecurityManager
 * 
 * 파일 시스템, 네트워크, 시스템 명령 실행 등을 차단합니다.
 */
class RestrictiveMigrationSecurityManager : SecurityManager() {
    
    private val allowedPermissions = setOf(
        // 기본적인 property 읽기는 허용 (쓰기는 금지)
        "java.version",
        "java.vendor", 
        "java.home",
        "os.name",
        "os.arch",
        "os.version",
        "file.separator",
        "path.separator",
        "line.separator",
        "user.dir",
        "java.class.path"
    )
    
    override fun checkPermission(perm: Permission) {
        when (perm) {
            // 파일 시스템 접근 차단
            is FilePermission -> {
                // .kts 파일 자체는 읽을 수 있어야 함
                if (perm.actions == "read" && perm.name.endsWith(".kts")) {
                    return
                }
                throw SecurityException(
                    "파일 시스템 접근이 금지되어 있습니다: ${perm.name} (${perm.actions})"
                )
            }
            
            // 네트워크 접근 차단
            is SocketPermission -> {
                throw SecurityException(
                    "네트워크 접근이 금지되어 있습니다: ${perm.name} (${perm.actions})"
                )
            }
            
            // 런타임 권한 제한
            is RuntimePermission -> {
                when (perm.name) {
                    // 차단할 권한들
                    "exitVM", "exitVM.*" -> {
                        throw SecurityException("JVM 종료가 금지되어 있습니다")
                    }
                    "setSecurityManager" -> {
                        throw SecurityException("SecurityManager 변경이 금지되어 있습니다")
                    }
                    "createClassLoader" -> {
                        throw SecurityException("ClassLoader 생성이 금지되어 있습니다")
                    }
                    "accessDeclaredMembers" -> {
                        throw SecurityException("리플렉션 접근이 금지되어 있습니다")
                    }
                    "modifyThread", "modifyThreadGroup" -> {
                        throw SecurityException("스레드 조작이 금지되어 있습니다")
                    }
                    // 일부 권한은 허용 (예: 로깅)
                    "accessClassInPackage.sun.reflect",
                    "accessClassInPackage.jdk.internal.reflect" -> {
                        // Kotlin 스크립트 엔진이 필요로 함
                        return
                    }
                }
            }
            
            // 프로퍼티 권한 확인
            is java.util.PropertyPermission -> {
                if (perm.actions.contains("write")) {
                    throw SecurityException(
                        "시스템 속성 쓰기가 금지되어 있습니다: ${perm.name}"
                    )
                }
                // 일부 속성 읽기는 허용
                if (perm.actions == "read" && perm.name in allowedPermissions) {
                    return
                }
                // 그 외 속성 읽기는 차단
                if (!isAllowedPropertyRead(perm.name)) {
                    throw SecurityException(
                        "시스템 속성 읽기가 제한되어 있습니다: ${perm.name}"
                    )
                }
            }
        }
    }
    
    override fun checkExec(cmd: String) {
        throw SecurityException("외부 프로세스 실행이 금지되어 있습니다: $cmd")
    }
    
    override fun checkConnect(host: String, port: Int) {
        throw SecurityException("네트워크 연결이 금지되어 있습니다: $host:$port")
    }
    
    override fun checkRead(file: String) {
        // .kts 파일과 classpath 리소스는 읽을 수 있어야 함
        if (!file.endsWith(".kts") && !file.contains("kotlin") && !file.contains(".jar")) {
            throw SecurityException("파일 읽기가 제한되어 있습니다: $file")
        }
    }
    
    override fun checkWrite(file: String) {
        throw SecurityException("파일 쓰기가 금지되어 있습니다: $file")
    }
    
    override fun checkDelete(file: String) {
        throw SecurityException("파일 삭제가 금지되어 있습니다: $file")
    }
    
    override fun checkLink(lib: String) {
        throw SecurityException("네이티브 라이브러리 로딩이 금지되어 있습니다: $lib")
    }
    
    override fun checkExit(status: Int) {
        throw SecurityException("JVM 종료가 금지되어 있습니다 (status: $status)")
    }
    
    override fun checkAccess(t: Thread) {
        // 현재 스레드 접근은 허용
        if (t != Thread.currentThread()) {
            throw SecurityException("다른 스레드 접근이 금지되어 있습니다")
        }
    }
    
    override fun checkAccess(g: ThreadGroup) {
        // 현재 스레드 그룹 접근은 허용
        if (g != Thread.currentThread().threadGroup) {
            throw SecurityException("다른 스레드 그룹 접근이 금지되어 있습니다")
        }
    }
    
    private fun isAllowedPropertyRead(property: String): Boolean {
        // Kotlin 스크립트 엔진이 필요로 하는 속성들
        return property.startsWith("kotlin.") || 
               property.startsWith("java.") ||
               property.startsWith("file.") ||
               property.startsWith("os.") ||
               property.startsWith("path.") ||
               property.startsWith("line.") ||
               property in allowedPermissions
    }
}