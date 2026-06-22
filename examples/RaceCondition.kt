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
