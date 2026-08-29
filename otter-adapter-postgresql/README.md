# Otter PostgreSQL Adapter

## 개요
Otter 마이그레이션 도구를 위한 PostgreSQL 데이터베이스 어댑터입니다. PostgreSQL 9.6 이상을 지원하며, 네이티브 기능을 최대한 활용합니다.

## ✅ 구현 상태 (2025-09-29)
- **완전한 DDL 지원**: CREATE TABLE, ALTER TABLE, DROP TABLE, INDEX 등 모든 DDL 작업 구현
- **트랜잭션 관리**: HikariCP를 통한 커넥션 풀링 및 트랜잭션 관리
- **Advisory Lock**: PostgreSQL 네이티브 Advisory Lock 구현
- **타입 매핑**: 모든 PostgreSQL 타입 지원
- **테스트 완료**: 단위 테스트 및 통합 테스트 통과

## 주요 기능

### PostgreSQL 네이티브 기능 지원
- **Advisory Lock**: 동시 마이그레이션 실행 방지
- **SERIAL 타입**: AUTO_INCREMENT 자동 변환
- **JSONB**: 바이너리 JSON 데이터 타입
- **UUID**: 네이티브 UUID 타입
- **배열**: 모든 타입의 배열 지원
- **COMMENT**: 테이블 및 컬럼 주석

### 타입 매핑

| Otter 타입 | PostgreSQL 타입 | 비고 |
|-----------|----------------|------|
| SmallInt | SMALLINT | |
| Integer | INTEGER | |
| BigInt | BIGINT | |
| Integer + AUTO_INCREMENT | SERIAL | 자동 변환 |
| BigInt + AUTO_INCREMENT | BIGSERIAL | 자동 변환 |
| Varchar(n) | VARCHAR(n) | |
| Char(n) | CHARACTER(n) | |
| Text | TEXT | |
| Boolean | BOOLEAN | |
| Date | DATE | |
| Time | TIME | |
| Timestamp | TIMESTAMP | |
| Decimal(p,s) | DECIMAL(p,s) | |
| Float | REAL | |
| Double | DOUBLE PRECISION | |
| Json | JSONB | PostgreSQL 권장 |
| Uuid | UUID | 네이티브 지원 |
| Blob | BYTEA | |

## 설치

### Gradle
```kotlin
dependencies {
    implementation("io.github.goodgoodjm:otter-core:1.0.0")
    implementation("io.github.goodgoodjm:otter-adapter-postgresql:1.0.0")
    implementation("org.postgresql:postgresql:42.5.1")
}
```

### Maven
```xml
<dependency>
    <groupId>io.github.goodgoodjm</groupId>
    <artifactId>otter-adapter-postgresql</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 사용법

### 어댑터 초기화
```kotlin
import io.github.goodgoodjm.otter.adapter.postgresql.PostgreSQLAdapter
import io.github.goodgoodjm.otter.core.adapter.DatabaseConfig

val config = DatabaseConfig(
    url = "jdbc:postgresql://localhost:5432/mydb",
    username = "user",
    password = "password",
    connectionPoolSize = 10
)

val adapter = PostgreSQLAdapter()
adapter.initialize(config)

// 연결 검증
val result = adapter.validate()
when (result) {
    is ValidationResult.Success -> println("Connected to PostgreSQL")
    is ValidationResult.Warning -> println("Warning: ${result.message}")
    is ValidationResult.Error -> throw Exception(result.message)
}
```

### DDL 생성
```kotlin
val ddlProvider = adapter.getDDLProvider()

// 테이블 생성
val table = TableDefinition(
    name = "users",
    columns = listOf(
        ColumnDefinition(
            name = "id",
            type = ColumnType.Integer,
            modifiers = setOf(
                ColumnModifier.PRIMARY_KEY,
                ColumnModifier.AUTO_INCREMENT  // SERIAL로 변환됨
            )
        ),
        ColumnDefinition(
            name = "email",
            type = ColumnType.Varchar(255),
            modifiers = setOf(ColumnModifier.UNIQUE, ColumnModifier.NOT_NULL)
        ),
        ColumnDefinition(
            name = "data",
            type = ColumnType.Json  // JSONB 타입
        ),
        ColumnDefinition(
            name = "created_at",
            type = ColumnType.Timestamp,
            defaultValue = "CURRENT_TIMESTAMP"
        )
    ),
    comment = "사용자 테이블"
)

val statements = ddlProvider.createTable(table)
// 생성된 SQL:
// CREATE TABLE IF NOT EXISTS users (
//     id SERIAL NOT NULL,
//     email VARCHAR(255) NOT NULL UNIQUE,
//     data JSONB,
//     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
//     CONSTRAINT users_pkey PRIMARY KEY (id)
// );
// COMMENT ON TABLE users IS '사용자 테이블';
```

### 트랜잭션 관리
```kotlin
val connectionProvider = adapter.getConnectionProvider()

