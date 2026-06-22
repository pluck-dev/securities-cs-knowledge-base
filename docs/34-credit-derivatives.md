# 34. 신용·파생 기초

## 개요

증권사 시스템에서 "신용(Credit)"은 단순히 돈을 빌려주는 것을 넘어, 실시간으로 담보가치를 평가하고 위험이 임계치를 초과하면 자동으로 반대매매(Forced Liquidation)를 실행하는 복잡한 메커니즘이다. 파생상품(Derivatives)은 기초자산(Underlying Asset)의 가격 변동에 연동되는 계약으로, 증거금(Margin) 제도와 일일정산(Mark-to-Market)이 핵심이다. 이 두 영역은 시스템 개발자에게 실시간 계산, 배치 처리, 경보 시스템이라는 세 가지 거대한 과제를 던진다.

> **주의**: 이 문서의 세율·비율 수치는 예시이며, 실제 적용 시 사내 리스크 기준 및 금융감독원 규정을 반드시 확인해야 한다.

---

## 1. 결제 사이클과 미수거래

### 1.1 D+2 결제 구조

한국 주식시장(KRX)은 **D+2 결제** 방식을 채택한다. 오늘(D일) 체결된 거래는 2영업일 후에 실제 현금과 주식이 이동한다.

```
D일       D+1일       D+2일
체결 ─────────────────→ 결제(Settlement)
(매수자 주문)           (현금 납입, 주식 수령)
```

이 구조가 **미수거래(Unsettled Trade / 위탁미수)**를 가능하게 한다. 계좌에 현금이 없어도 D+2일까지만 납입하면 되므로, 증권사는 D+2일 결제대금을 투자자 대신 일시 납부한다.

| 항목 | 설명 |
|------|------|
| 미수금(Unsettled Receivable) | D+2일에 납입해야 할 결제대금 부족분 |
| 미수 발생일 | 체결일(D일) |
| 납입 기한 | D+2일 장 시작 전(보통 08:30) |
| 미납 시 | 반대매매 실행 |

### 1.2 미수 반대매매 (Forced Liquidation on Unsettled)

D+2일까지 미수금을 납입하지 않으면, 증권사는 **당일 장 시작과 동시에 시장가(Market Order)로 반대매매**를 실행한다.

```
D+2일 08:00 배치
  └─ 미수계좌 조회
       └─ 미수금 > 0 AND 잔고 주식 존재
            └─ 매도주문 생성 (시장가, 미수금 충당분)
                 └─ 주문 전송 → OMS
```

**개발 포인트**:
- 반대매매 배치는 **멱등성(Idempotency)** 보장이 필수다. 배치가 중복 실행되어도 중복 매도가 나가면 안 된다.
- 반대매매 주문은 일반 주문과 **주문구분 코드(OrderCategory)** 로 분리해야 한다(감사 추적, 고객 안내용).
- 미수금 계산: `미수금 = 체결금액 - 납입가능금액 - 예수금`

---

## 2. 신용융자 (Margin Loan)

### 2.1 개념

**신용융자(Margin Loan)**는 투자자가 주식 매수 시 매수대금 일부를 증권사가 대출해주는 서비스다. 투자자는 자기 자금(위탁증거금) + 증권사 대출금으로 더 큰 포지션을 취할 수 있다(레버리지, Leverage).

```
매수금액: 1,000만원
  자기자금: 400만원 (증거금률 40%)
  신용융자: 600만원 (증권사 대출)

구매한 주식은 담보(Collateral)로 증권사가 보유
```

### 2.2 담보유지비율 (Maintenance Margin Ratio)

신용융자의 핵심은 **담보유지비율 = 담보평가액 / 융자금액**이다. 주가 하락으로 이 비율이 일정 수준 이하로 떨어지면 **마진콜(Margin Call)**이 발생한다.

| 단계 | 비율(예시) | 시스템 액션 |
|------|-----------|------------|
| 정상 | 140% 이상 | 모니터링만 |
| 경고 | 130~140% | 문자/앱 알림 발송 |
| 마진콜 | 120~130% | 추가 납입 요구 (D+2) |
| 반대매매 | 120% 미만 | 다음 거래일 시장가 강제매도 |

> 비율 기준은 증권사별, 종목별로 다르다. 사내 리스크 정책 확인 필수.

