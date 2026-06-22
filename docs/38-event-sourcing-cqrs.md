# 38. 이벤트 소싱과 CQRS

> 시스템의 **현재 상태**만 저장하면 "지금 어떻게 됐는가"는 알 수 있지만,  
> "왜 이렇게 됐는가"와 "언제 어떤 일이 있었는가"는 영영 잃어버린다.

---

## 1. 전통적 CRUD 상태 저장의 한계

대부분의 서비스는 최신 상태를 덮어쓰는 방식으로 데이터를 저장한다.

```
| order_id | status    | filled_qty | updated_at          |
|----------|-----------|------------|---------------------|
| 1001     | PARTIALLY | 30         | 2024-03-15 09:32:11 |
```

이 테이블 한 행을 보면 "현재 30주 체결됨" 은 알 수 있지만 다음 질문에 답할 수 없다.

- 처음 주문한 수량은 얼마였는가?
- 30주가 한 번에 체결됐는가, 10주씩 세 번에 걸쳐 체결됐는가?
- 중간에 정정 주문이 있었는가?
- 누가, 언제, 어떤 이유로 상태를 바꿨는가?

### 세 가지 핵심 문제

| 문제 | 설명 | 금융 영향 |
|------|------|-----------|
| 감사추적 부재 | 이전 상태가 덮어쓰여 복원 불가 | 규제 위반 (MiFID II, K-IFRS) |
| 시간여행 불가 | 특정 시점의 상태를 재구성할 수 없음 | 분쟁 처리, 백테스팅 불가 |
| 동시성 충돌 | 여러 프로세스가 동시에 같은 행을 갱신할 때 Lost Update 발생 | 잔고 오차, 이중 체결 |

> CRUD 방식에서 낙관적 락(Optimistic Lock)이나 버전 컬럼으로 동시성을 제어할 수 있지만,  
> 근본적으로 "무엇이 일어났는가"를 기록하지 않는 구조적 한계는 해결되지 않는다.

---

## 2. 이벤트 소싱(Event Sourcing): 사건의 연속을 저장한다

### 핵심 아이디어

> **상태(State)를 저장하지 말고, 상태를 만들어 낸 사건(Event)을 저장하라.**
> 현재 상태는 이벤트를 처음부터 재생(Replay)해서 언제든 재계산할 수 있다.

```
사건 목록 (Event Store):
  1. OrderPlaced       { qty: 100, price: 85_000, side: BUY }
  2. OrderPartiallyFilled { filledQty: 30, price: 85_000 }
  3. OrderPartiallyFilled { filledQty: 20, price: 84_900 }
  4. OrderModified     { newQty: 80 }        ← 정정
  5. OrderPartiallyFilled { filledQty: 30, price: 85_100 }

재생 결과 → 현재 상태: filled=80, remaining=0, avgPrice=84_991.25
```

### 주문/체결 이벤트 스트림 — Kotlin sealed class

```kotlin
// 모든 도메인 이벤트의 공통 인터페이스
sealed interface OrderEvent {
    val orderId: String
    val occurredAt: Instant
    val version: Long
}

data class OrderPlaced(
    override val orderId: String,
    override val occurredAt: Instant,
    override val version: Long,
    val memberId: String,
    val symbol: String,
    val side: OrderSide,          // BUY | SELL
    val orderType: OrderType,     // LIMIT | MARKET
    val qty: Int,
    val limitPrice: BigDecimal?,
) : OrderEvent

data class OrderPartiallyFilled(
    override val orderId: String,
    override val occurredAt: Instant,
    override val version: Long,
    val filledQty: Int,
    val executionPrice: BigDecimal,
    val counterPartyId: String,
) : OrderEvent

data class OrderFullyFilled(
    override val orderId: String,
    override val occurredAt: Instant,
    override val version: Long,
    val filledQty: Int,
    val executionPrice: BigDecimal,
) : OrderEvent

data class OrderModified(
    override val orderId: String,
    override val occurredAt: Instant,
    override val version: Long,
    val newQty: Int,
    val newLimitPrice: BigDecimal?,
    val reason: String,
) : OrderEvent

data class OrderCancelled(
    override val orderId: String,
    override val occurredAt: Instant,
    override val version: Long,
    val cancelledBy: CancelledBy,  // MEMBER | SYSTEM | BROKER
    val reason: String,
) : OrderEvent
```

