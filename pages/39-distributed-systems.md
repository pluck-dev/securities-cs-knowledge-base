# 39. 분산 시스템과 일관성 (Distributed Systems & Consistency)

분산 시스템은 단일 서버의 물리적 한계를 넘기 위해 필수지만, 동시에 "네트워크가 절대 신뢰할 수 없는 존재"라는 냉혹한 현실과 싸워야 한다. 이 장에서는 분산 환경에서 일관성을 유지하는 핵심 이론과 증권 시스템에 적용하는 실전 패턴을 다룬다. 이벤트 소싱/CQRS(38장)와 이벤트 드리븐 아키텍처(15장)의 연장선에서, "데이터를 어떻게 신뢰할 것인가"를 집중적으로 파고든다.

---

## 1. 왜 분산 시스템은 어려운가

단일 프로세스에서는 변수 값이 항상 최신이고, 함수 호출은 성공하거나 실패한다. 분산 환경은 세 가지 근본 문제를 추가한다.

### 1.1 네트워크 부분 실패 (Partial Failure)

```
Client ──→ Service A ──→ Service B
                  ↑
           응답이 안 온다
           (A가 죽었나? 네트워크가 끊겼나? B가 느린가?)
```

**함정:** 타임아웃 후 재시도하면 B에서 이미 처리된 요청이 두 번 실행될 수 있다. Exactly-Once는 허상(illusion)에 가깝고, At-Least-Once + 멱등 수신자가 현실적 해법이다.

### 1.2 시간 불확실성 (Clock Uncertainty)

각 노드의 시계는 NTP(Network Time Protocol)로 동기화하지만 수십 ms 오차가 발생한다. "A 이벤트가 B보다 먼저 발생했다"는 벽시계(wall clock)만으로 보장할 수 없다.

### 1.3 노드 실패 (Node Failure)

노드가 느린 것인지, 죽은 것인지, 응답 패킷만 유실된 것인지 외부에서 구별할 방법이 없다. 이를 **Byzantine 장애**로 일반화하면 더욱 복잡해진다 (증권 시스템에서는 주로 Crash-Stop 장애 모델로 가정).

---

## 2. CAP 정리 (CAP Theorem)

에릭 브루어(Eric Brewer)가 제시하고 길버트·린치가 증명한 정리: **분산 시스템에서 다음 세 가지를 동시에 만족하는 것은 불가능하다.**

```
        Consistency (일관성)
             /\
            /  \
           /    \
          / CA   \
         /--------\
        /  CP  AP  \
       /____________\
Availability   Partition Tolerance
(가용성)         (분할 내성)
```

| 선택 | 포기 | 대표 시스템 | 증권 적용 예 |
|------|------|-------------|--------------|
| CA | P | RDBMS (단일 노드) | 주문 체결 엔진 |
| CP | A | ZooKeeper, etcd, HBase | 분산 락, 메타데이터 |
| AP | C (일시적) | Cassandra, DynamoDB, Kafka Consumers | 시세 캐시, 뉴스 피드 |

> **핵심:** 네트워크 파티션(P)은 피할 수 없다. 따라서 실제 선택은 **CP vs AP**이다. CA는 단일 데이터센터 단일 노드에서만 의미 있다.

---

## 3. 일관성 모델 스펙트럼 (Consistency Models)

```
강함 ◄──────────────────────────────────────────────────► 약함
  │                                                          │
Linearizability  Sequential   Causal   Eventual    Read-Your-Writes
(선형화 가능성)   Consistency  Consistency Consistency
```

### 3.1 선형화 가능성 (Linearizability, Strong Consistency)

모든 연산이 원자적으로 어느 한 시점에 발생한 것처럼 보인다. 분산 데이터베이스에서 가장 강한 보장. 성능 비용이 크다.

**적용:** 주문장부(Order Book) 잔량 차감, 계좌 잔고 차감.

### 3.2 순차적 일관성 (Sequential Consistency)

모든 노드가 동일한 연산 순서를 관찰하지만, 그 순서가 실시간 순서와 일치하지 않을 수 있다.

### 3.3 최종적 일관성 (Eventual Consistency)

충돌 없이 업데이트가 전파되면 결국 모든 노드가 같은 값을 가진다. 응답성이 높지만 일시적 불일치를 허용해야 한다.