### 2.3 담보비율 계산 (Kotlin)

```kotlin
import java.math.BigDecimal
import java.math.RoundingMode

data class CreditPosition(
    val accountId: String,
    val loanAmount: BigDecimal,          // 융자금액
    val collateralShares: Long,          // 담보 주식 수량
    val currentPrice: BigDecimal,        // 현재가
    val additionalCash: BigDecimal = BigDecimal.ZERO  // 추가 현금 담보
)

enum class MarginStatus {
    NORMAL, WARNING, MARGIN_CALL, FORCED_LIQUIDATION
}

data class MarginResult(
    val ratio: BigDecimal,
    val status: MarginStatus,
    val deficiency: BigDecimal  // 부족금액 (정상이면 0)
)

object CreditMarginCalculator {

    private val WARNING_THRESHOLD    = BigDecimal("1.40")   // 140%
    private val MARGIN_CALL_THRESHOLD = BigDecimal("1.30")  // 130%
    private val LIQUIDATION_THRESHOLD = BigDecimal("1.20")  // 120%
    private val SCALE = 4
    private val ROUNDING = RoundingMode.HALF_UP

    fun calculate(position: CreditPosition): MarginResult {
        // 담보평가액 = 주식 수량 × 현재가 + 추가현금담보
        val collateralValue = BigDecimal(position.collateralShares)
            .multiply(position.currentPrice)
            .add(position.additionalCash)

        if (position.loanAmount <= BigDecimal.ZERO) {
            return MarginResult(BigDecimal("9.9999"), MarginStatus.NORMAL, BigDecimal.ZERO)
        }

        val ratio = collateralValue.divide(position.loanAmount, SCALE, ROUNDING)

        val status = when {
            ratio < LIQUIDATION_THRESHOLD -> MarginStatus.FORCED_LIQUIDATION
            ratio < MARGIN_CALL_THRESHOLD -> MarginStatus.MARGIN_CALL
            ratio < WARNING_THRESHOLD     -> MarginStatus.WARNING
            else                          -> MarginStatus.NORMAL
        }

        // 유지비율 달성에 필요한 담보가치 = 융자금 × 유지비율
        val requiredCollateral = position.loanAmount.multiply(MARGIN_CALL_THRESHOLD)
        val deficiency = maxOf(BigDecimal.ZERO, requiredCollateral.subtract(collateralValue))

        return MarginResult(ratio, status, deficiency)
    }

    // 담보 부족 시 추가 납입 또는 일부 상환 필요 수량 계산
    fun sharesNeededToSell(position: CreditPosition, targetRatio: BigDecimal): Long {
        val result = calculate(position)
        if (result.status == MarginStatus.NORMAL) return 0L

        // 매도 후 목표비율 달성: (보유주식수 - x) × 현재가 >= 융자금 × targetRatio
        // x <= 보유주식수 - (융자금 × targetRatio / 현재가)
        val requiredShares = position.loanAmount
            .multiply(targetRatio)
            .divide(position.currentPrice, 0, RoundingMode.CEILING)
            .toLong()

        return maxOf(0L, position.collateralShares - requiredShares)
    }
}
```

### 2.4 반대매매 배치 흐름

```
[매일 장 마감 후 배치]
  ↓
종목별 현재가 조회 (시가 기준 또는 종가)
  ↓
계좌별 담보유지비율 재계산
  ↓
임계치 미만 계좌 → MarginCallEvent 발행
  ↓
고객 알림 발송 (SMS/앱 푸시)
  ↓
[D+1일 추가 납입 미이행 계좌]
  ↓
반대매매 주문 생성 (시장가, 익일 장 시작)
  ↓
OMS 전송
```

---

## 3. 신용거래 대주 (Stock Lending / Short Selling)

### 3.1 대주와 대차 차이

| 구분 | 대주(Retail Short) | 대차(Institutional Lending) |
|------|-------------------|---------------------------|
| 대상 | 개인 투자자 | 기관·법인 투자자 |
| 목적 | 공매도(Short Selling) | 헤지, 차익거래 |
| 매개 | 증권사가 주식 조달 | 증권금융·기관 간 직접 |
| 한도 | 증권사 재고 의존 | 계약 기반 |

### 3.2 대주 프로세스

