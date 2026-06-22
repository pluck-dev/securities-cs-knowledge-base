# 32. 청산·결제·정산 (Clearing, Settlement & Reconciliation)

> **선행 학습**: [31. 주문 유형 전체](31-order-types.md), [30. 시장 구조와 매매제도](30-market-structure.md)

---

## 1. 매매체결 이후의 흐름

투자자가 주문을 내고 체결(Execution)이 발생한 이후에도 실제로 "돈과 주식이 오가는" 과정이 남아 있다. 이 과정을 **청산(Clearing)**과 **결제(Settlement)**라고 한다.

```
매매 체결 (Execution)
    │
    ▼
청산 (Clearing)          ← 누가 얼마를 주고받아야 하는지 계산
    │
    ▼
결제 (Settlement)        ← 실제 증권과 대금의 이전
    │
    ▼
정산 (Reconciliation)    ← 장부와 실제 잔고가 일치하는지 대사
```

체결은 거래 의사의 합치지만, **법적으로 소유권이 이전되는 시점은 결제 완료 시점**이다. 이 차이를 이해하지 못하면 예수금·출금가능금액 계산에서 심각한 오류가 발생한다.

---

## 2. 청산 (Clearing)

**청산(Clearing)**이란 체결된 거래를 토대로 **각 참가자가 주고받아야 할 증권과 대금의 순수량을 계산**하는 과정이다. 한국 시장에서는 KRX 청산소가 이 역할을 담당한다.

### 2.1 청산의 핵심 — 네팅 (Netting)

동일 종목에 대해 당일 여러 번 매수·매도한 경우, 건별로 결제하지 않고 **순포지션(Net Position)**을 계산하여 1건으로 결제한다.

```
예시: A 투자자 삼성전자 당일 거래
  매수 100주 @ 75,000원
  매도  30주 @ 75,500원
  매수  50주 @ 75,200원
────────────────────────
순 결제: 매수 120주 (= 100 - 30 + 50)
결제 대금: (100 × 75,000) + (50 × 75,200) - (30 × 75,500)
         = 7,500,000 + 3,760,000 - 2,265,000 = 8,995,000원
```

네팅 덕분에 결제 건수와 대금 규모가 줄어 시스템 부하와 리스크가 감소한다.

### 2.2 중앙거래상대방 (CCP, Central Counterparty)

KRX는 **CCP** 역할을 수행한다. 매수자와 매도자 사이에 KRX가 개입하여, 어느 한쪽이 결제 불이행을 해도 나머지 쪽은 정상 결제를 받을 수 있도록 보장한다.

```
[기존 방식]       매수자 ←─────────── 매도자

[CCP 방식]        매수자 ←── KRX ──── 매도자
                   (KRX가 양쪽의 거래 상대방이 됨)
```

---

## 3. 결제 (Settlement)

**결제(Settlement)**란 청산에서 계산된 내역대로 **실제 증권과 대금이 이전**되는 과정이다.

### 3.1 한국 주식 T+2 결제

한국 주식 시장은 **T+2 결제** 원칙을 적용한다. 체결일(T)로부터 **2 영업일** 후에 증권과 대금의 실제 이전이 이루어진다.

```
T일(월)   매매 체결
T+1일(화) 청산 확정, 결제 준비
T+2일(수) 결제 완료 (증권 이전 + 대금 이전)
```

### 3.2 영업일 (Business Day) 계산

T+2 계산에서 "2 영업일"은 **주말과 공휴일을 제외**한 거래소 영업일 기준이다.

```kotlin
class BusinessDayCalculator(
    private val holidayCalendar: Set<LocalDate>
) {
    fun isBusinessDay(date: LocalDate): Boolean {
        return date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            && date !in holidayCalendar
    }

    fun addBusinessDays(from: LocalDate, days: Int): LocalDate {
        var result = from
        var remaining = days
        while (remaining > 0) {
            result = result.plusDays(1)
            if (isBusinessDay(result)) {
                remaining--
            }
        }
        return result
    }

    fun getSettlementDate(tradeDate: LocalDate): LocalDate {
        return addBusinessDays(tradeDate, 2)  // T+2
    }
}
```

### 3.3 휴장일 캘린더 처리

공휴일은 매년 다르기 때문에 **DB 또는 설정 파일로 관리**해야 한다. KRX에서 매년 영업일 캘린더를 공시한다.

