# 76. 디버깅과 트러블슈팅 방법론

> 좋은 개발자는 버그를 빨리 고치는 사람이 아니라 문제를 체계적으로 좁히고 재발을 막는 사람입니다.

## 1. 디버깅 기본 루프

1. 현상과 기대 결과를 분리한다.
2. 재현 조건을 찾는다.
3. 최근 변경과 외부 요인을 확인한다.
4. 가설을 하나 세운다.
5. 가장 싼 검증을 한다.
6. 결과에 따라 가설을 수정한다.
7. 수정 후 회귀 테스트를 남긴다.

## 2. 문제를 좁히는 질문

- 언제부터 발생했는가?
- 모든 사용자에게 발생하는가, 일부인가?
- 특정 브라우저/지역/계정/데이터에서만 발생하는가?
- 성공 요청과 실패 요청의 차이는 무엇인가?
- 최근 배포, 설정 변경, 트래픽 변화가 있었는가?
- 재시도하면 성공하는가?

## 3. 로그 읽기

좋은 로그 분석 순서:

1. trace id로 한 요청을 연결한다.
2. 시간 순으로 정렬한다.
3. 최초 오류와 파생 오류를 구분한다.
4. 사용자 영향과 시스템 영향을 분리한다.
5. 오류율과 샘플을 함께 본다.

## 4. 메트릭 읽기

- latency spike.
- error rate.
- traffic volume.
- saturation.
- queue lag.
- DB connection usage.
- GC pause.

상관관계가 인과관계는 아니지만, 시간 축이 맞는 지표는 좋은 단서입니다.

## 5. 분산 추적

Trace는 요청이 여러 서비스와 DB를 거치는 경로를 보여줍니다.

확인할 것:

- 가장 긴 span.
- 실패한 span.
- retry로 반복된 span.
- downstream timeout.
- fan-out 호출 수.

## 6. 로컬 디버깅

- debugger breakpoint.
- unit test로 최소 재현.
- logging 추가.
- binary search로 변경 범위 축소.
- feature flag 끄기.
- dependency version 되돌리기.

## 7. 운영 장애 디버깅

운영에서는 복구가 우선입니다.

- rollback.
- traffic drain.
- circuit breaker open.
- feature flag off.
- cache purge.
- scaling.
- rate limit.

분석은 복구 후에도 계속하지만, 사용자가 영향을 받는 동안은 완화 조치가 먼저입니다.

## 8. 흔한 원인 패턴

- null/undefined.
- timezone.
- race condition.
- stale cache.
- DB index 누락.
- connection pool 고갈.
- retry storm.
- config drift.
- dependency API 변경.
- 권한 누락.

## 9. 재발 방지

- 회귀 테스트 추가.
- 알림 추가.
- 런북 업데이트.
- 타입/스키마 강화.
- 안전한 기본값 설정.
- 배포 검증 단계 추가.
- 코드 삭제 또는 단순화.

## 10. 체크리스트

- [ ] 현상, 영향 범위, 시작 시간이 정리되었다.
- [ ] trace id로 요청 흐름을 확인했다.
- [ ] 최근 변경과 외부 의존성 상태를 확인했다.
- [ ] 한 번에 하나의 가설만 검증했다.
- [ ] 수정 후 회귀 테스트나 모니터링을 추가했다.
- [ ] 재발 방지 액션이 문서화되었다.