### 이벤트 저장 테이블 스키마

```sql
CREATE TABLE order_events (
    id          BIGSERIAL       PRIMARY KEY,
    order_id    VARCHAR(36)     NOT NULL,
    event_type  VARCHAR(100)    NOT NULL,
    version     BIGINT          NOT NULL,
    payload     JSONB           NOT NULL,
    metadata    JSONB,                        -- 요청자 IP, 채널, trace_id 등
    occurred_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (order_id, version)               -- 낙관적 잠금
);

CREATE INDEX idx_order_events_order_id ON order_events (order_id, version);
```

---

## 3. 애그리거트(Aggregate)와 이벤트 재생(Replay)

### Order 애그리거트

```kotlin
data class OrderAggregate(
    val orderId: String,
    val memberId: String,
    val symbol: String,
    val side: OrderSide,
    val originalQty: Int,
    val currentQty: Int,
    val filledQty: Int,
    val status: OrderStatus,
    val weightedAvgPrice: BigDecimal,
    val version: Long,
    val uncommittedEvents: List<OrderEvent> = emptyList(),
) {
    companion object {
        /** 이벤트 스트림으로 애그리거트를 재구성한다 */
        fun reconstitute(events: List<OrderEvent>): OrderAggregate {
            require(events.isNotEmpty()) { "이벤트가 없으면 애그리거트를 복원할 수 없다" }
            val first = events.first() as OrderPlaced
            val initial = OrderAggregate(
                orderId        = first.orderId,
                memberId       = first.memberId,
                symbol         = first.symbol,
                side           = first.side,
                originalQty    = first.qty,
                currentQty     = first.qty,
                filledQty      = 0,
                status         = OrderStatus.ACCEPTED,
                weightedAvgPrice = BigDecimal.ZERO,
                version        = first.version,
            )
            return events.drop(1).fold(initial) { agg, event -> agg.apply(event) }
        }
    }

    fun apply(event: OrderEvent): OrderAggregate = when (event) {
        is OrderPartiallyFilled -> {
            val totalCost = weightedAvgPrice * filledQty.toBigDecimal() +
                            event.executionPrice * event.filledQty.toBigDecimal()
            val newFilled = filledQty + event.filledQty
            copy(
                filledQty        = newFilled,
                weightedAvgPrice = totalCost / newFilled.toBigDecimal(),
                status           = OrderStatus.PARTIALLY_FILLED,
                version          = event.version,
            )
        }
        is OrderFullyFilled -> copy(
            filledQty        = filledQty + event.filledQty,
            weightedAvgPrice = event.executionPrice,
            status           = OrderStatus.FILLED,
            version          = event.version,
        )
        is OrderModified  -> copy(currentQty = event.newQty, version = event.version)
        is OrderCancelled -> copy(status = OrderStatus.CANCELLED, version = event.version)
        else              -> this
    }
}
```

---

## 4. 스냅샷(Snapshot) 전략

이벤트가 수천 건 쌓이면 매번 전체를 재생하는 비용이 커진다.

```
ASCII: 스냅샷 + 증분 이벤트 재생

버전 1~499 이벤트  → [스냅샷 v500] → 버전 501~현재 이벤트 → 현재 상태
                                        ↑ 이 구간만 재생
```

### 스냅샷 저장소