```
[투자자]
  ↓ 대주 신청 (종목, 수량)
[증권사]
  ↓ 대주 가능 재고 확인 (증권금융으로부터 조달)
  ↓ 대주 계약 체결 (이자율 설정)
  ↓ 주식 대출 → 투자자에게 지급
[투자자]
  ↓ 공매도 주문 (시장에 매도)
  ↓ (나중에 시장에서 재매수 → 주식 반환)
[증권사]
  ← 주식 반환 + 이자 수취
```

### 3.3 대주 시스템 개발 과제

- **재고 관리**: 증권사가 증권금융(Korea Securities Finance Corporation)으로부터 조달한 재고 실시간 추적
- **상환 추적**: 대주 포지션의 상환 기한, 이자 계산
- **공매도 잔고 집계**: 종목별 공매도 잔고는 거래소에도 보고해야 함
- **담보**: 대주도 담보 유지 의무 (주가 상승 시 추가 담보 요구)

---

## 4. 증거금 제도 (Margin Requirement)

### 4.1 위탁증거금 (Commission Margin)

주식을 매수할 때 증권사에 미리 납입해야 하는 증거금.

```
증거금률(%) = 위탁증거금 / 매수대금 × 100

예시:
  매수대금 1,000만원, 증거금률 40%
  → 위탁증거금 400만원 납입 필요
  → 600만원은 신용융자
```

### 4.2 종목별 증거금률 분류

KRX와 증권사는 종목 리스크에 따라 증거금률을 차등 적용한다.

| 증거금률 | 해당 종목 예시 | 특징 |
|---------|--------------|------|
| 20% | 코스피 대형주 우량주 | 최저 증거금 |
| 30% | 일반 코스피/코스닥 | 표준 |
| 40%~50% | 변동성 높은 종목 | 경고 단계 |
| 100% | 관리종목, 투자경고 | 신용 불가 |

### 4.3 증거금 계산 (Kotlin)

```kotlin
import java.math.BigDecimal
import java.math.RoundingMode

enum class MarginGrade(val rate: BigDecimal) {
    GRADE_20(BigDecimal("0.20")),
    GRADE_30(BigDecimal("0.30")),
    GRADE_40(BigDecimal("0.40")),
    GRADE_50(BigDecimal("0.50")),
    GRADE_100(BigDecimal("1.00"))  // 신용 불가
}

data class OrderMarginRequest(
    val stockCode: String,
    val orderQty: Long,
    val orderPrice: BigDecimal,
    val marginGrade: MarginGrade,
    val availableCash: BigDecimal
)

data class MarginCheckResult(
    val requiredMargin: BigDecimal,    // 필요 증거금
    val availableCash: BigDecimal,     // 보유 현금
    val creditNeeded: BigDecimal,      // 필요 신용융자액
    val isOrderable: Boolean,
    val reason: String
)

object MarginCalculationService {

    fun checkOrderable(request: OrderMarginRequest): MarginCheckResult {
        val totalOrderAmount = BigDecimal(request.orderQty).multiply(request.orderPrice)
        val requiredMargin = totalOrderAmount.multiply(request.marginGrade.rate)
            .setScale(0, RoundingMode.CEILING)

        // 종목이 100% 증거금이면 신용 불가 (현금만 가능)
        if (request.marginGrade == MarginGrade.GRADE_100) {
            val isOrderable = request.availableCash >= totalOrderAmount
            return MarginCheckResult(
                requiredMargin = totalOrderAmount,
                availableCash = request.availableCash,
                creditNeeded = BigDecimal.ZERO,
                isOrderable = isOrderable,
                reason = if (isOrderable) "현금 주문 가능" else "현금 부족 (신용 불가 종목)"
            )
        }

        val isOrderable = request.availableCash >= requiredMargin
        val creditNeeded = if (isOrderable)
            totalOrderAmount.subtract(request.availableCash).coerceAtLeast(BigDecimal.ZERO)
        else BigDecimal.ZERO

        return MarginCheckResult(
            requiredMargin = requiredMargin,
            availableCash = request.availableCash,
            creditNeeded = creditNeeded,
            isOrderable = isOrderable,
            reason = if (isOrderable) "주문 가능" else "증거금 부족"
        )
    }

    private fun BigDecimal.coerceAtLeast(min: BigDecimal) =
        if (this < min) min else this
}
```

