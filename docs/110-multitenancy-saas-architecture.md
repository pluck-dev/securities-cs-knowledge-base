# 110. 멀티테넌시와 SaaS 아키텍처

> SaaS는 여러 고객을 같은 플랫폼에서 안전하게 분리하고 운영하는 구조가 핵심입니다.

## 1. Tenant 격리 모델

| 모델 | 장점 | 단점 |
|---|---|---|
| Shared DB/Shared Schema | 비용 효율 | 격리 위험, 쿼리 실수 위험 |
| Shared DB/Separate Schema | 중간 격리 | migration 복잡도 |
| Separate DB | 강한 격리 | 운영 비용 증가 |
| Separate Cluster | 최고 격리 | 비용과 운영 부담 큼 |

보안, 비용, 고객 규모, 규제에 따라 선택합니다.

## 2. Tenant Context

모든 요청에는 tenant context가 있어야 합니다.

- subdomain.
- path.
- header.
- token claim.
- session.

서버에서 검증된 tenant만 신뢰하고, 클라이언트가 보낸 tenant id를 그대로 믿으면 안 됩니다.

## 3. 데이터 접근

- 모든 쿼리에 tenant filter 적용.
- row-level security 고려.
- unique constraint는 tenant 범위를 포함.
- background job도 tenant context 필요.
- cache key에 tenant id 포함.
- 로그와 메트릭도 tenant 단위 분석 가능.

## 4. Noisy Neighbor

한 tenant의 부하가 다른 tenant에 영향을 줄 수 있습니다.

대응:

- rate limit.
- quota.
- per-tenant queue.
- resource isolation.
- priority.
- large tenant 분리.

SaaS에서는 성능 격리도 보안 격리만큼 중요합니다.

## 5. Tenant 운영

- tenant provisioning.
- plan/feature entitlement.
- billing.
- data export.
- tenant deletion.
- audit log.
- custom domain.
- region/data residency.

## 6. 체크리스트

- [ ] tenant context가 서버에서 검증된다.
- [ ] 쿼리/cache/job/log에 tenant id가 반영된다.
- [ ] tenant별 rate limit과 quota가 있다.
- [ ] tenant 삭제와 데이터 export 절차가 있다.
- [ ] large tenant 격리 전략이 있다.
- [ ] 권한 테스트에 cross-tenant 접근 시나리오가 포함된다.
