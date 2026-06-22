# 107. Service Mesh와 API Gateway

> 서비스 간 통신이 많아지면 인증, 라우팅, 재시도, 관측성, 정책을 애플리케이션 밖에서 일관되게 다루고 싶어집니다.

## 1. API Gateway 역할

- 외부 요청 진입점.
- 인증/인가 위임.
- rate limiting.
- request/response transformation.
- routing.
- WAF 연계.
- API versioning.
- analytics.

Gateway는 비즈니스 로직을 넣는 곳이 아니라 cross-cutting concern을 처리하는 경계입니다.

## 2. Service Mesh 역할

Service Mesh는 서비스 간 east-west traffic을 다룹니다.

- mTLS.
- traffic routing.
- retry/timeout.
- circuit breaking.
- telemetry.
- policy.
- canary/shadow traffic.

대표 구현은 sidecar 또는 ambient 방식으로 data plane을 구성합니다.

## 3. Gateway와 Mesh 차이

| 구분 | API Gateway | Service Mesh |
|---|---|---|
| 트래픽 | 외부→내부 | 내부 서비스 간 |
| 관심사 | API 노출, 인증, 제한 | mTLS, 라우팅, 관측성 |
| 사용자 | 외부 client | 내부 service |
| 위험 | 단일 진입 병목 | 운영 복잡도 증가 |

## 4. 정책과 보안

- JWT 검증.
- mTLS identity.
- authorization policy.
- egress policy.
- service-to-service ACL.
- rate limit.
- audit log.

정책이 너무 분산되면 누가 접근 가능한지 이해하기 어려워집니다.

## 5. 장애 모드

- sidecar resource 부족.
- control plane 장애.
- 잘못된 retry로 retry storm.
- timeout 중복 설정.
- mTLS 인증서 만료.
- config push 지연.
- gateway route 충돌.

## 6. 체크리스트

- [ ] timeout/retry 정책이 앱과 mesh에서 중복 충돌하지 않는다.
- [ ] mTLS 인증서 회전이 자동화되어 있다.
- [ ] gateway route와 ownership이 문서화되어 있다.
- [ ] rate limit과 circuit breaker가 사용자 영향 기준으로 설정되어 있다.
- [ ] mesh 도입 비용과 디버깅 복잡도를 감당할 수 있다.
- [ ] fallback/rollback 경로가 있다.
