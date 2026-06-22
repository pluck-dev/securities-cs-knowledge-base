# 71. 테스트와 품질 엔지니어링

> 테스트는 버그를 찾는 도구이면서 변경을 안전하게 만드는 설계 도구입니다.

## 1. 테스트 피라미드

```
많음  Unit
      Component/Integration
적음  E2E
```

- Unit test는 빠르고 정확히 실패 위치를 알려준다.
- Integration test는 실제 DB/큐/외부 경계를 검증한다.
- E2E test는 사용자 관점의 핵심 흐름을 보호한다.

## 2. 좋은 테스트의 조건

- 빠르다.
- 결정적이다.
- 실패 이유가 명확하다.
- 구현 세부보다 관찰 가능한 동작을 검증한다.
- 테스트 데이터가 읽기 쉽다.
- 독립 실행 가능하다.

## 3. 테스트 더블

| 종류 | 의미 |
|---|---|
| Dummy | 채우기용 객체 |
| Stub | 정해진 응답 반환 |
| Fake | 단순 구현체, 예: in-memory repository |
| Mock | 호출 여부와 인자를 검증 |
| Spy | 실제 호출을 기록 |

Mock을 남용하면 구현 변경에 취약해집니다. 도메인 규칙은 가능하면 순수 객체로 테스트합니다.

## 4. 백엔드 테스트

- 도메인 단위 테스트.
- API controller 테스트.
- repository 통합 테스트.
- transaction/locking 테스트.
- messaging consumer 테스트.
- contract test.

중요한 것은 정상 흐름보다 실패 흐름입니다.

## 5. 프론트엔드 테스트

- 사용자가 보는 텍스트와 role을 기준으로 선택한다.
- 로딩/빈 상태/오류 상태를 테스트한다.
- 접근성 위반을 자동 검사한다.
- 핵심 플로우는 E2E로 보호한다.
- visual regression은 디자인 시스템과 랜딩 페이지에 유용하다.

## 6. 성능 테스트

| 종류 | 목적 |
|---|---|
| Load test | 예상 부하에서 정상인지 |
| Stress test | 한계를 찾기 |
| Soak test | 오래 실행해 누수 확인 |
| Spike test | 급격한 트래픽 증가 대응 |

성능 테스트는 실제 데이터 분포, think time, 캐시 상태, 외부 의존성 제한을 반영해야 합니다.

## 7. 보안 테스트

- dependency vulnerability scan.
- SAST.
- DAST.
- secret scan.
- authorization test.
- fuzzing.

특히 권한 테스트는 “내 데이터만 볼 수 있는가”를 반복적으로 검증해야 합니다.

## 8. 테스트 데이터 관리

- fixture는 작고 의미 있게 만든다.
- 랜덤 데이터는 seed를 고정한다.
- 시간은 clock abstraction으로 제어한다.
- 외부 API는 contract mock 또는 sandbox를 쓴다.
- 테스트 DB는 격리하고 정리한다.

## 9. CI 품질 게이트

- format/lint.
- typecheck.
- unit/integration test.
- build.
- security scan.
- coverage threshold는 맹신하지 말고 핵심 영역을 보호한다.

## 10. 체크리스트

- [ ] 핵심 도메인 불변 조건이 unit test로 보호된다.
- [ ] DB/큐/외부 연동 경계가 integration test로 검증된다.
- [ ] 핵심 사용자 흐름 E2E가 있다.
- [ ] 실패/권한/동시성 케이스가 포함되어 있다.
- [ ] CI에서 자동으로 실행된다.
- [ ] flaky test를 방치하지 않는다.
