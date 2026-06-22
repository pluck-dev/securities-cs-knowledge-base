# 40. 캐시 전략과 Redis

캐시(Cache)는 느린 저장소 앞에 빠른 저장소를 두어 응답 속도를 높이는 기법이다.
여의도 시스템에서 종목 시세·호가창·순위는 초당 수천 건 조회되지만, PostgreSQL에 매번 질의하면 DB가 곧 병목이 된다.
이 문서는 캐시가 필요한 이유부터 Redis 자료구조, 패턴, 함정, 코틀린 통합까지 심층적으로 다룬다.

---

## 1. 캐시가 왜 필요한가

### 1.1 지역성(Locality) 원리

| 지역성 종류 | 설명 | 캐시 적용 예 |
|---|---|---|
| **Temporal Locality** | 최근 사용된 데이터가 다시 사용될 가능성이 높다 | 자주 조회되는 종목(삼성전자, SK하이닉스) |
| **Spatial Locality** | 인접한 데이터가 함께 사용될 가능성이 높다 | 호가창 1~5호가를 묶어 캐싱 |

대부분의 트래픽은 소수의 데이터에 집중된다(파레토 법칙 80/20).
상위 100개 종목이 전체 조회의 80%를 차지한다면, 그 100개만 캐싱해도 DB 부하를 극적으로 줄인다.

### 1.2 DB 병목 시나리오

```
[클라이언트 10,000 TPS]
       │
       ▼
[Spring 앱 서버 x 4]
       │  ← 쿼리당 평균 5ms, Connection Pool 200
       ▼
[PostgreSQL]  ← 최대 처리량 ~2,000 TPS → 병목!
```

캐시 히트율(Hit Rate) 90%를 달성하면 실제 DB 도달 TPS는 1,000으로 줄어든다.

---

## 2. 캐시 계층 구조

```
[요청]
  │
  ├─ L1/L2/L3 CPU 캐시 (ns 단위, OS/HW 관리)
  │
  ├─ JVM 힙 (로컬 객체, GC 대상)
  │
  ├─ 로컬 캐시 — Caffeine (ms 단위, 프로세스 내)
  │     ・ 네트워크 왕복 없음
  │     ・ 인스턴스마다 별도 복사본 → 일관성 문제
  │
  ├─ 분산 캐시 — Redis (ms~수십 ms, 네트워크 왕복)
  │     ・ 모든 앱 인스턴스가 공유
  │     ・ 장애 시 단일 장애점(SPOF) 위험 → Sentinel/Cluster 구성
  │
  └─ 원본 저장소 — PostgreSQL (ms~초 단위)
```

**Two-Level Cache 패턴**: Caffeine(L1) + Redis(L2)를 조합하면 네트워크 왕복을 줄이면서 인스턴스 간 일관성도 유지할 수 있다.
단, L1 캐시 무효화 메시지를 Redis Pub/Sub 또는 메시지 브로커로 전파해야 한다.

---

## 3. Redis 기초 자료구조

| 자료구조 | 명령어 예 | 여의도 활용 사례 |
|---|---|---|
| **String** | `SET key value EX 60` | 단순 종목 정보, 세션 토큰 |
| **Hash** | `HSET stock:005930 price 70000 volume 1200000` | 종목 스냅샷 (필드별 개별 갱신) |
| **List** | `LPUSH news:005930 "..."` | 종목 최신 뉴스 큐 |
| **Sorted Set** | `ZADD rank:change -3.5 005930` | 등락률 순위, 호가창 |
| **Pub/Sub** | `PUBLISH price-update "..."` | 실시간 시세 브로드캐스트 |
| **Stream** | `XADD trade-stream * ...` | 체결 이력 이벤트 소싱 |

> Stream은 15장(이벤트 드리븐 아키텍처)에서 다룬 Kafka의 경량 대안이다.
> 처리량이 낮고 영속성 요구가 단순할 때 적합하다.

---

## 4. 캐시 패턴

### 4.1 Cache-Aside (Lazy Loading)

```
읽기: 앱 → Redis Miss → DB 조회 → Redis Set → 응답
쓰기: 앱 → DB Update → Redis Delete (Invalidation)
```

