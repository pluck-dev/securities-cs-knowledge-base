# 19. 코틀린 객체지향과 설계

> **대상**: 주식 투자 경험은 있으나 코틀린/개발이 처음인 증권사 백엔드 지망자  
> **목표**: 코틀린 OOP 기능을 증권 도메인에 적용하여 안전하고 표현력 있는 도메인 모델을 설계한다

---

## 1. 클래스와 생성자

### 1.1 주 생성자 (Primary Constructor)

```kotlin
import java.math.BigDecimal

// 주 생성자: class 헤더에 바로 선언
class BrokerageAccount(
    val accountId: String,        // val: 읽기 전용 프로퍼티 자동 생성
    val ownerId: String,
    var balance: BigDecimal,      // var: 변경 가능 프로퍼티 자동 생성
    val currency: String = "KRW"  // 기본값
) {
    // init 블록: 생성 시 유효성 검사
    init {
        require(accountId.isNotBlank()) { "계좌 ID는 비어 있을 수 없습니다" }
        require(balance >= BigDecimal.ZERO) { "잔고는 0 이상이어야 합니다" }
    }
}
```

### 1.2 부 생성자 (Secondary Constructor)

```kotlin
enum class OrderType { LIMIT, MARKET, STOP, STOP_LIMIT }

class StockOrder {
    val symbol: String
    val quantity: Long
    val price: BigDecimal?
    val orderType: OrderType

    // 부 생성자 1: 지정가 주문
    constructor(symbol: String, quantity: Long, price: BigDecimal) {
        this.symbol    = symbol
        this.quantity  = quantity
        this.price     = price
        this.orderType = OrderType.LIMIT
    }

    // 부 생성자 2: 시장가 주문
    constructor(symbol: String, quantity: Long) {
        this.symbol    = symbol
        this.quantity  = quantity
        this.price     = null
        this.orderType = OrderType.MARKET
    }
}
```

> **실무 팁**: 대부분의 경우 부 생성자보다 **주 생성자 + 기본값** 조합을 선호한다.
> 부 생성자는 자바 상호운용이나 특정 프레임워크 요구사항이 있을 때 사용한다.

### 1.3 init 블록 실행 순서

```kotlin
class TradeRecord(
    val tradeId: String,
    val amount: BigDecimal
) {
    val formattedAmount: String  // 선언만

    init {
        // init 블록은 주 생성자 인자를 바로 사용 가능
        require(amount > BigDecimal.ZERO) { "체결 금액은 양수여야 합니다" }
        formattedAmount = "₩${amount}"
        println("TradeRecord 생성: $tradeId")
    }

    // 프로퍼티 초기화와 init 블록은 선언 순서대로 실행됨
    val summary = "체결 $tradeId: $formattedAmount"
}
```

---

## 2. 프로퍼티와 Backing Field

```kotlin
import java.math.BigDecimal

class Position(
    val symbol: String,
    private var _quantity: Long,
    val averageCost: BigDecimal
) {
    // 커스텀 getter — 내부 필드를 외부에 읽기 전용으로 노출
    val quantity: Long get() = _quantity

    // 커스텀 getter/setter + backing field
    var unrealizedPnl: BigDecimal = BigDecimal.ZERO
        get() = field
        set(value) {
            require(value.scale() <= 6) { "손익은 소수 6자리까지" }
            field = value
        }

    // 파생 프로퍼티 (항상 재계산)
    val totalCost: BigDecimal get() = averageCost * _quantity.toBigDecimal()

    fun addShares(qty: Long) {
        require(qty > 0) { "추가 수량은 양수여야 합니다" }
        _quantity += qty
    }
}
```

---

## 3. 가시성 제어자 (Visibility Modifiers)

| 제어자 | 클래스 멤버 | 최상위 선언 |
|--------|------------|-------------|
| `public` (기본) | 어디서든 접근 가능 | 어디서든 접근 가능 |
| `internal` | 같은 모듈 내 | 같은 모듈 내 |
| `protected` | 클래스 + 서브클래스 | 사용 불가 |
| `private` | 클래스 내부만 | 파일 내부만 |

