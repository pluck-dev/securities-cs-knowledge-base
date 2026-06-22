# 90. DDD와 모듈러 아키텍처

> 큰 시스템을 오래 유지하려면 도메인 경계, 모델 언어, 모듈 의존성을 설계해야 합니다.

## 1. DDD가 필요한 이유

코드가 커질수록 기술 문제가 아니라 모델과 경계 문제가 커집니다.

- 같은 단어를 팀마다 다르게 쓴다.
- 한 변경이 여러 모듈을 깨뜨린다.
- DB 테이블이 도메인 모델을 지배한다.
- 서비스 간 소유권이 불명확하다.

DDD는 도메인 언어와 경계를 중심으로 복잡도를 다룹니다.

## 2. Ubiquitous Language

도메인 전문가와 개발자가 같은 언어를 써야 합니다.

예:

- 주문, 체결, 정정, 취소.
- 예수금, 증거금, 미수금.
- 정산, 대사, 원장.

코드의 클래스/함수/이벤트 이름이 업무 언어와 맞아야 유지보수가 쉬워집니다.

## 3. Bounded Context

같은 단어도 문맥마다 의미가 다를 수 있습니다.

- 주문 context의 Account.
- 인증 context의 Account.
- 회계 context의 Account.

Bounded Context는 모델이 일관되게 의미를 갖는 경계입니다.

## 4. Aggregate

Aggregate는 일관성을 지켜야 하는 객체 묶음입니다.

원칙:

- aggregate root를 통해 변경.
- 내부 불변 조건 보호.
- transaction boundary 후보.
- 너무 크게 만들면 경합 증가.
- 너무 작게 만들면 규칙 분산.

## 5. Domain Event

도메인 이벤트는 업무적으로 의미 있는 발생 사실입니다.

예:

- OrderPlaced.
- OrderExecuted.
- DepositReserved.
- SettlementCompleted.

이벤트는 시스템 통합과 감사 추적에 유용하지만 스키마 버전 관리가 필요합니다.

## 6. Hexagonal Architecture

도메인을 외부 기술에서 분리합니다.

- Domain core.
- Application use case.
- Port.
- Adapter.
- Infrastructure.

DB, HTTP, 메시지 브로커는 도메인의 중심이 아니라 외부 adapter입니다.

## 7. 모듈 의존성

좋은 모듈 구조:

- 의존 방향이 단순하다.
- 순환 의존이 없다.
- 공개 API가 작다.
- 내부 구현이 숨겨져 있다.
- 변경 이유가 비슷한 코드가 함께 있다.

모듈은 폴더가 아니라 경계와 규칙입니다.

## 8. 체크리스트

- [ ] 핵심 도메인 용어가 코드 이름에 반영되어 있다.
- [ ] bounded context가 문서화되어 있다.
- [ ] aggregate 경계와 transaction 경계가 맞다.
- [ ] domain event 이름과 스키마가 관리된다.
- [ ] 외부 기술이 도메인 모델을 침범하지 않는다.
- [ ] 모듈 간 순환 의존이 없다.
