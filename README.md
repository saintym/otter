# Otter

DB Migration Tool for Kotlin - A database-agnostic migration framework.

[Specs](https://www.notion.so/goodgoodman/Otter-9dd4f8307c27415a8d7d2ccf2dee2768)

## 🎉 Major Milestone: Exposed ORM 완전 제거 완료! (2025-09-29)

### 🚀 DatabaseAdapter 아키텍처 - 100% 데이터베이스 독립적
- **✅ Exposed 의존성 완전 제거**: Exposed ORM 프레임워크 완전 제거
- **✅ 순수 JDBC 구현**: HikariCP를 통한 커넥션 풀링과 직접적인 데이터베이스 접근
- **✅ DatabaseAdapter 패턴**: 데이터베이스 엔진 간 깔끔한 분리
- **✅ 100% 테스트 성공률**: otter-core 90개 테스트 전체 통과
- **✅ 완벽한 DSL 호환성**: 기존 마이그레이션 스크립트 변경 없이 작동

### 🏗️ 새로운 아키텍처 구성요소
- **DatabaseAdapter Interface**: 데이터베이스 독립적 작업 인터페이스
- **DDLProvider**: 각 데이터베이스별 SQL DDL 생성
- **ConnectionProvider**: 트랜잭션 및 연결 관리
- **MigrationTracker**: 마이그레이션 이력 추적
- **TypeMapper**: 데이터베이스별 타입 매핑
- **LockProvider**: 동시 마이그레이션 방지

## 프로젝트 구조

```
otter/
├── otter-core/                    # 핵심 엔진 (DB 중립적)
├── otter-adapter-postgresql/      # PostgreSQL 어댑터 (✅ 완료)
├── otter-adapter-mysql/           # MySQL 어댑터 (🚧 예정)
├── otter-adapter-sqlite/          # SQLite 어댑터 (🚧 예정)
├── otter-spring-boot-starter/     # Spring Boot 통합
└── otter-plugin/                  # Gradle 플러그인 (🚧 개발 중)
```

## 🎯 현재 상태

### ✅ 완료된 기능
- **데이터베이스 어댑터**: PostgreSQL, H2 (테스트용)
- **마이그레이션 작업**: CREATE TABLE, ALTER TABLE, DROP TABLE
- **타입 시스템**: Exposed 없이 완전한 타입 매핑
- **트랜잭션 관리**: HikariCP를 통한 커넥션 풀링
- **롤백 지원**: 의존성 분석을 통한 완전한 down() 마이그레이션
- **보안**: SQL 인젝션 방지, 스크립트 검증
- **동시성**: 안전한 병렬 실행을 위한 락 메커니즘

### 📊 테스트 커버리지
```
총 테스트: 90
성공: 90 (100%)
실패: 0

✅ AdapterRegistryTest: 8/8 (100%)
✅ AlterTableTests: 8/8 (100%)
✅ ConstraintTests: 10/10 (100%)
✅ CreateTableContextTests: 5/5 (100%)
✅ DatabaseAdapterTest: 6/6 (100%)
✅ DependencyAnalyzerTests: 6/6 (100%)
✅ DownMigrationTests: 7/7 (100%)
✅ ImprovedLockProviderTest: 5/5 (100%)
✅ LockProviderTest: 8/8 (100%)
✅ MigrationTests: 2/2 (100%)
✅ NewSystemIntegrationTest: 5/5 (100%)
✅ SecureMigrationScriptEngineTests: 10/10 (100%)
✅ TypeMapperTest: 7/7 (100%)
✅ UserInputHandlerTests: 3/3 (100%)
```

## 빠른 시작

### 의존성 추가

#### Gradle (Kotlin DSL)
```kotlin
dependencies {
    // Core
    implementation("io.github.goodgoodjm:otter-core:1.0.0")

    // DB 어댑터 (필요한 것만 선택)
    implementation("io.github.goodgoodjm:otter-adapter-postgresql:1.0.0")
    // implementation("io.github.goodgoodjm:otter-adapter-mysql:1.0.0")

    // Spring Boot 사용 시
    implementation("io.github.goodgoodjm:otter-spring-boot-starter:1.0.0")

    // 마이그레이션 스크립트 실행용
    implementation(kotlin("script-runtime"))
}
```

### Spring Boot 설정

```yaml
# application.yml
otter:
  enabled: true
  migration-path: classpath:migrations
  driver-class-name: org.postgresql.Driver
  url: jdbc:postgresql://localhost:5432/mydb
  username: user
  password: password
  show-sql: true
  test-mode: false
```

### 마이그레이션 파일 작성

```kotlin
// src/main/resources/migrations/001_create_users.kts
import io.github.goodgoodjm.otter.core.Migration

object : Migration() {
    override fun up() {
        createTable("users") {
            "id" - SERIAL PRIMARY KEY
            "email" - VARCHAR(255) constraints NOT_NULL and UNIQUE
            "name" - VARCHAR(100)
            "data" - JSONB  // PostgreSQL 특화
            "created_at" - TIMESTAMP DEFAULT "CURRENT_TIMESTAMP"
        }
    }

    override fun down() {
        dropTable("users")
    }
}
```

## 새로운 어댑터 기반 사용법

```kotlin
import io.github.goodgoodjm.otter.adapter.postgresql.PostgreSQLAdapter
import io.github.goodgoodjm.otter.core.adapter.DatabaseConfig

// 어댑터 초기화
val adapter = PostgreSQLAdapter()
adapter.initialize(DatabaseConfig(
    url = "jdbc:postgresql://localhost:5432/mydb",
    username = "user",
    password = "password"
))

// DDL 생성
val ddlProvider = adapter.getDDLProvider()
val table = TableDefinition(
    name = "users",
    columns = listOf(
        ColumnDefinition("id", ColumnType.Integer,
                        setOf(ColumnModifier.PRIMARY_KEY, ColumnModifier.AUTO_INCREMENT)),
        ColumnDefinition("email", ColumnType.Varchar(255),
                        setOf(ColumnModifier.UNIQUE))
    )
)

// SQL 실행
adapter.getConnectionProvider().useTransaction { context ->
    val statements = ddlProvider.createTable(table)
    statements.forEach { sql ->
        context.execute(sql)
    }
}
```

## 🔄 Exposed에서 마이그레이션 가이드

### 주요 변경사항
기존 Exposed ORM 기반 구현에서 순수 JDBC 기반 DatabaseAdapter 패턴으로 전환되었습니다.

### 코드 변경 필요 없음!
**✨ 좋은 소식: 기존 마이그레이션 스크립트(.kts 파일)는 변경 없이 그대로 작동합니다!**

DSL 인터페이스는 동일하게 유지되므로 기존 마이그레이션 파일 수정이 불필요합니다.

### Gradle 의존성 변경
```kotlin
// 이전 (Exposed 기반) - 더 이상 필요 없음
// dependencies {
//     implementation("org.jetbrains.exposed:exposed-core:0.38.2")
//     implementation("org.jetbrains.exposed:exposed-dao:0.38.2")
//     implementation("org.jetbrains.exposed:exposed-jdbc:0.38.2")
// }

// 현재 (DatabaseAdapter 기반)
dependencies {
    // Core만 필요 (Exposed 의존성 제거됨)
    implementation("io.github.goodgoodjm:otter-core:1.0.0")

    // 사용할 DB 어댑터 추가
    implementation("io.github.goodgoodjm:otter-adapter-postgresql:1.0.0")

    // Coroutines 지원 (비동기 작업용)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}
```

### 성능 및 안정성 개선
- **메모리 사용량 감소**: Exposed ORM 오버헤드 제거
- **시작 시간 단축**: 경량화된 초기화 프로세스
- **더 나은 제어**: 직접적인 JDBC 제어로 세밀한 튜닝 가능
- **확장성 향상**: 새로운 DB 지원 추가가 더 쉬워짐

## Spring Boot 통합

### Gradle 설정
```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.goodgoodjm:otter-spring-boot-starter:1.0.0")
    implementation(kotlin("script-runtime"))
}

// Spring Boot JAR 패키징 설정
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    requiresUnpack("**/kotlin-compiler-embeddable-*.jar")
}
```

### 프로퍼티 설정
```yaml
# application.yml
otter:
  driverClassName: ${spring.datasource.driver-class-name}
  url: ${spring.datasource.url}
  username: ${spring.datasource.username}
  password: ${spring.datasource.password}
  migrationPath: ${MIGRATION_PATH:filesystem:/app/resources/main/migrations}
```

### 마이그레이션 파일 배치
```
resources/
  migrations/
    - M001_CreateBasicTables.kts
    - M002_CreateForeignKeyTables.kts
    - M003_InsertSampleData.kts
    ...
```

## 지원 데이터베이스

| 데이터베이스 | 어댑터 상태 | 버전 |
|-------------|------------|------|
| PostgreSQL | ✅ 완료 | 9.6+ |
| MySQL | 🚧 개발 예정 | 5.7+ |
| MariaDB | 🚧 개발 예정 | 10.2+ |
| SQLite | 🚧 개발 예정 | 3.x |
| H2 | ✅ 테스트용 완료 | 1.4+ |
| Oracle | 🔮 계획 중 | - |
| SQL Server | 🔮 계획 중 | - |

## 주요 기능

### ✅ 타입 안전한 DSL
```kotlin
createTable("users") {
    "id" - INT constraints PRIMARY and AUTO_INCREMENT
    "email" - VARCHAR(255) constraints NOT_NULL and UNIQUE
    "age" - INT
    "created_at" - TIMESTAMP constraints NOT_NULL
    "is_active" - BOOLEAN constraints DEFAULT(true)
}
```

### ✅ 마이그레이션 롤백 (Down)
```kotlin
override fun down() {
    dropTable("users")
    dropTable("posts")
}
```

### ✅ ALTER TABLE 지원
```kotlin
alterTable("users") {
    // ADD - 완벽 지원
    add("phone") - VARCHAR(20)
    add("bio") - TEXT
    add("last_login") - TIMESTAMP

    // DROP - 완벽 지원
    drop("old_column")

    // MODIFY - 구현 완료
    modify("email") - VARCHAR(500) constraints UNIQUE
}
```

### 동시성 제어
- PostgreSQL: Advisory Lock
- MySQL: GET_LOCK (예정)
- 기타: 테이블 기반 락

## 문서

- [Core 모듈 문서](./otter-core/README.md)
- [PostgreSQL 어댑터 문서](./otter-adapter-postgresql/README.md)
- [Spring Boot Starter 문서](./otter-spring-boot-starter/README.md)
- [아키텍처 설계](./DB_AGNOSTIC_ARCHITECTURE_PLAN.md)

## 기여

버그 리포트, 기능 요청, 풀 리퀘스트는 환영합니다!

### 개발 환경 설정
```bash
git clone https://github.com/saintym/otter.git
cd Otter
./gradlew build
```

### 테스트 실행
```bash
# 전체 테스트
./gradlew test

# PostgreSQL 어댑터 테스트 (Docker 필요)
./gradlew :otter-adapter-postgresql:test

# PostgreSQL Docker 컨테이너 실행
docker run --name otter-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=otter_test \
  -p 5433:5432 \
  postgres:15-alpine
```

### 테스트 현황
- ✅ **Core 모듈 테스트**: 90/90 성공 (100%)
- ✅ **PostgreSQL 어댑터 테스트**: 36/36 성공 (100%, Docker/Testcontainers 필요)
- ✅ **Spring Boot Starter 테스트**: 8/8 성공 (100%, Docker/Testcontainers 필요)

## 라이선스
MIT License

## 관련 프로젝트
- [Harmonica](https://github.com/KenjiOhtsuka/harmonica) - 영감을 받은 프로젝트
- [Flyway](https://flywaydb.org/) - 업계 표준 마이그레이션 도구
- [Liquibase](https://www.liquibase.org/) - 엔터프라이즈 마이그레이션 도구