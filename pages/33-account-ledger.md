# 33. 계좌·원장·손익 계산 (Account, Ledger & P&L Calculation)

> **선행 학습**: [32. 청산·결제·정산](32-clearing-settlement), [10. BigDecimal 심화](10-bigdecimal-deep)

---

## 1. 계좌 체계 (Account Structure)

증권사에서 "계좌(Account)"는 투자자의 자산과 거래 내역을 관리하는 핵심 단위다. 계좌 유형에 따라 가능한 거래, 관리되는 자산의 종류, 원장(Ledger) 구조가 달라진다.

### 1.1 주요 계좌 유형

| 계좌 유형 | 설명 | 주요 특징 |
|-----------|------|-----------|
| **위탁계좌 (Brokerage Account)** | 일반 주식 매매용 기본 계좌 | 주식, ETF 등 현물 거래 |
| **CMA (Cash Management Account)** | 예치금을 단기 금융상품에 자동 운용 | 수시 입출금 + 이자 |
| **ISA (Individual Savings Account)** | 개인종합자산관리계좌, 세제 혜택 | 비과세·분리과세 한도 있음 |
| **연금저축계좌** | 노후 자금 장기 운용 | 세액공제, 의무 유지 기간 |
| **IRP (Individual Retirement Pension)** | 개인형 퇴직연금 | 세액공제 한도 별도 |
| **신용계좌** | 증권사 자금 빌려 투자 (레버리지) | 담보비율 관리, 반대매매 위험 |

> 이 문서에서는 가장 기본적인 **위탁계좌**를 중심으로 설명한다.

### 1.2 계좌 도메인 모델

```kotlin
data class Account(
    val id: Long,
    val accountNumber: String,          // 계좌번호 (예: 12345-67-890123)
    val investorId: Long,               // 계좌 소유 투자자
    val accountType: AccountType,
    val status: AccountStatus,
    val baseCurrency: String = "KRW",
    val openedAt: LocalDate,
    val closedAt: LocalDate?
)

enum class AccountType {
    BROKERAGE,       // 위탁계좌
    CMA,
    ISA,
    PENSION_SAVINGS,
    IRP,
    MARGIN           // 신용계좌
}

enum class AccountStatus {
    ACTIVE,          // 정상
    SUSPENDED,       // 거래 정지
    DORMANT,         // 휴면
    CLOSED           // 해지
}
```

---

## 2. 원장 (Ledger) — 진실의 원천

**원장(Ledger)**은 계좌의 모든 금전적 변동을 기록하는 핵심 데이터다. 원장은 **진실의 원천(Single Source of Truth)**이다. 화면에 보이는 잔고, 손익, 출금가능금액 등은 모두 원장을 기반으로 계산되어야 한다.

### 2.1 복식부기(Double-Entry Bookkeeping) 개념 차용

금융 시스템은 단순 잔고 필드 하나를 업데이트하는 방식이 아니라, **모든 변동을 원장 항목(Ledger Entry)으로 남기는** 접근이 신뢰성 면에서 우월하다.

```
잘못된 방식 (단순 업데이트):
  account.cash += 1_000_000  ← 오류 발생 시 추적 불가

올바른 방식 (원장 기록):
  INSERT INTO cash_ledger (account_id, amount, type, description, created_at)
  VALUES (1234, 1_000_000, 'DEPOSIT', '입금', NOW())
  → 잔고는 원장 합산으로 계산
```

### 2.2 현금 원장 (Cash Ledger)

