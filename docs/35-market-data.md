# 35. 시세·시장 데이터

## 개요

증권사 시스템에서 시세(Market Data)는 모든 서비스의 혈액이다. 주문 가능 여부 판단, 손익 계산, 리스크 평가, 차트 렌더링, 알림 발송까지 시세 데이터에 의존하지 않는 기능이 없다. 동시에 시세 데이터는 **초당 수만 건**이라는 엄청난 처리량, **밀리초 단위**의 지연 요구, **정합성**이라는 세 가지 도전을 개발자에게 던진다. 이 문서는 시세 데이터의 종류부터 실시간 처리, 캐싱, 기술적 지표 계산까지 백엔드 개발자가 알아야 할 전체 그림을 다룬다.

> **주의**: 이 문서의 수치·방식은 예시이며, 실제 구현 시 거래소(KRX) API 명세 및 사내 정책을 반드시 확인해야 한다.

---

## 1. 시세 데이터 종류

### 1.1 데이터 분류 체계

```
시세 데이터
├── 실시간(Real-time)
│   ├── 현재가(Current Price)         ← 가장 최근 체결가
│   ├── 체결가(Trade Price)           ← 개별 체결 건
│   ├── 호가(Quote / Order Book)      ← 매수/매도 10단 호가
│   ├── 예상체결가(Expected Price)     ← 동시호가 중 예측값
│   └── 거래량(Volume)                ← 누적/건별
├── 분류별 집계
│   ├── OHLCV 봉 데이터(Candle)
│   ├── 누적 거래대금(Turnover)
│   └── 종목 정보(Issue Info)
└── 지수(Index)
    ├── KOSPI / KOSDAQ
    └── 섹터지수, KRX300 등
```

### 1.2 현재가 vs 체결가

| 항목 | 현재가(Current Price) | 체결가(Trade Price) |
|------|----------------------|---------------------|
| 의미 | 가장 최근 체결된 가격 | 개별 체결 이벤트의 가격 |
| 빈도 | 체결 발생 시 갱신 | 체결마다 1건씩 발행 |
| 용도 | 잔고 평가, 화면 표시 | 체결 타임라인, 차트 원본 |
| 데이터양 | 1건(최신) | 초당 수천~수만 건 가능 |

### 1.3 호가 데이터 (Quote) 구조

매수/매도 각 10단계 호가를 포함한다. 상세 구조는 [12. 호가창](12-orderbook.md)을 참조.

```
호가 메시지 예시 (KRX EXTURE+ 포맷 기반)
├── 종목코드(StockCode): "005930"
├── 호가시각(QuoteTime): "093015123"
├── 매도호가[1~10]: 가격, 잔량, 건수
├── 매수호가[1~10]: 가격, 잔량, 건수
├── 예상체결가(ExpectedPrice)
├── 예상체결량(ExpectedVolume)
└── 총매도/매수잔량
```

### 1.4 예상체결가 (Expected / Indicative Price)

장 시작 전 동시호가(Pre-Market Auction) 중에는 아직 체결이 발생하지 않는다. 거래소는 현재까지 접수된 매수/매도 주문을 기반으로 **이론적으로 가장 많이 체결될 가격**을 예상체결가로 제공한다. 장 중에도 단일가 매매(Batch Auction) 종목에 적용된다.

---

## 2. 실시간 데이터 배포 구조

### 2.1 거래소 → 증권사 → 고객 경로

```
┌─────────────┐         ┌──────────────────────────┐         ┌──────────────┐
│  KRX 거래소  │──UDP──→ │        증권사             │──Push──→│  고객 단말  │
│  (EXTURE+)   │Multicast│  ┌────────────────────┐  │WebSocket│  (앱/웹/HTS) │
└─────────────┘         │  │  시세 수신 시스템   │  │         └──────────────┘
                         │  │  (Market Data Feed) │  │
                         │  └────────────────────┘  │
                         │           │               │
                         │  ┌────────▼───────────┐  │
                         │  │  시세 처리/배포 서버 │  │
                         │  │  (Distribution)     │  │
                         │  └────────────────────┘  │
                         └──────────────────────────┘
```