**적용:** 종목 시세, 공지사항, 투자자 보고서, 비실시간 잔고 조회.

---

## 4. 분산 트랜잭션의 문제와 2PC의 한계

### 4.1 2PC (Two-Phase Commit) 흐름

```
Coordinator
    │
    ├──(Phase 1: Prepare)──→ Participant A: "커밋 가능한가?"
    │                         Participant B: "커밋 가능한가?"
    │
    ├──(Votes: Yes/Yes)──────────────────────────────
    │
    └──(Phase 2: Commit)───→ Participant A: "커밋하라"
                              Participant B: "커밋하라"
```

### 4.2 2PC의 치명적 한계

| 문제 | 설명 |
|------|------|
| Blocking | Coordinator 장애 시 Participant가 Prepare 상태로 무한 대기, 락 점유 |
| Coordinator SPOF | 단일 코디네이터가 전체 시스템 가용성을 결정 |
| 성능 | 두 번의 네트워크 왕복 + 동기 디스크 쓰기 |
| 장거리 지연 | 멀티 리전에서 수백 ms 지연 발생 |

> **결론:** 마이크로서비스 환경에서 2PC는 안티패턴에 가깝다. 서비스 간 DB를 공유하거나, 아래의 사가(Saga) 패턴으로 대체한다.

---

## 5. 사가 패턴 (Saga Pattern)

사가는 긴 비즈니스 트랜잭션을 **로컬 트랜잭션의 시퀀스**로 분해하고, 실패 시 **보상 트랜잭션(Compensating Transaction)**으로 롤백한다.

### 5.1 오케스트레이션 vs 코레오그래피 비교

```
오케스트레이션 (Orchestration)          코레오그래피 (Choreography)
─────────────────────────────          ────────────────────────────
         Orchestrator                  Service A → Event → Service B
         /    |    \                              → Event → Service C
        /     |     \                  (중앙 조율자 없음, 이벤트로 연결)
   Order   Payment  Ledger
   Service  Service  Service
```

| 기준 | 오케스트레이션 | 코레오그래피 |
|------|--------------|-------------|
| 중앙 조율 | O (Orchestrator) | X |
| 가시성 | 높음 (한 곳에서 흐름 파악) | 낮음 (흐름이 분산됨) |
| 결합도 | Orchestrator ↔ 각 서비스 | 서비스 간 이벤트 계약 |
| 복잡도 | Orchestrator가 SPoF 위험 | 분산 디버깅 어려움 |
| 증권 적합성 | 주문 처리 (명확한 순서) | 시세 전파, 알림 |

### 5.2 주문-결제-원장 사가 예제

#### OrderSaga (Orchestrator)

```kotlin
@Component
class OrderSaga(
    private val paymentClient: PaymentServiceClient,
    private val ledgerClient: LedgerServiceClient,
    private val orderRepository: OrderRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 사가 상태: STARTED → PAYMENT_RESERVED → LEDGER_RECORDED → COMPLETED
    //                                      ↘ COMPENSATING → FAILED
    fun execute(command: PlaceOrderCommand): SagaResult {
        val sagaId = UUID.randomUUID().toString()
        log.info("사가 시작: sagaId={}, orderId={}", sagaId, command.orderId)

        // Step 1: 주문 생성 (로컬 트랜잭션)
        val order = orderRepository.saveWithStatus(
            orderId = command.orderId,
            amount = command.amount,     // BigDecimal
            status = OrderStatus.PENDING,
            sagaId = sagaId,
        )

        // Step 2: 결제 예약
        val paymentResult = runCatching {
            paymentClient.reserve(
                ReservePaymentRequest(
                    sagaId = sagaId,
                    orderId = command.orderId,
                    amount = command.amount,
                    accountId = command.accountId,
                )
            )
        }.getOrElse { ex ->
            log.error("결제 예약 실패: {}", ex.message)
            compensateOrder(sagaId, order.id)
            return SagaResult.Failure(sagaId, "결제 예약 실패")
        }

        // Step 3: 원장 기록
        val ledgerResult = runCatching {
            ledgerClient.record(
                RecordLedgerRequest(
                    sagaId = sagaId,
                    orderId = command.orderId,
                    amount = command.amount,
                    type = LedgerType.DEBIT,
                )
            )
        }.getOrElse { ex ->
            log.error("원장 기록 실패: {}", ex.message)
            compensatePayment(sagaId, paymentResult.reservationId)
            compensateOrder(sagaId, order.id)
            return SagaResult.Failure(sagaId, "원장 기록 실패")
        }

        // 완료 처리
        orderRepository.updateStatus(order.id, OrderStatus.COMPLETED)
        return SagaResult.Success(sagaId)
    }

    // 보상 트랜잭션: 역순으로 실행
    private fun compensateOrder(sagaId: String, orderId: Long) {
        orderRepository.updateStatus(orderId, OrderStatus.CANCELLED)
        log.info("보상: 주문 취소 sagaId={}", sagaId)
    }

    private fun compensatePayment(sagaId: String, reservationId: String) {
        runCatching { paymentClient.cancel(CancelPaymentRequest(sagaId, reservationId)) }
            .onFailure { log.error("보상 결제 취소 실패 (수동 개입 필요): sagaId={}", sagaId) }
    }
}
```

