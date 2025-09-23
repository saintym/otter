package io.github.goodgoodjm.otter.security

import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.exception.MigrationScriptException
import io.github.goodgoodjm.otter.core.security.SecureMigrationScriptEngine
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class SecureMigrationScriptEngineTests {
    
    private val scriptEngine = SecureMigrationScriptEngine()
    
    @Test
    fun `정상적인 마이그레이션 스크립트는 성공적으로 실행됨`() {
        val script = """
            import io.github.goodgoodjm.otter.core.Migration
            import io.github.goodgoodjm.otter.core.dsl.type.*
            import io.github.goodgoodjm.otter.core.dsl.Constraint
            import io.github.goodgoodjm.otter.core.dsl.createtable.constraints
            
            object : Migration() {
                override val comment = "정상적인 마이그레이션"
                
                override fun up() {
                    createTable("users") {
                        "id" - INT constraints Constraint.PRIMARY
                        "name" - VARCHAR(100)
                    }
                }
                
                override fun down() {
                    dropTable("users")
                }
            }
        """.trimIndent()
        
        val migration = scriptEngine.evalMigration(script.reader())
        assertEquals("정상적인 마이그레이션", migration.comment)
    }
    
    @Test
    fun `Runtime 클래스 사용 시 보안 예외 발생`() {
        val script = """
            import io.github.goodgoodjm.otter.core.Migration
            
            object : Migration() {
                override val comment = "악의적인 마이그레이션"
                
                init {
                    Runtime.getRuntime().exec("whoami")
                }
                
                override fun up() {}
                override fun down() {}
            }
        """.trimIndent()
        
        val exception = assertThrows<SecurityException> {
            scriptEngine.evalMigration(script.reader())
        }
        
        assertTrue(exception.message?.contains("Runtime") == true)
        assertTrue(exception.message?.contains("시스템 명령 실행") == true)
    }
    
    @Test
    fun `파일 시스템 접근 시 보안 예외 발생`() {
        val script = """
            import io.github.goodgoodjm.otter.core.Migration
            import java.io.File
            
            object : Migration() {
                override val comment = "파일 접근 시도"
                
                override fun up() {
                    File("/etc/passwd").readText()
                }
                
                override fun down() {}
            }
        """.trimIndent()
        
        val exception = assertThrows<SecurityException> {
            scriptEngine.evalMigration(script.reader())
        }
        
        assertTrue(exception.message?.contains("File") == true)
        assertTrue(exception.message?.contains("파일 시스템 접근") == true)
    }
    
    @Test
    fun `네트워크 접근 시 보안 예외 발생`() {
        val script = """
            import io.github.goodgoodjm.otter.core.Migration
            import java.net.URL
            
            object : Migration() {
                override val comment = "네트워크 접근 시도"
                
                override fun up() {
                    URL("http://evil.com").readText()
                }
                
                override fun down() {}
            }
        """.trimIndent()
        
        val exception = assertThrows<SecurityException> {
            scriptEngine.evalMigration(script.reader())
        }
        
        assertTrue(exception.message?.contains("URL") == true)
    }
    
    @Test
    fun `리플렉션 사용 시 보안 예외 발생`() {
        val script = """
            import io.github.goodgoodjm.otter.core.Migration
            
            object : Migration() {
                override val comment = "리플렉션 시도"
                
                override fun up() {
                    val clazz = Class.forName("java.lang.System")
                    val method = clazz.getDeclaredMethod("exit", Int::class.java)
                    method.invoke(null, 0)
                }
                
                override fun down() {}
            }
        """.trimIndent()
        
        val exception = assertThrows<SecurityException> {
            scriptEngine.evalMigration(script.reader())
        }
        
        assertTrue(exception.message?.contains("Class.forName") == true)
    }
    
    @Test
    fun `시스템 속성 접근 시 보안 예외 발생`() {
        val script = """
            import io.github.goodgoodjm.otter.core.Migration
            
            object : Migration() {
                override val comment = "시스템 속성 접근"
                
                override fun up() {
                    val password = System.getenv("DB_PASSWORD")
                    println(password)
                }
                
                override fun down() {}
            }
        """.trimIndent()
        
        val exception = assertThrows<SecurityException> {
            scriptEngine.evalMigration(script.reader())
        }
        
        assertTrue(exception.message?.contains("시스템 속성이나 환경변수 접근이 금지") == true)
    }
    
    @Test
    fun `위험한 import 시 보안 예외 발생`() {
        val script = """
            import io.github.goodgoodjm.otter.core.Migration
            import java.io.*
            
            object : Migration() {
                override val comment = "위험한 import"
                
                override fun up() {}
                override fun down() {}
            }
        """.trimIndent()
        
        val exception = assertThrows<SecurityException> {
            scriptEngine.evalMigration(script.reader())
        }
        
        assertTrue(exception.message?.contains("java.io") == true)
        assertTrue(exception.message?.contains("import가 금지") == true)
    }
    
    @Test
    fun `스레드 생성 시 보안 예외 발생`() {
        val script = """
            import io.github.goodgoodjm.otter.core.Migration
            
            object : Migration() {
                override val comment = "스레드 생성 시도"
                
                override fun up() {
                    Thread {
                        println("악의적인 스레드")
                    }.start()
                }
                
                override fun down() {}
            }
        """.trimIndent()
        
        val exception = assertThrows<SecurityException> {
            scriptEngine.evalMigration(script.reader())
        }
        
        assertTrue(exception.message?.contains("Thread") == true)
    }
    
    @Test
    fun `System exit 호출 시 보안 예외 발생`() {
        val script = """
            import io.github.goodgoodjm.otter.core.Migration
            
            object : Migration() {
                override val comment = "시스템 종료 시도"
                
                override fun up() {
                    System.exit(0)
                }
                
                override fun down() {}
            }
        """.trimIndent()
        
        val exception = assertThrows<SecurityException> {
            scriptEngine.evalMigration(script.reader())
        }
        
        assertTrue(exception.message?.contains("System.exit") == true)
    }
    
    @Test
    fun `Migration이 아닌 객체 반환 시 예외 발생`() {
        val script = """
            "이것은 문자열입니다"
        """.trimIndent()
        
        val exception = assertThrows<MigrationScriptException> {
            scriptEngine.evalMigration(script.reader())
        }
        
        assertTrue(exception.message?.contains("Migration 객체를 반환하지 않았습니다") == true)
    }
}