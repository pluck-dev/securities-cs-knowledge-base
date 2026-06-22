# 100. 용량 계획과 FinOps

> 성능과 비용은 함께 봐야 합니다. 너무 적게 쓰면 장애, 너무 많이 쓰면 낭비입니다.

## 1. 용량 계획 입력

- 현재 트래픽.
- 피크 트래픽.
- 성장률.
- 시즌성.
- 사용자 행동 변화.
- 배치/이벤트 부하.
- 장애 시 우회 트래픽.

평균이 아니라 피크와 tail latency 기준으로 계획합니다.

## 2. 자원별 병목

| 자원 | 증상 |
|---|---|
| CPU | 처리량 한계, run queue 증가 |
| Memory | GC 증가, OOM, swap |
| Disk | I/O wait, fsync 지연 |
| Network | packet loss, retransmit |
| DB Connection | pool wait, timeout |
| Queue | lag 증가 |

병목은 한 곳을 해결하면 다음 곳으로 이동합니다.

## 3. Headroom

Headroom은 예기치 않은 증가를 흡수할 여유입니다.

- baseline usage.
- peak usage.
- failover capacity.
- deployment surge.
- batch overlap.
- autoscaling delay.

고가용성 구성에서는 한 zone이 죽어도 남은 zone이 버틸 수 있어야 합니다.

## 4. FinOps

FinOps는 클라우드 비용을 엔지니어링과 제품 의사결정에 연결합니다.

- showback/chargeback.
- unit economics.
- reserved/spot usage.
- rightsizing.
- idle resource cleanup.
- storage lifecycle.
- egress cost.

## 5. 비용 최적화 함정

- 비용만 줄이다 SLO 위반.
- 로그 샘플링 과도해 장애 분석 불가.
- spot 사용으로 안정성 하락.
- DB scale down 후 피크 장애.
- egress 비용을 설계 후에야 발견.

## 6. 체크리스트

- [ ] 피크와 성장률 기반 용량 계획이 있다.
- [ ] autoscaling 지연을 고려한다.
- [ ] failover 시 필요한 headroom이 있다.
- [ ] 서비스별 비용 소유자가 있다.
- [ ] unit cost를 추적한다.
- [ ] 비용 절감이 SLO에 미치는 영향을 검토한다.
