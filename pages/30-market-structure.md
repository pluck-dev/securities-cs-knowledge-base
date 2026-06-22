# 30. 시장 구조와 매매제도 (Market Structure & Trading Rules)

> **대상 독자**: 주식 투자 경험은 있으나 증권사 시스템 개발은 처음인 백엔드 개발자  
> **선행 학습**: [04. 증권 도메인 입문](04-securities-domain), [09. 용어집](09-glossary)

---

## 1. 한국거래소(KRX) 개요

**한국거래소(Korea Exchange, KRX)**는 국내 유가증권 및 파생상품 거래를 위한 단일 거래소다. 2005년 한국증권거래소·코스닥증권시장·한국선물거래소가 통합되어 설립되었다.

```
┌─────────────────────────────────────────────────────┐
│              한국거래소 (KRX)                        │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────┐ │
│  │  유가증권  │  │  코스닥   │  │  파생상품 시장      │ │
│  │  시장     │  │  시장     │  │  (선물·옵션)        │ │
│  │ (KOSPI)  │  │ (KOSDAQ) │  │                    │ │
│  └──────────┘  └──────────┘  └────────────────────┘ │
│  ┌──────────┐  ┌──────────┐                         │
│  │  코넥스   │  │  ETF/ELW │                         │
│  │ (KONEX)  │  │  시장     │                         │
│  └──────────┘  └──────────┘                         │
└─────────────────────────────────────────────────────┘
         │ 회원사(Member) 관계
         ▼
┌─────────────────────────────────────────────────────┐
│         증권사 (Securities Firm / Member)            │
│  투자자 주문 접수 → 거래소 전송 → 체결 결과 수신       │
└─────────────────────────────────────────────────────┘
```

### 1.1 시장별 특징

| 시장 | 정식명칭 | 상장 기준 | 대표 지수 | 특징 |
|------|----------|-----------|-----------|------|
| **KOSPI** | 유가증권시장 | 자기자본 300억 이상 등 엄격 | KOSPI 지수 | 대형·우량주 중심 |
| **KOSDAQ** | 코스닥시장 | 기술·성장기업 우대, 완화된 기준 | KOSDAQ 지수 | 중소·벤처기업 |
| **KONEX** | 코넥스시장 | 창업 초기기업, 지정자문인 필수 | - | 스타트업 자금조달 |
| **ETF** | 상장지수펀드 | ETF 요건 충족 | 추종 지수 | 펀드를 주식처럼 거래 |

> **왜 시장이 분리되어 있나?** 투자자 보호 수준과 기업 성장 단계에 따라 공시·상장 요건을 차등화하기 위함이다. 시스템 개발 시 시장구분(market type)을 필드로 관리해야 하며, 호가단위·거래시간·가격제한폭이 시장별로 다를 수 있다.

---

## 2. 매매 거래시간 (Trading Hours)

### 2.1 정규장 및 시간외 세션

| 세션 | 시간 | 방식 | 비고 |
|------|------|------|------|
| **장전 시간외 단일가** | 08:00 ~ 08:30 | 단일가 (1회 체결) | 전일 종가로만 매매 |
| **장전 동시호가** | 08:30 ~ 09:00 | 단일가 누적, 09:00 일괄체결 | 개장 직전 호가 접수 |
| **정규장 (접속매매)** | 09:00 ~ 15:20 | 접속매매 (연속체결) | 가격·시간 우선원칙 |
| **장마감 동시호가** | 15:20 ~ 15:30 | 단일가 누적, 15:30 일괄체결 | 종가 결정 |
| **장후 시간외 단일가** | 15:40 ~ 16:00 | 단일가 (10분마다 체결) | 당일 종가로만 매매 |
| **장후 시간외 종가** | 15:30 ~ 16:00 | 종가로 즉시체결 | 당일 종가 매매 |

> ※ 위 시간은 2024년 기준 KOSPI/KOSDAQ 기준이며, 향후 변경될 수 있습니다. **반드시 사내 기준 확인 필요.**

### 2.2 개발 시 주의 — 세션 관리

