# 36. 리스크 관리와 컴플라이언스

## 개요

증권사 시스템에서 리스크 관리(Risk Management)와 컴플라이언스(Compliance)는 선택이 아닌 법적 의무다. 단 하나의 fat-finger(오조작) 주문이 수십억 원의 손실을 낼 수 있고, 미탐지된 시세조종 패턴은 금융감독원의 기관 제재로 이어질 수 있다. 개발자 관점에서 이 두 영역은 **주문 처리 파이프라인에 삽입되는 검증 게이트**와 **변경 불가능한 감사 로그 시스템**으로 구현된다. 이 문서는 사전 리스크 체크(Pre-trade Risk)부터 이상거래탐지(Abnormal Trade Detection), 규제 대응, 시스템 아키텍처까지 전체 그림을 다룬다.

> **주의**: 이 문서의 한도 수치·규정 기준은 예시이며, 실제 적용 시 금융감독원 규정, 자본시장법, 사내 준법감시팀 기준을 반드시 확인해야 한다.

---

## 1. 주문 전 위험관리 (Pre-Trade Risk)

### 1.1 개념과 필요성

주문이 거래소에 도달하기 전에 차단하는 것이 핵심이다. 체결 이후에는 취소가 매우 어렵고, 이미 시장에 영향을 준다.

```
[고객 주문 입력]
        │
        ▼
[Pre-Trade Risk Gate]  ← 이 레이어에서 차단
  ├── 한도 체크
  ├── 가격/수량 이상치 검사
  ├── 종목 거래 가능 여부
  └── 포지션 한도 체크
        │ 통과
        ▼
[OMS → 거래소]
```

### 1.2 주문 한도 체크 (Order Limit Check)

| 한도 유형 | 설명 | 예시 |
|----------|------|------|
| 1회 주문금액 한도 | 단일 주문의 최대 금액 | 계좌별 10억원 |
| 1일 주문금액 한도 | 하루 누적 주문금액 | 계좌별 50억원 |
| 1일 주문수량 한도 | 하루 누적 주문 건수 | 계좌별 1,000건 |
| 종목별 한도 | 특정 종목 최대 보유 수량/금액 | 종목당 전체 발행주식 5% |
| 계좌별 신용 한도 | 총 신용잔고 한도 | 고객 등급별 차등 |

```kotlin
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong

data class OrderRequest(
    val accountId: String,
    val stockCode: String,
    val orderQty: Long,
    val orderPrice: BigDecimal,
    val orderSide: OrderSide  // BUY / SELL
)

enum class OrderSide { BUY, SELL }

data class RiskCheckResult(
    val passed: Boolean,
    val failedRules: List<String> = emptyList(),
    val errorCode: String? = null
)

data class AccountLimitConfig(
    val singleOrderMaxAmount: BigDecimal,   // 1회 주문 한도
    val dailyOrderMaxAmount: BigDecimal,    // 일일 누적 한도
    val dailyOrderMaxCount: Long            // 일일 주문 건수 한도
)

// Redis 기반 일일 누적 추적 (실제 구현은 Redis INCR/INCRBY)
class DailyAccumulator {
    private val amountMap = mutableMapOf<String, BigDecimal>()
    private val countMap  = mutableMapOf<String, AtomicLong>()

    fun getAmount(accountId: String): BigDecimal =
        amountMap.getOrDefault(accountId, BigDecimal.ZERO)

    fun getCount(accountId: String): Long =
        countMap.getOrDefault(accountId, AtomicLong(0)).get()

    fun add(accountId: String, amount: BigDecimal) {
        amountMap[accountId] = getAmount(accountId).add(amount)
        countMap.getOrPut(accountId) { AtomicLong(0) }.incrementAndGet()
    }
}
```

### 1.3 Fat-Finger 방지 (Price/Quantity Anomaly)

"Fat-finger"는 입력 실수로 주문가격이나 수량이 비정상적으로 큰 값이 되는 오류다. 2010년 미국 Flash Crash의 원인 중 하나다.

