# 72. 성능과 확장성 엔지니어링

> 성능 최적화는 감이 아니라 측정, 병목 식별, trade-off 선택의 반복입니다.

## 1. 성능 지표

- Latency: 요청 하나가 걸리는 시간.
- Throughput: 단위 시간 처리량.
- QPS/RPS: 초당 요청 수.
- p50/p95/p99: 지연 시간 분포.
- Error rate: 실패 비율.
- Saturation: CPU, 메모리, 커넥션, 큐가 얼마나 찼는지.

평균 latency는 거의 항상 부족합니다. p95와 p99를 봐야 사용자가 느끼는 지연을 이해할 수 있습니다.

## 2. 병목 찾기

```
사용자 → CDN → LB → App → Cache → DB → External API
```

각 구간에서 시간을 측정합니다. 추측으로 튜닝하면 병목이 아닌 곳을 최적화하게 됩니다.

## 3. Amdahl의 법칙

전체 시간의 대부분을 차지하는 부분을 줄여야 의미가 있습니다.

- 1% 구간을 100배 빠르게 해도 전체는 거의 안 빨라진다.
- 60% 구간을 절반으로 줄이면 큰 효과가 난다.
- 병렬화해도 순차 구간은 남는다.

## 4. 백엔드 성능

- DB 쿼리 수 줄이기.
- N+1 제거.
- connection pool 적정화.
- thread pool 적정화.
- serialization 비용 줄이기.
- 캐시 사용.
- batch 처리.
- 비동기 큐 분리.

## 5. 프론트엔드 성능

- JavaScript bundle 줄이기.
- 이미지 최적화.
- SSR/SSG/CSR 선택.
- code splitting.
- prefetch/preload.
- long task 제거.
- layout shift 방지.

## 6. DB 성능

- 실행 계획 확인.
- 복합 인덱스 설계.
- keyset pagination.
- hot row 줄이기.
- 읽기 replica.
- 파티셔닝.
- slow query log 분석.

## 7. 캐시 전략

캐시는 성능을 올리지만 정합성을 어렵게 합니다.

- TTL 기반.
- explicit invalidation.
- write-through.
- cache warming.
- request coalescing.
- stale-while-revalidate.

## 8. 수평 확장

수평 확장은 stateless 설계가 전제입니다.

- 세션을 외부 저장소 또는 토큰으로 분리.
- 파일 업로드를 object storage로 분리.
- 배치 중복 실행 방지.
- 분산 락 또는 leader election 검토.
- DB가 최종 병목이 될 수 있음.

## 9. 부하 테스트 해석

- 에러율이 먼저 오르는가, latency가 먼저 오르는가?
- CPU가 높은가, DB가 높은가, 큐가 쌓이는가?
- p99 spike가 GC와 관련 있는가?
- connection pool 대기가 있는가?
- downstream rate limit에 걸리는가?

## 10. 체크리스트

- [ ] 목표 latency/throughput이 수치로 정의되어 있다.
- [ ] p95/p99를 측정한다.
- [ ] 병목 구간이 trace/metric으로 확인되었다.
- [ ] 캐시 도입 시 무효화 전략이 있다.
- [ ] 부하 테스트 데이터가 실제 분포와 유사하다.
- [ ] 최적화 전후를 같은 조건에서 비교했다.
