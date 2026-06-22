# 18. 코틀린 타입 시스템과 함수

> **대상**: 주식 투자 경험은 있으나 코틀린/개발이 처음인 증권사 백엔드 지망자  
> **목표**: 코틀린 타입 계층과 함수의 작동 원리를 이해하고, 증권 도메인 코드에서 안전하고 표현력 있는 함수를 작성한다

---

## 1. 코틀린 타입 계층 (Type Hierarchy)

### 1.1 최상위 타입 — `Any`

자바의 `Object`에 해당하지만, 코틀린은 **nullable 여부를 타입 수준에서 구분**한다.

| 타입 | 설명 | 자바 대응 |
|------|------|-----------|
| `Any` | 모든 non-null 타입의 최상위 | `Object` (non-null) |
| `Any?` | 모든 nullable 타입의 최상위 | `Object` |
| `Unit` | 반환값 없음 | `void` / `Void` |
| `Nothing` | 정상 반환이 없음을 표현 | (없음) |

```kotlin
// Any: 모든 non-null 값은 Any를 상속
fun describe(value: Any): String = when (value) {
    is Int     -> "정수: $value"
    is String  -> "문자열: \"$value\""
    is Boolean -> "불리언: $value"
    else       -> "기타: ${value::class.simpleName}"
}

val price: Any = 75_300       // Int지만 Any로 받을 수 있다
println(describe(price))      // 정수: 75300
```

### 1.2 `Unit` — 반환값 없음

```kotlin
// Unit은 타입이자 싱글톤 객체다
fun logOrder(orderId: String): Unit {
    println("주문 로그: $orderId")
    // return Unit 생략 가능
}

// 반환 타입 선언을 아예 생략해도 Unit이 추론된다
fun logTrade(tradeId: String) {
    println("체결 로그: $tradeId")
}
```

> **실무 팁**: `Unit`은 제네릭 타입 인자로도 쓸 수 있어 `Callback<Unit>` 같은 패턴이 가능하다.
> 자바의 `Void`처럼 null을 강제로 반환할 필요가 없다.

### 1.3 `Nothing` — 정상 반환이 없음

`Nothing`은 **값이 절대 만들어지지 않는** 타입이다. 예외를 던지거나 무한 루프에 사용한다.

```kotlin
// throw 표현식의 타입은 Nothing
fun fail(message: String): Nothing = throw IllegalStateException(message)

// Nothing은 모든 타입의 서브타입 → Elvis 오른쪽에서 활용
fun findAccount(accountId: String): Account {
    return accountRepository[accountId]
        ?: fail("계좌 없음: $accountId")  // Nothing이므로 컴파일 통과
}

// 무한 루프도 Nothing
fun marketDataLoop(): Nothing {
    while (true) {
        fetchAndPublish()
    }
}
```

```
타입 계층도

        Any?
         │
    ┌────┴────┐
   Any      null
    │
  ┌─┼──────────────┐
 Int String Boolean ... (모든 non-null 타입)

  Nothing  ← 모든 타입의 서브타입 (최하위)
```

---

## 2. 기본형(Primitive)과 박싱(Boxing)

코틀린 소스에서는 모두 클래스처럼 보이지만, **컴파일러가 JVM 기본형으로 최적화**한다.

| 코틀린 타입 | JVM 기본형 (non-null) | JVM 박싱 (nullable) |
|------------|----------------------|---------------------|
| `Int` | `int` | `Integer` |
| `Long` | `long` | `Long` |
| `Double` | `double` | `Double` |
| `Boolean` | `boolean` | `Boolean` |

```kotlin
// 이 두 줄은 JVM 바이트코드에서 다르게 컴파일된다
val quantity: Int    = 100   // JVM: int (기본형)
val nullableQty: Int? = null  // JVM: Integer (박싱)

// 증권 도메인 주의사항: 금액은 BigDecimal, 수량은 Long 사용
import java.math.BigDecimal

data class StockOrder(
    val symbol: String,         // 종목 코드
    val quantity: Long,         // 수량 (기본형 long)
    val price: BigDecimal,      // 가격 (정확한 소수점)
    val orderType: String
)
```

