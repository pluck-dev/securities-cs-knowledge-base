# 37. 저지연 시스템 설계

## 1. 지연(Latency)과 처리량(Throughput)의 본질적 차이

지연(Latency)과 처리량(Throughput)은 흔히 같은 방향으로 개선된다고 오해하지만, 실제로는 **근본적으로 다른 목표**이며 종종 상충(trade-off)한다.

| 구분 | 지연 (Latency) | 처리량 (Throughput) |
|------|--------------|------------------|
| 정의 | 요청 1건이 완료되는 데 걸리는 시간 | 단위 시간당 처리되는 요청 수 |
| 단위 | ms, μs, ns | RPS, TPS, msg/sec |
| 최적화 방향 | 경로 단축, 대기 제거 | 병렬화, 배치 처리 |
| 상충 예시 | 배치를 쌓으면 처리량↑, 지연↑ | 나노초 경쟁에서 배치는 금기 |
| 주요 지표 | p99, p999 tail latency | avg RPS, peak TPS |

### 리틀의 법칙(Little's Law)

```
L = λ × W
```

- `L`: 시스템 내 평균 요청 수
- `λ`: 요청 도착률 (처리량)
- `W`: 평균 지연

처리량을 올리면서 큐 길이(`L`)가 늘어나면 지연(`W`)은 반드시 증가한다. **저지연 시스템에서 배치(batching)는 독이다.**

---

## 2. 증권 주문계의 마이크로초 경쟁

### 2.1 HFT(High-Frequency Trading)의 세계

여의도 증권사 주문 시스템은 다음과 같은 지연 목표를 가진다.

```
거래소 매칭엔진 ──── 마이크로초 단위 ────▶ 내부 주문 라우터
      │                                        │
  코로케이션(Co-location)              전용회선(Dedicated Line)
      │                                        │
  나노초 클럭 동기화(PTP/GPS)          커널 바이패스 NIC
```

| 경쟁 구간 | 목표 지연 | 주요 기술 |
|----------|---------|---------|
| 시세 수신 → 의사결정 | < 1 μs | FPGA, DPDK |
| 의사결정 → 주문 전송 | < 5 μs | 커널 바이패스, Zero-copy |
| 내부 주문 처리 | < 100 μs | 디스럽터, 락프리(Lock-free) |
| 리스크 체크 | < 50 μs | 인메모리, 원자적 연산 |

### 2.2 코로케이션(Co-location)

거래소(KRX) 데이터센터에 서버를 물리적으로 위치시켜 광 케이블 전파 지연을 제거한다. 서울↔판교 구간은 약 `30km × 5ns/m ≒ 150μs` 의 물리적 하한이 존재한다. 코로케이션으로 이 지연을 나노초 수준으로 줄인다.

---

## 3. 지연의 원인 상세 분석

### 3.1 지연 원인 분류

```
┌─────────────────────────────────────────────────┐
│                  지연 원인 트리                   │
├──────────────┬──────────────────────────────────┤
│  JVM 내부    │  GC Stop-the-World               │
│              │  JIT 컴파일 웜업                  │
│              │  객체 박싱(Boxing)                │
│              │  스레드 컨텍스트 스위칭            │
├──────────────┼──────────────────────────────────┤
│  하드웨어    │  CPU 캐시 미스(Cache Miss)        │
│              │  NUMA 원격 메모리 접근             │
│              │  False Sharing                   │
│              │  메모리 배리어(Memory Barrier)    │
├──────────────┼──────────────────────────────────┤
│  OS/커널    │  시스템 콜 오버헤드               │
│              │  인터럽트(Interrupt)              │
│              │  소켓 버퍼 복사                   │
├──────────────┼──────────────────────────────────┤
│  네트워크   │  TCP 핸드셰이크                   │
│              │  패킷 직렬화/역직렬화             │
│              │  스위치/라우터 홉                 │
├──────────────┼──────────────────────────────────┤
│  애플리케이션│  락 경합(Lock Contention)        │
│              │  동기 I/O 블로킹                  │
│              │  직렬화 병목 (JSON 파싱)          │
└──────────────┴──────────────────────────────────┘
```

### 3.2 GC Stop-the-World가 가장 치명적인 이유

GC가 발생하면 모든 애플리케이션 스레드가 멈춘다(STW: Stop-the-World). 주문 처리 중 50ms GC가 발생하면 그 사이 들어온 수백 건의 시세 변동을 놓친다.