```kotlin
data class CashLedgerEntry(
    val id: Long,
    val accountId: Long,
    val entryType: CashEntryType,
    val amount: BigDecimal,             // 양수: 증가, 음수: 감소
    val description: String,
    val referenceId: String?,           // 관련 주문번호, 체결번호 등
    val balanceAfter: BigDecimal,       // 해당 항목 반영 후 잔고 (스냅샷)
    val valueDate: LocalDate,           // 실제 반영일 (T+2 등)
    val createdAt: Instant
)

enum class CashEntryType {
    DEPOSIT,             // 입금
    WITHDRAWAL,          // 출금
    BUY_EXECUTION,       // 매수 체결 (감소)
    SELL_EXECUTION,      // 매도 체결 (증가, T+2 후)
    COMMISSION,          // 수수료 (감소)
    TRANSACTION_TAX,     // 거래세 (감소)
    DIVIDEND,            // 배당금 수령
    INTEREST,            // 이자 수령 (CMA 등)
    MARGIN_CALL,         // 반대매매 관련
    ADJUSTMENT           // 수기 조정 (관리자)
}
```

### 2.3 증권 원장 (Securities Ledger)

```kotlin
data class SecuritiesLedgerEntry(
    val id: Long,
    val accountId: Long,
    val stockCode: String,
    val entryType: SecuritiesEntryType,
    val quantity: Int,                  // 양수: 증가, 음수: 감소
    val unitPrice: BigDecimal,          // 체결 단가
    val totalAmount: BigDecimal,        // 체결 대금
    val description: String,
    val referenceId: String?,
    val settlementDate: LocalDate,      // T+2 결제일
    val createdAt: Instant
)

enum class SecuritiesEntryType {
    BUY_EXECUTION,       // 매수 체결
    SELL_EXECUTION,      // 매도 체결
    TRANSFER_IN,         // 타사 이전 입고
    TRANSFER_OUT,        // 타사 이전 출고
    RIGHTS_ISSUE,        // 유상증자
    BONUS_ISSUE,         // 무상증자
    DIVIDEND_STOCK,      // 주식배당
    ADJUSTMENT           // 수기 조정
}
```

---

## 3. 예수금·증거금·출금가능금액

이 세 개념은 서로 연관되어 있지만 명확히 구분해야 한다.

### 3.1 개념 정의

| 항목 | 정의 |
|------|------|
| **예수금 (Deposit Balance)** | 계좌에 있는 현금 총액. 매매대금, 수수료, 세금 등이 반영된 잔고 |
| **증거금 (Margin)** | 매수 주문 시 필요한 최소 담보 금액. 현재가 × 수량 × 증거금률 |
| **출금가능금액 (Withdrawable Amount)** | 당장 출금할 수 있는 금액. 예수금에서 미결제 매수대금, 증거금 등을 뺀 값 |
| **주문가능금액 (Orderable Amount)** | 새 매수 주문을 낼 수 있는 한도. 출금가능금액 기준 |

### 3.2 출금가능금액 계산

```kotlin
data class AccountBalance(
    val accountId: Long,
    val depositBalance: BigDecimal,           // 예수금 (총 현금)
    val pendingBuyAmount: BigDecimal,         // 미결제 매수 예약 금액 (T+2 전)
    val pendingSellAmount: BigDecimal,        // 미결제 매도 예정 금액 (T+2 전, 아직 입금 전)
    val lockedMargin: BigDecimal,             // 신용거래 담보 잠금 금액
    val orderedButNotFilled: BigDecimal,      // 주문 중이나 미체결 금액 (출금 불가)
) {
    /**
     * 출금가능금액
     * = 예수금 - 미결제 매수 예약금 - 신용담보 잠금 - 미체결 주문 금액
     */
    val withdrawableAmount: BigDecimal
        get() = depositBalance
            .subtract(pendingBuyAmount)
            .subtract(lockedMargin)
            .subtract(orderedButNotFilled)
            .coerceAtLeast(BigDecimal.ZERO)

    /**
     * 주문가능금액 (출금가능금액 기반, 증거금률 적용)
     * 예: 증거금률 40%면 출금가능 100만원으로 250만원 어치 주문 가능
     */
    fun orderableAmount(marginRate: BigDecimal): BigDecimal {
        return if (marginRate <= BigDecimal.ZERO) BigDecimal.ZERO
        else withdrawableAmount.divide(marginRate, 0, java.math.RoundingMode.FLOOR)
    }
}

private fun BigDecimal.coerceAtLeast(min: BigDecimal): BigDecimal =
    if (this >= min) this else min
```

