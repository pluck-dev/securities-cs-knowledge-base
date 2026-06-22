# 21. 코틀린 제네릭과 관용구

> **대상**: 주식 투자 경험은 있으나 코틀린/개발이 처음인 증권사 백엔드 지망자  
> **목표**: 제네릭으로 재사용 가능한 코드를 작성하고, 코틀린 관용구로 실무 코드의 품질을 높인다

---

## 1. 제네릭 기초 (Generics)

제네릭은 **타입을 파라미터로 받아 다양한 타입에서 재사용**하는 코드를 작성한다.

```kotlin
// 제네릭 없이: 타입별로 중복 함수 필요
fun findFirstOrder(orders: List<Order>): Order? = orders.firstOrNull()
fun findFirstTrade(trades: List<Trade>): Trade? = trades.firstOrNull()

// 제네릭으로: 타입 파라미터 T
fun <T> findFirst(items: List<T>): T? = items.firstOrNull()

val firstOrder = findFirst(orders)  // T = Order로 추론
val firstTrade = findFirst(trades)  // T = Trade로 추론
```

### 제네릭 클래스 — 증권 도메인 예제

```kotlin
import java.math.BigDecimal

// 페이지네이션 응답 래퍼
data class PagedResponse<T>(
    val items: List<T>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int,
    val hasNext: Boolean
) {
    val totalPages: Int get() = ((totalCount + pageSize - 1) / pageSize).toInt()
    val isEmpty: Boolean get() = items.isEmpty()
}

// 사용: 다양한 타입에 재사용
val tradePage: PagedResponse<Trade> = tradeService.getTrades(page = 1, size = 20)
val orderPage: PagedResponse<Order> = orderService.getOrders(page = 1, size = 20)

// API 응답 래퍼
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val code: String, val message: String) : ApiResult<Nothing>()
}

fun fetchPrice(symbol: String): ApiResult<BigDecimal> = try {
    ApiResult.Success(priceService.get(symbol))
} catch (e: Exception) {
    ApiResult.Failure("PRICE_FETCH_ERROR", e.message ?: "알 수 없는 오류")
}
```

---

## 2. 타입 파라미터 제약 (Generic Constraints)

```kotlin
// Number로 제한
fun <T : Number> average(items: List<T>): Double =
    items.sumOf { it.toDouble() } / items.size

// Comparable로 제한 → 정렬/비교 가능
fun <T : Comparable<T>> clamp(value: T, min: T, max: T): T = when {
    value < min -> min
    value > max -> max
    else        -> value
}

// 사용
val limitedQty = clamp(quantity, 1L, 100_000L)

// 여러 제약: where 절 사용
interface Priceable { fun currentPrice(): BigDecimal }
interface Tradeable { fun canTrade(): Boolean }

fun <T> getTradeableProducts(
    items: List<T>
): List<T> where T : Priceable, T : Tradeable {
    return items.filter { it.canTrade() && it.currentPrice() > BigDecimal.ZERO }
}
```

---

## 3. 변성 (Variance) — 공변·반공변·무변

> 변성은 "A가 B의 서브타입일 때 `Container<A>`와 `Container<B>`는 어떤 관계인가?"를 정의한다.

### 3.1 무변 (Invariant) — 기본

```kotlin
// MutableList는 무변: MutableList<String>은 MutableList<Any>의 서브타입이 아님
fun addAny(list: MutableList<Any>) {
    list.add(42)  // Int 추가 가능
}

// val strings = mutableListOf("a", "b")
// addAny(strings)  // 컴파일 오류! (무변)
// 이유: addAny 안에서 strings.add(42) 같은 위험 코드가 가능하기 때문
```

### 3.2 공변 (Covariant) — `out`

