# 31. 주문 유형 전체 (Order Types)

> **선행 학습**: [30. 시장 구조와 매매제도](30-market-structure), [04. 증권 도메인 입문](04-securities-domain)

---

## 1. 주문이란 무엇인가

**주문(Order)**은 투자자가 특정 종목을 특정 조건으로 매수하거나 매도하겠다는 의사 표시다. 거래소의 호가창(Order Book)에 등록되어 상대방 주문과 매칭(체결)되거나, 조건이 맞지 않아 취소된다. 백엔드 관점에서 주문은 **상태(state)**를 가지는 도메인 객체이며, 전이 흐름과 검증 로직이 시스템 신뢰도를 결정한다.

---

## 2. 주문 유형 (Order Types)

### 2.1 가격 지정 방식에 따른 분류

#### 지정가 주문 (Limit Order)

투자자가 **최대 매수가(또는 최소 매도가)를 직접 지정**하는 주문이다.

- 매수 지정가: 지정 가격 이하에서 체결
- 매도 지정가: 지정 가격 이상에서 체결
- 지정 가격에 거래상대가 없으면 호가창에 대기

```kotlin
data class LimitOrder(
    val price: BigDecimal,    // 지정 가격
    val quantity: Int,
    val side: OrderSide       // BUY / SELL
)
```

**장점**: 원하지 않는 가격에 체결되지 않음  
**단점**: 시장 상황에 따라 체결 안 될 수 있음

#### 시장가 주문 (Market Order)

**가격 지정 없이 현재 시장 최우선 가격으로 즉시 체결**을 요청하는 주문이다.

- 매수 시장가: 매도 호가 중 가장 낮은 가격부터 체결
- 매도 시장가: 매수 호가 중 가장 높은 가격부터 체결
- 잔량이 부족하면 다음 가격대로 넘어가며 체결 (슬리피지 발생)

**개발 시 주의**: 시장가 주문은 정규장에서만 허용된다. 동시호가 세션, 시간외 단일가에서는 수신 불가다. 세션 × 주문 유형 검증 매트릭스를 반드시 구현해야 한다.

#### 조건부 지정가 주문 (Conditional Limit Order)

정규장 중 지정가 주문으로 시작하여, **정규장 종료(15:20) 시 미체결 잔량이 있으면 자동으로 장마감 단일가(동시호가)로 전환**되는 주문이다.

```
정규장 중 → [지정가 주문] → 15:20 이후 미체결 잔량 →
  장마감 동시호가에서 [단일가 주문]으로 자동 전환 → 15:30 체결 시도
```

**개발 시 주의**: 조건부 지정가는 15:20에 자동 전환 배치가 필요하다. 원주문 ID를 유지하면서 상태를 전환해야 하므로 이력 추적이 복잡해진다.

#### 최유리 지정가 주문 (Best Limit Order)

주문 **접수 시점의 상대방 최우선 호가 가격으로 자동 지정**되는 주문이다.

- 매수 최유리: 접수 시점 매도 1호가 가격으로 지정
- 매도 최유리: 접수 시점 매수 1호가 가격으로 지정

```
상황: 매도 1호가 = 10,100원
투자자: 매수 최유리 주문 → 시스템: 10,100원 지정가 주문으로 변환
```

#### 최우선 지정가 주문 (Best Priority Limit Order)

**자신(투자자) 최우선 호가 가격**으로 지정되는 주문이다.

- 매수 최우선: 현재 매수 1호가 가격으로 지정 (가장 유리한 대기 위치)
- 매도 최우선: 현재 매도 1호가 가격으로 지정

> 최유리 vs 최우선의 차이: **최유리**는 상대방 최우선 가격 → 즉시 체결 가능성 높음. **최우선**은 자기 편 최우선 가격 → 대기 중 호가이므로 즉시 체결이 아닐 수 있음.

### 2.2 체결 조건에 따른 분류

#### IOC (Immediate Or Cancel)

주문 접수 즉시 체결 가능한 수량만 체결하고, **미체결 잔량은 즉시 자동 취소**되는 주문이다.