> ※ 실제 출금가능금액 계산 로직은 증권사 정책, 신용거래 여부, 상품 유형 등에 따라 다릅니다. **사내 기준 확인 필요.**

---

## 4. 보유 잔고와 평균단가 계산

### 4.1 보유 잔고 (Holdings)

```kotlin
data class Holding(
    val accountId: Long,
    val stockCode: String,
    val quantity: Int,                  // 보유 수량
    val availableQuantity: Int,         // 매도 가능 수량 (T+2 미결제 제외)
    val averageCostPrice: BigDecimal,   // 평균 매입 단가 (평단가)
    val totalCost: BigDecimal,          // 총 매입 원가 (= averageCostPrice × quantity)
    val updatedAt: Instant
)
```

### 4.2 평균단가(평단가) 계산 상세

**평균단가(Average Cost Price)**는 현재 보유 중인 주식의 주당 평균 매입 가격이다. 매수할 때마다 가중평균으로 갱신된다.

#### 매수 시 — 가중평균 갱신

```kotlin
/**
 * 매수 체결 시 평균단가 재계산 (가중평균)
 *
 * 기존: 100주 @ 10,000원 (총원가 = 1,000,000원)
 * 신규 매수: 50주 @ 11,000원 (체결 대금 = 550,000원)
 * 결과: 150주 @ 10,333.33원 (총원가 = 1,550,000원)
 */
fun recalculateAverageCostOnBuy(
    existingQuantity: Int,
    existingTotalCost: BigDecimal,
    newQuantity: Int,
    executionPrice: BigDecimal,
    commission: BigDecimal               // 수수료도 원가에 포함 여부: 사내 정책에 따름
): Pair<BigDecimal, BigDecimal> {        // (새 평단가, 새 총원가)
    val newTotalCost = existingTotalCost
        .add(executionPrice.multiply(BigDecimal(newQuantity)))
        .add(commission)                 // 수수료 포함 여부는 사내 정책 확인 필요

    val newTotalQuantity = existingQuantity + newQuantity

    val newAvgPrice = if (newTotalQuantity == 0) BigDecimal.ZERO
    else newTotalCost.divide(
        BigDecimal(newTotalQuantity),
        4,                               // 소수점 4자리 (사내 정책에 따름)
        java.math.RoundingMode.HALF_UP
    )

    return Pair(newAvgPrice, newTotalCost)
}
```

#### 매도 시 — 평단가 유지, 수량 차감

매도 시에는 평균단가를 변경하지 않는다. 보유 수량과 총원가만 차감한다.

```kotlin
/**
 * 매도 체결 시 잔고 업데이트
 *
 * 기존: 150주 @ 10,333원 (총원가 1,550,000원)
 * 매도: 50주 @ 12,000원
 * 결과: 100주 @ 10,333원 (총원가 = 10,333 × 100 = 1,033,300원)
 */
fun recalculateHoldingOnSell(
    holding: Holding,
    sellQuantity: Int
): Holding {
    require(sellQuantity <= holding.quantity) {
        "매도 수량($sellQuantity)이 보유 수량(${holding.quantity})을 초과합니다."
    }

    val newQuantity = holding.quantity - sellQuantity
    val newTotalCost = holding.averageCostPrice
        .multiply(BigDecimal(newQuantity))
        .setScale(0, java.math.RoundingMode.HALF_UP)

    return holding.copy(
        quantity = newQuantity,
        availableQuantity = holding.availableQuantity - sellQuantity,
        totalCost = newTotalCost
        // averageCostPrice는 매도 시 변경하지 않음
    )
}
```