```kotlin
import java.math.BigDecimal
import java.math.RoundingMode

data class FatFingerConfig(
    // 현재가 대비 최대 괴리율 (예: ±30%)
    val maxPriceDeviationRate: BigDecimal = BigDecimal("0.30"),
    // 단일 주문 최대 수량 (전체 발행주식수 대비 비율)
    val maxQtyRatioOfShares: BigDecimal = BigDecimal("0.01"),   // 1%
    // 절대 최소/최대 주문 수량
    val minQty: Long = 1L,
    val maxQty: Long = 10_000_000L
)

object FatFingerChecker {

    fun checkPrice(
        orderPrice: BigDecimal,
        currentPrice: BigDecimal,
        config: FatFingerConfig
    ): RiskCheckResult {
        if (currentPrice <= BigDecimal.ZERO) {
            return RiskCheckResult(passed = true) // 현재가 없으면 패스 (상장 초기 등)
        }

        val deviation = orderPrice.subtract(currentPrice).abs()
            .divide(currentPrice, 6, RoundingMode.HALF_UP)

        return if (deviation > config.maxPriceDeviationRate) {
            RiskCheckResult(
                passed = false,
                failedRules = listOf("PRICE_DEVIATION_EXCEEDED"),
                errorCode = "E_PRICE_ABNORMAL"
            )
        } else {
            RiskCheckResult(passed = true)
        }
    }

    fun checkQuantity(
        orderQty: Long,
        totalIssuedShares: Long,
        config: FatFingerConfig
    ): RiskCheckResult {
        val errors = mutableListOf<String>()

        if (orderQty < config.minQty || orderQty > config.maxQty) {
            errors.add("QTY_OUT_OF_ABSOLUTE_RANGE")
        }

        val qtyRatio = BigDecimal(orderQty)
            .divide(BigDecimal(totalIssuedShares), 6, RoundingMode.HALF_UP)
        if (qtyRatio > config.maxQtyRatioOfShares) {
            errors.add("QTY_EXCEEDS_ISSUED_SHARES_RATIO")
        }

        return if (errors.isEmpty()) {
            RiskCheckResult(passed = true)
        } else {
            RiskCheckResult(passed = false, failedRules = errors, errorCode = "E_QTY_ABNORMAL")
        }
    }
}
```

### 1.4 종목 거래 가능 여부

| 상태 | 코드(예시) | 주문 가능 여부 |
|------|-----------|--------------|
| 정상 | NORMAL | 가능 |
| 거래정지 | HALT | 불가 |
| 투자유의 | CAUTION | 가능 (경고 표시) |
| 투자경고 | WARNING | 가능 (경고 표시, 증거금 100%) |
| 투자위험 | DANGER | 매수 불가 |
| 관리종목 | ADMIN | 가능 (증거금 100%) |
| 상장폐지예정 | DELIST | 매수 불가 |

### 1.5 실시간 포지션 한도

매수 주문 체결 시마다 계좌의 특정 종목 보유 비율이 규정 한도를 넘지 않도록 체크한다. 예를 들어 자본시장법상 단일 법인이 특정 주권 상장법인 주식의 5% 이상을 취득하면 보고 의무가 발생한다.

```
포지션 한도 체크:
  현재 보유량 + 주문 수량 > 임계치 → 경고 또는 차단
```

---

## 2. Pre-Trade Risk 파이프라인 (Kotlin)

### 2.1 검증 게이트 체인

