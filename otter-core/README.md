# Otter Core

## 개요
Otter Core는 데이터베이스 마이그레이션 도구의 핵심 엔진입니다. DB 중립적인 인터페이스를 제공하며, 다양한 데이터베이스를 플러그인 방식으로 지원합니다.

## 🎉 주요 변경사항 (2025-09-29)
- **✅ Exposed 라이브러리 완전 제거**: 순수 JDBC 기반으로 전환 완료
- **✅ DB 중립적 아키텍처**: 모든 DB 기능을 인터페이스로 추상화 완료
- **✅ 플러그인 시스템**: DB별 어댑터를 별도 모듈로 분리 완료
- **✅ 테스트 성공률 98.7%**: 77개 중 76개 테스트 통과

## 디렉토리 구조

```
otter-core/src/main/kotlin/io/github/goodgoodjm/otter/core/
├── adapter/              # DB 어댑터 인터페이스
│   ├── DatabaseAdapter.kt
│   ├── AdapterRegistry.kt
│   ├── connection/       # 연결 관리
│   ├── ddl/             # DDL 생성
│   ├── lock/            # 동시성 제어
│   ├── type/            # 타입 매핑
│   └── model/           # 공통 모델
├── analyzer/            # 마이그레이션 의존성 분석
├── concurrent/          # 동시성 제어 헬퍼
├── dsl/                # 테이블 생성/수정 DSL
├── exception/          # 커스텀 예외
├── io/                 # 사용자 입력 처리
├── migration/          # MigrationContext (의존성 주입)
├── process/            # 마이그레이션 프로세스
├── resourceresolver/   # 마이그레이션 파일 로딩
├── security/           # 스크립트 실행 보안
├── transaction/        # 트랜잭션 관리
├── util/              # 유틸리티
└── validation/        # 마이그레이션 검증
```

## 어댑터 시스템

### 핵심 인터페이스

#### DatabaseAdapter
```kotlin
interface DatabaseAdapter {
    val name: String
    val version: String

    fun getConnectionProvider(): ConnectionProvider
    fun getDDLProvider(): DDLProvider
    fun getLockProvider(): LockProvider
    fun getTypeMapper(): TypeMapper
    fun getMigrationTracker(): MigrationTracker
    fun getCapabilities(): DatabaseCapabilities

    fun initialize(config: DatabaseConfig)
    fun validate(): ValidationResult
    fun close()
}
```

#### 주요 컴포넌트
- **ConnectionProvider**: DB 연결 및 트랜잭션 관리
- **DDLProvider**: CREATE TABLE, ALTER TABLE 등 DDL 생성
- **LockProvider**: 마이그레이션 동시 실행 방지
- **TypeMapper**: DB 중립적 타입을 DB별 SQL 타입으로 변환
- **MigrationTracker**: 마이그레이션 이력 추적

### 어댑터 등록 및 사용

```kotlin
// 어댑터 등록
AdapterRegistry.register(PostgreSQLAdapter())

// 어댑터 조회
val adapter = AdapterRegistry.get("PostgreSQL")
// 또는 JDBC URL로 자동 선택
val adapter = AdapterRegistry.getByUrl("jdbc:postgresql://...")

// 어댑터 사용
adapter.initialize(config)
val ddl = adapter.getDDLProvider()
val sql = ddl.createTable(tableDefinition)
```

## MigrationContext를 통한 의존성 주입

```kotlin
// 마이그레이션 실행 시 자동으로 설정됨
MigrationContext.setAdapter(adapter)

// DSL 내부에서 자동으로 어댑터 사용
createTable("users") {
    "id" - INT constraints PRIMARY and AUTO_INCREMENT
    "email" - VARCHAR(255) constraints NOT_NULL and UNIQUE
}
// 내부적으로 MigrationContext.getAdapter()를 통해 DDLProvider 호출
```

## 공통 모델

### ColumnType (DB 중립적 타입)
```kotlin
sealed class ColumnType {
    object SmallInt : ColumnType()
    object Integer : ColumnType()
    object BigInt : ColumnType()
    data class Varchar(val length: Int) : ColumnType()
    data class Char(val length: Int) : ColumnType()
    object Text : ColumnType()
    object Boolean : ColumnType()
    object Date : ColumnType()
    object Time : ColumnType()
    object Timestamp : ColumnType()
    data class Decimal(val precision: Int, val scale: Int) : ColumnType()
    object Float : ColumnType()
    object Double : ColumnType()
    object Json : ColumnType()      // 선택적 지원
    object Uuid : ColumnType()      // 선택적 지원
    object Blob : ColumnType()
}
```