```kotlin
enum class TradingSession {
    PRE_MARKET_SINGLE_PRICE,   // 장전 시간외 단일가
    PRE_MARKET_SIMULTANEOUS,   // 장전 동시호가
    REGULAR,                   // 정규장
    CLOSING_SIMULTANEOUS,      // 장마감 동시호가
    AFTER_MARKET_CLOSING_PRICE,// 장후 시간외 종가
    AFTER_MARKET_SINGLE_PRICE, // 장후 시간외 단일가
    CLOSED                     // 휴장
}

data class SessionSchedule(
    val session: TradingSession,
    val start: LocalTime,
    val end: LocalTime
)

fun getCurrentSession(now: LocalTime): TradingSession {
    return when {
        now < LocalTime.of(8, 0)   -> TradingSession.CLOSED
        now < LocalTime.of(8, 30)  -> TradingSession.PRE_MARKET_SINGLE_PRICE
        now < LocalTime.of(9, 0)   -> TradingSession.PRE_MARKET_SIMULTANEOUS
        now < LocalTime.of(15, 20) -> TradingSession.REGULAR
        now < LocalTime.of(15, 30) -> TradingSession.CLOSING_SIMULTANEOUS
        now < LocalTime.of(16, 0)  -> TradingSession.AFTER_MARKET_SINGLE_PRICE
        else                       -> TradingSession.CLOSED
    }
}
```

**주문 접수 시 세션 검증은 필수**다. 세션별로 허용 호가 유형이 다르므로(예: 시간외 단일가에서 지정가는 종가로만), 주문 유형 × 세션 조합의 유효성 검사 매트릭스를 관리해야 한다.

---

## 3. 호가 종류 (Order Types — 거래소 호가)

거래소에서 정의하는 호가 유형은 증권사 HTS/MTS의 주문 유형과 매핑 관계를 가진다. 자세한 주문 유형은 [31. 주문 유형 전체](31-order-types)를 참고한다.

| 호가 유형 | 설명 | 사용 가능 세션 |
|-----------|------|---------------|
| 지정가 (Limit) | 투자자가 가격 지정 | 전 세션 |
| 시장가 (Market) | 시장 최우선가로 체결 | 정규장, 동시호가 일부 |
| 조건부 지정가 | 정규장 미체결 시 종가 단일가로 전환 | 정규장 |
| 최유리 지정가 | 접수 시점 최우선 호가 가격으로 지정 | 정규장 |
| 최우선 지정가 | 자기 최우선 호가 가격으로 지정 | 정규장 |

---

## 4. 호가단위 (Tick Size)

**호가단위(Tick Size)**란 주문 가격의 최소 변동 단위다. 가격대별로 단계적으로 커지는 구조다.

### 4.1 KOSPI 호가단위

| 주가 범위 | 호가단위 | 예시 |
|-----------|----------|------|
| 1,000원 미만 | 1원 | 950 → 951 |
| 1,000원 ~ 5,000원 미만 | 5원 | 2,000 → 2,005 |
| 5,000원 ~ 10,000원 미만 | 10원 | 7,000 → 7,010 |
| 10,000원 ~ 50,000원 미만 | 50원 | 30,000 → 30,050 |
| 50,000원 ~ 100,000원 미만 | 100원 | 75,000 → 75,100 |
| 100,000원 ~ 500,000원 미만 | 500원 | 200,000 → 200,500 |
| 500,000원 이상 | 1,000원 | 600,000 → 601,000 |

> ※ KOSDAQ 호가단위는 별도 기준이 있으며, ETF·ELW 등 상품별로 다릅니다. **사내 기준 확인 필요.**

### 4.2 개발 시 주의 — 틱사이즈 검증

```kotlin
object TickSizeCalculator {
    fun getTickSize(price: BigDecimal): BigDecimal {
        return when {
            price < BigDecimal("1000")   -> BigDecimal("1")
            price < BigDecimal("5000")   -> BigDecimal("5")
            price < BigDecimal("10000")  -> BigDecimal("10")
            price < BigDecimal("50000")  -> BigDecimal("50")
            price < BigDecimal("100000") -> BigDecimal("100")
            price < BigDecimal("500000") -> BigDecimal("500")
            else                         -> BigDecimal("1000")
        }
    }

    /**
     * 주문 가격이 호가단위에 맞는지 검증
     * 호가단위로 나누어 떨어지지 않으면 유효하지 않은 가격
     */
    fun isValidPrice(price: BigDecimal): Boolean {
        val tickSize = getTickSize(price)
        return price.remainder(tickSize) == BigDecimal.ZERO
    }

    /**
     * 가장 가까운 유효 호가로 올림 처리
     */
    fun roundUpToTickSize(price: BigDecimal): BigDecimal {
        val tickSize = getTickSize(price)
        val remainder = price.remainder(tickSize)
        return if (remainder == BigDecimal.ZERO) price
               else price.subtract(remainder).add(tickSize)
    }
}
```