```kotlin
// out T: "생산자(Producer)"만 됨 — T를 꺼낼 수만 있음, 넣기 불가
interface PriceSource<out T : Priceable> {
    fun getProduct(): T
    // fun setProduct(t: T)  // 컴파일 오류 — out이면 T 입력 불가
}

// Stock이 Priceable의 서브타입이면
// PriceSource<Stock>은 PriceSource<Priceable>의 서브타입 (공변)
fun displayPrice(source: PriceSource<Priceable>) {
    println(source.getProduct().currentPrice())
}

// PriceSource<Stock>을 PriceSource<Priceable> 자리에 사용 가능
val stockSource: PriceSource<Stock> = StockPriceSource(samsung)
displayPrice(stockSource)  // OK
```

### 3.3 반공변 (Contravariant) — `in`

```kotlin
// in T: "소비자(Consumer)"만 됨 — T를 넣을 수만 있음, 꺼내기 불가
interface OrderProcessor<in T : BaseOrder> {
    fun process(order: T)
    // fun getLastOrder(): T  // 컴파일 오류 — in이면 T 출력 불가
}

// BaseOrder 프로세서는 LimitOrder(서브타입)도 처리 가능 (반공변)
class GenericProcessor : OrderProcessor<BaseOrder> {
    override fun process(order: BaseOrder) {
        println("처리: ${order.orderId}")
    }
}

val processor: OrderProcessor<LimitOrder> = GenericProcessor()  // OK — 반공변
processor.process(limitOrder)
```

### 3.4 변성 요약표

| | 공변 `out` | 반공변 `in` | 무변 |
|--|-----------|------------|------|
| 타입 관계 | `C<Sub>` → `C<Super>` 사용 가능 | `C<Super>` → `C<Sub>` 사용 가능 | 관계 없음 |
| 쓰기(put) | 불가 | 가능 | 가능 |
| 읽기(get) | 가능 | 불가 | 가능 |
| 예시 | `List<out T>` | `Comparator<in T>` | `MutableList<T>` |
| 역할 | 생산자 (Producer) | 소비자 (Consumer) | 생산자 + 소비자 |

> **외우는 법**: **PECS — Producer Extends, Consumer Super**
> 코틀린에서는 "Producer `out`, Consumer `in`"으로 기억하면 된다.

---

## 4. 타입 소거와 reified

JVM은 런타임에 제네릭 타입 정보를 지운다(type erasure). `reified`로 극복한다.

```kotlin
// 문제: 런타임에 T 타입을 알 수 없음
// fun <T> parseJson(json: String): T {
//     return objectMapper.readValue(json, T::class.java)  // 컴파일 오류: T::class 불가
// }

// 해결: inline + reified → 런타임에도 타입 정보 유지
inline fun <reified T> parseJson(json: String): T {
    return objectMapper.readValue(json, T::class.java)  // OK
}

inline fun <reified T> parseJsonList(json: String): List<T> {
    val type = objectMapper.typeFactory.constructCollectionType(List::class.java, T::class.java)
    return objectMapper.readValue(json, type)
}

// 사용 — 타입 명시
val trade: Trade = parseJson<Trade>(tradeJson)
val orders: List<Order> = parseJsonList<Order>(ordersJson)

// 타입 검사에도 활용
inline fun <reified T> List<*>.filterIsInstance2(): List<T> =
    filterIsInstance(T::class.java)

val baseOrders: List<BaseOrder> = listOf(LimitOrder(...), MarketOrder(...), LimitOrder(...))
val limitOrders: List<LimitOrder> = baseOrders.filterIsInstance2<LimitOrder>()
// 코틀린 표준 라이브러리도 동일한 방식: baseOrders.filterIsInstance<LimitOrder>()
```

---

## 5. 스타 프로젝션 (Star Projection)

타입 인자를 알 수 없거나 중요하지 않을 때 `*`를 사용한다.