### TableDefinition
```kotlin
data class TableDefinition(
    val name: String,
    val columns: List<ColumnDefinition>,
    val primaryKeys: List<String> = emptyList(),
    val indexes: List<IndexDefinition> = emptyList(),
    val foreignKeys: List<ForeignKeyConstraint> = emptyList(),
    val uniqueConstraints: List<UniqueConstraint> = emptyList(),
    val checkConstraints: List<CheckConstraint> = emptyList(),
    val comment: String? = null,
    val schema: String? = null
)
```

## 타입 시스템

### 전역 타입 변수 (getter 사용 중요!)
```kotlin
// 올바른 사용법 - getter를 통해 매번 새 인스턴스 생성
val INT get() = Type.INT
val VARCHAR get() = Type.VARCHAR
val TEXT get() = Type.TEXT
val BOOLEAN get() = Type.BOOLEAN
// ... 기타 타입들

// 잘못된 사용법 (싱글톤 버그 발생)
// val INT = Type.INT  // 모든 컬럼이 같은 인스턴스 공유!
```

## 테스트

### 단위 테스트 실행
```bash
./gradlew :otter-core:test
```

### 테스트 결과 (2025-09-29)
```
총 테스트: 77
성공: 76 (98.7%)
실패: 1 (LockProvider 동시성 테스트)

✅ AlterTableTests: 8/8 (100%)
✅ ConstraintTests: 10/10 (100%)
✅ CreateTableContextTests: 1/1 (100%)
✅ DownMigrationTests: 7/7 (100%)
✅ MigrationTests: 2/2 (100%)
✅ DependencyAnalyzerTests: 6/6 (100%)
✅ AdapterRegistryTest: 8/8 (100%)
✅ DatabaseAdapterTest: 6/6 (100%)
⚠️ LockProviderTest: 7/8 (87.5%)
✅ TypeMapperTest: 7/7 (100%)
✅ UserInputHandlerTests: 3/3 (100%)
✅ SecureMigrationScriptEngineTests: 10/10 (100%)
✅ DebugTest: 1/1 (100%)
```

### 테스트 구조
- `adapter/` - 인터페이스 계약 테스트
- `adapter/MockProviders.kt` - Mock 구현체
- 각 인터페이스별 테스트 케이스

## 의존성

### 필수 의존성
- Kotlin 1.6+
- SLF4J (로깅)
- HikariCP (커넥션 풀)
- kotlinx.coroutines (비동기 지원)

### 제거된 의존성
- ~~Exposed~~ (2025-09-29 완전 제거)

## 사용 가능한 DB 어댑터

| 어댑터 | 모듈 | 상태 |
|--------|------|------|
| PostgreSQL | otter-adapter-postgresql | ✅ 구현 완료 |
| MySQL | otter-adapter-mysql | 🚧 예정 |
| SQLite | otter-adapter-sqlite | 🚧 예정 |
| H2 | otter-adapter-h2 | ✅ 테스트용 구현 완료 |

## 마이그레이션 가이드

### 기존 Exposed 기반 코드 (더 이상 사용 불가)
```kotlin
// 이전 방식 (Exposed 사용) - 제거됨
// transaction(database) {
//     exec(sql)
// }
```

### 새로운 어댑터 기반 코드
```kotlin
// 현재 방식 (어댑터 사용)
adapter.getConnectionProvider().useTransaction { context ->
    context.execute(sql, params)
}

// 또는 DSL 사용 (권장)
createTable("users") {
    "id" - SERIAL PRIMARY KEY
    "email" - VARCHAR(255) constraints NOT_NULL and UNIQUE
}
```

## 보안 기능

### SQL 인젝션 방지
- `UserInputValidator`를 통한 모든 사용자 입력 검증
- Prepared Statement 사용 권장

### 스크립트 실행 보안
- `SecureMigrationScriptEngine`을 통한 안전한 스크립트 실행
- 파일 시스템 접근 제한
- 네트워크 접근 제한

## 의존성 분석

### DependencyAnalyzer
- 외래 키 관계 분석
- 순환 의존성 감지
- 안전한 실행 순서 결정
- 롤백 시 역순 처리

## 기여 가이드

새로운 DB 어댑터 추가 시:
1. `DatabaseAdapter` 인터페이스 구현
2. 각 Provider 구현 (Connection, DDL, Lock, Type, MigrationTracker)
3. 단위 테스트 작성
4. 통합 테스트 작성 (Testcontainers 활용)
5. `AdapterRegistry`에 자동 등록 설정

## 라이선스
MIT