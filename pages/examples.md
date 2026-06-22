# 실행 가능한 예제 코드

아래 4개 예제는 모두 **실제 컴파일·실행 검증 완료**입니다.
설치 없이 [play.kotlinlang.org](https://play.kotlinlang.org) 에 붙여넣고 **Run** 하면 바로 돌아갑니다.

## 1. OrderProcessor — 주문 처리기

주문 검증 → 체결 → 평단가 재계산.
관련 문서: [07. 실습 주문 처리기](07-order-system-tutorial)

```kotlin
/**
 * 작은 주문 처리기 (Order Processor) — 코틀린 학습용 예제
 *
 * 실제 증권 주문이 거치는 과정을 작게 재현합니다:
 *   주문 생성 → 검증 → 체결 → 잔고/예수금 반영 → 평단가 계산
 *
 * ▶ 실행 방법 (설치 불필요):
 *   1) https://play.kotlinlang.org 접속
 *   2) 이 파일 전체를 붙여넣기
 *   3) Run 클릭
 *
 * ▶ 로컬 실행 (코틀린 설치돼 있다면):
 *   kotlinc OrderProcessor.kt -include-runtime -d op.jar && java -jar op.jar
 */

import java.math.BigDecimal
import java.math.RoundingMode

// ── 1. 도메인 타입 정의 ───────────────────────────────────────────

enum class Side { BUY, SELL }            // 매수 / 매도
enum class OrderType { LIMIT, MARKET }   // 지정가 / 시장가

data class Order(
    val orderId: String,    // 주문 번호
    val symbol: String,     // 종목코드 (예: "005930" 삼성전자)
    val side: Side,         // 매수/매도
    val type: OrderType,    // 지정가/시장가
    val price: BigDecimal,  // 가격 (★금액은 항상 BigDecimal)
    val quantity: Int       // 수량
)

/** 보유 종목 1건 (수량 + 평단가) */
data class Position(
    var quantity: Int,
    var avgPrice: BigDecimal
)

data class Account(
    val accountId: String,
    var cash: BigDecimal,                                       // 예수금
    val positions: MutableMap<String, Position> = mutableMapOf() // 종목코드 → 보유정보
)

// 검증 결과: 성공 또는 사유 있는 거절
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Rejected(val reason: String) : ValidationResult()
}

// ── 2. 호가단위 (한국거래소 코스피 규칙 간략판) ──────────────────

fun tickSizeOf(price: BigDecimal): BigDecimal = when {
    price < BigDecimal(2000)   -> BigDecimal(1)
    price < BigDecimal(5000)   -> BigDecimal(5)
    price < BigDecimal(20000)  -> BigDecimal(10)
    price < BigDecimal(50000)  -> BigDecimal(50)
    price < BigDecimal(200000) -> BigDecimal(100)
    price < BigDecimal(500000) -> BigDecimal(500)
    else                       -> BigDecimal(1000)
}

// ── 3. 주문 검증 ─────────────────────────────────────────────────

fun validateOrder(order: Order, account: Account): ValidationResult {
    // 1) 수량 검증
    if (order.quantity <= 0) {
        return ValidationResult.Rejected("수량은 1주 이상이어야 합니다")
    }

    // 2) 호가단위 검증 (지정가만 — 시장가는 가격을 따지지 않음)
    if (order.type == OrderType.LIMIT) {
        val tick = tickSizeOf(order.price)
        if (order.price.remainder(tick) != BigDecimal.ZERO) {
            return ValidationResult.Rejected("호가단위(${tick}원)에 맞지 않습니다: ${order.price}")
        }
    }

    // 3) 매수: 예수금 검증
    if (order.side == Side.BUY) {
        val needed = order.price.multiply(BigDecimal(order.quantity))
        if (account.cash < needed) {
            return ValidationResult.Rejected("예수금 부족 (필요: $needed, 보유: ${account.cash})")
        }
    }

    // 4) 매도: 보유수량 검증
    if (order.side == Side.SELL) {
        val held = account.positions[order.symbol]?.quantity ?: 0   // 없으면 0 (null 안전)
        if (held < order.quantity) {
            return ValidationResult.Rejected("보유수량 부족 (보유: $held, 매도시도: ${order.quantity})")
        }
    }

    return ValidationResult.Success
}

// ── 4. 체결 처리 (잔고/예수금/평단가 반영) ───────────────────────

fun applyExecution(order: Order, account: Account) {
    val amount = order.price.multiply(BigDecimal(order.quantity))

    when (order.side) {
        Side.BUY -> {
            account.cash = account.cash.subtract(amount)
            val pos = account.positions[order.symbol]
            if (pos == null) {
                // 신규 매수
                account.positions[order.symbol] = Position(order.quantity, order.price)
            } else {
                // 추가 매수 → 평단가 재계산
                //   새 평단 = (기존수량*기존평단 + 신규수량*신규가) / 총수량
                val prevValue = pos.avgPrice.multiply(BigDecimal(pos.quantity))
                val newValue = order.price.multiply(BigDecimal(order.quantity))
                val totalQty = pos.quantity + order.quantity
                pos.avgPrice = prevValue.add(newValue)
                    .divide(BigDecimal(totalQty), 2, RoundingMode.HALF_UP) // 소수2자리 반올림
                pos.quantity = totalQty
            }
        }
        Side.SELL -> {
            account.cash = account.cash.add(amount)
            val pos = account.positions[order.symbol]!!  // 검증 통과했으니 존재 보장
            pos.quantity -= order.quantity               // 매도는 평단가 유지, 수량만 감소
            if (pos.quantity == 0) {
                account.positions.remove(order.symbol)   // 전량 매도 시 제거
            }
        }
    }
}

// ── 5. 한 건 처리 흐름 (검증 → 체결 → 출력) ─────────────────────

fun process(order: Order, account: Account) {
    println("\n[주문] ${order.orderId} | ${order.symbol} | ${order.side} | ${order.type} | ${order.price}원 x ${order.quantity}주")
    when (val result = validateOrder(order, account)) {
        is ValidationResult.Success -> {
            applyExecution(order, account)
            println("  ✅ 체결 완료 | 예수금: ${account.cash}원")
            account.positions.forEach { (symbol, pos) ->
                println("     보유: $symbol → ${pos.quantity}주 (평단 ${pos.avgPrice}원)")
            }
        }
        is ValidationResult.Rejected -> {
            println("  ❌ 거절: ${result.reason}")
        }
    }
}

// ── 6. 시나리오 실행 ─────────────────────────────────────────────

fun main() {
    val account = Account("ACC-001", cash = BigDecimal("2000000"))
    println("=== 초기 계좌: 예수금 ${account.cash}원 ===")

    // ① 정상 매수: 삼성전자 7만원 x 10주
    process(Order("ORD-001", "005930", Side.BUY, OrderType.LIMIT, BigDecimal("70000"), 10), account)

    // ② 추가 매수: 삼성전자 8만원 x 5주 → 평단가 재계산 확인
    process(Order("ORD-002", "005930", Side.BUY, OrderType.LIMIT, BigDecimal("80000"), 5), account)

    // ③ 호가단위 위반 (5만원↑은 50원 단위인데 71,001원)
    process(Order("ORD-003", "005930", Side.BUY, OrderType.LIMIT, BigDecimal("71001"), 1), account)

    // ④ 예수금 부족 (남은 현금보다 큰 주문)
    process(Order("ORD-004", "005930", Side.BUY, OrderType.LIMIT, BigDecimal("70000"), 100), account)

    // ⑤ 일부 매도: 삼성전자 7주 매도
    process(Order("ORD-005", "005930", Side.SELL, OrderType.LIMIT, BigDecimal("85000"), 7), account)

    // ⑥ 보유수량 초과 매도 (현재 8주 보유인데 20주 매도 시도)
    process(Order("ORD-006", "005930", Side.SELL, OrderType.LIMIT, BigDecimal("85000"), 20), account)

    println("\n=== 최종 계좌 ===")
    println("예수금: ${account.cash}원")
    account.positions.forEach { (symbol, pos) ->
        println("보유: $symbol → ${pos.quantity}주 (평단 ${pos.avgPrice}원)")
    }
}

```

## 2. BigDecimalGuide — 금액 계산 함정

Double 오차·생성·반올림·compareTo 등 5가지 함정.
관련 문서: [10. 금액 계산 완전정복](10-bigdecimal-deep)

```kotlin
/**
 * 금액 계산 완전정복 — BigDecimal 함정과 올바른 사용법
 *
 * 증권 개발에서 가장 많은 사고가 "돈 계산"에서 납니다.
 * 이 예제는 흔한 함정 5가지와 올바른 해법을 직접 출력으로 보여줍니다.
 *
 * ▶ 실행: https://play.kotlinlang.org 에 붙여넣고 Run
 *        또는  kotlinc BigDecimalGuide.kt -include-runtime -d bg.jar && java -jar bg.jar
 */

import java.math.BigDecimal
import java.math.RoundingMode

fun main() {

    // ── 함정 1: Double은 오차가 생긴다 ──────────────────────────
    println("[함정 1] Double 소수점 오차")
    val wrong = 0.1 + 0.2
    println("  0.1 + 0.2 (Double)      = $wrong   ← 0.3이 아니다!")
    val right = BigDecimal("0.1").add(BigDecimal("0.2"))
    println("  0.1 + 0.2 (BigDecimal)  = $right")
    println()

    // ── 함정 2: BigDecimal 생성은 반드시 문자열로 ──────────────
    println("[함정 2] BigDecimal 생성 방법")
    val fromDouble = BigDecimal(0.1)        // ❌ Double을 넣으면 오차가 그대로 들어감
    val fromString = BigDecimal("0.1")      // ✅ 문자열로 넣어야 정확
    println("  BigDecimal(0.1)   = $fromDouble  ← 쓰레기값")
    println("  BigDecimal(\"0.1\") = $fromString")
    println()

    // ── 함정 3: 사칙연산은 메서드로 (+ 대신 .add 등) ───────────
    println("[함정 3] 사칙연산")
    val price = BigDecimal("70000")
    val qty = BigDecimal("10")
    println("  매수금액 = 70000 x 10 = ${price.multiply(qty)}")
    println("  차감 후  = 1000000 - 700000 = ${BigDecimal("1000000").subtract(price.multiply(qty))}")
    println()

    // ── 함정 4: 나눗셈은 scale(소수자리)과 반올림을 꼭 지정 ─────
    println("[함정 4] 나눗셈 — 평단가 계산")
    // 110만원어치 15주 → 평단가
    val totalValue = BigDecimal("1100000")
    val totalQty = BigDecimal("15")
    // divide만 쓰면 무한소수에서 예외 발생! → scale + RoundingMode 필수
    val avg = totalValue.divide(totalQty, 2, RoundingMode.HALF_UP)
    println("  1,100,000 / 15 = $avg  (소수 2자리 반올림)")
    // RoundingMode 종류 비교
    val v = BigDecimal("73333.335")
    println("  73333.335 반올림(HALF_UP)  = ${v.setScale(2, RoundingMode.HALF_UP)}")
    println("  73333.335 버림(DOWN)       = ${v.setScale(2, RoundingMode.DOWN)}")
    println("  73333.335 올림(UP)         = ${v.setScale(2, RoundingMode.UP)}")
    println()

    // ── 함정 5: 값 비교는 equals 말고 compareTo ────────────────
    println("[함정 5] 값 비교 (★실수 매우 잦음)")
    val a = BigDecimal("1.0")
    val b = BigDecimal("1.00")
    println("  1.0 == 1.00 ? equals    = ${a == b}      ← scale이 달라 false!")
    println("  1.0 == 1.00 ? compareTo = ${a.compareTo(b) == 0}    ← 값 비교는 compareTo")
    println()

    // ── 실전: 수수료/세금 계산 예시 ────────────────────────────
    println("[실전] 매도 시 수수료(0.015%) + 거래세(0.18%) 계산")
    val sellAmount = BigDecimal("850000")               // 매도금액
    val feeRate = BigDecimal("0.00015")                 // 수수료율
    val taxRate = BigDecimal("0.0018")                  // 거래세율
    val fee = sellAmount.multiply(feeRate).setScale(0, RoundingMode.DOWN)  // 원 미만 절사
    val tax = sellAmount.multiply(taxRate).setScale(0, RoundingMode.DOWN)
    val net = sellAmount.subtract(fee).subtract(tax)
    println("  매도금액 : $sellAmount 원")
    println("  수수료   : $fee 원")
    println("  거래세   : $tax 원")
    println("  실수령액 : $net 원")
}

```

## 3. RaceCondition — 동시성 잔고 꼬임

잠금 없이 동시 출금하면 잔고가 틀어지는 현장.
관련 문서: [11. 동시성과 잔고 정합성](11-concurrency)

```kotlin
/**
 * 동시성 사고 재현 — "왜 잔고가 꼬이는가" 를 눈으로 보기
 *
 * 같은 계좌에서 두 스레드가 동시에 출금하면, 잠금(lock)이 없으면
 * 예수금이 실제와 다르게 계산됩니다. 증권사에서 제일 무서운 버그입니다.
 *
 * 이 예제는 (1) 잠금 없는 버전 → 금액이 틀어짐
 *           (2) 잠금 있는 버전 → 항상 정확함
 * 을 비교해서 보여줍니다.
 *
 * ▶ 실행: kotlinc RaceCondition.kt -include-runtime -d rc.jar && java -jar rc.jar
 *   (play.kotlinlang.org 에서도 동작)
 */

import kotlin.concurrent.thread

// ── 1. 잠금 없는 계좌 (위험) ─────────────────────────────────────
class UnsafeAccount(var balance: Long)

// ── 2. 잠금 있는 계좌 (안전) ─────────────────────────────────────
class SafeAccount(var balance: Long) {
    private val lock = Any()
    fun withdraw(amount: Long) {
        synchronized(lock) {            // 한 번에 한 스레드만 진입
            balance -= amount
        }
    }
}

fun main() {
    val repeatCount = 100_000
    val withdrawAmount = 1L

    // ── 시나리오 A: 잠금 없음 ──────────────────────────────────
    val unsafe = UnsafeAccount(balance = 2 * repeatCount.toLong())
    val t1 = thread { repeat(repeatCount) { unsafe.balance -= withdrawAmount } }
    val t2 = thread { repeat(repeatCount) { unsafe.balance -= withdrawAmount } }
    t1.join(); t2.join()

    println("[A. 잠금 없음]")
    println("  기대 잔고: 0")
    println("  실제 잔고: ${unsafe.balance}   ← 0이 아니면 데이터 꼬임 발생!")
    println("  (balance -= 1 이 '읽기→계산→쓰기' 3단계라, 두 스레드가 겹치면 갱신 유실)")
    println()

    // ── 시나리오 B: 잠금 있음 ──────────────────────────────────
    val safe = SafeAccount(balance = 2 * repeatCount.toLong())
    val t3 = thread { repeat(repeatCount) { safe.withdraw(withdrawAmount) } }
    val t4 = thread { repeat(repeatCount) { safe.withdraw(withdrawAmount) } }
    t3.join(); t4.join()

    println("[B. 잠금 있음 (synchronized)]")
    println("  기대 잔고: 0")
    println("  실제 잔고: ${safe.balance}   ← 항상 정확")
    println()

    println("=== 교훈 ===")
    println("같은 계좌에 동시 주문이 들어오면 반드시 직렬화해야 한다.")
    println("실무에서는: DB 트랜잭션 + 락(비관적/낙관적), 계좌별 순차 처리(큐),")
    println("            또는 단일 스레드 처리 모델(예: LMAX Disruptor)을 사용한다.")
}

```

## 4. OrderBook — 호가창 매칭

TreeMap 기반 호가창과 시장가 매칭(부분체결).
관련 문서: [12. 호가창 자료구조](12-orderbook)

```kotlin
/**
 * 호가창(Order Book) 자료구조 + 간단한 매칭 엔진
 *
 * 시세/체결의 핵심 자료구조입니다.
 *   - 매수호가(bids): 높은 가격이 먼저 (내림차순)
 *   - 매도호가(asks): 낮은 가격이 먼저 (오름차순)
 *   - 최우선매수(bestBid) / 최우선매도(bestAsk) / 스프레드(spread)
 *   - 시장가 매수가 들어오면 가장 싼 매도호가부터 체결
 *
 * TreeMap을 쓰는 이유: 가격순 정렬을 자동 유지 → 최우선 호가를 O(log n)에 조회.
 *
 * ▶ 실행: kotlinc OrderBook.kt -include-runtime -d ob.jar && java -jar ob.jar
 */

import java.math.BigDecimal
import java.util.TreeMap

class OrderBook {
    // 매수호가: 높은 가격이 맨 위 (내림차순 정렬)
    private val bids = TreeMap<BigDecimal, Int>(compareByDescending<BigDecimal> { it })
    // 매도호가: 낮은 가격이 맨 위 (오름차순 정렬)
    private val asks = TreeMap<BigDecimal, Int>()

    fun addBid(price: BigDecimal, qty: Int) { bids[price] = (bids[price] ?: 0) + qty }
    fun addAsk(price: BigDecimal, qty: Int) { asks[price] = (asks[price] ?: 0) + qty }

    fun bestBid(): BigDecimal? = if (bids.isEmpty()) null else bids.firstKey()
    fun bestAsk(): BigDecimal? = if (asks.isEmpty()) null else asks.firstKey()

    fun spread(): BigDecimal? {
        val b = bestBid() ?: return null
        val a = bestAsk() ?: return null
        return a.subtract(b)   // 최우선매도 - 최우선매수
    }

    /** 시장가 매수: 가장 싼 매도호가부터 차례로 체결. 체결 내역을 반환 */
    fun marketBuy(quantity: Int): List<String> {
        var remaining = quantity
        val fills = mutableListOf<String>()
        while (remaining > 0 && asks.isNotEmpty()) {
            val price = asks.firstKey()           // 제일 싼 매도호가
            val available = asks[price]!!
            val fillQty = minOf(remaining, available)
            fills.add("  체결: ${fillQty}주 @ ${price}원")
            remaining -= fillQty
            if (fillQty == available) asks.remove(price)     // 그 가격 물량 소진
            else asks[price] = available - fillQty           // 일부만 체결
        }
        if (remaining > 0) fills.add("  미체결 잔량: ${remaining}주 (매도호가 부족)")
        return fills
    }

    fun print() {
        println("─ 호가창 ─────────────")
        // 매도호가는 높은 가격이 위로 보이도록 역순 출력
        asks.entries.reversed().forEach { (p, q) -> println("  매도 $p  | $q") }
        println("  ----------------- (스프레드: ${spread() ?: "-"})")
        bids.forEach { (p, q) -> println("  매수 $p  | $q") }
        println("──────────────────────")
    }
}

fun main() {
    val book = OrderBook()

    // 매도호가 쌓기 (파는 사람들)
    book.addAsk(BigDecimal("70200"), 30)
    book.addAsk(BigDecimal("70100"), 20)
    book.addAsk(BigDecimal("70300"), 50)
    // 매수호가 쌓기 (사는 사람들)
    book.addBid(BigDecimal("70000"), 40)
    book.addBid(BigDecimal("69900"), 60)
    book.addBid(BigDecimal("69800"), 100)

    println("초기 상태")
    book.print()
    println("최우선매수: ${book.bestBid()}  최우선매도: ${book.bestAsk()}  스프레드: ${book.spread()}")
    println()

    // 시장가로 35주 매수 → 70100(20주) 다 먹고, 70200에서 15주 체결
    println("[시장가 35주 매수]")
    book.marketBuy(35).forEach { println(it) }
    println()

    println("체결 후 상태")
    book.print()
}

```

