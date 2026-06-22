# 83. 브라우저와 JavaScript 엔진 내부

> 프론트엔드 성능과 버그를 제대로 다루려면 브라우저 엔진, 이벤트 루프, JIT, 메모리 모델을 이해해야 합니다.

## 1. 브라우저 프로세스 모델

현대 브라우저는 여러 프로세스로 나뉩니다.

- Browser process: 탭, 주소창, 네트워크 조정.
- Renderer process: HTML/CSS/JS 실행과 렌더링.
- GPU process: compositing과 그래픽.
- Network service: 네트워크 요청.

사이트 격리와 sandbox는 보안과 안정성을 높입니다.

## 2. 렌더링 엔진

렌더링 엔진은 HTML/CSS를 화면으로 바꿉니다.

```text
Parse → DOM/CSSOM → Style → Layout → Paint → Composite
```

성능 최적화는 어떤 코드가 layout, paint, composite 중 무엇을 유발하는지 이해하는 데서 시작합니다.

## 3. JavaScript 엔진

V8 같은 엔진은 JS를 해석하고 최적화합니다.

- Parser가 AST를 만든다.
- Interpreter가 빠르게 실행을 시작한다.
- JIT compiler가 hot code를 최적화한다.
- hidden class와 inline cache가 객체 접근을 빠르게 한다.
- deoptimization이 발생하면 성능이 급락할 수 있다.

## 4. Event Loop

브라우저는 call stack, task queue, microtask queue를 통해 비동기 작업을 처리합니다.

- Promise callback은 microtask.
- setTimeout은 task.
- rendering은 event loop 사이클 중 특정 시점에 발생.
- 긴 JS 작업은 입력 반응을 막는다.

INP 최적화는 long task를 줄이는 것과 연결됩니다.

## 5. 메모리와 GC

브라우저 JS도 GC를 사용합니다.

메모리 누수 패턴:

- DOM node 참조를 계속 들고 있음.
- 이벤트 리스너 해제 누락.
- interval/timer 해제 누락.
- 큰 closure가 필요 없는 객체를 붙잡음.
- 전역 cache 무제한 증가.

## 6. 네트워크 스택

브라우저는 connection pooling, HTTP cache, service worker, preflight, priority를 관리합니다.

프론트엔드 성능은 코드뿐 아니라 다음과 연결됩니다.

- DNS/TLS 지연.
- HTTP/2 multiplexing.
- resource priority.
- preload/prefetch.
- cache headers.
- service worker strategy.

## 7. 보안 경계

브라우저 보안 모델:

- Same-Origin Policy.
- CORS.
- CSP.
- sandbox iframe.
- cookie SameSite.
- storage partitioning.

브라우저가 막아주는 것과 서버가 반드시 검증해야 하는 것을 구분해야 합니다.

## 8. 체크리스트

- [ ] layout/paint/composite 유발 작업을 구분한다.
- [ ] event loop와 microtask를 설명할 수 있다.
- [ ] long task와 INP를 측정한다.
- [ ] memory leak을 heap snapshot으로 찾을 수 있다.
- [ ] resource priority와 cache header를 확인한다.
- [ ] 브라우저 보안 정책과 서버 검증 책임을 구분한다.
