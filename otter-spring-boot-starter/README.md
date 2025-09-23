# Otter Spring Boot Starter

## 개요
Spring Boot 애플리케이션에서 Otter 마이그레이션 도구를 자동으로 설정하고 실행하는 스타터 모듈입니다.

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
  - `otter.up()` 호출로 마이그레이션 실행
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

### 컴파일 의존성
- `spring-boot-autoconfigure`: 자동 설정
- `spring-boot-configuration-processor`: 설정 메타데이터 생성

## 동작 방식

1. **애플리케이션 시작**
2. **자동 설정 조건 확인**
   - `otter.enable=true` (기본값)
   - DataSource 존재
3. **설정 로드**
   - application.yml에서 `otter.*` 속성 읽기
4. **Bean 생성**
   - OtterConfig Bean
   - SpringOtter Bean
5. **마이그레이션 실행**
   - `afterPropertiesSet()`에서 `otter.up()` 호출
   - 모든 대기 중인 마이그레이션 적용

## 사용 방법

### 1. 의존성 추가
```gradle
implementation("io.github.goodgoodjm:otter-spring-boot-starter:버전")
```

### 2. application.yml 설정
```yaml
otter:
  migration-path: "classpath:migrations"
  driver-class-name: "org.h2.Driver"
  url: "jdbc:h2:mem:test"
  username: "sa"
  password: ""
  show-sql: true
```

### 3. 마이그레이션 파일 작성
`src/main/resources/migrations/` 폴더에 .kts 파일 추가

## 특징
- Spring Boot 2.x/3.x 호환
- 자동 설정으로 코드 없이 사용 가능
- DataSource 자동 감지 지원 (예정)
- 조건부 실행 설정 가능

## 현재 제한사항
- 애플리케이션 시작 시 무조건 실행 (비동기 옵션 없음)
- 마이그레이션 실패 시 애플리케이션 시작 실패
- 다중 DataSource 미지원
- Spring 트랜잭션 관리와 독립적 동작