```kotlin
import java.math.BigDecimal

// 단일 책임 원칙: 각 규칙은 하나의 체크만 수행
interface RiskRule {
    val ruleName: String
    fun check(order: OrderRequest, context: RiskContext): RiskCheckResult
}

data class RiskContext(
    val currentPrice: BigDecimal,
    val totalIssuedShares: Long,
    val dailyAccumulator: DailyAccumulator,
    val limitConfig: AccountLimitConfig,
    val fatFingerConfig: FatFingerConfig,
    val stockStatus: String  // "NORMAL", "HALT", etc.
)

// 주문금액 한도 규칙
class SingleOrderAmountRule : RiskRule {
    override val ruleName = "SINGLE_ORDER_AMOUNT_LIMIT"

    override fun check(order: OrderRequest, context: RiskContext): RiskCheckResult {
        val orderAmount = BigDecimal(order.orderQty).multiply(order.orderPrice)
        return if (orderAmount > context.limitConfig.singleOrderMaxAmount) {
            RiskCheckResult(
                passed = false,
                failedRules = listOf(ruleName),
                errorCode = "E_SINGLE_ORDER_LIMIT"
            )
        } else RiskCheckResult(passed = true)
    }
}

// 일일 누적 한도 규칙
class DailyAccumulatedAmountRule : RiskRule {
    override val ruleName = "DAILY_AMOUNT_LIMIT"

    override fun check(order: OrderRequest, context: RiskContext): RiskCheckResult {
        val orderAmount = BigDecimal(order.orderQty).multiply(order.orderPrice)
        val accumulated = context.dailyAccumulator.getAmount(order.accountId)
        return if (accumulated.add(orderAmount) > context.limitConfig.dailyOrderMaxAmount) {
            RiskCheckResult(
                passed = false,
                failedRules = listOf(ruleName),
                errorCode = "E_DAILY_LIMIT"
            )
        } else RiskCheckResult(passed = true)
    }
}

// 종목 거래정지 규칙
class StockHaltRule : RiskRule {
    override val ruleName = "STOCK_HALT_CHECK"
    private val tradableStatuses = setOf("NORMAL", "CAUTION", "WARNING", "ADMIN")

    override fun check(order: OrderRequest, context: RiskContext): RiskCheckResult {
        return if (context.stockStatus !in tradableStatuses) {
            RiskCheckResult(
                passed = false,
                failedRules = listOf(ruleName),
                errorCode = "E_STOCK_NOT_TRADABLE"
            )
        } else RiskCheckResult(passed = true)
    }
}

// Fat-Finger 가격 체크 규칙
class PriceAnomalyRule : RiskRule {
    override val ruleName = "FAT_FINGER_PRICE"

    override fun check(order: OrderRequest, context: RiskContext): RiskCheckResult =
        FatFingerChecker.checkPrice(
            order.orderPrice,
            context.currentPrice,
            context.fatFingerConfig
        )
}

// 파이프라인 실행기
class PreTradeRiskPipeline(private val rules: List<RiskRule>) {

    /**
     * 모든 규칙을 순서대로 실행.
     * fail-fast 모드: 첫 실패 시 즉시 반환.
     * 또는 전체 실행 후 모든 실패 집계 가능.
     */
    fun evaluate(order: OrderRequest, context: RiskContext): RiskCheckResult {
        val allFailures = mutableListOf<String>()

        for (rule in rules) {
            val result = runCatching { rule.check(order, context) }
                .getOrElse {
                    // 규칙 실행 중 예외 → 안전하게 차단
                    return RiskCheckResult(
                        passed = false,
                        failedRules = listOf("RULE_EXECUTION_ERROR:${rule.ruleName}"),
                        errorCode = "E_RISK_INTERNAL_ERROR"
                    )
                }

            if (!result.passed) {
                allFailures.addAll(result.failedRules)
                // fail-fast: 첫 실패 시 즉시 반환
                return RiskCheckResult(
                    passed = false,
                    failedRules = allFailures,
                    errorCode = result.errorCode
                )
            }
        }

        return RiskCheckResult(passed = true)
    }
}

// 파이프라인 조립 (DI 컨테이너에서 Bean으로 등록)
fun buildDefaultPipeline(): PreTradeRiskPipeline = PreTradeRiskPipeline(
    rules = listOf(
        StockHaltRule(),
        SingleOrderAmountRule(),
        DailyAccumulatedAmountRule(),
        PriceAnomalyRule()
        // 필요 시 추가 규칙 삽입
    )
)
```

---

## 3. 사후 모니터링 (Post-Trade Monitoring)

### 3.1 이상거래탐지 (Abnormal Trade Detection)

거래소와 금융감독원은 증권사에 이상거래 탐지 및 보고 의무를 부여한다. 주요 탐지 패턴은 아래와 같다.

#### 패턴 1: 시세조종 (Market Manipulation)

```
[허수 주문 패턴 (Spoofing)]
  - 대량 매수 주문 → 가격 상승 유도
  - 실제 매도 후 → 매수 주문 취소
  탐지: 주문 취소율이 비정상적으로 높은 계좌

[가장매매 (Wash Trading / 자전거래)]
  - 같은 주체가 매수/매도 반복
  - 거래량이 많아 보이도록 부풀리기
  탐지: 동일 계좌/관련 계좌 간 반복 거래 패턴

[연속 고가 매수 (Painting the Tape)]
  - 종가 직전 소량 고가 매수로 종가 끌어올리기
  탐지: 장 마감 직전 특정 계좌의 반복 고가 체결
```

