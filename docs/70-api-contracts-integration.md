# 70. API 계약과 시스템 연동

> 실무 시스템은 혼자 동작하지 않습니다. API 계약, 버전 관리, 외부 연동 안정성이 품질을 좌우합니다.

## 1. API는 계약이다

API는 함수가 아니라 팀과 시스템 사이의 약속입니다.

계약에 포함할 것:

- endpoint와 method.
- request/response schema.
- 인증 방식.
- 오류 코드와 상태 코드.
- rate limit.
- idempotency 규칙.
- versioning과 deprecation 정책.

## 2. 스키마 설계

좋은 스키마는 모호함을 줄입니다.

- 필수/선택 필드를 명확히 한다.
- nullable과 missing을 구분한다.
- enum은 미래 확장 가능성을 고려한다.
- 금액은 통화와 scale을 함께 표현한다.
- 시간은 timezone과 format을 명시한다.

## 3. 버전 관리

| 방식 | 예시 | 특징 |
|---|---|---|
| URL version | `/v1/orders` | 명확하지만 URL 증가 |
| Header version | `Accept: ...v=1` | 깔끔하지만 도구 지원 확인 필요 |
| Field evolution | 필드 추가 중심 | 하위 호환에 유리 |

Breaking change 예:

- 필드 삭제.
- 타입 변경.
- enum 값 의미 변경.
- 오류 코드 의미 변경.
- 동기 처리에서 비동기 처리로 의미 변경.

## 4. 하위 호환 원칙

- 응답 필드 추가는 보통 안전하다.
- 요청 필수 필드 추가는 breaking change다.
- 클라이언트는 모르는 응답 필드를 무시해야 한다.
- 서버는 가능한 한 오래된 클라이언트를 일정 기간 지원해야 한다.

## 5. 오류 설계

오류는 개발자 경험의 핵심입니다.

```json
{
  "code": "ACCOUNT_NOT_FOUND",
  "message": "계좌를 찾을 수 없습니다.",
  "traceId": "...",
  "retryable": false
}
```

- 사람이 읽을 message.
- 기계가 판단할 code.
- 추적 가능한 traceId.
- 재시도 가능 여부.
- 필드별 검증 오류.

## 6. 멱등성

재시도 가능한 시스템에는 멱등성이 필요합니다.

- `GET`, `PUT`, `DELETE`는 멱등하게 설계한다.
- `POST` 생성 요청은 `Idempotency-Key`를 고려한다.
- 서버는 key, request hash, response를 저장해 중복 처리한다.
- 만료 정책과 충돌 정책을 문서화한다.

## 7. 외부 API 연동

외부 API는 언젠가 느려지고 실패합니다.

필수 요소:

- timeout.
- retry with backoff.
- circuit breaker.
- bulkhead.
- fallback.
- sandbox와 contract test.
- 장애 공지/상태 페이지 확인 루틴.

## 8. Webhook

Webhook은 상대 시스템이 우리에게 이벤트를 보내는 방식입니다.

주의점:

- 서명 검증.
- 재전송과 중복 처리.
- 순서 보장 없음 가정.
- 빠른 2xx 응답 후 내부 큐 처리.
- dead letter와 재처리 도구.

## 9. 계약 테스트

- Provider test: 제공자가 계약대로 응답하는지.
- Consumer test: 소비자가 계약에 맞게 요청/해석하는지.
- Schema lint: OpenAPI/protobuf 규칙 검사.
- Mock server: 프론트엔드와 백엔드 병렬 개발.

## 10. API 체크리스트

- [ ] 스키마에 nullable/missing/enum/time/money 규칙이 있다.
- [ ] 오류 코드가 문서화되어 있다.
- [ ] breaking change 정책이 있다.
- [ ] 재시도 가능한 요청은 멱등성이 있다.
- [ ] 외부 연동에 timeout/retry/circuit breaker가 있다.
- [ ] webhook은 서명 검증과 중복 처리가 있다.