```
매수 IOC 1,000주 → 500주 체결 → 잔여 500주 즉시 취소
                 → 0주 체결   → 전량 취소
```

#### FOK (Fill Or Kill)

주문 접수 즉시 **전량 체결이 가능한 경우에만 체결**하고, 전량 체결이 불가하면 전량 취소하는 주문이다.

```
매수 FOK 1,000주 → 매도 잔량 1,000주 이상 → 전량 체결
                 → 매도 잔량 999주 이하   → 전량 취소 (1주도 체결 안 됨)
```

#### 일반 주문 (GTC 개념, Good Till Cancel)

체결 또는 명시적 취소까지 유효한 주문이다. 한국 거래소는 기본적으로 **당일 유효(Day Order)** 정책을 적용하므로, 장 종료 시 미체결 주문은 자동 소멸된다.

| 구분 | IOC | FOK | 일반(Day) |
|------|-----|-----|-----------|
| 부분체결 | 허용 | 불허 | 허용 |
| 미체결 잔량 | 즉시 취소 | 즉시 전량 취소 | 당일 장 종료 시 소멸 |
| 주 사용처 | 기관, 알고리즘 | 대형 주문 확실성 | 일반 투자자 |

---

## 3. 예약주문 (Pre-Order / Scheduled Order)

**예약주문**은 거래소 정규 거래 시간 외(야간 등)에 투자자가 주문을 등록해두면, 다음 거래일 또는 지정 시간에 자동으로 주문을 전송하는 서비스다.

### 3.1 예약주문 처리 흐름

```
투자자 예약주문 등록
    │
    ▼
예약주문 DB 저장 (status: SCHEDULED)
    │
    ▼
[스케줄러] 장 시작 전 또는 지정 시간 도래
    │
    ▼
예약주문 → 실주문 변환 → 거래소 전송
    │
    ▼
체결/거부 결과 수신 → 예약주문 status 업데이트 (EXECUTED / FAILED)
```

### 3.2 개발 시 주의

```kotlin
data class ScheduledOrder(
    val id: Long,
    val investorId: Long,
    val stockCode: String,
    val orderType: OrderType,
    val price: BigDecimal?,          // 지정가의 경우 필수
    val quantity: Int,
    val side: OrderSide,
    val scheduledAt: LocalDateTime,  // 예약 실행 시각
    val status: ScheduledOrderStatus,
    val executedOrderId: Long?       // 실행된 주문 ID
)

enum class ScheduledOrderStatus {
    SCHEDULED,   // 예약 대기
    EXECUTING,   // 실행 중 (거래소 전송)
    EXECUTED,    // 실행 완료
    FAILED,      // 실행 실패
    CANCELLED    // 투자자 취소
}
```

- 예약주문은 잔고/증거금을 **예약 등록 시점이 아닌 실행 시점**에 확인해야 한다.
- 동일 투자자의 예약주문 중복 실행 방지 (멱등성 보장) 로직이 필요하다.

---

## 4. 주문 상태 전이도 (Order State Machine)

### 4.1 상태 정의

```kotlin
enum class OrderStatus {
    PENDING,           // 접수 대기 (시스템 내부)
    SUBMITTED,         // 거래소 전송 완료
    CONFIRMED,         // 거래소 확인 (접수 승인)
    PARTIALLY_FILLED,  // 부분 체결
    FILLED,            // 전량 체결
    REJECTED,          // 거부 (거래소 또는 사전 검증)
    CANCELLING,        // 취소 요청 중
    CANCELLED,         // 취소 완료
    EXPIRED            // 만료 (장 종료 시 소멸)
}
```

### 4.2 상태 전이 다이어그램