#### PaymentSaga (Participant, 멱등 처리 포함)

```kotlin
@Service
class PaymentSagaService(
    private val paymentRepository: PaymentRepository,
    private val idempotencyStore: IdempotencyStore,
) {
    fun reserve(request: ReservePaymentRequest): ReservationResult {
        // 멱등성 키: sagaId + 작업명
        val idempotencyKey = "${request.sagaId}:reserve"

        return idempotencyStore.getOrExecute(idempotencyKey) {
            val balance = paymentRepository.findBalanceByAccount(request.accountId)
            if (balance < request.amount) {
                throw InsufficientBalanceException("잔고 부족: balance=$balance, required=${request.amount}")
            }
            paymentRepository.reserveAmount(
                accountId = request.accountId,
                amount = request.amount,      // BigDecimal
                sagaId = request.sagaId,
            )
        }
    }
}
```

### 5.3 보상 트랜잭션 설계 원칙

- 보상은 **취소가 아닌 역연산(semantic undo)**이다. 이미 발생한 사실을 숨기지 않는다.
- 보상 자체도 실패할 수 있다. **Dead Letter Queue + 수동 개입 프로세스**를 반드시 설계한다.
- 보상 불가능한 연산(예: 이메일 발송)은 사가 맨 마지막에 배치한다.

---

## 6. 멱등성과 Exactly-Once의 현실

### 6.1 Exactly-Once는 허상인가?

네트워크 수준에서 Exactly-Once 배달을 보장하는 것은 불가능하다. 현실적 접근법:

```
At-Least-Once 배달  +  멱등 수신자  =  Effectively Exactly-Once
```

### 6.2 멱등성 구현 패턴

```kotlin
@Service
class IdempotencyStore(private val redisTemplate: StringRedisTemplate) {

    fun <T> getOrExecute(key: String, ttl: Duration = Duration.ofHours(24), block: () -> T): T {
        val cached = redisTemplate.opsForValue().get("idempotency:$key")
        if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            return objectMapper.readValue(cached, Any::class.java) as T
        }

        val result = block()
        redisTemplate.opsForValue().set(
            "idempotency:$key",
            objectMapper.writeValueAsString(result),
            ttl,
        )
        return result
    }
}
```

### 6.3 Kafka Idempotent Producer 설정 (Spring Boot)

```yaml
spring:
  kafka:
    producer:
      properties:
        enable.idempotence: true          # Producer 중복 방지
        acks: all                         # 모든 ISR 확인
        retries: 2147483647
        max.in.flight.requests.per.connection: 5
    consumer:
      properties:
        isolation.level: read_committed   # 커밋된 메시지만 소비
```

> **함정:** Kafka의 Exactly-Once Semantics(EOS)는 동일 클러스터 내 토픽 간에만 보장된다. 외부 DB 쓰기와 Kafka 발행의 원자성은 Transactional Outbox 패턴(38장 연계)으로 별도 해결해야 한다.

---

## 7. 분산 락 (Distributed Lock)

### 7.1 Redis 분산 락 (Redlock)