```
타임라인:
──────────────[주문 처리]──[GC 50ms]──────────────▶ 시간
                              ↑
                         이 구간 시세 변동 모두 누락
```

---

## 4. GC 튜닝 전략

### 4.1 GC 선택 기준

| GC | 목표 | STW 특성 | 적합 시나리오 |
|----|-----|---------|------------|
| G1GC | 균형 | 수십~수백 ms | 일반 백엔드 |
| ZGC | 초저지연 | < 1ms (JDK 15+) | 저지연 주문계 |
| Shenandoah | 저지연 | < 10ms | ZGC 대안 |
| Epsilon | No-op | GC 없음 (OOM 위험) | 성능 측정용 |

### 4.2 ZGC 설정 (스프링 부트 기준)

```yaml
# application.yml (JVM 옵션은 별도 설정)
spring:
  application:
    name: order-system
```

```bash
# JVM 옵션 (저지연 최적화)
JAVA_OPTS="\
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -Xms8g -Xmx8g \
  -XX:+AlwaysPreTouch \
  -XX:+DisableExplicitGC \
  -XX:+UseTransparentHugePages \
  -XX:ZUncommitDelay=300 \
  -Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=5,filesize=20m"
```

### 4.3 핵심 힙 사이징 원칙

- `-Xms`와 `-Xmx`를 동일하게 설정 → 힙 리사이징 오버헤드 제거
- `-XX:+AlwaysPreTouch` → 시작 시 힙 메모리를 물리 메모리에 미리 매핑
- GC 로그를 항상 활성화하고 GCViewer/GCeasy로 분석

### 4.4 Allocation 줄이기 — 코틀린 실전

```kotlin
// 나쁜 예: 불필요한 객체 생성
data class OrderPrice(val amount: BigDecimal, val currency: String)

fun processOrder(price: Double): OrderPrice {
    return OrderPrice(BigDecimal.valueOf(price), "KRW") // 매번 BigDecimal 생성
}

// 좋은 예: Value Class로 박싱 회피 + 객체 재사용
@JvmInline
value class OrderAmount(val raw: Long) { // 원 단위 Long으로 저장 (소수점 없음)
    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(raw)
    operator fun plus(other: OrderAmount) = OrderAmount(raw + other.raw)
    operator fun compareTo(other: OrderAmount) = raw.compareTo(other.raw)
}

// 금액 상수 — 자주 쓰는 값은 미리 생성
object PriceConstants {
    val ZERO = OrderAmount(0L)
    val TICK_SIZE = OrderAmount(100L)  // 1틱 = 100원
}
```

---

## 5. 객체 풀링(Object Pooling)과 GC 회피

### 5.1 패턴 개요

객체를 생성/소멸하는 대신 **미리 생성된 풀(pool)에서 빌려쓰고 반납**한다. GC 대상 객체 수를 줄여 STW 빈도와 시간을 낮춘다.

### 5.2 코틀린 주문 객체 풀 구현

```kotlin
import java.util.concurrent.ArrayBlockingQueue
import java.math.BigDecimal

data class OrderEvent(
    var orderId: Long = 0L,
    var symbol: String = "",
    var price: BigDecimal = BigDecimal.ZERO,
    var quantity: Long = 0L,
    var side: OrderSide = OrderSide.BUY,
    var timestamp: Long = 0L
) {
    fun reset() {
        orderId = 0L
        symbol = ""
        price = BigDecimal.ZERO
        quantity = 0L
        side = OrderSide.BUY
        timestamp = 0L
    }
}

enum class OrderSide { BUY, SELL }

class OrderEventPool(private val capacity: Int = 1024) {
    private val pool = ArrayBlockingQueue<OrderEvent>(capacity)

    init {
        repeat(capacity) { pool.offer(OrderEvent()) }
    }

    fun borrow(): OrderEvent = pool.poll() ?: OrderEvent() // 풀 소진 시 신규 생성 (fallback)

    fun release(event: OrderEvent) {
        event.reset()
        pool.offer(event) // 반납 실패 시 GC에 위임 (풀이 가득 찬 경우)
    }
}

// 사용 예
val pool = OrderEventPool()

fun handleMarketData(rawData: ByteArray) {
    val event = pool.borrow()
    try {
        // rawData 파싱하여 event 채우기 (Zero-allocation 파싱)
        event.orderId = /* 파싱 */ 12345L
        event.symbol = "005930"
        event.price = BigDecimal("85000")
        event.quantity = 100L
        event.side = OrderSide.BUY
        event.timestamp = System.nanoTime()

        processOrderEvent(event)
    } finally {
        pool.release(event)
    }
}

fun processOrderEvent(event: OrderEvent) {
    // 비즈니스 로직
}
```

