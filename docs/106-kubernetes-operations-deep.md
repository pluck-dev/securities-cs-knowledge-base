# 106. Kubernetes 운영 심화

> Kubernetes는 배포 도구가 아니라 분산 시스템 운영 플랫폼입니다. 리소스, 네트워크, 스케줄링, 장애 모드를 이해해야 합니다.

## 1. Control Plane

- API Server: 모든 상태 변경의 관문.
- etcd: 클러스터 상태 저장소.
- Scheduler: Pod를 Node에 배치.
- Controller Manager: desired state와 actual state를 맞춤.
- Cloud Controller: 클라우드 리소스와 통합.

장애 분석 시 “Pod가 안 뜬다”를 API, scheduler, image pull, volume, network, probe 문제로 분해해야 합니다.

## 2. Workload 리소스

| 리소스 | 용도 |
|---|---|
| Deployment | stateless 앱 배포 |
| StatefulSet | 안정적 identity와 storage가 필요한 앱 |
| DaemonSet | 모든/일부 node에 agent 실행 |
| Job | 완료되는 작업 |
| CronJob | 주기 작업 |

StatefulSet은 DB를 쉽게 운영하게 해주는 마법이 아닙니다. 백업, 복구, quorum, storage 성능을 별도로 설계해야 합니다.

## 3. 리소스 요청과 제한

- request는 스케줄링 기준.
- limit은 최대 사용량 제한.
- CPU limit은 throttling을 만들 수 있다.
- memory limit 초과는 OOMKill로 이어진다.
- QoS class는 eviction 우선순위에 영향을 준다.

request/limit 없는 Pod는 클러스터 안정성을 해칩니다.

## 4. Probe

- startupProbe: 시작 완료 전 liveness 실패 방지.
- readinessProbe: 트래픽 받을 준비 여부.
- livenessProbe: 재시작 필요 여부.

liveness가 너무 공격적이면 일시적 지연을 장애로 오판해 재시작 루프를 만들 수 있습니다.

## 5. 배포와 롤백

- rolling update.
- maxSurge/maxUnavailable.
- PodDisruptionBudget.
- preStop hook.
- terminationGracePeriodSeconds.
- schema compatibility.

무중단 배포는 Kubernetes 설정만으로 되지 않고 앱의 graceful shutdown과 하위 호환성이 필요합니다.

## 6. 운영 체크리스트

- [ ] 모든 Pod에 request/limit이 있다.
- [ ] readiness/liveness/startup probe가 역할에 맞다.
- [ ] PDB와 graceful shutdown이 설정되어 있다.
- [ ] node pressure, OOMKill, CPU throttling을 모니터링한다.
- [ ] etcd 백업과 복구 절차가 있다.
- [ ] 배포 중 구버전/신버전 동시 실행을 지원한다.
