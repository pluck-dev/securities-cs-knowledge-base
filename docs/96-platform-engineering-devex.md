# 96. 플랫폼 엔지니어링과 개발자 경험

> 플랫폼 엔지니어링은 개발자가 안전하고 빠르게 제품을 만들도록 paved road를 제공하는 일입니다.

## 1. 플랫폼의 목적

- 반복 작업 자동화.
- 표준화된 배포 경로.
- 관측성 기본 제공.
- 보안 통제 내장.
- self-service 환경 제공.
- 팀별 중복 도구 감소.

플랫폼은 통제가 아니라 생산성을 높이는 제품이어야 합니다.

## 2. Internal Developer Platform

구성 요소:

- service catalog.
- template/scaffold.
- CI/CD pipeline.
- environment provisioning.
- secret management.
- observability dashboard.
- documentation portal.
- ownership metadata.

## 3. Golden Path

Golden path는 권장 개발/배포 방식입니다.

좋은 golden path:

- 시작이 빠르다.
- 보안과 로깅이 기본 포함된다.
- 예외 경로가 가능하지만 비용이 명확하다.
- 문서와 템플릿이 최신이다.
- 팀 피드백으로 개선된다.

## 4. 개발자 생산성 측정

측정 후보:

- lead time.
- deployment frequency.
- change failure rate.
- MTTR.
- onboarding time.
- CI duration.
- flaky test rate.
- developer satisfaction.

숫자는 감시가 아니라 병목 개선에 사용해야 합니다.

## 5. Backstage와 Service Catalog

서비스 카탈로그에는 다음이 있으면 좋습니다.

- owner.
- lifecycle.
- repo.
- deployment.
- dependencies.
- runbook.
- SLO.
- dashboards.
- on-call.

## 6. 체크리스트

- [ ] 새 서비스 생성 템플릿이 있다.
- [ ] 서비스 소유자와 운영 정보가 catalog에 있다.
- [ ] 배포/로그/메트릭이 기본 제공된다.
- [ ] 플랫폼 API와 문서가 제품처럼 관리된다.
- [ ] 개발자 생산성 병목을 측정한다.
- [ ] 예외 경로와 책임이 명확하다.