```
                   ┌─────────┐
                   │ PENDING │ (투자자 주문 접수)
                   └────┬────┘
                        │ 사전 검증 실패
                        ├──────────────────► REJECTED
                        │
                        ▼ 거래소 전송
                   ┌───────────┐
                   │ SUBMITTED │
                   └─────┬─────┘
                         │ 거래소 거부
                         ├──────────────────► REJECTED
                         │
                         ▼ 거래소 접수 승인
                   ┌───────────┐
                   │ CONFIRMED │◄──────────────────────┐
                   └─────┬─────┘                       │
                         │                             │ 정정 승인 후
                   ┌─────┴───────────┐                 │
                   │                 │                 │
                   ▼ 일부 체결        ▼ 전량 체결        │
         ┌──────────────────┐  ┌────────┐             │
         │ PARTIALLY_FILLED │  │ FILLED │             │
         └──────────┬───────┘  └────────┘             │
                    │ 잔량 취소   잔량 추가 체결          │
                    │            │                    │
              ┌─────▼──────┐    │        ┌────────────┴──┐
              │ CANCELLING │    │        │ 정정 요청 처리   │
              └─────┬──────┘    │        └───────────────┘
                    │           │
                    ▼           ▼
               ┌──────────┐  ┌────────┐
               │CANCELLED │  │ FILLED │
               └──────────┘  └────────┘
                    ▲
                    │ 장 종료
               ┌────┴───┐
               │EXPIRED │ (미체결 소멸)
               └────────┘
```

### 4.3 상태 전이 sealed class 모델링

```kotlin
sealed class OrderEvent {
    object Submitted : OrderEvent()
    data class ExchangeConfirmed(val exchangeOrderId: String) : OrderEvent()
    data class ExchangeRejected(val rejectCode: String, val reason: String) : OrderEvent()
    data class PartiallyFilled(
        val filledQuantity: Int,
        val fillPrice: BigDecimal,
        val filledAt: Instant
    ) : OrderEvent()
    data class FullyFilled(
        val filledQuantity: Int,
        val fillPrice: BigDecimal,
        val filledAt: Instant
    ) : OrderEvent()
    object CancelRequested : OrderEvent()
    object CancelConfirmed : OrderEvent()
    object Expired : OrderEvent()
    data class AmendConfirmed(val newPrice: BigDecimal?, val newQuantity: Int?) : OrderEvent()
}

fun OrderStatus.transition(event: OrderEvent): OrderStatus {
    return when (this) {
        OrderStatus.PENDING -> when (event) {
            is OrderEvent.Submitted -> OrderStatus.SUBMITTED
            is OrderEvent.ExchangeRejected -> OrderStatus.REJECTED
            else -> throw IllegalStateException("Invalid transition: $this + $event")
        }
        OrderStatus.SUBMITTED -> when (event) {
            is OrderEvent.ExchangeConfirmed -> OrderStatus.CONFIRMED
            is OrderEvent.ExchangeRejected  -> OrderStatus.REJECTED
            else -> throw IllegalStateException("Invalid transition: $this + $event")
        }
        OrderStatus.CONFIRMED -> when (event) {
            is OrderEvent.PartiallyFilled   -> OrderStatus.PARTIALLY_FILLED
            is OrderEvent.FullyFilled       -> OrderStatus.FILLED
            is OrderEvent.CancelRequested   -> OrderStatus.CANCELLING
            is OrderEvent.AmendConfirmed    -> OrderStatus.CONFIRMED
            is OrderEvent.Expired           -> OrderStatus.EXPIRED
            else -> throw IllegalStateException("Invalid transition: $this + $event")
        }
        OrderStatus.PARTIALLY_FILLED -> when (event) {
            is OrderEvent.FullyFilled       -> OrderStatus.FILLED
            is OrderEvent.CancelRequested   -> OrderStatus.CANCELLING
            is OrderEvent.PartiallyFilled   -> OrderStatus.PARTIALLY_FILLED
            is OrderEvent.Expired           -> OrderStatus.EXPIRED
            else -> throw IllegalStateException("Invalid transition: $this + $event")
        }
        OrderStatus.CANCELLING -> when (event) {
            is OrderEvent.CancelConfirmed   -> OrderStatus.CANCELLED
            is OrderEvent.FullyFilled       -> OrderStatus.FILLED  // 취소 전 체결
            else -> throw IllegalStateException("Invalid transition: $this + $event")
        }
        else -> throw IllegalStateException("Terminal state cannot transition: $this")
    }
}
```

---

