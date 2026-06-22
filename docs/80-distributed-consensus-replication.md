# 80. 분산 합의와 복제

> 여러 서버가 같은 결정을 내려야 할 때 합의, 복제, 리더 선출, 장애 감지가 필요합니다.

## 1. 왜 합의가 어려운가

분산 환경에서는 다음이 동시에 발생합니다.

- 메시지 지연.
- 메시지 유실.
- 노드 장애.
- 네트워크 파티션.
- 시계 불일치.
- 중복 요청.

한 서버에서는 단순한 상태 변경도 여러 서버에서는 “누가 최신인가?” 문제가 됩니다.

## 2. Replication 모델

| 모델 | 설명 | trade-off |
|---|---|---|
| Leader-Follower | 리더가 쓰기 처리, follower 복제 | 단순하지만 리더 장애 처리 필요 |
| Multi-Leader | 여러 리더가 쓰기 처리 | 지역 분산에 좋지만 충돌 해결 필요 |
| Leaderless | quorum 기반 읽기/쓰기 | 가용성 높지만 복잡 |

복제는 읽기 확장과 장애 대응을 돕지만 replication lag와 split brain 위험을 만듭니다.

## 3. Quorum

Quorum은 여러 복제본 중 일정 수 이상의 응답을 요구하는 방식입니다.

- `N`: 복제본 수.
- `W`: 쓰기 성공에 필요한 수.
- `R`: 읽기 성공에 필요한 수.
- `R + W > N`이면 최신 쓰기와 읽기가 겹칠 가능성이 생긴다.

하지만 네트워크 지연, read repair, hinted handoff 같은 실제 구현 세부가 중요합니다.

## 4. Raft 핵심

Raft는 이해하기 쉽게 설계된 합의 알고리즘입니다.

- leader election.
- log replication.
- term.
- commit index.
- majority quorum.

리더는 로그를 follower에게 복제하고 과반수가 저장하면 commit할 수 있습니다. 리더 장애 시 새 리더를 선출합니다.

## 5. Paxos와 Zab

Paxos는 고전적인 합의 알고리즘이고, ZooKeeper의 Zab은 ZooKeeper에 맞춘 원자 broadcast 프로토콜입니다.

실무자는 수학적 증명보다 다음을 이해하면 됩니다.

- 합의에는 과반수가 필요하다.
- 네트워크 파티션 중 소수파는 쓰기를 중단해야 안전하다.
- 리더 선출과 로그 순서가 일관성을 만든다.

## 6. Split Brain

Split brain은 두 노드가 모두 자신이 리더라고 믿는 상황입니다.

방지 방법:

- quorum 기반 리더 선출.
- fencing token.
- lease와 clock drift 주의.
- 외부 lock 서비스 사용 시 장애 모드 이해.
- 수동 failover 절차 최소화.

## 7. 실무 적용

- DB primary failover.
- Kafka controller/broker metadata.
- Kubernetes etcd.
- 분산 락.
- configuration store.
- leader-only batch scheduler.

분산 합의를 직접 구현하는 일은 드뭅니다. 대신 사용하는 시스템의 보장과 장애 모드를 이해해야 합니다.

## 8. 체크리스트

- [ ] 리더 장애 시 누가 새 리더가 되는지 안다.
- [ ] replication lag가 사용자에게 어떤 영향을 주는지 안다.
- [ ] quorum이 깨지면 쓰기/읽기가 어떻게 되는지 안다.
- [ ] split brain 방지 장치가 있다.
- [ ] failover를 리허설했다.
- [ ] 일관성보다 가용성을 택한 지점이 문서화되어 있다.