```kotlin
// List<*>: 어떤 타입의 List인지 모르지만 List임은 안다
fun printCollectionSize(collection: Collection<*>) {
    println("크기: ${collection.size}")
    val first = collection.firstOrNull()  // Any?로만 받을 수 있음
    // collection.add("x")  // 불가 — 타입 모름
}

// 실무: 타입 파라미터가 중요하지 않을 때
fun logPagedResponse(response: PagedResponse<*>) {
    println("총 ${response.totalCount}건, 현재 페이지 ${response.page}/${response.totalPages}")
}

// Map<String, *>: 키는 String, 값은 모름
fun printMap(map: Map<String, *>) {
    map.forEach { (k, v) -> println("$k = $v") }
}
```

---

## 6. 스코프 함수 완전정복

스코프 함수는 객체를 컨텍스트로 코드 블록을 실행한다. 5가지의 차이를 완벽히 이해하자.

| 함수 | 컨텍스트 참조 | 반환값 | 주요 용도 |
|------|-------------|--------|----------|
| `let` | `it` | 람다 결과 | null 체크, 변환, 블록 범위 제한 |
| `run` | `this` | 람다 결과 | 초기화 + 결과 계산, 멤버 다수 호출 |
| `with` | `this` | 람다 결과 | 특정 객체 멤버 다수 호출 |
| `apply` | `this` | 객체 자신 | 객체 설정 (빌더 패턴) |
| `also` | `it` | 객체 자신 | 사이드 이펙트 (로깅, 검증) |

```kotlin
import java.math.BigDecimal

data class Order(
    var orderId: String   = "",
    var symbol: String    = "",
    var quantity: Long    = 0L,
    var price: BigDecimal = BigDecimal.ZERO
)

// ① let: null이 아닐 때 변환, 결과를 다른 타입으로 매핑
val currentPrice: BigDecimal? = priceService.getPrice("SAMSUNG")
val commission = currentPrice?.let { price ->
    price * BigDecimal("0.0025")  // BigDecimal 반환
} ?: BigDecimal.ZERO

// ② run: 초기화 후 결과 계산 (this = 수신 객체)
val orderValue = Order().run {
    symbol   = "KAKAO"
    quantity = 10L
    price    = BigDecimal("54000")
    price * quantity.toBigDecimal()  // 반환값: BigDecimal
}

// ③ with: 객체를 첫 인자로 받아 멤버 다수 호출
val order = Order(orderId = "O001", symbol = "NAVER", quantity = 5L, price = BigDecimal("198000"))
val summary = with(order) {
    // this = order, order. 없이 멤버 접근
    "[$orderId] $symbol ${quantity}주 @$price"
}

// ④ apply: 객체 설정 (자신을 반환 → 체이닝 가능)
val newOrder = Order().apply {
    orderId  = OrderId.generate().value
    symbol   = "HYUNDAI"
    quantity = 15L
    price    = BigDecimal("210000")
}  // newOrder는 Order 타입

// ⑤ also: 사이드 이펙트만 추가, 객체 자신을 그대로 반환
val validatedOrder = newOrder
    .also { log.info("주문 생성: ${it.orderId}") }
    .also { auditService.record(it) }
    .also { require(it.quantity > 0L) { "수량 오류" } }
// validatedOrder == newOrder (동일한 객체)
```

### 스코프 함수 선택 기준

```
객체를 변환해서 다른 값을 원한다
  ├─ null 체크 포함      → let
  └─ 초기화 후 계산      → run / with

객체 자신을 그대로 반환하고 싶다
  ├─ 설정(builder)       → apply
  └─ 로깅/검증 부수효과  → also
```

---

## 7. Result 타입과 예외 처리 전략

