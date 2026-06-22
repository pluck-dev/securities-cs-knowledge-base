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
