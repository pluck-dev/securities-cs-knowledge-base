# 43. 성능 튜닝

> "성급한 최적화는 모든 악의 근원이다." — Donald Knuth. 증권 시스템에서는 측정 없는 최적화가 오히려 장애를 만든다.

---

## 1. 성능 분석 방법론

성능 튜닝은 감과 경험이 아니라 **데이터 기반 과학**이다. 올바른 순서:

```
측정(Measure) → 병목 특정(Profile) → 개선(Optimize) → 검증(Validate) → 반복
```

### 1.1 황금 원칙

| 원칙 | 설명 |
|------|------|
| 측정 우선 | 코드를 보고 "이게 느릴 것 같다"는 추측은 대부분 틀린다 |
| 병목 집중 | 전체 응답 시간의 80%를 차지하는 20%의 코드에만 집중 |
| 목표 명확화 | "빠르게"가 아닌 "P99 < 100ms, 처리량 > 10,000 TPS" |
| 변경 최소화 | 한 번에 한 가지만 변경 후 측정 |
| 회귀 방지 | 성능 테스트를 CI 파이프라인에 포함 |

### 1.2 증권 시스템 성능 목표 예시

| 구간 | 목표 지연 | 비고 |
|------|---------|------|
| 주문 접수 API P99 | < 50ms | 게이트웨이 포함 |
| 주문 처리 (비즈니스 로직) | < 20ms | DB 제외 |
| 주문 DB 저장 | < 10ms | 인덱스 최적화 전제 |
| 시세 데이터 조회 (캐시 히트) | < 1ms | Redis |
| 시세 데이터 조회 (캐시 미스) | < 30ms | DB |
| 체결 통보 (WebSocket) | < 5ms | 메시지 큐 경유 |
| 전체 주문 왕복 P99 | < 100ms | SLA 기준 |
| 최대 처리량 (TPS) | > 5,000 | 정규장 피크 |

---

## 2. 프로파일링 도구

### 2.1 JFR(Java Flight Recorder)

JVM 내장 프로파일러. 운영 환경에서도 오버헤드 < 1%로 안전하게 사용 가능.

```bash
# 실행 중인 JVM에 JFR 연결 (PID 1234, 60초 기록)
jcmd 1234 JFR.start duration=60s filename=/tmp/order-service.jfr settings=profile

# 파일 덤프
jcmd 1234 JFR.dump filename=/tmp/order-service.jfr

# JDK Mission Control(JMC)로 분석
jmc
```

JFR에서 확인할 항목:
- **Hot Methods**: CPU 시간을 가장 많이 소비하는 메서드
- **Allocations**: 가장 많이 객체를 생성하는 코드 (GC 압박 원인)
- **Lock Contention**: 스레드 대기 발생 지점
- **I/O**: 파일/소켓 블로킹 구간

### 2.2 async-profiler + 플레임그래프

JFR보다 정확한 CPU/메모리 프로파일링. 네이티브 스택도 추적.

```bash
# async-profiler 다운로드 후
./profiler.sh -d 30 -f flamegraph.html -e cpu <PID>

# Wall-clock 프로파일링 (I/O 대기 포함)
./profiler.sh -d 30 -f flamegraph.html -e wall <PID>

# 메모리 할당 프로파일링
./profiler.sh -d 30 -f flamegraph.html -e alloc <PID>
```

플레임그래프 읽는 법:
```
넓을수록 CPU 점유율 높음
┌─────────────────────────────────────────┐ ← top (실제 실행)
│         OrderMatchingEngine.match()     │
├───────────────┬─────────────────────────┤
│  PriceCalc.   │   OrderBook.findBest()  │
│  compute()    │                         │
├───────────────┴─────────────────────────┤
│           OrderService.execute()        │
└─────────────────────────────────────────┘ ← bottom (진입점)
```

### 2.3 스프링 부트 액추에이터 메트릭 활용

```kotlin
// 특정 메서드 실행 시간 측정
@Component
class OrderMatchingProfiler(private val registry: MeterRegistry) {

    fun measureMatching(block: () -> MatchResult): MatchResult {
        val sample = Timer.start(registry)
        return try {
            block()
        } finally {
            sample.stop(Timer.builder("order.matching.duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry))
        }
    }
}
```

---

## 3. 부하 테스트 (Load Testing)

### 3.1 Gatling — 코드 기반 부하 테스트

