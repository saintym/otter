# Otter Core

## 개요
Otter Core는 데이터베이스 마이그레이션 도구의 핵심 엔진입니다. DB 중립적인 인터페이스를 제공하며, 다양한 데이터베이스를 플러그인 방식으로 지원합니다.

## 주요 변경사항 (2025-09-23)
- **Exposed 라이브러리 제거 진행 중**: 순수 JDBC 기반으로 전환
- **DB 중립적 아키텍처**: 모든 DB 기능을 인터페이스로 추상화
- **플러그인 시스템**: DB별 어댑터를 별도 모듈로 분리

## 디렉토리 구조

```
otter-core/src/main/kotlin/io/github/goodgoodjm/otter/core/
├── adapter/              # DB 어댑터 인터페이스 (신규)
│   ├── DatabaseAdapter.kt
│   ├── AdapterRegistry.kt
│   ├── connection/       # 연결 관리
│   ├── ddl/             # DDL 생성
│   ├── lock/            # 동시성 제어
│   ├── type/            # 타입 매핑
│   └── model/           # 공통 모델
├── analyzer/            # 마이그레이션 의존성 분석
├── concurrent/          # 동시성 제어 헬퍼
├── dsl/                # 테이블 생성/수정 DSL (리팩토링 예정)
├── exception/          # 커스텀 예외
├── io/                 # 사용자 입력 처리
├── process/            # 마이그레이션 프로세스
├── resourceresolver/   # 마이그레이션 파일 로딩
├── security/           # 스크립트 실행 보안
├── transaction/        # 트랜잭션 관리
├── util/              # 유틸리티
└── validation/        # 마이그레이션 검증
```

## 신규 어댑터 시스템

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

## 공통 모델

### ColumnType (DB 중립적 타입)
```kotlin
sealed class ColumnType {
    object SmallInt : ColumnType()
    object Integer : ColumnType()
    object BigInt : ColumnType()
    data class Varchar(val length: Int) : ColumnType()
    object Text : ColumnType()
    object Boolean : ColumnType()
    object Json : ColumnType()      // 선택적 지원
    object Uuid : ColumnType()      // 선택적 지원
    // ...
}
```

### TableDefinition
```kotlin
data class TableDefinition(
    val name: String,
    val columns: List<ColumnDefinition>,
    val primaryKeys: List<String> = emptyList(),
    val indexes: List<IndexDefinition> = emptyList(),
    val foreignKeys: List<ForeignKeyConstraint> = emptyList()
)
```

## 테스트

### 단위 테스트 실행
```bash
./gradlew :otter-core:test
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

### 임시 의존성 (제거 예정)
- Exposed (현재 DSL에서 사용 중, 리팩토링 진행 중)

## 사용 가능한 DB 어댑터

| 어댑터 | 모듈 | 상태 |
|--------|------|------|
| PostgreSQL | otter-adapter-postgresql | ✅ 구현 완료 |
| MySQL | otter-adapter-mysql | 🚧 예정 |
| SQLite | otter-adapter-sqlite | 🚧 예정 |
| H2 | otter-adapter-h2 | 🚧 예정 |

## 마이그레이션 가이드

### 기존 Exposed 기반 코드
```kotlin
// 이전 방식 (Exposed 사용)
transaction(database) {
    exec(sql)
}
```

### 새로운 어댑터 기반 코드
```kotlin
// 새로운 방식 (어댑터 사용)
adapter.getConnectionProvider().useTransaction { context ->
    context.execute(sql, params)
}
```

## 로드맵

1. **Phase 3** (진행 중): 기존 엔진 리팩토링
   - Exposed 완전 제거
   - 새로운 DSL 구현
   - 마이그레이션 파일 호환성 유지

2. **Phase 4** (예정): 추가 DB 지원
   - MySQL 어댑터
   - SQLite 어댑터
   - H2 어댑터

## 기여 가이드

새로운 DB 어댑터 추가 시:
1. `DatabaseAdapter` 인터페이스 구현
2. 각 Provider 구현 (Connection, DDL, Lock, Type)
3. 단위 테스트 작성
4. 통합 테스트 작성 (Testcontainers 활용)
5. `AdapterRegistry`에 자동 등록 설정

## 라이선스
MIT