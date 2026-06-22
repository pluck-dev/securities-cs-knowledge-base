# 41. 고가용성과 장애복구 (High Availability & Disaster Recovery)

증권 시스템은 9시~15시 30분 개장 중 단 1분의 중단도 수십억 원 손실과 직결된다. 이 장에서는 가용성 이론부터 실전 HA 설계, 금융권 BCP 의무사항까지 체계적으로 다룬다.

---

## 1. 가용성 지표 — 9의 개수(Nines)

가용성은 **서비스가 정상 동작하는 시간의 비율**이다.

```
가용성 = 정상 운영 시간 / (정상 운영 시간 + 장애 시간) × 100
```

| 가용성 표기 | 연간 허용 장애 시간 | 월간 허용 장애 시간 | 적용 사례 |
|---|---|---|---|
| 99% (2 Nines) | 87.6시간 | 7.3시간 | 일반 내부 시스템 |
| 99.9% (3 Nines) | 8.76시간 | 43.8분 | 일반 금융 서비스 |
| 99.95% | 4.38시간 | 21.9분 | 증권사 부가 서비스 |
| 99.99% (4 Nines) | 52.6분 | 4.4분 | 증권사 주문·체결 시스템 |
| 99.999% (5 Nines) | 5.26분 | 26.3초 | 코어 매매 시스템 목표 |

### 1-1. SLI / SLO / SLA 정의

| 용어 | 의미 | 예시 |
|---|---|---|
| **SLI** (Service Level Indicator) | 실측 지표 | 주문 API 성공률 = 성공 요청 / 전체 요청 |
| **SLO** (Service Level Objective) | 내부 목표 | 주문 API 성공률 99.99% 이상 유지 |
| **SLA** (Service Level Agreement) | 외부 계약 | SLO 미달 시 수수료 환불 약정 |

> **증권사 현실**: 금융위원회 전자금융감독규정 제15조는 증권사 코어 시스템에 99.99% 이상의 가용성을 요구한다. SLO는 SLA보다 엄격하게 설정해 오차를 흡수해야 한다.

---

## 2. 단일장애점(SPOF) 제거 전략

SPOF(Single Point of Failure)는 **해당 구성요소가 죽으면 전체가 죽는 지점**이다.

```
[SPOF 존재]                     [SPOF 제거]

Client → LB(1대) → App → DB    Client → LB(Active+Standby)
                                        ↓
          ↑ LB 장애 = 전체 장애          App(N대)
                                        ↓
                                    DB Primary
                                    DB Replica(2대)
```

### 주요 SPOF 체크리스트

- [ ] 로드밸런서 이중화 여부
- [ ] 네트워크 스위치 이중화 (LACP 본딩)
- [ ] 데이터베이스 이중화 (Primary-Replica)
- [ ] 메시지 브로커 클러스터링 (Kafka 3-broker 이상)
- [ ] DNS 단일 회선 여부 (Anycast, GeoDNS)
- [ ] 외부 의존 서비스 단일화 여부 (HTS/FIX Gateway)
- [ ] 전원 이중화 (UPS, 이중 전원 공급)
- [ ] 냉각 시스템 이중화

---

## 3. 이중화 방식 비교

### 3-1. Active-Active

양쪽 노드가 **동시에 트래픽을 처리**한다.

```
                 ┌─────────────┐
Client ─────────▶│  Load       │
                 │  Balancer   │
                 └──────┬──────┘
              ┌─────────┴─────────┐
              ▼                   ▼
       ┌─────────────┐   ┌─────────────┐
       │  App Node A │   │  App Node B │
       │  (Active)   │   │  (Active)   │
       └──────┬──────┘   └──────┬──────┘
              └────────┬─────────┘
                       ▼
                  ┌─────────┐
                  │ Shared  │
                  │  State  │ ← Redis Cluster
                  └─────────┘
```

**장점**: 자원 100% 활용, 페일오버 시간 없음 (수 ms 이내)  
**단점**: 상태 동기화 복잡, 충돌 해결 필요, 분산 트랜잭션 난이도 높음