```kotlin
@Entity
@Table(name = "order_snapshots")
data class OrderSnapshot(
    @Id val orderId: String,
    val snapshotVersion: Long,
    @Column(columnDefinition = "jsonb")
    val payload: String,          // JSON 직렬화된 OrderAggregate
    val createdAt: Instant,
)

@Repository
interface OrderSnapshotRepository : JpaRepository<OrderSnapshot, String>

// 500 이벤트마다 스냅샷 생성
const val SNAPSHOT_THRESHOLD = 500

fun loadAggregate(orderId: String): OrderAggregate {
    val snapshot = snapshotRepo.findById(orderId).orElse(null)
    val fromVersion = snapshot?.snapshotVersion ?: 0L
    val events = eventStore.loadEvents(orderId, afterVersion = fromVersion)

    return if (snapshot != null) {
        val base = objectMapper.readValue<OrderAggregate>(snapshot.payload)
        events.fold(base) { agg, e -> agg.apply(e) }
    } else {
        OrderAggregate.reconstitute(events)
    }
}
```

---

## 5. 감사추적(Audit Trail)과 규제 친화성

### MiFID II (Markets in Financial Instruments Directive II)

유럽 금융 규제인 MiFID II는 주문의 전 생애주기(Life Cycle)를 최소 5년간 보존하도록 의무화한다.  
이벤트 소싱은 이를 **구조적으로** 만족한다.

| 규제 요구사항 | CRUD 방식 | 이벤트 소싱 |
|---------------|-----------|-------------|
| 주문 정정 이력 | 별도 이력 테이블 수동 구현 | OrderModified 이벤트로 자동 기록 |
| 체결 단계별 기록 | 체결 건별 별도 테이블 필요 | OrderPartiallyFilled 이벤트 스트림 |
| 특정 시점 재구성 | 불가 (이미 덮어씌워짐) | 해당 시점 버전까지 Replay |
| 변경 주체 기록 | metadata 컬럼 수동 관리 | 이벤트 metadata 필드에 내장 |

### K-IFRS (한국 국제회계기준)

금융상품 공정가치 측정 시 특정 기준일의 포지션 재계산이 필요하다.  
이벤트 스트림이 있으면 임의 시점 `T`의 포지션을 `events WHERE occurred_at <= T` 로 재생해 얻을 수 있다.

---

## 6. CQRS: 명령과 조회의 책임 분리

### 왜 분리하는가

이벤트 소싱만으로는 "현재 잔고", "미체결 주문 목록" 같은 조회에 매번 Replay를 해야 한다.  
CQRS는 **쓰기 모델(Write Model)**과 **읽기 모델(Read Model)**을 완전히 분리해 이 문제를 해결한다.

```
┌─────────────────────────────────────────────────────────┐
│                      클라이언트                          │
└────────────┬──────────────────────┬─────────────────────┘
             │ Command              │ Query
             ▼                      ▼
┌────────────────────┐  ┌──────────────────────────────────┐
│   Command Handler  │  │        Query Handler             │
│  (애그리거트 로드,  │  │   (읽기 모델 DB에서 직접 조회)    │
│   이벤트 발행)     │  │                                  │
└────────┬───────────┘  └──────────────┬───────────────────┘
         │                             │
         ▼                             ▼
┌────────────────────┐  ┌──────────────────────────────────┐
│   Event Store      │  │      Read Model Store            │
│  (order_events)    │─▶│  (Redis / PostgreSQL / ES)       │
└────────────────────┘  └──────────────────────────────────┘
         │ 이벤트 발행
         ▼
┌────────────────────┐
│  Event Processor   │ ← Projection 업데이트
│  (Kafka Consumer)  │
└────────────────────┘
```

### Command Handler 예제