```kotlin
@Service
class StockQueryService(
    private val redis: ReactiveRedisTemplate<String, StockSnapshot>,
    private val stockRepository: StockRepository,
) {
    suspend fun getSnapshot(ticker: String): StockSnapshot {
        val key = "stock:$ticker"
        return redis.opsForValue().get(key).awaitFirstOrNull()
            ?: stockRepository.findByTicker(ticker)
                .also { snapshot ->
                    redis.opsForValue()
                        .set(key, snapshot, Duration.ofSeconds(10))
                        .awaitSingleOrNull()
                }
    }
}
```

**장점**: 구현이 단순하다. 실제로 읽히는 데이터만 캐싱된다.
**단점**: 첫 요청(Cold Miss)은 항상 느리다. Write 후 Delete를 빠뜨리면 Stale Read 발생.
**적합한 경우**: 읽기 빈도가 높고 쓰기 빈도가 낮은 데이터(종목 기본 정보).

---

### 4.2 Read-Through

캐시 레이어가 DB 조회 책임을 대신 진다. 앱은 캐시만 본다.

```
앱 → 캐시 라이브러리(Miss 시 DB 자동 조회) → 응답
```

스프링의 `@Cacheable`이 이 패턴을 추상화한다.

```kotlin
@Service
class StockInfoService(private val stockRepository: StockRepository) {

    @Cacheable(
        cacheNames = ["stockInfo"],
        key = "#ticker",
        unless = "#result == null",
    )
    fun getStockInfo(ticker: String): StockInfo? =
        stockRepository.findInfoByTicker(ticker)
}
```

`application.yml`:
```yaml
spring:
  cache:
    type: redis
  data:
    redis:
      host: redis-cluster.internal
      port: 6379
  cache:
    redis:
      time-to-live: 60s
      key-prefix: "yeouido:"
      use-key-prefix: true
```

**장점**: 앱 코드에서 캐시 로직이 분리된다.
**단점**: 캐시 미스 시 지연이 숨겨진다. 커스텀 TTL·직렬화 제어가 어렵다.

---

### 4.3 Write-Through

쓰기 시 DB와 캐시를 동시에 갱신한다.

```
앱 → DB Write + Redis Set (원자적이지 않음!) → 응답
```

```kotlin
@CachePut(cacheNames = ["stockInfo"], key = "#stockInfo.ticker")
fun updateStockInfo(stockInfo: StockInfo): StockInfo {
    return stockRepository.save(stockInfo)
}
```

**장점**: 캐시가 항상 최신 상태다. Cache-Aside의 Cold Miss 문제가 없다.
**단점**: 쓰기 지연이 증가한다. DB와 캐시 간 원자성이 보장되지 않는다(DB 성공 후 Redis 실패 시 불일치).
**적합한 경우**: 쓰기 직후 바로 읽히는 데이터(프로필, 설정).

---

### 4.4 Write-Behind (Write-Back)

쓰기를 캐시에만 먼저 반영하고, 비동기로 DB에 플러시한다.

```
앱 → Redis Set → 응답 (빠름!)
         └→ [비동기 워커] → DB Write
```

**장점**: 쓰기 응답 속도가 가장 빠르다. DB 쓰기를 배치로 묶을 수 있다.
**단점**: Redis 장애 시 미플러시 데이터 유실. 구현 복잡도가 높다.
**적합한 경우**: 조회수·좋아요 등 손실이 어느 정도 허용되는 카운터.
**금지 사례**: 주문·잔고·원장(Ledger). 유실 불가.

---

### 패턴 비교표

| 패턴 | 읽기 성능 | 쓰기 성능 | 일관성 | 구현 복잡도 |
|---|---|---|---|---|
| Cache-Aside | ★★★★ (Miss 후 저하) | ★★★ | 중 | 낮음 |
| Read-Through | ★★★★ | ★★★ | 중 | 낮음 |
| Write-Through | ★★★★★ | ★★ | 높음 | 중 |
| Write-Behind | ★★★★★ | ★★★★★ | 낮음 | 높음 |

---

## 5. 캐시 무효화(Cache Invalidation)

> *"There are only two hard things in Computer Science: cache invalidation and naming things."*
> — Phil Karlton