**증권 주문 시스템 적용 시 함정**: 두 노드가 동시에 같은 종목 주문을 받으면 체결 수량 집계가 어긋날 수 있다. 해결책은 **종목 샤딩** — 종목 코드로 해시하여 노드를 고정 배정한다.

### 3-2. Active-Standby (Active-Passive)

주 노드만 처리하고 부 노드는 대기한다.

```
Client ──────────▶ App Node A (Active)
                        │
                     Heartbeat
                        │
                   App Node B (Standby)
                   — 장애 감지 시 페일오버 —▶ App Node B (Active)
```

**장점**: 상태 동기화 단순, 일관성 보장  
**단점**: Standby 자원 낭비, 페일오버 시간 (보통 10초~60초)

| 구분 | Active-Active | Active-Standby |
|---|---|---|
| 자원 활용률 | 100% | 50% |
| 페일오버 시간 | 수 ms | 10~60초 |
| 구현 복잡도 | 높음 | 낮음 |
| 데이터 정합성 | 어려움 | 상대적으로 쉬움 |
| 증권 주문 적합성 | 샤딩 설계 필요 | 안전하지만 비용 증가 |

---

## 4. 로드밸런싱(Load Balancing)

### 4-1. L4 vs L7

| 구분 | L4 (Transport Layer) | L7 (Application Layer) |
|---|---|---|
| 동작 계층 | TCP/UDP | HTTP/HTTPS |
| 라우팅 기준 | IP + 포트 | URL, 헤더, 쿠키 |
| 성능 | 빠름 (패킷 레벨) | 상대적으로 느림 |
| 기능 | 단순 분산 | 경로 기반 라우팅, SSL 종료 |
| 예시 | AWS NLB, HAProxy TCP | AWS ALB, Nginx, Envoy |

증권 HTS는 FIX 프로토콜(TCP) → L4, REST API → L7을 병행 운용한다.

### 4-2. 로드밸런싱 알고리즘

| 알고리즘 | 방식 | 적합 상황 |
|---|---|---|
| Round Robin | 순서대로 순환 | 요청 처리 시간이 균일할 때 |
| Least Connection | 현재 연결 수 최소 노드 | 처리 시간이 가변적일 때 |
| IP Hash | 클라이언트 IP 해시 | 세션 고정(Sticky Session) 필요 시 |
| Weighted Round Robin | 가중치 비례 분산 | 노드 사양이 다를 때 |
| Random | 무작위 | 대용량 단순 서비스 |

> **증권 세션 주의**: WebSocket 기반 실시간 시세 구독은 IP Hash 또는 Sticky Session이 필요하다. 연결이 끊기면 재구독 비용이 크기 때문이다.

---

## 5. 헬스체크(Health Check)

### 5-1. Liveness vs Readiness Probe

| 프로브 종류 | 목적 | 실패 시 동작 | 예시 |
|---|---|---|---|
| **Liveness** | 프로세스 생존 확인 | 파드 재시작 | `/actuator/health/liveness` |
| **Readiness** | 트래픽 수신 가능 확인 | 로드밸런서 제외 | `/actuator/health/readiness` |
| **Startup** | 초기 기동 확인 | 기동 완료 전 다른 프로브 비활성 | 느린 JVM 워밍업 대응 |

### 5-2. 딥 헬스체크 (Deep Health Check) 구현

```kotlin
@Component
class DeepHealthIndicator(
    private val dataSource: DataSource,
    private val redisTemplate: StringRedisTemplate,
    private val kafkaTemplate: KafkaTemplate<String, String>
) : HealthIndicator {

    override fun health(): Health {
        val details = mutableMapOf<String, Any>()

        // DB 연결 확인
        val dbOk = runCatching {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT 1").executeQuery()
            }
            true
        }.getOrElse { e ->
            details["db_error"] = e.message ?: "unknown"
            false
        }

        // Redis 연결 확인
        val redisOk = runCatching {
            redisTemplate.opsForValue().get("health-check-ping")
            true
        }.getOrElse { e ->
            details["redis_error"] = e.message ?: "unknown"
            false
        }

        details["db"] = if (dbOk) "UP" else "DOWN"
        details["redis"] = if (redisOk) "UP" else "DOWN"

        return if (dbOk && redisOk) {
            Health.up().withDetails(details).build()
        } else {
            Health.down().withDetails(details).build()
        }
    }
}
```

