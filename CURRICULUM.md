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
LEVEL 9  개발자 CS 종합          (58~67)  ← 백엔드/프론트/인프라/보안/SRE
LEVEL 10 캡스톤: 통합 실전        (46~47)
```

---

## LEVEL 0 — 개발자 기초 소양 (처음이라면 여기부터)

| # | 문서 | 내용 |
|---|------|------|
| 16 | [개발 환경 구축](docs/16-dev-environment.md) | JDK, IntelliJ, Gradle, Git, 프로젝트 구조 |
| 17 | [JVM 동작 원리](docs/17-jvm-fundamentals.md) | 바이트코드, 클래스로더, GC, 메모리 구조 |

## LEVEL 1 — 입문: 큰 그림 (기존 입문편)

| # | 문서 | 내용 |
|---|------|------|
| 01 | [증권 개발 개요](docs/01-overview.md) | 증권사 5개 영역 |
| 02 | [코틀린 기초](docs/02-kotlin-basics.md) | 문법 첫걸음 |
| 03 | [스프링 부트 입문](docs/03-spring-boot.md) | 계층 구조 |
| 04 | [증권 도메인 입문](docs/04-securities-domain.md) | 주문 생명주기 |
| 05 | [실무 환경](docs/05-work-environment.md) | 규제/보안/문화 |
| 06 | [30일 로드맵](docs/06-roadmap.md) | 학습 계획 |
| 07 | [실습: 주문 처리기](docs/07-order-system-tutorial.md) | 코드로 배우기 |
| 08 | [아키텍처 개요](docs/08-architecture.md) | 주문/체결 시스템 |
| 09 | [용어집](docs/09-glossary.md) | 증권 용어 사전 |

## LEVEL 2 — 코틀린 언어 완전정복

| # | 문서 | 내용 |
|---|------|------|
| 18 | [타입 시스템과 함수](docs/18-kotlin-types-and-functions.md) | 타입, 고차함수, 람다, 확장함수 |
| 19 | [객체지향과 설계](docs/19-kotlin-oop.md) | class/interface/sealed/object/위임 |
| 20 | [컬렉션과 함수형](docs/20-kotlin-collections.md) | List/Set/Map, 시퀀스, 함수형 연산 |
| 21 | [제네릭과 관용구](docs/21-kotlin-generics-and-idioms.md) | 제네릭, 스코프함수, idioms |

## LEVEL 3 — 생태계: 빌드 · 테스트 · 상호운용

| # | 문서 | 내용 |
|---|------|------|
| 22 | [Gradle 빌드 시스템](docs/22-gradle-build.md) | 의존성, 멀티모듈, 태스크 |
| 23 | [테스트 완전정복](docs/23-testing.md) | JUnit5, MockK, Kotest, 전략 |
| 24 | [자바 상호운용](docs/24-java-interop.md) | 레거시 자바와 코틀린 함께 쓰기 |

## LEVEL 4 — 스프링 백엔드 심화

| # | 문서 | 내용 |
|---|------|------|
| 03 | [스프링 부트 입문](docs/03-spring-boot.md) | (복습) |
| 25 | [스프링 코어](docs/25-spring-core.md) | DI/IoC/Bean 생명주기/AOP |
| 26 | [웹 계층: MVC/WebFlux](docs/26-spring-boot-web.md) | 요청처리, 검증, 예외, 응답 |
| 27 | [데이터 접근](docs/27-data-access.md) | JPA 심화, N+1, MyBatis, 트랜잭션 |

## LEVEL 5 — 동시성 · 비동기

| # | 문서 | 내용 |
|---|------|------|
| 11 | [동시성과 잔고 정합성](docs/11-concurrency.md) | (입문 심화) |
| 28 | [JVM 동시성 깊게](docs/28-jvm-concurrency.md) | 스레드, JMM, 락, 동시성 컬렉션 |
| 13 | [코루틴 입문](docs/13-coroutines.md) | (복습) |
| 29 | [코루틴 완전정복](docs/29-coroutines-deep.md) | 구조화된 동시성, Flow, 디스패처 |

## LEVEL 6 — 증권 도메인 완전정복

| # | 문서 | 내용 |
|---|------|------|
| 04 | [증권 도메인 입문](docs/04-securities-domain.md) | (복습) |
| 30 | [시장 구조와 매매제도](docs/30-market-structure.md) | KRX, 호가, VI, 서킷브레이커 |
| 31 | [주문 유형 전체](docs/31-order-types.md) | 지정가/시장가/IOC/FOK/조건부, 정정취소 |
| 32 | [청산·결제·정산](docs/32-clearing-settlement.md) | T+2, 청산, 대사 |
| 33 | [계좌·원장·손익](docs/33-account-ledger.md) | 평단가/평가손익/실현손익/세금 |
| 34 | [신용·파생 기초](docs/34-credit-derivatives.md) | 미수/신용/대주/선물옵션 기초 |
| 35 | [시세·시장데이터](docs/35-market-data.md) | 실시간 시세, 차트, 기술지표 |
| 36 | [리스크·컴플라이언스](docs/36-risk-compliance.md) | 한도, 이상거래, 규제 대응 |

## LEVEL 7 — 시스템 설계 (초심화)

| # | 문서 | 내용 |
|---|------|------|
| 08 | [아키텍처 개요](docs/08-architecture.md) | (복습) |
| 12 | [호가창 자료구조](docs/12-orderbook.md) | (복습) |
| 37 | [저지연 시스템 설계](docs/37-low-latency.md) | GC튜닝, 메모리, Disruptor |
| 38 | [이벤트 소싱과 CQRS](docs/38-event-sourcing-cqrs.md) | 이벤트 저장, 읽기/쓰기 분리 |
| 39 | [분산 시스템과 일관성](docs/39-distributed-systems.md) | CAP, 사가, 분산 트랜잭션 |
| 40 | [캐시 전략과 Redis](docs/40-caching-redis.md) | 캐시 패턴, 시세 캐싱 |
| 15 | [이벤트 기반/Kafka](docs/15-event-driven.md) | (복습) |
| 41 | [고가용성과 장애복구](docs/41-high-availability.md) | 이중화, 페일오버, DR |

## LEVEL 8 — 운영 · 보안 (초심화)

| # | 문서 | 내용 |
|---|------|------|
| 05 | [실무 환경](docs/05-work-environment.md) | (복습) |
| 42 | [관측성](docs/42-observability.md) | 로깅/메트릭/분산추적 |
| 43 | [성능 튜닝](docs/43-performance-tuning.md) | 프로파일링, JVM/DB 튜닝 |
| 44 | [보안](docs/44-security.md) | 인증/인가/암호화/규제/망분리 |
| 45 | [CI/CD와 배포](docs/45-cicd-deploy.md) | 파이프라인, 무중단 배포, 롤백 |


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

## LEVEL 10 — 캡스톤: 통합 실전

| # | 문서 | 내용 |
|---|------|------|
| 46 | [미니 증권 시스템 설계](docs/46-capstone-project.md) | 전체를 합친 실전 프로젝트 |
| 47 | [마스터 체크리스트](docs/47-mastery-checklist.md) | 자가 점검 + 다음 단계 |

## 용어집 (전 분야 망라 · 어느 팀이든 대비)

| # | 문서 | 내용 |
|---|------|------|
| 09 | [용어집 완전판](docs/09-glossary.md) | 20개 카테고리, 약 900개 표준 용어 |
| 50 | [백오피스·정산·대사](docs/50-glossary-backoffice.md) | 정산/회계/배치/보고 |
| 51 | [파생상품 심화](docs/51-glossary-derivatives.md) | 선물/옵션/그릭스/증거금(SPAN) |
| 52 | [채권 심화](docs/52-glossary-bonds.md) | 국채/회사채/평가/금리 |
| 53 | [펀드·신탁·랩·구조화상품](docs/53-glossary-funds.md) | 펀드/ETF/ELS/신탁 |
| 54 | [해외주식·외환](docs/54-glossary-global.md) | 해외거래소/환전/세금 |
| 55 | [시세·체결시스템](docs/55-glossary-marketdata.md) | 마켓데이터/매칭/차트 |
| 56 | [리스크·컴플라이언스·규제](docs/56-glossary-risk-compliance.md) | 한도/FDS/AML/규제 |
| 57 | [가나다순 색인](docs/57-glossary-index.md) | 892개 표제어 빠른 찾기 |

---

## 실행 가능한 예제 (검증 완료)

| 예제 | 다루는 개념 |
|------|------------|
| [OrderProcessor.kt](examples/OrderProcessor.kt) | 주문 검증 → 체결 → 평단가 |
| [BigDecimalGuide.kt](examples/BigDecimalGuide.kt) | 금액 계산 함정 |
| [RaceCondition.kt](examples/RaceCondition.kt) | 동시성 잔고 꼬임 |
| [OrderBook.kt](examples/OrderBook.kt) | 호가창 매칭 |

## 추천 진행 방식

1. **개발이 처음**이면 LEVEL 0 → 1 순서대로.
2. **코틀린만 처음**이면 LEVEL 1(02) → 2 집중.
3. **빨리 실무 투입**이면 01~09 + LEVEL 9 CS 종합 + 본인 팀 레벨(주문계=5·7, 시세계=6·7, 원장계=6·8).
4. 각 레벨 끝에서 예제를 직접 돌려보고, 값을 바꿔 실험하세요.
5. 마지막 [47. 마스터 체크리스트](docs/47-mastery-checklist.md)로 자가 점검.

---

_상세 입문은 [README](README.md), 전체 학습은 이 문서를 기준으로._
