import io.github.goodgoodjm.otter.OtterAutoConfiguration
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

    private val dbUser = OtterPostgresContainer.USERNAME
    private val dbPassword = OtterPostgresContainer.PASSWORD
    private val dbDriver = OtterPostgresContainer.DRIVER

    @Test
    @Order(1)
    fun testPostgreSQLMigrationWithNewFiles() {
        val schemaName = "otter_integration_test"
        val dbUrl = OtterPostgresContainer.schemaUrl(schemaName)
        OtterPostgresContainer.resetSchema(schemaName)

        // M003까지만 실행 (기본 테이블 + 외래키 + 데이터 삽입)
        contextRunner.withPropertyValues(
            "otter.driverClassName=$dbDriver",
            "otter.url=$dbUrl",
            "otter.username=$dbUser",
            "otter.password=$dbPassword",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=M003_InsertSampleData.kts",
            "otter.testMode=true"
        ).run { context ->
            // SpringBoot 컨텍스트가 정상적으로 시작되는지 확인
            assertThat(context).getBean(OtterAutoConfiguration::class.java)

            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
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

                    // 마이그레이션 기록 확인 (파일명은 확장자 없이 저장됨)
                    val migrationRecords = stmt.executeQuery("SELECT COUNT(*) FROM otter_migration WHERE filename IN ('M001_CreateBasicTables', 'M002_CreateForeignKeyTables', 'M003_InsertSampleData')")
                    migrationRecords.next()
                    assertThat(migrationRecords.getInt(1)).isEqualTo(3)
                }
            }
        }
    }
}