> **왜 평단가를 매도 시 바꾸지 않나?** 남은 주식의 원가는 매도 여부와 무관하기 때문이다. 매도한 주식의 원가(실현손익 계산에 사용)를 기존 평단가로 계산하고, 나머지 보유분은 동일한 평단가를 유지한다.

---

## 5. 손익 계산 (P&L Calculation)

### 5.1 평가금액과 미실현손익 (Unrealized P&L)

**평가금액(Market Value)**은 현재 시장가(현재가) 기준으로 보유 주식의 가치다.

```kotlin
fun calculateUnrealizedPnL(
    holding: Holding,
    currentPrice: BigDecimal
): UnrealizedPnL {
    val marketValue = currentPrice.multiply(BigDecimal(holding.quantity))
    val unrealizedGainLoss = marketValue.subtract(holding.totalCost)
    val returnRate = if (holding.totalCost == BigDecimal.ZERO) BigDecimal.ZERO
    else unrealizedGainLoss
        .divide(holding.totalCost, 4, java.math.RoundingMode.HALF_UP)
        .multiply(BigDecimal("100"))

    return UnrealizedPnL(
        stockCode = holding.stockCode,
        quantity = holding.quantity,
        averageCostPrice = holding.averageCostPrice,
        currentPrice = currentPrice,
        totalCost = holding.totalCost,
        marketValue = marketValue,
        unrealizedGainLoss = unrealizedGainLoss,
        returnRate = returnRate  // % 단위
    )
}

data class UnrealizedPnL(
    val stockCode: String,
    val quantity: Int,
    val averageCostPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val totalCost: BigDecimal,
    val marketValue: BigDecimal,
    val unrealizedGainLoss: BigDecimal,   // 양수: 평가이익, 음수: 평가손실
    val returnRate: BigDecimal             // 수익률 (%)
)
```

### 5.2 실현손익 (Realized P&L)

매도 체결 시 실제로 확정되는 손익이다.

```kotlin
/**
 * 실현손익 계산
 * = 매도 대금 - 매입 원가 - 수수료 - 거래세 - 농특세
 *
 * 예시 (수치는 예시이며 사내 기준 확인 필요):
 *   매도 수량: 50주
 *   매도가: 12,000원
 *   평단가: 10,333원
 *   수수료: 360원 (0.06% 가정)
 *   거래세: 720원 (0.12% 가정, KOSPI 기준 예시)
 *   농특세: 180원 (거래세의 20% 가정)
 */
fun calculateRealizedPnL(
    sellQuantity: Int,
    sellPrice: BigDecimal,
    averageCostPrice: BigDecimal,
    commission: BigDecimal,
    transactionTax: BigDecimal,
    agricultureTax: BigDecimal
): RealizedPnL {
    val sellAmount = sellPrice.multiply(BigDecimal(sellQuantity))
    val costBasis = averageCostPrice.multiply(BigDecimal(sellQuantity))
        .setScale(0, java.math.RoundingMode.HALF_UP)
    val totalFees = commission.add(transactionTax).add(agricultureTax)
    val realizedGainLoss = sellAmount.subtract(costBasis).subtract(totalFees)

    return RealizedPnL(
        sellQuantity = sellQuantity,
        sellPrice = sellPrice,
        sellAmount = sellAmount,
        averageCostPrice = averageCostPrice,
        costBasis = costBasis,
        commission = commission,
        transactionTax = transactionTax,
        agricultureTax = agricultureTax,
        totalFees = totalFees,
        realizedGainLoss = realizedGainLoss
    )
}

data class RealizedPnL(
    val sellQuantity: Int,
    val sellPrice: BigDecimal,
    val sellAmount: BigDecimal,          // 매도 대금
    val averageCostPrice: BigDecimal,    // 평균 매입가
    val costBasis: BigDecimal,           // 매입 원가
    val commission: BigDecimal,
    val transactionTax: BigDecimal,
    val agricultureTax: BigDecimal,
    val totalFees: BigDecimal,
    val realizedGainLoss: BigDecimal     // 양수: 실현이익, 음수: 실현손실
)
```