### 5.1 무효화 전략

| 전략 | 방법 | 특징 |
|---|---|---|
| **TTL 기반** | 만료 시간 설정 | 단순하지만 Stale 윈도우 존재 |
| **이벤트 기반** | 데이터 변경 시 즉시 Delete | 일관성 높음, 이벤트 유실 위험 |
| **버전 기반** | 키에 버전 포함 (`stock:005930:v42`) | 롤백 용이, 공간 낭비 |
| **Tag 기반** | 관련 키를 태그로 묶어 일괄 무효화 | 복잡하지만 유연 |

### 5.2 이벤트 기반 무효화 흐름

```
[DB 업데이트]
     │
     ▼
[Debezium CDC] → [Kafka: stock-updated]
                        │
              ┌─────────┤
              ▼         ▼
         [앱 서버1]  [앱 서버2]
         Redis DEL  Redis DEL
```

CDC(Change Data Capture)를 활용하면 애플리케이션 코드 변경 없이 무효화할 수 있다(8장 아키텍처 참조).

---

## 6. TTL 설계 전략

TTL이 너무 짧으면 히트율이 낮고, 너무 길면 Stale 데이터 위험이 높다.

| 데이터 종류 | 권장 TTL | 이유 |
|---|---|---|
| 실시간 시세 | 1~3초 | 빠른 변동 |
| 호가창 | 500ms~1초 | 체결 시 즉시 변경 |
| 등락률 순위 | 5~10초 | 일부 오차 허용 가능 |
| 종목 기본 정보 | 1시간 | 거의 변하지 않음 |
| 공시 목록 | 5분 | 새 공시 반영 지연 허용 |
| 세션 토큰 | 30분 (Sliding) | 활동 시 갱신 |

**Sliding TTL**: 접근할 때마다 TTL을 리셋한다. Redis에서는 `GET` 후 `EXPIRE`를 수동으로 호출해야 한다.

---

## 7. 캐시 스탬피드(Cache Stampede / Thundering Herd)

### 7.1 문제

TTL이 만료되는 순간 수천 개의 요청이 동시에 DB로 쏟아지는 현상.

```
T=0   Redis TTL 만료
T=0+ε 요청 1~5000이 동시에 Cache Miss → DB 쿼리 5000개 → DB 다운
```

### 7.2 해결책 1: Mutex Lock (분산 락)

```kotlin
suspend fun getWithMutex(ticker: String): StockSnapshot {
    val key = "stock:$ticker"
    val lockKey = "lock:$key"

    redis.opsForValue().get(key).awaitFirstOrNull()?.let { return it }

    // 락 획득 시도 (NX: 없을 때만 SET, PX: 밀리초 TTL)
    val acquired = redis.opsForValue()
        .setIfAbsent(lockKey, "1", Duration.ofMillis(500))
        .awaitFirst()

    return if (acquired == true) {
        try {
            stockRepository.findByTicker(ticker).also { snapshot ->
                redis.opsForValue().set(key, snapshot, Duration.ofSeconds(10)).awaitSingle()
            }
        } finally {
            redis.delete(lockKey).awaitSingle()
        }
    } else {
        // 락을 못 얻으면 잠시 대기 후 재시도 (캐시 읽힐 때까지)
        delay(50)
        getWithMutex(ticker)
    }
}
```

**단점**: 락 소유자가 죽으면 TTL 만료까지 대기.

### 7.3 해결책 2: Probabilistic Early Expiration (PER)

TTL이 완전히 만료되기 전에 확률적으로 미리 재갱신한다.

```kotlin
fun shouldRevalidate(ttlRemaining: Duration, beta: Double = 1.0): Boolean {
    val revalidationWindow = ttlRemaining.toMillis() * beta * Math.random()
    return revalidationWindow <= 0
}
```

### 7.4 해결책 3: TTL Jitter

동일한 TTL을 사용하면 모든 키가 동시에 만료된다. 무작위 편차(Jitter)를 추가한다.