---

## 5. 신용 한도 관리

### 5.1 다층적 한도 구조

```
[증권사 전체 신용 한도]
         ↓
[지점/부서별 한도]
         ↓
[계좌별 신용 한도]
         ↓
[종목별 한도] (특정 종목 집중 방지)
```

### 5.2 실시간 한도 체크 구현 포인트

- **Redis Atomic Counter**: 한도 체크는 DB 대신 Redis `INCRBY` + `GET`으로 atomic하게 처리해야 레이스 컨디션(Race Condition)을 방지한다.
- **한도 초과 = 즉시 거부**: 주문 수신 → 한도 체크 → 통과 시에만 OMS 전달
- **한도 복원**: 주문 취소/체결 실패 시 한도 원복 필수 (보상 트랜잭션)

---

## 6. 파생상품 기초

### 6.1 선물 (Futures)

**선물**은 미래의 특정 날짜(만기일, Expiration Date)에 특정 기초자산을 미리 정한 가격으로 매매하기로 하는 계약이다.

```
KOSPI200 선물 예시:
  기초자산: KOSPI200 지수
  계약단위: 지수 × 250,000원 (1포인트 = 25만원)
  만기: 분기 (3·6·9·12월 두번째 목요일)
  일일정산: 매일 종가로 손익 정산
```

| 용어 | 설명 |
|------|------|
| Long (매수) | 기초자산 가격 상승 기대 |
| Short (매도) | 기초자산 가격 하락 기대 |
| 미결제약정(OI, Open Interest) | 아직 청산되지 않은 계약 수 |
| 롤오버(Rollover) | 만기 전 근월물→원월물 교체 |
| 기저(Basis) | 선물가격 - 현물가격 |

### 6.2 일일정산 (Mark-to-Market, MTM)

선물은 매일 장 마감 시 **당일 종가(Daily Settlement Price)를 기준으로 손익을 정산**한다. 투자자 계좌에서 당일 손익이 현금으로 바로 입출금된다.

```kotlin
import java.math.BigDecimal

data class FuturesPosition(
    val contractCount: Int,           // 계약 수 (음수 = 숏)
    val entryPrice: BigDecimal,       // 진입가격
    val previousSettlementPrice: BigDecimal,  // 전일 정산가
    val currentSettlementPrice: BigDecimal,   // 당일 정산가
    val multiplier: BigDecimal = BigDecimal("250000")  // 계약당 승수 (예시)
)

object FuturesMTMCalculator {

    fun calculateDailyPnL(position: FuturesPosition): BigDecimal {
        // 일일손익 = (당일정산가 - 전일정산가) × 계약수 × 승수
        val priceDiff = position.currentSettlementPrice
            .subtract(position.previousSettlementPrice)

        return priceDiff
            .multiply(BigDecimal(position.contractCount))
            .multiply(position.multiplier)
    }

    fun calculateTotalPnL(position: FuturesPosition): BigDecimal {
        // 누적손익 = (당일정산가 - 진입가격) × 계약수 × 승수
        val priceDiff = position.currentSettlementPrice
            .subtract(position.entryPrice)

        return priceDiff
            .multiply(BigDecimal(position.contractCount))
            .multiply(position.multiplier)
    }
}
```

### 6.3 옵션 (Options)

**옵션**은 특정 기초자산을 특정 가격(행사가, Strike Price)에 사거나 팔 수 있는 권리(Rights)다.

| 구분 | 콜옵션(Call Option) | 풋옵션(Put Option) |
|------|--------------------|--------------------|
| 매수자 권리 | 기초자산 매수 권리 | 기초자산 매도 권리 |
| 매도자 의무 | 매수자 요구 시 매도 의무 | 매수자 요구 시 매수 의무 |
| 이익 기대 | 기초자산 가격 상승 | 기초자산 가격 하락 |
| 프리미엄 | 매수자가 납입 | 매도자가 수취 |

### 6.4 옵션 그릭스 (Option Greeks) 맛보기

그릭스는 옵션 가격 변화의 민감도를 나타내는 지표다. 리스크 관리의 핵심이다.

