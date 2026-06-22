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