```kotlin
fun jitteredTtl(baseTtl: Duration, jitterFactor: Double = 0.1): Duration {
    val jitter = (baseTtl.toMillis() * jitterFactor * Math.random()).toLong()
    return baseTtl.plusMillis(jitter)
}

// 사용
redis.opsForValue().set(key, value, jitteredTtl(Duration.ofSeconds(60)))
```

---

## 8. 핫키(Hot Key) 문제

### 8.1 문제

Redis는 단일 키에 대한 요청이 특정 노드에 집중되면 그 노드가 병목이 된다.
시장 개장 시 삼성전자(005930) 조회가 초당 수만 건에 달할 수 있다.

### 8.2 해결책 1: Local Cache Layer

```kotlin
@Service
class HotStockService(
    private val localCache: LoadingCache<String, StockSnapshot>,  // Caffeine
    private val redis: ReactiveRedisTemplate<String, StockSnapshot>,
) {
    suspend fun get(ticker: String): StockSnapshot =
        localCache.get(ticker) { key ->
            runBlocking {
                redis.opsForValue().get("stock:$key").awaitFirstOrNull()
                    ?: throw CacheMissException(key)
            }
        }
}
```

Caffeine 설정:
```kotlin
@Bean
fun stockLocalCache(): LoadingCache<String, StockSnapshot> =
    Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofSeconds(1))  // 1초 허용 오차
        .build { /* 실제 로딩은 서비스에서 */ throw UnsupportedOperationException() }
```

### 8.3 해결책 2: Key Sharding

핫키를 여러 샤드로 분산시킨다.

```kotlin
fun getShardKey(ticker: String, shards: Int = 10): String {
    val shard = (ticker.hashCode() and Int.MAX_VALUE) % shards
    return "stock:$ticker:$shard"
}

suspend fun getHotKey(ticker: String): StockSnapshot {
    val key = getShardKey(ticker, shards = 10)
    return redis.opsForValue().get(key).awaitFirstOrNull()
        ?: fetchAndCache(ticker)
}
```

---

## 9. 시세 캐싱 설계: Sorted Set 활용

### 9.1 호가창(Order Book) — Sorted Set

Score를 가격으로 사용하면 호가 순서가 자동 정렬된다.

```kotlin
// 매도 호가 저장 (낮은 가격이 우선)
suspend fun setAskOrders(ticker: String, orders: List<OrderLevel>) {
    val key = "orderbook:ask:$ticker"
    val tuples = orders.map { DefaultTypedTuple(it.quantity.toString(), it.price.toDouble()) }.toSet()
    redis.opsForZSet().add(key, tuples).awaitSingle()
    redis.expire(key, Duration.ofSeconds(2)).awaitSingle()
}

// 최우선 매도 5호가 조회
suspend fun getTopAsks(ticker: String, depth: Int = 5): List<OrderLevel> {
    val key = "orderbook:ask:$ticker"
    return redis.opsForZSet()
        .rangeWithScores(key, Range.closed(0L, depth.toLong() - 1))
        .collectList()
        .awaitSingle()
        .map { tuple ->
            OrderLevel(
                price = BigDecimal.valueOf(tuple.score!!),
                quantity = tuple.value!!.toLong(),
            )
        }
}
```

### 9.2 등락률 순위 — Sorted Set

```kotlin
data class StockRank(val ticker: String, val changeRate: BigDecimal)

// 등락률 업데이트 (음수 허용)
suspend fun updateChangeRate(ticker: String, changeRate: BigDecimal) {
    redis.opsForZSet()
        .add("rank:change", ticker, changeRate.toDouble())
        .awaitSingle()
}

// 상승률 TOP 10
suspend fun getTopGainers(limit: Int = 10): List<StockRank> {
    return redis.opsForZSet()
        .reverseRangeWithScores("rank:change", Range.closed(0L, limit.toLong() - 1))
        .collectList()
        .awaitSingle()
        .map { StockRank(it.value!!, BigDecimal.valueOf(it.score!!)) }
}

// 하락률 TOP 10 (낮은 score = 큰 하락)
suspend fun getTopLosers(limit: Int = 10): List<StockRank> {
    return redis.opsForZSet()
        .rangeWithScores("rank:change", Range.closed(0L, limit.toLong() - 1))
        .collectList()
        .awaitSingle()
        .map { StockRank(it.value!!, BigDecimal.valueOf(it.score!!)) }
}
```