```kotlin
data class HolidayCalendar(
    val year: Int,
    val holidays: Set<LocalDate>  // 해당 연도의 휴장일
)

// DB 테이블 예시
// CREATE TABLE market_holiday (
//     id BIGINT PRIMARY KEY,
//     holiday_date DATE NOT NULL UNIQUE,
//     description VARCHAR(100),  -- '설날', '광복절' 등
//     created_at TIMESTAMP
// )
```

**개발 시 주의**:
- 연말연시에 다음 해 캘린더가 DB에 없으면 T+2 계산이 불가해진다. 연초 전에 미리 등록하는 프로세스가 필요하다.
- 임시 휴장(예: 국가 비상사태)이 발생할 경우 긴급 업데이트 프로세스가 필요하다.

### 3.4 결제 예시 (T+2 시나리오)

```
월요일 체결 → 수요일 결제
화요일 체결 → 목요일 결제
목요일 체결 → 월요일 결제 (금·토·일 제외, 단 금요일은 영업일)
금요일 체결 → 화요일 결제
```

연휴가 끼는 경우:
```
추석 연휴: 수요일(10/1) ~ 금요일(10/3) 휴장, 토일 포함
화요일(9/30) 체결 → T+2 = 영업일 2일 후 = 다음 주 화요일(10/7)
```

---

## 4. 한국예탁결제원 (KSD, Korea Securities Depository)

**KSD(한국예탁결제원)**는 증권의 **집중 예탁·결제** 기관이다. 실물 주식 증권을 보관하고, 거래 시 장부상 이전(Book-entry)으로 처리한다.

```
[역할 분담]
KRX  : 매매 체결 + 청산 (거래의 이행 보증)
KSD  : 증권 보관 + 결제 (실제 소유권 이전)
증권사: KSD에 예탁된 고객 증권을 관리 (서브계좌)
```

### 4.1 예탁 결제 흐름

```
체결 확정
    │
    ▼
KRX 청산 → 결제 지시서 생성
    │
    ▼
KSD 수령 → 매도자 계좌에서 증권 차감
          → 매수자 계좌에 증권 가산
          (실물 이동 없이 장부 기재로 처리)
    │
    ▼
대금: 매수자 → 결제은행 → 매도자 (동시 결제, DVP 원칙)
```

**DVP (Delivery Versus Payment)**: 증권 인도와 대금 지급이 동시에 이루어지는 원칙. 어느 한쪽만 이행되는 리스크(결제 리스크)를 방지한다.

---

## 5. 미결제약정과 결제 불이행

### 5.1 미결제약정 (Open Position)

체결이 발생했으나 아직 결제가 완료되지 않은 상태의 거래를 **미결제약정**이라 한다. T일 체결 ~ T+2일 결제 사이의 기간 동안 존재한다.

```kotlin
data class PendingSettlement(
    val tradeId: Long,
    val tradeDate: LocalDate,
    val settlementDate: LocalDate,
    val investorId: Long,
    val stockCode: String,
    val side: OrderSide,         // BUY / SELL
    val quantity: Int,
    val tradeAmount: BigDecimal, // 체결 대금
    val settlementStatus: SettlementStatus
)

enum class SettlementStatus {
    PENDING,    // 결제 대기 중
    SETTLED,    // 결제 완료
    FAILED      // 결제 불이행
}
```

### 5.2 결제 불이행 (Settlement Failure)

투자자 또는 증권사가 결제일에 대금 또는 증권을 제공하지 못하는 상황이다.

- **매수자 불이행**: 대금 미납 → KRX가 대납 후 해당 투자자에게 채권 행사
- **매도자 불이행**: 증권 미납 → KRX가 시장에서 매수하여 처리 (차액 비용 청구)

**시스템 관점**: 결제 불이행 가능성을 줄이기 위해 주문 접수 시 **사전 잔고/예수금 검증(Pre-trade Validation)**이 필수다.

---

## 6. 예수금·출금가능금액과 T+2의 관계

이 부분이 백엔드 개발자가 가장 혼란스러워하는 영역이다. 자세한 계산은 [33. 계좌·원장·손익 계산](33-account-ledger.md)에서 다루며, 여기서는 T+2와의 관계를 이해한다.

```
[매수 후 예수금 흐름]

T일 (매수 체결)
  - 예수금: 즉시 감소 (가수요 처리)
  - 보유 잔고: 증가 (미결제 상태)
  - 출금가능금액: 감소 (아직 결제 전이지만 예약된 금액)

T+2일 (결제 완료)
  - 실제 대금 이체: 매수자 계좌 → KSD 경유 → 매도자 계좌
  - 증권 소유권 이전 완료

[매도 후 출금 흐름]

T일 (매도 체결)
  - 보유 잔고: 즉시 감소
  - 예수금: 아직 증가 안 됨 (T+2 결제 전)
  - 출금가능금액: 미증가 (매도대금은 T+2 이후 출금 가능)

T+2일 (결제 완료)
  - 매도대금이 예수금에 반영 → 출금 가능
```