```kotlin
class OrderBook internal constructor(     // 모듈 내부에서만 생성 가능
    private val symbol: String             // 외부 접근 불가
) {
    internal val bids: MutableList<Order> = mutableListOf()
    internal val asks: MutableList<Order> = mutableListOf()

    fun getBestBid(): BigDecimal? = bids.maxByOrNull { it.price!! }?.price
    fun getBestAsk(): BigDecimal? = asks.minByOrNull { it.price!! }?.price

    private fun validateOrder(order: Order): Boolean =
        order.quantity > 0L && (order.price?.let { it > BigDecimal.ZERO } ?: true)
}
```

---

## 4. data class

`data class`는 `equals`, `hashCode`, `toString`, `copy`, `componentN`을 자동 생성한다.

```kotlin
import java.time.Instant

enum class TradeSide { BUY, SELL }

data class Trade(
    val tradeId: String,
    val symbol: String,
    val quantity: Long,
    val price: BigDecimal,
    val side: TradeSide,
    val executedAt: Instant
)

// 자동 생성 기능 활용
val trade1 = Trade("T001", "SAMSUNG", 10L, BigDecimal("75300"), TradeSide.BUY, Instant.now())
val trade2 = trade1.copy(tradeId = "T002", quantity = 5L)  // 일부만 바꿔 복사

// 구조분해 (componentN 덕분에 가능)
val (id, symbol, qty, price) = trade1
println("$id: $symbol ${qty}주 @$price")

// equals는 모든 주 생성자 프로퍼티 기반
println(trade1 == trade2)  // false (tradeId 다름)
```

> **함정(Gotcha)**: `data class`의 `equals`/`hashCode`는 **주 생성자 프로퍼티만** 포함한다.
> 클래스 본문에서 선언한 프로퍼티는 포함되지 않는다.
> 이 점을 모르면 컬렉션에서 중복 제거가 의도대로 동작하지 않는다.

---

## 5. 인터페이스와 기본 구현

```kotlin
import java.math.RoundingMode

interface Priceable {
    val symbol: String
    fun currentPrice(): BigDecimal

    // 기본 구현 제공 (JVM default method)
    fun priceInUSD(exchangeRate: BigDecimal): BigDecimal =
        currentPrice().divide(exchangeRate, 2, RoundingMode.HALF_UP)
}

interface Tradeable {
    fun canTrade(): Boolean
    fun minimumOrderQuantity(): Long = 1L  // 기본값: 1주
}

// 다중 인터페이스 구현
class Stock(
    override val symbol: String,
    private val exchange: String,
    private val priceService: PriceService
) : Priceable, Tradeable {
    override fun currentPrice(): BigDecimal = priceService.getLatest(symbol)
    override fun canTrade(): Boolean = exchange in setOf("KRX", "KOSDAQ")
}
```

---

## 6. 추상 클래스 (Abstract Class)

```kotlin
abstract class BaseOrder(
    val orderId: String,
    val symbol: String,
    val quantity: Long
) {
    // 추상 메서드: 서브클래스가 반드시 구현
    abstract fun orderValue(): BigDecimal
    abstract fun validate(): Boolean

    // 공통 구현
    fun summary(): String =
        "[$orderId] $symbol ${quantity}주 - ${orderValue()}"

    init {
        require(quantity > 0L) { "주문 수량은 양수여야 합니다" }
    }
}

class LimitOrder(
    orderId: String,
    symbol: String,
    quantity: Long,
    val limitPrice: BigDecimal
) : BaseOrder(orderId, symbol, quantity) {
    override fun orderValue(): BigDecimal = limitPrice * quantity.toBigDecimal()
    override fun validate(): Boolean      = limitPrice > BigDecimal.ZERO
}

class MarketOrder(
    orderId: String,
    symbol: String,
    quantity: Long
) : BaseOrder(orderId, symbol, quantity) {
    override fun orderValue(): BigDecimal = BigDecimal.ZERO  // 시장가는 미정
    override fun validate(): Boolean      = true
}
```

