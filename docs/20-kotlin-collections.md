# 20. 코틀린 컬렉션과 함수형 프로그래밍

> **대상**: 주식 투자 경험은 있으나 코틀린/개발이 처음인 증권사 백엔드 지망자  
> **목표**: 코틀린 컬렉션 연산을 자유자재로 쓰고, 대량 거래 데이터를 함수형 방식으로 처리한다

---

## 1. 가변 vs 불변 컬렉션

코틀린은 **읽기 전용(read-only)** 과 **변경 가능(mutable)** 컬렉션을 타입 수준에서 구분한다.

| 인터페이스 | 설명 | 생성 함수 |
|-----------|------|-----------|
| `List<T>` | 읽기 전용 리스트 | `listOf()` |
| `MutableList<T>` | 추가/삭제 가능 | `mutableListOf()` |
| `Set<T>` | 읽기 전용 집합 | `setOf()` |
| `MutableSet<T>` | 변경 가능 집합 | `mutableSetOf()` |
| `Map<K,V>` | 읽기 전용 맵 | `mapOf()` |
| `MutableMap<K,V>` | 변경 가능 맵 | `mutableMapOf()` |

```kotlin
import java.math.BigDecimal
import java.time.LocalDate

data class Trade(
    val tradeId: String,
    val symbol: String,
    val quantity: Long,
    val price: BigDecimal,
    val side: String,          // "BUY" or "SELL"
    val executedAt: LocalDate
)

// 읽기 전용 — 컴파일 수준에서 수정 시도 차단
val trades: List<Trade> = listOf(
    Trade("T001", "SAMSUNG",  10L, BigDecimal("75300"),  "BUY",  LocalDate.of(2025, 6, 1)),
    Trade("T002", "KAKAO",     5L, BigDecimal("54000"),  "BUY",  LocalDate.of(2025, 6, 2)),
    Trade("T003", "SAMSUNG",   3L, BigDecimal("78000"),  "SELL", LocalDate.of(2025, 6, 3)),
    Trade("T004", "NAVER",     8L, BigDecimal("198000"), "BUY",  LocalDate.of(2025, 6, 3)),
    Trade("T005", "KAKAO",     2L, BigDecimal("56000"),  "SELL", LocalDate.of(2025, 6, 4)),
    Trade("T006", "HYUNDAI",  15L, BigDecimal("210000"), "BUY",  LocalDate.of(2025, 6, 5)),
)

// 변경 가능
val watchList: MutableSet<String> = mutableSetOf("SAMSUNG", "KAKAO")
watchList.add("NAVER")     // OK
watchList.remove("KAKAO")  // OK
```

> **함정(Gotcha)**: `listOf()`는 읽기 전용 **뷰(view)** 를 반환하지, 내부적으로 불변 객체가 아니다.
> `List<T>`를 받더라도 실제 구현이 `MutableList`라면 캐스팅으로 수정할 수 있다.
> 진정한 불변 컬렉션이 필요하면 `java.util.Collections.unmodifiableList()` 또는
> `kotlinx.collections.immutable` 라이브러리를 사용하라.

---

## 2. 다양한 컬렉션 생성

```kotlin
// 크기로 초기화 (인덱스 이용)
val priceHistory = MutableList(30) { index -> BigDecimal.ZERO }

// buildList / buildMap (DSL 스타일)
val portfolio = buildMap<String, Long> {
    put("SAMSUNG", 100L)
    put("KAKAO", 50L)
    put("NAVER", 30L)
}

// buildList: 조건부 추가
val symbols = buildList {
    add("SAMSUNG")
    add("KAKAO")
    if (portfolio.containsKey("NAVER")) add("NAVER")
}

// Array → List 변환
val symbolArray = arrayOf("SAMSUNG", "KAKAO", "NAVER")
val symbolList  = symbolArray.toList()

// 범위를 리스트로
val tickRange = (1..100).toList()

// mapOf — 가독성 좋은 to 중위 함수
val priceMap = mapOf(
    "SAMSUNG" to BigDecimal("75300"),
    "KAKAO"   to BigDecimal("54000"),
    "NAVER"   to BigDecimal("198000")
)
```