```yaml
# application.yml — Kubernetes probe 설정
management:
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true

# k8s deployment
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
  failureThreshold: 3
```

---

## 6. 페일오버(Failover)와 페일백(Failback)

### 6-1. 자동 vs 수동 페일오버

| 구분 | 자동 페일오버 | 수동 페일오버 |
|---|---|---|
| 전환 시간 | 수십 초 이내 | 수분~수십 분 |
| 정확도 | 오탐 가능 (Split-Brain) | 운영자 판단으로 정확 |
| 증권 적용 | 비중요 서비스 | 주문·체결 코어 |

### 6-2. 뇌분열(Split-Brain) 문제

Split-Brain은 **두 노드가 모두 자신이 Primary라고 인식**하는 상태다.

```
정상:    Primary ──네트워크──▶ Replica

분리:    Primary        Replica
         (자신이 P)     (자신도 P 선언)
              ↓               ↓
           쓰기 수용       쓰기 수용
              ↓               ↓
          데이터 충돌 발생 !!
```

**해결책**:

1. **Quorum(과반수) 투표**: 3개 이상의 노드에서 과반 동의 시만 Primary 승격 (Raft, Paxos)
2. **Fencing(STONITH)**: 구 Primary 강제 종료 후 신 Primary 승격
3. **Witness 노드**: 판정 역할 전담 노드 배치

---

## 7. 무상태(Stateless) 설계와 상태 외부화

무상태 설계는 HA의 전제 조건이다. 어떤 노드가 어떤 요청을 받아도 동일하게 처리할 수 있어야 한다.

```
[상태 있는 설계 — BAD]
Client ──▶ App Node A (세션 메모리에 로그인 정보 보관)
           ↑ 이 노드 죽으면 세션 유실

[상태 외부화 — GOOD]
Client ──▶ App Node A ──▶ Redis (세션 저장)
Client ──▶ App Node B ──▶ Redis (동일 세션 조회 가능)
```

### 외부화 대상

| 상태 종류 | 외부화 수단 | 비고 |
|---|---|---|
| 사용자 세션 | Redis Cluster | TTL 설정 필수 |
| 주문 임시 상태 | Redis + DB | 이중 기록으로 안전성 확보 |
| 파일 업로드 | S3 / NFS | 로컬 디스크 금지 |
| 스케줄러 락 | ShedLock + DB | 중복 실행 방지 |

---

## 8. 데이터 복제(Replication) 전략

### 8-1. 동기 복제(Synchronous Replication)

```
Client ──▶ Primary ──동기 복제──▶ Replica
                    ◀── ACK ────
           ◀── OK ──
```

- Primary가 Replica의 ACK를 받은 후에 클라이언트에 응답
- **강한 일관성** 보장, 데이터 손실 없음
- **지연 증가**: Replica 응답 시간이 전체 지연에 포함
- 적용: 증권 주문 데이터, 계좌 잔고 변경

### 8-2. 비동기 복제(Asynchronous Replication)

```
Client ──▶ Primary ──▶ OK (즉시 응답)
                   ──비동기──▶ Replica (나중에 복제)
```

- Primary가 즉시 응답 → **낮은 지연**
- Primary 장애 시 미복제 데이터 손실 가능 (**RPO > 0**)
- 적용: 시세 데이터, 통계, 로그

### 8-3. Semi-Synchronous (반동기)

최소 1개 Replica의 ACK만 확인 후 응답 — 지연과 안정성의 균형점. MySQL Group Replication, PostgreSQL synchronous_commit = remote_write 등이 이를 지원한다.

---

## 9. RPO / RTO — 백업과 복구 목표

| 지표 | 정의 | 증권사 목표 |
|---|---|---|
| **RPO** (Recovery Point Objective) | 복구 시 허용되는 최대 데이터 손실 기간 | 주문 데이터: 0초 (무손실) |
| **RTO** (Recovery Time Objective) | 장애 후 서비스 복구까지 허용 시간 | 코어: 30분 이내 |

