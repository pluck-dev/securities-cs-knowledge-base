# 73. 고급 프론트엔드 플랫폼 지식

> 현대 프론트엔드는 단순 화면 구현을 넘어 렌더링 전략, 빌드, 접근성, 보안, 디자인 시스템을 함께 다룹니다.

## 1. 렌더링 전략

| 전략 | 설명 | 적합한 곳 |
|---|---|---|
| CSR | 브라우저에서 렌더링 | 앱형 서비스 |
| SSR | 서버에서 HTML 생성 | SEO, 초기 로딩 중요 |
| SSG | 빌드 시 HTML 생성 | 문서, 블로그, 랜딩 |
| ISR | 정적 페이지를 주기적으로 갱신 | 자주 바뀌지 않는 콘텐츠 |
| Streaming SSR | HTML을 점진 전송 | 큰 페이지 초기 표시 개선 |

## 2. Hydration

SSR/SSG HTML에 JavaScript 이벤트와 상태를 연결하는 과정입니다.

주의점:

- 서버와 클라이언트 렌더 결과가 다르면 hydration mismatch.
- 시간, 랜덤 값, 브라우저 전용 API 사용에 주의.
- 너무 많은 hydration은 초기 JS 비용을 키운다.

## 3. 번들러와 빌드

- Tree shaking: 사용하지 않는 코드 제거.
- Code splitting: 필요한 시점에 코드 로드.
- Minification: 코드 크기 축소.
- Source map: 운영 디버깅에 필요하지만 노출 정책 주의.
- Transpilation: 최신 문법을 대상 브라우저에 맞게 변환.

## 4. 패키지 관리

- dependency와 devDependency 구분.
- lockfile 커밋.
- peer dependency 충돌 이해.
- transitive dependency 취약점 관리.
- monorepo에서는 workspace 경계와 version policy가 중요하다.

## 5. 디자인 시스템

디자인 시스템은 컴포넌트 모음이 아니라 제품 UI의 언어입니다.

구성 요소:

- design tokens.
- typography.
- color system.
- spacing.
- component variants.
- accessibility rules.
- usage guidelines.

## 6. 폼과 검증

폼은 프론트엔드 복잡도의 중심입니다.

- client validation은 UX용.
- server validation은 최종 진실.
- field-level error와 form-level error를 구분.
- 입력 중 검증과 제출 후 검증의 타이밍을 조절.
- IME, 모바일 키보드, 자동완성 고려.

## 7. 국제화와 현지화

- 언어 번역.
- 날짜/시간 형식.
- 숫자/통화 형식.
- 복수형.
- RTL layout.
- 타임존.

문자열을 코드에 직접 박아두면 나중에 분리 비용이 큽니다.

## 8. PWA와 오프라인

- Service Worker.
- Cache Storage.
- offline fallback.
- push notification.
- background sync.

오프라인 지원은 동기화 충돌과 stale data 정책까지 포함합니다.

## 9. 프론트엔드 모니터링

- JavaScript error.
- unhandled promise rejection.
- route change latency.
- Core Web Vitals.
- API 실패율.
- 사용자 세션 replay는 개인정보 정책을 고려한다.

## 10. 체크리스트

- [ ] 렌더링 전략 선택 이유가 명확하다.
- [ ] hydration mismatch 가능성을 검토했다.
- [ ] bundle size와 dependency를 측정한다.
- [ ] 디자인 토큰과 접근성 규칙이 있다.
- [ ] 폼 오류 UX가 명확하다.
- [ ] 프론트엔드 오류와 Web Vitals를 수집한다.
