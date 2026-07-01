import io.github.goodgoodjm.otter.OtterAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Spring Boot 자동설정이 실제 PostgreSQL(Testcontainers)에서 마이그레이션을
 * 정상 수행하는지 검증하는 스모크 테스트.
 *
 * 컨텍스트가 정상 기동하면 해당 버전까지의 마이그레이션이 성공한 것이다.
 * 각 테스트는 고유 스키마로 격리한다.
 */
class AutoConfigurationApplicationTests {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OtterAutoConfiguration::class.java))

    private fun runUpTo(schema: String, version: String?): ApplicationContextRunner {
        OtterPostgresContainer.resetSchema(schema)
        val props = mutableListOf(
            "otter.driverClassName=${OtterPostgresContainer.DRIVER}",
            "otter.url=${OtterPostgresContainer.schemaUrl(schema)}",
            "otter.username=${OtterPostgresContainer.USERNAME}",
            "otter.password=${OtterPostgresContainer.PASSWORD}",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.testMode=true"
        )
        if (version != null) props.add("otter.version=$version")
        return contextRunner.withPropertyValues(*props.toTypedArray())
    }

    @Test
    fun `01_기본 테이블 생성 테스트`() {
        runUpTo("ac_basic", "M001_CreateBasicTables.kts").run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
        }
    }

    @Test
    fun `02_외래키 테이블 생성 테스트`() {
        runUpTo("ac_fk", "M002_CreateForeignKeyTables.kts").run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
        }
    }

    @Test
    fun `03_샘플 데이터 삽입 테스트`() {
        runUpTo("ac_data", "M003_InsertSampleData.kts").run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
        }
    }

    @Test
    fun `04_ALTER TABLE ADD 컬럼 테스트`() {
        runUpTo("ac_alter_add", "M004_AlterTableAddColumns.kts").run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
        }
    }

    @Test
    fun `05_ALTER TABLE MODIFY 컬럼 테스트`() {
        runUpTo("ac_alter_modify", "M005_AlterTableModifyColumns.kts").run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
        }
    }

    @Test
    fun `06_복잡한 데이터 타입 테스트`() {
        runUpTo("ac_complex", "M006_CreateComplexTypes.kts").run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
        }
    }

    @Test
    fun `07_전체 마이그레이션 통합 테스트`() {
        runUpTo("ac_full", null).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
        }
    }
}