---

## 3. 핵심 컬렉션 연산 완전정복

### 3.1 filter / filterNot / filterNotNull

```kotlin
// filter: 조건에 맞는 것만 추출
val buyTrades   = trades.filter { it.side == "BUY" }
val largeTrades = trades.filter { it.quantity * it.price > BigDecimal("500_000") }

// filterNot: 조건이 거짓인 것만
val nonSamsung = trades.filterNot { it.symbol == "SAMSUNG" }

// filterNotNull: null 제거
val prices: List<BigDecimal?> = listOf(BigDecimal("75300"), null, BigDecimal("54000"))
val nonNullPrices: List<BigDecimal> = prices.filterNotNull()
```

### 3.2 map / mapNotNull / flatMap

```kotlin
// map: 각 요소를 변환
val symbolList  = trades.map { it.symbol }
val tradeValues = trades.map { it.quantity.toBigDecimal() * it.price }

// mapNotNull: 변환 결과 null 제거
val validPrices = trades.mapNotNull { trade ->
    if (trade.price > BigDecimal.ZERO) trade.price else null
}

// flatMap: 중첩 리스트 평탄화
data class Portfolio(val ownerId: String, val trades: List<Trade>)
val portfolios: List<Portfolio> = listOf(
    Portfolio("USER-001", trades.take(3)),
    Portfolio("USER-002", trades.drop(3))
)

val allTrades: List<Trade> = portfolios.flatMap { it.trades }
println("전체 거래 수: ${allTrades.size}")  // 6
```

### 3.3 groupBy / associate / associateBy

```kotlin
// groupBy: 키 기준으로 그룹핑 → Map<K, List<V>>
val tradesBySymbol: Map<String, List<Trade>> = trades.groupBy { it.symbol }
// { "SAMSUNG" -> [T001, T003], "KAKAO" -> [T002, T005], ... }

// groupBy + transform: 값도 변환
val quantityBySymbol: Map<String, List<Long>> =
    trades.groupBy({ it.symbol }, { it.quantity })

// associateBy: 고유 키로 맵 생성 (중복 키는 마지막 값 우선)
val latestTradeBySymbol: Map<String, Trade> =
    trades.associateBy { it.symbol }

// associate: 키-값 쌍을 직접 지정
val symbolToTotalQty: Map<String, Long> =
    trades.groupBy { it.symbol }
          .associate { (symbol, list) ->
              symbol to list.sumOf { it.quantity }
          }

println(symbolToTotalQty)  // {SAMSUNG=13, KAKAO=7, NAVER=8, HYUNDAI=15}
```

### 3.4 partition — 두 그룹으로 분할

```kotlin
// partition: Pair(true 리스트, false 리스트) 반환
val (buyList, sellList) = trades.partition { it.side == "BUY" }
println("매수: ${buyList.size}건, 매도: ${sellList.size}건")

// 대량 주문 vs 소량 주문 분리
val (largeTrades2, smallTrades) = trades.partition {
    it.quantity * it.price >= BigDecimal("1_000_000")
}
```

### 3.5 fold / reduce — 집계

```kotlin
// fold: 초기값 있는 집계
val totalAmount: BigDecimal = trades.fold(BigDecimal.ZERO) { acc, trade ->
    acc + trade.price * trade.quantity.toBigDecimal()
}

// reduce: 초기값 없는 집계 (빈 컬렉션에서 NoSuchElementException)
val maxPrice: BigDecimal = trades
    .map { it.price }
    .reduce { a, b -> if (a > b) a else b }

// sumOf: 숫자 합산 단축 함수
val totalQty: Long      = trades.sumOf { it.quantity }
val totalValue: BigDecimal = trades.sumOf { it.price * it.quantity.toBigDecimal() }

// 안전한 집계: 빈 리스트 대비
val safeMax = trades.maxOfOrNull { it.price }  // null-safe
```