```scala
// OrderSimulation.scala
class OrderSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("https://api.securities.internal")
    .header("Content-Type", "application/json")
    .header("Authorization", "Bearer ${token}")

  val orderScenario = scenario("주문 시나리오")
    .exec(
      http("주문 접수")
        .post("/api/v1/orders")
        .body(StringBody("""
          {
            "symbol": "005930",
            "side": "BUY",
            "type": "LIMIT",
            "quantity": 10,
            "price": 75000
          }
        """))
        .check(status.is(200))
        .check(responseTimeInMillis.lte(100)) // P99 목표 100ms
    )

  setUp(
    orderScenario.inject(
      rampUsersPerSec(100).to(1000).during(60.seconds), // 램프업
      constantUsersPerSec(1000).during(300.seconds),    // 정상 부하
      rampUsersPerSec(1000).to(3000).during(60.seconds) // 피크 부하
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile3.lte(100),   // P99 < 100ms
     global.successfulRequests.percent.gte(99.9) // 성공률 > 99.9%
   )
}
```

### 3.2 부하 테스트 시나리오 설계

| 시나리오 | 목적 | 기간 |
|---------|------|------|
| 기준선(Baseline) | 정상 부하에서 성능 측정 | 10분 |
| 스트레스(Stress) | 최대 처리량 탐색 | 30분 |
| 스파이크(Spike) | 급격한 트래픽 증가 대응 | 5분 |
| 내구성(Soak) | 장시간 안정성 확인 | 4~8시간 |
| 장 개시(Open) | 09:00 동시 접속 시뮬레이션 | 3분 |

> **실무 함정**: 부하 테스트는 항상 운영과 동일한 스펙의 **스테이징 환경**에서 수행해야 한다. 개발 환경 결과는 의미가 없다.

---

## 4. JVM 튜닝

### 4.1 힙 메모리 설정

```bash
# 증권 서비스 JVM 옵션 예시 (8GB 컨테이너 기준)
JAVA_OPTS="\
  -Xms4g \
  -Xmx4g \
  -XX:MetaspaceSize=256m \
  -XX:MaxMetaspaceSize=512m \
  -XX:+UseStringDeduplication \
  -XX:+AlwaysPreTouch"
```

**Xms와 Xmx를 동일하게** 설정하는 이유: 힙 확장 시 GC 일시정지가 발생하므로 증권 시스템에서는 시작부터 최대 힙을 고정한다.

### 4.2 GC 선택 가이드

| GC | 특징 | 증권 시스템 적합성 |
|----|------|-----------------|
| G1GC | 기본값, 균형 잡힌 성능 | 일반 백오피스 서비스 ✓ |
| ZGC | 최저 지연 (< 1ms GC 일시정지) | 주문 처리 핵심 서비스 ✓✓ |
| Shenandoah | ZGC와 유사, OpenJDK | 오픈소스 환경 ✓✓ |
| 직렬 GC | 단일 스레드 | 증권 시스템 절대 사용 금지 ✗ |

```bash
# ZGC 설정 (JDK 21 권장)
-XX:+UseZGC
-XX:ZCollectionInterval=30    # 30초마다 GC
-XX:ZFragmentationLimit=10    # 단편화 10% 이하 유지
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=5,filesize=20m
```

### 4.3 GC 로그 분석

```bash
# GCViewer로 GC 로그 시각화
# 확인 항목:
# - Full GC 빈도 (운영에서 Full GC = 위험 신호)
# - GC 일시정지 시간 분포
# - 힙 사용 패턴 (메모리 누수 탐지)
```

---

## 5. 애플리케이션 코드 최적화

### 5.1 불필요한 객체 생성 방지

```kotlin
// 나쁜 예: 루프마다 객체 생성
fun calculateTotal(orders: List<Order>): BigDecimal {
    var total = BigDecimal.ZERO
    for (order in orders) {
        total = total.add(order.price.multiply(BigDecimal(order.quantity))) // BigDecimal(int) 매번 생성
    }
    return total
}

// 좋은 예: 상수 재사용
fun calculateTotal(orders: List<Order>): BigDecimal =
    orders.fold(BigDecimal.ZERO) { acc, order ->
        acc.add(order.price.multiply(order.quantity.toBigDecimal()))
    }
```

### 5.2 BigDecimal 비용과 대안

증권 도메인은 금액 정밀도 때문에 BigDecimal을 필수로 사용하지만, 내부 계산에서는 비용을 최소화해야 한다.

