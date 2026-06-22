# 🎓 증권사 개발·CS 지식 베이스 커리큘럼

> 투자 경험만 있는 입문자가 **증권사 백엔드 개발자(Kotlin)** 로 완성되기까지.
> 이 순서대로 읽고 예제를 돌리면 초보 → 중급 → 고급 → 초심화까지 전부 익힐 수 있습니다.

## 학습 지도

```
LEVEL 0  개발자 기초 소양        (16~17)  ← 환경/JVM
LEVEL 1  입문 큰 그림            (01~09)  ← 기존 입문편
LEVEL 2  코틀린 언어 완전정복     (18~21)  ← 언어 마스터
LEVEL 3  생태계: 빌드·테스트      (22~24)  ← Gradle/테스트/자바연동
LEVEL 4  스프링 백엔드           (03,25~27)
LEVEL 5  동시성·비동기           (11,13,28~29)
LEVEL 6  증권 도메인 완전정복     (04,30~36)
LEVEL 7  시스템 설계 (초심화)     (08,12,15,37~41)
LEVEL 8  운영·보안 (초심화)       (05,42~45)
LEVEL 9  개발자 CS 종합          (58~120)  ← 백엔드/프론트/인프라/보안/SRE
LEVEL 10 캡스톤: 통합 실전        (46~47)
```

---

## LEVEL 0 — 개발자 기초 소양 (처음이라면 여기부터)

| # | 문서 | 내용 |
|---|------|------|
| 16 | [개발 환경 구축](16-dev-environment) | JDK, IntelliJ, Gradle, Git, 프로젝트 구조 |
| 17 | [JVM 동작 원리](17-jvm-fundamentals) | 바이트코드, 클래스로더, GC, 메모리 구조 |

## LEVEL 1 — 입문: 큰 그림 (기존 입문편)

| # | 문서 | 내용 |
|---|------|------|
| 01 | [증권 개발 개요](01-overview) | 증권사 5개 영역 |
| 02 | [코틀린 기초](02-kotlin-basics) | 문법 첫걸음 |
| 03 | [스프링 부트 입문](03-spring-boot) | 계층 구조 |
| 04 | [증권 도메인 입문](04-securities-domain) | 주문 생명주기 |
| 05 | [실무 환경](05-work-environment) | 규제/보안/문화 |
| 06 | [30일 로드맵](06-roadmap) | 학습 계획 |
| 07 | [실습: 주문 처리기](07-order-system-tutorial) | 코드로 배우기 |
| 08 | [아키텍처 개요](08-architecture) | 주문/체결 시스템 |
| 09 | [용어집](09-glossary) | 증권 용어 사전 |

## LEVEL 2 — 코틀린 언어 완전정복

| # | 문서 | 내용 |
|---|------|------|
| 18 | [타입 시스템과 함수](18-kotlin-types-and-functions) | 타입, 고차함수, 람다, 확장함수 |
| 19 | [객체지향과 설계](19-kotlin-oop) | class/interface/sealed/object/위임 |
| 20 | [컬렉션과 함수형](20-kotlin-collections) | List/Set/Map, 시퀀스, 함수형 연산 |
| 21 | [제네릭과 관용구](21-kotlin-generics-and-idioms) | 제네릭, 스코프함수, idioms |

## LEVEL 3 — 생태계: 빌드 · 테스트 · 상호운용

| # | 문서 | 내용 |
|---|------|------|
| 22 | [Gradle 빌드 시스템](22-gradle-build) | 의존성, 멀티모듈, 태스크 |
| 23 | [테스트 완전정복](23-testing) | JUnit5, MockK, Kotest, 전략 |
| 24 | [자바 상호운용](24-java-interop) | 레거시 자바와 코틀린 함께 쓰기 |

## LEVEL 4 — 스프링 백엔드 심화