> **함정(Gotcha)**: `Int?` 리스트 `List<Int?>`는 `List<Integer>`로 컴파일된다.
> 성능이 중요한 대량 시세 처리라면 `IntArray`(기본형 배열)를 고려하자.

---

## 3. 타입 추론 (Type Inference)

```kotlin
// 컴파일러가 오른쪽 식으로 타입을 추론
val symbol   = "SAMSUNG"               // String
val price    = BigDecimal("75300.00")  // BigDecimal
val orderIds = listOf("O001", "O002")  // List<String>

// 함수 반환 타입도 단일 표현식에서 추론 가능
fun commissionRate(tradeAmount: BigDecimal) =
    if (tradeAmount >= BigDecimal("10_000_000")) BigDecimal("0.0025")
    else BigDecimal("0.0035")

// 단, public API는 반환 타입을 명시하는 것이 권장 사항
fun calculateCommission(amount: BigDecimal): BigDecimal =
    amount * commissionRate(amount)
```

> **실무 팁**: 지역 변수는 추론에 맡기고, 함수 파라미터·반환 타입·공개 프로퍼티는 명시하는 것이
> 팀 코드 가독성에 좋다.

---

## 4. 타입 변환 (Type Conversion)

코틀린은 자바와 달리 **묵시적 타입 변환이 없다**. 항상 명시적으로 변환해야 한다.

```kotlin
val quantity: Int = 500
// val longQty: Long = quantity  // 컴파일 오류!
val longQty: Long = quantity.toLong()  // OK

// 숫자 변환 함수
val price     = 75300
val bigPrice: BigDecimal = price.toBigDecimal()
val doublePrice: Double  = price.toDouble()

// 문자열 → 숫자 (파싱 실패 시 예외)
val inputPrice = "75300.50"
val parsed     = inputPrice.toBigDecimal()

// 안전한 변환 (실패 시 null)
val maybeLong = "abc".toLongOrNull()    // null
val safeLong  = "12345".toLongOrNull()  // 12345L
```

### 타입 검사 — `is` / `!is`

```kotlin
fun processOrderEvent(event: Any) {
    if (event is OrderPlaced) {
        // 스마트 캐스트: 이 블록 안에서 event는 OrderPlaced 타입
        println("주문 접수: ${event.orderId}")
    } else if (event !is OrderCancelled) {
        println("알 수 없는 이벤트")
    }
}
```

---

## 5. Null 안전성 심화 (Null Safety)

> **기초는** `docs/02-kotlin-basics.md` 참조. 여기서는 심화 패턴을 다룬다.

### 5.1 안전 호출 `?.` 체이닝

```kotlin
data class Account(val portfolio: Portfolio?)
data class Portfolio(val holdings: List<Holding>?)
data class Holding(val symbol: String, val quantity: Long)

fun getFirstSymbol(account: Account?): String? =
    account?.portfolio?.holdings?.firstOrNull()?.symbol

// 결과가 null이면 중간에 단락(short-circuit)되어 null 반환
```

### 5.2 Elvis 연산자 `?:` — 기본값과 조기 반환

```kotlin
fun getAccountBalance(accountId: String): BigDecimal {
    val account = accountRepository[accountId]
        ?: return BigDecimal.ZERO  // null이면 즉시 반환

    val balance = account.balance
        ?: throw IllegalStateException("잔고 정보 없음: $accountId")

    return balance
}
```

### 5.3 비-null 단언 `!!` — 사용 기준