| 그릭 | 의미 | 개발 관련성 |
|------|------|------------|
| Delta (Δ) | 기초자산 1원 변화 시 옵션가격 변화 | 포지션 헤지 비율 계산 |
| Gamma (Γ) | Delta의 변화율 | Delta 헤지 리밸런싱 주기 |
| Theta (Θ) | 시간 경과(1일)에 따른 옵션가격 감소 | 시간가치 소멸 계산 |
| Vega (ν) | 변동성 1% 변화 시 옵션가격 변화 | 변동성 리스크 노출 |
| Rho (ρ) | 금리 1% 변화 시 옵션가격 변화 | 금리 리스크 |

### 6.5 ELW / ELS 개념

| 상품 | ELW (Equity Linked Warrant) | ELS (Equity Linked Securities) |
|------|-----------------------------|---------------------------------|
| 성격 | 상장 파생증권 (주식처럼 거래) | 장외 구조화상품 |
| 수익 구조 | 옵션 유사 (레버리지) | 조건부 원금보장형 多 |
| 시스템 이슈 | 유동성공급자(LP) 호가 관리 | 기초자산 평가, 만기 상환 계산 |

---

## 7. 파생 증거금 제도

### 7.1 선물·옵션 증거금 구조

```
위탁증거금(Initial Margin)
  = 스팬(SPAN) 증거금 + 납부불이행 위험액
     ↑
  거래소(KRX)가 SPAN 모델로 산출 (포트폴리오 기반)

유지증거금(Maintenance Margin)
  = 초기 증거금의 일정 비율 (예: 75%)
     ↑
  이 이하로 떨어지면 마진콜
```

### 7.2 개발 과제 요약

```
[파생 시스템 개발 체크리스트]
□ 일일정산 배치: 매일 15:30 이후 정산가 수신 → 손익 계산 → 계좌 반영
□ 증거금 실시간 모니터링: 포지션 변화 시마다 증거금 재계산
□ 마진콜 알림: 임계치 이하 계좌 즉시 알림 발송
□ 만기일 처리: 만기 계약 자동 청산 또는 실물 인수도 처리
□ 미결제약정 집계: 종목별·계좌별 OI 관리
□ 롤오버 지원: 근월물→원월물 자동/수동 교체 기능
```

---

## 8. 신용·파생 시스템 아키텍처 개요

```
                      ┌─────────────────────────────┐
                      │     신용/파생 관리 시스템       │
                      └──────────┬──────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
   ┌──────────────────┐  ┌──────────────┐  ┌──────────────────┐
   │  담보평가 엔진    │  │  증거금 엔진  │  │  반대매매 배치   │
   │  (실시간 시가 연계)│  │(SPAN/단순방식)│  │  (스케줄러)      │
   └──────────────────┘  └──────────────┘  └──────────────────┘
              │                  │                  │
              └──────────────────┼──────────────────┘
                                 ▼
                      ┌──────────────────┐
                      │    이벤트 버스    │
                      │ (마진콜/반대매매) │
                      └──────┬───────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
      ┌──────────┐  ┌──────────────┐  ┌─────────┐
      │ OMS 연계  │  │ 알림 서비스  │  │감사 로그│
      └──────────┘  └──────────────┘  └─────────┘
```

---

## 9. 개발자 관점 핵심 정리

| 업무 영역 | 핵심 개발 과제 | 기술 포인트 |
|----------|--------------|------------|
| 미수거래 | 미수금 계산, 반대매매 배치 | 멱등성, D+2 날짜 계산 |
| 신용융자 | 담보비율 실시간 평가 | BigDecimal, 실시간 시세 연동 |
| 증거금 | 종목별 증거금률 관리, 주문 전 체크 | Redis 원자 연산, 한도 관리 |
| 선물 MTM | 일일정산 배치 | 정산가 수신, 배치 스케줄링 |
| 옵션 | 그릭스 계산, 증거금 | 수치 계산 정밀도 |
| 공통 | 반대매매 주문 추적 | 주문구분 코드, 감사 로그 |

> **BigDecimal 사용 원칙**: 모든 금액 계산은 `BigDecimal`로. `double`/`float` 사용 금지. 증거금, 담보비율, 손익 계산에서 부동소수점 오차는 실제 금전 손실로 이어진다.

---

이전: [33. 수수료와 세금 계산](33-fee-tax.md) · 다음: [35. 시세·시장 데이터](35-market-data.md) · [전체 커리큘럼](../CURRICULUM.md)
