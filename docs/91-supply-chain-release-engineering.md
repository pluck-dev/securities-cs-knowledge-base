# 91. 공급망 보안과 릴리즈 엔지니어링

> 현대 소프트웨어는 수많은 오픈소스와 CI/CD 위에서 만들어지므로 산출물의 출처와 무결성을 관리해야 합니다.

## 1. 공급망 공격면

공격자는 코드만 노리지 않습니다.

- dependency takeover.
- typosquatting.
- compromised maintainer account.
- malicious postinstall script.
- CI secret exfiltration.
- artifact tampering.
- container base image 취약점.
- deployment credential 탈취.

## 2. SBOM

Software Bill of Materials는 소프트웨어 구성 요소 목록입니다.

포함할 것:

- package name.
- version.
- license.
- source.
- hash.
- transitive dependency.

SBOM은 취약점 영향 범위를 빠르게 파악하는 데 도움이 됩니다.

## 3. SLSA와 provenance

SLSA는 소프트웨어 공급망 무결성을 위한 프레임워크입니다.

핵심 질문:

- 누가 빌드했는가?
- 어떤 소스에서 빌드했는가?
- 어떤 빌드 환경이었는가?
- 산출물이 중간에 바뀌지 않았는가?

Provenance는 산출물의 출처 증명입니다.

## 4. CI/CD 보안

- CI token 최소 권한.
- pull request from fork의 secret 접근 제한.
- protected branch.
- required review.
- signed commit/tag.
- dependency cache 오염 방지.
- 배포 권한 분리.

CI는 강력한 권한을 가지므로 공격 표면이 큽니다.

## 5. Artifact 관리

릴리즈 산출물은 추적 가능해야 합니다.

- immutable artifact.
- version tag.
- checksum/signature.
- container digest pinning.
- promotion between environments.
- rollback artifact 보존.

운영 배포는 같은 artifact를 환경별 설정만 바꿔 승격하는 방식이 안전합니다.

## 6. 취약점 대응

- advisory 수신.
- 영향 범위 파악.
- exploitability 판단.
- patch 또는 mitigation.
- regression test.
- 릴리즈와 공지.

CVSS 점수만 보지 말고 실제 노출 경로를 함께 판단합니다.

## 7. 릴리즈 운영

좋은 릴리즈 프로세스:

- changelog.
- migration note.
- compatibility check.
- canary.
- monitoring window.
- rollback plan.
- post-release verification.

릴리즈는 이벤트가 아니라 반복 가능한 시스템이어야 합니다.

## 8. 체크리스트

- [ ] lockfile과 SBOM을 관리한다.
- [ ] CI secret 접근 범위를 제한한다.
- [ ] 산출물 digest/signature를 확인한다.
- [ ] 컨테이너 base image를 스캔한다.
- [ ] 배포 artifact가 환경별로 재빌드되지 않는다.
- [ ] 취약점 대응 런북과 소유자가 있다.