```kotlin
@Service
@Transactional
class PlaceOrderCommandHandler(
    private val eventStore: OrderEventStore,
    private val snapshotRepo: OrderSnapshotRepository,
    private val kafkaProducer: KafkaTemplate<String, OrderEvent>,
) {
    fun handle(cmd: PlaceOrderCommand): String {
        // 1. 비즈니스 유효성 검증
        val orderId = UUID.randomUUID().toString()
        val event = OrderPlaced(
            orderId     = orderId,
            occurredAt  = Instant.now(),
            version     = 1L,
            memberId    = cmd.memberId,
            symbol      = cmd.symbol,
            side        = cmd.side,
            orderType   = cmd.orderType,
            qty         = cmd.qty,
            limitPrice  = cmd.limitPrice,
        )

        // 2. 이벤트 저장 (원자적)
        eventStore.save(event)

        // 3. Kafka 발행 (읽기 모델 프로젝션 트리거)
        kafkaProducer.send("order-events", orderId, event)

        return orderId
    }
}
```

---

## 7. 읽기 모델 프로젝션(Projection): 잔고·포지션 뷰

### 포지션 뷰 읽기 모델

```kotlin
@Entity
@Table(name = "position_view")
data class PositionView(
    @Id val memberId: String,
    val symbol: String,
    val netQty: Int,                         // 순보유수량
    val avgCostPrice: BigDecimal,            // 평균 매입단가
    val unrealizedPnl: BigDecimal,           // 미실현 손익
    val lastUpdatedAt: Instant,
)

@Component
class PositionProjector(
    private val positionViewRepo: PositionViewRepository,
) {
    @KafkaListener(topics = ["order-events"], groupId = "position-projector")
    fun on(event: OrderEvent) {
        when (event) {
            is OrderFullyFilled, is OrderPartiallyFilled -> updatePosition(event)
            else -> Unit
        }
    }

    private fun updatePosition(event: OrderEvent) {
        val (memberId, symbol, qty, price) = extractFillInfo(event)
        val view = positionViewRepo.findByMemberIdAndSymbol(memberId, symbol)
            ?: PositionView(memberId, symbol, 0, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now())

        val newQty = view.netQty + qty         // SELL이면 음수
        val newAvg = if (newQty == 0) BigDecimal.ZERO
                     else (view.avgCostPrice * view.netQty.toBigDecimal() + price * qty.toBigDecimal()) /
                          newQty.toBigDecimal()

        positionViewRepo.save(view.copy(netQty = newQty, avgCostPrice = newAvg, lastUpdatedAt = Instant.now()))
    }
}
```

### Query Handler: 읽기 모델에서 직접 조회

```kotlin
@Service
class PositionQueryService(
    private val positionViewRepo: PositionViewRepository,
) {
    // 이벤트 Replay 없이 읽기 모델에서 O(1) 조회
    fun getPositions(memberId: String): List<PositionView> =
        positionViewRepo.findAllByMemberId(memberId)
}
```

---

## 8. 최종적 일관성(Eventual Consistency)과 함정

### 타임라인

```
t=0ms   주문 체결 이벤트 발행 (Event Store 저장)
t=5ms   Kafka 메시지 발행
t=50ms  Projection Consumer 수신
t=52ms  읽기 모델(position_view) 업데이트 완료
         ↑ 이 구간 동안 사용자가 잔고를 조회하면 구 잔고가 보인다
```

### 함정 1: 읽기 지연으로 인한 사용자 혼란

```
사용자: "방금 팔았는데 왜 잔고가 그대로야?"
```

**완화 전략**:
- **Read-Your-Writes**: 명령 직후 응답에 `commandId`를 포함하고, 클라이언트가 `commandId`가 프로젝션에 반영될 때까지 폴링.
- **Optimistic UI**: 클라이언트 로컬 상태를 즉시 반영, 프로젝션과 동기화 후 최종 확정.
- **동기 프로젝션 옵션**: 중요한 읽기는 Event Store에서 직접 Replay하는 **강일관성 경로** 제공.

### 함정 2: 프로젝션 재구성 중 서비스 중단