---

## 6. 메커니컬 심퍼시(Mechanical Sympathy) — CPU 캐시 친화적 코드

### 6.1 CPU 캐시 계층 지연

```
CPU 코어
  └── L1 캐시 (32~64KB, ~1ns, 4 사이클)
        └── L2 캐시 (256KB~1MB, ~4ns, 12 사이클)
              └── L3 캐시 (8~32MB, ~12ns, 40 사이클)
                    └── 메인 메모리 (GB, ~100ns, 200+ 사이클)
```

캐시 미스가 발생하면 메모리 접근 비용이 100배 이상 증가한다.

### 6.2 캐시 라인(Cache Line)과 False Sharing

CPU는 64바이트 단위 캐시 라인으로 데이터를 로드한다. **서로 다른 스레드가 같은 캐시 라인의 다른 변수를 수정하면** 캐시 무효화(Invalidation) 폭풍이 발생한다.

```kotlin
// 나쁜 예: False Sharing
class CounterBad {
    var counter1: Long = 0L  // 같은 캐시 라인에 위치
    var counter2: Long = 0L  // Thread-A와 Thread-B가 각각 접근 → False Sharing
}

// 좋은 예: 패딩(Padding)으로 캐시 라인 분리
// JVM에서는 @Contended 어노테이션 사용 (JDK 8+)
// -XX:-RestrictContended JVM 옵션 필요
class CounterGood {
    @jvm.internal.vm.annotation.Contended
    @Volatile
    var counter1: Long = 0L

    @jvm.internal.vm.annotation.Contended
    @Volatile
    var counter2: Long = 0L
}

// Kotlin + 수동 패딩 (이식성 높음)
class PaddedCounter {
    var p1 = 0L; var p2 = 0L; var p3 = 0L; var p4 = 0L
    var p5 = 0L; var p6 = 0L; var p7 = 0L  // 앞 패딩 (7 × 8 = 56 bytes)
    @Volatile var value: Long = 0L          // 실제 값 (8 bytes) → 총 64 bytes
    var q1 = 0L; var q2 = 0L; var q3 = 0L  // 뒤 패딩
    var q4 = 0L; var q5 = 0L; var q6 = 0L
    var q7 = 0L
}
```

### 6.3 데이터 배치 — Array of Structs vs Struct of Arrays

```kotlin
// AoS (Array of Structs) — 캐시 비효율적 (필드가 분산)
data class Order(val price: Long, val qty: Long, val flags: Int)
val orders = Array(1_000_000) { Order(0L, 0L, 0) }

// SoA (Struct of Arrays) — 캐시 친화적 (같은 필드가 연속)
class OrderBook(val capacity: Int) {
    val prices = LongArray(capacity)
    val quantities = LongArray(capacity)
    val flags = IntArray(capacity)
    var size = 0

    fun addOrder(price: Long, qty: Long, flag: Int) {
        prices[size] = price
        quantities[size] = qty
        flags[size] = flag
        size++
    }

    // prices 배열만 순회 → CPU가 캐시 라인 단위로 prefetch, 빠름
    fun findBestBid(): Long = prices.take(size).max() ?: 0L
}
```

---

## 7. LMAX Disruptor 패턴

### 7.1 개념

LMAX Exchange가 개발한 고성능 이벤트 처리 라이브러리. **링버퍼(Ring Buffer)** 를 중심으로 다음 원칙을 따른다.

```
Producer ──▶ [Ring Buffer] ──▶ Consumer 1 (주문 검증)
                                        │
                                        ▼
                              Consumer 2 (리스크 체크)
                                        │
                                        ▼
                              Consumer 3 (주문 라우팅)

- 단일 Writer 원칙으로 CAS(Compare-And-Swap) 없음
- Pre-allocated Ring Buffer → GC 없음
- 메모리 배리어(Memory Barrier)로 volatile 최소화
- False Sharing 제거 (Sequence 패딩)
```

### 7.2 스프링 부트 + Disruptor 연동

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.lmax:disruptor:4.0.0")
    implementation("org.springframework.boot:spring-boot-starter")
}
```

```kotlin
import com.lmax.disruptor.*
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import java.math.BigDecimal
import java.util.concurrent.Executors