### 2.2 KRX EXTURE+ 시세 수신

한국거래소(KRX)는 **EXTURE+(익스처플러스)** 시스템을 통해 멀티캐스트(Multicast) UDP로 시세를 배포한다.

| 항목 | 내용 |
|------|------|
| 프로토콜 | UDP Multicast |
| 채널 수 | 종목군별 다수 채널 분산 |
| 시퀀스 번호 | 패킷별 순번으로 유실 감지 |
| 리커버리 | TCP Recovery Session으로 유실 패킷 재요청 |
| 포맷 | 이진(Binary) 고정 길이 포맷 |

**개발 포인트**: UDP 패킷은 순서 보장이 없다. 시퀀스 번호를 추적해 갭(Gap) 감지 후 Recovery Session으로 재요청하는 로직이 필수다.

### 2.3 증권사 내부 배포 아키텍처

```
[KRX Multicast UDP]
        │
        ▼
[Feed Handler Server]  ← 물리적으로 거래소 코로케이션(Co-location) 또는 전용선
  - UDP 수신
  - 패킷 디코딩 (Binary → 내부 포맷)
  - 시퀀스 갭 감지 / 재요청
        │
        ▼
[Message Bus]          ← Kafka / ZeroMQ / 자체 UDP 브로드캐스트
        │
   ┌────┴────┐
   ▼         ▼
[시세 서버]  [리스크 엔진]  [주문 시스템]  [차트 서버]
   │
   ▼
[클라이언트 배포 서버]
  - WebSocket / SSE
  - 구독자별 필터링
  - Throttling / Conflation
   │
   ▼
[고객 앱/웹/HTS]
```

---

## 3. 시세 폭증 처리

### 3.1 트래픽 특성

장 시작(09:00)과 마감(15:30) 전후, 이슈 종목 급등락 시 시세 메시지가 폭발적으로 증가한다.

```
평상시: 초당 5,000~10,000 건
이슈 시: 초당 50,000 건 이상
동시 구독 고객: 수십만 명
```

### 3.2 컨플레이션 (Conflation)

같은 종목의 여러 업데이트를 하나로 합쳐 최신값만 전송하는 기법이다. 1초에 수십 번 체결되는 종목의 경우, 고객 화면에 모든 체결을 보낼 필요가 없다.

```
원본 이벤트 스트림 (삼성전자 100ms 동안):
  T+0ms:  체결가 78,000
  T+10ms: 체결가 78,100
  T+40ms: 체결가 77,900
  T+80ms: 체결가 78,200

Conflation 후 100ms 주기로 발송:
  최신값: 78,200 (T+80ms 기준)
```

### 3.3 스로틀링 (Throttling)

구독자별로 초당 최대 전송 건수를 제한한다. 화면 갱신 주기(보통 초당 10~30회)를 초과하는 업데이트는 의미가 없다.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds

// 종목별 시세 스트림에 컨플레이션 적용
fun Flow<MarketPrice>.conflateByStock(): Flow<MarketPrice> =
    conflate()  // 소비자가 느리면 최신값만 유지

// 주기적 샘플링 (throttling) — 100ms마다 최신값 emit
fun Flow<MarketPrice>.throttleLatest(periodMs: Long = 100L): Flow<MarketPrice> =
    sample(periodMs.milliseconds)
```

### 3.4 구독 필터링

고객이 관심 종목만 구독하도록 서버 사이드에서 필터링해야 한다. 모든 종목 시세를 모든 고객에게 보내는 것은 불가능하다.

```
[WebSocket 서버]
  - 고객 연결 시: 관심 종목 목록 수신
  - 종목 구독/해제 메시지 처리
  - 내부 Pub/Sub: 종목코드를 토픽(Topic)으로 사용
  - 시세 업데이트 → 해당 종목 구독 고객에게만 전송
