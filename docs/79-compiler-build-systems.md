# 79. 컴파일러와 빌드 시스템 내부

> 컴파일러와 빌드 도구를 이해하면 타입 오류, 번들 문제, 느린 CI, 의존성 충돌을 더 빨리 해결할 수 있습니다.

## 1. 컴파일 파이프라인

일반적인 컴파일러는 다음 단계를 거칩니다.

```text
Source Code → Lexer → Parser → AST → Semantic Analysis → IR → Optimization → Code Generation
```

- Lexer는 문자를 토큰으로 나눈다.
- Parser는 토큰을 문법 트리로 만든다.
- AST는 코드 구조를 표현한다.
- Semantic analysis는 타입, 스코프, 이름 해석을 검증한다.
- IR은 최적화하기 쉬운 중간 표현이다.
- Code generation은 바이트코드, 기계어, JavaScript 등 대상 코드를 만든다.

## 2. 타입 검사

타입 시스템은 런타임 오류를 컴파일 타임으로 당기는 장치입니다.

- 정적 타입은 실행 전 많은 오류를 잡는다.
- 타입 추론은 명시적 타입 없이도 타입을 계산한다.
- 제네릭은 타입을 매개변수화한다.
- variance는 생산자/소비자 관계를 안전하게 다룬다.
- nullability는 값 부재를 타입으로 표현한다.

실무에서는 타입을 “문서”로도 사용합니다. API 응답, 도메인 상태, 권한 수준을 타입으로 표현하면 잘못된 조합을 줄일 수 있습니다.

## 3. JVM/JS 빌드 차이

| 생태계 | 빌드 산출물 | 주요 이슈 |
|---|---|---|
| JVM | class, jar, bytecode | classpath, annotation processing, incremental build |
| JavaScript | bundle, chunk, sourcemap | tree shaking, transpilation, module format |
| Native | binary | target architecture, linking, ABI |

JVM은 런타임 classpath 충돌이 많고, 프론트엔드는 번들 크기와 트랜스파일 대상이 중요합니다.

## 4. 의존성 해석

빌드 도구는 직접 의존성과 전이 의존성을 합쳐 실제 그래프를 만듭니다.

주의할 점:

- 같은 라이브러리의 여러 버전이 충돌할 수 있다.
- transitive dependency가 취약점을 가져올 수 있다.
- lockfile은 재현 가능한 빌드에 중요하다.
- peer dependency는 소비자가 버전을 제공해야 한다.
- snapshot/dynamic version은 CI 재현성을 해칠 수 있다.

## 5. Incremental Build와 Cache

빌드가 느릴 때는 전체를 다시 만드는지 확인합니다.

- 입력 파일 hash.
- task dependency graph.
- build cache.
- remote cache.
- generated source.
- annotation processor 영향.

빌드 캐시는 빠르게 만들지만 잘못된 cache key는 오래된 산출물을 재사용하는 위험을 만듭니다.

## 6. CI에서 자주 터지는 문제

- 로컬과 CI의 Node/JDK/Python 버전 차이.
- lockfile 미반영.
- 대소문자 파일명 차이.
- timezone/locale 차이.
- 테스트 순서 의존성.
- 캐시 오염.
- secret이나 인증 토큰 누락.

## 7. 체크리스트

- [ ] 빌드 산출물과 소스 파일을 구분한다.
- [ ] lockfile과 런타임 버전을 고정한다.
- [ ] 의존성 그래프를 확인할 수 있다.
- [ ] incremental build가 깨지는 원인을 추적할 수 있다.
- [ ] CI와 로컬 환경 차이를 문서화한다.
- [ ] sourcemap과 debug symbol의 노출 정책이 있다.
