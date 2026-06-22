# 15. 심화: 이벤트 기반 아키텍처와 메시지 큐(Kafka)

증권 시스템은 "주문이 들어옴 → 검증됨 → 체결됨 → 잔고 반영됨 → 알림"처럼 **사건(이벤트)이 흐르는** 구조입니다. 이걸 메시지 큐로 연결합니다.

## 왜 메시지 큐가 필요한가?

### 직접 호출(동기)의 한계
```
주문서버 → (직접 호출) → 원장서버 → (직접 호출) → 알림서버
```
- 원장서버가 죽으면? **주문 전체가 실패**.
- 알림이 느리면? **주문 응답도 느려짐**.
- 새 기능(예: 사기탐지) 추가하려면? **주문서버 코드를 또 고쳐야 함**.

### 메시지 큐(비동기)
```
주문서버 → [Kafka: "체결됨" 이벤트] → 원장서버 (구독)
                                  → 알림서버 (구독)
                                  → 사기탐지 (구독)
```
- 각 서버는 **독립적**. 알림서버가 죽어도 주문/원장은 정상.
- 새 소비자(consumer)는 그냥 **구독만 추가**하면 됨. 기존 코드 안 건드림.
- 이벤트가 큐에 **쌓여 있어서**, 소비자가 잠시 죽어도 살아나면 밀린 걸 처리.

## Kafka 핵심 개념

| 용어 | 뜻 | 비유 |
|------|-----|------|
| **Topic** | 이벤트 종류별 채널 | "체결" 게시판 |
| **Producer** | 이벤트를 보내는 쪽 | 주문서버 |
| **Consumer** | 이벤트를 받는 쪽 | 원장/알림서버 |
| **Partition** | 토픽을 나눈 단위 (병렬 처리/순서 보장) | 게시판의 여러 칸 |
| **Offset** | 어디까지 읽었는지 표시 | 책갈피 |
| **Consumer Group** | 같이 일하는 소비자 묶음 | 처리 팀 |

## 순서 보장 (증권에서 매우 중요)

같은 계좌의 이벤트는 **순서대로** 처리돼야 합니다 (입금 → 출금 순서가 바뀌면 사고).

```
- 같은 계좌 이벤트는 같은 Partition으로 보냄 (key = accountId)
- Kafka는 Partition 안에서는 순서를 보장
```

```kotlin
// 계좌ID를 key로 주면, 같은 계좌는 항상 같은 파티션 → 순서 유지
producer.send(ProducerRecord("executions", order.accountId, executionEvent))
```

## 이벤트 예시 흐름

```
1. [order-requested]  주문 접수      → 검증 서비스가 구독
2. [order-validated]  검증 통과      → 거래소 전송 서비스가 구독
3. [order-executed]   체결 완료      → 원장/알림/통계 서비스가 각각 구독
4. [ledger-updated]   잔고 반영 완료  → 대시보드 갱신 서비스가 구독
```

## 신뢰성 보장 패턴

| 패턴 | 문제 | 해결 |
|------|------|------|
| **멱등 소비** | 같은 이벤트 중복 수신 | 이벤트 ID로 중복 제거 |
| **Outbox 패턴** | DB 저장과 이벤트 발행이 따로 놀아 불일치 | DB 트랜잭션에 이벤트도 함께 기록 후 발행 |
| **Dead Letter Queue** | 처리 실패 이벤트 유실 | 실패분을 별도 큐로 보내 재처리 |
| **재처리(replay)** | 버그로 잘못 처리됨 | offset 되감아 이벤트 재생 |

## 코틀린 + Spring Kafka 예시

```kotlin
// Producer — 체결 이벤트 발행
@Service
class ExecutionPublisher(private val kafka: KafkaTemplate<String, ExecutionEvent>) {
    fun publish(event: ExecutionEvent) {
        kafka.send("order-executed", event.accountId, event)  // key=accountId로 순서 보장
    }
}

// Consumer — 원장 서비스가 구독
@Service
class LedgerConsumer(private val ledgerService: LedgerService) {
    @KafkaListener(topics = ["order-executed"], groupId = "ledger")
    fun onExecuted(event: ExecutionEvent) {
        ledgerService.applyExecution(event)   // 잔고 반영
    }
}
```

## 언제 메시지 큐를 쓰나? (과용 주의)

| 적합 | 부적합 |
|------|--------|
| 여러 서비스가 같은 사건에 반응 | 즉시 응답이 꼭 필요한 단순 조회 |
| 비동기로 처리해도 되는 후속 작업 | 강한 일관성이 필요한 단일 트랜잭션 |
| 대량 이벤트 버퍼링 | 작은 모놀리식 앱 |

> 모든 걸 큐로 만들면 오히려 복잡해집니다. "독립적으로 반응해야 하는 사건"에 씁니다.

---

홈으로: [README](../README.md) · 이전: [14. REST API](14-rest-api.md)