// 이벤트 객체 (Ring Buffer에 Pre-allocated)
class TradeEvent {
    var orderId: Long = 0L
    var symbol: String = ""
    var price: BigDecimal = BigDecimal.ZERO
    var quantity: Long = 0L
    var timestamp: Long = 0L
}

// 이벤트 팩토리 (Ring Buffer 초기화 시 사용)
class TradeEventFactory : EventFactory<TradeEvent> {
    override fun newInstance() = TradeEvent()
}

// 이벤트 핸들러 — 비즈니스 로직은 단일 스레드
class RiskCheckHandler : EventHandler<TradeEvent> {
    override fun onEvent(event: TradeEvent, sequence: Long, endOfBatch: Boolean) {
        // 리스크 한도 체크 (단일 스레드이므로 락 불필요)
        if (event.price * BigDecimal.valueOf(event.quantity) > BigDecimal("1000000000")) {
            throw IllegalStateException("주문 한도 초과: orderId=${event.orderId}")
        }
    }
}

class OrderRoutingHandler(
    private val exchangeGateway: ExchangeGateway
) : EventHandler<TradeEvent> {
    override fun onEvent(event: TradeEvent, sequence: Long, endOfBatch: Boolean) {
        exchangeGateway.send(event)
    }
}

// 인터페이스
interface ExchangeGateway {
    fun send(event: TradeEvent)
}

// Disruptor 설정
@org.springframework.context.annotation.Configuration
class DisruptorConfig(private val exchangeGateway: ExchangeGateway) {

    @org.springframework.context.annotation.Bean
    fun disruptor(): Disruptor<TradeEvent> {
        val bufferSize = 1024  // 반드시 2의 거듭제곱
        val executor = Executors.newCachedThreadPool()

        val disruptor = Disruptor(
            TradeEventFactory(),
            bufferSize,
            executor,
            ProducerType.SINGLE,       // 단일 Producer → 최고 성능
            BusySpinWaitStrategy()     // 저지연: CPU를 태워서 대기
            // BlockingWaitStrategy()  // 일반 서버: CPU 절약
        )

        disruptor
            .handleEventsWith(RiskCheckHandler())
            .then(OrderRoutingHandler(exchangeGateway))

        disruptor.start()
        return disruptor
    }
}

// 주문 접수 서비스
@org.springframework.stereotype.Service
class OrderService(private val disruptor: Disruptor<TradeEvent>) {

    private val ringBuffer = disruptor.ringBuffer

    fun submitOrder(orderId: Long, symbol: String, price: BigDecimal, qty: Long) {
        val sequence = ringBuffer.next()  // 슬롯 예약
        try {
            val event = ringBuffer[sequence]
            event.orderId = orderId
            event.symbol = symbol
            event.price = price
            event.quantity = qty
            event.timestamp = System.nanoTime()
        } finally {
            ringBuffer.publish(sequence)  // 메모리 배리어 → Consumer에게 가시성 보장
        }
    }
}
```

### 7.3 Wait Strategy 비교

| Strategy | CPU 사용 | 지연 | 적합 시나리오 |
|----------|---------|-----|------------|
| `BusySpinWaitStrategy` | 100% | 최저 | 전용 코어, HFT |
| `YieldingWaitStrategy` | 높음 | 낮음 | 저지연 일반 서버 |
| `SleepingWaitStrategy` | 낮음 | 수μs | 배치 처리 |
| `BlockingWaitStrategy` | 최저 | 높음 | 처리량 우선 |

---

## 8. JIT 웜업(JIT Warm-up) 전략

JVM은 시작 직후 인터프리터 모드로 실행되다가 **컴파일 임계치(10,000회 호출)** 를 넘으면 JIT 컴파일한다. 웜업 전 측정값은 쓸모없다.

### 8.1 코틀린 웜업 예시

```kotlin
@org.springframework.boot.context.event.EventListener
fun onApplicationReady(event: org.springframework.boot.context.event.ApplicationReadyEvent) {
    warmUpCriticalPaths()
}