```kotlin
// !! 는 null이면 NullPointerException 발생
// 사용해도 되는 경우: 비즈니스 로직상 null이 절대 불가능함을 증명할 수 있을 때
fun processConfirmedTrade(tradeId: String) {
    // DB에서 확인된 체결건만 들어오는 함수 — null일 수 없다는 계약이 있음
    val trade = tradeRepository.findById(tradeId)!!
    settleAccount(trade)
}

// !! 대신 require 사용 권장 (더 나은 에러 메시지)
fun processConfirmedTradeSafe(tradeId: String) {
    val trade = tradeRepository.findById(tradeId)
        ?: error("체결 데이터 없음: $tradeId")
    settleAccount(trade)
}
```

> **함정(Gotcha)**: `!!`가 많이 보이는 코드는 null 안전성의 이점을 포기한 것이다.
> 팀 코드리뷰에서 `!!`는 반드시 이유를 주석으로 설명하게 하는 것이 관행이다.

### 5.4 `let`으로 null 분기 처리

```kotlin
val currentPrice: BigDecimal? = priceService.getPrice("SAMSUNG")

// let: null이 아닐 때만 블록 실행
currentPrice?.let { price ->
    val orderValue = price * BigDecimal(quantity)
    submitOrder(symbol, orderValue)
}

// 단순 변환
val displayPrice = currentPrice?.let { "₩${it.toPlainString()}" } ?: "시세 없음"
```

### 5.5 안전한 캐스트 `as?`

```kotlin
fun extractOrderId(event: Any): String? {
    val orderEvent = event as? OrderEvent  // 실패 시 예외 대신 null 반환
    return orderEvent?.orderId
}

// 일반 as: 캐스트 실패 시 ClassCastException
// as?  : 캐스트 실패 시 null (안전)
```

### 5.6 스마트 캐스트 (Smart Cast)

컴파일러가 `is` 검사 후 자동으로 타입을 좁혀준다.

```kotlin
sealed class MarketEvent
data class PriceUpdate(val symbol: String, val price: BigDecimal) : MarketEvent()
data class VolumeAlert(val symbol: String, val volume: Long)      : MarketEvent()

fun handleEvent(event: MarketEvent) {
    when (event) {
        is PriceUpdate -> {
            // event는 자동으로 PriceUpdate로 캐스트
            println("${event.symbol}: ₩${event.price}")
        }
        is VolumeAlert -> {
            println("${event.symbol} 거래량 급증: ${event.volume}")
        }
    }
}
```

> **스마트 캐스트 한계**: `var` 프로퍼티나 `open` 프로퍼티는 스마트 캐스트가 작동하지 않는다
> (다른 스레드가 변경할 수 있기 때문). 지역 `val` 변수에서만 확실히 작동한다.

---

## 6. 함수 기초 — 파라미터 전략

### 6.1 기본값 파라미터 (Default Parameters)

```kotlin
fun placeOrder(
    symbol: String,
    quantity: Long,
    price: BigDecimal?   = null,           // null이면 시장가
    orderType: OrderType = OrderType.LIMIT,
    timeInForce: String  = "DAY"
): OrderResult {
    val effectiveType = if (price == null) OrderType.MARKET else orderType
    return submitToExchange(symbol, quantity, price, effectiveType, timeInForce)
}

// 호출 시 필요한 것만 지정
placeOrder("SAMSUNG", 10)
placeOrder("KAKAO", 5, BigDecimal("55000"))
placeOrder("NAVER", 3, orderType = OrderType.MARKET)
```

### 6.2 명명 인자 (Named Arguments)

```kotlin
// 파라미터가 많을 때 명명 인자로 가독성 확보
val result = placeOrder(
    symbol      = "LG_ENERGY",
    quantity    = 20L,
    price       = BigDecimal("420000"),
    timeInForce = "IOC"
)
```

### 6.3 가변인자 `vararg`

```kotlin
fun calculatePortfolioValue(vararg symbols: String): BigDecimal {
    return symbols
        .map { priceService.getPrice(it) ?: BigDecimal.ZERO }
        .fold(BigDecimal.ZERO, BigDecimal::add)
}

// 직접 전달
calculatePortfolioValue("SAMSUNG", "KAKAO", "NAVER")

// 배열을 spread 연산자로 전달
val watchList = arrayOf("SAMSUNG", "HYUNDAI")
calculatePortfolioValue(*watchList)
```