connectionProvider.useTransaction { context ->
    // DDL 실행
    statements.forEach { sql ->
        context.execute(sql)
    }

    // 데이터 삽입
    context.execute(
        "INSERT INTO users (email, data) VALUES (?, ?::jsonb)",
        listOf(
            "user@example.com",
            """{"name": "John", "age": 30}"""
        )
    )

    // 쿼리
    val users = context.query(
        "SELECT * FROM users WHERE email = ?",
        listOf("user@example.com")
    ) { rs ->
        User(
            id = rs.getInt("id"),
            email = rs.getString("email"),
            data = rs.getString("data")
        )
    }

    // Savepoint 사용
    val savepoint = context.setSavepoint("before_update")
    try {
        context.execute("UPDATE users SET email = ? WHERE id = ?",
                       listOf("new@example.com", 1))
    } catch (e: Exception) {
        context.rollbackToSavepoint(savepoint)
    }
}
```

### Advisory Lock 사용
```kotlin
val lockProvider = adapter.getLockProvider()

// PostgreSQL Advisory Lock 사용
if (lockProvider.acquireLock("migration_lock", Duration.ofSeconds(60))) {
    try {
        // 마이그레이션 실행
        performMigration()
    } finally {
        lockProvider.releaseLock("migration_lock")
    }
} else {
    println("다른 프로세스가 마이그레이션을 실행 중입니다")
}

// 락 정보 조회
val lockInfo = lockProvider.getLockInfo("migration_lock")
lockInfo?.let {
    println("Lock held by: ${it.hostname} (PID: ${it.processId})")
}
```

### ALTER TABLE 작업
```kotlin
// 컬럼 추가
val addColumnSql = ddlProvider.alterTableAddColumn(
    "users",
    ColumnDefinition("age", ColumnType.Integer)
)

// 컬럼 수정
val modifyColumnSql = ddlProvider.alterTableModifyColumn(
    "users",
    ColumnDefinition("email", ColumnType.Varchar(500),
                     setOf(ColumnModifier.NOT_NULL))
)

// 컬럼 삭제
val dropColumnSql = ddlProvider.alterTableDropColumn(
    "users", "age", cascade = false
)

// 컬럼 이름 변경
val renameColumnSql = ddlProvider.alterTableRenameColumn(
    "users", "email", "email_address"
)

// Foreign Key 추가
val foreignKey = ForeignKeyConstraint(
    name = "fk_user_posts",
    tableName = "posts",
    columns = listOf("user_id"),
    referencedTable = "users",
    referencedColumns = listOf("id"),
    onDelete = ReferentialAction.CASCADE,
    onUpdate = ReferentialAction.RESTRICT
)
val addForeignKeySql = ddlProvider.alterTableAddForeignKey("posts", foreignKey)
```

## 고급 기능

### 부분 인덱스 (Partial Index)
```kotlin
val index = IndexDefinition(
    name = "idx_active_users",
    tableName = "users",
    columns = listOf("id"),
    where = "is_active = true"  // PostgreSQL 부분 인덱스
)
val sql = ddlProvider.createIndex("users", index)
// CREATE INDEX idx_active_users ON users (id) WHERE is_active = true
```

### GIN/GiST 인덱스
```kotlin
val jsonIndex = IndexDefinition(
    name = "idx_user_data",
    tableName = "users",
    columns = listOf("data"),
    type = IndexType.GIN  // JSONB 인덱싱용
)
```

## 연결 풀 설정

HikariCP를 사용한 연결 풀 최적화:
```kotlin
val config = DatabaseConfig(
    url = "jdbc:postgresql://localhost:5432/mydb",
    username = "user",
    password = "password",
    connectionPoolSize = 20,     // 최대 연결 수
    connectionTimeout = 30000    // 타임아웃 (ms)
)
```

## 테스트

### 단위 테스트
```bash
./gradlew :otter-adapter-postgresql:test
```

### 통합 테스트 (Docker 필요)
```bash
# PostgreSQL Docker 컨테이너 실행
docker run --name otter-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=otter_test \
  -p 5433:5432 \
  postgres:15-alpine

# 테스트 실행
./gradlew :otter-adapter-postgresql:test
```

## 요구사항

- PostgreSQL 9.6 이상
- Java 8 이상
- Kotlin 1.6 이상

## 지원되는 PostgreSQL 버전

| 버전 | 지원 상태 | 비고 |
|------|----------|------|
| 9.6 | ⚠️ 지원 (경고) | 최소 버전 |
| 10.x | ✅ 완전 지원 | |
| 11.x | ✅ 완전 지원 | |
| 12.x | ✅ 완전 지원 | |
| 13.x | ✅ 완전 지원 | |
| 14.x | ✅ 완전 지원 | |
| 15.x | ✅ 완전 지원 | 테스트 완료 |
| 16.x | ✅ 완전 지원 | |

## 제한사항

- Advisory Lock은 세션 기반으로 작동하므로 연결이 끊어지면 자동 해제됩니다
- JSONB 인덱싱은 GIN 인덱스 타입을 사용해야 효율적입니다

## 문제 해결

### 연결 오류
```
Failed to connect to PostgreSQL: FATAL: password authentication failed
```
→ 사용자 이름과 비밀번호를 확인하세요

### 버전 경고
```
Warning: PostgreSQL 9.6 is supported but consider upgrading to version 10 or later
```
→ PostgreSQL 10 이상으로 업그레이드를 권장합니다

### Advisory Lock 획득 실패
```
Failed to acquire migration lock
```
→ 다른 프로세스가 마이그레이션을 실행 중입니다. 잠시 후 다시 시도하세요.

## 기여

버그 리포트와 풀 리퀘스트는 GitHub에서 받습니다:
https://github.com/saintym/otter

## 라이선스
MIT