| # | 문서 | 내용 |
|---|------|------|
| 03 | [스프링 부트 입문](03-spring-boot) | (복습) |
| 25 | [스프링 코어](25-spring-core) | DI/IoC/Bean 생명주기/AOP |
| 26 | [웹 계층: MVC/WebFlux](26-spring-boot-web) | 요청처리, 검증, 예외, 응답 |
| 27 | [데이터 접근](27-data-access) | JPA 심화, N+1, MyBatis, 트랜잭션 |

## LEVEL 5 — 동시성 · 비동기

| # | 문서 | 내용 |
|---|------|------|
| 11 | [동시성과 잔고 정합성](11-concurrency) | (입문 심화) |
| 28 | [JVM 동시성 깊게](28-jvm-concurrency) | 스레드, JMM, 락, 동시성 컬렉션 |
| 13 | [코루틴 입문](13-coroutines) | (복습) |
| 29 | [코루틴 완전정복](29-coroutines-deep) | 구조화된 동시성, Flow, 디스패처 |

## LEVEL 6 — 증권 도메인 완전정복

| # | 문서 | 내용 |
|---|------|------|
| 04 | [증권 도메인 입문](04-securities-domain) | (복습) |
| 30 | [시장 구조와 매매제도](30-market-structure) | KRX, 호가, VI, 서킷브레이커 |
| 31 | [주문 유형 전체](31-order-types) | 지정가/시장가/IOC/FOK/조건부, 정정취소 |
| 32 | [청산·결제·정산](32-clearing-settlement) | T+2, 청산, 대사 |
| 33 | [계좌·원장·손익](33-account-ledger) | 평단가/평가손익/실현손익/세금 |
| 34 | [신용·파생 기초](34-credit-derivatives) | 미수/신용/대주/선물옵션 기초 |
| 35 | [시세·시장데이터](35-market-data) | 실시간 시세, 차트, 기술지표 |
| 36 | [리스크·컴플라이언스](36-risk-compliance) | 한도, 이상거래, 규제 대응 |

## LEVEL 7 — 시스템 설계 (초심화)

| # | 문서 | 내용 |
|---|------|------|
| 08 | [아키텍처 개요](08-architecture) | (복습) |
| 12 | [호가창 자료구조](12-orderbook) | (복습) |
| 37 | [저지연 시스템 설계](37-low-latency) | GC튜닝, 메모리, Disruptor |
| 38 | [이벤트 소싱과 CQRS](38-event-sourcing-cqrs) | 이벤트 저장, 읽기/쓰기 분리 |
| 39 | [분산 시스템과 일관성](39-distributed-systems) | CAP, 사가, 분산 트랜잭션 |
| 40 | [캐시 전략과 Redis](40-caching-redis) | 캐시 패턴, 시세 캐싱 |
| 15 | [이벤트 기반/Kafka](15-event-driven) | (복습) |
| 41 | [고가용성과 장애복구](41-high-availability) | 이중화, 페일오버, DR |

## LEVEL 8 — 운영 · 보안 (초심화)

| # | 문서 | 내용 |
|---|------|------|
| 05 | [실무 환경](05-work-environment) | (복습) |
| 42 | [관측성](42-observability) | 로깅/메트릭/분산추적 |
| 43 | [성능 튜닝](43-performance-tuning) | 프로파일링, JVM/DB 튜닝 |
| 44 | [보안](44-security) | 인증/인가/암호화/규제/망분리 |
| 45 | [CI/CD와 배포](45-cicd-deploy) | 파이프라인, 무중단 배포, 롤백 |


## LEVEL 9 — 개발자 CS 종합: 백엔드 · 프론트엔드 · 인프라