```

---

## 4. 호가창 데이터 연계

호가창(Order Book) 상세 구조와 업데이트 처리는 [12. 호가창](12-orderbook.md)에서 다룬다. 여기서는 시세 파이프라인 관점의 처리를 요약한다.

```
[KRX 호가 메시지 수신]
        │
        ▼
[호가 스냅샷 저장]  ← 최신 전체 호가 (Redis Hash)
        │
        ▼
[호가 변경분(Delta) 계산]  ← 이전 호가와 비교
        │
        ▼
[클라이언트 전송]
  - 최초 연결: 스냅샷 전체 전송
  - 이후: Delta만 전송 (트래픽 절감)
```

---

## 5. 차트 데이터 (OHLCV)

### 5.1 OHLCV 구조

| 항목 | 영문 | 설명 |
|------|------|------|
| O | Open | 시가 (해당 기간 첫 체결가) |
| H | High | 고가 (해당 기간 최고 체결가) |
| L | Low | 저가 (해당 기간 최저 체결가) |
| C | Close | 종가 (해당 기간 마지막 체결가) |
| V | Volume | 거래량 (해당 기간 총 체결 수량) |

### 5.2 봉(Candle) 단위

| 단위 | 설명 | 기간 |
|------|------|------|
| 틱(Tick) | 체결 1건 | 건별 |
| 분봉 | 1·3·5·10·15·30·60분 | 당일~수개월 |
| 일봉 | 1거래일 | 수년 |
| 주봉 | 1주 (월~금) | 수년 |
| 월봉 | 1달 | 10년+ |

### 5.3 실시간 분봉 생성

```kotlin
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class Trade(
    val stockCode: String,
    val price: BigDecimal,
    val volume: Long,
    val tradeTime: LocalDateTime
)

data class OhlcvCandle(
    val stockCode: String,
    val periodStart: LocalDateTime,
    val open: BigDecimal,
    var high: BigDecimal,
    var low: BigDecimal,
    var close: BigDecimal,
    var volume: Long,
    var tradeCount: Int = 0
)

class CandleAggregator(private val intervalMinutes: Long = 1L) {

    private val candleMap = mutableMapOf<String, OhlcvCandle>()  // key: stockCode:periodKey

    fun onTrade(trade: Trade): OhlcvCandle {
        val periodStart = trade.tradeTime.truncatedTo(ChronoUnit.MINUTES)
            .let { base ->
                // intervalMinutes 단위로 내림
                val minuteOfDay = base.hour * 60 + base.minute
                val aligned = (minuteOfDay / intervalMinutes) * intervalMinutes
                base.withHour((aligned / 60).toInt()).withMinute((aligned % 60).toInt())
            }

        val key = "${trade.stockCode}:$periodStart"

        val candle = candleMap.getOrPut(key) {
            OhlcvCandle(
                stockCode = trade.stockCode,
                periodStart = periodStart,
                open = trade.price,
                high = trade.price,
                low = trade.price,
                close = trade.price,
                volume = 0L
            )
        }

        // 고가/저가 업데이트
        if (trade.price > candle.high) candle.high = trade.price
        if (trade.price < candle.low)  candle.low = trade.price
        candle.close = trade.price
        candle.volume += trade.volume
        candle.tradeCount++

        return candle
    }
}
```

---

## 6. 기술적 지표 계산

### 6.1 이동평균 (Moving Average, MA)

```kotlin
import java.math.BigDecimal
import java.math.RoundingMode

object TechnicalIndicators {

    private val SCALE = 4
    private val RM = RoundingMode.HALF_UP

    /**
     * 단순이동평균 (SMA, Simple Moving Average)
     * @param prices 종가 리스트 (오래된 것부터)
     * @param period MA 기간 (예: 5, 20, 60, 120)
     */
    fun sma(prices: List<BigDecimal>, period: Int): List<BigDecimal> {
        require(period > 0) { "period must be positive" }
        return (period..prices.size).map { end ->
            val window = prices.subList(end - period, end)
            window.fold(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal(period), SCALE, RM)
        }
    }

