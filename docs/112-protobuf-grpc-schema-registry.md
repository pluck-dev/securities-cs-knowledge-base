# 112. Protobuf, gRPC, Schema Registry

> 서비스 간 계약을 강하게 관리하려면 스키마 언어, 코드 생성, 호환성 검사가 필요합니다.

## 1. Protobuf

Protocol Buffers는 구조화 데이터를 효율적으로 직렬화하는 스키마 언어입니다.

- field number가 wire format의 핵심.
- field 이름보다 번호가 중요.
- optional/repeated/map.
- enum.
- backward/forward compatibility.

한 번 사용한 field number는 재사용하지 않는 것이 안전합니다.

## 2. gRPC

gRPC는 HTTP/2 기반 RPC 프레임워크입니다.

- unary.
- server streaming.
- client streaming.
- bidirectional streaming.
- deadline.
- status code.
- metadata.

내부 서비스 간 통신에 강하지만 브라우저 직접 사용과 디버깅 도구는 REST보다 제약이 있을 수 있습니다.

## 3. 스키마 호환성

안전한 변경:

- 새 optional field 추가.
- deprecated field 유지.
- enum 값 추가는 소비자 대응 확인 필요.

위험한 변경:

- field number 변경.
- 타입 변경.
- required field 추가.
- field number 재사용.
- 의미 변경.

## 4. Schema Registry

이벤트/메시지 스키마를 중앙에서 관리합니다.

- schema version.
- compatibility rule.
- producer validation.
- consumer validation.
- ownership.
- documentation.

Kafka Avro/Protobuf/JSON Schema 환경에서 자주 사용합니다.

## 5. 코드 생성

스키마에서 client/server 코드를 생성하면 계약 불일치를 줄일 수 있습니다.

주의:

- generated code commit 여부.
- plugin version.
- breaking change CI check.
- language별 null/default 차이.
- package namespace 관리.

## 6. 체크리스트

- [ ] field number 재사용 금지 규칙이 있다.
- [ ] breaking change 검사가 CI에 있다.
- [ ] deadline/timeout을 모든 gRPC 호출에 설정한다.
- [ ] schema owner와 version 정책이 있다.
- [ ] enum unknown value 처리를 고려한다.
- [ ] generated code 버전이 재현 가능하다.
