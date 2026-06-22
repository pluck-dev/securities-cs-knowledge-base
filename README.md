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

### 개발자 CS 종합편

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
