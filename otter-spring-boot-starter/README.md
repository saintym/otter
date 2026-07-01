# Otter Spring Boot Starter

## 개요
Spring Boot 애플리케이션에서 Otter 마이그레이션 도구를 자동으로 설정하고 실행하는 스타터 모듈입니다.

## ✅ 구현 상태 (2025-09-29)
- **자동 설정 완료**: Spring Boot 2.x/3.x 자동 설정 구현
- **데이터베이스 어댑터 통합**: PostgreSQL, MySQL 등 자동 선택
- **Exposed 제거 완료**: 순수 JDBC 기반으로 전환
- **테스트 완료**: Spring Boot 통합 테스트 통과

## 프로젝트 구조

```
otter-spring-boot-starter/src/main/kotlin/
├── OtterAutoConfiguration.kt          # Spring Boot 자동 설정
├── OtterProperties.kt                 # application.yml 속성 매핑
├── SpringOtter.kt                     # Spring 환경 Otter 실행기
└── OtterDatabaseInitializerDetector.kt # DB 초기화 순서 제어
```

## 핵심 클래스

### OtterAutoConfiguration.kt
- **역할**: Spring Boot 자동 설정
- **주요 기능**:
  - `@ConditionalOnProperty`로 조건부 활성화
  - DataSource, JPA 설정 이후 실행 (`@AutoConfigureAfter`)
  - DatabaseAdapter 자동 선택
  - OtterConfig Bean 생성
  - SpringOtter Bean 등록

### OtterProperties.kt
- **역할**: application.yml 설정 매핑
- **설정 프리픽스**: `otter`
- **설정 항목**:
  ```yaml
  otter:
    enabled: true            # Otter 활성화 여부 (기본: true)
    migration-path: ""       # 마이그레이션 파일 경로
    driver-class-name: ""    # JDBC 드라이버
    url: ""                  # DB URL
    username: ""             # DB 사용자
    password: ""             # DB 비밀번호
    show-sql: false         # SQL 로그 출력 (기본: false)
    version: ""             # 목표 버전
    test-mode: false        # 테스트 모드 (락 비활성화)
  ```

### SpringOtter.kt
- **역할**: Spring 라이프사이클과 통합
- **구현**: `InitializingBean` 인터페이스
- **동작**:
  - 애플리케이션 시작 시 자동 실행 (`afterPropertiesSet`)
  - DatabaseAdapter를 사용한 마이그레이션 실행
  - 성공/실패 로그 출력

### OtterDatabaseInitializerDetector.kt
- **역할**: Spring Boot DB 초기화 감지
- **구현**: `DatabaseInitializerDetector` 인터페이스
- **목적**: Flyway, Liquibase와 유사한 초기화 순서 보장

## 의존성

### 런타임 의존성
- `otter-core`: 핵심 마이그레이션 엔진
- `spring-boot-starter`: Spring Boot 기본
- `spring-boot-starter-jdbc`: JDBC 지원
- `kotlin-stdlib`: Kotlin 표준 라이브러리
- `kotlin-script-runtime`: 마이그레이션 스크립트 실행

### 선택적 의존성
- `otter-adapter-postgresql`: PostgreSQL 지원
- `otter-adapter-mysql`: MySQL 지원 (예정)
- `otter-adapter-h2`: H2 지원 (예정)

### 제거된 의존성
- ~~Exposed~~ (2025-09-29 완전 제거)

## 동작 방식

1. **애플리케이션 시작**
2. **자동 설정 조건 확인**
   - `otter.enable=true` (기본값)
   - DataSource 존재
3. **DatabaseAdapter 자동 선택**
   - JDBC URL 기반으로 적절한 어댑터 선택
   - 예: `jdbc:postgresql://` → PostgreSQLAdapter
4. **설정 로드**
   - application.yml에서 `otter.*` 속성 읽기
5. **Bean 생성**
   - DatabaseAdapter Bean
   - OtterConfig Bean
   - SpringOtter Bean
6. **마이그레이션 실행**
   - `afterPropertiesSet()`에서 마이그레이션 실행
   - 모든 대기 중인 마이그레이션 적용

## 사용 방법

### 1. 의존성 추가

#### Gradle
```kotlin
dependencies {
    // Spring Boot Starter
    implementation("io.github.goodgoodjm:otter-spring-boot-starter:1.0.0")

    // 데이터베이스 어댑터 (필요한 것만)
    implementation("io.github.goodgoodjm:otter-adapter-postgresql:1.0.0")
    // implementation("io.github.goodgoodjm:otter-adapter-mysql:1.0.0")

    // 마이그레이션 스크립트 실행
    implementation(kotlin("script-runtime"))
}

// Spring Boot JAR 패키징 설정 (중요!)
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    requiresUnpack("**/kotlin-compiler-embeddable-*.jar")
}
```