private fun warmUpCriticalPaths() {
    val iterations = 50_000
    val dummyOrder = TradeEvent().apply {
        orderId = -1L
        symbol = "WARMUP"
        price = BigDecimal("100.00")
        quantity = 1L
    }

    repeat(iterations) {
        // 핵심 경로를 실제로 실행하여 JIT 컴파일 유도
        riskCheckHandler.onEvent(dummyOrder, it.toLong(), false)
    }

    println("[WARMUP] $iterations 회 완료 — JIT 컴파일 완료")
}
```

### 8.2 GraalVM AOT(Ahead-of-Time) 컴파일

```bash
# Spring Boot 3 + GraalVM Native Image
./gradlew nativeCompile

# JIT 웜업 불필요, 시작 시간 < 100ms
# 단점: 리플렉션 제한, 빌드 시간 5~15분
```

---

## 9. 커널 바이패스(Kernel Bypass)

### 9.1 전통적 네트워크 스택의 문제

```
NIC ──▶ 커널 드라이버 ──▶ 소켓 버퍼 ──▶ 시스템 콜 ──▶ 애플리케이션
                                                ↑
                                     컨텍스트 스위칭 발생 (~5μs)
```

### 9.2 커널 바이패스 기술

| 기술 | 원리 | 지연 | 비고 |
|-----|-----|-----|-----|
| DPDK | 커널 스택 우회, 유저스페이스 NIC | ~1μs | 리눅스, C/C++ |
| RDMA | NIC가 CPU 없이 메모리 직접 접근 | <1μs | InfiniBand |
| AF_XDP | eBPF 기반 유저스페이스 패킷 처리 | ~2μs | 리눅스 5.x+ |
| Solarflare OpenOnload | 소켓 API 유지, 커널 바이패스 | ~1μs | 드롭인 교체 가능 |

JVM 환경에서는 직접 DPDK를 사용하기 어려우므로, **Chronicle Network** 또는 **Aeron** 라이브러리를 통해 JVM에서 커널 바이패스 효과를 얻는다.

```kotlin
// Aeron 기반 저지연 UDP 발행 예시
// build.gradle.kts: implementation("io.aeron:aeron-all:1.44.0")

import io.aeron.Aeron
import io.aeron.Publication
import org.agrona.concurrent.UnsafeBuffer
import java.nio.ByteBuffer

class AeronOrderPublisher {
    private val aeron: Aeron = Aeron.connect()
    private val publication: Publication = aeron.addPublication(
        "aeron:udp?endpoint=239.255.0.1:40123",
        1001
    )
    private val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(256))

    fun publishOrder(orderId: Long, price: Long, qty: Long) {
        buffer.putLong(0, orderId)
        buffer.putLong(8, price)
        buffer.putLong(16, qty)

        while (publication.offer(buffer, 0, 24) < 0L) {
            // Back-pressure 대응: Thread.yield() 또는 busy-spin
            Thread.onSpinWait()
        }
    }
}
```

---

## 10. 올바른 측정: Percentile과 코디네이티드 오미션

### 10.1 평균(Average)의 함정

평균 지연은 **tail latency(꼬리 지연)** 을 숨긴다. 주문 99.9%가 1ms 이내이더라도 0.1%가 500ms라면 운영 중 매일 수백 건의 주문이 500ms 지연된다.

```
지연 분포 예시:
p50  =   0.8ms  → 50%가 이 이하
p90  =   2.1ms  → 90%가 이 이하
p99  =   8.5ms  → 99%가 이 이하  ← 모니터링 필수
p999 =  45.0ms  → 99.9%가 이 이하 ← GC STW 주요 원인
p9999= 200.0ms  → 99.99%가 이 이하 ← 코로케이션 시스템 임계
```

### 10.2 코디네이티드 오미션(Coordinated Omission)

부하 생성기(load generator)가 응답을 기다린 후 다음 요청을 보내면, **시스템이 느릴 때 요청을 덜 보내게 되어** 실제보다 빠른 측정값이 나온다.

```
잘못된 측정 (순차 방식):
요청1─▶[처리 200ms]─▶요청2─▶[처리 1ms]─▶요청3 → 평균 100ms

실제 올바른 측정 (독립 타임스탬프):
T=0ms:   요청1 전송
T=1ms:   요청2 전송 (계획)
T=200ms: 요청1 완료 → 지연 200ms, 하지만 요청2,3...은 T=200ms까지 대기
          이 대기 시간도 지연에 포함해야 함
```

**HDR Histogram**을 사용하면 코디네이티드 오미션을 보정할 수 있다.

```kotlin
// build.gradle.kts: implementation("org.hdrhistogram:HdrHistogram:2.2.2")
import org.HdrHistogram.Histogram