### 3.6 maxByOrNull / minByOrNull / sortedBy

```kotlin
val mostExpensive = trades.maxByOrNull { it.price }
val cheapest      = trades.minByOrNull { it.price }

// sortedBy: 불변 정렬 (새 리스트 반환)
val sortedByPrice      = trades.sortedBy { it.price }
val sortedByValueDesc  = trades.sortedByDescending {
    it.price * it.quantity.toBigDecimal()
}

// 복합 정렬: compareBy + thenBy
val sorted = trades.sortedWith(
    compareBy<Trade> { it.symbol }
        .thenByDescending { it.executedAt }
        .thenByDescending { it.quantity }
)
```

### 3.7 distinct / distinctBy

```kotlin
val uniqueSymbols: List<String> = trades.map { it.symbol }.distinct()
// [SAMSUNG, KAKAO, NAVER, HYUNDAI]

// distinctBy: 특정 키 기준 중복 제거 (첫 번째 항목 유지)
val firstTradePerSymbol: List<Trade> = trades.distinctBy { it.symbol }
```

### 3.8 chunked / windowed

```kotlin
// chunked: N개씩 배치로 분할
val batches: List<List<Trade>> = trades.chunked(2)
// [[T001, T002], [T003, T004], [T005, T006]]

// 실무: 대량 DB 삽입 배치 처리
val allTrades2: List<Trade> = fetchAllTrades()
allTrades2.chunked(500).forEach { batch ->
    tradeRepository.saveAll(batch)  // 500건씩 삽입
}

// windowed: 슬라이딩 윈도우 (이동평균 등)
val prices = listOf(
    BigDecimal("74000"), BigDecimal("75300"), BigDecimal("76500"),
    BigDecimal("75800"), BigDecimal("77200"), BigDecimal("78000")
)

// 3일 이동평균
val ma3: List<BigDecimal> = prices.windowed(size = 3) { window ->
    window.fold(BigDecimal.ZERO, BigDecimal::add)
          .divide(BigDecimal("3"), 2, java.math.RoundingMode.HALF_UP)
}
println("3일 이동평균: $ma3")
// [75266.67, 75866.67, 76500.00, 77000.00]
```

### 3.9 zip / unzip

```kotlin
val symbols   = listOf("SAMSUNG", "KAKAO", "NAVER")
val quantities = listOf(100L, 50L, 30L)

// zip: 같은 인덱스끼리 쌍으로 묶기 (짧은 쪽 기준)
val holdings: List<Pair<String, Long>> = symbols.zip(quantities)
// [(SAMSUNG, 100), (KAKAO, 50), (NAVER, 30)]

// zip with transform
val displayLines = symbols.zip(quantities) { sym, qty -> "$sym: ${qty}주" }

// unzip: Pair 리스트를 두 리스트로 분리
val (syms, qtys) = holdings.unzip()
```

### 3.10 any / all / none / count

```kotlin
// any: 하나라도 조건 만족
val hasBuyOrder = trades.any { it.side == "BUY" }

// all: 모두 조건 만족
val allPositiveQty = trades.all { it.quantity > 0L }

// none: 조건 만족하는 것이 없음
val noNegativePrice = trades.none { it.price < BigDecimal.ZERO }

// count: 조건 만족 개수
val buyCount = trades.count { it.side == "BUY" }
```

---

## 4. Sequence — 지연 평가 (Lazy Evaluation)

### 4.1 컬렉션 vs 시퀀스 차이

| | 컬렉션 (Eager) | 시퀀스 (Lazy) |
|--|---------------|--------------|
| 평가 시점 | 각 연산 즉시 전체 처리 | 최종 연산 시 원소별 처리 |
| 중간 컬렉션 | 각 단계마다 생성 | 생성 안 함 |
| 적합한 크기 | 소~중간 (수만 건 이하) | 대용량 또는 무한 |
| 단락(short-circuit) | `first`/`take` 사용 시 | 자동 단락 |

