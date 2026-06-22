# 111. 워크플로 오케스트레이션과 스케줄러

> 배치, 정산, 리포트, 데이터 파이프라인은 순서와 재시도, 보상, 관측성을 갖춘 워크플로로 관리해야 합니다.

## 1. 워크플로가 필요한 이유

- 여러 단계 의존성.
- 긴 실행 시간.
- 실패 후 재시도.
- 중간 상태 저장.
- 수동 승인.
- SLA 관리.
- 감사 추적.

단순 cron은 복잡한 업무 흐름을 관리하기에 부족할 수 있습니다.

## 2. Orchestration vs Choreography

- Orchestration: 중앙 엔진이 단계 순서를 제어.
- Choreography: 이벤트를 받은 각 서비스가 다음 동작 수행.

정산/배치처럼 전체 상태를 추적해야 하면 orchestration이 유리하고, 느슨한 이벤트 반응은 choreography가 유리합니다.

## 3. 스케줄링 고려사항

- timezone.
- holiday calendar.
- daylight saving.
- 중복 실행 방지.
- missed schedule 처리.
- backfill.
- priority.
- concurrency limit.

금융/증권에서는 영업일 캘린더가 특히 중요합니다.

## 4. 재시도와 멱등성

워크플로 단계는 재실행 가능해야 합니다.

- idempotency key.
- checkpoint.
- compensation.
- retry policy.
- timeout.
- dead letter.
- manual intervention.

단계가 외부 시스템을 호출한다면 중복 호출 결과를 반드시 고려합니다.

## 5. 운영 지표

- workflow success rate.
- step duration.
- retry count.
- queue wait time.
- SLA miss.
- stuck workflow.
- manual intervention count.

운영자는 “어느 단계에서 왜 멈췄는지” 바로 알아야 합니다.

## 6. 체크리스트

- [ ] 영업일/타임존/스케줄 정책이 명확하다.
- [ ] 각 단계가 재실행 가능하다.
- [ ] 중복 실행 방지 장치가 있다.
- [ ] stuck workflow 탐지와 수동 복구 도구가 있다.
- [ ] SLA와 단계별 지표를 수집한다.
- [ ] backfill 절차가 안전하다.