### 9.3 세션 캐싱

```kotlin
@Service
class SessionCacheService(
    private val redis: ReactiveRedisTemplate<String, UserSession>,
) {
    private fun sessionKey(token: String) = "session:$token"

    suspend fun save(token: String, session: UserSession) {
        redis.opsForValue()
            .set(sessionKey(token), session, Duration.ofMinutes(30))
            .awaitSingle()
    }

    suspend fun get(token: String): UserSession? =
        redis.opsForValue().get(sessionKey(token)).awaitFirstOrNull()
            ?.also { touchExpiry(token) }  // Sliding TTL

    private suspend fun touchExpiry(token: String) {
        redis.expire(sessionKey(token), Duration.ofMinutes(30)).awaitSingle()
    }

    suspend fun invalidate(token: String) {
        redis.delete(sessionKey(token)).awaitSingle()
    }
}
```

---

## 10. 분산 락(Distributed Lock): Redlock

단일 Redis 노드 락은 노드 장애 시 락이 유실된다. Redlock은 N개의 독립 Redis 인스턴스에 과반수 획득을 요구한다.

```
N = 5 Redis 인스턴스
과반수 = 3개 이상 동시 획득 시 락 성공
```

```kotlin
// Redisson 라이브러리 사용 예
@Service
class DistributedLockService(private val redissonClient: RedissonClient) {

    suspend fun <T> withLock(key: String, ttl: Duration, block: suspend () -> T): T {
        val lock = redissonClient.getLock("dlock:$key")
        val acquired = lock.tryLockAsync(0, ttl.toMillis(), TimeUnit.MILLISECONDS)
            .await()

        check(acquired) { "락 획득 실패: $key" }

        return try {
            block()
        } finally {
            lock.unlockAsync().await()
        }
    }
}
```

**Redlock 주의점**:
- 시계 드리프트(Clock Drift)가 크면 TTL 계산이 틀어질 수 있다.
- GC 일시 정지(STW Pause) 동안 락 TTL이 만료될 수 있다.
- 최강 일관성이 필요하면 ZooKeeper Fencing Token 패턴을 고려한다.
- Martin Kleppmann의 Redlock 비판 글을 반드시 읽을 것.

---

## 11. 일관성 vs 성능 트레이드오프

```
강한 일관성 ◄──────────────────────────────► 높은 성능
     │                                           │
  원장(Ledger)     등락률 순위        실시간 시세
  주문 잔고        공시 목록           방문자 수
  체결 내역        세션 정보           핫 종목 캐시
     │                                           │
  캐시 금지      TTL 짧게 유지       TTL 1~3초 허용
```

**Stale Read를 허용할 수 있는 기준**:
1. 사용자가 오차를 인지하지 못하는가?
2. 오차로 인한 금전적 손실이 없는가?
3. 이벤트 기반으로 즉시 보정되는가?

**등락률 순위**: 5초 오차는 사용자가 눈치채지 못하고 금전적 손실도 없다 → TTL 10초 허용.

**주문 가능 잔고**: 1원이라도 틀리면 과잉 주문이 발생한다 → **캐시 금지, DB 직접 조회**.

---

## 12. 캐시가 부적합한 경우

| 데이터 | 이유 | 올바른 접근 |
|---|---|---|
| 주문 원장(Ledger) | 정확한 잔고 계산 필요 | DB 트랜잭션 + 낙관적 락 |
| 체결 내역 | 감사(Audit) 추적 필요 | 이벤트 소싱 + 불변 저장소 |
| 결제 상태 | 이중 결제 방지 | DB 유니크 제약 + 멱등성 키 |
| 사용자 인증 상태 | 강제 로그아웃 즉시 반영 | 세션 저장소(짧은 TTL) + 블랙리스트 |

> "캐시는 DB의 보조 수단이지 대체 수단이 아니다."
> 원장 데이터를 캐시에서 읽으면 Double Spend, 과잉 주문, 규제 위반이 발생한다.

---

## 13. 운영 체크리스트

### 캐시 설계 체크리스트