```kotlin
@Component
class RedisDistributedLock(private val redisTemplate: StringRedisTemplate) {

    fun <T> withLock(key: String, ttl: Duration = Duration.ofSeconds(30), block: () -> T): T {
        val lockKey = "lock:$key"
        val lockValue = UUID.randomUUID().toString()

        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, ttl)
            ?: false

        if (!acquired) throw LockAcquisitionException("락 획득 실패: $key")

        return try {
            block()
        } finally {
            // Lua 스크립트로 원자적 해제 (자신의 락만 해제)
            val script = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
            """.trimIndent()
            redisTemplate.execute(
                RedisScript.of(script, Long::class.java),
                listOf(lockKey),
                lockValue,
            )
        }
    }
}
```

### 7.2 분산 락의 위험

```
위험 1: Clock Skew
Node A의 락 TTL = 30s, 실제 클럭이 빠르면 29s만에 만료 → 다른 노드가 동시 획득

위험 2: GC 일시 정지 (Stop-The-World)
락 보유 중 GC 발생 → 처리가 멈춘 동안 TTL 만료 → 다른 노드가 락 획득 → 두 노드 동시 처리

위험 3: Network Partition
락 서버와 클라이언트 분리 → 락 없이 진행하거나 무한 대기
```

**대안:** ZooKeeper의 **Ephemeral Sequential Node** 방식은 세션 종료 시 자동 락 해제로 Clock Skew 문제를 부분 해결한다. 고가용성이 필요하면 etcd의 lease 기반 락을 고려한다.

> **증권 시스템에서의 원칙:** 분산 락은 최후의 수단이다. 잔고 차감은 DB 수준 낙관적 락(Optimistic Lock) + 버전 관리, 또는 단일 파티션 내 직렬화로 처리하는 것이 훨씬 안전하다.

---

## 8. 분산 ID 생성 (Distributed ID Generation)

| 방식 | 구조 | 정렬 가능 | 충돌 위험 | 크기 | 증권 적합성 |
|------|------|-----------|-----------|------|------------|
| UUID v4 | 128bit 랜덤 | X | 매우 낮음 | 36자(문자열) | 로그, 사가 ID |
| UUID v7 | 시간 + 랜덤 | O (시간순) | 매우 낮음 | 36자(문자열) | 이벤트 ID |
| Snowflake | 시간+노드+시퀀스 | O | 노드ID 충돌 가능 | 64bit Long | 주문 ID, 거래 ID |
| ULID | 시간+랜덤 | O | 매우 낮음 | 26자(문자열) | 범용 |

### 8.1 Snowflake ID 구조

```
63      62    22      12       0
 │       │     │       │       │
 0  [timestamp][node_id][sequence]
     41 bits    10 bits  12 bits
     (ms)       (1024)   (4096/ms/node)
```

### 8.2 ULID 사용 예 (Kotlin)

```kotlin
// build.gradle.kts
// implementation("com.github.guepardoapps:kulid:2.0.0.0")

@Entity
data class TradeOrder(
    @Id
    val id: String = ULID.random(),   // "01ARYZ6S41TSV4RRFFQ69G5FAV"
    val symbol: String,
    val amount: BigDecimal,
    val price: BigDecimal,
    val createdAt: Instant = Instant.now(),
)
```

> **UUID v7 vs ULID:** UUID v7은 RFC 표준이고 UUID 형태를 유지해 기존 시스템 호환성이 좋다. ULID는 Crockford Base32로 가독성이 약간 낫다. 신규 프로젝트에서는 UUID v7을 권장한다.

---

## 9. 시계 동기화와 논리 시계

### 9.1 NTP 한계

물리 시계는 드리프트(drift)로 인해 수십 ms 오차가 누적된다. Google Spanner의 TrueTime API는 원자 시계 + GPS로 오차 범위를 7ms 이내로 줄이지만, 일반 인프라에서는 불가능하다.

### 9.2 Lamport Clock (논리 시계)

물리 시간 없이 **이벤트 순서**를 추론한다.

```
규칙:
1. 로컬 이벤트 발생: timestamp += 1
2. 메시지 송신: timestamp += 1, 메시지에 포함
3. 메시지 수신: timestamp = max(local, received) + 1

한계: A.t < B.t 이면 A가 B보다 먼저라는 뜻이지만,
     A.t > B.t여도 A → B 인과관계가 있을 수 있다 (인과관계 파악 불가).
```

