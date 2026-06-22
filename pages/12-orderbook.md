# 12. 심화: 호가창(Order Book) 자료구조와 매칭

시세/체결의 심장입니다. 실행 예제: [`examples/OrderBook.kt`](/examples) (검증 완료)

## 호가창이란?

특정 종목에 대해 "사겠다/팔겠다"는 주문이 가격별로 쌓인 장부입니다.

```
─ 호가창 (삼성전자) ──────
  매도 70300  | 50      ← 매도호가(asks): 낮은 가격이 먼저 체결됨
  매도 70200  | 30
  매도 70100  | 20      ← 최우선매도(best ask)
  ----------------- (스프레드: 100)
  매수 70000  | 40      ← 최우선매수(best bid)
  매수 69900  | 60
  매수 69800  | 100     ← 매수호가(bids): 높은 가격이 먼저 체결됨
──────────────────────
```

| 개념 | 뜻 |
|------|-----|
| **매수호가(bid)** | 사겠다는 가격. **높을수록** 우선 |
| **매도호가(ask)** | 팔겠다는 가격. **낮을수록** 우선 |
| **최우선매수(best bid)** | 가장 높은 매수 가격 (70000) |
| **최우선매도(best ask)** | 가장 낮은 매도 가격 (70100) |
| **스프레드(spread)** | best ask − best bid (100) |

## 왜 TreeMap을 쓰나?

가격순 정렬을 항상 유지해야 "최우선 호가"를 빠르게 찾습니다.

```kotlin
// 매수: 높은 가격이 맨 위 (내림차순)
private val bids = TreeMap<BigDecimal, Int>(compareByDescending<BigDecimal> { it })
// 매도: 낮은 가격이 맨 위 (오름차순, 기본)
private val asks = TreeMap<BigDecimal, Int>()

fun bestBid() = bids.firstKey()   // O(log n)
fun bestAsk() = asks.firstKey()
```

- `TreeMap`은 키를 정렬 상태로 보관 → `firstKey()`로 최우선 호가를 즉시 조회.
- 가격이 키, 그 가격의 누적 수량이 값.

## 매칭 (시장가 매수 체결)

시장가 매수는 **가장 싼 매도호가부터** 차례로 먹습니다.

```kotlin
fun marketBuy(quantity: Int): List<String> {
    var remaining = quantity
    while (remaining > 0 && asks.isNotEmpty()) {
        val price = asks.firstKey()          // 제일 싼 매도
        val available = asks[price]!!
        val fillQty = minOf(remaining, available)
        // ... 체결 기록 ...
        remaining -= fillQty
        if (fillQty == available) asks.remove(price)   // 물량 소진 → 호가 제거
        else asks[price] = available - fillQty         // 부분 체결
    }
    // remaining > 0 이면 미체결 잔량
}
```

### 실행 결과 (35주 시장가 매수)

```
[시장가 35주 매수]
  체결: 20주 @ 70100원   ← 최우선매도 전량 소진
  체결: 15주 @ 70200원   ← 다음 호가에서 부분 체결

체결 후: 70100 호가 사라지고, 70200은 15주 남음. 스프레드 100 → 200
```

이것이 **부분체결(partial fill)**의 원리입니다. 한 주문이 여러 가격에 나눠 체결될 수 있습니다.

## 실무에서 더 고려할 것

| 주제 | 설명 |
|------|------|
| **가격-시간 우선** | 같은 가격이면 먼저 낸 주문이 먼저 체결 (FIFO 큐 필요) |
| **성능** | 초당 수만 건 → 자료구조와 GC 최적화가 핵심 |
| **호가 단계** | 보통 10단계 호가만 노출, 내부는 전체 보관 |
| **동시성** | 호가창 갱신도 [동시성 보호](11-concurrency) 필요 |
| **시장가 보호** | 매도 물량 부족 시 미체결/거부 처리 정책 |

> 우리 예제는 "가격별 누적 수량"만 다루지만, 실제 거래소는 각 가격 안에서 **개별 주문을 시간순 큐**로 관리합니다.

---

다음: [13. 코루틴과 비동기](13-coroutines)
