# 46. 캡스톤: 미니 증권 매매 시스템 만들기

> **대상**: LEVEL 0~8을 모두 학습한 증권사 백엔드 개발자  
> **목표**: 지금까지 배운 모든 개념을 통합하여 실제 동작하는 미니 증권 매매 시스템(Mini Stock Trading System)을 설계·구현한다.

---

## 목차

1. [시스템 요구사항 정의](#1-시스템-요구사항-정의)
2. [전체 아키텍처 설계](#2-전체-아키텍처-설계)
3. [멀티모듈 프로젝트 구조](#3-멀티모듈-프로젝트-구조)
4. [도메인 모델 설계](#4-도메인-모델-설계)
5. [DB 스키마 설계](#5-db-스키마-설계)
6. [REST API 명세](#6-rest-api-명세)
7. [핵심 컴포넌트 구현](#7-핵심-컴포넌트-구현)
8. [동시성·정합성 전략](#8-동시성정합성-전략)
9. [이벤트 기반 아키텍처](#9-이벤트-기반-아키텍처)
10. [실시간 시세 푸시](#10-실시간-시세-푸시)
11. [테스트 전략](#11-테스트-전략)
12. [관측성 및 배포](#12-관측성-및-배포)
13. [6주 구현 로드맵](#13-6주-구현-로드맵)
14. [확장 과제](#14-확장-과제)

---

## 1. 시스템 요구사항 정의

### 1.1 기능 요구사항 (Functional Requirements)

#### 회원·계좌 관리
| 기능 | 설명 |
|------|------|
| 회원 가입/로그인 | JWT 기반 인증, 역할(일반/VIP) |
| 증권 계좌 개설 | 계좌번호 자동 발급, 1인 다계좌 지원 |
| 계좌 잔고 조회 | 예수금(KRW), 보유 종목별 수량·평단가·평가금액·손익 |

#### 입출금 (Deposit/Withdrawal)
| 기능 | 설명 |
|------|------|
| 입금 | 은행 연동(Mock) → 예수금 증가 |
| 출금 | 가용 예수금 범위 내, D+2 정산 고려 |
| 입출금 내역 조회 | 페이징, 날짜 필터 |

#### 주문 (Order)
| 기능 | 설명 |
|------|------|
| 지정가 주문 (LIMIT) | 가격·수량 지정, 호가창 등록 |
| 시장가 주문 (MARKET) | 수량만 지정, 즉시 체결 |
| 주문 취소 | 미체결(OPEN) 상태만 취소 가능 |
| 주문 조회 | 상태별(OPEN/PARTIAL/FILLED/CANCELLED) 필터 |

#### 체결 (Execution / Matching)
| 기능 | 설명 |
|------|------|
| 간이 매칭 엔진 | 가격 우선 → 시간 우선 매칭 |
| 부분 체결 (Partial Fill) | 잔여 수량 자동 분리 |
| 체결 이벤트 발행 | Kafka → 원장/시세/알림 서비스 연동 |

#### 잔고·손익 (Position / P&L)
| 기능 | 설명 |
|------|------|
| 보유 잔고 갱신 | 매수 체결 시 수량 증가, 평단가 재계산 |
| 실현 손익 | 매도 체결 시 (체결가 - 평단가) × 수량 |
| 평가 손익 | 현재가 기준 미실현 손익 |

#### 실시간 시세 (Market Data)
| 기능 | 설명 |
|------|------|
| 체결가 시세 | 매칭 결과 → WebSocket 브로드캐스트 |
| 호가창 조회 | REST, 상위 5호가 |

#### 거래 내역
| 기능 | 설명 |
|------|------|
| 거래(체결) 내역 | 종목별·기간별 조회, 페이징 |
| 손익 요약 | 일별·종목별 실현 손익 집계 |

### 1.2 비기능 요구사항 (Non-Functional Requirements)

```
- 처리량 (Throughput) : 지정가 주문 1,000 TPS 이상
- 지연 (Latency)      : 주문 접수~체결 이벤트 발행 P99 < 50ms
- 정합성 (Consistency): 예수금·잔고 중복 차감 없음 (멱등 처리)
- 가용성 (Availability): 서비스 단위 독립 배포, 무중단
- 관측성 (Observability): 분산 추적 traceId, 메트릭 수집
```

---

## 2. 전체 아키텍처 설계

### 2.1 레이어드 아키텍처 (ASCII 다이어그램)

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT (Browser / App)                       │
│            REST API  ────────────────────  WebSocket                │
└──────────────────────────────┬──────────────────┬───────────────────┘
                               │                  │
                    ┌──────────▼──────────┐  ┌────▼────────────────┐
                    │   API Gateway       │  │  WebSocket Gateway  │
                    │  (Spring Cloud GW)  │  │  (market-data-ws)   │
                    └──────────┬──────────┘  └────────────▲────────┘
                               │                          │ STOMP/WS
              ┌────────────────┼──────────────────┐       │
              │                │                  │       │
   ┌──────────▼──────┐ ┌───────▼───────┐ ┌───────▼───────────────┐
   │  account-service│ │ order-service │ │   market-data-service  │
   │  (계좌·입출금)   │ │  (주문 접수)  │ │   (시세·호가창)        │
   └──────────┬──────┘ └───────┬───────┘ └───────────────────────┘
              │                │                  ▲
              │         ┌──────▼──────────┐       │
              │         │ matching-engine │       │
              │         │  (매칭·체결)    │       │
              │         └──────┬──────────┘       │
              │                │                  │
              │    ┌───────────▼──────────────┐   │
              │    │        Kafka Cluster      │   │
              │    │  Topics:                  │   │
              │    │  · order.created          │   │
              │    │  · execution.completed    ├───┘
              │    │  · position.updated       │
              │    └───────────┬──────────────┘
              │                │
   ┌──────────▼────────────────▼──────┐
   │         ledger-service           │
   │  (원장: 예수금·잔고·손익 반영)    │
   └──────────────────────────────────┘
              │                │
   ┌──────────▼──────┐ ┌───────▼───────┐
   │   PostgreSQL     │ │     Redis      │
   │  (영속 데이터)   │ │  (캐시/Lock)  │
   └─────────────────┘ └───────────────┘
```

### 2.2 데이터 흐름 시퀀스

```
Client → order-service : POST /orders (지정가 매수)
order-service           : 주문 검증 파이프라인 실행
order-service           : DB 저장 (status=OPEN)
order-service           : Kafka topic[order.created] 발행
matching-engine ←       : order.created 소비
matching-engine         : 호가창에 주문 삽입
matching-engine         : 매칭 시도 (가격/시간 우선)
matching-engine         : Kafka topic[execution.completed] 발행
ledger-service ←        : execution.completed 소비
ledger-service          : @Transactional {
                            예수금 차감(매수) or 증가(매도),
                            Position 갱신 (평단가 재계산),
                            실현손익 기록
                          }
market-data-service ←   : execution.completed 소비
market-data-service     : 체결가 업데이트, WebSocket 브로드캐스트
Client ← WebSocket      : 실시간 체결가 수신
```

---

## 3. 멀티모듈 프로젝트 구조

```
mini-trading-system/
├── build.gradle.kts          (루트 빌드)
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml    (BOM 버전 관리)
│
├── shared/
│   ├── domain/               (공유 도메인 모델, 이벤트 DTO)
│   ├── common/               (공통 유틸, 예외 클래스)
│   └── security/             (JWT 필터, 인증 공통)
│
├── account-service/          (계좌·입출금 서비스)
│   ├── src/main/kotlin/
│   └── src/test/kotlin/
│
├── order-service/            (주문 접수·취소 서비스)
│   ├── src/main/kotlin/
│   └── src/test/kotlin/
│
├── matching-engine/          (매칭·체결 엔진)
│   ├── src/main/kotlin/
│   └── src/test/kotlin/
│
├── ledger-service/           (원장·잔고·손익)
│   ├── src/main/kotlin/
│   └── src/test/kotlin/
│
├── market-data-service/      (시세·WebSocket 푸시)
│   ├── src/main/kotlin/
│   └── src/test/kotlin/
│
└── docker/
    ├── docker-compose.yml
    └── init-db/
        └── 01-schema.sql
```

### 3.1 루트 build.gradle.kts 핵심

```kotlin
// build.gradle.kts (루트)
plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.spring") version "2.0.21" apply false
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "io.spring.dependency-management")

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation("org.springframework.boot:spring-boot-starter-test")
    }
}
```

---

## 4. 도메인 모델 설계

### 4.1 Order (주문)

```kotlin
// shared/domain/src/main/kotlin/com/trading/domain/order/Order.kt

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * 주문 상태 (Order Status)
 */
enum class OrderStatus {
    OPEN,       // 미체결 (호가창 대기)
    PARTIAL,    // 부분 체결
    FILLED,     // 전량 체결
    CANCELLED,  // 취소됨
    REJECTED    // 거부됨 (검증 실패)
}

/**
 * 주문 방향 (Order Side)
 */
enum class OrderSide {
    BUY,   // 매수
    SELL   // 매도
}

/**
 * 주문 유형 (Order Type)
 */
sealed class OrderType {
    /** 지정가 주문 — 가격과 수량 모두 지정 */
    data class Limit(val price: BigDecimal) : OrderType()

    /** 시장가 주문 — 수량만 지정, 현재 시장가로 즉시 체결 */
    object Market : OrderType()
}

/**
 * 주문 (Order) 도메인 객체
 */
data class Order(
    val orderId: UUID = UUID.randomUUID(),
    val accountId: UUID,
    val symbol: String,          // 종목코드 (예: "005930" 삼성전자)
    val side: OrderSide,
    val type: OrderType,
    val quantity: Long,          // 주문 수량
    val filledQuantity: Long = 0L, // 체결 수량
    val status: OrderStatus = OrderStatus.OPEN,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    /** 미체결 수량 */
    val remainingQuantity: Long get() = quantity - filledQuantity

    /** 전량 체결 여부 */
    val isFilled: Boolean get() = filledQuantity >= quantity

    /** 지정가 주문의 가격 (시장가는 null) */
    val limitPrice: BigDecimal? get() = (type as? OrderType.Limit)?.price

    /** 부분 체결 적용 */
    fun applyFill(fillQty: Long): Order {
        require(fillQty > 0) { "체결 수량은 0보다 커야 합니다." }
        require(fillQty <= remainingQuantity) { "체결 수량이 잔여 수량을 초과합니다." }
        val newFilled = filledQuantity + fillQty
        val newStatus = if (newFilled >= quantity) OrderStatus.FILLED else OrderStatus.PARTIAL
        return copy(filledQuantity = newFilled, status = newStatus, updatedAt = Instant.now())
    }
}
```

### 4.2 Account (계좌)

```kotlin
// shared/domain/src/main/kotlin/com/trading/domain/account/Account.kt

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Account(
    val accountId: UUID = UUID.randomUUID(),
    val userId: UUID,
    val accountNumber: String,           // 계좌번호 (예: "1234-5678-9012")
    val availableCash: BigDecimal,       // 가용 예수금 (출금·주문 가능 금액)
    val totalCash: BigDecimal,           // 총 예수금 (체결 대기 포함)
    val createdAt: Instant = Instant.now()
) {
    /** 주문 가능 금액 확인 */
    fun canOrder(requiredAmount: BigDecimal): Boolean =
        availableCash >= requiredAmount

    /** 예수금 차감 (매수 주문 접수 시) */
    fun reserveCash(amount: BigDecimal): Account {
        require(canOrder(amount)) { "가용 예수금 부족: 필요=${amount}, 가용=${availableCash}" }
        return copy(availableCash = availableCash - amount)
    }
}
```

### 4.3 Position (보유 잔고)

```kotlin
// shared/domain/src/main/kotlin/com/trading/domain/position/Position.kt

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class Position(
    val positionId: UUID = UUID.randomUUID(),
    val accountId: UUID,
    val symbol: String,
    val quantity: Long,                        // 보유 수량
    val averageCost: BigDecimal,               // 평균 매입 단가 (평단가)
    val totalCost: BigDecimal                  // 총 매입 비용 (평단가 × 수량)
) {
    /**
     * 매수 체결 시 평단가 재계산
     * 새 평단가 = (기존 총비용 + 신규 매입비용) / (기존 수량 + 신규 수량)
     */
    fun applyBuy(fillQty: Long, fillPrice: BigDecimal): Position {
        val newQty = quantity + fillQty
        val additionalCost = fillPrice * fillQty.toBigDecimal()
        val newTotalCost = totalCost + additionalCost
        val newAvgCost = newTotalCost.divide(newQty.toBigDecimal(), 4, RoundingMode.HALF_UP)
        return copy(quantity = newQty, averageCost = newAvgCost, totalCost = newTotalCost)
    }

    /**
     * 매도 체결 시 잔고 차감 및 실현 손익 계산
     * 실현 손익 = (체결가 - 평단가) × 체결 수량
     */
    fun applySell(fillQty: Long, fillPrice: BigDecimal): Pair<Position, BigDecimal> {
        require(fillQty <= quantity) { "매도 수량이 보유 수량을 초과합니다." }
        val realizedPnl = (fillPrice - averageCost) * fillQty.toBigDecimal()
        val newQty = quantity - fillQty
        val newTotalCost = averageCost * newQty.toBigDecimal()
        return copy(quantity = newQty, totalCost = newTotalCost) to realizedPnl
    }

    /** 평가 손익 = (현재가 - 평단가) × 보유 수량 */
    fun unrealizedPnl(currentPrice: BigDecimal): BigDecimal =
        (currentPrice - averageCost) * quantity.toBigDecimal()
}
```

### 4.4 Execution / Trade (체결)

```kotlin
// shared/domain/src/main/kotlin/com/trading/domain/execution/Execution.kt

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * 체결 결과 (Execution) — 매칭 엔진이 생성
 */
data class Execution(
    val executionId: UUID = UUID.randomUUID(),
    val buyOrderId: UUID,
    val sellOrderId: UUID,
    val symbol: String,
    val price: BigDecimal,          // 체결가
    val quantity: Long,             // 체결 수량
    val executedAt: Instant = Instant.now()
) {
    /** 총 체결 금액 */
    val totalAmount: BigDecimal get() = price * quantity.toBigDecimal()
}

/**
 * 거래 내역 (Trade) — 원장 서비스가 저장
 */
data class Trade(
    val tradeId: UUID = UUID.randomUUID(),
    val accountId: UUID,
    val orderId: UUID,
    val executionId: UUID,
    val symbol: String,
    val side: OrderSide,
    val price: BigDecimal,
    val quantity: Long,
    val amount: BigDecimal,         // 거래 금액 (price × quantity)
    val fee: BigDecimal,            // 수수료
    val realizedPnl: BigDecimal?,   // 실현 손익 (매도 시)
    val tradedAt: Instant = Instant.now()
)
```

---

## 5. DB 스키마 설계

```sql
-- docker/init-db/01-schema.sql

-- 회원
CREATE TABLE users (
    user_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 계좌
CREATE TABLE accounts (
    account_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(user_id),
    account_number  VARCHAR(20) UNIQUE NOT NULL,
    available_cash  NUMERIC(20, 4) NOT NULL DEFAULT 0,
    total_cash      NUMERIC(20, 4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 입출금 내역
CREATE TABLE cash_transactions (
    tx_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES accounts(account_id),
    tx_type         VARCHAR(10) NOT NULL,   -- DEPOSIT | WITHDRAWAL
    amount          NUMERIC(20, 4) NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'COMPLETED',
    idempotency_key VARCHAR(64) UNIQUE,     -- 멱등성 키
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 주문
CREATE TABLE orders (
    order_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES accounts(account_id),
    symbol          VARCHAR(20) NOT NULL,
    side            VARCHAR(4) NOT NULL,    -- BUY | SELL
    order_type      VARCHAR(10) NOT NULL,   -- LIMIT | MARKET
    price           NUMERIC(20, 4),         -- NULL = 시장가
    quantity        BIGINT NOT NULL,
    filled_quantity BIGINT NOT NULL DEFAULT 0,
    status          VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_account_status ON orders(account_id, status);
CREATE INDEX idx_orders_symbol_status  ON orders(symbol, status);

-- 체결 기록
CREATE TABLE executions (
    execution_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    buy_order_id    UUID NOT NULL,
    sell_order_id   UUID NOT NULL,
    symbol          VARCHAR(20) NOT NULL,
    price           NUMERIC(20, 4) NOT NULL,
    quantity        BIGINT NOT NULL,
    executed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_executions_symbol ON executions(symbol, executed_at DESC);

-- 보유 잔고 (Position)
CREATE TABLE positions (
    position_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES accounts(account_id),
    symbol          VARCHAR(20) NOT NULL,
    quantity        BIGINT NOT NULL DEFAULT 0,
    average_cost    NUMERIC(20, 4) NOT NULL,
    total_cost      NUMERIC(20, 4) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (account_id, symbol)
);

-- 거래 내역 (Trade)
CREATE TABLE trades (
    trade_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES accounts(account_id),
    order_id        UUID NOT NULL REFERENCES orders(order_id),
    execution_id    UUID NOT NULL,
    symbol          VARCHAR(20) NOT NULL,
    side            VARCHAR(4) NOT NULL,
    price           NUMERIC(20, 4) NOT NULL,
    quantity        BIGINT NOT NULL,
    amount          NUMERIC(20, 4) NOT NULL,
    fee             NUMERIC(20, 4) NOT NULL DEFAULT 0,
    realized_pnl    NUMERIC(20, 4),
    traded_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trades_account_symbol ON trades(account_id, symbol, traded_at DESC);

-- 실현 손익 집계 (일별)
CREATE TABLE daily_pnl_summary (
    summary_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES accounts(account_id),
    symbol          VARCHAR(20) NOT NULL,
    trade_date      DATE NOT NULL,
    realized_pnl    NUMERIC(20, 4) NOT NULL DEFAULT 0,
    trade_count     INT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (account_id, symbol, trade_date)
);
```

---

## 6. REST API 명세

### 6.1 계좌·입출금 API

| Method | Path | 설명 | Auth |
|--------|------|------|------|
| POST | `/api/v1/auth/register` | 회원 가입 | - |
| POST | `/api/v1/auth/login` | 로그인 (JWT 발급) | - |
| POST | `/api/v1/accounts` | 계좌 개설 | JWT |
| GET | `/api/v1/accounts/{accountId}` | 계좌 조회 | JWT |
| GET | `/api/v1/accounts/{accountId}/balance` | 잔고(예수금+보유종목) | JWT |
| POST | `/api/v1/accounts/{accountId}/deposit` | 입금 | JWT |
| POST | `/api/v1/accounts/{accountId}/withdrawal` | 출금 | JWT |
| GET | `/api/v1/accounts/{accountId}/cash-transactions` | 입출금 내역 (페이징) | JWT |

### 6.2 주문 API

| Method | Path | 설명 | Auth |
|--------|------|------|------|
| POST | `/api/v1/orders` | 주문 접수 | JWT |
| GET | `/api/v1/orders/{orderId}` | 주문 단건 조회 | JWT |
| GET | `/api/v1/orders?accountId=&status=&page=` | 주문 목록 (페이징) | JWT |
| DELETE | `/api/v1/orders/{orderId}` | 주문 취소 | JWT |

#### POST /api/v1/orders — Request Body

```json
{
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "symbol": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "price": "75000",
  "quantity": 10
}
```

#### POST /api/v1/orders — Response

```json
{
  "orderId": "7f3e4a20-...",
  "status": "OPEN",
  "symbol": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "price": "75000",
  "quantity": 10,
  "filledQuantity": 0,
  "createdAt": "2026-06-15T09:00:00Z"
}
```

### 6.3 시세·호가창 API

| Method | Path | 설명 | Auth |
|--------|------|------|------|
| GET | `/api/v1/market/{symbol}/orderbook` | 호가창 (상위 5호가) | - |
| GET | `/api/v1/market/{symbol}/last-price` | 최근 체결가 | - |
| GET | `/api/v1/market/{symbol}/executions` | 체결 내역 (최근 50건) | - |

### 6.4 손익·거래내역 API

| Method | Path | 설명 | Auth |
|--------|------|------|------|
| GET | `/api/v1/accounts/{accountId}/positions` | 보유 잔고 + 평가 손익 | JWT |
| GET | `/api/v1/accounts/{accountId}/trades` | 거래 내역 (페이징) | JWT |
| GET | `/api/v1/accounts/{accountId}/pnl/daily` | 일별 실현 손익 | JWT |

### 6.5 WebSocket Endpoint

```
ws://host/ws/market
STOMP subscribe: /topic/market/{symbol}
메시지 형식:
{
  "symbol": "005930",
  "price": "75500",
  "quantity": 5,
  "executedAt": "2026-06-15T09:01:23.456Z"
}
```

---

## 7. 핵심 컴포넌트 구현

### 7.1 주문 검증 파이프라인 (Order Validation Pipeline)

> 참고: [19. 커스텀 검증](19-custom-validation.md)

```kotlin
// order-service/src/main/kotlin/com/trading/order/validation/OrderValidationPipeline.kt

/**
 * 주문 검증 파이프라인 — Chain of Responsibility 패턴
 */
interface OrderValidator {
    fun validate(order: OrderRequest, context: ValidationContext): ValidationResult
}

data class ValidationContext(
    val account: Account,
    val currentPrice: BigDecimal?,   // 시장가 주문 시 현재가 필요
    val symbolInfo: SymbolInfo       // 종목 정보 (단주/거래단위 등)
)

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Failure(val reason: String, val code: String) : ValidationResult()
}

/**
 * 1단계: 계좌 상태 검증
 */
class AccountStatusValidator : OrderValidator {
    override fun validate(order: OrderRequest, context: ValidationContext) =
        if (context.account.status != AccountStatus.ACTIVE)
            ValidationResult.Failure("계좌가 비활성 상태입니다.", "ACCOUNT_INACTIVE")
        else ValidationResult.Success
}

/**
 * 2단계: 수량 검증 (거래 단위 준수)
 */
class QuantityValidator : OrderValidator {
    override fun validate(order: OrderRequest, context: ValidationContext): ValidationResult {
        if (order.quantity <= 0) return ValidationResult.Failure("수량은 1 이상이어야 합니다.", "INVALID_QTY")
        val unit = context.symbolInfo.tradingUnit
        if (order.quantity % unit != 0L)
            return ValidationResult.Failure("수량은 거래단위(${unit})의 배수여야 합니다.", "INVALID_QTY_UNIT")
        return ValidationResult.Success
    }
}

/**
 * 3단계: 가용 예수금 검증 (매수 주문)
 */
class BuyCashValidator : OrderValidator {
    override fun validate(order: OrderRequest, context: ValidationContext): ValidationResult {
        if (order.side != OrderSide.BUY) return ValidationResult.Success
        val requiredAmount = when (order.orderType) {
            is OrderType.Limit -> order.orderType.price * order.quantity.toBigDecimal()
            OrderType.Market   -> (context.currentPrice ?: return ValidationResult.Failure("현재가 없음", "NO_MARKET_PRICE")) * order.quantity.toBigDecimal() * BigDecimal("1.05") // 5% 버퍼
        }
        return if (context.account.availableCash >= requiredAmount) ValidationResult.Success
               else ValidationResult.Failure("가용 예수금 부족 (필요: $requiredAmount, 가용: ${context.account.availableCash})", "INSUFFICIENT_CASH")
    }
}

/**
 * 4단계: 보유 수량 검증 (매도 주문)
 */
class SellPositionValidator(private val positionRepository: PositionRepository) : OrderValidator {
    override fun validate(order: OrderRequest, context: ValidationContext): ValidationResult {
        if (order.side != OrderSide.SELL) return ValidationResult.Success
        val position = positionRepository.findByAccountIdAndSymbol(context.account.accountId, order.symbol)
            ?: return ValidationResult.Failure("보유 종목 없음", "NO_POSITION")
        return if (position.quantity >= order.quantity) ValidationResult.Success
               else ValidationResult.Failure("보유 수량 부족 (보유: ${position.quantity}, 요청: ${order.quantity})", "INSUFFICIENT_POSITION")
    }
}

/**
 * 파이프라인 실행기
 */
@Service
class OrderValidationPipeline(
    private val positionRepository: PositionRepository
) {
    private val validators: List<OrderValidator> = listOf(
        AccountStatusValidator(),
        QuantityValidator(),
        BuyCashValidator(),
        SellPositionValidator(positionRepository)
    )

    fun validate(order: OrderRequest, context: ValidationContext): ValidationResult {
        for (validator in validators) {
            val result = validator.validate(order, context)
            if (result is ValidationResult.Failure) return result
        }
        return ValidationResult.Success
    }
}
```

### 7.2 간이 매칭 엔진 (Matching Engine)

> 참고: [12. 호가창과 매칭](12-orderbook.md), [28. JVM 동시성](28-jvm-concurrency.md)

```kotlin
// matching-engine/src/main/kotlin/com/trading/matching/OrderBook.kt

import java.math.BigDecimal
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 종목별 호가창 (OrderBook)
 * - 매수 호가: 높은 가격 우선 (내림차순) — TreeMap(reverseOrder)
 * - 매도 호가: 낮은 가격 우선 (오름차순) — TreeMap
 * - 가격 동일 시 시간 우선 (삽입 순서 유지 — ArrayDeque)
 */
class OrderBook(val symbol: String) {
    private val lock = ReentrantLock()

    // 매수 호가 (Bid): 높은 가격 우선
    private val bids = TreeMap<BigDecimal, ArrayDeque<Order>>(reverseOrder())
    // 매도 호가 (Ask): 낮은 가격 우선
    private val asks = TreeMap<BigDecimal, ArrayDeque<Order>>()

    /**
     * 주문 추가 후 즉시 매칭 시도
     * @return 체결된 Execution 목록
     */
    fun addAndMatch(incomingOrder: Order): List<Execution> = lock.withLock {
        val executions = mutableListOf<Execution>()
        var remaining = incomingOrder

        when (incomingOrder.side) {
            OrderSide.BUY  -> matchBuy(remaining, executions)
            OrderSide.SELL -> matchSell(remaining, executions)
        }

        // 미체결 잔량이 있으면 호가창에 등록 (시장가 잔량은 취소)
        if (!remaining.isFilled && incomingOrder.type is OrderType.Limit) {
            insertIntoBook(remaining)
        }

        executions
    }

    private fun matchBuy(order: Order, executions: MutableList<Execution>): Order {
        var remaining = order
        val limitPrice = (order.type as? OrderType.Limit)?.price

        while (remaining.remainingQuantity > 0 && asks.isNotEmpty()) {
            val bestAskPrice = asks.firstKey()

            // 지정가: 매도 최저가 <= 매수 지정가 일 때만 체결
            if (limitPrice != null && bestAskPrice > limitPrice) break

            val askQueue = asks[bestAskPrice]!!
            val sellOrder = askQueue.first()
            val fillQty = minOf(remaining.remainingQuantity, sellOrder.remainingQuantity)

            executions.add(Execution(
                buyOrderId  = remaining.orderId,
                sellOrderId = sellOrder.orderId,
                symbol      = symbol,
                price       = bestAskPrice,   // 매도 호가 기준 체결
                quantity    = fillQty
            ))

            // 매도 주문 체결 반영
            val updatedSell = sellOrder.applyFill(fillQty)
            if (updatedSell.isFilled) {
                askQueue.removeFirst()
                if (askQueue.isEmpty()) asks.remove(bestAskPrice)
            } else {
                askQueue[0] = updatedSell
            }

            remaining = remaining.applyFill(fillQty)
        }
        return remaining
    }

    private fun matchSell(order: Order, executions: MutableList<Execution>): Order {
        var remaining = order
        val limitPrice = (order.type as? OrderType.Limit)?.price

        while (remaining.remainingQuantity > 0 && bids.isNotEmpty()) {
            val bestBidPrice = bids.firstKey()

            // 지정가: 매수 최고가 >= 매도 지정가 일 때만 체결
            if (limitPrice != null && bestBidPrice < limitPrice) break

            val bidQueue = bids[bestBidPrice]!!
            val buyOrder = bidQueue.first()
            val fillQty = minOf(remaining.remainingQuantity, buyOrder.remainingQuantity)

            executions.add(Execution(
                buyOrderId  = buyOrder.orderId,
                sellOrderId = remaining.orderId,
                symbol      = symbol,
                price       = bestBidPrice,
                quantity    = fillQty
            ))

            val updatedBuy = buyOrder.applyFill(fillQty)
            if (updatedBuy.isFilled) {
                bidQueue.removeFirst()
                if (bidQueue.isEmpty()) bids.remove(bestBidPrice)
            } else {
                bidQueue[0] = updatedBuy
            }

            remaining = remaining.applyFill(fillQty)
        }
        return remaining
    }

    private fun insertIntoBook(order: Order) {
        val price = (order.type as OrderType.Limit).price
        when (order.side) {
            OrderSide.BUY  -> bids.getOrPut(price) { ArrayDeque() }.addLast(order)
            OrderSide.SELL -> asks.getOrPut(price) { ArrayDeque() }.addLast(order)
        }
    }

    /** 호가창 스냅샷 (상위 N호가) */
    fun snapshot(depth: Int = 5): OrderBookSnapshot {
        return lock.withLock {
            OrderBookSnapshot(
                symbol  = symbol,
                bids    = bids.entries.take(depth).map { (p, q) -> PriceLevel(p, q.sumOf { it.remainingQuantity }) },
                asks    = asks.entries.take(depth).map { (p, q) -> PriceLevel(p, q.sumOf { it.remainingQuantity }) }
            )
        }
    }
}

/**
 * 전체 종목 호가창 레지스트리
 */
@Component
class OrderBookRegistry {
    private val books = ConcurrentHashMap<String, OrderBook>()

    fun getOrCreate(symbol: String): OrderBook =
        books.getOrPut(symbol) { OrderBook(symbol) }

    fun get(symbol: String): OrderBook? = books[symbol]
}
```

### 7.3 원장 서비스 — @Transactional 적용

> 참고: [22. 트랜잭션과 정합성](22-transaction.md)

```kotlin
// ledger-service/src/main/kotlin/com/trading/ledger/LedgerService.kt

@Service
@Transactional
class LedgerService(
    private val accountRepository: AccountRepository,
    private val positionRepository: PositionRepository,
    private val tradeRepository: TradeRepository,
    private val dailyPnlRepository: DailyPnlRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 체결 완료 이벤트 처리 — 멱등성 보장
     * @param executionEvent Kafka에서 수신한 체결 이벤트
     */
    fun processExecution(executionEvent: ExecutionCompletedEvent) {
        // 멱등성 체크: 이미 처리한 체결이면 스킵
        if (tradeRepository.existsByExecutionId(executionEvent.executionId)) {
            log.info("이미 처리된 체결 — executionId=${executionEvent.executionId}")
            return
        }

        val fee = calculateFee(executionEvent.price, executionEvent.quantity)

        // 매수 계좌 처리
        processBuyerLedger(executionEvent, fee)

        // 매도 계좌 처리
        processSellerLedger(executionEvent, fee)
    }

    private fun processBuyerLedger(event: ExecutionCompletedEvent, fee: BigDecimal) {
        val account = accountRepository.findByIdWithLock(event.buyerAccountId)
            ?: throw IllegalStateException("계좌 없음: ${event.buyerAccountId}")

        val totalCost = event.price * event.quantity.toBigDecimal() + fee

        // 예수금 차감 (매수 시 체결 확정)
        accountRepository.save(account.copy(
            totalCash     = account.totalCash - totalCost,
            availableCash = account.availableCash  // 주문 접수 시 이미 차감됨
        ))

        // 포지션 업데이트
        val position = positionRepository
            .findByAccountIdAndSymbol(event.buyerAccountId, event.symbol)
            ?: Position(accountId = event.buyerAccountId, symbol = event.symbol,
                        quantity = 0L, averageCost = BigDecimal.ZERO, totalCost = BigDecimal.ZERO)

        positionRepository.save(position.applyBuy(event.quantity, event.price))

        // 거래 내역 저장
        tradeRepository.save(Trade(
            accountId   = event.buyerAccountId,
            orderId     = event.buyOrderId,
            executionId = event.executionId,
            symbol      = event.symbol,
            side        = OrderSide.BUY,
            price       = event.price,
            quantity    = event.quantity,
            amount      = event.price * event.quantity.toBigDecimal(),
            fee         = fee,
            realizedPnl = null
        ))
    }

    private fun processSellerLedger(event: ExecutionCompletedEvent, fee: BigDecimal) {
        val account = accountRepository.findByIdWithLock(event.sellerAccountId)
            ?: throw IllegalStateException("계좌 없음: ${event.sellerAccountId}")

        val position = positionRepository
            .findByAccountIdAndSymbol(event.sellerAccountId, event.symbol)
            ?: throw IllegalStateException("포지션 없음: ${event.sellerAccountId} / ${event.symbol}")

        val (updatedPosition, realizedPnl) = position.applySell(event.quantity, event.price)
        positionRepository.save(updatedPosition)

        val proceeds = event.price * event.quantity.toBigDecimal() - fee
        accountRepository.save(account.copy(
            totalCash     = account.totalCash + proceeds,
            availableCash = account.availableCash + proceeds
        ))

        tradeRepository.save(Trade(
            accountId   = event.sellerAccountId,
            orderId     = event.sellOrderId,
            executionId = event.executionId,
            symbol      = event.symbol,
            side        = OrderSide.SELL,
            price       = event.price,
            quantity    = event.quantity,
            amount      = event.price * event.quantity.toBigDecimal(),
            fee         = fee,
            realizedPnl = realizedPnl
        ))

        // 일별 손익 업데이트 (UPSERT)
        dailyPnlRepository.upsertPnl(event.sellerAccountId, event.symbol, realizedPnl)
    }

    private fun calculateFee(price: BigDecimal, quantity: Long): BigDecimal {
        // 수수료율 0.015% (단순화)
        val feeRate = BigDecimal("0.00015")
        return (price * quantity.toBigDecimal() * feeRate)
            .setScale(0, java.math.RoundingMode.UP)
    }
}
```

### 7.4 이벤트 DTO (Kafka 메시지)

```kotlin
// shared/domain/src/main/kotlin/com/trading/domain/event/TradingEvents.kt

/**
 * 주문 생성 이벤트
 */
data class OrderCreatedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val orderId: UUID,
    val accountId: UUID,
    val symbol: String,
    val side: OrderSide,
    val orderType: String,   // "LIMIT" | "MARKET"
    val price: BigDecimal?,
    val quantity: Long,
    val occurredAt: Instant = Instant.now()
)

/**
 * 체결 완료 이벤트 (매칭 엔진 → 원장/시세 서비스)
 */
data class ExecutionCompletedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val executionId: UUID,
    val buyOrderId: UUID,
    val sellOrderId: UUID,
    val buyerAccountId: UUID,
    val sellerAccountId: UUID,
    val symbol: String,
    val price: BigDecimal,
    val quantity: Long,
    val occurredAt: Instant = Instant.now()
)
```

---

## 8. 동시성·정합성 전략

> 참고: [28. JVM 동시성](28-jvm-concurrency.md), [22. 트랜잭션과 정합성](22-transaction.md)

### 8.1 동시성 문제 시나리오와 해결책

| 시나리오 | 문제 | 해결 방법 |
|----------|------|-----------|
| 동일 계좌 동시 주문 | 예수금 이중 차감 | DB SELECT FOR UPDATE (비관적 락) |
| 매칭 엔진 동시 접근 | 호가창 데이터 레이스 | 종목별 ReentrantLock |
| 체결 이벤트 중복 수신 | 원장 중복 반영 | 멱등성 키 (executionId) |
| 포지션 동시 업데이트 | Lost Update | Optimistic Lock (@Version) |

### 8.2 비관적 락 예제 (예수금 차감)

```kotlin
// account-service/src/main/kotlin/com/trading/account/AccountRepository.kt

interface AccountRepository : JpaRepository<AccountEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.accountId = :accountId")
    fun findByIdWithLock(accountId: UUID): AccountEntity?
}
```

### 8.3 낙관적 락 예제 (포지션 업데이트)

```kotlin
@Entity
@Table(name = "positions")
data class PositionEntity(
    @Id val positionId: UUID,
    val accountId: UUID,
    val symbol: String,
    var quantity: Long,
    var averageCost: BigDecimal,
    var totalCost: BigDecimal,
    @Version var version: Long = 0   // 낙관적 락
)
```

### 8.4 멱등성 처리 (체결 중복 방지)

```kotlin
@Transactional
fun processExecution(event: ExecutionCompletedEvent) {
    // DB UNIQUE 제약 + 체크로 이중 방어
    if (tradeRepository.existsByExecutionId(event.executionId)) {
        log.warn("중복 체결 이벤트 무시 — executionId=${event.executionId}")
        return
    }
    // ... 이하 정상 처리
}
```

---

## 9. 이벤트 기반 아키텍처

> 참고: [30. Kafka 기초](30-kafka.md), [31. 이벤트 소싱](31-event-sourcing.md)

### 9.1 Kafka 토픽 설계

```
Topic 이름                  파티션   복제 인수   설명
─────────────────────────────────────────────────────────────────────
trading.order.created         4        3        주문 접수 이벤트 (symbol 해시 파티셔닝)
trading.execution.completed   4        3        체결 완료 이벤트
trading.position.updated      4        3        포지션 변경 이벤트
trading.notification          2        3        알림 이벤트 (push/email)
```

### 9.2 Producer — 주문 서비스

```kotlin
@Service
class OrderEventProducer(private val kafkaTemplate: KafkaTemplate<String, Any>) {
    companion object {
        const val TOPIC = "trading.order.created"
    }

    fun publish(event: OrderCreatedEvent) {
        kafkaTemplate.send(
            TOPIC,
            event.symbol,    // 파티션 키 — 같은 종목은 같은 파티션으로
            event
        ).whenComplete { result, ex ->
            if (ex != null) log.error("주문 이벤트 발행 실패", ex)
            else log.info("주문 이벤트 발행 성공 — offset=${result.recordMetadata.offset()}")
        }
    }
}
```

### 9.3 Consumer — 매칭 엔진

```kotlin
@Component
class OrderCreatedConsumer(
    private val orderBookRegistry: OrderBookRegistry,
    private val executionEventProducer: ExecutionEventProducer
) {
    @KafkaListener(
        topics = ["trading.order.created"],
        groupId = "matching-engine",
        concurrency = "4"   // 파티션 수만큼
    )
    fun consume(event: OrderCreatedEvent) {
        val orderBook = orderBookRegistry.getOrCreate(event.symbol)
        val order = event.toDomain()
        val executions = orderBook.addAndMatch(order)

        executions.forEach { execution ->
            executionEventProducer.publish(execution.toEvent())
        }
    }
}
```

---

## 10. 실시간 시세 푸시

> 참고: [35. WebSocket과 실시간](35-websocket.md)

### 10.1 WebSocket STOMP 설정

```kotlin
// market-data-service/src/main/kotlin/com/trading/market/config/WebSocketConfig.kt

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/market")
            .setAllowedOriginPatterns("*")
            .withSockJS()
    }
}
```

### 10.2 시세 이벤트 브로드캐스트

```kotlin
// market-data-service/src/main/kotlin/com/trading/market/service/MarketDataService.kt

@Service
class MarketDataService(
    private val messagingTemplate: SimpMessagingTemplate,
    private val lastPriceRepository: LastPriceRepository
) {
    @KafkaListener(topics = ["trading.execution.completed"], groupId = "market-data-service")
    fun onExecution(event: ExecutionCompletedEvent) {
        // 최근 체결가 갱신 (Redis)
        lastPriceRepository.update(event.symbol, event.price)

        // WebSocket 브로드캐스트
        val message = MarketTickMessage(
            symbol      = event.symbol,
            price       = event.price,
            quantity    = event.quantity,
            executedAt  = event.occurredAt
        )
        messagingTemplate.convertAndSend("/topic/market/${event.symbol}", message)
    }
}
```

---

## 11. 테스트 전략

> 참고: [23. 테스트](23-testing.md)

### 11.1 테스트 피라미드 (Test Pyramid)

```
        ┌─────────────┐
        │   E2E Test  │  ← 시나리오 (소수, 전체 흐름 검증)
        │   (몇 개)   │
        └──────┬──────┘
        ┌──────▼──────────┐
        │  Integration    │  ← Kafka, DB, WebSocket 연동 검증
        │  Test (중간)    │
        └──────┬──────────┘
    ┌──────────▼──────────────┐
    │   Unit Test (다수)      │  ← 도메인 로직, 매칭 엔진, 평단가 계산
    └────────────────────────┘
```

### 11.2 매칭 엔진 단위 테스트

```kotlin
class OrderBookTest {

    private lateinit var orderBook: OrderBook

    @BeforeEach fun setUp() { orderBook = OrderBook("005930") }

    @Test
    fun `지정가 매수-매도 가격 교차 시 체결된다`() {
        // given
        val sell = Order(accountId = UUID.randomUUID(), symbol = "005930",
                         side = OrderSide.SELL, type = OrderType.Limit(BigDecimal("75000")), quantity = 10L)
        orderBook.addAndMatch(sell)

        val buy = Order(accountId = UUID.randomUUID(), symbol = "005930",
                        side = OrderSide.BUY, type = OrderType.Limit(BigDecimal("75000")), quantity = 10L)

        // when
        val executions = orderBook.addAndMatch(buy)

        // then
        assertThat(executions).hasSize(1)
        assertThat(executions[0].quantity).isEqualTo(10L)
        assertThat(executions[0].price).isEqualTo(BigDecimal("75000"))
    }

    @Test
    fun `부분 체결 후 잔량이 호가창에 남는다`() {
        val sell = Order(accountId = UUID.randomUUID(), symbol = "005930",
                         side = OrderSide.SELL, type = OrderType.Limit(BigDecimal("75000")), quantity = 5L)
        orderBook.addAndMatch(sell)

        val buy = Order(accountId = UUID.randomUUID(), symbol = "005930",
                        side = OrderSide.BUY, type = OrderType.Limit(BigDecimal("75000")), quantity = 10L)
        val executions = orderBook.addAndMatch(buy)

        assertThat(executions).hasSize(1)
        assertThat(executions[0].quantity).isEqualTo(5L)
        val snapshot = orderBook.snapshot()
        assertThat(snapshot.bids.first().quantity).isEqualTo(5L)  // 잔량 5 남음
    }
}
```

### 11.3 원장 서비스 통합 테스트

```kotlin
@SpringBootTest
@Transactional
class LedgerServiceIntegrationTest {

    @Autowired lateinit var ledgerService: LedgerService
    @Autowired lateinit var positionRepository: PositionRepository

    @Test
    fun `매수 체결 시 평단가가 정확히 계산된다`() {
        // given: 1차 매수 100주 @ 70,000
        val firstBuy = buildExecutionEvent(price = BigDecimal("70000"), quantity = 100L, side = OrderSide.BUY)
        ledgerService.processExecution(firstBuy)

        // when: 2차 매수 50주 @ 73,000
        val secondBuy = buildExecutionEvent(price = BigDecimal("73000"), quantity = 50L, side = OrderSide.BUY)
        ledgerService.processExecution(secondBuy)

        // then: 평단가 = (7,000,000 + 3,650,000) / 150 = 71,000
        val position = positionRepository.findByAccountIdAndSymbol(testAccountId, "005930")!!
        assertThat(position.quantity).isEqualTo(150L)
        assertThat(position.averageCost).isEqualByComparingTo(BigDecimal("71000.0000"))
    }
}
```

### 11.4 Kafka 통합 테스트 (Testcontainers)

```kotlin
@Testcontainers
@SpringBootTest
class MatchingEngineKafkaTest {

    companion object {
        @Container
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))

        @DynamicPropertySource
        @JvmStatic
        fun kafkaProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
    }

    @Test
    fun `주문 생성 이벤트 소비 후 매칭 결과 이벤트가 발행된다`() {
        // ... 테스트 구현
    }
}
```

---

## 12. 관측성 및 배포

### 12.1 분산 추적 (Distributed Tracing)

```kotlin
// 모든 서비스: spring-cloud-sleuth + zipkin 의존성 추가
// application.yml

management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### 12.2 구조화 로깅 (Structured Logging)

```kotlin
// 모든 서비스 공통 로깅 패턴
fun processOrder(orderId: UUID) {
    val log = LoggerFactory.getLogger(javaClass)
    log.info("주문 처리 시작",
        kv("orderId", orderId),
        kv("symbol", symbol),
        kv("traceId", MDC.get("traceId"))
    )
}
```

### 12.3 docker-compose.yml (로컬 개발 환경)

```yaml
# docker/docker-compose.yml

version: '3.8'
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: trading
      POSTGRES_USER: trading
      POSTGRES_PASSWORD: trading
    ports: ["5432:5432"]
    volumes:
      - ./init-db:/docker-entrypoint-initdb.d

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    depends_on: [zookeeper]
    ports: ["9092:9092"]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'

  zipkin:
    image: openzipkin/zipkin:3
    ports: ["9411:9411"]

  prometheus:
    image: prom/prometheus:v2.51.0
    ports: ["9090:9090"]
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:10.4.0
    ports: ["3000:3000"]
    depends_on: [prometheus]
```

---

## 13. 6주 구현 로드맵

### 1주차: 기반 환경 + 계좌 서비스

```
목표: 로컬 환경 구성, 회원 인증, 계좌·입출금 API

[ ] 멀티모듈 Gradle 프로젝트 초기화
[ ] Docker Compose (PostgreSQL + Redis + Kafka) 실행
[ ] shared/domain 모듈 — 도메인 모델 코드 작성
[ ] account-service: 회원 가입/로그인 (JWT)
[ ] account-service: 계좌 개설 API
[ ] account-service: 입금/출금 API (멱등성 키 포함)
[ ] 단위 테스트 작성

참고: [06-kotlin-spring.md], [17-auth-jwt.md], [22-transaction.md]
```

### 2주차: 주문 서비스

```
목표: 주문 접수, 검증 파이프라인, 주문 취소

[ ] order-service: 주문 접수 API (지정가·시장가)
[ ] OrderValidationPipeline 구현 (계좌·수량·예수금·보유잔고 검증)
[ ] 주문 접수 시 예수금 선차감 (비관적 락)
[ ] Kafka 주문 이벤트 발행 (order.created)
[ ] 주문 취소 API (OPEN 상태만 취소, 예수금 반환)
[ ] 통합 테스트: 주문 접수 → Kafka 이벤트 발행 검증

참고: [19-custom-validation.md], [28-jvm-concurrency.md], [30-kafka.md]
```

### 3주차: 매칭 엔진

```
목표: 호가창 + 매칭 로직, 체결 이벤트 발행

[ ] matching-engine: OrderBook 구현 (TreeMap + ReentrantLock)
[ ] OrderBookRegistry — 종목별 호가창 관리
[ ] order.created 소비 → 매칭 시도
[ ] 체결 이벤트 발행 (execution.completed)
[ ] 부분 체결 처리
[ ] 매칭 엔진 단위 테스트 (경계 케이스 포함)
[ ] Testcontainers Kafka 통합 테스트

참고: [12-orderbook.md], [28-jvm-concurrency.md], [31-event-sourcing.md]
```

### 4주차: 원장 서비스 + 손익 계산

```
목표: 체결 결과 원장 반영, 평단가·손익 계산

[ ] ledger-service: execution.completed 소비
[ ] 매수 체결: 포지션 업데이트 + 평단가 재계산
[ ] 매도 체결: 포지션 차감 + 실현 손익 계산
[ ] Trade 저장 + daily_pnl_summary UPSERT
[ ] 멱등성 처리 (executionId 중복 체크)
[ ] 보유 잔고 + 평가 손익 조회 API
[ ] 원장 통합 테스트

참고: [22-transaction.md], [28-jvm-concurrency.md]
```

### 5주차: 실시간 시세 + 호가창 조회

```
목표: WebSocket 브로드캐스트, 시세 API

[ ] market-data-service: STOMP WebSocket 설정
[ ] execution.completed 소비 → Redis 최근 체결가 저장
[ ] WebSocket: /topic/market/{symbol} 브로드캐스트
[ ] 호가창 조회 REST API (매칭 엔진 스냅샷 호출)
[ ] 최근 체결 내역 조회 API
[ ] 프론트엔드 Mock 클라이언트로 실시간 검증

참고: [35-websocket.md], [32-redis-cache.md]
```

### 6주차: 관측성 + 마무리 + 최적화

```
목표: 통합 테스트, 모니터링, 성능 검증

[ ] Zipkin 분산 추적 연동 + traceId MDC 전파
[ ] Prometheus + Grafana 메트릭 대시보드 구성
[ ] 구조화 로깅 (JSON 형식)
[ ] E2E 시나리오 테스트: 입금→주문→체결→잔고확인
[ ] 성능 테스트 (k6): 주문 1,000 TPS 목표
[ ] 취약점 점검: SQL 인젝션, JWT 만료, 인가 검사

참고: [40-monitoring.md], [42-security.md], [23-testing.md]
```

---

## 14. 확장 과제

### 14.1 신용 거래 (Margin Trading)

- 담보 평가 비율(LTV) 계산, 반대 매매 자동 트리거
- 참고: [36-risk-management.md]

### 14.2 파생 상품 (Derivatives)

- 선물(Futures) 계약 구조, 마진 콜(Margin Call)
- 옵션(Option) 프리미엄, 델타 헤징

### 14.3 차트 데이터 (OHLCV)

- 체결 이벤트 집계 → 1분/5분/일봉 OHLCV 생성
- TimescaleDB 또는 InfluxDB 활용

### 14.4 알림 서비스 (Notification)

- 체결 알림 → Push/Email/SMS
- notification.topic 소비 후 멀티채널 발송

### 14.5 고빈도 트레이딩 최적화 (HFT)

- Lock-free Queue (LMAX Disruptor) 적용
- 매칭 엔진 GC-less 설계 (Object Pooling)

---

> **마무리**: 이 캡스톤 프로젝트는 LEVEL 0~8에서 학습한 모든 기술의 종합판입니다.  
> 막히는 부분마다 해당 챕터로 돌아가 개념을 재확인하고, 작은 단위부터 구현하여 동작을 확인하면서 완성해 나가세요.

---

이전: [45. 보안 심화](45-security-advanced.md) · [전체 커리큘럼](../CURRICULUM.md)
