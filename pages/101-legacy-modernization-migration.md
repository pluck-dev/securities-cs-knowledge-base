# 101. 레거시 현대화와 마이그레이션

> 레거시는 나쁜 코드가 아니라 비즈니스를 오래 지탱한 시스템입니다. 안전한 전환 전략이 필요합니다.

## 1. 레거시를 대하는 태도

- 먼저 이해하고 측정한다.
- 동작을 테스트로 고정한다.
- 한 번에 갈아엎지 않는다.
- 사용자 영향이 작은 경로부터 분리한다.
- 도메인 지식을 보존한다.
- 기존 장애와 운영 지식을 존중한다.

## 2. Strangler Fig 패턴

새 시스템이 기존 시스템 주변을 감싸며 기능을 점진적으로 대체합니다.

절차:

1. 경계 식별.
2. 프록시/라우팅 도입.
3. 일부 기능 신규 구현.
4. 트래픽 점진 전환.
5. 검증 후 구 기능 제거.

## 3. 데이터 마이그레이션

- dual write.
- change data capture.
- backfill.
- shadow read.
- consistency check.
- cutover.
- rollback.

데이터 전환은 코드 전환보다 어렵고 오래 걸립니다.

## 4. 호환성

- API 하위 호환.
- DB schema expand/contract.
- 이벤트 스키마 버전.
- 오래된 클라이언트 지원.
- batch job 의존성.
- 보고서/정산 영향.

## 5. 리스크 관리

- migration feature flag.
- canary cohort.
- reconciliation report.
- rollback plan.
- freeze window.
- business owner sign-off.
- 운영 런북.

## 6. 체크리스트

- [ ] 현재 동작을 테스트와 로그로 이해했다.
- [ ] 점진 전환 경로가 있다.
- [ ] 데이터 정합성 검증 리포트가 있다.
- [ ] rollback 가능 지점이 정의되어 있다.
- [ ] 오래된 클라이언트와 배치 의존성을 확인했다.
- [ ] 제거 완료 조건이 명확하다.
