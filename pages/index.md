# 증권사 개발·CS 지식 베이스 📈

> **투자 경험만 있는 입문자**가 **증권사 백엔드 개발자(Kotlin)** 로 완성되기까지.
> 입문 → 중급 → 고급 → 초심화 → 캡스톤, 그리고 전 분야 용어집까지.

!!! tip "이렇게 보세요"
    - 왼쪽 위 **탭**에서 레벨(입문/코틀린/스프링/동시성/도메인/설계/운영/개발자 CS/캡스톤/용어집)을 고르세요.
    - 오른쪽 위 **🔍 검색**에 한글로 용어를 치면 바로 찾아갑니다. (예: "평단가", "코루틴", "T+2")
    - 오른쪽 위 **🌙 아이콘**으로 다크 모드 전환.

## 학습 지도

```
LEVEL 0  개발자 기초 소양        16~17   환경/JVM
LEVEL 1  입문 큰 그림            01~09   증권 개발 전체 조망
LEVEL 2  코틀린 언어 완전정복     18~21   언어 마스터
LEVEL 3  생태계: 빌드·테스트      22~24   Gradle/테스트/자바연동
LEVEL 4  스프링 백엔드           25~27   DI/웹/데이터
LEVEL 5  동시성·비동기           28~29   스레드/코루틴
LEVEL 6  증권 도메인 완전정복     30~36   시장/주문/정산/원장/리스크
LEVEL 7  시스템 설계 (초심화)     37~41   저지연/CQRS/분산/캐시/HA
LEVEL 8  운영·보안 (초심화)       42~45   관측성/성능/보안/배포
LEVEL 9  개발자 CS 종합          58~120   백엔드/프론트/인프라/보안/SRE
LEVEL 10 캡스톤: 통합 실전        46~47   미니 증권 시스템 만들기
```

## 어디서부터 시작할까?

=== "개발이 처음이라면"
    [16. 개발 환경 구축](16-dev-environment) → [17. JVM 원리](17-jvm-fundamentals) → [01. 큰 그림](01-overview) 순서로.

=== "코틀린만 처음이라면"
    [01. 큰 그림](01-overview) → [02. 코틀린 기초](02-kotlin-basics) → 코틀린 레벨(18~21) 집중.

=== "빨리 실무 투입"
    입문(01~09) 정독 후, 배치된 팀 레벨로:
    주문계 → LEVEL 5·7, 시세계 → LEVEL 6·7, 원장계 → LEVEL 6·8.

## 핵심 3축

1. **CS 기본기** — 자료구조, 알고리즘, OS, 네트워크, 동시성
2. **제품 개발 역량** — 백엔드 API, 프론트엔드 UX, 데이터, 인프라, 운영
3. **증권 도메인** — 주문 생명주기 + 용어 + "돈을 다루는 철칙"

!!! note "투자 경험이 강점입니다"
    증권 개발에서 가장 어려운 게 도메인 이해인데, 투자를 해봤다면 절반은 깔고 들어갑니다.

## 개발자가 알아야 할 CS 종합