**개발 시 주의**: 매도 체결 당일에 매도대금을 즉시 출금하려 하면 결제 전 자금이 유출되므로, T+2 전 출금 제한 로직이 반드시 필요하다.

---

## 7. 대사 (Reconciliation) 배치

**대사(Reconciliation)**는 증권사의 내부 장부와 외부(거래소, KSD, 은행 등)의 데이터를 비교하여 불일치를 탐지하고 정정하는 과정이다.

### 7.1 대사의 중요성

- 시스템 오류, 네트워크 장애, 수동 처리 등으로 인한 데이터 불일치 조기 발견
- 금융감독원 보고 및 감사 대응
- 고객 자산 보호 (잔고 오류 방지)

### 7.2 주요 대사 항목

| 대사 유형 | 내부 기준 | 외부 기준 | 주기 |
|-----------|-----------|-----------|------|
| 체결 대사 | 내부 체결 원장 | KRX 체결 데이터 | 매일 장 종료 후 |
| 잔고 대사 | 내부 보유 잔고 | KSD 예탁 잔고 | 매일 |
| 예수금 대사 | 내부 예수금 원장 | 은행 계좌 잔액 | 매일 |
| 미결제 대사 | 내부 미결제약정 | KRX 결제 데이터 | 매일 |

### 7.3 대사 배치 설계

```kotlin
interface ReconciliationJob {
    val jobName: String
    fun execute(targetDate: LocalDate): ReconciliationResult
}

data class ReconciliationResult(
    val jobName: String,
    val targetDate: LocalDate,
    val processedCount: Int,
    val matchedCount: Int,
    val discrepancies: List<Discrepancy>,
    val executedAt: Instant
) {
    val hasDiscrepancy: Boolean get() = discrepancies.isNotEmpty()
}

data class Discrepancy(
    val type: DiscrepancyType,
    val referenceId: String,      // 체결번호, 계좌번호 등
    val internalValue: String,    // 내부 장부 값
    val externalValue: String,    // 외부 데이터 값
    val difference: BigDecimal?
)

enum class DiscrepancyType {
    QUANTITY_MISMATCH,    // 수량 불일치
    AMOUNT_MISMATCH,      // 금액 불일치
    MISSING_IN_INTERNAL,  // 내부에 없는 건
    MISSING_IN_EXTERNAL   // 외부에 없는 건
}

class TradeReconciliationJob(
    private val internalTradeRepository: InternalTradeRepository,
    private val krxTradeDataProvider: KrxTradeDataProvider
) : ReconciliationJob {

    override val jobName = "TRADE_RECONCILIATION"

    override fun execute(targetDate: LocalDate): ReconciliationResult {
        val internalTrades = internalTradeRepository.findByTradeDate(targetDate)
        val krxTrades = krxTradeDataProvider.fetchByDate(targetDate)

        val discrepancies = mutableListOf<Discrepancy>()

        // 내부 체결 기준으로 KRX 데이터와 비교
        for (internal in internalTrades) {
            val krx = krxTrades.find { it.executionId == internal.executionId }
            when {
                krx == null -> discrepancies.add(
                    Discrepancy(
                        DiscrepancyType.MISSING_IN_EXTERNAL,
                        internal.executionId,
                        "${internal.quantity}주 @ ${internal.price}",
                        "N/A",
                        null
                    )
                )
                krx.quantity != internal.quantity -> discrepancies.add(
                    Discrepancy(
                        DiscrepancyType.QUANTITY_MISMATCH,
                        internal.executionId,
                        "${internal.quantity}",
                        "${krx.quantity}",
                        BigDecimal(krx.quantity - internal.quantity)
                    )
                )
            }
        }

        return ReconciliationResult(
            jobName = jobName,
            targetDate = targetDate,
            processedCount = internalTrades.size,
            matchedCount = internalTrades.size - discrepancies.size,
            discrepancies = discrepancies,
            executedAt = Instant.now()
        )
    }
}
```

---

## 8. 정산 배치 시스템 설계

### 8.1 일반적인 마감 후 배치 처리 흐름