---

## 7. sealed class / sealed interface — 주문 상태 모델링

`sealed`는 **닫힌 타입 계층**을 만든다. `when`에서 모든 경우를 강제하여
`else` 없이도 컴파일러 경고를 받는다.

```kotlin
// 주문 상태를 sealed class로 모델링
sealed class OrderStatus {
    data object Pending   : OrderStatus()
    data object Submitted : OrderStatus()

    data class PartiallyFilled(
        val filledQuantity: Long,
        val remainingQuantity: Long
    ) : OrderStatus()

    data class Filled(
        val tradeId: String,
        val executedPrice: BigDecimal
    ) : OrderStatus()

    data class Rejected(
        val reason: String,
        val errorCode: String
    ) : OrderStatus()

    data object Cancelled : OrderStatus()
}

// when 표현식: else 없이 완전 분기 (컴파일러가 보장)
fun describeStatus(status: OrderStatus): String = when (status) {
    is OrderStatus.Pending         -> "접수 대기 중"
    is OrderStatus.Submitted       -> "거래소 제출 완료"
    is OrderStatus.PartiallyFilled -> "부분 체결 (${status.filledQuantity}주)"
    is OrderStatus.Filled          -> "전량 체결 @${status.executedPrice}"
    is OrderStatus.Rejected        -> "거부: ${status.reason} (${status.errorCode})"
    is OrderStatus.Cancelled       -> "취소됨"
}

// 상태 전이 유효성 검사
fun canTransition(from: OrderStatus, to: OrderStatus): Boolean = when (from) {
    is OrderStatus.Pending    -> to is OrderStatus.Submitted
                              || to is OrderStatus.Cancelled
    is OrderStatus.Submitted  -> to is OrderStatus.PartiallyFilled
                              || to is OrderStatus.Filled
                              || to is OrderStatus.Rejected
                              || to is OrderStatus.Cancelled
    is OrderStatus.PartiallyFilled -> to is OrderStatus.Filled
                                   || to is OrderStatus.Cancelled
    else -> false
}
```

> **실무 팁**: `sealed class` 대신 `sealed interface`를 쓰면 `data class`가 다른 클래스를
> 상속하면서도 sealed 계층에 참여할 수 있다. 도메인 모델 복잡도가 높을 때 유용하다.

---

## 8. enum class — 고급 패턴

```kotlin
import java.time.DayOfWeek
import java.time.LocalTime

enum class MarketSession(
    val displayName: String,
    val startHour: Int,
    val endHour: Int
) {
    PRE_MARKET("장전 시간외", 8, 9),
    REGULAR("정규장", 9, 15),
    AFTER_MARKET("장후 시간외", 15, 18),
    CLOSED("휴장", 0, 0);

    fun isActive(hour: Int): Boolean =
        hour in startHour until endHour

    companion object {
        fun current(): MarketSession {
            val hour = LocalTime.now().hour
            return values().find { it.isActive(hour) } ?: CLOSED
        }
    }
}

// 사용
val session = MarketSession.current()
println("현재 세션: ${session.displayName}")  // 현재 세션: 정규장

// 수수료 등급 enum: 프로퍼티 + 메서드를 가진 풍부한 enum
enum class CommissionTier(
    val minTradeAmount: Long,
    val rate: BigDecimal,
    val displayName: String
) {
    VIP(1_000_000_000L, BigDecimal("0.0010"), "VIP"),
    PREMIUM(100_000_000L, BigDecimal("0.0015"), "프리미엄"),
    REGULAR(10_000_000L, BigDecimal("0.0025"), "일반"),
    BASIC(0L, BigDecimal("0.0035"), "기본");

    fun calculate(tradeAmount: BigDecimal): BigDecimal =
        tradeAmount * rate

    companion object {
        fun fromAmount(totalMonthlyTrade: Long): CommissionTier =
            values().first { totalMonthlyTrade >= it.minTradeAmount }
    }
}
```