```
RPO 시각화:

마지막 백업       장애 발생       복구 완료
    │                │               │
────┼────────────────┼───────────────┼────
    ◀─── RPO ───────▶ ◀─── RTO ────▶
```

### 백업 전략 비교

| 방식 | RPO | 스토리지 비용 | 복구 시간 |
|---|---|---|---|
| 전체 백업 (Full) | 백업 주기만큼 | 높음 | 빠름 |
| 증분 백업 (Incremental) | 증분 주기만큼 | 낮음 | 느림 (누적 적용) |
| 차등 백업 (Differential) | 차등 주기만큼 | 중간 | 중간 |
| 연속 아카이브 (WAL/Binlog) | 거의 0 | 중간 | 중간 |

증권사 핵심 DB는 **Full 백업 + WAL 연속 아카이브** 조합으로 RPO를 분 단위 이하로 유지한다.

---

## 10. 재해복구(DR) — 주센터 / 재해센터 구성

```
[주센터 — Seoul Region]              [재해센터 — Busan Region]
┌──────────────────────┐             ┌──────────────────────┐
│  Load Balancer (HA)  │──WAN 복제──▶│  Standby LB          │
│  App Cluster (N대)   │             │  App Cluster (대기)   │
│  DB Primary          │──동기/비동기▶│  DB Replica (DR)     │
│  Kafka Cluster       │             │  Kafka Mirror        │
│  Redis Cluster       │             │  Redis Replica       │
└──────────────────────┘             └──────────────────────┘
         │                                      ▲
         └────── DNS Failover ─────────────────┘
                 (Route53 / GeoDNS)
```

### DR 등급 분류 (금융위 기준 준용)

| 등급 | 방식 | RTO | RPO | 비용 |
|---|---|---|---|---|
| Hot Standby | 실시간 동기화, 즉시 전환 | 분 이내 | 0 | 매우 높음 |
| Warm Standby | 비동기 복제, 수동 전환 | 수십 분 | 수 분 | 높음 |
| Cold Standby | 백업 복원, 수동 구성 | 수 시간 | 수 시간 | 낮음 |

증권사 코어 시스템은 Warm~Hot Standby를 의무화하며, 금융위원회 전자금융감독규정에 따라 **연 1회 이상 DR 훈련**을 실시해야 한다.

---

## 11. BCP(Business Continuity Plan) 금융권 의무사항

**근거 법령**: 전자금융거래법 제21조의2, 전자금융감독규정 제15조~제16조

| 의무 항목 | 내용 |
|---|---|
| 업무연속성계획 수립 | 재해·장애 시 핵심 업무 지속 방안 문서화 |
| DR 시스템 구축 | 주센터 장애 시 대체 운영 가능한 재해복구센터 |
| 연 1회 DR 훈련 | 실제 전환 훈련, 결과 보고서 금감원 제출 |
| RTO/RPO 목표 수립 | 업무 중요도별 목표 설정 및 달성 여부 모니터링 |
| 백업 데이터 검증 | 분기별 1회 이상 복구 가능 여부 확인 |
| 재해복구 테스트 결과 보고 | 이사회 또는 대표이사 보고 의무 |

> **실무 함정**: DR 훈련을 형식적으로 진행하다가 실제 재해 발생 시 복구 스크립트가 구버전이거나 담당자가 퇴사한 사례가 빈번하다. 런북(Runbook)을 항상 최신 상태로 유지하고 자동화 스크립트로 검증해야 한다.

---

## 12. 장애 격리 패턴

### 12-1. 서킷브레이커(Circuit Breaker) — Resilience4j

서킷브레이커는 반복 실패하는 외부 호출을 **빠르게 차단**해 장애 전파를 막는다.

```
CLOSED ──(실패율 임계치 초과)──▶ OPEN
  ▲                               │
  │                    대기 시간 후│
  │                               ▼
  └────(성공)──── HALF-OPEN ◀─────┘
                  (시험 호출)
```