#### Maven
```xml
<dependency>
    <groupId>io.github.goodgoodjm</groupId>
    <artifactId>otter-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. application.yml 설정

#### 기본 설정
```yaml
otter:
  enabled: true
  migration-path: classpath:migrations
  driver-class-name: ${spring.datasource.driver-class-name}
  url: ${spring.datasource.url}
  username: ${spring.datasource.username}
  password: ${spring.datasource.password}
  show-sql: true
```

#### PostgreSQL 설정
```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/mydb
    username: postgres
    password: postgres

otter:
  enabled: true
  migration-path: classpath:migrations
  driver-class-name: ${spring.datasource.driver-class-name}
  url: ${spring.datasource.url}
  username: ${spring.datasource.username}
  password: ${spring.datasource.password}
  show-sql: true
  test-mode: false
```

#### 프로파일별 설정
```yaml
# application-dev.yml
otter:
  enabled: true
  show-sql: true
  test-mode: true

# application-prod.yml
otter:
  enabled: true
  show-sql: false
  test-mode: false
```

### 3. 마이그레이션 파일 작성

`src/main/resources/migrations/` 폴더에 .kts 파일 추가:

```kotlin
// M001_CreateUsers.kts
import io.github.goodgoodjm.otter.core.Migration

object : Migration() {
    override fun up() {
        createTable("users") {
            "id" - SERIAL PRIMARY KEY
            "email" - VARCHAR(255) constraints NOT_NULL and UNIQUE
            "name" - VARCHAR(100)
            "created_at" - TIMESTAMP DEFAULT "CURRENT_TIMESTAMP"
        }
    }

    override fun down() {
        dropTable("users")
    }
}
```

### 4. 실행 확인

애플리케이션 시작 로그:
```
INFO  i.g.g.o.s.SpringOtter - Starting Otter migration...
INFO  i.g.g.o.c.Otter - Loading migrations from: classpath:migrations
INFO  i.g.g.o.c.Otter - Found 3 migrations
INFO  i.g.g.o.c.Otter - Executing migration: M001_CreateUsers
INFO  i.g.g.o.c.Otter - Executing migration: M002_CreatePosts
INFO  i.g.g.o.c.Otter - Executing migration: M003_AddIndexes
INFO  i.g.g.o.s.SpringOtter - Otter migration completed successfully
```

## 특징

### 장점
- **Zero Configuration**: 자동 설정으로 코드 없이 사용 가능
- **Spring Boot 통합**: Spring 라이프사이클과 완벽 통합
- **유연한 설정**: 프로파일별 설정 지원
- **다양한 DB 지원**: PostgreSQL, MySQL, H2 등
- **테스트 모드**: 개발 환경용 테스트 모드 제공

### 제한사항
- 애플리케이션 시작 시 동기적 실행 (비동기 옵션 예정)
- 마이그레이션 실패 시 애플리케이션 시작 실패
- 다중 DataSource 미지원 (예정)

## 문제 해결

### 마이그레이션 파일을 찾을 수 없음
```
No migrations found in: classpath:migrations
```
→ 마이그레이션 파일이 올바른 위치에 있는지 확인
→ `src/main/resources/migrations/` 폴더 확인

### Kotlin 스크립트 실행 오류
```
Failed to execute migration script
```
→ `kotlin-script-runtime` 의존성 추가 확인
→ BootJar의 `requiresUnpack` 설정 확인

### 데이터베이스 연결 실패
```
Failed to initialize DatabaseAdapter
```
→ `application.yml`의 데이터베이스 설정 확인
→ 데이터베이스 드라이버 의존성 확인

## 테스트

### 통합 테스트 예제
```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTests {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun contextLoads() {
        // 마이그레이션이 실행되었는지 확인
        val tables = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
        )
        assertTrue(tables.any { it["table_name"] == "otter_migration" })
    }
}
```

## 로드맵

### 예정된 기능
- [ ] 비동기 마이그레이션 실행 옵션
- [ ] 다중 DataSource 지원
- [ ] Spring Actuator 통합
- [ ] 마이그레이션 상태 엔드포인트
- [ ] 롤백 명령 지원

## 기여

버그 리포트와 풀 리퀘스트는 GitHub에서 받습니다:
https://github.com/saintym/otter

## 라이선스
MIT