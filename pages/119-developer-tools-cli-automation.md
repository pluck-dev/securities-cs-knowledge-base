# 119. 개발자 도구, CLI, 자동화

> 반복 가능한 작업은 도구화해야 합니다. 좋은 CLI와 자동화는 팀의 실수를 줄이고 속도를 높입니다.

## 1. 자동화 후보

- 프로젝트 생성.
- 로컬 환경 점검.
- 코드 생성.
- 마이그레이션.
- 릴리즈 노트.
- 배포 전 검증.
- 로그/트레이스 조회.
- 장애 진단.
- 데이터 보정 스크립트.

## 2. 좋은 CLI

- 명령 이름이 일관됨.
- dry-run 지원.
- --json 출력 지원.
- exit code가 의미 있음.
- help가 충분함.
- destructive command는 확인 또는 보호 장치.
- 로그와 결과를 구분.

자동화 도구는 사람이 읽기 좋은 출력과 기계가 읽기 좋은 출력을 모두 제공하면 좋습니다.

## 3. 스크립트 안전성

- set -euo pipefail.
- 임시 파일 정리.
- lock file.
- idempotent 실행.
- retry와 timeout.
- 입력 검증.
- 권한 확인.
- audit log.

## 4. 코드 생성

코드 생성은 반복을 줄이지만 생성물과 소스의 경계를 명확히 해야 합니다.

- generator version.
- template test.
- generated marker.
- regeneration command.
- manual edit 금지 여부.
- diff review.

## 5. 내부 도구 운영

내부 도구도 제품입니다.

- owner.
- changelog.
- versioning.
- telemetry.
- error reporting.
- onboarding guide.
- deprecation policy.

## 6. 체크리스트

- [ ] 반복 작업이 CLI/스크립트로 자동화되어 있다.
- [ ] destructive command는 dry-run과 확인 절차가 있다.
- [ ] 자동화 결과는 exit code와 JSON으로 검증 가능하다.
- [ ] 스크립트는 재실행 가능하다.
- [ ] 코드 생성 절차와 버전이 문서화되어 있다.
- [ ] 내부 도구에 소유자와 릴리즈 노트가 있다.