## 5. 정정과 취소 (Order Amendment & Cancellation)

### 5.1 정정 가능 항목

| 항목 | 정정 가능 여부 | 비고 |
|------|--------------|------|
| 가격 (Price) | ✅ 가능 | 체결 전 잔량에 대해 |
| 수량 (감소) | ✅ 가능 | 증가는 불가 (새 주문으로) |
| 수량 (증가) | ❌ 불가 | 신규 주문으로 처리해야 함 |
| 종목코드 | ❌ 불가 | 취소 후 재주문 필요 |
| 매수/매도 방향 | ❌ 불가 | 취소 후 재주문 필요 |
| 주문 유형 | 제한적 | 사내 정책에 따름 |

> ※ 정정 규칙은 거래소 규정 및 사내 정책에 따라 다를 수 있으므로 사내 기준 확인 필요.

### 5.2 정정 처리 흐름

```
투자자: 주문 정정 요청 (원주문 ID + 변경 내용)
    │
    ▼
사전 검증:
  - 원주문이 CONFIRMED 또는 PARTIALLY_FILLED 상태인지
  - 이미 취소/체결 완료 여부
  - 수량 감소인지 (증가 불가)
    │
    ▼
거래소 정정 전문 전송
    │
    ├── 거래소 승인 → 원주문 업데이트 (이력 저장)
    └── 거래소 거부 → 거부 사유 투자자 통보
```

### 5.3 원주문-정정주문 추적

```kotlin
data class OrderAmendment(
    val amendId: Long,
    val originalOrderId: Long,       // 원주문 ID
    val requestedAt: Instant,
    val amendType: AmendType,
    val previousPrice: BigDecimal?,
    val newPrice: BigDecimal?,
    val previousQuantity: Int,
    val newQuantity: Int,
    val status: AmendStatus,
    val exchangeAmendId: String?     // 거래소 정정번호
)

enum class AmendType {
    PRICE_CHANGE,
    QUANTITY_DECREASE,
    PRICE_AND_QUANTITY
}

enum class AmendStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}
```

**중요**: 정정 이력은 감사(Audit) 목적으로 삭제하지 않고 영구 보관해야 한다. 분쟁 발생 시 원주문~정정~체결 전 과정을 재현할 수 있어야 한다.

### 5.4 취소 처리

취소는 정정보다 단순하나, **취소 도중 체결이 발생하는 Race Condition**이 핵심 난제다.

```kotlin
// 취소 요청과 체결이 동시에 발생하는 경우 처리
fun handleCancelOrFill(order: Order, event: OrderEvent): Order {
    return when {
        order.status == OrderStatus.CANCELLING && event is OrderEvent.FullyFilled -> {
            // 취소 요청 중에 전량 체결된 경우 → FILLED로 처리
            order.copy(status = OrderStatus.FILLED)
        }
        order.status == OrderStatus.CANCELLING && event is OrderEvent.CancelConfirmed -> {
            order.copy(status = OrderStatus.CANCELLED)
        }
        else -> order.copy(status = order.status.transition(event))
    }
}
```

---

## 6. 체결 통보 처리 (Fill Notification)

### 6.1 체결 통보 데이터

거래소로부터 체결 통보(Execution Report) 수신 시 처리해야 할 주요 정보:

```kotlin
data class ExecutionReport(
    val exchangeOrderId: String,     // 거래소 주문번호
    val clientOrderId: String,       // 증권사 주문번호 (ClOrdID)
    val stockCode: String,
    val executionId: String,         // 체결번호
    val executionType: ExecutionType,
    val filledQuantity: Int,         // 이번 체결 수량
    val filledPrice: BigDecimal,     // 이번 체결 가격
    val cumulativeFilledQty: Int,    // 누적 체결 수량
    val remainingQuantity: Int,      // 미체결 잔량
    val executedAt: Instant,         // 체결 시각
    val side: OrderSide
)

enum class ExecutionType {
    NEW,           // 거래소 접수 확인
    PARTIAL_FILL,  // 부분 체결
    FILL,          // 전량 체결
    CANCELLED,     // 취소 확인
    REJECTED,      // 거부
    AMENDED        // 정정 확인
}
```

