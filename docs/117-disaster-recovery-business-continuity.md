# 117. 재해복구와 비즈니스 연속성

> DR은 백업을 갖고 있다는 말이 아니라, 실제 재해에서 목표 시간 안에 서비스를 복구할 수 있다는 증명입니다.

## 1. RTO와 RPO

- RTO: 복구 시간 목표. 얼마나 빨리 복구해야 하는가.
- RPO: 복구 시점 목표. 얼마나 많은 데이터 손실을 허용하는가.

시스템별 RTO/RPO가 다르면 복구 전략도 달라야 합니다.

## 2. DR 전략

| 전략 | 설명 |
|---|---|
| Backup & Restore | 가장 저렴하지만 느림 |
| Pilot Light | 핵심 최소 인프라 유지 |
| Warm Standby | 축소된 복제 환경 유지 |
| Active-Active | 여러 지역 동시 운영 |

비용과 복잡도는 Active-Active로 갈수록 커집니다.

## 3. 데이터 복구

- snapshot.
- WAL/PITR.
- cross-region replication.
- backup encryption.
- restore drill.
- consistency validation.
- backup retention.

백업은 복원 테스트 전까지 신뢰할 수 없습니다.

## 4. Failover

- DNS failover.
- global load balancer.
- database promotion.
- traffic drain.
- split brain 방지.
- rollback/failback.

failover보다 failback이 더 어려운 경우가 많습니다.

## 5. BCP

Business Continuity Plan은 기술 외 요소를 포함합니다.

- 담당자와 연락망.
- 의사결정 권한.
- 고객 공지.
- 수동 업무 절차.
- 규제 보고.
- 우선 복구 서비스 목록.

## 6. 체크리스트

- [ ] 서비스별 RTO/RPO가 정의되어 있다.
- [ ] 백업 복원 훈련을 정기적으로 한다.
- [ ] failover/failback 절차가 문서화되어 있다.
- [ ] DR 환경의 secret/config가 최신이다.
- [ ] 고객/규제 커뮤니케이션 절차가 있다.
- [ ] 복구 후 데이터 정합성 검증을 수행한다.