```kotlin
// build.gradle.kts
implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
implementation("io.github.resilience4j:resilience4j-kotlin:2.2.0")

// application.yml
resilience4j:
  circuitbreaker:
    instances:
      orderService:
        registerHealthIndicator: true
        slidingWindowSize: 10          # 최근 10회 호출 기준
        failureRateThreshold: 50       # 50% 실패 시 OPEN
        waitDurationInOpenState: 30s   # 30초 후 HALF-OPEN
        permittedNumberOfCallsInHalfOpenState: 3
        minimumNumberOfCalls: 5

// 사용 예
@Service
class OrderRouterService(
    private val primaryGateway: OrderGateway,
    private val fallbackGateway: OrderGateway
) {

    @CircuitBreaker(name = "orderService", fallbackMethod = "fallbackOrder")
    fun submitOrder(order: Order): OrderResult {
        return primaryGateway.submit(order)
    }

    fun fallbackOrder(order: Order, ex: Exception): OrderResult {
        log.warn("Circuit OPEN — 보조 게이트웨이로 전환: ${ex.message}")
        return fallbackGateway.submit(order)
    }
}
```

### 12-2. 벌크헤드(Bulkhead) — 스레드 풀 격리

벌크헤드는 **특정 기능의 장애가 다른 기능으로 전파되지 않도록** 자원을 격리한다.

```
[격리 없음]               [벌크헤드 적용]
공통 스레드 풀             주문 스레드 풀 (10개)
 ├─ 주문 요청             시세 스레드 풀 (20개)
 ├─ 시세 요청             리포트 스레드 풀 (5개)
 └─ 리포트 요청
     ↓
 리포트 처리 지연 →        리포트 지연이 주문에 영향 없음
 주문도 지연됨
```

```kotlin
resilience4j:
  bulkhead:
    instances:
      orderBulkhead:
        maxConcurrentCalls: 10       # 동시 주문 처리 최대 10
        maxWaitDuration: 100ms       # 100ms 내 자리 없으면 거부
      marketDataBulkhead:
        maxConcurrentCalls: 50
        maxWaitDuration: 10ms

@Bulkhead(name = "orderBulkhead", type = Bulkhead.Type.SEMAPHORE)
fun processOrder(order: Order): OrderResult { ... }
```

### 12-3. 타임아웃(Timeout) + 재시도(Retry) + 지수 백오프

```kotlin
resilience4j:
  retry:
    instances:
      orderRetry:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2.0   # 500ms → 1000ms → 2000ms
        randomizedWaitFactor: 0.3           # Jitter: ±30%

  timelimiter:
    instances:
      orderTimeout:
        timeoutDuration: 2s                 # 2초 초과 시 TimeoutException

@Retry(name = "orderRetry")
@TimeLimiter(name = "orderTimeout")
@CircuitBreaker(name = "orderService")
fun submitOrder(order: Order): CompletableFuture<OrderResult> {
    return CompletableFuture.supplyAsync { gateway.submit(order) }
}
```

> **Jitter 적용 이유**: 모든 클라이언트가 동시에 재시도하면 **Thundering Herd** 현상으로 서버가 재장애 난다. Jitter로 재시도 시점을 분산시킨다.

**재시도 금지 대상**: 주문 제출(멱등성 없음), 결제 처리 — 중복 실행 시 이중 주문/결제가 발생한다. 재시도 전에 반드시 **멱등성 키(Idempotency Key)** 를 설계해야 한다.

---

## 13. 카오스 엔지니어링(Chaos Engineering)

카오스 엔지니어링은 **통제된 환경에서 고의로 장애를 주입**해 시스템의 복원력을 검증한다.

### 13-1. 실험 원칙

1. **정상 상태 기준 정의**: 주문 처리량 1,000 TPS 유지
2. **가설 수립**: "DB Primary 장애 시에도 30초 내 복구되어 주문이 재개된다"
3. **실제 운영 환경에 최소한의 장애 주입**
4. **결과 측정 및 취약점 개선**

### 13-2. 증권 시스템 카오스 실험 예시