| # | 문서 | 내용 |
|---|------|------|
| 58 | [개발자 CS 핵심 총정리](docs/58-cs-core.md) | 자료구조/알고리즘/OS/메모리/동시성 |
| 59 | [네트워크와 웹 프로토콜](docs/59-networking-web-protocols.md) | TCP/IP, DNS, TLS, HTTP, CORS, CDN |
| 60 | [백엔드 엔지니어링](docs/60-backend-engineering.md) | API, 인증/인가, 트랜잭션, 메시징, 테스트 |
| 61 | [프론트엔드 엔지니어링](docs/61-frontend-engineering.md) | 브라우저, HTML/CSS/JS, 상태, 접근성, 성능 |
| 62 | [데이터베이스와 저장소](docs/62-data-storage.md) | RDBMS, 인덱스, 트랜잭션, 캐시, 백업 |
| 63 | [리눅스와 런타임 운영](docs/63-linux-runtime.md) | 프로세스, 파일, 로그, 컨테이너, JVM/Node 운영 |
| 64 | [인프라와 클라우드](docs/64-infra-cloud.md) | VPC, LB, Kubernetes, IaC, 확장성과 비용 |
| 65 | [DevOps와 SRE](docs/65-devops-sre.md) | SLO, 알림, 배포 전략, 장애 대응, 포스트모템 |
| 66 | [보안 엔지니어링](docs/66-security-engineering.md) | 인증, 인가, 암호화, 웹/API/공급망 보안 |
| 67 | [시스템 설계와 아키텍처](docs/67-architecture-design.md) | 품질 속성, 일관성, 확장, 장애 설계 |
| 68 | [컴퓨터 구조와 운영체제 심화](docs/68-computer-architecture-os-deep.md) | CPU/메모리/커널/I/O/GC와 런타임 |
| 69 | [알고리즘 문제 해결과 실무 적용](docs/69-algorithms-problem-solving.md) | 정렬/탐색/해시/그래프/DP 실무화 |
| 70 | [API 계약과 시스템 연동](docs/70-api-contracts-integration.md) | 스키마/버전/오류/멱등성/Webhook |
| 71 | [테스트와 품질 엔지니어링](docs/71-testing-quality-engineering.md) | 테스트 피라미드/E2E/성능·보안 테스트 |
| 72 | [성능과 확장성 엔지니어링](docs/72-performance-scalability.md) | p95/p99/병목/캐시/수평 확장/부하 테스트 |
| 73 | [고급 프론트엔드 플랫폼](docs/73-advanced-frontend-platform.md) | SSR/SSG/hydration/번들러/디자인 시스템 |
| 74 | [메시징·큐·스트리밍](docs/74-messaging-streaming.md) | Kafka/RabbitMQ/SQS/outbox/saga/consumer 설계 |
| 75 | [데이터 엔지니어링과 분석](docs/75-data-engineering-analytics.md) | OLTP/OLAP/이벤트/ETL·ELT/데이터 품질 |
| 76 | [디버깅과 트러블슈팅](docs/76-debugging-troubleshooting.md) | 로그/메트릭/트레이스/장애 축소 방법론 |
| 77 | [협업과 유지보수](docs/77-engineering-collaboration.md) | 요구사항/설계문서/Git/리뷰/리팩터링 |
| 78 | [개발자 CS 커버리지 감사표](docs/78-developer-cs-coverage-audit.md) | 커버리지 감사/빠진 영역/반복 검증 |
| 79 | [컴파일러와 빌드 시스템 내부](docs/79-compiler-build-systems.md) | 컴파일 파이프라인/타입 검사/의존성/CI |
| 80 | [분산 합의와 복제](docs/80-distributed-consensus-replication.md) | Raft/quorum/replication/failover/split brain |
| 81 | [저장 엔진 내부 구조](docs/81-storage-engine-internals.md) | B-Tree/LSM/WAL/MVCC/buffer pool |
| 82 | [관측성 심화와 OpenTelemetry](docs/82-observability-deep.md) | logs/metrics/traces/OpenTelemetry/SLO 알림 |
| 83 | [브라우저와 JavaScript 엔진 내부](docs/83-browser-js-engine-internals.md) | 브라우저 프로세스/V8/event loop/GC |
| 84 | [모바일과 네이티브 클라이언트 기본](docs/84-mobile-native-engineering.md) | Android/iOS/lifecycle/offline/push/mobile security |
| 85 | [AI/ML 엔지니어링 기본](docs/85-ai-ml-engineering.md) | training/inference/RAG/LLM ops/evaluation |
| 86 | [검색, 추천, 벡터 데이터베이스](docs/86-search-recommendation-vector.md) | 역색인/ranking/recommendation/vector search |
| 87 | [실시간 협업 시스템](docs/87-realtime-collaboration-systems.md) | WebSocket/SSE/CRDT/OT/fan-out |
| 88 | [개인정보, 규제, 데이터 거버넌스](docs/88-privacy-compliance-governance.md) | 개인정보/보관/파기/접근감사/거버넌스 |
| 89 | [제품 분석과 실험 플랫폼](docs/89-product-analytics-experimentation.md) | event/funnel/cohort/A-B test/feature flag |
| 90 | [DDD와 모듈러 아키텍처](docs/90-ddd-modular-architecture.md) | bounded context/aggregate/domain event/hexagonal |
| 91 | [공급망 보안과 릴리즈 엔지니어링](docs/91-supply-chain-release-engineering.md) | SBOM/SLSA/CI 보안/artifact/release |
| 92 | [실무 암호학](docs/92-applied-cryptography.md) | hash/password hash/AEAD/KMS/key rotation |
| 93 | [위협 모델링과 보안 설계](docs/93-threat-modeling-secure-design.md) | STRIDE/trust boundary/attack tree/security review |
| 94 | [접근성과 포용적 디자인 심화](docs/94-accessibility-inclusive-design.md) | WCAG/keyboard/screen reader/form accessibility |
| 95 | [국제화, 현지화, 시간·숫자 처리](docs/95-internationalization-localization.md) | i18n/l10n/timezone/currency/Unicode |
| 96 | [플랫폼 엔지니어링과 개발자 경험](docs/96-platform-engineering-devex.md) | IDP/golden path/service catalog/dev productivity |
| 97 | [모노레포와 대규모 저장소 관리](docs/97-monorepo-large-repo-management.md) | monorepo/build cache/CODEOWNERS/dependency graph |
| 98 | [설정 관리와 Feature Flag](docs/98-configuration-feature-flags.md) | runtime config/secret/feature flag/kill switch |
| 99 | [카오스와 복원력 엔지니어링](docs/99-chaos-resilience-engineering.md) | chaos/game day/resilience pattern/blast radius |
| 100 | [용량 계획과 FinOps](docs/100-capacity-planning-finops.md) | capacity/headroom/unit cost/cloud cost |
| 101 | [레거시 현대화와 마이그레이션](docs/101-legacy-modernization-migration.md) | strangler/data migration/compatibility/cutover |
| 102 | [엔터프라이즈 연동 패턴](docs/102-enterprise-integration-patterns.md) | batch/file/SFTP/ESB/anti-corruption layer |
| 103 | [문서화와 지식 관리](docs/103-documentation-knowledge-management.md) | ADR/runbook/onboarding/troubleshooting/postmortem |
| 104 | [클린 코드와 유지보수성 심화](docs/104-clean-code-maintainability.md) | naming/error handling/refactoring/abstraction |
| 105 | [Edge와 Serverless 아키텍처](docs/105-edge-serverless-architecture.md) | serverless/edge/cold start/stateless/cost |
| 106 | [Kubernetes 운영 심화](docs/106-kubernetes-operations-deep.md) | control plane/workload/probe/resource/PDB |
| 107 | [Service Mesh와 API Gateway](docs/107-service-mesh-api-gateway.md) | gateway/service mesh/mTLS/routing/policy |
| 108 | [Identity, IAM, OAuth2/OIDC 심화](docs/108-identity-iam-oauth-oidc.md) | principal/OAuth2/OIDC/JWT/IAM |
| 109 | [데이터베이스 샤딩과 파티셔닝](docs/109-database-sharding-partitioning.md) | partition/sharding/key/resharding/cross-shard |
| 110 | [멀티테넌시와 SaaS 아키텍처](docs/110-multitenancy-saas-architecture.md) | tenant isolation/noisy neighbor/provisioning |
| 111 | [워크플로 오케스트레이션과 스케줄러](docs/111-workflow-orchestration-schedulers.md) | batch/scheduler/retry/checkpoint/SLA |
| 112 | [Protobuf, gRPC, Schema Registry](docs/112-protobuf-grpc-schema-registry.md) | protobuf/gRPC/schema compatibility/codegen |
| 113 | [이벤트 모델링과 감사 원장](docs/113-event-modeling-audit-ledger.md) | command/event/audit log/ledger/correction |
| 114 | [데이터 정합성, 대사, 보정](docs/114-data-consistency-reconciliation.md) | consistency/reconciliation/correction/read model |
| 115 | [고급 캐싱, CDN, Edge 전략](docs/115-advanced-caching-cdn-edge.md) | HTTP cache/CDN/invalidation/stampede |
| 116 | [고급 네트워킹과 로드밸런싱](docs/116-advanced-networking-loadbalancing.md) | L4/L7/LB/timeout/retry/network metrics |
| 117 | [재해복구와 비즈니스 연속성](docs/117-disaster-recovery-business-continuity.md) | RTO/RPO/DR/failover/BCP |
| 118 | [불변조건, 정형기법, 안전한 상태 설계](docs/118-formal-methods-invariants.md) | invariant/state machine/property test/model checking |
| 119 | [개발자 도구, CLI, 자동화](docs/119-developer-tools-cli-automation.md) | CLI/dry-run/script/codegen/internal tools |
| 120 | [종합 개발자 역량 맵과 자가 진단](docs/120-comprehensive-engineer-readiness-map.md) | role level/readiness map/self assessment |

