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
    fun testPostgreSQLMigrationWithNewFiles() {
        val schemaName = "otter_integration_test"
        val dbUrl = getSchemaUrl(schemaName)
        cleanSchema(schemaName)

        // M003까지만 실행 (기본 테이블 + 외래키 + 데이터 삽입)
        contextRunner.withPropertyValues(
            "otter.driverClassName=$DB_DRIVER",
            "otter.url=$dbUrl",
            "otter.username=$DB_USER",
            "otter.password=$DB_PASSWORD",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=M003_InsertSampleData.kts",
            "otter.testMode=true"
        ).run { context ->
            // SpringBoot 컨텍스트가 정상적으로 시작되는지 확인
            assertThat(context).getBean(OtterAutoConfiguration::class.java)

            DriverManager.getConnection(dbUrl, DB_USER, DB_PASSWORD).use { conn ->
                conn.createStatement().use { stmt ->
                    // M001에서 생성된 users, posts 테이블 확인
                    val basicTablesCount = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$schemaName' AND table_name IN ('users', 'posts')")
                    basicTablesCount.next()
                    assertThat(basicTablesCount.getInt(1)).isEqualTo(2)

                    // M002에서 생성된 comments, tags, post_tags 테이블 확인
                    val fkTablesCount = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$schemaName' AND table_name IN ('comments', 'tags', 'post_tags')")
                    fkTablesCount.next()
                    assertThat(fkTablesCount.getInt(1)).isEqualTo(3)

                    // M003에서 삽입된 데이터 확인
                    val usersCount = stmt.executeQuery("SELECT COUNT(*) FROM users")
                    usersCount.next()
                    assertThat(usersCount.getInt(1)).isEqualTo(3)

                    val postsCount = stmt.executeQuery("SELECT COUNT(*) FROM posts")
                    postsCount.next()
                    assertThat(postsCount.getInt(1)).isEqualTo(3)

                    // 마이그레이션 기록 확인
                    val migrationRecords = stmt.executeQuery("SELECT COUNT(*) FROM otter_migration WHERE filename IN ('M001_CreateBasicTables.kts', 'M002_CreateForeignKeyTables.kts', 'M003_InsertSampleData.kts')")
                    migrationRecords.next()
                    assertThat(migrationRecords.getInt(1)).isEqualTo(3)
                }
            }
        }
    }
}