```kotlin
// 컬렉션: filter → map 각각 전체 순회, 중간 리스트 생성
val resultEager = trades
    .filter { it.side == "BUY" }     // 새 List 생성 (6원소 → 4원소)
    .map { it.symbol }               // 또 새 List 생성
    .take(2)                         // 또 새 List 생성

// 시퀀스: 한 번의 순회로 처리, 중간 컬렉션 없음
val resultLazy = trades.asSequence()
    .filter { it.side == "BUY" }     // 지연: 아직 아무것도 안 함
    .map { it.symbol }               // 지연: 아직 아무것도 안 함
    .take(2)                         // 지연: 아직 아무것도 안 함
    .toList()                        // 여기서 한 번만 순회, 2개 찾으면 즉시 중단
```

### 4.2 대용량 거래내역 처리 — 성능 비교

```kotlin
// 10만 건 거래 데이터 처리 시나리오

// 방법 1: 컬렉션 (중간 리스트 최대 3개 생성, 메모리 부담)
fun processEager(allTrades: List<Trade>): List<String> {
    return allTrades
        .filter { it.side == "BUY" }           // 중간 List (최대 10만 건)
        .filter { it.quantity > 50L }           // 또 중간 List
        .map { "${it.symbol}:${it.quantity}" }  // 또 중간 List
        .take(100)                              // 최종 100개
}

// 방법 2: 시퀀스 (중간 리스트 0개, 100개 찾으면 즉시 중단)
fun processLazy(allTrades: List<Trade>): List<String> {
    return allTrades.asSequence()
        .filter { it.side == "BUY" }
        .filter { it.quantity > 50L }
        .map { "${it.symbol}:${it.quantity}" }
        .take(100)
        .toList()
}
```

### 4.3 generateSequence — 무한 시퀀스

```kotlin
// 피보나치 수열 (무한 시퀀스)
val fibonacci: Sequence<Long> = generateSequence(Pair(0L, 1L)) { (a, b) ->
    Pair(b, a + b)
}.map { it.first }

println(fibonacci.take(10).toList())  // [0, 1, 1, 2, 3, 5, 8, 13, 21, 34]

// 실무 예시: 페이지 단위 API 호출 (커서 기반 페이지네이션)
data class PageResult(val trades: List<Trade>, val nextCursor: String?)

fun fetchAllTradesLazily(accountId: String, apiClient: ApiClient): Sequence<Trade> =
    generateSequence(apiClient.getTrades(accountId, cursor = null)) { page ->
        page.nextCursor?.let { apiClient.getTrades(accountId, cursor = it) }
    }.flatMap { it.trades.asSequence() }

// 메모리에 전부 올리지 않고 스트리밍 처리
fetchAllTradesLazily("ACC-001", apiClient)
    .filter { it.side == "BUY" }
    .take(1000)
    .forEach { processTrade(it) }
```

### 4.4 언제 Sequence를 쓰나?

> **Sequence 사용 기준**
> - 연산 단계가 3개 이상이고 데이터가 수천 건 이상일 때
> - `take`, `first`, `find` 등 조기 종료가 있을 때
> - 파일/DB 스트림처럼 전체를 메모리에 올리기 어려울 때
>
> **컬렉션 사용 기준**
> - 데이터가 수백 건 이하일 때
> - `groupBy`, `sortedBy`처럼 전체 데이터를 봐야 하는 연산 (Sequence 이점 없음)
> - 디버깅이 중요할 때 (Sequence는 평가 시점이 지연돼 디버깅이 어려움)

---

## 5. 실전 예제 — 종목별 손익 집계

