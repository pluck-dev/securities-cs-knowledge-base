# 74. 메시징, 큐, 스트리밍 시스템

> 큐와 스트리밍은 비동기 처리, 확장성, 장애 격리를 제공하지만 중복·순서·일관성 문제를 함께 가져옵니다.

## 1. 큐를 쓰는 이유

- 요청 경로에서 느린 작업 분리.
- 피크 트래픽 버퍼링.
- 서비스 간 결합도 완화.
- 재시도와 DLQ 처리.
- 이벤트 기반 확장.

## 2. 큐와 스트림 차이

| 구분 | Queue | Stream |
|---|---|---|
| 소비 | 보통 한 소비자 그룹이 메시지 처리 후 제거 | 로그에 남고 offset으로 읽음 |
| 목적 | 작업 분배 | 이벤트 기록과 재처리 |
| 예 | RabbitMQ, SQS | Kafka, Pulsar, Kinesis |

## 3. 전달 보장

- At most once: 최대 한 번. 유실 가능.
- At least once: 최소 한 번. 중복 가능.
- Exactly once: 특정 조건에서 정확히 한 번처럼 보장.

실무에서는 대부분 at-least-once를 전제로 소비자를 멱등하게 만듭니다.

## 4. 순서 보장

전체 순서 보장은 비싸고 확장성을 제한합니다.

- 주문별 순서가 필요하면 accountId/orderId 같은 key로 partitioning.
- 같은 key는 같은 partition으로 보내 순서를 유지.
- partition 수 변경은 key 분포와 순서에 영향을 줄 수 있다.

## 5. Consumer 설계

좋은 consumer:

- 멱등하다.
- 처리 실패를 명확히 분류한다.
- 재시도 횟수와 backoff가 있다.
- poison message를 DLQ로 보낸다.
- offset commit 타이밍이 안전하다.
- 처리 지연과 lag를 모니터링한다.

## 6. Outbox 패턴

DB 변경과 이벤트 발행을 하나의 원자적 작업처럼 다루기 위한 패턴입니다.

1. 비즈니스 테이블 변경과 outbox 테이블 insert를 같은 DB 트랜잭션에서 수행.
2. 별도 publisher가 outbox를 읽어 broker에 발행.
3. 발행 성공 후 상태 변경 또는 삭제.

## 7. Saga 패턴

분산 트랜잭션 대신 여러 로컬 트랜잭션과 보상 작업을 연결합니다.

- Orchestration: 중앙 조정자가 단계 제어.
- Choreography: 이벤트에 반응해 각 서비스가 다음 작업 수행.

보상 작업은 완전한 되돌리기가 아니라 업무적으로 균형을 맞추는 작업일 수 있습니다.

## 8. Event Sourcing

상태를 저장하지 않고 상태 변경 이벤트를 append-only로 저장합니다.

장점:

- 감사 추적.
- 과거 상태 재구성.
- 다양한 read model 생성.

주의:

- 이벤트 스키마 버전 관리.
- snapshot.
- replay 부하.
- 개인정보 삭제 요구와 충돌 가능.

## 9. 운영 지표

- broker availability.
- publish rate / consume rate.
- consumer lag.
- DLQ count.
- retry count.
- partition skew.
- message size.

## 10. 체크리스트

- [ ] 메시지 중복 처리를 가정했다.
- [ ] 순서가 필요한 key가 정의되어 있다.
- [ ] DLQ와 재처리 도구가 있다.
- [ ] consumer lag 알림이 있다.
- [ ] 이벤트 스키마 버전 정책이 있다.
- [ ] DB 변경과 이벤트 발행 원자성 문제를 다뤘다.
