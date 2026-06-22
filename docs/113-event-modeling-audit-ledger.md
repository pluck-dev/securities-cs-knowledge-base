# 113. 이벤트 모델링과 감사 원장

> 돈과 권한, 상태 변경을 다루는 시스템은 “무슨 일이 왜 일어났는지” 재구성할 수 있어야 합니다.

## 1. 이벤트 모델링

이벤트는 상태가 아니라 발생한 사실입니다.

좋은 이벤트 이름:

- 과거형.
- 도메인 언어.
- 업무 의미.
- 불변 사실.

예: OrderPlaced, CashReserved, TradeSettled, PasswordChanged.

## 2. Command와 Event

- Command: 무엇을 해달라는 요청.
- Event: 이미 일어난 사실.
- State: 이벤트들이 반영된 현재 모습.

Command는 거절될 수 있지만 Event는 발생 후 변경하지 않는 것이 일반적입니다.

## 3. 감사 로그

감사 로그에는 다음이 필요합니다.

- actor.
- action.
- target.
- timestamp.
- source IP/device.
- before/after 또는 diff.
- reason.
- trace id.
- result.

관리자 기능과 민감 데이터 접근은 반드시 감사 대상입니다.

## 4. 원장 사고방식

원장은 현재 잔고만 저장하지 않고 변동 내역을 기록합니다.

- append-only.
- double-entry.
- immutable history.
- correction entry.
- reconciliation.
- closing.

돈을 다루는 시스템에서는 값 수정이 아니라 정정 거래를 남기는 방식이 안전합니다.

## 5. 이벤트 스키마

- event id.
- event type.
- aggregate id.
- occurred at.
- producer.
- schema version.
- payload.
- causation/correlation id.

스키마 버전과 재처리 가능성이 중요합니다.

## 6. 체크리스트

- [ ] command/event/state를 구분한다.
- [ ] 중요한 변경은 append-only로 추적된다.
- [ ] 감사 로그에 actor/action/target/reason이 있다.
- [ ] 이벤트 스키마 버전과 소유자가 있다.
- [ ] 정정은 삭제/수정이 아니라 보정 이벤트로 처리한다.
- [ ] 대사/reconciliation 절차가 있다.