```kotlin
import kotlin.Result

// runCatching: 예외를 Result로 감싼다
fun fetchPrice(symbol: String): Result<BigDecimal> = runCatching {
    priceService.getPrice(symbol)
        ?: throw NoSuchElementException("시세 없음: $symbol")
}

// 처리 패턴

// 1. fold: 성공/실패 분기
val displayPrice = fetchPrice("SAMSUNG").fold(
    onSuccess = { "₩$it" },
    onFailure = { "시세 조회 실패: ${it.message}" }
)

// 2. getOrDefault: 실패 시 기본값
val price = fetchPrice("KAKAO").getOrDefault(BigDecimal.ZERO)

// 3. getOrElse: 실패 시 람다로 기본값 생성
val price2 = fetchPrice("NAVER").getOrElse { ex ->
    log.warn("가격 조회 실패", ex)
    BigDecimal.ZERO
}

// 4. 체이닝: map → recover
val commission = fetchPrice("HYUNDAI")
    .map { it * BigDecimal("0.0025") }
    .recover { BigDecimal.ZERO }
    .getOrThrow()

// 5. onSuccess / onFailure: 부수효과 추가 (자신 반환)
fetchPrice("SAMSUNG")
    .onSuccess { log.info("시세 조회 성공: $it") }
    .onFailure { log.error("시세 조회 실패", it) }
```

---

## 8. require / check / error

```kotlin
// require: 함수 인자 유효성 → IllegalArgumentException
fun createOrder(symbol: String, quantity: Long, price: BigDecimal) {
    require(symbol.isNotBlank())       { "종목 코드는 비어 있을 수 없습니다" }
    require(quantity > 0L)             { "수량은 양수여야 합니다: $quantity" }
    require(price > BigDecimal.ZERO)   { "가격은 양수여야 합니다: $price" }
    require(quantity <= 100_000L)      { "단일 주문 한도 초과: $quantity" }
}

// check: 상태 유효성 → IllegalStateException
class TradingSession {
    private var isOpen = false

    fun open()  { isOpen = true  }
    fun close() { isOpen = false }

    fun placeOrder(order: Order) {
        check(isOpen) { "거래 세션이 열려 있지 않습니다" }
        // ...
    }
}

// error: 도달하면 안 되는 코드 → IllegalStateException + Nothing 반환
fun getExchangeCode(market: String): String = when (market) {
    "KOSPI"  -> "KSE"
    "KOSDAQ" -> "KQE"
    "KONEX"  -> "KNX"
    else     -> error("알 수 없는 시장: $market")
}

// 셋의 선택 기준
// require → 입력값 검증 (파라미터)
// check   → 현재 상태 검증 (인스턴스 상태)
// error   → 논리적으로 불가능한 상황
```

---

## 9. 구조분해 선언 (Destructuring Declaration)

```kotlin
// Pair / Triple
val (symbol, price) = Pair("SAMSUNG", BigDecimal("75300"))

// data class
data class TradeResult(val tradeId: String, val executedPrice: BigDecimal, val quantity: Long)
val result = TradeResult("T001", BigDecimal("75300"), 100L)
val (id, execPrice, qty) = result

// Map 순회
val portfolio: Map<String, Long> = mapOf("SAMSUNG" to 100L, "KAKAO" to 50L)
for ((sym, qty) in portfolio) {
    println("$sym: ${qty}주")
}

// _ 로 필요 없는 값 건너뜀
val (_, price2, quantity2) = result

// 커스텀 component 함수로 구조분해 지원
class Candle(val open: BigDecimal, val high: BigDecimal, val low: BigDecimal, val close: BigDecimal) {
    operator fun component1() = open
    operator fun component2() = high
    operator fun component3() = low
    operator fun component4() = close
}

val candle = Candle(
    BigDecimal("74000"), BigDecimal("76500"),
    BigDecimal("73500"), BigDecimal("75300")
)
val (open, high, low, close) = candle
println("O:$open H:$high L:$low C:$close")
```

---

## 10. 중위 함수 (Infix Function)

```kotlin
// infix: 점(.)과 괄호 없이 호출 가능
infix fun BigDecimal.isGreaterThan(other: BigDecimal): Boolean = this > other

infix fun BigDecimal.percentOf(base: BigDecimal): BigDecimal =
    this.divide(base, 6, java.math.RoundingMode.HALF_UP)
        .multiply(BigDecimal("100"))

// 사용
val profitRate = currentPrice percentOf buyPrice
if (currentPrice isGreaterThan BigDecimal("80000")) {
    sendAlert("목표가 달성")
}

// to 도 infix 함수
val entry = "SAMSUNG" to 100L  // Pair("SAMSUNG", 100L)

// 도메인 DSL 스타일
data class Holding(val symbol: String, val quantity: Long)
infix fun String.holds(quantity: Long) = Holding(symbol = this, quantity = quantity)

val h1 = "SAMSUNG" holds 100L
val h2 = "KAKAO"   holds 50L
```

