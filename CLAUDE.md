# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Otter는 Kotlin 기반의 데이터베이스 마이그레이션 도구입니다. Harmonica에서 영감을 받아 만들어졌으며, Spring Boot 애플리케이션과 통합하여 사용할 수 있습니다.

## 빌드 및 개발 명령어

### 빌드
```bash
./gradlew build        # 전체 프로젝트 빌드
./gradlew clean build  # 클린 빌드
```

### 테스트
```bash
./gradlew test         # 전체 테스트 실행
./gradlew :otter-core:test  # 특정 모듈 테스트
```

### 배포
```bash
./gradlew publish      # Maven 저장소에 배포
```

## 프로젝트 구조 및 아키텍처

### 모듈 구조
- **otter-core**: 핵심 마이그레이션 엔진
  - `Migration`: 마이그레이션 추상 클래스, up/down 메서드 정의
  - `Otter`: 메인 엔진 클래스, 마이그레이션 실행 관리
  - `OtterConfig`: 설정 관리 (DB 연결 정보, 마이그레이션 경로 등)
  - DSL 패키지: 테이블 생성/수정을 위한 Kotlin DSL 제공

- **otter-spring-boot-starter**: Spring Boot 자동 설정
  - `OtterAutoConfiguration`: Spring Boot 자동 설정
  - `OtterProperties`: application.yml 설정 매핑
  - `SpringOtter`: Spring 환경에서의 Otter 실행

- **otter-plugin**: Gradle 플러그인 (개발 중)

### 핵심 아키텍처 개념

1. **마이그레이션 파일 (.kts)**
   - Kotlin 스크립트로 작성된 마이그레이션 정의
   - `Migration` 추상 클래스를 상속하여 구현
   - `up()`과 `down()` 메서드로 적용/롤백 정의

2. **DSL 기반 스키마 정의**
   - 타입 안전한 Kotlin DSL로 테이블 스키마 정의
   - Exposed 라이브러리 위에 구축된 추상화 레이어
   - 예: `"id" - INT constraints PRIMARY and AUTO_INCREMENT`

3. **락 메커니즘**
   - `otter_lock` 테이블로 동시성 제어
   - 마이그레이션 실행 시 자동으로 락 획득/해제
   - 최대 60초 대기 후 타임아웃

4. **마이그레이션 추적**
   - `otter_migration` 테이블에 실행된 마이그레이션 기록
   - 파일명 기반으로 순서 관리
   - 버전 지정으로 특정 버전까지만 마이그레이션 가능

### 주요 의존성
- Kotlin Script Runtime: 마이그레이션 파일 실행
- Exposed: SQL 쿼리 생성 및 실행
- SLF4J: 로깅

## 개발 시 유의사항

1. 마이그레이션 파일명은 고유해야 하며, 알파벳 순서로 실행됨
2. DSL 사용 시 타입 안전성을 위해 정의된 타입 상수 사용 권장
3. 테스트 실행 시 H2 인메모리 데이터베이스 사용
4. Spring Boot Starter 사용 시 `bootJar` 태스크에 `requiresUnpack` 설정 필수

## 테스트 코드 작성 규칙

1. **필수 테스트**: 모든 새로운 기능과 변경사항에는 반드시 테스트 코드를 작성해야 함
2. **테스트 위치**: 
   - 단위 테스트: `src/test/kotlin/` 아래 동일한 패키지 구조로 작성
   - 통합 테스트: 별도의 통합 테스트 클래스로 작성
3. **명명 규칙**: 테스트 메서드명은 `테스트대상_조건_기대결과` 형식 사용
   - 예: `createTable_withPrimaryKey_shouldGenerateCorrectSQL`
4. **테스트 프레임워크**: Kotlin Test + JUnit5 사용
5. **데이터베이스**: H2 인메모리 데이터베이스 사용
6. **검증 사항**:
   - DSL 문법의 정확성
   - 생성되는 SQL 문의 정확성
   - 제약조건 적용 여부
   - 예외 처리

## Commit / Push / PR 생성 규칙
1. AI 관련 언급 금지
2. Title 가장 앞에 feat:, refactor:, docs: 등 커밋 분류 기입
3. 내용 및 설명은 모두 한글로
4. 이모지 금지
5. PR 생성 시 작업 내용에 맞는 브랜치명 사용 (예: feature/alter-table, fix/legacy-code-removal)