프로젝션 로직을 변경하면 읽기 모델을 처음부터 재구성해야 한다.  
해결책: **블루/그린 프로젝션** — 새 읽기 모델 테이블에 백그라운드 재구성 후 트래픽 전환.

### 함정 3: 프로젝션 실패 시 데이터 불일치

Kafka Consumer 실패 시 메시지 재처리로 중복 적용 가능.  
해결책: 모든 프로젝션 업데이트를 **멱등(Idempotent)** 하게 설계 — `event.version`을 기록하고 이미 처리된 버전은 건너뜀.

---

## 9. 이벤트 버전관리와 스키마 진화 (Upcaster 패턴)

이벤트는 영구 저장되므로 스키마 변경이 까다롭다.

### 문제 시나리오

```kotlin
// v1 — 초기 설계
data class OrderPlaced_v1(val qty: Int, val price: BigDecimal)

// v2 — `limitPrice`로 필드명 변경, `orderType` 추가
data class OrderPlaced_v2(val qty: Int, val limitPrice: BigDecimal?, val orderType: OrderType)
```

### Upcaster — 구버전 이벤트를 신버전으로 변환

```kotlin
interface EventUpcaster<OLD : OrderEvent, NEW : OrderEvent> {
    fun upcast(old: OLD): NEW
}

class OrderPlacedV1ToV2Upcaster : EventUpcaster<OrderPlaced_v1, OrderPlaced_v2> {
    override fun upcast(old: OrderPlaced_v1) = OrderPlaced_v2(
        qty        = old.qty,
        limitPrice = old.price,       // 필드명 매핑
        orderType  = OrderType.LIMIT, // 기존은 모두 지정가로 간주
    )
}

// 이벤트 로드 시 Upcaster 체인 적용
fun loadAndUpcast(orderId: String): List<OrderEvent> =
    eventStore.loadRaw(orderId).map { raw ->
        when (raw.eventType) {
            "OrderPlaced_v1" -> upcaster.upcast(objectMapper.readValue<OrderPlaced_v1>(raw.payload))
            else             -> objectMapper.readValue(raw.payload, Class.forName(raw.eventType))
        }
    }
```

---

## 10. Kafka와의 연계 — 이벤트 스토어 vs. 메시지 버스

> 15-event-driven.md에서 Kafka의 기본 개념과 토픽 설계를 다뤘다.  
> 여기서는 **이벤트 소싱 맥락에서** Kafka를 어떻게 위치시킬지를 다룬다.

### 역할 비교

| 관점 | 전용 Event Store (PostgreSQL/EventStoreDB) | Kafka를 Event Store로 |
|------|-------------------------------------------|----------------------|
| 장점 | 순서 보장, 낙관적 잠금, 스냅샷 | 고처리량, 컨슈머 그룹, 확장성 |
| 단점 | 확장성 제한, 별도 인프라 | 무한 보존 비용, 임의 이벤트 조회 어려움 |
| 금융 적합성 | 이벤트 순서·일관성이 중요한 주문 도메인 | 고빈도 시세·체결 스트림 전파 |

**권장 패턴 (하이브리드)**:

```
Command ──▶ [Event Store: PostgreSQL]  ← 진실의 원천(Source of Truth)
                      │
                      │ CDC (Debezium) 또는 Transactional Outbox
                      ▼
               [Kafka Topic]           ← 프로젝션·외부 서비스 전파
                  │         │
                  ▼         ▼
          [Position      [Notification
           Projector]     Service]
```

Transactional Outbox 패턴을 쓰면 Event Store 저장과 Kafka 발행의 **원자성**을 데이터베이스 트랜잭션으로 보장한다(15-event-driven.md § Outbox 참조).

---

## 11. 도입 트레이드오프

### 언제 이벤트 소싱 + CQRS가 맞는가