```kotlin
// BigDecimal 비용 측정 (나노초 단위 차이)
// BigDecimal.add(): ~50ns
// double +: ~1ns
// → 핫패스에서 50배 차이

// 전략: 내부 계산은 Long(원 단위 × 10000), 출력 시 변환
data class Price(val rawValue: Long) { // 1원 = 10000 단위
    fun toBigDecimal(): BigDecimal = BigDecimal(rawValue).movePointLeft(4)
    operator fun plus(other: Price) = Price(rawValue + other.rawValue)
    operator fun times(quantity: Int) = Price(rawValue * quantity)
}
```

### 5.3 컬렉션 선택

| 상황 | 권장 컬렉션 | 이유 |
|------|-----------|------|
| 순서 있는 리스트, 순회 중심 | `ArrayList` | 캐시 친화적 연속 메모리 |
| 빠른 키 조회 | `HashMap` | O(1) 평균 |
| 정렬 유지 + 범위 검색 | `TreeMap` | 호가창 구현에 적합 |
| 순서 보장 맵 | `LinkedHashMap` | 체결 우선순위 |
| 스레드 안전 카운터 | `ConcurrentHashMap` | 락 없는 세그먼트 잠금 |
| 읽기 多 쓰기 少 | `CopyOnWriteArrayList` | 락 없는 읽기 |

### 5.4 문자열 최적화

```kotlin
// 나쁜 예: 루프에서 String 연결 → O(n²) 메모리
fun buildOrderLog(orders: List<Order>): String {
    var log = ""
    for (order in orders) {
        log += "주문: ${order.id}, " // 매번 새 String 객체
    }
    return log
}

// 좋은 예: StringBuilder 또는 joinToString
fun buildOrderLog(orders: List<Order>): String =
    orders.joinToString(separator = ", ") { "주문: ${it.id}" }
```

---

## 6. DB 튜닝

### 6.1 실행계획(Explain Plan) 분석

```sql
-- PostgreSQL 실행계획 분석
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT o.*, a.name
FROM orders o
JOIN accounts a ON o.account_id = a.id
WHERE o.status = 'PENDING'
  AND o.created_at > NOW() - INTERVAL '1 hour'
ORDER BY o.created_at ASC
LIMIT 100;

-- 확인 포인트:
-- Seq Scan → 인덱스 추가 필요
-- Nested Loop → 대용량 시 Hash Join이 유리
-- cost 높은 노드 → 최적화 우선순위
```

### 6.2 인덱스 전략

```sql
-- 주문 조회 패턴 분석 후 복합 인덱스 설계
-- 조회 패턴: WHERE status = ? AND account_id = ? ORDER BY created_at

-- 나쁜 예: 개별 인덱스 (status 선택도 낮아 비효율)
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_account ON orders(account_id);

-- 좋은 예: 복합 인덱스 (카디널리티 높은 컬럼 먼저)
CREATE INDEX idx_orders_account_status_created
    ON orders(account_id, status, created_at DESC)
    WHERE status IN ('PENDING', 'PARTIAL'); -- 부분 인덱스로 크기 감소
```

### 6.3 슬로우 쿼리 탐지

```yaml
# application.yml — Hibernate 슬로우 쿼리 로깅
spring:
  jpa:
    properties:
      hibernate:
        session:
          events:
            log:
              LOG_QUERIES_SLOWER_THAN_MS: 100  # 100ms 이상 쿼리 로깅
```

```kotlin
// P6Spy로 실제 SQL + 파라미터 + 실행 시간 로깅
// build.gradle.kts
// implementation("com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.9.0")
```

### 6.4 N+1 문제 탐지 및 해결

```kotlin
// 나쁜 예: N+1 발생
val orders = orderRepository.findAll() // SELECT * FROM orders (1번)
orders.forEach { order ->
    val account = accountRepository.findById(order.accountId) // N번 추가 쿼리
    println("${order.id}: ${account.name}")
}

// 좋은 예: JOIN FETCH 또는 @EntityGraph
@Query("SELECT o FROM Order o JOIN FETCH o.account WHERE o.status = :status")
fun findWithAccount(@Param("status") status: OrderStatus): List<Order>

// 또는 Batch Size 설정
@BatchSize(size = 100)
@ManyToOne(fetch = FetchType.LAZY)
val account: Account
```

### 6.5 커넥션 풀(HikariCP) 사이징

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20        # CPU 코어 수 × 2 + 디스크 수 (공식)
      minimum-idle: 10             # 최소 유지 커넥션
      connection-timeout: 3000     # 3초 이내 커넥션 획득 못하면 예외
      idle-timeout: 600000         # 10분 유휴 시 반납
      max-lifetime: 1800000        # 30분 최대 수명
      leak-detection-threshold: 5000  # 5초 이상 미반납 시 경고 로그