---

## 11. typealias

긴 타입 이름에 별칭을 부여한다. 새 타입이 아닌 **컴파일 시 치환**되는 별칭이다.

```kotlin
// 도메인 의미를 타입 이름에 담기
typealias SymbolCode    = String
typealias Quantity      = Long
typealias TradeMap      = Map<SymbolCode, List<Trade>>
typealias PriceCallback = (SymbolCode, BigDecimal) -> Unit
typealias OrderFilter   = (Order) -> Boolean

// 함수 타입에 이름 붙여 가독성 향상
val isLargeOrder: OrderFilter = { it.quantity > 500L }
val isBuyOrder:   OrderFilter = { it.side == "BUY" }

// 합성
fun combineFilters(vararg filters: OrderFilter): OrderFilter =
    { order -> filters.all { it(order) } }

val largeByOrder = combineFilters(isLargeOrder, isBuyOrder)

// 사용
fun registerAlert(symbol: SymbolCode, callback: PriceCallback) {
    priceService.subscribe(symbol, callback)
}

registerAlert("SAMSUNG") { sym, price ->
    println("$sym 가격 알림: $price")
}
```

---

## 12. 코틀린답게 코드 쓰는 법 — 관용구 모음

### 12.1 Elvis로 조기 반환

```kotlin
// 안티패턴
fun processAccount(accountId: String?) {
    if (accountId == null) return
    val account = repository.find(accountId)
    if (account == null) return
    val balance = account.balance
    if (balance == null) return
    // 처리...
}

// 코틀린 관용구
fun processAccount(accountId: String?) {
    val id      = accountId ?: return
    val account = repository.find(id) ?: return
    val balance = account.balance ?: return
    // 처리...
}
```

### 12.2 when을 표현식으로 사용

```kotlin
// 안티패턴: if-else 체인
fun getOrderTypeName(type: OrderType): String {
    if (type == OrderType.LIMIT)      return "지정가"
    if (type == OrderType.MARKET)     return "시장가"
    if (type == OrderType.STOP)       return "스탑"
    if (type == OrderType.STOP_LIMIT) return "스탑 지정가"
    return "기타"
}

// 코틀린 관용구
fun getOrderTypeName(type: OrderType): String = when (type) {
    OrderType.LIMIT      -> "지정가"
    OrderType.MARKET     -> "시장가"
    OrderType.STOP       -> "스탑"
    OrderType.STOP_LIMIT -> "스탑 지정가"
}
// sealed enum이면 else 불필요 — 새 항목 추가 시 컴파일 경고
```

### 12.3 apply로 객체 초기화

```kotlin
// 안티패턴
val request = TradeRequest()
request.symbol   = "SAMSUNG"
request.quantity = 10L
request.price    = BigDecimal("75300")
request.side     = "BUY"

// 코틀린 관용구
val request = TradeRequest().apply {
    symbol   = "SAMSUNG"
    quantity = 10L
    price    = BigDecimal("75300")
    side     = "BUY"
}
```

### 12.4 also로 파이프라인 중간 로깅

```kotlin
val result = fetchOrders(accountId)
    .also { log.debug("주문 조회: ${it.size}건") }
    .filter { it.status == OrderStatus.Pending }
    .also { log.debug("대기 주문: ${it.size}건") }
    .map { processOrder(it) }
    .also { log.debug("처리 완료: ${it.size}건") }
```

### 12.5 takeIf / takeUnless

