# 114. 데이터 정합성, 대사, 보정

> 분산 시스템에서는 데이터가 어긋날 수 있습니다. 중요한 것은 어긋남을 탐지하고 안전하게 보정하는 능력입니다.

## 1. 정합성의 종류

- strong consistency.
- eventual consistency.
- read-your-writes.
- monotonic reads.
- causal consistency.
- external consistency.

모든 데이터를 강한 정합성으로 만들 수는 없으므로 데이터별 요구 수준을 정해야 합니다.

## 2. 어긋나는 이유

- 중복 메시지.
- 순서 뒤바뀜.
- 일부 단계 실패.
- 재시도 중 timeout.
- 캐시 stale.
- 수동 운영 작업.
- 배치 누락.
- 외부 시스템 지연.

## 3. Reconciliation

대사는 두 시스템 또는 두 관점의 데이터를 비교합니다.

예:

- 주문 시스템 vs 체결 시스템.
- 원장 vs 은행 입출금.
- 내부 정산 vs 외부 거래소.
- 이벤트 로그 vs read model.

대사는 차이를 찾고 원인 분류와 보정까지 이어져야 합니다.

## 4. 보정 전략

- 자동 보정.
- 수동 승인 후 보정.
- 보정 이벤트 발행.
- reverse transaction.
- compensating command.
- read model rebuild.

중요 데이터는 조용히 덮어쓰지 않고 근거를 남겨야 합니다.

## 5. 정합성 지표

- mismatch count.
- reconciliation delay.
- correction count.
- stale read ratio.
- duplicate event count.
- out-of-order event count.
- cache invalidation miss.

## 6. 체크리스트

- [ ] 데이터별 정합성 요구 수준이 정의되어 있다.
- [ ] 대사 기준과 주기가 있다.
- [ ] mismatch 원인 분류가 가능하다.
- [ ] 보정은 감사 가능한 이벤트로 남는다.
- [ ] read model 재생성 절차가 있다.
- [ ] 정합성 지표와 알림이 있다.