#### 패턴 2: 내부자 거래 (Insider Trading) 의심

- 공시 직전 비정상적 거래량 급증
- 특정 계좌의 공시 전 집중 매수/매도

#### 탐지 시스템 설계

```
[체결 스트림]
        │
        ▼
[패턴 분석 엔진]
  - 슬라이딩 윈도우 기반 통계
  - 계좌별 행동 프로파일
  - 이상 스코어(Anomaly Score) 계산
        │
   임계치 초과
        ▼
[Alert 발행]
  - 준법감시팀 알림
  - 증거 데이터 스냅샷 저장
  - 규제기관 보고 큐에 적재
```

### 3.2 AML (Anti-Money Laundering) / KYC (Know Your Customer)

| 항목 | 설명 | 시스템 구현 |
|------|------|------------|
| KYC | 고객 신원 확인 | 계좌 개설 시 신분증 OCR, 실명 확인 API |
| EDD | 강화된 고객 확인 (Enhanced Due Diligence) | 고위험 고객 주기적 재확인 |
| STR | 의심거래 보고 (Suspicious Transaction Report) | 임계치 초과 거래 금융정보분석원(KoFIU) 자동 보고 |
| CTR | 고액현금거래 보고 (Currency Transaction Report) | 1일 1천만원 이상 현금 거래 자동 보고 |

**개발 포인트**: KoFIU 보고는 정해진 전문(電文) 포맷으로 제출해야 한다. 보고 실패 시 재시도 큐(Retry Queue)와 수동 처리 대기열이 필요하다.

---

## 4. 규제 준수 시스템

### 4.1 자본시장법 핵심 개발 관련 사항

| 조항 (요약) | 내용 | 개발 과제 |
|------------|------|----------|
| 주문기록 보존 | 주문·체결·취소 5년 보존 | 불변 스토리지(Immutable Storage) |
| 투자자 보호 | 부적합 상품 투자 차단 | 상품 적합성 평가 시스템 |
| 공시 의무 | 5% 이상 지분 취득 보고 | 보고서 자동 생성, 제출 |
| 공매도 규정 | 무차입 공매도 금지 | 대주 잔고 확인 후 매도 허용 |
| 내부통제 | 이상거래 탐지·보고 의무 | 이상거래탐지 시스템 |

### 4.2 금융감독원·거래소 보고

```
[정기 보고 체계]
├── 일간 보고
│   ├── 매매 명세 (거래소)
│   ├── 미결제 약정 (파생, 거래소)
│   └── 의심거래 (금융정보분석원)
├── 주간 보고
│   └── 신용공여 현황
└── 월간/분기/연간 보고
    ├── 경영공시
    └── 리스크 현황 보고

[시스템 요건]
- 보고 포맷: 거래소/금감원 지정 전문(XML/고정길이 등)
- 보고 기한: 마감 시각 엄수 (자동화 필수)
- 보고 실패 처리: 재시도 + 수동 개입 알림
```

---

## 5. 감사 추적 (Audit Trail)

### 5.1 감사 로그 의무화 항목

자본시장법과 내부통제 기준에 따라 아래 항목은 **변경 불가능한 로그**로 보존해야 한다.

| 항목 | 보존 기간 | 포함 내용 |
|------|---------|----------|
| 주문 이력 | 5년 | 주문 전문, 수신 시각, 처리 결과 |
| 체결 이력 | 5년 | 체결 번호, 가격, 수량, 체결 시각 |
| 취소·정정 이력 | 5년 | 원주문 참조, 변경 내용, 사유 |
| 로그인·접근 로그 | 5년 | 사용자 ID, IP, 기기 정보, 시각 |
| 관리자 조작 로그 | 5년 | 관리자 ID, 변경 대상, 변경 전/후 |
| 리스크 한도 변경 | 영구 | 변경자, 승인자, 변경 내용 |

### 5.2 불변 감사 로그 설계