---

## 9. object — 싱글톤과 companion object

### 9.1 object 선언 (싱글톤)

```kotlin
import java.time.LocalDate

object MarketHolidayCalendar {
    private val holidays: Set<LocalDate> = loadHolidays()

    fun isHoliday(date: LocalDate): Boolean = date in holidays

    fun isTradeDay(date: LocalDate): Boolean =
        !isHoliday(date) && date.dayOfWeek !in setOf(
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
        )

    private fun loadHolidays(): Set<LocalDate> {
        // 실제로는 DB나 설정에서 로드
        return setOf(
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 3, 1),
            LocalDate.of(2025, 5, 5)
        )
    }
}

println(MarketHolidayCalendar.isTradeDay(LocalDate.now()))
```

### 9.2 companion object — 팩토리 패턴

```kotlin
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant

class OrderId private constructor(val value: String) {
    override fun toString() = value

    companion object {
        private val counter = AtomicLong(0)

        // 팩토리 메서드
        fun generate(): OrderId {
            val timestamp = Instant.now().epochSecond
            val seq       = counter.incrementAndGet()
            return OrderId("ORD-$timestamp-$seq")
        }

        fun from(raw: String): OrderId {
            require(raw.startsWith("ORD-")) { "잘못된 주문 ID 형식: $raw" }
            return OrderId(raw)
        }
    }
}

val newId      = OrderId.generate()
val existingId = OrderId.from("ORD-1700000000-42")
```

> **실무 팁**: `companion object`에 `@JvmStatic`을 붙이면 자바에서
> `OrderId.generate()`처럼 정적 메서드로 호출할 수 있다.

---

## 10. 중첩 클래스와 내부 클래스

```kotlin
class OrderBook(val symbol: String) {

    // 중첩 클래스 (nested): 외부 클래스 인스턴스 불필요, static처럼 동작
    class PriceLevel(val price: BigDecimal, val totalQuantity: Long) {
        override fun toString() = "$price × $totalQuantity"
    }

    // 내부 클래스 (inner): 외부 클래스 인스턴스 참조 가능
    inner class Snapshot {
        val capturedSymbol: String  = this@OrderBook.symbol  // 외부 참조
        val bestBid: BigDecimal?    = getBestBid()
        val bestAsk: BigDecimal?    = getBestAsk()
        val spread: BigDecimal?     = if (bestBid != null && bestAsk != null)
                                          bestAsk - bestBid
                                      else null
    }

    private fun getBestBid(): BigDecimal? = null  // 실제 구현 생략
    private fun getBestAsk(): BigDecimal? = null

    fun takeSnapshot() = Snapshot()
}

// 중첩 클래스는 외부 인스턴스 없이 생성
val level = OrderBook.PriceLevel(BigDecimal("75300"), 1000L)

// 내부 클래스는 외부 인스턴스 필요
val book     = OrderBook("SAMSUNG")
val snapshot = book.takeSnapshot()
```

---

## 11. 위임 (Delegation)

### 11.1 `by` — 인터페이스 위임

```kotlin
interface OrderValidator {
    fun validate(order: Order): Boolean
    fun errorMessage(): String
}

class QuantityValidator : OrderValidator {
    override fun validate(order: Order) =
        order.quantity in 1L..100_000L
    override fun errorMessage() = "수량은 1~100,000 사이여야 합니다"
}

// 위임: OrderValidator 구현을 내부 인스턴스에 위임
class EnhancedOrderService(
    private val validator: OrderValidator = QuantityValidator()
) : OrderValidator by validator {
    // validate, errorMessage는 자동으로 validator에 위임됨

    fun processOrder(order: Order) {
        check(validate(order)) { errorMessage() }
        submitToExchange(order)
    }
}
```

### 11.2 `lazy` — 지연 초기화

