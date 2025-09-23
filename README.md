# Otter

DB Migration Tool for Kotlin. Inspired by [harmonica](https://github.com/KenjiOhtsuka/harmonica).

[Specs](https://www.notion.so/goodgoodman/Otter-9dd4f8307c27415a8d7d2ccf2dee2768)

## 🚀 주요 업데이트 (2025-09-23)

### 새로운 DB 중립적 아키텍처
- **Exposed 제거 진행**: 순수 JDBC 기반으로 전환 중
- **플러그인 시스템**: DB별 어댑터를 별도 모듈로 분리
- **PostgreSQL 우선 지원**: PostgreSQL 어댑터 구현 완료
- **타입 안전성 강화**: 제네릭과 sealed class 활용

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

## V2 Roadmap (신규 아키텍처)

### Phase 1-2: 인프라 구축 ✅
- [x] DB 중립적 인터페이스 설계
- [x] PostgreSQL 어댑터 구현
- [x] 테스트 인프라 구축 (Testcontainers)

### Phase 3: 엔진 리팩토링 (진행 중)
- [ ] Exposed 완전 제거
- [ ] 새로운 DSL 구현
- [ ] 기존 마이그레이션 호환성 유지

### Phase 4: 추가 DB 지원
- [ ] MySQL 어댑터
- [ ] SQLite 어댑터
- [ ] H2 어댑터
- [ ] MariaDB 어댑터

### Phase 5: 기능 완성
- [x] Down (롤백) - 기본 구현 완료
- [ ] Sequence
- [ ] Seeding
- [ ] 사용자 가이드
- [ ] Gradle Plugin 개선

## V1 Roadmap (Legacy)

- [x] New Syntax
- [x] Type Support
- [x] Alter
- [ ] ~~Sequence~~
- [ ] Create user guide
- [x] Down
- [x] Target
- [ ] ~~SEEDING~~
- [ ] Gradle Plugin - generate
- [ ] Gradle Plugin - migrate
- [ ] Gradle Plugin - rollback
- [ ] Gradle Plugin - check

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

## 새로운 어댑터 기반 사용법 (V2)

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

## Spring Boot 통합 (Legacy - 리팩토링 예정)

### Gradle 설정
```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.goodgoodjm:otter-spring-boot-starter:0.0.23")
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
    - M001_CreateUser.kts
    - M002_CreatePost.kts
    ...
```

## 지원 데이터베이스

| 데이터베이스 | 어댑터 상태 | 버전 |
|-------------|------------|------|
| PostgreSQL | ✅ 완료 | 9.6+ |
| MySQL | 🚧 개발 예정 | 5.7+ |
| MariaDB | 🚧 개발 예정 | 10.2+ |
| SQLite | 🚧 개발 예정 | 3.x |
| H2 | 🚧 개발 예정 | 1.4+ |
| Oracle | 🔮 계획 중 | - |
| SQL Server | 🔮 계획 중 | - |

## 주요 기능

### 타입 안전한 DSL
```kotlin
createTable("users") {
    "id" - SERIAL PRIMARY KEY
    "email" - VARCHAR(255) constraints NOT_NULL and UNIQUE
    "data" - JSONB  // PostgreSQL
    "tags" - ARRAY(VARCHAR(50))  // PostgreSQL
}
```

### 마이그레이션 롤백
```kotlin
override fun down() {
    dropTable("users", cascade = true)
}
```

### ALTER TABLE 지원
```kotlin
alterTable("users") {
    add("phone") - VARCHAR(20)
    dropColumn("old_column")
    renameColumn("name", "full_name")
}
```

### 동시성 제어
- PostgreSQL: Advisory Lock
- MySQL: GET_LOCK
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
git clone https://github.com/GoodGoodJM/Otter.git
cd Otter
./gradlew build
```

### 테스트 실행
```bash
# 전체 테스트
./gradlew test

# PostgreSQL 어댑터 테스트 (Docker 필요)
./gradlew :otter-adapter-postgresql:test
```

## 라이선스
MIT License

## 관련 프로젝트
- [Harmonica](https://github.com/KenjiOhtsuka/harmonica) - 영감을 받은 프로젝트
- [Flyway](https://flywaydb.org/) - 업계 표준 마이그레이션 도구
- [Liquibase](https://www.liquibase.org/) - 엔터프라이즈 마이그레이션 도구