```kotlin
import java.time.Instant
import java.util.UUID

data class AuditEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: AuditEventType,
    val accountId: String?,
    val operatorId: String?,            // 관리자 조작 시 직원 ID
    val resourceType: String,           // "ORDER", "ACCOUNT", "LIMIT" 등
    val resourceId: String,
    val action: String,                 // "CREATE", "UPDATE", "DELETE", "LOGIN" 등
    val before: String? = null,         // JSON 직렬화된 변경 전 상태
    val after: String? = null,          // JSON 직렬화된 변경 후 상태
    val ipAddress: String?,
    val userAgent: String?,
    val occurredAt: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
)

enum class AuditEventType {
    ORDER_SUBMITTED,
    ORDER_CANCELLED,
    ORDER_AMENDED,
    TRADE_EXECUTED,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    ACCOUNT_MODIFIED,
    LIMIT_CHANGED,
    RISK_ALERT_TRIGGERED,
    SUSPICIOUS_TRADE_DETECTED,
    REGULATORY_REPORT_SENT
}

// 감사 로그는 절대 UPDATE/DELETE 없이 INSERT만
// Append-Only 저장소 사용 (S3 Object Lock, Kafka + 장기 보관 등)
interface AuditLogRepository {
    suspend fun append(event: AuditEvent)    // INSERT만 허용
    suspend fun query(
        accountId: String?,
        eventType: AuditEventType?,
        from: Instant,
        to: Instant
    ): List<AuditEvent>
    // DELETE 메서드 없음 — 의도적 설계
}
```

### 5.3 구현 아키텍처

```
[서비스 레이어]
  AuditService.record(event)
        │ 비동기
        ▼
[Kafka Topic: audit-events]
        │
   ┌────┴────┐
   ▼         ▼
[DB 저장]  [S3 아카이브]
(Append Only  (WORM:
 파티션 테이블  Write Once
 5년 보존)     Read Many)
```

**개발 포인트**:
- 감사 로그 기록 실패는 **비즈니스 트랜잭션을 롤백시키면 안 된다**. 별도 비동기 큐로 처리하고, 감사 로그 누락 자체를 별도 모니터링한다.
- 감사 로그에는 PII(개인정보)가 포함되므로 접근 제어 및 암호화 필수.

---

## 6. 내부통제 (Internal Control)

### 6.1 직무 분리 (Segregation of Duties)

| 역할 | 수행 가능 업무 |
|------|--------------|
| 트레이더 | 주문 입력, 포지션 조회 |
| 리스크 담당 | 한도 설정·변경, 이상거래 검토 |
| 준법감시인 | 보고서 생성, 규제 제출, 한도 최종 승인 |
| IT 관리자 | 시스템 설정 변경 (비즈니스 데이터 접근 제한) |
| 감사팀 | 감사 로그 조회 전용 (변경 불가) |

### 6.2 4-eyes 원칙 (Two-Person Integrity)

한도 변경, 대규모 주문 등 고위험 작업은 반드시 2인 이상의 승인이 필요하다.

```
[한도 변경 워크플로우]
  리스크 담당자 → 변경 요청 생성
        │
        ▼
  준법감시인 → 1차 승인
        │
        ▼
  (금액 임계치 초과 시) 임원 → 2차 승인
        │
        ▼
  시스템 한도 변경 반영 (감사 로그 기록)
```

---

## 7. 시스템 알림과 모니터링

### 7.1 알림 계층 구조

```
[이벤트 발생]
        │
        ▼
[심각도 분류]
  ├── CRITICAL: 즉각 대응 필요
  │   ├── 리스크 한도 90% 초과
  │   ├── 반대매매 실패
  │   └── 규제 보고 실패
  ├── HIGH: 당일 내 대응
  │   ├── 이상거래 패턴 탐지
  │   └── 마진콜 발생
  ├── MEDIUM: 영업일 내 검토
  │   ├── 경고 단계 담보비율
  │   └── 한도 80% 초과
  └── LOW: 정기 검토
      └── 소액 의심 거래
```

### 7.2 알림 발송 채널

| 심각도 | 채널 |
|--------|------|
| CRITICAL | SMS + 전화 + 사내 채팅(Slack 등) + 이메일 |
| HIGH | SMS + 사내 채팅 + 이메일 |
| MEDIUM | 사내 채팅 + 이메일 |
| LOW | 이메일 + 대시보드 |