```kotlin
class MarketDataService(private val db: Database) {
    // 첫 접근 시에만 초기화 (스레드 안전: SYNCHRONIZED 기본)
    val historicalData: Map<String, List<BigDecimal>> by lazy {
        println("히스토리 데이터 로드 중...")
        db.loadHistoricalPrices()
    }

    // LazyThreadSafetyMode.NONE: 단일 스레드 환경에서 성능 최적화
    val symbolList: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        db.loadSymbols()
    }
}
```

### 11.3 `observable` — 변경 감지

```kotlin
import kotlin.properties.Delegates

class AlertService {
    var threshold: BigDecimal by Delegates.observable(BigDecimal("80000")) {
            _, old, new ->
        println("알림 기준가 변경: $old → $new")
        notifySubscribers(new)
    }

    // vetoable: 조건을 만족해야만 변경 허용
    var maxOrderQty: Long by Delegates.vetoable(10_000L) {
            _, _, new -> new > 0L && new <= 1_000_000L
    }

    private fun notifySubscribers(value: BigDecimal) { /* ... */ }
}

val alert = AlertService()
alert.threshold = BigDecimal("85000")  // "알림 기준가 변경: 80000 → 85000" 출력
alert.maxOrderQty = -1L               // 거부됨 (vetoable 조건 불만족)
```

---

## 12. 연산자 오버로딩 — Money 클래스 예제

```kotlin
import java.math.BigDecimal
import java.math.RoundingMode

data class Money(val amount: BigDecimal, val currency: String = "KRW") {

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "통화 불일치: $currency vs ${other.currency}" }
        return Money(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "통화 불일치" }
        return Money(amount - other.amount, currency)
    }

    operator fun times(multiplier: BigDecimal): Money =
        Money(amount * multiplier, currency)

    operator fun times(multiplier: Long): Money =
        Money(amount * multiplier.toBigDecimal(), currency)

    operator fun div(divisor: BigDecimal): Money =
        Money(amount.divide(divisor, 10, RoundingMode.HALF_UP), currency)

    // compareTo: >, <, >=, <= 모두 지원
    operator fun compareTo(other: Money): Int {
        require(currency == other.currency) { "통화 불일치" }
        return amount.compareTo(other.amount)
    }

    operator fun unaryMinus(): Money = Money(amount.negate(), currency)

    override fun toString(): String =
        "₩${amount.setScale(0, RoundingMode.HALF_UP)}"

    companion object {
        val ZERO = Money(BigDecimal.ZERO)
        fun of(amount: Long)   = Money(amount.toBigDecimal())
        fun of(amount: String) = Money(BigDecimal(amount))
    }
}

// 사용 예시
val buyPrice     = Money.of("75300")
val currentPrice = Money.of("82500")
val quantity     = 100L

val totalCost    = buyPrice     * quantity  // operator times(Long)
val currentValue = currentPrice * quantity
val profit       = currentValue - totalCost

println("매수 금액: $totalCost")    // ₩7530000
println("현재 가치: $currentValue") // ₩8250000
println("평가 손익: $profit")       // ₩720000

val isProfit = currentPrice > buyPrice  // operator compareTo
println("수익 중: $isProfit")           // true
```

---

## 13. 봉인된 계층으로 도메인 모델링 — 종합 예제