---

## 6. 수수료·거래세·농특세 계산

### 6.1 비용 구성

| 비용 항목 | 부과 시점 | 대상 | 비고 |
|-----------|-----------|------|------|
| **증권사 수수료 (Brokerage Commission)** | 매수/매도 체결 시 | 체결 대금 × 수수료율 | 증권사별, 계좌 유형별 상이 |
| **증권거래세 (Securities Transaction Tax)** | 매도 체결 시 | 매도 대금 × 세율 | KOSPI·KOSDAQ 세율 다를 수 있음 |
| **농어촌특별세 (Agriculture & Rural Tax)** | 매도 체결 시 | 거래세 × 일정 비율 | 시장에 따라 부과 여부 상이 |

> ※ 모든 세율 및 수수료율은 시점과 증권사 정책에 따라 다르므로 **반드시 사내 기준 확인 필요.**

### 6.2 수수료·세금 계산 예제

```kotlin
data class FeePolicy(
    val commissionRate: BigDecimal,         // 수수료율 (예: 0.00015)
    val transactionTaxRate: BigDecimal,     // 거래세율 (예: 0.0018)
    val agricultureTaxRate: BigDecimal,     // 농특세율 (예: 거래세의 20%)
    val minCommission: BigDecimal = BigDecimal.ZERO  // 최소 수수료
)

object FeeCalculator {

    fun calculateBuyFees(
        executionAmount: BigDecimal,
        policy: FeePolicy
    ): BuyFees {
        val commission = calculateCommission(executionAmount, policy)
        return BuyFees(commission = commission)
    }

    fun calculateSellFees(
        executionAmount: BigDecimal,
        policy: FeePolicy
    ): SellFees {
        val commission = calculateCommission(executionAmount, policy)
        val transactionTax = executionAmount
            .multiply(policy.transactionTaxRate)
            .setScale(0, java.math.RoundingMode.FLOOR)  // 원 단위 절사 (사내 정책 확인)
        val agricultureTax = transactionTax
            .multiply(policy.agricultureTaxRate)
            .setScale(0, java.math.RoundingMode.FLOOR)

        return SellFees(
            commission = commission,
            transactionTax = transactionTax,
            agricultureTax = agricultureTax
        )
    }

    private fun calculateCommission(
        executionAmount: BigDecimal,
        policy: FeePolicy
    ): BigDecimal {
        val calculated = executionAmount
            .multiply(policy.commissionRate)
            .setScale(0, java.math.RoundingMode.HALF_UP)
        return calculated.max(policy.minCommission)
    }
}

data class BuyFees(val commission: BigDecimal)
data class SellFees(
    val commission: BigDecimal,
    val transactionTax: BigDecimal,
    val agricultureTax: BigDecimal
) {
    val totalFees: BigDecimal
        get() = commission.add(transactionTax).add(agricultureTax)
}
```

---

## 7. 거래내역 원장 설계

### 7.1 거래내역의 역할

거래내역 원장(Transaction Ledger)은 계좌에서 발생한 모든 사건의 **불변(Immutable) 기록**이다. 삭제나 수정 없이 INSERT만 허용하며, 잘못된 처리는 역분개(Reversal Entry)로 보정한다.