### 6.2 체결 후 처리 파이프라인

```
체결 통보 수신 (거래소 → 증권사 서버)
    │
    ▼
1. 주문 상태 업데이트 (PARTIALLY_FILLED / FILLED)
    │
    ▼
2. 체결 내역 저장 (execution log)
    │
    ▼
3. 보유 잔고 업데이트 (매수: 잔고 증가, 매도: 잔고 감소)
    │
    ▼
4. 예수금 업데이트 (→ 32. 청산·결제·정산 참조)
    │
    ▼
5. 투자자 체결 통보 (Push 알림, HTS/MTS 실시간)
    │
    ▼
6. 수수료 계산 및 기록
```

---

## 7. 주문 거부 사유 코드 (Rejection Reason Codes)

주문이 거부되는 경우 거래소 또는 내부 검증에서 코드와 함께 사유가 전달된다.

### 7.1 사전 검증 (Pre-trade) 거부 — 증권사 내부

| 코드 | 사유 | 설명 |
|------|------|------|
| PRE-001 | 잔고 부족 | 매도 주문 시 보유 수량 부족 |
| PRE-002 | 증거금 부족 | 매수 주문 시 예수금/출금가능금액 부족 |
| PRE-003 | 가격 범위 초과 | 상하한가 범위 외 가격 |
| PRE-004 | 호가단위 불일치 | 틱사이즈 미부합 가격 |
| PRE-005 | 유효하지 않은 세션 | 현재 세션에서 허용되지 않는 주문 유형 |
| PRE-006 | 수량 오류 | 0 이하 또는 매매단위 미부합 |
| PRE-007 | 위험한도 초과 | RMS 한도 초과 |

### 7.2 거래소 거부 (Post-trade Validation)

| 코드 | 사유 |
|------|------|
| EXC-001 | 가격 범위 초과 |
| EXC-002 | 상장 종목 아님 |
| EXC-003 | 거래 정지 종목 |
| EXC-004 | 주문 수량 초과 |
| EXC-005 | 시스템 오류 |

```kotlin
sealed class OrderRejection {
    abstract val code: String
    abstract val message: String

    data class PreTradeRejection(
        override val code: String,
        override val message: String,
        val validatedBy: String = "INTERNAL"
    ) : OrderRejection()

    data class ExchangeRejection(
        override val code: String,
        override val message: String,
        val exchangeCode: String    // 거래소 원본 거부 코드
    ) : OrderRejection()
}
```

---

## 8. 단일가/접속매매에서의 주문 처리 차이

| 항목 | 접속매매 (Continuous) | 단일가 (Call Auction) |
|------|----------------------|----------------------|
| 체결 시점 | 즉시 (상대 호가 있으면) | 누적 후 특정 시점에 일괄 |
| 허용 주문 유형 | 지정가, 시장가, 최유리, 최우선 등 | 지정가, 시장가 (일부) |
| IOC/FOK | 허용 | 허용 안 됨 (즉시 취소 의미 없음) |
| 체결가 | 매수·매도 호가 교차 가격 | 단일 균형 가격 (최대 체결량) |
| 부분 체결 | 즉시 발생 가능 | 일괄 체결이라 같은 주문이 한 가격에 체결 |

### 8.1 개발 시 주의 — 세션별 주문 유형 허용 매트릭스

