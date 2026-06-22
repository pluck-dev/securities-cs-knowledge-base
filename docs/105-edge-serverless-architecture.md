# 105. Edge와 Serverless 아키텍처

> Edge와 Serverless는 운영 부담을 줄이고 지연을 낮출 수 있지만 실행 제약과 관측성 복잡도를 이해해야 합니다.

## 1. Serverless

Serverless는 서버가 없다는 뜻이 아니라 서버 운영을 플랫폼에 위임한다는 뜻입니다.

장점:

- 자동 확장.
- 사용량 기반 과금.
- 운영 부담 감소.
- 이벤트 기반 처리에 적합.

단점:

- cold start.
- 실행 시간 제한.
- 벤더 종속.
- 로컬 재현 어려움.

## 2. Edge Computing

Edge는 사용자 가까운 위치에서 코드를 실행합니다.

적합한 작업:

- 인증/리다이렉트.
- A/B 라우팅.
- 이미지 변환.
- 캐시 제어.
- personalization 일부.
- bot filtering.

무거운 DB 트랜잭션에는 보통 적합하지 않습니다.

## 3. 이벤트 기반 Serverless

- object upload trigger.
- queue trigger.
- scheduled job.
- webhook handler.
- stream processor.

중복 실행과 재시도를 가정해야 하며, 함수는 멱등하게 설계해야 합니다.

## 4. 상태 관리

Serverless 함수는 stateless가 기본입니다.

- 상태는 DB/cache/object storage에 둔다.
- connection 재사용은 runtime 재활용에 의존한다.
- global variable cache는 최적화일 뿐 정확성 근거가 아니다.
- idempotency key가 중요하다.

## 5. 관측성과 비용

- invocation count.
- duration.
- cold start rate.
- error rate.
- retry count.
- concurrency.
- memory usage.
- egress cost.

작은 함수가 많으면 trace와 비용 추적이 중요해집니다.

## 6. 체크리스트

- [ ] cold start가 사용자 흐름에 미치는 영향을 측정했다.
- [ ] 함수가 멱등하게 동작한다.
- [ ] timeout과 retry 정책이 명확하다.
- [ ] edge에서 접근 가능한 데이터와 secret 범위를 제한했다.
- [ ] vendor lock-in과 migration 비용을 검토했다.
- [ ] invocation/duration/error/cost를 모니터링한다.
