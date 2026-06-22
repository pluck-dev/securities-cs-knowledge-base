# 109. 데이터베이스 샤딩과 파티셔닝

> 데이터가 커지면 인덱스만으로 부족해집니다. 파티셔닝과 샤딩은 성능과 운영 복잡도의 교환입니다.

## 1. Partitioning과 Sharding

- Partitioning: 같은 DB 안에서 테이블/인덱스를 물리적으로 나눔.
- Sharding: 여러 DB 노드에 데이터를 분산.
- Horizontal split: row 기준 분리.
- Vertical split: column/도메인 기준 분리.

샤딩은 마지막 수단에 가깝습니다. 먼저 인덱스, 쿼리, 캐시, read replica, archive를 검토합니다.

## 2. 파티션 키

좋은 파티션 키:

- 쿼리 조건에 자주 등장.
- 데이터가 고르게 분포.
- 시간에 따른 hotspot이 적음.
- 변경되지 않음.
- 업무 경계와 맞음.

나쁜 키는 특정 shard에 트래픽을 몰아 hotspot을 만듭니다.

## 3. Range, Hash, List

| 방식 | 장점 | 단점 |
|---|---|---|
| Range | 시간/범위 조회 좋음 | 최신 구간 hotspot |
| Hash | 분산 균등 | 범위 조회 어려움 |
| List | 명시적 그룹 | 관리 부담 |

로그/이력은 시간 파티션이 흔하고, 사용자 데이터는 hash 샤딩이 흔합니다.

## 4. Cross-Shard 문제

- cross-shard join.
- distributed transaction.
- global unique id.
- resharding.
- backup/restore.
- query routing.
- aggregate query.

샤딩 후에는 “간단한 SQL”이 애플리케이션 로직과 데이터 파이프라인 문제로 바뀔 수 있습니다.

## 5. Resharding

resharding은 매우 위험한 작업입니다.

- dual write.
- backfill.
- consistency check.
- traffic shifting.
- cutover.
- rollback.
- long-running migration monitoring.

처음부터 shard key와 성장 시나리오를 신중히 정해야 합니다.

## 6. 체크리스트

- [ ] 샤딩 전 다른 최적화를 검토했다.
- [ ] shard/partition key 선택 근거가 있다.
- [ ] cross-shard query와 transaction을 식별했다.
- [ ] global id 전략이 있다.
- [ ] resharding 절차와 검증 리포트가 있다.
- [ ] 백업/복구가 shard 단위로 검증되었다.