```
✅ 도입 권장
  - 금융 거래 감사 이력이 규제 의무인 경우
  - 시간 역행(Time Travel) 조회가 필요한 경우
  - 쓰기·읽기 부하가 극단적으로 다른 경우 (주문 입력 vs. 잔고 조회)
  - 이벤트 기반 마이크로서비스 간 통신이 이미 설계된 경우

❌ 도입 주의
  - 단순 CRUD 관리 도메인 (회원 주소 변경 등)
  - 팀이 Eventually Consistent 모델에 익숙하지 않은 경우
  - 빠른 MVP 프로토타입이 목표인 경우
  - 읽기/쓰기 분리가 오버엔지니어링인 소규모 서비스
```

### 복잡성 증가 항목

| 항목 | 설명 |
|------|------|
| 이벤트 스키마 진화 | Upcaster 체인 유지 비용 |
| 프로젝션 재구성 | 로직 변경 시 전체 재빌드 필요 |
| 디버깅 | 현재 상태가 Replay 결과이므로 추적 복잡 |
| 트랜잭션 경계 | 애그리거트 단위 제한 → 복수 애그리거트 사가(Saga) 필요 |
| 팀 학습 비용 | DDD, 이벤트 모델링, Eventually Consistent 사고방식 전환 |

---

## 12. 구현 체크리스트

```
이벤트 설계
  [ ] 모든 이벤트가 과거형 동사로 명명됐는가 (OrderPlaced ✓, PlaceOrder ✗)
  [ ] 이벤트에 version 필드가 있는가
  [ ] 이벤트 metadata에 trace_id, actor, channel이 포함됐는가
  [ ] 이벤트 페이로드에 금액 필드가 BigDecimal인가

Event Store
  [ ] (order_id, version) UNIQUE 제약으로 낙관적 잠금이 구현됐는가
  [ ] 스냅샷 임계치가 설정됐는가
  [ ] 이벤트 payload가 JSONB로 저장되고 인덱싱됐는가

CQRS
  [ ] Command와 Query가 서로 다른 서비스/모듈로 분리됐는가
  [ ] 읽기 모델 업데이트가 멱등하게 구현됐는가
  [ ] 프로젝션 재구성 절차가 문서화됐는가

Eventual Consistency 대응
  [ ] Read-Your-Writes 전략이 구현됐는가
  [ ] 프로젝션 지연 모니터링 메트릭이 있는가 (lag)
  [ ] 프로젝션 실패 시 Dead Letter Queue(DLQ)가 설정됐는가

스키마 진화
  [ ] Upcaster가 등록됐는가
  [ ] 이벤트 타입별 버전 네이밍 규칙이 정해졌는가
  [ ] 이전 버전 이벤트의 역호환성 테스트가 있는가

규제 대응
  [ ] 이벤트가 삭제되지 않도록 DELETE 권한이 제한됐는가
  [ ] 이벤트 보존 기간이 규제 요건(MiFID II 5년, K-IFRS 10년 등)에 맞게 설정됐는가
  [ ] 개인정보(PII) 이벤트의 암호화 전략이 수립됐는가
```

---

## 13. 대안과 비교

| 전략 | 적합 상황 | 한계 |
|------|-----------|------|
| **이벤트 소싱 + CQRS** | 금융·감사·복잡 도메인 | 높은 복잡성 |
| **이벤트 소싱만 적용** | 감사 이력 필요, 단순 조회 | 조회 성능 제한 |
| **CQRS만 적용 (이벤트 소싱 없이)** | 읽기·쓰기 부하 차이만 존재 | 시간여행·감사 불가 |
| **Change Data Capture (CDC)** | 기존 CRUD 시스템에 이벤트 스트림 추가 | 이벤트 의미론 희박 |
| **Audit Log 테이블** | 간단한 변경 이력 | 쿼리 복잡, 재생 불가 |

---

이전: [37. 저지연 시스템 설계](37-low-latency.md) · 다음: [39. 분산 시스템과 일관성](39-distributed-systems.md) · [전체 커리큘럼](../CURRICULUM.md)