## LEVEL 10 — 캡스톤: 통합 실전

| # | 문서 | 내용 |
|---|------|------|
| 46 | [미니 증권 시스템 설계](46-capstone-project) | 전체를 합친 실전 프로젝트 |
| 47 | [마스터 체크리스트](47-mastery-checklist) | 자가 점검 + 다음 단계 |

## 용어집 (전 분야 망라 · 어느 팀이든 대비)

| # | 문서 | 내용 |
|---|------|------|
| 09 | [용어집 완전판](09-glossary) | 20개 카테고리, 약 900개 표준 용어 |
| 50 | [백오피스·정산·대사](50-glossary-backoffice) | 정산/회계/배치/보고 |
| 51 | [파생상품 심화](51-glossary-derivatives) | 선물/옵션/그릭스/증거금(SPAN) |
| 52 | [채권 심화](52-glossary-bonds) | 국채/회사채/평가/금리 |
| 53 | [펀드·신탁·랩·구조화상품](53-glossary-funds) | 펀드/ETF/ELS/신탁 |
| 54 | [해외주식·외환](54-glossary-global) | 해외거래소/환전/세금 |
| 55 | [시세·체결시스템](55-glossary-marketdata) | 마켓데이터/매칭/차트 |
| 56 | [리스크·컴플라이언스·규제](56-glossary-risk-compliance) | 한도/FDS/AML/규제 |
| 57 | [가나다순 색인](57-glossary-index) | 892개 표제어 빠른 찾기 |

---

## 실행 가능한 예제 (검증 완료)

| 예제 | 다루는 개념 |
|------|------------|
| [OrderProcessor.kt](/examples) | 주문 검증 → 체결 → 평단가 |
| [BigDecimalGuide.kt](/examples) | 금액 계산 함정 |
| [RaceCondition.kt](/examples) | 동시성 잔고 꼬임 |
| [OrderBook.kt](/examples) | 호가창 매칭 |

## 추천 진행 방식

1. **개발이 처음**이면 LEVEL 0 → 1 순서대로.
2. **코틀린만 처음**이면 LEVEL 1(02) → 2 집중.
3. **빨리 실무 투입**이면 01~09 + LEVEL 9 CS 종합 + 본인 팀 레벨(주문계=5·7, 시세계=6·7, 원장계=6·8).
4. 각 레벨 끝에서 예제를 직접 돌려보고, 값을 바꿔 실험하세요.
5. 마지막 [47. 마스터 체크리스트](47-mastery-checklist)로 자가 점검.

---

_상세 입문은 [README](/), 전체 학습은 이 문서를 기준으로._
