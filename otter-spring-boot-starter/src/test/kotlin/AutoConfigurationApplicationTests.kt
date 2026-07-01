import io.github.goodgoodjm.otter.OtterAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner


class AutoConfigurationApplicationTests {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OtterAutoConfiguration::class.java))

    @Test
    fun `01_기본 테이블 생성 테스트`() {
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:test_basic;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=M001_CreateBasicTables.kts"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            // users, posts 테이블이 생성되어야 함
        }
    }

    @Test
    fun `02_외래키 테이블 생성 테스트`() {
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:test_fk;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=M002_CreateForeignKeyTables.kts"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            // comments, tags, post_tags 테이블이 생성되어야 함
        }
    }
    
    @Test
    fun `03_샘플 데이터 삽입 테스트`() {
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:test_data;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=M003_InsertSampleData.kts"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            // 샘플 데이터가 정상적으로 삽입되어야 함
        }
    }

    @Test
    fun `04_ALTER TABLE ADD 컬럼 테스트`() {
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:test_alter_add;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=M004_AlterTableAddColumns.kts"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            // ALTER TABLE ADD COLUMN이 정상 작동해야 함
            // users: phone, bio, is_active, last_login 추가
            // posts: view_count, updated_at, category 추가
        }
    }

    @Test
    fun `05_ALTER TABLE MODIFY 컬럼 테스트`() {
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:test_alter_modify;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=M005_AlterTableModifyColumns.kts"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            // ALTER TABLE MODIFY COLUMN이 정상 작동해야 함
            // users.email: VARCHAR(500) + UNIQUE
            // users.age: BIGINT + CHECK constraint
            // posts.title: VARCHAR(500)
        }
    }

    @Test
    fun `06_복잡한 데이터 타입 테스트`() {
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:test_complex;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true",
            "otter.version=M006_CreateComplexTypes.kts"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            // DECIMAL, DATE, TIMESTAMP 등 복잡한 타입이 정상 작동해야 함
        }
    }

    @Test
    fun `07_전체 마이그레이션 통합 테스트`() {
        contextRunner.withPropertyValues(
            "otter.driverClassName=org.h2.Driver",
            "otter.url=jdbc:h2:mem:test_full;DB_CLOSE_DELAY=-1",
            "otter.username=root",
            "otter.password=",
            "otter.migrationPath=migrations",
            "otter.showSql=true"
        ).run { context ->
            assertThat(context).getBean(OtterAutoConfiguration::class.java)
            // 모든 마이그레이션(M001~M007)이 순차적으로 성공해야 함
        }
    }
}