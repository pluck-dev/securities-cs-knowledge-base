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
LEVEL 9  개발자 CS 종합          58~77   백엔드/프론트/인프라/보안/SRE
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
