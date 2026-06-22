# 103. 문서화와 지식 관리

> 문서는 코드가 말하지 못하는 의도, 운영 지식, 의사결정 맥락을 보존합니다.

## 1. 문서의 종류

- README.
- API reference.
- Architecture Decision Record.
- Runbook.
- Onboarding guide.
- Troubleshooting guide.
- Postmortem.
- Migration guide.
- Design proposal.

## 2. 좋은 문서의 조건

- 대상 독자가 명확하다.
- 최신이다.
- 찾을 수 있다.
- 예제가 있다.
- 소유자가 있다.
- 변경 시점이 코드와 연결된다.
- “왜”가 들어 있다.

## 3. ADR

Architecture Decision Record는 중요한 결정을 짧게 기록합니다.

포함할 것:

- context.
- decision.
- consequences.
- alternatives.
- status.
- date.

미래의 팀원이 같은 논쟁을 반복하지 않게 합니다.

## 4. Runbook

운영 런북은 장애 중 실행 가능한 절차여야 합니다.

- 증상.
- 영향.
- 확인 명령.
- 완화 조치.
- rollback.
- escalation.
- 관련 대시보드.
- 위험한 명령 주의.

## 5. 문서 부채

문서도 부채가 쌓입니다.

- 소유자 없는 문서.
- 오래된 스크린샷.
- 깨진 링크.
- 실제 절차와 다른 런북.
- 중복된 진실.

문서 정리도 정기 작업이어야 합니다.

## 6. 체크리스트

- [ ] 중요한 결정은 ADR로 남긴다.
- [ ] 런북은 실제 장애 훈련에서 검증된다.
- [ ] 문서 소유자와 마지막 검토일이 있다.
- [ ] 코드 변경과 문서 변경이 함께 리뷰된다.
- [ ] 온보딩 문서는 새 팀원이 직접 검증한다.
- [ ] 검색 가능한 지식 베이스가 있다.
