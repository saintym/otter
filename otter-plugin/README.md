# Otter Plugin

## 개요
Otter Plugin은 Otter 마이그레이션 도구를 위한 Gradle 플러그인입니다. 현재 개발 초기 단계입니다.

## 현재 상태
- **버전**: 0.0.0 (미출시)
- **상태**: 개발 중 (기본 구조만 구현)

## 프로젝트 구조

```
otter-plugin/
├── build.gradle.kts    # Gradle 플러그인 설정
└── src/main/kotlin/io/github/goodgoodjm/otter/plugin/
    └── OtterPlugin.kt  # 플러그인 메인 클래스
```

## 구현 내용

### OtterPlugin.kt
- `Plugin<Project>` 인터페이스 구현
- `apply()` 메서드에 임시 코드만 존재
- "hello" 태스크 등록 (테스트용)

## 빌드 설정
- **플러그인 ID**: `io.github.goodgoodjm.otter.plugin`
- **그룹**: `io.github.goodgoodjm.otter.plugin`
- **Gradle Plugin Portal 등록 준비**

## 계획된 기능 (미구현)
Gradle 플러그인을 통해 다음과 같은 기능을 제공할 예정:
- 마이그레이션 실행 태스크
- 마이그레이션 생성 태스크
- 마이그레이션 상태 확인 태스크
- 롤백 태스크

## 현재 문제점
- 실제 기능 구현 없음
- 테스트 코드 없음
- otter-core 의존성 연결 안됨

## 참고
현재는 개발 초기 단계로 실제 사용 불가능한 상태입니다.