```kotlin
import java.math.BigDecimal
import java.math.RoundingMode

data class SymbolPnL(
    val symbol: String,
    val totalBuyAmount: BigDecimal,
    val totalSellAmount: BigDecimal,
    val netQuantity: Long,       // 순보유 수량 (매수 - 매도)
    val realizedPnL: BigDecimal  // 실현 손익 (단순 FIFO 근사)
)

fun calculatePnLBySymbol(trades: List<Trade>): List<SymbolPnL> {
    return trades
        .groupBy { it.symbol }
        .map { (symbol, symbolTrades) ->
            val buyTrades  = symbolTrades.filter { it.side == "BUY" }
            val sellTrades = symbolTrades.filter { it.side == "SELL" }

            val totalBuyAmount  = buyTrades.sumOf  { it.price * it.quantity.toBigDecimal() }
            val totalSellAmount = sellTrades.sumOf { it.price * it.quantity.toBigDecimal() }

            val totalBuyQty  = buyTrades.sumOf  { it.quantity }
            val totalSellQty = sellTrades.sumOf { it.quantity }

            // 평균 매수가 계산
            val avgBuyPrice = if (totalBuyQty > 0L)
                totalBuyAmount.divide(totalBuyQty.toBigDecimal(), 2, RoundingMode.HALF_UP)
            else BigDecimal.ZERO

            // 실현 손익 = (매도가 - 평균매수가) × 매도수량
            val realizedPnL = sellTrades.sumOf { sell ->
                (sell.price - avgBuyPrice) * sell.quantity.toBigDecimal()
            }

            SymbolPnL(
                symbol          = symbol,
                totalBuyAmount  = totalBuyAmount,
                totalSellAmount = totalSellAmount,
                netQuantity     = totalBuyQty - totalSellQty,
                realizedPnL     = realizedPnL.setScale(2, RoundingMode.HALF_UP)
            )
        }
        .sortedByDescending { it.realizedPnL }  // 수익 높은 순 정렬
}

// 실행
val result = calculatePnLBySymbol(trades)
result.forEach { pnl ->
    val sign = if (pnl.realizedPnL >= BigDecimal.ZERO) "+" else ""
    println("${pnl.symbol.padEnd(10)}: 실현 손익 $sign${pnl.realizedPnL}, 잔여 ${pnl.netQuantity}주")
}
```

---

## 6. 실전 예제 — 일별 거래 통계

```kotlin
data class DailyStats(
    val date: LocalDate,
    val tradeCount: Int,
    val totalVolume: Long,
    val totalAmount: BigDecimal,
    val buyCount: Int,
    val sellCount: Int,
    val topSymbol: String
)

fun calculateDailyStats(trades: List<Trade>): List<DailyStats> {
    return trades
        .groupBy { it.executedAt }              // Map<LocalDate, List<Trade>>
        .map { (date, dayTrades) ->
            val (buys, sells) = dayTrades.partition { it.side == "BUY" }

            val topSymbol = dayTrades
                .groupBy { it.symbol }
                .maxByOrNull { (_, list) -> list.sumOf { it.quantity } }
                ?.key ?: "N/A"

            DailyStats(
                date        = date,
                tradeCount  = dayTrades.size,
                totalVolume = dayTrades.sumOf { it.quantity },
                totalAmount = dayTrades.sumOf { it.price * it.quantity.toBigDecimal() },
                buyCount    = buys.size,
                sellCount   = sells.size,
                topSymbol   = topSymbol
            )
        }
        .sortedBy { it.date }
}
```

---

## 7. 실전 예제 — 이동평균 계산

```kotlin
// 주가 데이터
data class Candle(val date: LocalDate, val close: BigDecimal)

fun calculateMovingAverages(
    candles: List<Candle>,
    periods: List<Int> = listOf(5, 20, 60)
): Map<Int, List<Pair<LocalDate, BigDecimal>>> {
    return periods.associateWith { period ->
        candles
            .windowed(size = period) { window ->
                val date = window.last().date
                val avg  = window
                    .map { it.close }
                    .fold(BigDecimal.ZERO, BigDecimal::add)
                    .divide(period.toBigDecimal(), 2, java.math.RoundingMode.HALF_UP)
                date to avg
            }
    }
}
```