```kotlin
data class TransactionRecord(
    val id: Long,
    val accountId: Long,
    val transactionType: TransactionType,
    val stockCode: String?,              // 증권 거래일 경우
    val quantity: Int?,
    val unitPrice: BigDecimal?,
    val grossAmount: BigDecimal,         // 체결 대금 (세전·수수료 전)
    val commission: BigDecimal,
    val transactionTax: BigDecimal,
    val agricultureTax: BigDecimal,
    val netAmount: BigDecimal,           // 실제 정산 금액
    val cashEffect: BigDecimal,          // 현금 변동 (양수: 증가, 음수: 감소)
    val referenceOrderId: Long?,
    val referenceExecutionId: String?,
    val tradeDate: LocalDate,
    val settlementDate: LocalDate,
    val description: String,
    val createdAt: Instant,
    val isReversal: Boolean = false,     // 역분개 여부
    val reversedRecordId: Long? = null   // 역분개 대상 원본 레코드 ID
)

enum class TransactionType {
    BUY,                 // 매수
    SELL,                // 매도
    DEPOSIT,             // 입금
    WITHDRAWAL,          // 출금
    DIVIDEND,            // 배당
    INTEREST,            // 이자
    TRANSFER_IN,         // 이전 입고
    TRANSFER_OUT,        // 이전 출고
    RIGHTS_ISSUE,        // 유상증자
    BONUS_ISSUE,         // 무상증자
    COMMISSION_REFUND,   // 수수료 환급
    TAX_REFUND,          // 세금 환급
    ADJUSTMENT,          // 관리자 조정
    REVERSAL             // 역분개
}
```

### 7.2 역분개 패턴

```kotlin
fun createReversal(original: TransactionRecord): TransactionRecord {
    return original.copy(
        id = generateNewId(),
        cashEffect = original.cashEffect.negate(),        // 반대 부호
        netAmount = original.netAmount.negate(),
        description = "[역분개] ${original.description}",
        isReversal = true,
        reversedRecordId = original.id,
        createdAt = Instant.now()
    )
}
```

---

## 8. 정합성 보장

### 8.1 원장 정합성 규칙

```
잔고 = SUM(cashLedgerEntries.amount WHERE accountId = ?)
총원가 = SUM(securitiesLedgerEntries.unitPrice × quantity WHERE accountId = ? AND stockCode = ?)
```

현금 잔고와 증권 잔고는 항상 원장 합산으로 재계산 가능해야 한다. **캐시된 잔고 필드**는 성능을 위한 스냅샷이며, 항상 원장과 일치해야 한다.

### 8.2 잔고 스냅샷과 원장 대사

```kotlin
/**
 * 캐시된 잔고와 원장 합산 결과를 비교하여 불일치 감지
 * 정기 배치 또는 의심 거래 후 실행
 */
fun verifyBalanceIntegrity(
    accountId: Long,
    cachedBalance: BigDecimal,
    ledgerEntries: List<CashLedgerEntry>
): IntegrityCheckResult {
    val calculatedBalance = ledgerEntries
        .filter { it.accountId == accountId }
        .fold(BigDecimal.ZERO) { acc, entry -> acc.add(entry.amount) }

    val discrepancy = calculatedBalance.subtract(cachedBalance)

    return IntegrityCheckResult(
        accountId = accountId,
        cachedBalance = cachedBalance,
        calculatedBalance = calculatedBalance,
        discrepancy = discrepancy,
        isConsistent = discrepancy.compareTo(BigDecimal.ZERO) == 0,
        checkedAt = Instant.now()
    )
}

data class IntegrityCheckResult(
    val accountId: Long,
    val cachedBalance: BigDecimal,
    val calculatedBalance: BigDecimal,
    val discrepancy: BigDecimal,
    val isConsistent: Boolean,
    val checkedAt: Instant
)
```

---

## 9. 입출금·이체 처리

### 9.1 입금 처리

```kotlin
fun processDeposit(
    accountId: Long,
    amount: BigDecimal,
    description: String
): CashLedgerEntry {
    require(amount > BigDecimal.ZERO) { "입금액은 0보다 커야 합니다." }

    val currentBalance = getCurrentBalance(accountId)
    val newBalance = currentBalance.add(amount)

    return CashLedgerEntry(
        id = generateId(),
        accountId = accountId,
        entryType = CashEntryType.DEPOSIT,
        amount = amount,
        description = description,
        referenceId = null,
        balanceAfter = newBalance,
        valueDate = LocalDate.now(),
        createdAt = Instant.now()
    )
}
```

