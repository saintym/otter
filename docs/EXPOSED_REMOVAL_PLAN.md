# Exposed 제거 작업 계획 및 진행 상황

## 개요
otter-core에서 Exposed 의존성을 완전히 제거하고 DB 중립적 어댑터 패턴으로 전환하는 프로젝트입니다.

## 작업 원칙
- **DB 중립성**: core 모듈은 특정 DB에 의존하지 않음
- **어댑터 패턴**: DB별 차이는 어댑터가 처리
- **하위 호환성**: 기존 마이그레이션 스크립트 동작 보장
- **확장 가능성**: 다양한 Primary Key 타입 및 복합키 지원

## 진행 상황

### ✅ Phase 1: OtterV2 엔진 구축 (완료)

#### 구현 완료 항목
1. **OtterV2.kt** - 새로운 메인 엔진
   - Exposed 없이 DatabaseAdapter 기반으로 작동
   - 마이그레이션 실행, 롤백, 상태 확인 기능
   - ThreadLocal 기반 컨텍스트 관리

2. **AdapterFactory.kt** - 어댑터 자동 선택
   - JDBC URL 파싱으로 DB 타입 자동 감지
   - ServiceLoader 지원으로 플러그인 방식 확장 가능
   - PostgreSQL, MySQL, H2, SQLite 등 지원 준비

3. **MigrationTracker.kt** - 마이그레이션 추적
   - 순수 SQL로 otter_migration 테이블 관리
   - DB별 차이점 처리 (SERIAL vs AUTO_INCREMENT)
   - information_schema 활용한 테이블 존재 확인

4. **MigrationExecutor.kt** - 스크립트 실행
   - 보안 강화된 스크립트 엔진 사용
   - V2 컨텍스트로 어댑터 주입
   - up/down 메서드 실행 관리

5. **PrimaryKeyStrategy.kt** - 다양한 PK 지원
   - INT, BIGINT, UUID, VARCHAR 등 다양한 타입
   - AUTO_INCREMENT, SERIAL, gen_random_uuid() 등 생성 전략
   - 복합 Primary Key 지원
   - DB별 전략 구현 (PostgreSQL, MySQL, SQLite)

### ✅ Phase 2: DSL 어댑터화 (완료)

#### 구현 완료 항목
1. **TableBuilder.kt** - 테이블 정의 인터페이스
   - 컬럼, Primary Key, Foreign Key, Index, Check 제약조건 지원
   - 복합 Primary Key 지원
   - DB 중립적 테이블 정의 (TableDefinition)

2. **DSLSupport.kt** - 기존 DSL 문법 지원
   - INT, BIGINT, UUID, VARCHAR, TEXT 등 타입 정의
   - SERIAL, BIGSERIAL 자동 변환
   - Constraint 상수 제공

3. **MigrationV2.kt** - V2 마이그레이션 베이스 클래스
   - 기존 Migration 클래스 확장
   - createTable, alterTable, dropTable 메서드 재구현
   - Raw SQL 실행, 데이터 CRUD 메서드 제공

4. **CreateTableContextV2.kt** - CREATE TABLE DSL
   - 기존 DSL 문법 완벽 호환 ("column" - TYPE)
   - constraints, and 키워드 지원
   - 복합키, 인덱스, Foreign Key 지원

5. **AlterTableContextV2.kt** - ALTER TABLE DSL
   - ADD, MODIFY, DROP, RENAME 컬럼 지원
   - Primary Key, Foreign Key, Index 추가/삭제
   - CASCADE 옵션 지원

### 🚧 Phase 3: 기존 코드 마이그레이션 (계획 중)

#### 예정된 작업
1. **호환성 레이어 구현**
   ```kotlin
   @Deprecated("Use OtterV2")
   class Otter {
       private val v2: OtterV2
       // 기존 API를 V2로 위임
   }
   ```

2. **Spring Boot Starter 업데이트**
   - OtterV2 자동 설정 추가
   - 기존 설정과 호환성 유지

### 🚧 Phase 4: 테스트 및 최적화 (계획 중)

#### 테스트 계획
1. **단위 테스트**
   - 각 어댑터별 DDL 생성 검증
   - PrimaryKeyStrategy 동작 확인
   - Lock 메커니즘 테스트

2. **통합 테스트**
   - 기존 마이그레이션 파일 실행
   - 다양한 DB 버전 호환성
   - 성능 벤치마크

### 🚧 Phase 5: Exposed 의존성 제거 (최종 단계)

#### 마지막 정리 작업
1. build.gradle.kts에서 Exposed 의존성 제거
2. 불필요한 import 정리
3. 문서 업데이트

## 기술적 세부사항

### DB별 Primary Key 매핑

| DB | INT AUTO_INCREMENT | UUID | BIGINT |
|---|---|---|---|
| PostgreSQL | SERIAL | UUID DEFAULT gen_random_uuid() | BIGSERIAL |
| MySQL | INT AUTO_INCREMENT | CHAR(36) DEFAULT (UUID()) | BIGINT AUTO_INCREMENT |
| SQLite | INTEGER AUTOINCREMENT | TEXT | INTEGER |
| H2 | INT AUTO_INCREMENT | UUID | BIGINT AUTO_INCREMENT |

### 복합 Primary Key 예시
```kotlin
createTable("user_roles") {
    "user_id" - UUID
    "role_id" - INT
    "assigned_at" - TIMESTAMP

    primaryKey("user_id", "role_id")
}
```

### Natural Key vs Surrogate Key
```kotlin
// Natural Key (비즈니스 키)
createTable("countries") {
    "iso_code" - VARCHAR(2) PRIMARY KEY
    "name" - VARCHAR(100)
}

// Surrogate Key (인공 키)
createTable("users") {
    "id" - UUID PRIMARY KEY DEFAULT(gen_random_uuid())
    "email" - VARCHAR(255) UNIQUE
}
```

## 리스크 및 대응

### 식별된 리스크
1. **성능 저하 가능성**
   - Exposed의 최적화 기능 상실
   - 대응: PreparedStatement 캐싱, Connection Pool 최적화

2. **복잡한 쿼리 지원**
   - JOIN, 서브쿼리 등 복잡한 SQL
   - 대응: rawSql 메서드 제공

3. **기존 사용자 영향**
   - Breaking Change 가능성
   - 대응: Deprecated 어노테이션 + 호환성 레이어

## 성공 지표
- [ ] Exposed 의존성 0개
- [ ] PostgreSQL, MySQL, H2, SQLite 모두 지원
- [ ] 기존 마이그레이션 100% 호환
- [ ] 성능 저하 ±10% 이내
- [ ] 테스트 커버리지 90% 이상

## 참고 자료
- [DatabaseAdapter 인터페이스](../otter-core/src/main/kotlin/io/github/goodgoodjm/otter/core/adapter/DatabaseAdapter.kt)
- [PostgreSQL 어댑터 구현](../otter-adapter-postgresql/src/main/kotlin/io/github/goodgoodjm/otter/adapter/postgresql/)
- [OtterV2 엔진](../otter-core/src/main/kotlin/io/github/goodgoodjm/otter/core/v2/OtterV2.kt)

## 다음 단계
1. DSL 어댑터화 구현
2. Migration 클래스 V2 호환성 작업
3. 통합 테스트 작성 및 실행
4. Spring Boot Starter 업데이트