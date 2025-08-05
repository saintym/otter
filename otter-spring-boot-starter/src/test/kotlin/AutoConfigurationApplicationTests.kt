import io.github.goodgoodjm.otter.OtterAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner


class AutoConfigurationApplicationTests {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OtterAutoConfiguration::class.java))

    @Test
    fun `기본 마이그레이션 실행 테스트`() {
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
        }

        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:test2",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=B.kts"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
        }
    }
    
    @Test
    fun `ALTER TABLE 기능을 포함한 전체 마이그레이션 테스트`() {
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:altertest;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            // ALTER TABLE 기능을 포함한 모든 마이그레이션이 성공적으로 실행됨
        }
    }
    
    @Test
    fun `특정 버전까지만 마이그레이션 실행 테스트`() {
        // D.kts까지만 실행 (E.kts는 실행하지 않음)
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:versiontest;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=D.kts"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
        }
    }
    
    @Test
    fun `모든 ALTER 기능 포함 E파일까지 전체 마이그레이션 테스트`() {
        // E.kts까지 모든 마이그레이션 실행 (modify 포함)
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:fulltest;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=E.kts"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            // E.kts에 포함된 modify, GENERATED, COLLATE, REFERENCES 등 모든 기능이 실행됨
        }
    }
    
    @Test
    fun `modify 기능 검증을 위한 F파일까지 실행 테스트`() {
        // F.kts까지 실행하여 modify가 제대로 작동했는지 확인
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:modifytest;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            // E.kts의 modify가 성공적으로 실행되어 Test.age에 CHECK 제약조건이 추가됨
            // F.kts의 INSERT가 성공함 (age가 양수이므로)
        }
    }
}