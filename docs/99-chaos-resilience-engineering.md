# 99. 카오스와 복원력 엔지니어링

> 복원력은 문서로 증명되지 않습니다. 안전한 실험으로 시스템이 장애를 견디는지 확인해야 합니다.

## 1. 복원력의 목표

- 장애를 완전히 없애는 것이 아니라 사용자 영향을 줄인다.
- 단일 장애점(SPOF)을 줄인다.
- 장애를 빠르게 탐지한다.
- 자동 또는 반자동으로 복구한다.
- graceful degradation을 제공한다.

## 2. Chaos Engineering

카오스 실험은 무작정 장애를 내는 것이 아닙니다.

절차:

1. 정상 상태 가설 정의.
2. 영향 범위 제한.
3. 장애 주입.
4. 지표 관찰.
5. 자동 중단 조건.
6. 개선 액션 도출.

## 3. 장애 주입 예시

- 인스턴스 종료.
- 네트워크 지연.
- DNS 실패.
- DB connection 거부.
- 큐 지연.
- 캐시 장애.
- disk full.
- clock skew.

운영 실험 전 staging과 game day로 시작합니다.

## 4. Resilience 패턴

- timeout.
- retry with jitter.
- circuit breaker.
- bulkhead.
- fallback.
- rate limit.
- load shedding.
- graceful shutdown.
- degraded mode.

## 5. Game Day

Game day는 팀이 장애 대응을 연습하는 시간입니다.

- 시나리오 준비.
- 역할 분담.
- 커뮤니케이션 채널.
- 런북 검증.
- 관측성 검증.
- 회고와 액션 아이템.

## 6. 체크리스트

- [ ] 정상 상태 지표가 정의되어 있다.
- [ ] 실험 blast radius를 제한한다.
- [ ] 자동 중단 조건이 있다.
- [ ] 장애 대응 런북을 실제로 실행해봤다.
- [ ] 복원력 패턴이 테스트되어 있다.
- [ ] game day 결과가 개선으로 이어진다.