    /**
     * 지수이동평균 (EMA, Exponential Moving Average)
     * 최근 데이터에 더 높은 가중치
     */
    fun ema(prices: List<BigDecimal>, period: Int): List<BigDecimal> {
        if (prices.size < period) return emptyList()
        val k = BigDecimal("2").divide(BigDecimal(period + 1), 10, RM)
        val oneMinusK = BigDecimal.ONE.subtract(k)

        val result = mutableListOf<BigDecimal>()
        // 첫 EMA = 첫 period의 SMA
        var prev = prices.subList(0, period)
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal(period), SCALE, RM)
        result.add(prev)

        for (i in period until prices.size) {
            prev = prices[i].multiply(k).add(prev.multiply(oneMinusK))
                .setScale(SCALE, RM)
            result.add(prev)
        }
        return result
    }

    /**
     * RSI (Relative Strength Index)
     * 0~100 사이 값. 70 이상 과매수, 30 이하 과매도
     */
    fun rsi(prices: List<BigDecimal>, period: Int = 14): List<BigDecimal> {
        if (prices.size <= period) return emptyList()

        val changes = (1 until prices.size).map { i ->
            prices[i].subtract(prices[i - 1])
        }

        // 초기 평균 이득/손실
        var avgGain = changes.subList(0, period)
            .filter { it > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal(period), 10, RM)

        var avgLoss = changes.subList(0, period)
            .filter { it < BigDecimal.ZERO }
            .map { it.abs() }
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal(period), 10, RM)

        val result = mutableListOf<BigDecimal>()

        fun calcRsi(ag: BigDecimal, al: BigDecimal): BigDecimal {
            if (al == BigDecimal.ZERO) return BigDecimal("100")
            val rs = ag.divide(al, 10, RM)
            return BigDecimal("100").subtract(
                BigDecimal("100").divide(BigDecimal.ONE.add(rs), SCALE, RM)
            )
        }

        result.add(calcRsi(avgGain, avgLoss))

        val periodBD = BigDecimal(period)
        val periodMinus1 = BigDecimal(period - 1)

        for (i in period until changes.size) {
            val gain = if (changes[i] > BigDecimal.ZERO) changes[i] else BigDecimal.ZERO
            val loss = if (changes[i] < BigDecimal.ZERO) changes[i].abs() else BigDecimal.ZERO

            // Wilder's Smoothing
            avgGain = avgGain.multiply(periodMinus1).add(gain).divide(periodBD, 10, RM)
            avgLoss = avgLoss.multiply(periodMinus1).add(loss).divide(periodBD, 10, RM)

            result.add(calcRsi(avgGain, avgLoss))
        }

        return result
    }

    /**
     * MACD (Moving Average Convergence Divergence)
     * MACD선 = EMA(12) - EMA(26)
     * Signal선 = MACD선의 EMA(9)
     * 히스토그램 = MACD - Signal
     */
    data class MacdResult(
        val macd: BigDecimal,
        val signal: BigDecimal,
        val histogram: BigDecimal
    )

    fun macd(
        prices: List<BigDecimal>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): List<MacdResult> {
        val fastEma = ema(prices, fastPeriod)
        val slowEma = ema(prices, slowPeriod)

        // EMA 길이 정렬: slowEma가 더 짧음
        val offset = fastEma.size - slowEma.size
        val macdLine = slowEma.indices.map { i ->
            fastEma[i + offset].subtract(slowEma[i])
        }

        val signalLine = ema(macdLine, signalPeriod)
        val signalOffset = macdLine.size - signalLine.size

        return signalLine.indices.map { i ->
            val m = macdLine[i + signalOffset]
            val s = signalLine[i]
            MacdResult(m, s, m.subtract(s))
        }
    }

    /**
     * 볼린저밴드 (Bollinger Bands)
     * 중심선 = SMA(20)
     * 상단 = 중심선 + 2 × 표준편차
     * 하단 = 중심선 - 2 × 표준편차
     */
    data class BollingerBand(
        val upper: BigDecimal,
        val middle: BigDecimal,
        val lower: BigDecimal
    )

    fun bollingerBands(
        prices: List<BigDecimal>,
        period: Int = 20,
        multiplier: BigDecimal = BigDecimal("2")
    ): List<BollingerBand> {
        return (period..prices.size).map { end ->
            val window = prices.subList(end - period, end)
            val mean = window.fold(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal(period), 10, RM)

            // 표준편차
            val variance = window
                .map { it.subtract(mean).pow(2) }
                .fold(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal(period), 10, RM)

            val stdDev = variance.toDouble().let { Math.sqrt(it) }
                .let { BigDecimal(it).setScale(SCALE, RM) }

            val deviation = multiplier.multiply(stdDev).setScale(SCALE, RM)
            BollingerBand(
                upper = mean.add(deviation).setScale(SCALE, RM),
                middle = mean.setScale(SCALE, RM),
                lower = mean.subtract(deviation).setScale(SCALE, RM)
            )
        }
    }
}
```

---

## 7. 시세 캐싱과 스냅샷

### 7.1 캐시 계층 구조

```
[KRX 시세 스트림]
        │
        ▼
