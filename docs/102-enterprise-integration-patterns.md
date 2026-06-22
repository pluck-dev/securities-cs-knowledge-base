# 102. 엔터프라이즈 연동 패턴

> 현업에서는 최신 API뿐 아니라 파일, 배치, SFTP, ESB, 레거시 프로토콜과도 연동해야 합니다.

## 1. 연동 방식

- REST API.
- gRPC.
- message queue.
- event stream.
- batch file.
- SFTP.
- database link.
- webhook.
- ESB.

연동 방식은 조직, 보안, 레거시, 감사 요구사항에 의해 결정됩니다.

## 2. 파일 연동

파일 연동에서 중요한 것:

- 파일명 규칙.
- encoding.
- delimiter.
- header/trailer.
- checksum.
- 완료 marker.
- 재처리.
- 중복 방지.
- 부분 파일 방지.

## 3. 배치 연동

- schedule.
- dependency.
- retry.
- idempotency.
- checkpoint.
- reconciliation.
- SLA.
- holiday calendar.

배치는 실패 후 재실행 가능성이 핵심입니다.

## 4. Canonical Model

여러 시스템을 연결할 때 공통 모델을 만들 수 있습니다.

장점:

- 변환 규칙 중앙화.
- 시스템 간 결합 감소.

단점:

- 공통 모델이 너무 커질 수 있음.
- 도메인별 의미 차이를 숨길 수 있음.

## 5. Anti-Corruption Layer

외부 시스템 모델이 내부 도메인을 오염시키지 않도록 변환 계층을 둡니다.

- external DTO.
- mapper.
- validation.
- error translation.
- retry policy.
- audit logging.

## 6. 체크리스트

- [ ] 연동 계약과 파일/API 스키마가 문서화되어 있다.
- [ ] 중복/부분/지연 데이터 처리가 있다.
- [ ] 재처리와 대사 방법이 있다.
- [ ] 외부 모델이 내부 도메인을 침범하지 않는다.
- [ ] 연동 실패 알림과 SLA가 있다.
- [ ] 보안 채널과 접근 권한이 검토되었다.
