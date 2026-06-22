# 증권사 개발·CS 지식 베이스

> 투자 경험은 있지만 개발 실무와 증권 시스템은 처음인 사람을 위한 종합 가이드.
> 코틀린·스프링 백엔드뿐 아니라 개발자가 알아야 할 CS, 프론트엔드, 인프라, 보안, SRE까지 한 흐름으로 정리했습니다.

## 핵심 6축

실무 개발은 결국 이 여섯 가지를 연결하는 일입니다.

1. **CS 기본기** — 자료구조, 알고리즘, OS, 네트워크, 동시성
2. **백엔드** — API, 트랜잭션, 데이터 접근, 메시징, 운영 가능성
3. **프론트엔드** — 브라우저 렌더링, 접근성, 상태, 성능, 보안
4. **인프라·SRE** — 리눅스, 클라우드, 배포, 관측성, 장애 대응
5. **코틀린·스프링** — JVM 기반 서버 개발 실전
6. **증권 도메인** — 주문 생명주기 + 용어 + "돈을 다루는 철칙"

> 💡 투자 경험이 있으면 3번 도메인을 절반은 깔고 들어갑니다. 가장 큰 강점이에요.

## 문서 목차

| 순서 | 문서 | 내용 |
|------|------|------|
| 01 | [큰 그림](docs/01-overview.md) | 증권사에서 개발자가 뭘 만드나 |
| 02 | [코틀린 기초](docs/02-kotlin-basics.md) | 꼭 알아야 할 코틀린 문법 |
| 03 | [스프링 부트](docs/03-spring-boot.md) | 백엔드 서버의 구조 |
| 04 | [증권 도메인](docs/04-securities-domain.md) | 주문 생명주기, 용어, 철칙 |
| 05 | [실무 환경](docs/05-work-environment.md) | 규제, 보안, 문화 |
| 06 | [30일 로드맵](docs/06-roadmap.md) | 입사 후 학습 계획 |
| 07 | [실습: 주문 처리기](docs/07-order-system-tutorial.md) | 코드로 배우는 코틀린 |
| 08 | [아키텍처](docs/08-architecture.md) | 주문/체결 시스템 전체 구조 |
| 09 | [용어집](docs/09-glossary.md) | 개발자용 증권 용어 사전 |

### 개발자 CS 종합편 (58~120)

| 순서 | 문서 | 내용 |
|------|------|------|
| 58 | [CS 핵심 총정리](docs/58-cs-core.md) | 자료구조·알고리즘·OS·동시성 기본기 |
| 59 | [네트워크와 웹 프로토콜](docs/59-networking-web-protocols.md) | TCP/IP, DNS, TLS, HTTP, CORS, CDN |
| 60 | [백엔드 엔지니어링](docs/60-backend-engineering.md) | API, 인증/인가, 트랜잭션, 메시징, 테스트 |
| 61 | [프론트엔드 엔지니어링](docs/61-frontend-engineering.md) | 브라우저, HTML/CSS/JS, 상태, 접근성, 성능 |
| 62 | [데이터베이스와 저장소](docs/62-data-storage.md) | RDBMS, 인덱스, 트랜잭션, 캐시, 백업 |
| 63 | [리눅스와 런타임 운영](docs/63-linux-runtime.md) | 프로세스, 파일, 로그, 컨테이너, JVM/Node 운영 |
| 64 | [인프라와 클라우드](docs/64-infra-cloud.md) | VPC, LB, Kubernetes, IaC, 확장성과 비용 |
| 65 | [DevOps와 SRE](docs/65-devops-sre.md) | SLO, 알림, 배포, 장애 대응, 포스트모템 |
| 66 | [보안 엔지니어링](docs/66-security-engineering.md) | 인증, 인가, 암호화, 웹/API/공급망 보안 |
| 67 | [시스템 설계와 아키텍처](docs/67-architecture-design.md) | 품질 속성, 일관성, 확장, 장애 설계 |
| 68 | [컴퓨터 구조와 운영체제 심화](docs/68-computer-architecture-os-deep.md) | CPU, 메모리, 커널, I/O, GC와 런타임 |
| 69 | [알고리즘 문제 해결과 실무 적용](docs/69-algorithms-problem-solving.md) | 정렬, 탐색, 해시, 그래프, DP 실무화 |
| 70 | [API 계약과 시스템 연동](docs/70-api-contracts-integration.md) | 스키마, 버전, 오류, 멱등성, Webhook |
| 71 | [테스트와 품질 엔지니어링](docs/71-testing-quality-engineering.md) | 테스트 피라미드, E2E, 성능/보안 테스트 |
| 72 | [성능과 확장성 엔지니어링](docs/72-performance-scalability.md) | p95/p99, 병목, 캐시, 수평 확장, 부하 테스트 |
| 73 | [고급 프론트엔드 플랫폼](docs/73-advanced-frontend-platform.md) | SSR/SSG, hydration, 번들러, 디자인 시스템 |
| 74 | [메시징·큐·스트리밍](docs/74-messaging-streaming.md) | Kafka/RabbitMQ/SQS, outbox, saga, consumer 설계 |
| 75 | [데이터 엔지니어링과 분석](docs/75-data-engineering-analytics.md) | OLTP/OLAP, 이벤트, ETL/ELT, 데이터 품질 |
| 76 | [디버깅과 트러블슈팅](docs/76-debugging-troubleshooting.md) | 로그, 메트릭, 트레이스, 장애 축소 방법론 |
| 77 | [협업과 유지보수](docs/77-engineering-collaboration.md) | 요구사항, 설계문서, Git, 리뷰, 리팩터링 |
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

### 심화편 (실무에서 진짜 골치 아픈 것들)

| 순서 | 문서 | 내용 |
|------|------|------|
| 10 | [금액 계산 완전정복](docs/10-bigdecimal-deep.md) | BigDecimal 함정 5가지 |
| 11 | [동시성과 잔고 정합성](docs/11-concurrency.md) | 왜 잔고가 꼬이나 + 해결 |
| 12 | [호가창 자료구조](docs/12-orderbook.md) | Order Book + 매칭 엔진 |
| 13 | [코루틴과 비동기](docs/13-coroutines.md) | suspend / async / Flow |
| 14 | [스프링 REST API 버전](docs/14-rest-api.md) | 콘솔 예제 → 실제 서버 API |
| 15 | [이벤트 기반 아키텍처](docs/15-event-driven.md) | 메시지 큐(Kafka) |

## 실행 가능한 예제

설치 없이 [play.kotlinlang.org](https://play.kotlinlang.org) 에 붙여넣고 바로 실행 가능합니다.
(모두 실제 컴파일·실행 검증 완료)

- [`examples/OrderProcessor.kt`](examples/OrderProcessor.kt) — 주문 처리기 (검증 → 체결 → 평단가 재계산)
- [`examples/BigDecimalGuide.kt`](examples/BigDecimalGuide.kt) — 금액 계산 함정 5가지 시연
- [`examples/RaceCondition.kt`](examples/RaceCondition.kt) — 동시성으로 잔고가 꼬이는 현장
- [`examples/OrderBook.kt`](examples/OrderBook.kt) — 호가창 + 시장가 매칭

> 로컬 실행: `kotlinc 파일.kt -include-runtime -d a.jar && java -jar a.jar`

## 추천 학습 자료

- [resources.md](resources.md) 참고

---

_시작은 [01-overview.md](docs/01-overview.md) 부터._