**개발 시 주의**: 호가 검증 실패 시 거래소에서 주문을 거부한다. 주문 접수 단계에서 사전 검증하여 불필요한 거래소 통신을 줄여야 한다.

---

## 5. 가격제한폭 (Price Limit)

### 5.1 상하한가 제도

한국 증시는 **±30%** 가격제한폭을 적용한다(2015년 확대). 전일 종가 기준으로 당일 최대 등락폭을 제한하여 과도한 가격 급등락을 방지한다.

```
전일 종가: 10,000원
상한가 = 10,000 × 1.30 = 13,000원  (원 단위 절사·반올림 규칙 적용)
하한가 = 10,000 × 0.70 =  7,000원
```

> ※ 상·하한가 계산 시 호가단위 절사 처리가 있으며, 정확한 계산 로직은 사내 기준 확인 필요.

```kotlin
data class PriceLimit(
    val upperLimit: BigDecimal,  // 상한가
    val lowerLimit: BigDecimal,  // 하한가
    val basePrice: BigDecimal    // 기준가 (전일 종가)
)

fun calculatePriceLimit(basePrice: BigDecimal): PriceLimit {
    val limitRate = BigDecimal("0.30")
    val rawUpper = basePrice.multiply(BigDecimal.ONE.add(limitRate))
    val rawLower = basePrice.multiply(BigDecimal.ONE.subtract(limitRate))

    // 호가단위에 맞게 보정 (상한가는 내림, 하한가는 올림)
    val upperLimit = floorToTickSize(rawUpper)
    val lowerLimit = ceilToTickSize(rawLower)

    return PriceLimit(upperLimit, lowerLimit, basePrice)
}
```

### 5.2 신규 상장 종목 특례

신규 상장(IPO) 종목의 경우 **상장 첫날 가격제한폭이 공모가의 60% ~ 400%**로 확대 적용된다. 시스템은 종목별 가격제한폭 예외 처리를 지원해야 한다.

---

## 6. 변동성 완화 장치 (VI, Volatility Interruption)

**VI(Volatility Interruption)**는 단기간 급격한 가격 변동 시 2분간 단일가 매매로 전환하는 제도다. 거래 대기 시간을 주어 투자자가 과열된 시장을 재평가할 기회를 제공한다.

### 6.1 동적 VI (Dynamic VI)

- **발동 조건**: 직전 체결가 대비 단기간(약 1~2분) 내 가격이 일정 % 이상 변동
- **효과**: 해당 종목 2분간 단일가 매매 전환
- **목적**: 알고리즘 매매, 오주문 등에 의한 순간 급변 완충

### 6.2 정적 VI (Static VI)

- **발동 조건**: 당일 기준가(전일 종가) 대비 일정 % 이상 가격 변동
- **효과**: 동적 VI와 동일하게 2분 단일가 전환
- **목적**: 중기적 가격 급변 완충

```
┌─────────────────────────────────────────────┐
│           VI 발동 흐름                       │
│                                              │
│  체결가 급변 감지                              │
│       │                                      │
│       ▼                                      │
│  VI 발동 공시 (종목코드, 발동유형, 기준가)      │
│       │                                      │
│       ▼                                      │
│  2분간 단일가 누적 (접속매매 중단)              │
│       │                                      │
│       ▼                                      │
│  2분 후 단일가 일괄 체결 → 접속매매 재개        │
└─────────────────────────────────────────────┘
```

### 6.3 개발 시 주의

- 거래소에서 VI 발동/해제 실시간 메시지를 수신한다. 해당 메시지 수신 즉시 주문 처리 모드를 전환해야 한다.
- VI 발동 중 접수된 주문은 단일가 호가창에 누적되며, VI 해제 시 일괄 체결된다.
- 고객 화면에 VI 발동 상태를 명확히 표시해야 한다 (체결 지연 안내).

---

## 7. 서킷브레이커 (CB, Circuit Breaker)

**서킷브레이커(Circuit Breaker)**는 시장 전체의 급락 시 모든 종목의 거래를 일시 중단하는 제도다. 1987년 블랙먼데이 이후 전 세계 거래소에 도입되었으며, 한국은 1998년 도입했다.

### 7.1 발동 단계