[Feed Handler]
        │ pub
        ▼
[Redis]  ←──────── 현재가 스냅샷 (1건/종목), 호가 스냅샷
        │
        ├── Hash: mktprice:{stockCode}  → 현재가, 등락률, 거래량 등
        ├── Hash: orderbook:{stockCode} → 매수/매도 10단 호가
        └── Sorted Set: trades:{stockCode} → 최근 체결 내역 (ZADD timestamp score)
```

### 7.2 캐시 갱신 전략

| 데이터 | 갱신 방식 | TTL |
|--------|----------|-----|
| 현재가 | Write-Through (체결 시 즉시) | 없음(장 중 상시 유효) |
| 호가 | Write-Through | 없음 |
| 분봉 | 분 단위 배치 | 1일 |
| 일봉 | 장 마감 후 배치 | 영구(DB로 이관) |

### 7.3 스냅샷 API

고객이 처음 앱을 열거나 재연결 시 스냅샷(Snapshot)을 먼저 받고, 이후 실시간 델타(Delta)를 수신한다.

```
[클라이언트 연결 시퀀스]
  1. WebSocket 연결 수립
  2. 관심 종목 구독 요청 전송
  3. 서버: Redis에서 스냅샷 조회 → 즉시 전송 (현재가, 호가 등)
  4. 서버: 이후 실시간 업데이트 구독 채널에 등록
  5. 실시간 시세 수신 시작
```

---

## 8. 정정·취소 데이터 처리

### 8.1 체결 정정·취소 발생 시나리오

드물지만 거래소는 체결 정정(Trade Correction)이나 취소(Trade Cancellation) 메시지를 발행할 수 있다. 시스템 오류, 착오 체결 등이 원인이다.

```
[정정/취소 처리 플로우]
  1. 거래소 정정/취소 메시지 수신
  2. 원래 체결 식별 (원체결 시퀀스 번호)
  3. 차트 OHLCV 재계산
  4. 잔고/손익 역산 처리
  5. 고객 알림 발송
  6. 감사 로그 기록
```

**개발 포인트**: 정정·취소는 이미 처리 완료된 데이터를 수정해야 하므로 **이벤트 소싱(Event Sourcing)** 패턴이 유리하다. 원본 이벤트를 유지하고 정정 이벤트를 추가해 최종 상태를 재계산한다.

---

## 9. 데이터 정합성 관리

### 9.1 체크포인트와 갭 감지

```kotlin
class SequenceGapDetector {
    private var lastSeq = 0L

    data class GapResult(
        val hasGap: Boolean,
        val missedFrom: Long,
        val missedTo: Long
    )