```kotlin
// 금융 이벤트를 sealed interface로 모델링
sealed interface DomainEvent {
    val occurredAt: java.time.Instant
}

data class OrderPlaced(
    val orderId: String,
    val symbol: String,
    val quantity: Long,
    val price: BigDecimal?,
    override val occurredAt: java.time.Instant = java.time.Instant.now()
) : DomainEvent

data class TradeExecuted(
    val tradeId: String,
    val orderId: String,
    val executedPrice: BigDecimal,
    val executedQuantity: Long,
    override val occurredAt: java.time.Instant = java.time.Instant.now()
) : DomainEvent

data class OrderCancelled(
    val orderId: String,
    val reason: String,
    override val occurredAt: java.time.Instant = java.time.Instant.now()
) : DomainEvent

// 이벤트 처리 — else 없이 컴파일 완전성 보장
fun handleDomainEvent(event: DomainEvent) {
    when (event) {
        is OrderPlaced    -> {
            println("주문 접수: ${event.orderId} ${event.symbol} ${event.quantity}주")
            // 주문 접수 처리 로직
        }
        is TradeExecuted  -> {
            println("체결: ${event.tradeId} @${event.executedPrice}")
            // 잔고 업데이트, 수수료 정산 등
        }
        is OrderCancelled -> {
            println("취소: ${event.orderId} - ${event.reason}")
            // 취소 처리 로직
        }
    }
}

// 금융 상품 타입 계층
sealed interface FinancialProduct {
    val productCode: String
    val name: String
    fun riskLevel(): RiskLevel
}

enum class RiskLevel { LOW, MEDIUM, HIGH, VERY_HIGH }

data class DomesticStock(
    override val productCode: String,
    override val name: String,
    val marketCap: Money
) : FinancialProduct {
    override fun riskLevel(): RiskLevel = when {
        marketCap > Money.of("5000000000000") -> RiskLevel.MEDIUM
        marketCap > Money.of("500000000000")  -> RiskLevel.HIGH
        else                                  -> RiskLevel.VERY_HIGH
    }
}

data class GovernmentBond(
    override val productCode: String,
    override val name: String,
    val maturityYears: Int,
    val couponRate: BigDecimal
) : FinancialProduct {
    override fun riskLevel(): RiskLevel = RiskLevel.LOW
}

data class ETF(
    override val productCode: String,
    override val name: String,
    val underlyingAssets: List<String>
) : FinancialProduct {
    override fun riskLevel(): RiskLevel = RiskLevel.MEDIUM
}

fun describeProduct(product: FinancialProduct): String = when (product) {
    is DomesticStock  -> "${product.name}: 시가총액 ${product.marketCap}, 위험도 ${product.riskLevel()}"
    is GovernmentBond -> "${product.name}: 만기 ${product.maturityYears}년, 쿠폰 ${product.couponRate}%"
    is ETF            -> "${product.name}: 구성 종목 ${product.underlyingAssets.size}개"
}
```

---

## 14. 핵심 정리

### OOP 설계 원칙 체크리스트

- [ ] 불변 데이터는 `data class` + `val`로 표현한다
- [ ] 상태 모델링에 `sealed class`를 쓰고 `when`에서 `else`를 제거한다
- [ ] 팩토리 로직은 `companion object`에 넣는다
- [ ] 반복 코드는 `by` 위임으로 제거한다
- [ ] `init` 블록에서 `require`/`check`로 불변식을 강제한다
- [ ] 연산자 오버로딩은 의미가 명확할 때만 사용한다 (Money +/- 는 OK)
- [ ] 중첩 클래스는 `nested`, 외부 참조 필요 시만 `inner`를 쓴다

### 설계 패턴 vs 코틀린 관용구

| GoF 패턴 | 코틀린 관용구 |
|----------|--------------|
| 싱글톤 | `object` |
| 팩토리 메서드 | `companion object` 함수 |
| 데코레이터 | `by` 위임 |
| 상태 패턴 | `sealed class` + `when` |
| 전략 패턴 | 함수 타입 인자 (고차 함수) |
| 빌더 패턴 | 기본값 + 명명 인자 |
| 옵저버 패턴 | `Delegates.observable` |

### data class 활용 가이드

```kotlin
// DO: 불변 값 객체, DTO, 이벤트
data class TradeConfirmation(val tradeId: String, val amount: BigDecimal)

// DON'T: 상태를 가진 도메인 엔터티 (equals가 의미 없음)
// data class OrderBook(...)  // 잔량이 계속 바뀌는 오더북에 data class는 부적합
class OrderBook(...)           // 일반 클래스가 적합
```

---

이전: [18. 코틀린 타입 시스템과 함수](18-kotlin-types-and-functions.md) · 다음: [20. 코틀린 컬렉션과 함수형 프로그래밍](20-kotlin-collections.md) · [전체 커리큘럼](../CURRICULUM.md)
