# 61. 프론트엔드 엔지니어링 필수 지식

> 프론트엔드는 HTML/CSS/JavaScript로 사용자 경험, 접근성, 성능, 보안을 브라우저 안에서 구현하는 영역입니다.

## 1. 브라우저 렌더링 파이프라인

```
HTML 파싱 → DOM 생성
CSS 파싱 → CSSOM 생성
DOM + CSSOM → Render Tree
Layout → Paint → Composite
```

성능을 이해하려면 어떤 변경이 어느 단계를 다시 실행시키는지 알아야 합니다.

- DOM 구조 변경: layout과 paint를 유발할 수 있다.
- 색상 변경: paint만 유발할 수 있다.
- transform/opacity: composite 단계에서 처리되어 상대적으로 저렴한 경우가 많다.
- 강제 동기 layout은 성능을 크게 떨어뜨린다.

## 2. HTML과 접근성

시맨틱 HTML은 접근성과 유지보수의 시작입니다.

- 버튼은 `div`가 아니라 `button`.
- 페이지 구조는 `header`, `nav`, `main`, `section`, `footer`.
- 입력에는 `label`을 연결한다.
- 이미지에는 의미 있는 `alt`를 제공하거나 장식이면 비운다.
- 키보드만으로 모든 기능을 사용할 수 있어야 한다.

### ARIA 원칙

1. 네이티브 HTML로 가능하면 ARIA를 쓰지 않는다.
2. ARIA를 쓰면 역할, 상태, 키보드 동작까지 책임진다.
3. 스크린리더 테스트를 실제로 해본다.

## 3. CSS 핵심

| 개념 | 설명 |
|---|---|
| Cascade | 여러 규칙 중 어떤 스타일이 적용되는지 결정 |
| Specificity | 선택자 우선순위 |
| Box Model | content, padding, border, margin |
| Flexbox | 1차원 배치 |
| Grid | 2차원 배치 |
| Stacking Context | z-index가 적용되는 맥락 |
| Container Query | 컴포넌트 컨테이너 크기에 따른 반응형 |

### CSS 실무 원칙

- 전역 스타일을 최소화한다.
- 디자인 토큰으로 색상·간격·폰트를 통일한다.
- 레이아웃과 시각 스타일을 분리해서 생각한다.
- 반응형은 모바일/태블릿/데스크톱뿐 아니라 콘텐츠 길이까지 고려한다.

## 4. JavaScript/TypeScript

### JavaScript 런타임

- Call Stack은 현재 실행 중인 함수 스택이다.
- Heap은 객체가 저장되는 메모리다.
- Event Loop는 태스크 큐와 마이크로태스크 큐를 처리한다.
- Promise callback은 마이크로태스크로 처리된다.

### TypeScript를 쓰는 이유

- API 계약을 코드로 표현한다.
- null/undefined를 명시적으로 다룬다.
- 잘못된 상태를 컴파일 단계에서 줄인다.
- 리팩터링 안정성을 높인다.

주의점: 타입은 런타임 검증이 아닙니다. 외부 입력은 zod, yup 같은 런타임 검증 또는 직접 검증이 필요합니다.

## 5. 상태 관리

상태는 위치에 따라 복잡도가 달라집니다.

| 상태 | 예시 | 관리 위치 |
|---|---|---|
| Local UI State | 모달 열림, 입력값 | 컴포넌트 내부 |
| Server State | 사용자 목록, 주문 목록 | query/cache 라이브러리 |
| URL State | 검색어, 필터, 페이지 | URL query/path |
| Global Client State | 테마, 로그인 사용자 | 전역 store/context |
| Derived State | 총합, 필터 결과 | 저장하지 말고 계산 |

원칙: 가능한 한 상태를 적게 만들고, 한 상태의 소유자를 분명히 합니다.

## 6. React/Vue/Svelte 공통 사고법

- UI는 상태의 함수다.
- 렌더링은 비싸질 수 있으므로 불필요한 재렌더를 줄인다.
- key는 리스트 항목의 정체성을 나타낸다.
- effect는 외부 세계와 동기화할 때만 사용한다.
- 컴포넌트 추상화는 재사용보다 변경 이유가 같을 때 만든다.

## 7. 프론트엔드 성능

### Core Web Vitals 감각

- **LCP**: 주요 콘텐츠가 빨리 보이는가?
- **INP**: 사용자의 입력에 빠르게 반응하는가?
- **CLS**: 화면이 갑자기 밀리지 않는가?

### 최적화 방법

- 이미지 크기와 포맷 최적화.
- 코드 스플리팅과 lazy loading.
- critical CSS와 폰트 로딩 최적화.
- 불필요한 JavaScript 줄이기.
- API 병렬화와 prefetch.
- 긴 작업을 쪼개 메인 스레드 점유 줄이기.

## 8. 프론트엔드 보안

- XSS: 사용자 입력을 HTML로 직접 삽입하지 않는다.
- CSRF: 쿠키 기반 인증에서는 SameSite, CSRF token, Origin 검증을 고려한다.
- Clickjacking: `X-Frame-Options` 또는 CSP frame-ancestors.
- 민감정보: localStorage에 장기 토큰을 저장할 때 위험을 이해한다.
- 의존성: 번들에 포함되는 패키지의 공급망 위험을 관리한다.

## 9. 테스트

| 테스트 | 확인하는 것 |
|---|---|
| Unit | 순수 함수, 유틸, 훅 |
| Component | 렌더링과 사용자 상호작용 |
| Visual Regression | 의도치 않은 UI 변경 |
| E2E | 실제 브라우저 사용자 흐름 |
| Accessibility | 키보드/스크린리더/색 대비 |

테스트는 구현 세부보다 사용자가 보는 결과와 접근 가능한 인터랙션을 검증합니다.

## 10. 프론트엔드 체크리스트

- [ ] 시맨틱 HTML과 키보드 접근성이 지켜졌다.
- [ ] API 오류·로딩·빈 상태가 설계되어 있다.
- [ ] 상태 소유자가 명확하고 중복 상태가 없다.
- [ ] 큰 번들·느린 이미지·긴 메인 스레드 작업을 확인했다.
- [ ] XSS/CSRF/토큰 저장 위치를 검토했다.
- [ ] 주요 흐름 E2E와 접근성 테스트가 있다.