---

## 7. 단일 표현식 함수 (Single-Expression Function)

```kotlin
// 블록 함수
fun isValidSymbol(symbol: String): Boolean {
    return symbol.length in 1..6 && symbol.all { it.isLetterOrDigit() }
}

// 단일 표현식으로 동등하게 작성
fun isValidSymbol2(symbol: String) =
    symbol.length in 1..6 && symbol.all { it.isLetterOrDigit() }

// 증권 예제
fun toBasisPoints(rate: BigDecimal): Int =
    (rate * BigDecimal("10000")).toInt()

fun isPriceWithinLimit(price: BigDecimal, limitPrice: BigDecimal): Boolean =
    price <= limitPrice
```

---

## 8. 고차 함수 (Higher-Order Functions)

함수를 **인자로 받거나 반환하는 함수**. 코틀린 컬렉션 API가 모두 이 방식이다.

### 8.1 함수를 인자로 받기

```kotlin
// (Order) -> Boolean 타입의 함수를 인자로 받는다
fun filterOrders(
    orders: List<Order>,
    predicate: (Order) -> Boolean
): List<Order> = orders.filter(predicate)

// 사용
val highValueOrders = filterOrders(orders) { order ->
    order.price * order.quantity.toBigDecimal() > BigDecimal("1_000_000")
}
```

### 8.2 함수를 반환하기

```kotlin
// 수수료율 계산 함수를 조건에 따라 반환
fun getCommissionCalculator(accountType: AccountType): (BigDecimal) -> BigDecimal {
    return when (accountType) {
        AccountType.VIP     -> { amount -> amount * BigDecimal("0.0010") }
        AccountType.REGULAR -> { amount -> amount * BigDecimal("0.0025") }
        AccountType.BASIC   -> { amount -> amount * BigDecimal("0.0035") }
    }
}

// 사용
val calculator = getCommissionCalculator(account.type)
val commission = calculator(tradeAmount)
```

### 8.3 함수 참조 (Function Reference)

```kotlin
fun isLargeOrder(order: Order): Boolean = order.quantity >= 1000L

// :: 로 함수를 객체처럼 전달
val largeOrders = orders.filter(::isLargeOrder)

// 메서드 참조
val symbols = orders.map(Order::symbol)
```

---

## 9. 람다와 클로저 (Lambda & Closure)

```kotlin
// 람다 기본 문법: { 파라미터 -> 본문 }
val isPositive: (BigDecimal) -> Boolean = { price -> price > BigDecimal.ZERO }

// it: 파라미터가 하나일 때 암묵적 이름
val isPositive2: (BigDecimal) -> Boolean = { it > BigDecimal.ZERO }

// 클로저: 람다는 외부 변수를 캡처할 수 있다
fun makeAlertChecker(threshold: BigDecimal): (BigDecimal) -> Boolean {
    return { price -> price > threshold }  // threshold 캡처
}

val isOverThreshold = makeAlertChecker(BigDecimal("80000"))
println(isOverThreshold(BigDecimal("85000")))  // true

// 주의: 람다가 var를 캡처하면 힙에 래퍼 객체가 생긴다
var alertCount = 0
val countingFilter: (Order) -> Boolean = { order ->
    if (order.quantity > 500L) {
        alertCount++  // var 캡처 — 박싱 발생
        true
    } else false
}
```

> **함정(Gotcha)**: 람다가 `var`를 캡처하면 JVM에서 `Ref<T>` 래퍼 객체로 힙에 할당된다.
> 성능 민감 코드에서는 캡처를 피하거나 `inline` 함수를 사용하자.

---

## 10. 익명 함수 (Anonymous Function)

람다와 비슷하지만 `return`이 **함수 자체**를 반환한다.