| 단계 | 발동 조건 (KOSPI 기준) | 매매 중단 | 재개 |
|------|----------------------|-----------|------|
| 1단계 | KOSPI 전일 대비 -8% 이상, 1분 이상 지속 | 20분 중단 | 10분 단일가 후 재개 |
| 2단계 | KOSPI 전일 대비 -15% 이상, 1분 이상 지속 | 20분 중단 | 10분 단일가 후 재개 |
| 3단계 | KOSPI 전일 대비 -20% 이상, 1분 이상 지속 | 당일 거래 종료 | 재개 없음 |

> - 각 단계는 1일 1회만 발동 가능  
> - 14:50 이후에는 1·2단계 발동 안 함  
> - 3단계는 시간 무관 발동 가능  
> ※ 위 수치는 예시이며 사내 기준 확인 필요

### 7.2 개발 시 주의

서킷브레이커 발동 메시지는 거래소로부터 실시간으로 수신된다. 시스템은:
- 발동 즉시 모든 신규 주문 접수 차단
- 미체결 주문의 처리 보류 (취소 여부는 사내 정책에 따름)
- 고객 알림 발송 (Push, SMS 등)
- 재개 후 정상화 처리

```kotlin
sealed class MarketHaltEvent {
    data class CircuitBreakerTriggered(
        val stage: Int,              // 1, 2, 3단계
        val triggeredAt: Instant,
        val resumeAt: Instant?,      // 3단계는 null
        val kospiChangeRate: BigDecimal
    ) : MarketHaltEvent()

    data class CircuitBreakerLifted(
        val stage: Int,
        val liftedAt: Instant
    ) : MarketHaltEvent()

    data class SideCar(
        val market: String,          // KOSPI200 선물 기준
        val triggeredAt: Instant,
        val resumeAt: Instant
    ) : MarketHaltEvent()
}
```

---

## 8. 사이드카 (Sidecar)

**사이드카(Sidecar)**는 선물 시장의 급변이 현물 시장에 미치는 충격을 완화하기 위한 프로그램 매매 호가 효력 일시 정지 제도다.

| 구분 | 발동 조건 | 효과 | 지속 시간 |
|------|-----------|------|-----------|
| KOSPI 사이드카 | KOSPI200 선물 전일 대비 ±5% 변동, 1분 이상 지속 | 프로그램 매매 호가 5분 효력 정지 | 5분 |
| KOSDAQ 사이드카 | KOSDAQ150 선물 기준 | 동일 | 5분 |

> - 1일 1회 발동 가능  
> - 14:50 이후 발동 불가  
> ※ 수치는 예시이며 사내 기준 확인 필요

프로그램 매매(바스켓 주문) 시스템을 운영하는 경우 사이드카 메시지 수신 시 해당 주문을 보류하는 로직이 필요하다.

---

## 9. 매매체결 원칙 (Matching Principles)

### 9.1 세 가지 우선 원칙

정규장(접속매매) 체결은 다음 우선 순위로 이루어진다:

```
1순위: 가격 우선 (Price Priority)
  - 매수: 높은 가격 → 낮은 가격 순 (더 비싸게 사겠다는 주문이 우선)
  - 매도: 낮은 가격 → 높은 가격 순 (더 싸게 팔겠다는 주문이 우선)

2순위: 시간 우선 (Time Priority)
  - 같은 가격이면 먼저 접수된 주문이 우선 체결

3순위: 수량 우선 (Quantity Priority) ※ 단일가 매매에서만 적용
  - 같은 가격, 같은 시간이면 수량이 많은 주문 우선
```

### 9.2 단일가 매매 vs 접속매매

| 구분 | 단일가 (Call Auction) | 접속매매 (Continuous Auction) |
|------|----------------------|------------------------------|
| 체결 방식 | 일정 시간 호가 누적 후 단일 가격으로 일괄 체결 | 매수/매도 호가 매칭 즉시 체결 |
| 적용 세션 | 동시호가, 시간외 단일가, VI 발동 중 | 정규장 (09:00~15:20) |
| 체결 가격 | 최대 체결 수량이 되는 단일 가격 결정 | 매수·매도 가격이 만나는 즉시 체결 |

```
[단일가 체결 예시]
매수 호가  │ 매도 호가
─────────────────────
10,100원 x 500주 │ 9,900원 x 300주
10,050원 x 1,000주│ 10,000원 x 800주  ← 체결가격 (최대 체결량)
9,950원 x 700주  │ 10,050원 x 400주
```

---

## 10. 매매단위 (Trading Unit)