```
15:30 장 마감
    │
    ▼
16:00 ~ 17:00  [T일 체결 대사]
  - KRX 체결 데이터 수신
  - 내부 체결 원장과 대사
  - 불일치 건 알림 / 수기 처리
    │
    ▼
17:00 ~ 18:00  [수수료/세금 계산]
  - 체결별 수수료 계산
  - 거래세(증권거래세), 농특세 계산
  - 고객 원장 반영
    │
    ▼
18:00 ~ 19:00  [T+2 결제 준비]
  - 당일(T일) 체결 건 → T+2 결제 대상으로 등록
  - 매수 고객: 출금 예약 금액 확정
  - 매도 고객: 입금 예정 금액 확정
    │
    ▼
익일 영업 개시 전  [전일 T+2 결제 처리]
  - T-2일 체결 건 결제 확정
  - 매도대금 예수금 반영
  - 보유 잔고 최종 확정 (미결제 → 결제 완료)
    │
    ▼
[잔고 대사]
  - KSD 예탁 잔고와 내부 잔고 비교
```

### 8.2 배치 설계 포인트

```kotlin
data class SettlementBatch(
    val settlementDate: LocalDate,          // T+2 결제일
    val tradeDate: LocalDate,               // T일 (체결일)
    val items: List<SettlementItem>,
    val status: BatchStatus,
    val startedAt: Instant?,
    val completedAt: Instant?
)

data class SettlementItem(
    val executionId: String,
    val accountId: Long,
    val stockCode: String,
    val side: OrderSide,
    val quantity: Int,
    val settlementAmount: BigDecimal,        // 결제 대금 (수수료·세금 반영)
    val status: SettlementStatus
)

enum class BatchStatus {
    PENDING,    // 실행 전
    RUNNING,    // 실행 중
    COMPLETED,  // 완료
    FAILED,     // 실패 (부분 실패 포함)
    PARTIAL     // 일부 성공
}
```

**개발 시 주의**:
- 배치는 **멱등성(Idempotency)**이 보장되어야 한다. 재실행 시 중복 처리되지 않도록 처리 여부를 상태로 관리한다.
- 배치 실패 시 **롤백 vs 부분 완료 정책**을 명확히 정의해야 한다.
- 결제 금액 계산은 반드시 `BigDecimal`로 처리 ([10. BigDecimal 심화](10-bigdecimal-deep.md) 참조).
- 배치 처리 시간이 다음 날 장 시작 전에 완료되어야 하므로 **성능 목표(SLA)**를 설정해야 한다.

---

## 9. 수수료·세금과 결제 대금

결제 대금은 단순히 체결 수량 × 체결 가격이 아니다. 수수료와 세금이 포함된다.

```kotlin
data class TradeAmount(
    val executionAmount: BigDecimal,     // 체결 대금 (수량 × 가격)
    val brokerageCommission: BigDecimal, // 증권사 수수료
    val transactionTax: BigDecimal,      // 증권거래세 (매도 시)
    val agricultureTax: BigDecimal,      // 농어촌특별세 (매도 시)
) {
    // 매수 결제 대금: 체결 대금 + 수수료
    val buySettlementAmount: BigDecimal
        get() = executionAmount.add(brokerageCommission)

    // 매도 결제 대금: 체결 대금 - 수수료 - 세금
    val sellSettlementAmount: BigDecimal
        get() = executionAmount
            .subtract(brokerageCommission)
            .subtract(transactionTax)
            .subtract(agricultureTax)
}
```

> ※ 증권거래세율·농특세율·수수료율은 시점 및 증권사 정책에 따라 다르므로 **사내 기준 확인 필요**.  
> 세금 계산 로직은 [33. 계좌·원장·손익 계산](33-account-ledger.md)에서 상세히 다룬다.

---

## 10. 개발 체크리스트

- [ ] 영업일 계산 로직 구현 (주말 + 공휴일 제외)
- [ ] 공휴일 캘린더 DB 관리 및 연초 갱신 프로세스
- [ ] T+2 기준 결제예정일 계산 함수
- [ ] 미결제약정 상태 관리 (PENDING → SETTLED / FAILED)
- [ ] 매도 대금 T+2 전 출금 제한 로직
- [ ] 체결 대사 배치 구현 및 불일치 알림 체계
- [ ] 잔고 대사 (내부 vs KSD)
- [ ] 정산 배치 멱등성 보장
- [ ] DVP 원칙 이해 및 결제 불이행 처리 정책 문서화
- [ ] 배치 SLA 및 모니터링 알림 설정
- [ ] BigDecimal 사용 및 반올림 정책 통일

---

이전: [31. 주문 유형 전체](31-order-types.md) · 다음: [33. 계좌·원장·손익 계산](33-account-ledger.md) · [전체 커리큘럼](../CURRICULUM.md)
