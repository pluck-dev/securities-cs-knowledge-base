# 47. 마스터 체크리스트와 다음 단계

> **대상**: 이 코스를 완주한 증권사 백엔드 개발자  
> **목적**: 레벨별 자가 점검 → 성장 로드맵 확인 → 다음 목표 설정

---

## 목차

1. [자가 점검 체크리스트 — LEVEL별](#1-자가-점검-체크리스트)
2. [증권 도메인 전문 체크리스트](#2-증권-도메인-전문-체크리스트)
3. [시스템 설계·운영·보안 체크리스트](#3-시스템-설계운영보안-체크리스트)
4. [주니어 → 미들 → 시니어 성장 로드맵](#4-성장-로드맵)
5. [추천 심화 주제](#5-추천-심화-주제)
6. [추천 도서·자료 카테고리](#6-추천-도서자료-카테고리)
7. [면접·실무 자주 나오는 질문 모음](#7-면접실무-자주-나오는-질문)
8. [마지막 메시지](#8-마지막-메시지)

---

## 1. 자가 점검 체크리스트

> 각 항목을 실제로 코드나 설명으로 재현할 수 있으면 체크하세요.  
> **체크 기준**: "남에게 설명할 수 있고, 직접 구현할 수 있다" = ✅

---

### LEVEL 0 — 환경 설정 & 기초

#### 개발 환경
- [ ] JDK 21, Kotlin, IntelliJ IDEA 설치 및 프로젝트 생성 가능
- [ ] Gradle 멀티모듈 프로젝트 구조를 직접 만들 수 있다
- [ ] `build.gradle.kts` 의존성 추가 및 BOM 관리 이해
- [ ] Git Flow 또는 Trunk-based 브랜치 전략을 팀에서 운용한다
- [ ] Docker Compose로 PostgreSQL + Redis + Kafka 로컬 환경 구성 가능

---

### LEVEL 1 — 코틀린 언어 마스터

#### 기본 문법
- [ ] `val` / `var` 차이, 불변성(immutability) 철학 이해
- [ ] `data class` — `equals`, `hashCode`, `copy`, `toString` 자동 생성 원리
- [ ] `sealed class` — 타입 안전한 상태 표현, 패턴 매칭
- [ ] `object` — 싱글톤, companion object 용도 이해
- [ ] `when` 표현식 — 스마트 캐스트, 타입 분기

#### 함수형 스타일
- [ ] 람다(Lambda), 고차 함수(Higher-Order Function) 작성
- [ ] `map`, `filter`, `fold`, `groupBy`, `associateBy` 활용
- [ ] 확장 함수(Extension Function) — 기존 클래스에 비침투적 기능 추가
- [ ] 인라인 함수(`inline`) — 람다 성능 최적화 이유 설명 가능
- [ ] `let`, `run`, `also`, `apply`, `with` — 각 스코프 함수의 차이와 사용 시점

#### 타입 시스템
- [ ] Null Safety — `?`, `!!`, `?.`, `?:` 연산자 의미 모두 이해
- [ ] 제네릭(Generics) — 공변성(`out`), 반공변성(`in`), `*` 투영
- [ ] 타입 소거(Type Erasure) — 런타임 타입 정보 제한 인식
- [ ] `reified` 타입 파라미터 — 언제 필요한지 설명 가능

#### 코루틴 (Coroutines)
- [ ] `suspend` 함수 — 코루틴 컨텍스트 내에서만 호출 가능한 이유
- [ ] `CoroutineScope`, `launch`, `async`, `await` 사용법
- [ ] Dispatcher 종류 (`IO`, `Default`, `Main`) 및 선택 기준
- [ ] `Flow` — 콜드 스트림 vs `StateFlow`/`SharedFlow` 핫 스트림 차이
- [ ] 구조적 동시성(Structured Concurrency) — 부모-자식 코루틴 생명주기
- [ ] 코루틴 예외 처리 — `CoroutineExceptionHandler`, `supervisorScope`

---

### LEVEL 2 — 스프링 부트 핵심

#### IoC / DI
- [ ] `@Component`, `@Service`, `@Repository`, `@Controller` 차이 설명
- [ ] 생성자 주입(Constructor Injection) 권장 이유 (불변성, 테스트 용이성)
- [ ] `@Primary`, `@Qualifier` — 빈 충돌 해결
- [ ] `@Conditional`, `@Profile` — 환경별 빈 등록

#### AOP & 설정
- [ ] `@Aspect`, `@Around` — 횡단 관심사(Logging, Transaction, Auth) 적용
- [ ] `@ConfigurationProperties` — 타입 안전 설정 바인딩
- [ ] `@Scheduled` — 주기적 작업 스케줄링

#### Spring Data JPA
- [ ] `@Entity`, `@Table`, `@Column` 매핑 규칙
- [ ] 연관 관계 (`@OneToMany`, `@ManyToOne`) 및 `FetchType` 선택 기준
- [ ] JPQL, Native Query, Querydsl 사용 시점 구분
- [ ] N+1 문제 — 발생 원인 및 `JOIN FETCH` / `EntityGraph` 해결책
- [ ] `@Version` 낙관적 락 — 충돌 시 예외 처리 방법

#### Spring MVC / WebFlux
- [ ] `@RestController`, `@RequestMapping`, `@PathVariable`, `@RequestBody` 이해
- [ ] `@ExceptionHandler`, `@ControllerAdvice` — 전역 예외 처리
- [ ] Filter vs Interceptor vs AOP 처리 시점 차이
- [ ] WebFlux `Mono`/`Flux` — 리액티브 프로그래밍 기본 이해

---

### LEVEL 3 — 테스트 전략

- [ ] JUnit 5 + Mockito 기본 단위 테스트 작성 가능
- [ ] `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest` 슬라이스 테스트 용도 이해
- [ ] MockMvc — HTTP 요청/응답 검증
- [ ] Testcontainers — PostgreSQL, Kafka 실제 컨테이너로 통합 테스트
- [ ] 테스트 더블(Mock, Stub, Spy, Fake) 차이 설명 가능
- [ ] Given-When-Then 패턴으로 테스트 가독성 확보
- [ ] 코드 커버리지 80% 이상 목표 설정 및 JaCoCo 리포트 확인
- [ ] 테스트 피라미드(단위 > 통합 > E2E) 비율 전략 수립 가능

---

### LEVEL 4 — 데이터베이스 & 캐시

#### PostgreSQL / 관계형 DB
- [ ] 인덱스 설계 — B-Tree 구조, 복합 인덱스 컬럼 순서 선택 이유
- [ ] `EXPLAIN ANALYZE` 결과 해석 — Seq Scan vs Index Scan
- [ ] 파티셔닝(Range/List/Hash) — 대용량 거래 내역 테이블 적용 가능
- [ ] 트랜잭션 격리 수준(Read Committed, Repeatable Read, Serializable) 차이
- [ ] 데드락(Deadlock) — 발생 조건 및 락 순서 통일 해결책
- [ ] `SELECT FOR UPDATE` 비관적 락 vs `@Version` 낙관적 락 선택 기준

#### Redis
- [ ] 데이터 구조별 사용 시나리오 (String, Hash, Sorted Set, List)
- [ ] TTL 설정 전략 — 캐시 만료 + Eviction Policy
- [ ] 분산 락 (RedLock 알고리즘 개념 이해)
- [ ] Cache-Aside 패턴 — 캐시 미스 시 DB 조회 후 적재
- [ ] 캐시 무효화 전략 (Write-Through, Write-Behind)

#### 플라이웨이 (Flyway) / 리퀴베이스 (Liquibase)
- [ ] DB 마이그레이션 파일 버전 관리 이해
- [ ] 무중단 스키마 변경 절차 (Add → Deploy → Remove Old Column)

---

### LEVEL 5 — 동시성 & 정합성

- [ ] JVM 메모리 모델 — `happens-before` 관계 이해
- [ ] `synchronized`, `ReentrantLock`, `ReadWriteLock` 차이와 선택 기준
- [ ] `volatile` — 가시성(Visibility) 보장, 원자성(Atomicity) 미보장
- [ ] `AtomicLong`, `AtomicReference` — CAS(Compare-And-Swap) 원리
- [ ] `ConcurrentHashMap` vs `Collections.synchronizedMap` 성능 차이
- [ ] 코루틴 Mutex, Semaphore — 비동기 컨텍스트에서의 동시성 제어
- [ ] 분산 환경 동시성 — DB 락 vs 분산 락(Redis) 선택 기준
- [ ] 멱등성(Idempotency) — 중복 요청 방지 설계 (`idempotency_key`)

---

### LEVEL 6 — 이벤트 & 메시지 큐

#### Kafka
- [ ] 토픽(Topic), 파티션(Partition), 오프셋(Offset), 컨슈머 그룹 개념
- [ ] 파티션 키 선택 — 순서 보장이 필요한 단위(종목 코드)로 설정
- [ ] `at-least-once` vs `exactly-once` 의미론 차이, 멱등 프로듀서
- [ ] 컨슈머 리밸런싱 — 파티션 재할당 시 처리 중 메시지 보호
- [ ] Dead Letter Queue (DLQ) — 처리 실패 메시지 격리 전략
- [ ] 트랜잭셔널 아웃박스 패턴 — DB 저장 + 이벤트 발행 원자성 보장

#### 이벤트 소싱 (Event Sourcing)
- [ ] 이벤트 소싱 vs 상태 저장 방식 장단점 비교
- [ ] CQRS — 명령(Command) 모델과 조회(Query) 모델 분리 이유
- [ ] 이벤트 스토어 설계 및 스냅샷(Snapshot) 전략

---

### LEVEL 7 — 보안 & 인증

- [ ] JWT(JSON Web Token) 구조 — Header, Payload, Signature 이해
- [ ] Access Token + Refresh Token 이중 토큰 전략
- [ ] Spring Security 필터 체인 — 인증(Authentication) vs 인가(Authorization)
- [ ] `@PreAuthorize`, `@PostAuthorize` — 메서드 레벨 인가
- [ ] OWASP Top 10 — SQL 인젝션, XSS, CSRF, IDOR 방어 방법 이해
- [ ] 비밀번호 해싱 — BCrypt 솔트(Salt) 원리, 평문 저장 금지
- [ ] API Rate Limiting — Redis 슬라이딩 윈도우, 토큰 버킷 알고리즘
- [ ] TLS/HTTPS — 인증서 교체 절차, HSTS 설정

---

### LEVEL 8 — 관측성 & 운영

- [ ] 구조화 로깅(JSON 형식) — `traceId`, `spanId` MDC 전파
- [ ] Prometheus 메트릭 — Counter, Gauge, Histogram, Summary 차이
- [ ] Grafana 대시보드 — 핵심 지표(TPS, P99 지연, 오류율) 패널 구성
- [ ] 분산 추적(Distributed Tracing) — Zipkin/Jaeger traceId로 요청 흐름 추적
- [ ] Alert 설정 — P99 지연 > 200ms, 오류율 > 1% 시 알림
- [ ] 블루/그린 배포, 카나리 배포 전략 설명 가능
- [ ] Kubernetes — Pod, Service, Deployment, ConfigMap 기본 리소스 이해
- [ ] Health Check — Liveness vs Readiness Probe 차이

---

## 2. 증권 도메인 전문 체크리스트

### 주문 생명주기 (Order Lifecycle)
- [ ] 주문 상태 전이 — OPEN → PARTIAL → FILLED → CANCELLED 각 트리거 이해
- [ ] 지정가(LIMIT) vs 시장가(MARKET) vs 최유리 지정가 차이
- [ ] 주문 접수 → 검증 → 호가창 등록 → 매칭 → 체결 전체 흐름 설명 가능
- [ ] 예수금 선차감(주문 접수 시) vs 최종 정산(체결 시) 타이밍 이해
- [ ] 부분 체결(Partial Fill) 처리 및 잔량 주문 관리

### 호가창·매칭 (Order Book & Matching)
- [ ] 가격 우선 → 시간 우선 매칭 규칙 설명 가능
- [ ] TreeMap을 이용한 호가창 자료구조 직접 구현 가능
- [ ] 매수 최고가(Best Bid) vs 매도 최저가(Best Ask) 스프레드 개념
- [ ] 시장가 주문의 슬리피지(Slippage) 리스크 인식

### 원장·잔고·손익 (Ledger / Position / P&L)
- [ ] 평단가(Average Cost) 재계산 공식 — 가중평균 방식 직접 계산 가능
- [ ] 실현 손익(Realized P&L) vs 평가 손익(Unrealized P&L) 구분
- [ ] 매도 대금 정산 타이밍(D+2) 이해
- [ ] 수수료(Fee) 계산 방식 — 체결 금액 × 수수료율

### 리스크 관리 (Risk Management)
- [ ] 주문 한도(Max Order Size) 설정 이유
- [ ] 계좌 내 종목 편입 한도(Concentration Limit) 개념
- [ ] 반대 매매(Forced Liquidation) 발동 조건 이해
- [ ] KYC/AML — 금융 거래 실명 확인·자금세탁 방지 의무 인식

### 정산 (Settlement)
- [ ] T+2 정산 사이클 — 체결일 기준 2영업일 후 예수금 확정
- [ ] 배당금 처리 — 권리락일(Ex-Dividend Date) 이후 포지션 반영
- [ ] 주식 분할/병합 시 평단가·수량 조정 필요성 인식

---

## 3. 시스템 설계·운영·보안 체크리스트

### 고가용성 (High Availability)
- [ ] 단일 장애점(SPOF) 제거 — 로드 밸런서, 다중화(Replication)
- [ ] 서킷 브레이커(Circuit Breaker) — Resilience4j 적용 가능
- [ ] Graceful Shutdown — 진행 중인 요청 완료 후 서버 종료
- [ ] 데이터베이스 Failover — Primary → Replica 전환 시간 인식

### 성능 최적화 (Performance)
- [ ] 커넥션 풀 — HikariCP 적정 사이즈 계산 (`코어 수 × 2 + 유효 스핀들`)
- [ ] 배치 처리 — `saveAll()` vs 개별 `save()` 성능 차이 실측 경험
- [ ] 비동기 처리 — `@Async`, 코루틴으로 I/O 병렬화
- [ ] 페이징 최적화 — `OFFSET` 방식 vs 커서(Cursor) 기반 페이징 차이

### 보안 운영
- [ ] 비밀값(Secrets) 관리 — 코드에 하드코딩 금지, Vault / KMS 활용
- [ ] 감사 로그(Audit Log) — 금융 거래 모든 행위 추적 가능
- [ ] 취약점 스캔 — OWASP Dependency-Check, Snyk 정기 실행
- [ ] 침해 대응 절차 — 이상 거래 탐지(FDS) 시 계좌 즉시 차단 절차 이해

---

## 4. 성장 로드맵

### 주니어 백엔드 (Junior, 0~2년)
```
핵심 역량
  ├─ 스프링 부트 기본 CRUD API 독립 구현
  ├─ JPA 매핑 및 기본 쿼리 작성
  ├─ 단위 테스트 + MockMvc 작성
  └─ Git 기반 코드 리뷰 참여

이 단계의 증권사 업무
  ├─ 주문 조회 API 개발
  ├─ 입출금 내역 페이징 조회
  └─ 기존 레거시 코드 분석 및 버그 수정

목표 체크
  [ ] 혼자서 REST API 엔드포인트를 설계·구현·테스트할 수 있다
  [ ] PR 피드백을 이해하고 코드를 개선할 수 있다
  [ ] 로컬 Docker 환경을 스스로 구성할 수 있다
```

### 미들 백엔드 (Middle, 2~5년)
```
핵심 역량
  ├─ 동시성 문제 진단 및 해결 (락 전략 선택)
  ├─ Kafka 이벤트 기반 서비스 설계·구현
  ├─ 성능 병목 프로파일링 (EXPLAIN ANALYZE, 메트릭)
  ├─ 멀티모듈 아키텍처 설계 참여
  └─ 신입/주니어 코드 리뷰 및 멘토링

이 단계의 증권사 업무
  ├─ 체결 이벤트 처리 파이프라인 구현
  ├─ 잔고·손익 계산 서비스 개발
  ├─ 대용량 거래 내역 조회 최적화
  └─ 배포 자동화(CI/CD) 구축 참여

목표 체크
  [ ] 기술 부채를 식별하고 리팩터링 계획을 수립할 수 있다
  [ ] 장애 상황에서 원인을 스스로 찾아 조치할 수 있다
  [ ] 신규 기능의 테스트 전략을 주도적으로 설계할 수 있다
  [ ] 아키텍처 결정 이유를 문서로 남기고 팀과 공유할 수 있다
```

### 시니어 백엔드 (Senior, 5년+)
```
핵심 역량
  ├─ 시스템 전체 설계 — 비기능 요구사항 기반 기술 선택
  ├─ 장애 시나리오 사전 설계 (FMEAFail Mode & Effects Analysis)
  ├─ 플랫폼 표준화 — 공통 라이브러리, 내부 프레임워크 제공
  ├─ 기술 로드맵 수립 및 경영진 설득
  └─ 팀 생산성 향상 — DevEx(Developer Experience) 개선

이 단계의 증권사 업무
  ├─ 매칭 엔진 아키텍처 재설계 (HFT 대응)
  ├─ 리스크 관리 시스템 설계
  ├─ 레거시 모놀리스 → 마이크로서비스 마이그레이션 전략
  └─ 기술 감리·보안 아키텍처 리뷰

목표 체크
  [ ] "왜 이 기술을 선택했는가"를 트레이드오프 관점에서 설명할 수 있다
  [ ] 10배 트래픽 증가 시 시스템 변화 포인트를 사전에 식별할 수 있다
  [ ] 팀 문화와 프로세스를 개선하는 제안을 지속적으로 낼 수 있다
  [ ] 기술 선택이 비즈니스 목표와 어떻게 연결되는지 설명할 수 있다
```

---

## 5. 추천 심화 주제

### 고성능 매칭 엔진
- **LMAX Disruptor** — Lock-free Ring Buffer 기반 고처리량 큐
- **Off-Heap Memory** — GC 영향 없는 메모리 관리 (Chronicle Map)
- **Aeron** — 초저지연 메시지 전송 라이브러리
- 연구 자료: Martin Thompson의 "Mechanical Sympathy" 블로그

### 분산 시스템
- **CAP 정리** — 일관성 vs 가용성 트레이드오프 실제 사례
- **Raft / Paxos** — 분산 합의(Consensus) 알고리즘 이해
- **Saga 패턴** — 마이크로서비스 간 분산 트랜잭션 처리
- **CRDTs** — 충돌 없는 분산 데이터 타입

### 데이터 엔지니어링
- **TimescaleDB** — 시계열 데이터 최적화 (OHLCV 차트)
- **Apache Flink** — 실시간 스트림 처리, 슬라이딩 윈도우 집계
- **dbt** — 데이터 변환 파이프라인, 손익 집계 자동화
- **Apache Iceberg** — 대용량 거래 데이터 레이크하우스

### 클라우드·인프라
- **Kubernetes Operator** — 커스텀 리소스로 매칭 엔진 관리
- **Service Mesh (Istio)** — 서비스 간 mTLS, 트래픽 제어
- **FinOps** — 클라우드 비용 최적화 (Reserved Instance, Spot)
- **AWS Well-Architected Framework** — 금융 워크로드 5가지 기둥

### 금융 공학
- **FIX Protocol** — 금융 정보 교환 표준 프로토콜 (주문 전송)
- **SWIFT gpi** — 국제 송금 추적 표준
- **바젤 III 자본 규제** — 리스크 가중 자산(RWA) 계산 개념
- **Black-Scholes** — 옵션 가격 결정 모델 (파생상품 필수)

---

## 6. 추천 도서·자료 카테고리

### 시스템 설계
| 카테고리 | 추천 대상 |
|----------|-----------|
| Designing Data-Intensive Applications (Martin Kleppmann) | 분산 시스템·데이터 설계의 바이블 |
| Release It! (Michael Nygard) | 프로덕션 레디 시스템 설계 패턴 |
| Building Microservices (Sam Newman) | 마이크로서비스 분해·운영 실전 |
| Clean Architecture (Robert Martin) | 레이어 설계, 의존성 역전 원칙 |

### 코틀린·JVM
| 카테고리 | 추천 대상 |
|----------|-----------|
| Kotlin in Action (Jemerov, Isakova) | 코틀린 언어 심화 이해 |
| Java Concurrency in Practice (Brian Goetz) | JVM 동시성 불변의 교과서 |
| Effective Java (Joshua Bloch) | 자바/JVM 고품질 코드 원칙 |

### 금융 도메인
| 카테고리 | 추천 대상 |
|----------|-----------|
| Trading and Exchanges (Larry Harris) | 시장 미시구조·매칭 엔진 이론 |
| Algorithmic Trading (Ernest Chan) | 퀀트·알고리즘 트레이딩 기초 |
| 증권업무 길라잡이 (증권연수원) | 국내 증권사 실무 프로세스 |

### 온라인 자료
| 자료 | 설명 |
|------|------|
| Martin Fowler's bliki (martinfowler.com) | 마이크로서비스·DDD 패턴 원전 |
| High Scalability Blog | 대규모 시스템 사례 연구 |
| Confluent Blog | Kafka 운영·패턴 심화 자료 |
| InfoQ Architecture (infoq.com) | 실제 회사 기술 아키텍처 발표 |
| AWS Architecture Center | 금융 워크로드 레퍼런스 아키텍처 |

---

## 7. 면접·실무 자주 나오는 질문

### 기술 기초

**Q1. 지정가 주문과 시장가 주문의 차이를, 매칭 엔진 구현 관점에서 설명하세요.**
> 포인트: 지정가는 호가창에 삽입되어 매칭을 기다리지만, 시장가는 호가창 반대편을 즉시 소비한다. 시장가 주문의 잔량은 취소 처리됨.

**Q2. 평단가 계산 시 BigDecimal을 써야 하는 이유는?**
> 포인트: 부동소수점(double) 연산의 오차 — `0.1 + 0.2 ≠ 0.3`. 금융 계산은 정밀도가 법적 의무이므로 `BigDecimal`과 `HALF_UP` 반올림 필수.

**Q3. 동일 계좌에서 동시에 주문이 들어올 때 예수금 이중 차감을 어떻게 방지하나요?**
> 포인트: `SELECT FOR UPDATE` (비관적 락), 또는 DB `CHECK` 제약 + `UPDATE ... WHERE available_cash >= amount` 조건부 업데이트.

**Q4. Kafka 컨슈머 그룹에서 파티션 수와 컨슈머 수의 관계는?**
> 포인트: 파티션 수가 상한선. 컨슈머가 파티션보다 많으면 유휴(idle) 컨슈머 발생. 파티션 수 = 최대 병렬 소비자 수.

**Q5. 트랜잭션 격리 수준 Read Committed에서 발생할 수 있는 문제는?**
> 포인트: Non-Repeatable Read — 같은 트랜잭션 내 같은 행을 두 번 읽으면 다른 값이 나올 수 있음. Repeatable Read로 해결.

### 시스템 설계

**Q6. 주문 처리량을 현재 1,000 TPS에서 10,000 TPS로 늘려야 한다면 어떻게 접근하겠습니까?**
> 포인트: 병목 지점 먼저 측정 (APM, 프로파일러) → 수직 확장(Scale-Up) 우선 → 파티셔닝 키 재검토 → DB 커넥션 풀 튜닝 → 매칭 엔진 Lock-free 자료구조 전환 → 읽기 레플리카 분리.

**Q7. 체결 이벤트가 Kafka에서 중복으로 소비될 때 원장에 이중 반영되지 않으려면?**
> 포인트: 멱등성 키 (`executionId`) DB UNIQUE 제약 + 처리 전 중복 체크. 또는 Kafka 트랜잭셔널 프로듀서 + `exactly-once` 설정.

**Q8. 마이크로서비스 환경에서 서비스 간 데이터 정합성을 어떻게 보장하나요?**
> 포인트: 분산 트랜잭션(2PC) 대신 Saga 패턴 — Choreography(이벤트 기반) 또는 Orchestration(오케스트레이터). 보상 트랜잭션(Compensating Transaction) 설계 필수.

**Q9. 거래 내역이 10억 건을 넘어서 조회가 느려졌습니다. 어떻게 해결하겠습니까?**
> 포인트: ① 날짜 기반 Range 파티셔닝 → 오래된 파티션 아카이빙 ② 커서 기반 페이징(OFFSET 제거) ③ 집계 테이블(daily_summary) 분리 ④ 읽기 전용 레플리카 + CQRS.

**Q10. WebSocket 연결이 수만 개일 때 시세 브로드캐스트 성능을 어떻게 유지하나요?**
> 포인트: ① 종목별 구독 필터링 — 관심 없는 시세는 전송하지 않음 ② Redis Pub/Sub로 여러 서버 인스턴스 간 메시지 공유 ③ 연결당 쓰레드 → 논블로킹 이벤트 루프(WebFlux, Netty).

### 코드 리뷰 단골 지적

**Q11. 이 코드의 문제점은?**
```kotlin
// 면접에서 실제로 보여주는 코드
fun transfer(fromId: Long, toId: Long, amount: BigDecimal) {
    val from = accountRepo.findById(fromId)!!
    val to = accountRepo.findById(toId)!!
    accountRepo.save(from.copy(balance = from.balance - amount))
    accountRepo.save(to.copy(balance = to.balance + amount))
}
```
> 포인트: ① `@Transactional` 없어서 중간 실패 시 롤백 안 됨 ② 락 없어서 동시 이체 시 Race Condition ③ `fromId < toId` 순으로 락 획득 안 하면 데드락 가능 ④ `amount <= 0` 검증 없음 ⑤ `!!` NPE 위험.

---

## 8. 마지막 메시지

이 코스를 끝까지 완주한 당신에게.

증권사 백엔드 개발은 단순히 코드를 작성하는 일이 아닙니다.  
사람들의 소중한 자산이 초당 수천 번 이동하는 시스템을 책임지는 일입니다.

당신이 작성한 `BigDecimal` 한 줄, `@Transactional` 하나, `SELECT FOR UPDATE` 하나가  
실제로 누군가의 퇴직금이 안전하게 보호되는지를 결정합니다.

기술적 완벽함을 추구하는 것도 중요하지만,  
더 중요한 것은 **왜 이 시스템이 존재하는가**를 항상 기억하는 것입니다.

---

**성장의 진짜 신호는 "코드를 얼마나 잘 쓰는가"가 아니라**  
**"내 코드가 실패했을 때 어떻게 복구되는가를 설계했는가"입니다.**

모르는 것이 생겼을 때 솔직하게 인정하고 배우는 개발자,  
시스템 장애 앞에서 당황하지 않고 차분히 추적하는 개발자,  
팀원의 코드를 존중하면서도 날카롭게 리뷰하는 개발자 —  
그 개발자가 되는 여정이 지금부터 시작입니다.

**코드는 계속 작성하세요. 시스템은 계속 부서질 것이고, 당신은 계속 강해질 것입니다.**

---

이전: [46. 캡스톤: 미니 증권 매매 시스템 만들기](46-capstone-project.md) · [전체 커리큘럼](../CURRICULUM.md)
