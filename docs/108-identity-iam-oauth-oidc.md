# 108. Identity, IAM, OAuth2/OIDC 심화

> 인증과 권한은 모든 제품의 보안 경계입니다. 프로토콜과 토큰, 세션, 권한 모델을 정확히 구분해야 합니다.

## 1. Identity 개념

- Principal: 행위 주체. 사용자, 서비스, 기기.
- Authentication: 신원 확인.
- Authorization: 권한 판단.
- Federation: 외부 identity provider와 신뢰 연결.
- Delegation: 사용자가 앱에 제한된 권한 위임.
- Impersonation: 관리자/시스템이 다른 주체처럼 행동. 강한 감사 필요.

## 2. OAuth2

OAuth2는 인증 프로토콜이 아니라 권한 위임 프레임워크입니다.

주요 grant:

- Authorization Code + PKCE.
- Client Credentials.
- Refresh Token.
- Device Code.

Implicit flow는 현대 보안 기준에서는 피하는 것이 일반적입니다.

## 3. OIDC

OIDC는 OAuth2 위에 identity layer를 얹습니다.

- ID Token: 사용자 인증 정보.
- Access Token: API 접근 권한.
- UserInfo endpoint.
- Discovery document.
- JWKS.

ID Token을 API authorization 용도로 쓰지 않도록 구분합니다.

## 4. Token 설계

- issuer.
- audience.
- subject.
- expiry.
- scope/claim.
- signature.
- key id.

JWT는 stateless 검증이 가능하지만 폐기와 권한 변경 반영이 어렵습니다. 짧은 만료와 refresh 전략이 필요합니다.

## 5. IAM 권한 모델

- RBAC: 역할 기반.
- ABAC: 속성 기반.
- ReBAC: 관계 기반.
- Policy-as-code.
- least privilege.
- separation of duties.

권한은 UI 숨김이 아니라 서버 정책으로 강제해야 합니다.

## 6. 체크리스트

- [ ] 인증과 인가 책임이 분리되어 있다.
- [ ] Authorization Code + PKCE를 사용한다.
- [ ] access token과 id token 용도를 구분한다.
- [ ] token audience/issuer/expiry를 검증한다.
- [ ] refresh token 회전과 폐기 정책이 있다.
- [ ] 관리자 impersonation은 감사 로그를 남긴다.