- [58. CS 핵심 총정리](58-cs-core.md) — 자료구조, 알고리즘, OS, 메모리, 동시성
- [59. 네트워크와 웹 프로토콜](59-networking-web-protocols.md) — TCP/IP, DNS, TLS, HTTP, CORS, CDN
- [60. 백엔드 엔지니어링](60-backend-engineering.md) — API, 인증/인가, 트랜잭션, 메시징, 테스트
- [61. 프론트엔드 엔지니어링](61-frontend-engineering.md) — 브라우저, 접근성, 상태, 성능, 보안
- [62. 데이터베이스와 저장소](62-data-storage.md) — SQL, 인덱스, 트랜잭션, 캐시, 백업
- [63. 리눅스와 런타임 운영](63-linux-runtime.md) — 프로세스, 파일, 로그, 컨테이너, JVM/Node 운영
- [64. 인프라와 클라우드](64-infra-cloud.md) — VPC, LB, Kubernetes, IaC, 확장성과 비용
- [65. DevOps와 SRE](65-devops-sre.md) — SLO, 알림, 배포, 장애 대응, 포스트모템
- [66. 보안 엔지니어링](66-security-engineering.md) — 인증, 인가, 암호화, 웹/API/공급망 보안
- [67. 시스템 설계와 아키텍처](67-architecture-design.md) — 품질 속성, 일관성, 확장, 장애 설계
- [68. 컴퓨터 구조와 운영체제 심화](68-computer-architecture-os-deep.md) — CPU, 메모리, 커널, I/O, GC와 런타임
- [69. 알고리즘 문제 해결과 실무 적용](69-algorithms-problem-solving.md) — 정렬, 탐색, 해시, 그래프, DP 실무화
- [70. API 계약과 시스템 연동](70-api-contracts-integration.md) — 스키마, 버전, 오류, 멱등성, Webhook
- [71. 테스트와 품질 엔지니어링](71-testing-quality-engineering.md) — 테스트 피라미드, E2E, 성능/보안 테스트
- [72. 성능과 확장성 엔지니어링](72-performance-scalability.md) — p95/p99, 병목, 캐시, 수평 확장, 부하 테스트
- [73. 고급 프론트엔드 플랫폼](73-advanced-frontend-platform.md) — SSR/SSG, hydration, 번들러, 디자인 시스템
- [74. 메시징·큐·스트리밍](74-messaging-streaming.md) — Kafka/RabbitMQ/SQS, outbox, saga, consumer 설계
- [75. 데이터 엔지니어링과 분석](75-data-engineering-analytics.md) — OLTP/OLAP, 이벤트, ETL/ELT, 데이터 품질
- [76. 디버깅과 트러블슈팅](76-debugging-troubleshooting.md) — 로그, 메트릭, 트레이스, 장애 축소 방법론
- [77. 협업과 유지보수](77-engineering-collaboration.md) — 요구사항, 설계문서, Git, 리뷰, 리팩터링
- [78. 개발자 CS 커버리지 감사표](78-developer-cs-coverage-audit.md) — 커버리지 감사/빠진 영역/반복 검증
- [79. 컴파일러와 빌드 시스템 내부](79-compiler-build-systems.md) — 컴파일 파이프라인/타입 검사/의존성/CI
- [80. 분산 합의와 복제](80-distributed-consensus-replication.md) — Raft/quorum/replication/failover/split brain
- [81. 저장 엔진 내부 구조](81-storage-engine-internals.md) — B-Tree/LSM/WAL/MVCC/buffer pool
- [82. 관측성 심화와 OpenTelemetry](82-observability-deep.md) — logs/metrics/traces/OpenTelemetry/SLO 알림
- [83. 브라우저와 JavaScript 엔진 내부](83-browser-js-engine-internals.md) — 브라우저 프로세스/V8/event loop/GC
- [84. 모바일과 네이티브 클라이언트 기본](84-mobile-native-engineering.md) — Android/iOS/lifecycle/offline/push/mobile security
- [85. AI/ML 엔지니어링 기본](85-ai-ml-engineering.md) — training/inference/RAG/LLM ops/evaluation
- [86. 검색, 추천, 벡터 데이터베이스](86-search-recommendation-vector.md) — 역색인/ranking/recommendation/vector search
- [87. 실시간 협업 시스템](87-realtime-collaboration-systems.md) — WebSocket/SSE/CRDT/OT/fan-out
- [88. 개인정보, 규제, 데이터 거버넌스](88-privacy-compliance-governance.md) — 개인정보/보관/파기/접근감사/거버넌스
- [89. 제품 분석과 실험 플랫폼](89-product-analytics-experimentation.md) — event/funnel/cohort/A-B test/feature flag
- [90. DDD와 모듈러 아키텍처](90-ddd-modular-architecture.md) — bounded context/aggregate/domain event/hexagonal
- [91. 공급망 보안과 릴리즈 엔지니어링](91-supply-chain-release-engineering.md) — SBOM/SLSA/CI 보안/artifact/release
- [92. 실무 암호학](92-applied-cryptography.md) — hash/password hash/AEAD/KMS/key rotation
- [93. 위협 모델링과 보안 설계](93-threat-modeling-secure-design.md) — STRIDE/trust boundary/attack tree/security review
- [94. 접근성과 포용적 디자인 심화](94-accessibility-inclusive-design.md) — WCAG/keyboard/screen reader/form accessibility
- [95. 국제화, 현지화, 시간·숫자 처리](95-internationalization-localization.md) — i18n/l10n/timezone/currency/Unicode
- [96. 플랫폼 엔지니어링과 개발자 경험](96-platform-engineering-devex.md) — IDP/golden path/service catalog/dev productivity
- [97. 모노레포와 대규모 저장소 관리](97-monorepo-large-repo-management.md) — monorepo/build cache/CODEOWNERS/dependency graph
- [98. 설정 관리와 Feature Flag](98-configuration-feature-flags.md) — runtime config/secret/feature flag/kill switch
- [99. 카오스와 복원력 엔지니어링](99-chaos-resilience-engineering.md) — chaos/game day/resilience pattern/blast radius
- [100. 용량 계획과 FinOps](100-capacity-planning-finops.md) — capacity/headroom/unit cost/cloud cost
- [101. 레거시 현대화와 마이그레이션](101-legacy-modernization-migration.md) — strangler/data migration/compatibility/cutover
- [102. 엔터프라이즈 연동 패턴](102-enterprise-integration-patterns.md) — batch/file/SFTP/ESB/anti-corruption layer
- [103. 문서화와 지식 관리](103-documentation-knowledge-management.md) — ADR/runbook/onboarding/troubleshooting/postmortem
- [104. 클린 코드와 유지보수성 심화](104-clean-code-maintainability.md) — naming/error handling/refactoring/abstraction
- [105. Edge와 Serverless 아키텍처](105-edge-serverless-architecture.md) — serverless/edge/cold start/stateless/cost
- [106. Kubernetes 운영 심화](106-kubernetes-operations-deep.md) — control plane/workload/probe/resource/PDB
- [107. Service Mesh와 API Gateway](107-service-mesh-api-gateway.md) — gateway/service mesh/mTLS/routing/policy
- [108. Identity, IAM, OAuth2/OIDC 심화](108-identity-iam-oauth-oidc.md) — principal/OAuth2/OIDC/JWT/IAM
- [109. 데이터베이스 샤딩과 파티셔닝](109-database-sharding-partitioning.md) — partition/sharding/key/resharding/cross-shard
- [110. 멀티테넌시와 SaaS 아키텍처](110-multitenancy-saas-architecture.md) — tenant isolation/noisy neighbor/provisioning
- [111. 워크플로 오케스트레이션과 스케줄러](111-workflow-orchestration-schedulers.md) — batch/scheduler/retry/checkpoint/SLA
- [112. Protobuf, gRPC, Schema Registry](112-protobuf-grpc-schema-registry.md) — protobuf/gRPC/schema compatibility/codegen
- [113. 이벤트 모델링과 감사 원장](113-event-modeling-audit-ledger.md) — command/event/audit log/ledger/correction
- [114. 데이터 정합성, 대사, 보정](114-data-consistency-reconciliation.md) — consistency/reconciliation/correction/read model
- [115. 고급 캐싱, CDN, Edge 전략](115-advanced-caching-cdn-edge.md) — HTTP cache/CDN/invalidation/stampede
- [116. 고급 네트워킹과 로드밸런싱](116-advanced-networking-loadbalancing.md) — L4/L7/LB/timeout/retry/network metrics
- [117. 재해복구와 비즈니스 연속성](117-disaster-recovery-business-continuity.md) — RTO/RPO/DR/failover/BCP
- [118. 불변조건, 정형기법, 안전한 상태 설계](118-formal-methods-invariants.md) — invariant/state machine/property test/model checking
- [119. 개발자 도구, CLI, 자동화](119-developer-tools-cli-automation.md) — CLI/dry-run/script/codegen/internal tools
- [120. 종합 개발자 역량 맵과 자가 진단](120-comprehensive-engineer-readiness-map.md) — role level/readiness map/self assessment


## 실행 가능한 예제

[예제 코드 페이지](examples)에서 4개의 코틀린 예제를 바로 볼 수 있습니다 (전부 컴파일·실행 검증 완료).

- **OrderProcessor** — 주문 검증 → 체결 → 평단가 재계산
- **BigDecimalGuide** — 금액 계산 함정 5가지
- **RaceCondition** — 동시성으로 잔고가 꼬이는 현장
- **OrderBook** — 호가창 + 시장가 매칭

## 용어집 (어느 팀이든 대비)

약 1,800개 용어를 분야별로 정리했습니다. [가나다순 색인](57-glossary-index)에서 모르는 용어를 바로 찾으세요.

---

_막막하면 [01. 큰 그림](01-overview)부터 천천히._