    fun check(receivedSeq: Long): GapResult {
        val expected = lastSeq + 1
        val hasGap = receivedSeq > expected

        val result = GapResult(
            hasGap = hasGap,
            missedFrom = if (hasGap) expected else 0L,
            missedTo = if (hasGap) receivedSeq - 1 else 0L
        )

        lastSeq = receivedSeq
        return result
    }
}
```

### 9.2 종가 확정과 일별 집계

| 이벤트 | 시각(예시) | 처리 |
|--------|----------|------|
| 정규장 종료 | 15:30 | 당일 체결 집계, OHLCV 확정 |
| 시간외 단일가 종료 | 16:00 | 시간외 OHLCV 확정 |
| 장후 배치 | 16:30~ | 일봉 생성, DB 영구 저장, 캐시 업데이트 |

---

## 10. WebSocket 실시간 시세 푸시

### 10.1 프로토콜 선택

| 프로토콜 | 특징 | 증권 시스템 적합성 |
|---------|------|------------------|
| WebSocket | 양방향, 저지연 | 가장 일반적 |
| SSE (Server-Sent Events) | 단방향(서버→클라이언트), HTTP 기반 | 단순 시세 표시에 적합 |
| gRPC Streaming | 바이너리, 타입 안전 | 내부 서버 간 통신 |
| HTTP Long Polling | 레거시 | 비추천 |

### 10.2 WebSocket 메시지 설계

```json
// 현재가 업데이트 (서버 → 클라이언트)
{
  "type": "PRICE_UPDATE",
  "stockCode": "005930",
  "price": 78200,
  "change": 300,
  "changeRate": 0.39,
  "volume": 12345678,
  "tradeTime": "093015",
  "timestamp": 1718000000000
}

// 호가 업데이트
{
  "type": "QUOTE_UPDATE",
  "stockCode": "005930",
  "asks": [
    {"price": 78300, "qty": 5000, "count": 12},
    {"price": 78400, "qty": 3200, "count": 8}
  ],
  "bids": [
    {"price": 78100, "qty": 7000, "count": 20},
    {"price": 78000, "qty": 10000, "count": 35}
  ],
  "timestamp": 1718000000000
}

// 구독 요청 (클라이언트 → 서버)
{
  "type": "SUBSCRIBE",
  "stocks": ["005930", "000660", "035720"],
  "channels": ["PRICE", "QUOTE"]
}
```

### 10.3 연결 관리 체크리스트

```
[WebSocket 서버 구현 체크리스트]
□ 하트비트(Ping/Pong): 30초 간격, 응답 없으면 연결 종료
□ 재연결: 클라이언트 지수 백오프(Exponential Backoff) 재시도
□ 인증: JWT 토큰 검증 (연결 시 1회, 토큰 만료 감지)
□ 연결 수 제한: 계좌당 최대 동시 연결 수 (예: 5개)
□ 구독 수 제한: 연결당 최대 종목 수 (예: 100개)
□ 장외 시간 처리: 장 종료 후 실시간 스트림 중단, 스냅샷 유지
□ 로드 밸런싱: Sticky Session 또는 공유 Pub/Sub 필요
```

---

## 11. 개발자 관점 핵심 정리

| 문제 | 해결 접근 |
|------|----------|
| 초당 수만 건 처리 | Conflation + Throttling + 비동기 처리 |
| 구독자별 필터링 | 서버 사이드 Pub/Sub (Redis / Kafka 토픽) |
| 패킷 유실 | 시퀀스 번호 갭 감지 + TCP Recovery |
| 지연 최소화 | UDP → 내부 버스 → WebSocket 파이프라인 최적화 |
| 캐시 정합성 | 스냅샷 우선 제공 + Delta 스트리밍 |
| 지표 계산 정밀도 | BigDecimal 사용, double/float 금지 |
| 재연결 시 데이터 | Redis 스냅샷 즉시 제공 후 실시간 구독 합류 |

---

이전: [34. 신용·파생 기초](34-credit-derivatives.md) · 다음: [36. 리스크 관리와 컴플라이언스](36-risk-compliance.md) · [전체 커리큘럼](../CURRICULUM.md)