```kotlin
// Spring Boot + Chaos Monkey 설정
// build.gradle.kts
testImplementation("de.codecentric:chaos-monkey-spring-boot:3.1.0")

// application-chaos.yml
chaos:
  monkey:
    enabled: true
    assaults:
      level: 5
      latencyActive: true
      latencyRangeStart: 1000        # 1초 지연
      latencyRangeEnd: 3000          # 3초 지연
      exceptionsActive: true
      exception:
        type: java.io.IOException
        arguments:
          - type: java.lang.String
            value: "Chaos: 네트워크 단절 시뮬레이션"
    watcher:
      service: true
      repository: true
```

### 13-3. 주문 처리 중 강제 장애 주입 시나리오

| 장애 유형 | 주입 방법 | 검증 항목 |
|---|---|---|
| DB Primary 종료 | `kill -9` 또는 네트워크 차단 | Replica 승격, 주문 재처리 여부 |
| Redis 노드 1개 다운 | Docker stop | 세션 유실 없이 Cluster 유지 |
| Kafka Broker 1개 다운 | 프로세스 종료 | 메시지 손실 없이 파티션 재배치 |
| CPU 100% 부하 | stress-ng | Circuit Breaker OPEN, 타임아웃 작동 |
| 네트워크 패킷 지연 | tc netem | 재시도 로직, 타임아웃 검증 |
| 개장 중 롤링 배포 | kubectl rollout | 주문 중단 없이 배포 완료 |

---

## 14. 무중단 배포 전략 — 개장 중 운영

증권사는 **9시~15시 30분 장 중에 배포가 불가**한 것이 원칙이지만, 장외 시간에도 야간 배치, HTS 연결이 있으므로 무중단 배포가 필수다.

### 14-1. 배포 전략 비교

| 전략 | 방식 | 다운타임 | 롤백 속도 | 자원 비용 |
|---|---|---|---|---|
| Recreate | 기존 종료 후 새 버전 시작 | 있음 | 재배포 필요 | 낮음 |
| Rolling Update | 인스턴스 하나씩 교체 | 없음 | 느림 | 낮음 |
| Blue-Green | 두 환경 준비 후 트래픽 전환 | 없음 | 즉시 (DNS 전환) | 2배 |
| Canary | 일부 트래픽만 새 버전으로 | 없음 | 빠름 | 약간 증가 |

### 14-2. Blue-Green 배포 흐름

```
[Blue 환경 — 현재 운영]   [Green 환경 — 새 버전 준비]
  App v1.0 (3대)           App v1.1 (3대) ← 배포 및 스모크 테스트
       ↑                         ↑
  Load Balancer ─── 트래픽 전환 ──▶

전환 후:
[Blue 환경 — 대기]         [Green 환경 — 운영 중]
  App v1.0 (롤백 대기)      App v1.1 (3대)
```

### 14-3. 증권사 배포 윈도우

| 시간대 | 배포 가능 여부 | 비고 |
|---|---|---|
| 09:00 ~ 15:30 | 불가 | 정규 시장 개장 |
| 15:30 ~ 16:00 | 매우 제한적 | 시간외 체결 |
| 16:00 ~ 18:00 | 가능 (승인 필요) | 저녁 배포 윈도우 |
| 02:00 ~ 06:00 | 권장 | 야간 배치 완료 후 |

```kotlin
// Graceful Shutdown — 진행 중 주문 완료 후 종료
@Component
class GracefulShutdown : ApplicationListener<ContextClosedEvent> {

    @Value("\${server.shutdown.grace-period:30s}")
    private lateinit var gracePeriod: Duration

    override fun onApplicationEvent(event: ContextClosedEvent) {
        log.info("Graceful Shutdown 시작 — 진행 중 주문 처리 대기")
        // 새 요청 수신 중지 (Readiness probe DOWN)
        // 처리 중 요청 완료 대기
        Thread.sleep(gracePeriod.toMillis())
        log.info("Graceful Shutdown 완료")
    }
}
```

---

## 15. 증권 시스템 HA 설계 사례

### 15-1. 주문계(Order Management System) 이중화