주식의 기본 거래 단위는 **1주**다(2000년 이전에는 단위주 제도가 있었으나 현재는 폐지). ETF·ELW 등 일부 상품은 단위가 다를 수 있다.

```kotlin
fun validateOrderQuantity(quantity: Int, minUnit: Int = 1): Boolean {
    return quantity > 0 && quantity % minUnit == 0
}
```

---

## 11. 거래소-회원사(증권사) 관계

### 11.1 구조

```
투자자 (Investor)
    │  주문
    ▼
증권사 (Member Firm)
    │  FIX Protocol / KRX 전용 통신
    ▼
한국거래소 (KRX) 매매시스템
    │  체결 결과
    ▼
증권사 → 투자자 통보
```

### 11.2 회원사 의무

- 거래소 회원 자격 유지 (순자본비율 등 재무 요건)
- 투자자 주문의 유효성 검증 후 전송 (Pre-trade Validation)
- 위험관리 한도 초과 주문 차단 (RMS: Risk Management System)
- 체결 결과 투자자에게 즉시 통보

### 11.3 개발 시 주의

- 거래소와의 통신 프로토콜은 FIX Protocol 기반 KRX 규격을 따른다.
- 네트워크 장애 시 재연결·재전송 로직이 필요하다.
- 주문 ID(클라이언트 주문번호, ClOrdID)와 거래소 주문번호 매핑 관리가 핵심이다.

---

## 12. 시장조성/유동성공급 (Market Making / LP)

**시장조성자(Market Maker, LP: Liquidity Provider)**는 특정 종목에 대해 거래소와 계약을 맺고, 일정 수준 이상의 매수·매도 호가를 상시 제공할 의무를 지는 기관이다.

### 12.1 LP의 역할

- 유동성이 낮은 종목(ETF, 소형주, ELW 등)의 거래 활성화
- 매수-매도 스프레드(Spread) 최소화
- 시장 충격(Market Impact) 완화

### 12.2 LP 주문의 특징

```kotlin
data class OrderSource(
    val type: OrderSourceType,
    val lpContractId: String? = null  // LP 계약 종목 ID
)

enum class OrderSourceType {
    RETAIL_CUSTOMER,   // 일반 투자자
    INSTITUTIONAL,     // 기관 투자자
    MARKET_MAKER,      // 시장조성자 (LP)
    PROGRAM_TRADING    // 프로그램 매매
}
```

LP 주문은 별도 마킹이 필요하며, LP 의무 이행 실적 보고 배치가 요구되는 경우가 있다.

---

## 13. 개발 체크리스트

다음 항목들은 시장 구조를 이해하고 시스템 설계 시 반드시 고려해야 할 사항이다:

- [ ] 세션(Session) 관리: 시각 기반 현재 세션 판별 로직
- [ ] 세션별 허용 주문 유형 매트릭스 관리
- [ ] 호가단위(Tick Size) 검증: 가격대별 로직 구현
- [ ] 가격제한폭(Price Limit) 계산 및 검증
- [ ] VI 발동/해제 메시지 수신 및 주문 모드 전환
- [ ] 서킷브레이커 단계별 대응 로직
- [ ] 사이드카 대응 (프로그램 매매 보류)
- [ ] 체결 원칙(가격·시간·수량 우선) 구현 (자체 체결 엔진 시)
- [ ] 거래소 통신 장애 대응 (재연결, 재전송)
- [ ] 주문번호(ClOrdID) ↔ 거래소 주문번호 매핑 관리
- [ ] 신규 상장 종목 특례 가격제한폭 처리
- [ ] 휴장일 캘린더 관리 (→ [32. 청산·결제·정산](32-clearing-settlement) 참조)

---

## 14. 정리

한국 증권 시장은 KRX를 중심으로 KOSPI, KOSDAQ, KONEX, 파생상품 시장이 구성되어 있다. 증권사 시스템은 이 시장의 **회원사**로서 투자자 주문을 안전하게 처리하고 거래소와 통신하는 역할을 한다. 시장 구조의 이해는 단순한 배경 지식이 아니라 **주문 유효성 검증, 세션 관리, 이상 시장 대응** 등 핵심 개발 로직의 직접적인 근거가 된다.

다음 문서에서는 이 시장 구조 위에서 동작하는 구체적인 주문 유형들을 다룬다.

---

이전: [29. 기타 선행 주제](29-misc) · 다음: [31. 주문 유형 전체](31-order-types) · [전체 커리큘럼](/curriculum)