---

## 8. 구조분해 선언 (Destructuring)

```kotlin
// data class: componentN 함수로 구조분해
val (id, sym, qty, price) = trades.first()

// Map.Entry 구조분해
tradesBySymbol.forEach { (symbol, tradeList) ->
    println("$symbol: ${tradeList.size}건")
}

// Pair 구조분해
val (buyList2, sellList2) = trades.partition { it.side == "BUY" }

// _ 로 불필요한 값 무시
val (_, symbol, quantity) = trades.first()

// for 루프에서 구조분해
for ((index, trade) in trades.withIndex()) {
    println("${index + 1}. ${trade.symbol} @${trade.price}")
}
```

---

## 9. 함수형 사고방식 — 명령형 vs 함수형

```kotlin
// 명령형 스타일 (전통적 for 루프)
fun summarizeImperative(trades: List<Trade>): String {
    var totalBuy = BigDecimal.ZERO
    val symbols  = mutableSetOf<String>()
    var count    = 0
    for (trade in trades) {
        if (trade.side == "BUY") {
            totalBuy += trade.price * trade.quantity.toBigDecimal()
            symbols.add(trade.symbol)
            count++
        }
    }
    return "매수 ${count}건, 종목 ${symbols.size}개, 총 ${totalBuy}"
}

// 함수형 스타일 (체이닝) — 더 선언적이고 테스트 쉬움
fun summarizeFunctional(trades: List<Trade>): String {
    val buyTrades = trades.filter { it.side == "BUY" }
    val totalBuy  = buyTrades.sumOf { it.price * it.quantity.toBigDecimal() }
    val symbols   = buyTrades.map { it.symbol }.distinct()
    return "매수 ${buyTrades.size}건, 종목 ${symbols.size}개, 총 $totalBuy"
}
```

> **함수형 사고의 핵심**: 데이터를 **변환(transform)** 하는 파이프라인으로 생각하라.
> 상태 변경 대신 새로운 값을 반환한다. 코드가 짧고 병렬화·테스트가 쉬워진다.

---

## 10. 핵심 정리

### 연산 선택 가이드

| 목적 | 함수 |
|------|------|
| 조건 필터 | `filter` / `filterNot` / `filterNotNull` |
| 변환 | `map` / `mapNotNull` |
| 중첩 평탄화 | `flatMap` |
| 그룹핑 | `groupBy` |
| 유일 키 맵 | `associateBy` |
| 두 그룹 분리 | `partition` |
| 숫자 합산 | `sumOf` |
| 일반 집계 | `fold` (초기값 있음) / `reduce` |
| 최대/최소 | `maxByOrNull` / `minByOrNull` |
| 중복 제거 | `distinct` / `distinctBy` |
| 배치 분할 | `chunked` |
| 슬라이딩 윈도우 | `windowed` |
| 쌍으로 묶기 | `zip` |
| 조건 검사 | `any` / `all` / `none` |
| 대용량 처리 | `asSequence()` 추가 |

### 체크리스트

- [ ] `filter` → `map` → `take` 3단계 이상 + 대용량이면 `asSequence()` 추가를 검토한다
- [ ] `groupBy` 결과는 `Map<K, List<V>>`임을 기억한다
- [ ] `fold`와 `reduce`의 차이(초기값 유무, 빈 리스트 처리)를 안다
- [ ] `partition`으로 두 그룹을 한 번에 분리한다
- [ ] 구조분해로 `map.forEach { (k, v) -> }` 패턴을 쓴다
- [ ] 이동평균·배치 처리에 `windowed` / `chunked`를 쓴다

---

이전: [19. 코틀린 객체지향과 설계](19-kotlin-oop.md) · 다음: [21. 코틀린 제네릭과 관용구](21-kotlin-generics-and-idioms.md) · [전체 커리큘럼](../CURRICULUM.md)