### 9.3 Vector Clock

각 노드의 카운터를 벡터로 유지해 **인과 관계**를 정확히 추론한다.

```
Node A: [2, 0, 0]
Node B: [1, 3, 0]
Node C: [1, 2, 4]

A[2,0,0]와 B[1,3,0]는 동시 이벤트 (concurrent) → 충돌 해결 정책 필요
A[2,1,0]와 B[1,1,0]는 A → B (A가 먼저)
```

**실무 적용:** DynamoDB, Riak 같은 AP 데이터베이스가 벡터 시계(또는 유사 메커니즘)로 충돌을 감지한다.

---

## 10. 합의 알고리즘: Raft 맛보기

### 10.1 Raft의 세 역할

```
┌─────────────────────────────────────────────────────┐
│  Leader          Follower         Follower           │
│  (리더)          (팔로워)          (팔로워)           │
│                                                      │
│  모든 쓰기 처리  ← Heartbeat ←                       │
│  로그 복제 →     로그 수신 →       로그 수신          │
│                                                      │
│  [Leader Election 조건: 과반수(quorum) 투표 필요]    │
└─────────────────────────────────────────────────────┘
```

### 10.2 핵심 동작

| 단계 | 설명 |
|------|------|
| Leader Election | 팔로워가 heartbeat 타임아웃 → Candidate 전환 → 과반수 투표 획득 시 Leader |
| Log Replication | 클라이언트 요청 → Leader 로그 추가 → Follower에 복제 → 과반수 확인 → Commit |
| Safety | Committed 엔트리는 절대 변경되지 않음 (선형화 가능성 보장) |

**etcd, CockroachDB, TiKV** 등이 Raft를 사용한다.

> **증권 시스템 연관:** Kafka의 KRaft(Kafka without ZooKeeper)도 Raft 기반으로, 파티션 리더 선출과 메타데이터 관리에 사용된다.

---

## 11. 증권 시스템: 강한 일관성 vs 최종적 일관성 분류

```
┌──────────────────────────────────────────────────────────────────────┐
│                     증권 시스템 일관성 지도                           │
├─────────────────────────────┬────────────────────────────────────────┤
│  강한 일관성 필수            │  최종적 일관성 허용                    │
│  (Linearizability 필요)     │  (Eventual Consistency 가능)           │
├─────────────────────────────┼────────────────────────────────────────┤
│  • 주문장부(Order Book)     │  • 실시간 시세(Market Data)            │
│    잔량 차감                │  • 종목 공시/뉴스                      │
│  • 계좌 잔고 차감           │  • 투자자 보고서/잔고 조회(비실시간)   │
│  • 체결 처리(Trade Match)   │  • 공지사항, FAQ                      │
│  • 증거금 계산              │  • 거래 내역 조회(T+1 기준)            │
│  • 사가 상태 전이           │  • 관심종목, 알림 설정                 │
│  • 분산 락 획득             │  • 투자 성과 분석 대시보드             │
└─────────────────────────────┴────────────────────────────────────────┘
```

### 11.1 주문장부 잔량 차감 (강한 일관성)

```kotlin
@Service
@Transactional(isolation = Isolation.SERIALIZABLE) // 최강 격리 수준
class OrderBookService(private val orderBookRepository: OrderBookRepository) {

    fun deductQuantity(orderId: Long, symbol: String, quantity: BigDecimal): DeductResult {
        val orderBook = orderBookRepository.findBySymbolWithLock(symbol)
            ?: throw SymbolNotFoundException(symbol)

        if (orderBook.availableQuantity < quantity) {
            throw InsufficientQuantityException(
                "잔량 부족: available=${orderBook.availableQuantity}, requested=$quantity"
            )
        }

        orderBook.availableQuantity = orderBook.availableQuantity - quantity
        orderBook.version += 1  // 낙관적 락 버전 관리

        return DeductResult(
            orderId = orderId,
            deducted = quantity,
            remaining = orderBook.availableQuantity,
        )
    }
}
```

### 11.2 시세 조회 (최종적 일관성 허용)

