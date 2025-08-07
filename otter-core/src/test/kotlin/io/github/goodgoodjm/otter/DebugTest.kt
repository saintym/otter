package io.github.goodgoodjm.otter

import io.github.goodgoodjm.otter.core.Migration
import io.github.goodgoodjm.otter.core.resourceresolver.ResourceResolver
import org.junit.jupiter.api.Test
import javax.script.ScriptEngineManager

class DebugTest {
    
    @Test
    fun testLoadMigrations() {
        val path = "test-migrations"
        val resolver = ResourceResolver()
        val entries = resolver.resolveEntries(path)
        
        println("Found migration files: ${entries.size}")
        entries.forEach { println("  - $it") }
        
        // 첫 번째 파일 로드 테스트
        if (entries.isNotEmpty()) {
            val firstEntry = entries.first()
            val resource = this::class.java.classLoader.getResource(firstEntry)
            println("\nLoading: $firstEntry")
            println("Resource URL: $resource")
            
            if (resource != null) {
                val content = resource.readText()
                println("Content length: ${content.length}")
                println("First 200 chars: ${content.take(200)}")
                
                // 스크립트 엔진으로 평가
                try {
                    val engine = ScriptEngineManager().getEngineByExtension("kts")
                    val migration = engine.eval(content) as Migration
                    println("\nMigration loaded successfully!")
                    println("Comment: ${migration.comment}")
                    println("Contexts: ${migration.contexts.size}")
                    migration.up()
                    println("Up executed")
                    migration.contexts.forEach { ctx ->
                        println("Context type: ${ctx::class.simpleName}")
                    }
                } catch (e: Exception) {
                    println("Error evaluating script: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}