class LatencyRecorder {
    private val histogram = Histogram(
        1,           // 최소값 (나노초)
        60_000_000_000L, // 최대값 (60초)
        3            // 유효 자릿수
    )

    fun record(startNanos: Long) {
        val latencyNanos = System.nanoTime() - startNanos
        histogram.recordValue(latencyNanos)
    }

    fun printStats() {
        println("p50  = ${histogram.getValueAtPercentile(50.0) / 1_000}μs")
        println("p99  = ${histogram.getValueAtPercentile(99.0) / 1_000}μs")
        println("p999 = ${histogram.getValueAtPercentile(99.9) / 1_000}μs")
        println("max  = ${histogram.maxValue / 1_000}μs")
    }
}
```

### 10.3 JMH(Java Microbenchmark Harness) 활용

```kotlin
// build.gradle.kts
// jmh 플러그인 사용 (me.champeau.jmh)

import org.openjdk.jmh.annotations.*
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
open class OrderPriceBenchmark {

    @Benchmark
    fun bigDecimalCreation(): BigDecimal {
        return BigDecimal("85000.00")  // 파싱 비용 발생
    }

    @Benchmark
    fun bigDecimalValueOf(): BigDecimal {
        return BigDecimal.valueOf(85000, 2)  // 팩토리 메서드 (캐시 활용)
    }

    @Benchmark
    fun longBasedAmount(): OrderAmount {
        return OrderAmount(8500000L)  // Value class, 박싱 없음
    }
}
```

---

## 11. 코틀린/JVM 실전 저지연 팁

### 11.1 Value Class로 박싱 회피

```kotlin
// Long을 래핑하지만 런타임에 박싱 없음 (JVM 바이트코드에서 Long으로 처리)
@JvmInline
value class Price(val raw: Long) {  // 단위: 원 (최소 1원)
    companion object {
        fun of(amount: BigDecimal): Price = Price(amount.toLong())
        val ZERO = Price(0L)
    }
    operator fun compareTo(other: Price): Int = raw.compareTo(other.raw)
    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(raw)
}

@JvmInline
value class Quantity(val raw: Long) {
    operator fun times(price: Price): Long = raw * price.raw
    companion object {
        val ZERO = Quantity(0L)
    }
}
```

### 11.2 inline 함수로 람다 오버헤드 제거

```kotlin
// 람다 사용 시 Function 객체 생성 → GC 압박
fun measureLatency(block: () -> Unit): Long {
    val start = System.nanoTime()
    block()
    return System.nanoTime() - start
}

// inline 키워드: 컴파일 시점에 람다 코드 인라이닝 → 객체 생성 없음
inline fun measureLatencyInline(block: () -> Unit): Long {
    val start = System.nanoTime()
    block()
    return System.nanoTime() - start
}

// 호출 예
val latency = measureLatencyInline {
    orderService.submitOrder(123L, "005930", BigDecimal("85000"), 100L)
}
```

### 11.3 컬렉션 처리 최적화

```kotlin
// 나쁜 예: 중간 리스트 생성
val result = orders
    .filter { it.price > Price(80000L) }  // 임시 리스트 생성
    .map { it.quantity }                   // 또 다른 임시 리스트
    .sum()

// 좋은 예: Sequence로 지연 평가 (중간 컬렉션 없음)
val result = orders.asSequence()
    .filter { it.price > Price(80000L) }
    .sumOf { it.quantity.raw }

// 최고 성능: 직접 반복 (컬렉션 API 오버헤드 없음)
var result = 0L
for (order in orders) {
    if (order.price > Price(80000L)) {
        result += order.quantity.raw
    }
}
```

### 11.4 ThreadLocal로 스레드별 버퍼 재사용

```kotlin
object SerializationBuffer {
    private val buffer = ThreadLocal.withInitial {
        ByteArray(4096)
    }

    fun getBuffer(): ByteArray = buffer.get()
}

fun serializeOrder(event: TradeEvent): Int {
    val buf = SerializationBuffer.getBuffer()
    // buf에 직렬화 (heap 할당 없음)
    var offset = 0
    writeLong(buf, offset, event.orderId); offset += 8
    writeLong(buf, offset, event.quantity); offset += 8
    // ...
    return offset
}