---

## 8. 리스크 관리 시스템 전체 아키텍처

```
┌────────────────────────────────────────────────────────────────┐
│                    리스크 관리 플랫폼                           │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              Pre-Trade Risk Gate                        │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │  │
│  │  │ 한도체크  │ │Fat-Finger│ │ 종목상태  │ │ 포지션  │  │  │
│  │  │  Rule    │ │  Rule    │ │  Rule    │ │ 한도Rule │  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                          │ 주문 통과                           │
│  ┌───────────────────────▼─────────────────────────────────┐  │
│  │              실시간 포지션 추적                           │  │
│  │  ┌──────────────────────────────────────────────────┐   │  │
│  │  │  체결 이벤트 → 포지션 업데이트 → 리스크 재계산   │   │  │
│  │  └──────────────────────────────────────────────────┘   │  │
│  └─────────────────────────────────────────────────────────┘  │
│                          │                                     │
│  ┌───────────────────────▼─────────────────────────────────┐  │
│  │              사후 모니터링 레이어                        │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │  │
│  │  │이상거래  │ │  AML/    │ │ 담보비율  │ │ 규제보고  │  │  │
│  │  │  탐지   │ │  KYC    │ │  모니터링 │ │  스케줄  │  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                          │                                     │
│  ┌───────────────────────▼─────────────────────────────────┐  │
│  │              감사 로그 / 이벤트 버스                     │  │
│  │  Kafka → [DB Append-Only] + [S3 WORM Archive]           │  │
│  └─────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

---

## 9. 주요 리스크 시나리오 대응 체크리스트

```
[시스템 장애 발생 시 리스크 절차]
□ 시세 수신 중단 → 현재가 기반 리스크 계산 중단 → 신규 주문 차단
□ OMS 지연 → 체결 미수신 → 포지션 불일치 → 수동 조정 절차 가동
□ 반대매매 배치 실패 → 준법감시팀 즉시 알림 → 수동 반대매매 절차
□ 감사 로그 저장 실패 → 큐 적체 알림 → DR(Disaster Recovery) 사이트 전환
□ 규제 보고 실패 → 재시도 3회 후 수동 보고 절차 → 감독기관 지연 통보

[신규 기능 출시 전 컴플라이언스 체크]
□ 준법감시팀 사전 검토 완료
□ 개인정보보호 영향평가(PIA) 완료 (개인정보 처리 시)
□ 새 주문 유형 → Pre-Trade Risk 규칙 추가 여부 검토
□ 감사 로그 대상 이벤트 추가 여부 검토
□ 규제 보고 대상 데이터 변경 여부 검토
□ 한도 설정 기본값 준법감시팀 승인
```

---

## 10. 개발자 관점 핵심 정리

| 영역 | 핵심 원칙 | 안티패턴 |
|------|---------|---------|
| Pre-Trade Risk | 주문 파이프라인 최앞단에서 차단 | 체결 후 사후 보정 시도 |
| 한도 관리 | Redis Atomic 연산으로 경합 방지 | DB 트랜잭션으로 한도 체크 (느림) |
| Fat-Finger | 현재가 대비 상대 괴리율 사용 | 절대값 기준만 사용 (종목별 가격대 다름) |
| 감사 로그 | Append-Only, 비동기 처리 | 비즈니스 트랜잭션에 동기 결합 |
| 이상거래 | 패턴 기반 스코어링, 준법 담당자 검토 | 완전 자동 차단 (오탐지 위험) |
| 규제 보고 | 자동화 + 실패 알림 + 수동 백업 절차 | 수동 전송 의존 |
| 직무 분리 | 시스템 권한으로 강제 구현 | 정책·문서만으로 통제 |

> **설계 철학**: 리스크 시스템은 "통과시키는 것"보다 "차단해야 할 것을 확실히 차단"하는 것이 우선이다. 불확실할 때는 차단하고, 사유를 명확히 기록해 수동 해제 경로를 제공한다.

---

이전: [35. 시세·시장 데이터](35-market-data.md) · 다음: [37. 시스템 아키텍처와 고가용성](37-architecture-ha.md) · [전체 커리큘럼](../CURRICULUM.md)
