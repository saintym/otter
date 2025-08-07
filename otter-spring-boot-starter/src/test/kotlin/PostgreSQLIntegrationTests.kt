import io.github.goodgoodjm.otter.OtterAutoConfiguration
import io.github.goodgoodjm.otter.core.Otter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.sql.DriverManager

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PostgreSQLIntegrationTests {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OtterAutoConfiguration::class.java))
    
    companion object {
        const val DB_BASE_URL = "jdbc:postgresql://localhost:5433/otter_test"
        const val DB_USER = "postgres"
        const val DB_PASSWORD = "postgres"
        const val DB_DRIVER = "org.postgresql.Driver"
        
        fun getSchemaUrl(schemaName: String): String {
            return "${DB_BASE_URL}?currentSchema=${schemaName}"
        }
    }
    
    private fun cleanSchema(schemaName: String) {
        // 스키마를 삭제하고 다시 생성하여 완전히 정리
        DriverManager.getConnection(DB_BASE_URL, DB_USER, DB_PASSWORD).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DROP SCHEMA IF EXISTS $schemaName CASCADE")
                stmt.execute("CREATE SCHEMA $schemaName")
            }
        }
    }
    
    @Test
    @Order(1)
    fun testPostgreSQLMigrationFlowFromAToC() {
        val schemaName = "otter_integration_test"
        val dbUrl = getSchemaUrl(schemaName)
        cleanSchema(schemaName)
        
        // C.kts까지만 실행 (간단한 테스트)
        contextRunner.withPropertyValues(
            "otter.driverClassName=$DB_DRIVER",
            "otter.url=$dbUrl",
            "otter.username=$DB_USER",
            "otter.password=$DB_PASSWORD",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=C.kts",
            "otter.testMode=true"
        ).run { context ->
            // SpringBoot 컨텍스트가 정상적으로 시작되는지 확인
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            
            DriverManager.getConnection(dbUrl, DB_USER, DB_PASSWORD).use { conn ->
                conn.createStatement().use { stmt ->
                    // A.kts에서 생성된 test 테이블 확인
                    val testTableExists = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$schemaName' AND table_name = 'test'")
                    testTableExists.next()
                    assertThat(testTableExists.getInt(1)).isEqualTo(1)
                    
                    // B.kts에서 생성된 customers, products 테이블 확인
                    val businessTablesCount = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$schemaName' AND table_name IN ('customers', 'products')")
                    businessTablesCount.next()
                    assertThat(businessTablesCount.getInt(1)).isEqualTo(2)
                    
                    // C.kts는 rawQuery 테스트이므로 별도 테이블 생성 없음 - 정상 실행되었음을 확인
                    val migrationRecords = stmt.executeQuery("SELECT COUNT(*) FROM otter_migration WHERE filename IN ('A.kts', 'B.kts', 'C.kts')")
                    migrationRecords.next()
                    assertThat(migrationRecords.getInt(1)).isEqualTo(3)
                }
            }
        }
    }
}