- [ ] 캐시 히트율 목표를 설정했는가? (예: 90% 이상)
- [ ] TTL은 데이터 변경 빈도와 허용 오차를 기반으로 설정했는가?
- [ ] TTL Jitter를 적용해 동시 만료를 방지했는가?
- [ ] Cache Stampede 방지 전략(Mutex / PER / Jitter)을 선택했는가?
- [ ] 핫키 후보를 식별하고 Local Cache Layer를 적용했는가?
- [ ] 캐시 무효화 이벤트가 유실되었을 때 복구 전략이 있는가?
- [ ] 원장·잔고 데이터가 캐시를 우회하는지 확인했는가?
- [ ] Redis Sentinel 또는 Cluster로 고가용성을 구성했는가?
- [ ] 직렬화 형식(JSON vs MessagePack vs Protobuf)을 결정하고 버전 호환성을 고려했는가?
- [ ] 메모리 정책(`maxmemory-policy`)을 `allkeys-lru` 또는 `volatile-lru`로 설정했는가?

### 모니터링 지표

| 지표 | 경고 임계값 | 의미 |
|---|---|---|
| Cache Hit Rate | < 80% | 캐시 효과 미미 |
| Eviction Rate | 급증 | 메모리 부족 |
| Connected Clients | > 1000 | 연결 풀 고갈 |
| Blocked Clients | > 0 | BLPOP 등 블로킹 명령 대기 |
| Replication Lag | > 100ms | 복제 지연 |

---

## 14. 함정(Pitfall) 모음

**함정 1: Cache Penetration (캐시 관통)**
존재하지 않는 키를 반복 조회하면 매번 DB까지 도달한다.
→ Null 값도 짧은 TTL로 캐싱하거나, Bloom Filter로 사전 차단.

**함정 2: 직렬화 버전 불일치**
클래스 필드를 추가했는데 캐시에 구버전이 남아 역직렬화 오류 발생.
→ 클래스 버전 기반 키 접두사 사용, 배포 시 캐시 플러시 절차 마련.

**함정 3: 긴 파이프라인에서의 트랜잭션 착각**
Redis `MULTI/EXEC`는 원자성을 보장하지만, 조건 분기(IF)는 불가. Lua 스크립트를 사용할 것.

**함정 4: 분산 락을 DB 트랜잭션 대체로 사용**
Redis 락 + DB 쓰기는 원자적이지 않다. 락 해제 후 DB 오류가 나면 데이터 불일치.
→ 이런 경우 DB 트랜잭션 + 낙관적 락(Optimistic Lock)이 더 안전하다.

**함정 5: BigDecimal 직렬화**
`BigDecimal`을 JSON으로 직렬화하면 부동소수점 오차가 발생할 수 있다.
→ Jackson의 `@JsonSerialize(using = ToStringSerializer::class)`로 문자열로 저장.

```kotlin
data class StockSnapshot(
    val ticker: String,
    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = BigDecimalDeserializer::class)
    val price: BigDecimal,
    val timestamp: Instant,
)
```

---

## 15. 아키텍처 요약

```
[웹소켓 클라이언트]
        │
[Spring WebFlux / Coroutine]
        │
   ┌────┴────┐
   │  L1     │ Caffeine (JVM 내, TTL 1초)
   └────┬────┘
        │ Miss
   ┌────┴────┐
   │  L2     │ Redis Cluster (TTL 3~10초)
   └────┬────┘
        │ Miss
   ┌────┴────┐
   │ Origin  │ PostgreSQL (SELECT + Index)
   └─────────┘
        │
   [Debezium CDC] → [Kafka: stock-updated]
        │
   [Cache Invalidator] → Redis DEL → Caffeine Evict (Pub/Sub)
```

이 구조는 8장의 계층형 아키텍처, 15장의 이벤트 드리븐 무효화와 연동된다.
다음 장(41장)에서는 Redis Sentinel·Cluster 구성, 레플리카 페일오버, Redis 장애 시 Circuit Breaker 전략을 다룬다.

---

이전: [39. 분산 시스템과 일관성](39-distributed-systems.md) · 다음: [41. 고가용성과 장애복구](41-high-availability.md) · [전체 커리큘럼](../CURRICULUM.md)