```kotlin
// 람다에서 return은 외부 함수를 반환 (non-local return)
// 익명 함수에서 return은 익명 함수 자체를 반환 (local return)
val findFirstValid = fun(orders: List<Order>): Order? {
    for (order in orders) {
        if (order.quantity > 0L) return order  // 익명 함수만 반환
    }
    return null
}
```

---

## 11. 확장 함수와 확장 프로퍼티 (Extension Function/Property)

기존 클래스를 수정하지 않고 **함수를 추가**하는 것처럼 보이게 한다.

### 11.1 BigDecimal 확장 함수 — 증권 도메인 예제

```kotlin
import java.math.BigDecimal
import java.math.RoundingMode

// BigDecimal에 증권 도메인 연산 추가
fun BigDecimal.roundToKRW(): BigDecimal =
    this.setScale(0, RoundingMode.HALF_UP)

fun BigDecimal.toPercent(): String =
    "${this.multiply(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)}%"

fun BigDecimal.isPositive(): Boolean = this > BigDecimal.ZERO
fun BigDecimal.isNegative(): Boolean = this < BigDecimal.ZERO

// 손익률 계산 확장 함수
fun BigDecimal.profitRate(purchasePrice: BigDecimal): BigDecimal {
    require(purchasePrice.isPositive()) { "매수가는 양수여야 합니다" }
    return (this - purchasePrice)
        .divide(purchasePrice, 6, RoundingMode.HALF_UP)
}

// 사용
val currentPrice = BigDecimal("82500")
val buyPrice     = BigDecimal("75300")
val rate         = currentPrice.profitRate(buyPrice)
println("수익률: ${rate.toPercent()}")           // 수익률: 9.56%
println("현재가: ₩${currentPrice.roundToKRW()}")  // 현재가: ₩82500
```

### 11.2 확장 프로퍼티

```kotlin
// List<Order>에 총 거래대금 프로퍼티 추가
val List<Order>.totalTradeAmount: BigDecimal
    get() = fold(BigDecimal.ZERO) { acc, order ->
        acc + (order.price ?: BigDecimal.ZERO) * order.quantity.toBigDecimal()
    }

// 사용
val dailyOrders: List<Order> = getOrders()
println("일일 거래대금: ₩${dailyOrders.totalTradeAmount.roundToKRW()}")
```

> **실무 팁**: 확장 함수는 **정적 디스패치**다. 오버라이드되지 않는다.
> 따라서 다형성이 필요한 로직에는 멤버 함수를 사용해야 한다.

---

## 12. 인라인 함수 (Inline Function)

람다를 인자로 받는 고차 함수는 기본적으로 **람다 객체 생성 + 가상 호출** 오버헤드가 있다.
`inline`으로 해결한다.

```kotlin
// inline: 컴파일러가 호출 지점에 함수 본문을 직접 복사
inline fun measureTime(block: () -> Unit): Long {
    val start = System.currentTimeMillis()
    block()
    return System.currentTimeMillis() - start
}

// 실제 사용 시 람다 객체가 생성되지 않음
val elapsed = measureTime {
    processBulkOrders(largeOrderList)
}
println("처리 시간: ${elapsed}ms")

// noinline: 특정 람다만 인라인 제외
inline fun conditionalExecute(
    condition: () -> Boolean,
    noinline action: () -> Unit  // 이 람다는 객체로 저장 필요
) {
    if (condition()) action()
}
```

> **함정(Gotcha)**: `inline` 함수 안에서 인라인된 람다는 non-local `return`이 가능하다.
> 즉 외부 함수를 조기 반환시킬 수 있어 로직 흐름을 주의해야 한다.

---

## 13. 꼬리재귀 (Tail Recursion — `tailrec`)