```
[클라이언트 — HTS/MTS]
         │
         ▼
┌─────────────────────┐
│   L4 Load Balancer  │  (Active-Standby, VRRP)
│   (Keepalived VIP)  │
└──────────┬──────────┘
           │
    ┌──────┴──────┐
    ▼             ▼
┌────────┐   ┌────────┐
│Order   │   │Order   │   Active-Active (종목 샤딩)
│App A   │   │App B   │   삼성전자 → App A
│(홀수종목)│  │(짝수종목)│  SK하이닉스 → App B
└───┬────┘   └───┬────┘
    └──────┬──────┘
           ▼
┌─────────────────────┐
│  Kafka Cluster      │  3 Broker, Replication Factor 3
│  (주문 이벤트 스트림)│
└──────────┬──────────┘
           ▼
┌──────────────────────────────────┐
│  PostgreSQL + Patroni (자동 HA)  │
│  Primary ──동기 복제──▶ Replica1  │
│           ──비동기──▶  Replica2   │ (읽기 전용)
└──────────────────────────────────┘
```

**주문 금액 처리 원칙**: 모든 금액 필드는 `BigDecimal` 사용. 부동소수점 오차는 금융 시스템에서 허용 불가.

```kotlin
data class Order(
    val orderId: String,
    val stockCode: String,
    val quantity: Long,
    val price: BigDecimal,          // Float/Double 절대 금지
    val totalAmount: BigDecimal = price.multiply(BigDecimal(quantity))
)
```

### 15-2. 시세계(Market Data System) 이중화

```
[거래소 — KRX FIX Feed]
         │
    ┌────┴────┐
    ▼         ▼
┌────────┐ ┌────────┐
│Feed A  │ │Feed B  │   Active-Active (동일 데이터 수신)
│Handler │ │Handler │   이중 수신으로 패킷 손실 방지
└───┬────┘ └───┬────┘
    └─────┬────┘
          ▼
    중복 제거 (Sequence No 기준)
          ▼
┌──────────────────────┐
│  Redis Cluster       │  최신 시세 캐시 (TTL 1초)
│  (Pub/Sub 배포)       │
└──────────┬───────────┘
           ▼
    WebSocket Server (N대)
           ▼
    클라이언트 실시간 시세
```

---

## 16. 트레이드오프 요약

| 목표 | 선택 | 포기하는 것 |
|---|---|---|
| 높은 가용성 | 이중화, 복제 | 비용, 복잡도 |
| 강한 일관성 | 동기 복제 | 지연 시간(Latency) |
| 낮은 지연 | 비동기 복제 | 데이터 손실 가능성 |
| 빠른 페일오버 | Active-Active | 상태 동기화 복잡도 |
| 단순한 설계 | Active-Standby | 자원 낭비(50%) |
| 장애 격리 | 서킷브레이커 + 벌크헤드 | 구현 복잡도, 오버헤드 |

---

## 17. 최종 체크리스트

### 설계 단계

- [ ] SPOF 목록 식별 및 제거 계획 수립
- [ ] 모든 컴포넌트의 이중화 방식(Active-Active / Standby) 결정
- [ ] 세션·상태 외부화 (Redis, DB) 설계
- [ ] RPO / RTO 목표 수립 및 사업부 승인

### 구현 단계

- [ ] Liveness / Readiness Probe 구현 및 딥 헬스체크 포함
- [ ] 서킷브레이커, 벌크헤드, 재시도 + 지수 백오프 적용
- [ ] Graceful Shutdown 구현 (진행 중 요청 완료 후 종료)
- [ ] 금액 필드 전체 BigDecimal 사용 검증

### 운영 단계

- [ ] 연 1회 이상 DR 훈련 계획 수립 및 결과 보고
- [ ] 런북(Runbook) 최신화 및 자동화 스크립트 검증
- [ ] 카오스 엔지니어링 실험 주기적 실행
- [ ] 배포 윈도우 준수 및 Blue-Green 배포 파이프라인 구성
- [ ] SLI 대시보드 구성 및 SLO 알림 설정

---

이전: [40. 캐시 전략과 Redis](40-caching-redis) · 다음: [42. 관찰 가능성과 모니터링](42-observability) · [전체 커리큘럼](/curriculum)
