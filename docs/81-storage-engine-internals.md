# 81. 저장 엔진 내부 구조

> 데이터베이스 성능과 장애를 이해하려면 B-Tree, LSM, WAL, MVCC 같은 내부 구조를 알아야 합니다.

## 1. 저장 엔진이 하는 일

저장 엔진은 데이터를 디스크와 메모리에 배치하고 읽기/쓰기/복구를 담당합니다.

- 페이지 관리.
- 인덱스 관리.
- 트랜잭션 로그.
- 버퍼 풀.
- 동시성 제어.
- crash recovery.
- compaction 또는 vacuum.

## 2. B-Tree

B-Tree 계열은 RDBMS 인덱스에서 흔합니다.

특징:

- 정렬된 키를 유지한다.
- 범위 조회에 강하다.
- 디스크 페이지 단위 접근에 적합하다.
- 삽입/삭제 시 split/merge가 발생할 수 있다.

복합 인덱스는 선두 컬럼부터 효과가 크며, 범위 조건 뒤 컬럼은 활용이 제한될 수 있습니다.

## 3. LSM Tree

LSM Tree는 쓰기를 메모리에 모았다가 디스크에 순차적으로 내립니다.

구성:

- MemTable.
- SSTable.
- WAL.
- Compaction.
- Bloom Filter.

쓰기 처리량에 강하지만 compaction 비용과 read amplification이 생길 수 있습니다.

## 4. WAL

Write-Ahead Log는 데이터 페이지를 바꾸기 전에 변경 기록을 먼저 남기는 방식입니다.

효과:

- 장애 후 redo/undo 가능.
- commit durability 보장.
- 순차 쓰기로 성능 확보.

fsync 정책에 따라 성능과 내구성이 달라집니다.

## 5. MVCC

Multi-Version Concurrency Control은 여러 버전의 row를 유지해 읽기와 쓰기의 충돌을 줄입니다.

- reader는 특정 시점 snapshot을 본다.
- writer는 새 버전을 만든다.
- 오래된 버전은 vacuum/cleanup 대상이다.
- long transaction은 cleanup을 막아 bloat를 만들 수 있다.

## 6. Buffer Pool과 Page Cache

DB는 자체 buffer pool을 사용하고 OS도 page cache를 사용합니다.

- hot page는 메모리에 남는다.
- cache hit ratio가 성능에 중요하다.
- 메모리 설정이 잘못되면 swap 또는 cache miss가 늘어난다.
- DB와 OS cache의 역할을 구분해야 한다.

## 7. Vacuum, Compaction, Reindex

저장 엔진은 쓰기 후 정리 작업이 필요합니다.

- MVCC DB는 dead tuple cleanup이 필요하다.
- LSM DB는 compaction이 필요하다.
- 인덱스 bloat가 크면 reindex가 필요할 수 있다.
- 정리 작업이 지연되면 지연 시간 spike가 생길 수 있다.

## 8. 체크리스트

- [ ] 사용하는 DB의 인덱스 구조를 안다.
- [ ] WAL과 fsync 정책을 이해한다.
- [ ] MVCC와 long transaction 위험을 안다.
- [ ] compaction/vacuum 지표를 모니터링한다.
- [ ] slow query가 CPU 문제인지 I/O 문제인지 구분한다.
- [ ] 백업이 WAL/PITR과 어떻게 연결되는지 안다.
