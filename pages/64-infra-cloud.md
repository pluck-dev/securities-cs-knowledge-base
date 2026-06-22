# 64. 인프라와 클라우드 기본

> 인프라는 애플리케이션이 안전하고 확장 가능하게 실행되는 기반입니다. 서버, 네트워크, 배포, 보안 경계를 함께 이해해야 합니다.

## 1. 인프라 구성 요소

```
DNS → CDN/WAF → Load Balancer → App Server/Container → DB/Cache/Queue/Object Storage
                         ↘ Observability / CI/CD / Secrets / IAM
```

## 2. 컴퓨트 선택

| 방식 | 장점 | 단점 |
|---|---|---|
| VM | 익숙함, 제어권 큼 | 패치/운영 부담 |
| Container | 배포 일관성, 밀도 | 이미지/오케스트레이션 학습 필요 |
| Serverless | 운영 부담 감소, 자동 확장 | cold start, 제한, 관측성 복잡도 |
| Managed Platform | 생산성 | 벤더 종속, 세부 제어 제한 |

## 3. 네트워크

- VPC/VNet: 격리된 가상 네트워크.
- Subnet: 네트워크 범위 분할.
- Public subnet: 인터넷에서 접근 가능한 자원이 위치.
- Private subnet: 내부 자원 배치.
- NAT Gateway: private subnet에서 외부로 나갈 때 사용.
- Security Group/Firewall: 인바운드·아웃바운드 허용 규칙.
- Route Table: 패킷 경로 결정.

원칙: DB와 내부 큐는 public internet에 직접 노출하지 않습니다.

## 4. 로드 밸런싱

- L4 LB: TCP/UDP 레벨.
- L7 LB: HTTP 경로, 헤더, 호스트 기반 라우팅.
- Health check가 잘못되면 살아있는 서버도 트래픽을 못 받거나 죽은 서버가 계속 받는다.
- sticky session은 확장성과 장애 대응을 어렵게 할 수 있다.

## 5. Kubernetes 기본

| 개념 | 의미 |
|---|---|
| Pod | 함께 배치되는 컨테이너 묶음 |
| Deployment | 원하는 Pod 수와 배포 전략 선언 |
| Service | Pod 집합에 안정적인 네트워크 이름 제공 |
| Ingress | 외부 HTTP 라우팅 |
| ConfigMap | 설정 값 |
| Secret | 민감 설정 |
| HPA | 부하에 따른 자동 확장 |

주의점:

- resource request/limit을 설정한다.
- readiness와 liveness를 구분한다.
- config와 secret을 이미지에 굽지 않는다.
- rolling update 중 양 버전 호환성을 지킨다.

## 6. IaC

Infrastructure as Code는 인프라 변경을 코드 리뷰와 버전 관리 대상으로 만듭니다.

- Terraform, Pulumi, CloudFormation 같은 도구를 사용한다.
- 수동 콘솔 변경은 drift를 만든다.
- state 파일은 민감정보와 잠금 정책을 고려해야 한다.
- 모듈화는 재사용보다 소유권과 변경 경계를 기준으로 한다.

## 7. 환경 분리

- local: 개발자 개인.
- dev: 통합 개발.
- staging: 운영과 최대한 유사한 검증.
- prod: 실제 사용자.

환경별 차이는 코드 분기가 아니라 설정으로 관리합니다. 단, 운영 데이터와 비운영 데이터는 명확히 분리해야 합니다.

## 8. 확장성

### 수직 확장

서버 크기를 키운다. 단순하지만 한계가 있다.

### 수평 확장

서버 수를 늘린다. 세션, 캐시, DB 연결, 락, 배치 중복 실행을 고려해야 한다.

### 병목 위치

- CPU-bound: 계산이 병목.
- Memory-bound: 메모리 부족 또는 GC.
- IO-bound: DB, 디스크, 네트워크 지연.
- Lock-bound: 공유 자원 경합.

## 9. 비용 감각

- 항상 켜진 리소스가 비용의 대부분을 만든다.
- 로그·메트릭·트레이스도 저장 비용이 있다.
- egress traffic은 예상보다 비쌀 수 있다.
- 고가용성은 중복 자원 비용을 동반한다.
- 비용 최적화는 사용량 측정과 권한 있는 삭제 프로세스가 필요하다.

## 10. 인프라 체크리스트

- [ ] public/private subnet 경계가 명확하다.
- [ ] DB, 캐시, 큐가 인터넷에 직접 노출되지 않는다.
- [ ] health check와 graceful shutdown이 맞물린다.
- [ ] IaC로 변경 이력이 남는다.
- [ ] secret이 코드와 이미지에 없다.
- [ ] autoscaling 기준과 한계가 문서화되어 있다.
- [ ] 비용 알림과 예산이 설정되어 있다.