```kotlin
// takeIf: 조건 참이면 자신, 거짓이면 null
val validPrice = price.takeIf { it > BigDecimal.ZERO }

// takeUnless: 조건 거짓이면 자신, 참이면 null
val nonZeroQty = quantity.takeUnless { it == 0L }

// 체이닝
fun getValidatedPrice(raw: BigDecimal?): BigDecimal? =
    raw?.takeIf  { it > BigDecimal.ZERO }
       ?.takeIf  { it < BigDecimal("10_000_000") }  // 상한선 체크
       ?.takeUnless { it.scale() > 2 }              // 소수 2자리 초과 거부
```

### 12.6 repeat / forEachIndexed

```kotlin
// repeat: N회 반복
fun submitWithRetry(order: Order, maxRetries: Int = 3) {
    repeat(maxRetries) { attempt ->
        val result = runCatching { exchange.submit(order) }
        result.onSuccess { return }
        log.warn("시도 ${attempt + 1}/$maxRetries 실패")
    }
    throw RuntimeException("주문 제출 실패: ${order.orderId}")
}

// forEachIndexed: 인덱스 필요할 때
trades.forEachIndexed { index, trade ->
    println("${index + 1}. ${trade.symbol} @${trade.price}")
}
```

### 12.7 run의 블록 범위 활용

```kotlin
// run: 임시 변수를 블록 밖으로 노출하지 않을 때
val netValue = run {
    val grossValue = currentPrice * quantity.toBigDecimal()
    val commission = grossValue * BigDecimal("0.0025")
    grossValue - commission  // run의 반환값
}
// grossValue, commission은 이 블록 밖에서 보이지 않음
```

---

## 13. 핵심 정리

### 제네릭 체크리스트

- [ ] `<T : SomeType>` 제약으로 타입 안전성을 확보한다
- [ ] 생산자(Producer)는 `out`, 소비자(Consumer)는 `in`으로 변성을 선언한다
- [ ] 런타임 타입이 필요하면 `inline` + `reified`를 쓴다
- [ ] 타입 파라미터가 중요하지 않으면 `*`(스타 프로젝션)을 쓴다

### 관용구 체크리스트

- [ ] `let` / `run` / `with` / `apply` / `also`의 차이를 안다
- [ ] `runCatching`으로 예외를 `Result`로 변환한다
- [ ] `require` / `check` / `error`로 전제조건을 강제한다
- [ ] `typealias`로 도메인 의도를 타입 이름에 담는다
- [ ] `infix`로 읽기 좋은 DSL 스타일을 만든다
- [ ] `takeIf` / `takeUnless`로 조건부 null 처리를 간결하게 한다

### 코틀린 코드 품질 기준

| 나쁜 냄새 | 코틀린 해결책 |
|----------|-------------|
| `if (x != null) { x.foo() }` | `x?.foo()` |
| `if (x == null) return; x.foo()` | `val v = x ?: return; v.foo()` |
| 설정 코드 6줄 반복 | `apply { }` 블록 |
| `try { ... } catch (e: Exception) { null }` | `runCatching { }.getOrNull()` |
| `as CastType` (실패 위험) | `as? CastType` + null 처리 |
| 반복되는 타입 체인 | `typealias` |
| 예외 메시지 없는 null 체크 | `require(x != null) { "설명" }` |

### 스코프 함수 빠른 참조 카드

```kotlin
// null이 아닐 때 변환 → let
value?.let { transform(it) }

// 객체 설정 후 반환 → apply
SomeObject().apply { field = value }

// 부수효과 추가 후 그대로 통과 → also
obj.also { log.debug("$it") }

// 멤버 반복 호출 → with / run
with(obj) { foo(); bar(); baz() }

// 블록 결과 계산 → run (수신 객체 없을 때)
val x = run { compute(); result }
```

---

이전: [20. 코틀린 컬렉션과 함수형 프로그래밍](20-kotlin-collections) · 다음: [22. Gradle 빌드](22-gradle-build) · [전체 커리큘럼](/curriculum)