```

> **커넥션 풀 사이징 공식 (HikariCP 권장)**:
> `pool_size = (core_count × 2) + effective_spindle_count`
> SSD 기준 core_count × 2가 일반적 시작점.
> 무조건 크게 잡으면 DB 서버 스레드 경합이 오히려 증가한다.

---

## 7. 캐시 활용 (Redis)

### 7.1 캐시 적용 패턴

```kotlin
@Service
class MarketDataService(
    private val redis: RedisTemplate<String, Any>,
    private val marketDataRepository: MarketDataRepository
) {
    companion object {
        const val CACHE_TTL_SECONDS = 1L // 시세는 1초 TTL
        const val CACHE_KEY_PREFIX = "market:price:"
    }

    fun getLatestPrice(symbol: String): PriceData {
        val cacheKey = "$CACHE_KEY_PREFIX$symbol"

        // Cache-Aside 패턴
        val cached = redis.opsForValue().get(cacheKey) as? PriceData
        if (cached != null) return cached

        val data = marketDataRepository.findLatestBySymbol(symbol)
        redis.opsForValue().set(cacheKey, data, CACHE_TTL_SECONDS, TimeUnit.SECONDS)
        return data
    }
}
```

### 7.2 캐시 히트율 모니터링

```kotlin
@Component
class CacheMetrics(registry: MeterRegistry) {
    private val hitCounter = registry.counter("cache.hit.total", "cache", "market-price")
    private val missCounter = registry.counter("cache.miss.total", "cache", "market-price")

    fun recordHit() = hitCounter.increment()
    fun recordMiss() = missCounter.increment()
}
// 목표: 캐시 히트율 > 95%
```

---

## 8. 비동기화

### 8.1 코루틴 기반 비동기 처리

```kotlin
@Service
class OrderProcessingService(
    private val riskService: RiskService,
    private val positionService: PositionService,
    private val notificationService: NotificationService
) {
    suspend fun processOrder(order: Order): OrderResult = coroutineScope {
        // 리스크 체크와 포지션 조회를 병렬 실행
        val riskDeferred = async { riskService.check(order) }
        val positionDeferred = async { positionService.getPosition(order.accountId) }

        val riskResult = riskDeferred.await()
        val position = positionDeferred.await()

        if (!riskResult.approved) throw RiskRejectionException(riskResult.reason)

        val result = executeOrder(order, position)

        // 체결 통보는 비동기 (주문 응답 지연에 영향 없음)
        launch { notificationService.notify(result) }

        result
    }
}
```

### 8.2 비동기화가 성능을 개선하지 않는 경우

> **실무 함정**: 단일 스레드 I/O 바운드 작업에서는 코루틴이 성능을 개선하지만, CPU 바운드 작업에서는 오히려 컨텍스트 스위칭 오버헤드가 추가된다. 먼저 측정하라.

---

## 9. 성능 튜닝 체크리스트

### 측정 단계
- [ ] Gatling/JMeter로 기준선(Baseline) 성능 측정
- [ ] Grafana에서 P50/P95/P99 확인
- [ ] 슬로우 쿼리 로그 수집
- [ ] JFR/async-profiler로 핫패스 특정

### DB 최적화
- [ ] 실행계획(EXPLAIN ANALYZE) 확인
- [ ] 슬로우 쿼리 인덱스 추가
- [ ] N+1 쿼리 제거 (JOIN FETCH / @BatchSize)
- [ ] 커넥션 풀 사이징 적정성 확인

### 애플리케이션 최적화
- [ ] 불필요한 객체 생성 제거
- [ ] 캐시 히트율 > 95% 확인
- [ ] 병렬 처리 가능 구간 비동기화
- [ ] BigDecimal 핫패스 Long 대체 검토

### JVM 최적화
- [ ] GC 로그 분석 (Full GC 없음 확인)
- [ ] 힙 사이즈 적정성 확인
- [ ] ZGC 적용 여부 검토 (P99 지연 민감 서비스)

### 검증
- [ ] 최적화 후 부하 테스트 재실행
- [ ] 기준선 대비 개선율 측정
- [ ] 성능 회귀 테스트 CI 추가

---

이전: [42. 관측성](42-observability.md) · 다음: [44. 보안](44-security.md) · [전체 커리큘럼](../CURRICULUM.md)