```kotlin
@Service
class MarketDataService(
    private val marketDataCache: RedisTemplate<String, MarketData>,
    private val marketDataRepository: MarketDataRepository,
) {
    // 최대 500ms 지연 허용, 캐시 우선 (AP 모델)
    fun getPrice(symbol: String): MarketData {
        val cacheKey = "market:price:$symbol"
        return marketDataCache.opsForValue().get(cacheKey)
            ?: marketDataRepository.findLatest(symbol)
                .also { data ->
                    marketDataCache.opsForValue().set(cacheKey, data, Duration.ofMillis(500))
                }
    }
}
```

---

## 12. 트레이드오프 요약 및 의사결정 체크리스트

### 12.1 트레이드오프 분석

| 전략 | 장점 | 단점 | 선택 기준 |
|------|------|------|-----------|
| 2PC | 원자성 보장 | Blocking, SPOF, 성능 저하 | 레거시, 단일 DB 내 |
| Saga (Orchestration) | 명확한 흐름, 모니터링 용이 | Orchestrator 복잡도 | 순서 있는 비즈니스 흐름 |
| Saga (Choreography) | 결합도 낮음, 확장 용이 | 흐름 파악 어려움 | 독립적 서비스 연계 |
| Outbox Pattern | 트랜잭션-이벤트 원자성 | CDC 인프라 필요 | 이벤트 유실 방지 |
| 분산 락 (Redis) | 구현 쉬움, 빠름 | TTL 만료, GC 위험 | 단순 상호 배제 |
| DB 낙관적 락 | 충돌 감지, 락 없음 | 재시도 로직 필요 | 충돌 빈도 낮을 때 |

### 12.2 설계 체크리스트

- [ ] 각 서비스 간 일관성 모델을 명시적으로 결정했는가?
- [ ] 모든 외부 API 호출과 메시지 소비에 멱등성 키를 적용했는가?
- [ ] 사가 보상 트랜잭션 실패 시 Dead Letter Queue + 수동 개입 프로세스가 있는가?
- [ ] 분산 락 TTL이 최대 처리 시간보다 충분히 큰가? (GC 고려)
- [ ] 강한 일관성이 필요한 영역과 최종적 일관성 허용 영역을 문서화했는가?
- [ ] 분산 ID 충돌 가능성과 시계 오차를 고려한 ID 전략이 있는가?
- [ ] Kafka EOS 사용 시 동일 클러스터 외 시스템과의 원자성 처리를 별도 설계했는가?
- [ ] 주문 체결 등 Linearizability 필요 영역에 SERIALIZABLE 격리 수준 또는 동등한 보장이 있는가?

---

## 13. 흔한 함정 (Common Pitfalls)

**함정 1: "Kafka는 Exactly-Once이므로 멱등성 처리가 불필요하다"**
Kafka EOS는 동일 클러스터 내 Producer-Consumer 간에만 유효하다. DB 커밋과 Kafka 발행의 원자성은 별도 패턴이 필요하다.

**함정 2: "분산 락을 길게 잡으면 안전하다"**
TTL이 길수록 GC나 네트워크 지연으로 인한 이중 락 획득 가능성이 높아진다. 처리를 짧게 가져가고 Fencing Token으로 중복 실행을 검증하는 것이 더 안전하다.

**함정 3: "사가 보상이 항상 성공한다"**
보상 자체도 실패할 수 있다. 보상 불가 상황에 대한 운영 절차와 알림 시스템이 필수다.

**함정 4: "UUID로 고유성은 보장된다"**
UUID v4는 충돌 확률이 극히 낮지만 0은 아니다. 더 중요한 것은 UUID는 정렬 불가능해 인덱스 성능에 영향을 준다. 시간순 정렬이 필요한 컬럼에는 UUID v7 또는 ULID를 사용한다.

**함정 5: "최종 일관성 허용 = 데이터 유실 허용"**
최종 일관성은 데이터 유실을 허용하는 것이 아니다. 결국에는 모든 노드가 동일한 값을 가짐을 보장한다. 유실과 지연은 다른 개념이다.

---

이전: [38. 이벤트 소싱과 CQRS](38-event-sourcing-cqrs) · 다음: [40. 캐시 전략과 Redis](40-caching-redis) · [전체 커리큘럼](/curriculum)