### 9.2 출금 가능 여부 검증

```kotlin
fun validateWithdrawal(
    accountId: Long,
    requestAmount: BigDecimal,
    accountBalance: AccountBalance
): WithdrawalValidationResult {
    return when {
        requestAmount <= BigDecimal.ZERO ->
            WithdrawalValidationResult.Rejected("출금액은 0보다 커야 합니다.")
        requestAmount > accountBalance.withdrawableAmount ->
            WithdrawalValidationResult.Rejected(
                "출금가능금액(${accountBalance.withdrawableAmount}원)을 초과합니다."
            )
        else ->
            WithdrawalValidationResult.Approved
    }
}

sealed class WithdrawalValidationResult {
    object Approved : WithdrawalValidationResult()
    data class Rejected(val reason: String) : WithdrawalValidationResult()
}
```

---

## 10. 계산 정확성과 반올림 정책

금액 계산의 정확성은 법적 책임과 고객 신뢰에 직결된다. 자세한 BigDecimal 사용법은 [10. BigDecimal 심화](10-bigdecimal-deep)를 참고하며, 여기서는 원장·손익 계산에 특화된 정책을 정리한다.

### 10.1 반올림 정책 원칙

```kotlin
object RoundingPolicy {
    // 금액 (원 단위)
    val MONEY_SCALE = 0
    val MONEY_ROUNDING = java.math.RoundingMode.HALF_UP

    // 평단가 (소수점 4자리)
    val PRICE_SCALE = 4
    val PRICE_ROUNDING = java.math.RoundingMode.HALF_UP

    // 수익률 (소수점 2자리, %)
    val RATE_SCALE = 2
    val RATE_ROUNDING = java.math.RoundingMode.HALF_UP

    // 수수료 (원 단위, 고객에게 유리하게: 올림)
    val COMMISSION_ROUNDING = java.math.RoundingMode.HALF_UP

    // 거래세 (원 단위, 절사 — 사내 기준 확인 필요)
    val TAX_ROUNDING = java.math.RoundingMode.FLOOR
}
```

### 10.2 체크리스트

| 항목 | 확인 |
|------|------|
| 모든 금액 계산에 BigDecimal 사용 | ✅ |
| Double/Float 절대 사용 금지 | ✅ |
| 반올림 RoundingMode 명시 (UNNECESSARY 방지) | ✅ |
| 반올림 정책 팀 전체 문서화 | ✅ |
| 테스트에 경계값(0원, 최소 틱사이즈, 상한가) 포함 | ✅ |

---

## 11. 개발 체크리스트

- [ ] 계좌 유형별 허용 거래 및 원장 구조 정의
- [ ] 현금 원장(Cash Ledger) — INSERT only, 역분개 패턴 구현
- [ ] 증권 원장(Securities Ledger) — 체결별 항목 기록
- [ ] 평균단가 계산: 매수 시 가중평균, 매도 시 수량 차감만
- [ ] 미실현손익: 현재가 기반 실시간 계산
- [ ] 실현손익: 매도 시 수수료·세금 반영 후 확정
- [ ] 출금가능금액: 미결제 매수금·미체결 주문 제외 계산
- [ ] 잔고 정합성 검증 배치 (캐시 잔고 vs 원장 합산)
- [ ] 모든 금액 BigDecimal 처리, 반올림 정책 문서화
- [ ] 거래내역 삭제 금지 정책, 역분개 로직 구현
- [ ] 수수료·세금 계산 사내 기준 확인 및 FeePolicy 파라미터화
- [ ] 원장 이벤트 감사(Audit) 로그 보관 기간 정책 수립

---

이전: [32. 청산·결제·정산](32-clearing-settlement) · 다음: [34. 위험관리(RMS)](34-risk-management) · [전체 커리큘럼](/curriculum)
