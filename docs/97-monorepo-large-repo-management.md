# 97. 모노레포와 대규모 저장소 관리

> 코드베이스가 커지면 빌드, 테스트, 소유권, 의존성 경계를 관리하는 능력이 필요합니다.

## 1. Monorepo vs Polyrepo

| 방식 | 장점 | 단점 |
|---|---|---|
| Monorepo | 원자적 변경, 공유 도구, 전체 검색 | 빌드/권한/규모 관리 필요 |
| Polyrepo | 독립성, 권한 분리 | 변경 조율과 버전 관리 어려움 |

정답은 조직 구조와 배포 경계에 따라 다릅니다.

## 2. 대규모 빌드

- affected project detection.
- incremental build.
- remote cache.
- distributed execution.
- hermetic build.
- dependency graph.
- build profiling.

전체 테스트를 매번 돌리면 속도가 떨어지므로 변경 영향 범위를 계산해야 합니다.

## 3. 코드 소유권

- CODEOWNERS.
- module owner.
- review rule.
- public API boundary.
- deprecation policy.
- dependency approval.

소유권이 없으면 공용 모듈이 쓰레기장이 됩니다.

## 4. 버전과 릴리즈

모노레포에서도 모든 것을 동시에 배포할 필요는 없습니다.

- independent versioning.
- lockstep versioning.
- release train.
- changeset.
- generated changelog.
- compatibility test.

## 5. 의존성 경계

- 순환 의존 금지.
- internal/public API 구분.
- layer rule.
- domain boundary.
- dependency lint.
- shared util 남용 방지.

## 6. 체크리스트

- [ ] 변경 영향 범위를 계산한다.
- [ ] remote cache와 incremental build가 있다.
- [ ] CODEOWNERS와 모듈 책임자가 있다.
- [ ] dependency graph를 시각화할 수 있다.
- [ ] 공용 API 변경 정책이 있다.
- [ ] 대규모 rename/migration 절차가 있다.