```kotlin
object OrderTypeValidator {

    private val sessionOrderTypeMatrix: Map<TradingSession, Set<OrderType>> = mapOf(
        TradingSession.PRE_MARKET_SINGLE_PRICE to setOf(
            OrderType.LIMIT  // 장전 시간외: 지정가만 (종가 동일가)
        ),
        TradingSession.PRE_MARKET_SIMULTANEOUS to setOf(
            OrderType.LIMIT, OrderType.MARKET
        ),
        TradingSession.REGULAR to setOf(
            OrderType.LIMIT, OrderType.MARKET,
            OrderType.CONDITIONAL_LIMIT,
            OrderType.BEST_LIMIT, OrderType.BEST_PRIORITY_LIMIT
        ),
        TradingSession.CLOSING_SIMULTANEOUS to setOf(
            OrderType.LIMIT, OrderType.MARKET
        ),
        TradingSession.AFTER_MARKET_SINGLE_PRICE to setOf(
            OrderType.LIMIT  // 장후 시간외: 지정가만 (당일 종가)
        )
    )

    fun isAllowed(session: TradingSession, orderType: OrderType): Boolean {
        return sessionOrderTypeMatrix[session]?.contains(orderType) ?: false
    }
}

enum class OrderType {
    LIMIT,                  // 지정가
    MARKET,                 // 시장가
    CONDITIONAL_LIMIT,      // 조건부 지정가
    BEST_LIMIT,             // 최유리 지정가
    BEST_PRIORITY_LIMIT     // 최우선 지정가
}
```

---

## 9. 주문 도메인 모델 종합

```kotlin
data class Order(
    val id: Long,
    val clientOrderId: String,          // 증권사 주문번호 (거래소 전송용)
    val exchangeOrderId: String?,       // 거래소 주문번호 (확인 후 채움)
    val investorId: Long,
    val accountId: Long,
    val stockCode: String,
    val market: MarketType,             // KOSPI / KOSDAQ / KONEX
    val side: OrderSide,                // BUY / SELL
    val orderType: OrderType,
    val timeInForce: TimeInForce,       // DAY / IOC / FOK
    val price: BigDecimal?,             // 지정가 시 필수, 시장가 시 null
    val quantity: Int,                  // 주문 수량
    val filledQuantity: Int = 0,        // 체결 수량
    val remainingQuantity: Int = quantity,
    val status: OrderStatus,
    val session: TradingSession,        // 접수 당시 세션
    val submittedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val amendments: List<OrderAmendment> = emptyList(),
    val executions: List<ExecutionReport> = emptyList()
) {
    val isTerminal: Boolean
        get() = status in setOf(
            OrderStatus.FILLED,
            OrderStatus.CANCELLED,
            OrderStatus.REJECTED,
            OrderStatus.EXPIRED
        )

    val averageFillPrice: BigDecimal?
        get() = if (executions.isEmpty()) null
        else executions
            .filter { it.executionType in setOf(ExecutionType.PARTIAL_FILL, ExecutionType.FILL) }
            .fold(BigDecimal.ZERO to 0) { (totalAmount, totalQty), exec ->
                exec.filledPrice.multiply(BigDecimal(exec.filledQuantity)).add(totalAmount) to
                totalQty + exec.filledQuantity
            }
            .let { (totalAmount, totalQty) ->
                if (totalQty == 0) null
                else totalAmount.divide(BigDecimal(totalQty), 2, java.math.RoundingMode.HALF_UP)
            }
}

enum class OrderSide { BUY, SELL }
enum class TimeInForce { DAY, IOC, FOK }
enum class MarketType { KOSPI, KOSDAQ, KONEX, ETF }
```

---

## 10. 개발 체크리스트

- [ ] 세션별 주문 유형 허용 매트릭스 구현 및 사전 검증 적용
- [ ] 주문 상태 전이 로직 sealed class / state machine 패턴으로 구현
- [ ] 정정 이력 영구 보관 (감사 목적)
- [ ] 취소-체결 Race Condition 처리 로직
- [ ] 체결 통보 수신 후 파이프라인 (잔고, 예수금, 알림) 비동기 처리
- [ ] 조건부 지정가 15:20 자동 전환 배치 구현
- [ ] IOC/FOK 처리: 미체결 잔량 즉시 취소 로직
- [ ] 거부 사유 코드 표준화 및 투자자 친화적 메시지 변환
- [ ] 예약주문 실행 스케줄러 및 멱등성 보장
- [ ] clientOrderId 중복 방지 (UUID 또는 시퀀스)
- [ ] 주문 원장 및 체결 원장 분리 설계

---

이전: [30. 시장 구조와 매매제도](30-market-structure) · 다음: [32. 청산·결제·정산](32-clearing-settlement) · [전체 커리큘럼](/curriculum)