```kotlin
// 일반 재귀: 스택 오버플로 위험
fun factorial(n: Long): Long =
    if (n <= 1) 1L else n * factorial(n - 1)

// tailrec: 컴파일러가 루프로 변환 → 스택 안전
tailrec fun factorialTailrec(n: Long, acc: Long = 1L): Long =
    if (n <= 1) acc else factorialTailrec(n - 1, acc * n)

// 증권 예제: 복리 수익 계산
tailrec fun compoundReturn(
    principal: BigDecimal,
    rate: BigDecimal,
    periods: Int,
    acc: BigDecimal = principal
): BigDecimal =
    if (periods <= 0) acc
    else compoundReturn(principal, rate, periods - 1, acc * (BigDecimal.ONE + rate))

val result = compoundReturn(
    principal = BigDecimal("10000000"),
    rate      = BigDecimal("0.08"),
    periods   = 10
)
println("10년 후 복리 수익: ₩${result.roundToKRW()}")
```

> **조건**: `tailrec`이 작동하려면 재귀 호출이 함수의 **마지막 연산**이어야 한다.
> `n * factorial(n-1)` 같이 재귀 후 곱셈이 있으면 tailrec이 아니다.

---

## 14. 타입 별칭과 인라인 클래스 (Type Alias & Value Class)

### 14.1 typealias — 복잡한 타입에 이름 붙이기

```kotlin
typealias SymbolCode   = String
typealias Price        = BigDecimal
typealias PriceMap     = Map<SymbolCode, Price>
typealias OrderHandler = (Order) -> OrderResult

// 사용
fun registerHandler(handler: OrderHandler) { /* ... */ }
```

### 14.2 @JvmInline value class — 래퍼 오버헤드 없는 타입 안전성

```kotlin
@JvmInline
value class OrderId(val value: String) {
    init { require(value.isNotBlank()) { "주문 ID는 비어 있을 수 없습니다" } }
}

@JvmInline
value class Quantity(val value: Long) {
    init { require(value > 0) { "수량은 양수여야 합니다" } }
}

// String/Long을 혼용하는 실수를 컴파일 타임에 방지
fun getOrder(id: OrderId): Order = orderRepo.find(id.value)
//  getOrder("raw-string")  // 컴파일 오류 — 실수 방지
```

---

## 15. 핵심 정리

### 타입 체크리스트

- [ ] `Any`/`Unit`/`Nothing`의 역할을 설명할 수 있다
- [ ] non-null `Int`와 nullable `Int?`의 JVM 차이를 안다
- [ ] `as?`로 안전 캐스트를 사용한다
- [ ] `!!`는 반드시 이유를 주석으로 남긴다
- [ ] `@JvmInline value class`로 원시 타입에 도메인 의미를 부여한다

### 함수 체크리스트

- [ ] 기본값·명명 인자로 오버로딩을 줄인다
- [ ] 고차 함수로 중복 로직을 제거한다
- [ ] `BigDecimal` 확장 함수로 도메인 연산을 표현력 있게 작성한다
- [ ] 성능 민감 고차 함수에 `inline`을 적용한다
- [ ] 재귀가 필요하면 `tailrec`을 고려한다

### 자주 쓰는 패턴 요약

```kotlin
// 패턴 1: 안전 체인 + Elvis + let
val result = user?.account?.balance
    ?.let { formatBalance(it) }
    ?: "잔고 조회 불가"

// 패턴 2: BigDecimal 확장으로 도메인 표현력 향상
val profit = sellPrice.profitRate(buyPrice).toPercent()

// 패턴 3: 고차 함수로 재시도 로직 추상화
inline fun withRetry(maxAttempts: Int, action: () -> Unit) {
    repeat(maxAttempts) {
        runCatching { action() }.onSuccess { return }
    }
}

// 패턴 4: value class로 원시 타입 래핑
@JvmInline value class AccountId(val value: String)
@JvmInline value class TradeAmount(val value: BigDecimal)
```

---

이전: [17. JVM 기초](17-jvm-fundamentals) · 다음: [19. 코틀린 객체지향과 설계](19-kotlin-oop) · [전체 커리큘럼](/curriculum)
