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
