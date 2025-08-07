# Exposed 제거 및 자체 SQL 엔진 구현 계획

## 1. 현재 Exposed 사용 현황

### 1.1 주요 사용 영역
- **데이터베이스 연결**: `Database.connect()`
- **트랜잭션 관리**: `transaction {}` 블록
- **SQL 실행**: `transaction.exec()`
- **테이블 존재 확인**: `Table.exists()`
- **데이터 타입**: `ColumnType` 관련 클래스들
- **마이그레이션 테이블**: `IntIdTable`, `datetime`, `varchar` 등

### 1.2 Exposed 의존성이 있는 파일들
- `Otter.kt`: 데이터베이스 연결 및 트랜잭션 관리
- `MigrationTable`, `LockTable`: Exposed의 Table API 사용
- `Type.kt`: Exposed의 ColumnType 사용
- `CreateTableContext.kt`: SQL 생성 시 Exposed의 타입 시스템 활용

## 2. 제거 계획

### Phase 1: 데이터베이스 연결 추상화 (1주)
```kotlin
interface DatabaseConnection {
    fun execute(sql: String): ResultSet
    fun executeUpdate(sql: String): Int
    fun beginTransaction()
    fun commit()
    fun rollback()
    fun close()
}

class JdbcDatabaseConnection(
    private val connection: Connection
) : DatabaseConnection {
    // JDBC 기반 구현
}
```

### Phase 2: 트랜잭션 관리 구현 (1주)
```kotlin
class TransactionManager {
    fun <T> transaction(block: Transaction.() -> T): T {
        val tx = Transaction(connection)
        return try {
            tx.begin()
            val result = tx.block()
            tx.commit()
            result
        } catch (e: Exception) {
            tx.rollback()
            throw e
        }
    }
}
```

### Phase 3: 타입 시스템 재구현 (2주)
```kotlin
sealed class SqlType {
    abstract fun toSql(dialect: DatabaseDialect): String
    
    object INTEGER : SqlType() {
        override fun toSql(dialect: DatabaseDialect) = when(dialect) {
            DatabaseDialect.MYSQL -> "INT"
            DatabaseDialect.POSTGRESQL -> "INTEGER"
            DatabaseDialect.H2 -> "INT"
        }
    }
    
    data class VARCHAR(val length: Int) : SqlType() {
        override fun toSql(dialect: DatabaseDialect) = "VARCHAR($length)"
    }
    // ... 기타 타입들
}
```

### Phase 4: SQL 빌더 구현 (2주)
```kotlin
class SqlBuilder {
    fun createTable(name: String, columns: List<Column>): String
    fun alterTable(name: String, operations: List<AlterOperation>): String
    fun dropTable(name: String): String
    fun insert(table: String, values: Map<String, Any?>): String
    fun select(table: String, columns: List<String>, where: String? = null): String
    fun update(table: String, values: Map<String, Any?>, where: String): String
    fun delete(table: String, where: String): String
}
```

### Phase 5: 마이그레이션 테이블 재구현 (1주)
```kotlin
class MigrationRepository(private val connection: DatabaseConnection) {
    fun createTables() {
        connection.executeUpdate("""
            CREATE TABLE IF NOT EXISTS otter_migration (
                id SERIAL PRIMARY KEY,
                filename VARCHAR(255) NOT NULL UNIQUE,
                comment VARCHAR(255) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
            )
        """)
    }
    
    fun findLast(): MigrationRecord?
    fun insert(filename: String, comment: String)
    fun delete(filename: String)
}
```

## 3. 데이터베이스 방언(Dialect) 지원

```kotlin
enum class DatabaseDialect {
    MYSQL,
    POSTGRESQL,
    H2,
    ORACLE,
    SQLSERVER
}

interface DialectProvider {
    fun getDialect(url: String): DatabaseDialect
    fun getIdentifierQuote(): String
    fun supportsIfNotExists(): Boolean
    fun supportsReturning(): Boolean
    fun getCurrentTimestamp(): String
}
```

## 4. 마이그레이션 전략

### 4.1 점진적 마이그레이션
1. 새로운 인터페이스를 먼저 구현
2. Exposed를 래핑하여 새 인터페이스 구현
3. 기존 코드를 새 인터페이스 사용하도록 변경
4. 테스트 통과 확인
5. Exposed 래핑 제거하고 자체 구현으로 교체

### 4.2 호환성 유지
- 기존 DSL 문법은 그대로 유지
- 마이그레이션 파일 형식 변경 없음
- 생성되는 SQL은 동일하게 유지

## 5. 테스트 전략

### 5.1 단위 테스트
- 각 컴포넌트별 독립적인 테스트
- Mock을 활용한 격리된 테스트

### 5.2 통합 테스트
- H2, PostgreSQL, MySQL 등 실제 데이터베이스 대상 테스트
- 기존 Exposed 사용 시와 동일한 결과 보장

### 5.3 성능 테스트
- 대량 마이그레이션 처리 성능
- 메모리 사용량 비교

## 6. 예상 이점

1. **의존성 감소**: 외부 라이브러리 의존성 제거
2. **유연성 향상**: Otter 특화 기능 추가 용이
3. **성능 최적화**: 불필요한 기능 제거로 성능 향상
4. **유지보수성**: 전체 코드베이스 직접 관리 가능

## 7. 리스크 및 대응

### 7.1 리스크
- 초기 구현 시 버그 가능성
- 모든 데이터베이스 방언 지원의 복잡성
- 기존 사용자의 마이그레이션 부담

### 7.2 대응 방안
- 충분한 테스트 커버리지 확보
- 단계별 릴리즈 (베타 → RC → 정식)
- 마이그레이션 가이드 및 도구 제공
- 일정 기간 Exposed 버전과 병행 지원

## 8. 일정

- **Phase 1-2**: 2주 (데이터베이스 연결 및 트랜잭션)
- **Phase 3**: 2주 (타입 시스템)
- **Phase 4**: 2주 (SQL 빌더)
- **Phase 5**: 1주 (마이그레이션 테이블)
- **테스트 및 안정화**: 2주
- **문서화**: 1주

**총 예상 기간**: 10주