import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

/**
 * 통합 테스트에서 공유하는 PostgreSQL Testcontainers 컨테이너.
 *
 * 싱글턴으로 한 번만 기동하고 JVM 종료 시 Testcontainers(Ryuk)가 정리한다.
 * 각 테스트는 고유 스키마로 격리한다.
 */
object OtterPostgresContainer {
    private const val DB_NAME = "otter_test"
    const val USERNAME = "otter"
    const val PASSWORD = "otter"
    const val DRIVER = "org.postgresql.Driver"

    private val container: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
        .withDatabaseName(DB_NAME)
        .withUsername(USERNAME)
        .withPassword(PASSWORD)
        .also { it.start() }

    /** 스키마 파라미터가 없는 기본 JDBC URL */
    val baseUrl: String
        get() = "jdbc:postgresql://${container.host}:${container.getMappedPort(5432)}/$DB_NAME"

    /** 지정 스키마를 기본 스키마로 사용하는 JDBC URL */
    fun schemaUrl(schema: String): String = "$baseUrl?currentSchema=$schema"

    /** 스키마를 삭제 후 재생성하여 완전히 초기화한다 */
    fun resetSchema(schema: String) {
        DriverManager.getConnection(baseUrl, USERNAME, PASSWORD).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
                stmt.execute("CREATE SCHEMA $schema")
            }
        }
    }
}