fun writeLong(buf: ByteArray, offset: Int, value: Long) {
    buf[offset] = (value shr 56).toByte()
    buf[offset + 1] = (value shr 48).toByte()
    buf[offset + 2] = (value shr 40).toByte()
    buf[offset + 3] = (value shr 32).toByte()
    buf[offset + 4] = (value shr 24).toByte()
    buf[offset + 5] = (value shr 16).toByte()
    buf[offset + 6] = (value shr 8).toByte()
    buf[offset + 7] = value.toByte()
}
```

---

## 12. 저지연 설계 트레이드오프 분석

| 기법 | 지연 개선 | 비용 | 복잡도 | 도입 권장 조건 |
|-----|---------|-----|-------|------------|
| ZGC | GC STW ~1ms | 메모리 10~20% 추가 | 낮음 | 항상 |
| 객체 풀링 | GC 빈도 감소 | 코드 복잡도 | 중간 | 10μs 이하 목표 |
| Disruptor | 락 제거 | 아키텍처 변경 | 높음 | 초당 100만 이벤트+ |
| Value Class | 박싱 제거 | 코틀린 제약 | 낮음 | 항상 |
| 캐시 라인 패딩 | False Sharing 제거 | 메모리 낭비 | 낮음 | 멀티코어 집약적 경로 |
| 커널 바이패스 | 네트워크 ~5μs 제거 | 인프라 비용 | 매우 높음 | HFT, 코로케이션 |
| AOT (GraalVM) | 웜업 제거 | 빌드 복잡도 | 높음 | 리플렉션 없는 서비스 |

---

## 13. 저지연 시스템 설계 체크리스트

### 아키텍처
- [ ] 핵심 경로(Critical Path)에서 동기 I/O 제거
- [ ] 단일 스레드 비즈니스 로직 vs 멀티스레드 I/O 분리
- [ ] 큐 깊이(Queue Depth) 제한으로 head-of-line blocking 방지
- [ ] Back-pressure 전략 명확히 정의

### JVM / GC
- [ ] ZGC 또는 Shenandoah 적용
- [ ] `-Xms`와 `-Xmx` 동일하게 설정 + AlwaysPreTouch
- [ ] GC 로그 수집 및 주기적 분석
- [ ] Allocation 핫스팟 식별 (Async Profiler 활용)
- [ ] 객체 풀링 적용 여부 검토

### 코드
- [ ] Value class 활용으로 원시 타입 래핑 비용 제거
- [ ] `inline` 함수 사용으로 람다 객체 제거
- [ ] Sequence/직접 반복으로 중간 컬렉션 제거
- [ ] ThreadLocal 버퍼 재사용

### 측정
- [ ] p99, p999 tail latency 모니터링
- [ ] HDR Histogram 사용 (코디네이티드 오미션 방지)
- [ ] JMH로 마이크로벤치마크 작성
- [ ] 운영 환경과 동일한 조건에서 측정 (클라우드 노이즈 주의)
- [ ] 웜업 완료 후 측정값만 사용

### 인프라
- [ ] CPU 어피니티(Affinity) 설정 검토
- [ ] NUMA 토폴로지 인식 JVM 설정
- [ ] 투명 거대 페이지(Transparent Huge Pages) 활성화
- [ ] 인터럽트 밸런싱 비활성화 (저지연 코어 보호)

---

## 14. 자주 빠지는 함정

1. **평균 지연만 모니터링**: p99 이상 tail latency를 반드시 함께 관찰한다.
2. **웜업 전 벤치마크**: JIT 컴파일 전 측정값은 실제 운영과 10배 이상 차이날 수 있다.
3. **락 대신 volatile로 해결**: volatile은 가시성만 보장하고 원자성을 보장하지 않는다. CAS 또는 AtomicLong을 사용한다.
4. **무조건 객체 풀링**: GC 튜닝 없이 풀링만 적용하면 풀 경합이 새로운 병목이 된다. 프로파일링 먼저.
5. **Disruptor를 범용 큐로 사용**: Disruptor는 단일 Producer 최적화다. 다수 Producer가 필요하면 `ProducerType.MULTI`로 전환하면 CAS가 생기므로 효과가 반감된다.
6. **클라우드 환경에서 나노초 측정**: VM 환경에서는 `System.nanoTime()`이 1μs 이상의 오차를 가질 수 있다. 물리 서버에서 측정한다.

---

이전: [36. 성능 테스트와 프로파일링](36-performance-testing) · 다음: [38. 이벤트 소싱과 CQRS](38-event-sourcing-cqrs) · [전체 커리큘럼](/curriculum)
