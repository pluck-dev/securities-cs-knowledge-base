# 82. 관측성 심화와 OpenTelemetry

> 관측성은 로그를 많이 남기는 것이 아니라 시스템 상태를 질문하고 답할 수 있게 만드는 능력입니다.

## 1. 관측성의 목표

운영 중 모르는 문제가 생겼을 때 코드를 다시 배포하지 않고도 다음 질문에 답할 수 있어야 합니다.

- 어떤 사용자가 영향을 받았는가?
- 어느 서비스/의존성이 느린가?
- 언제부터 문제가 시작되었는가?
- 배포와 관련 있는가?
- 재시도나 큐 적체가 있는가?
- 비즈니스 영향은 어느 정도인가?

## 2. Logs, Metrics, Traces의 역할

| 신호 | 강점 | 약점 |
|---|---|---|
| Logs | 개별 사건 설명 | 비용, 검색 복잡도 |
| Metrics | 알림과 추세 | 상세 원인 부족 |
| Traces | 요청 경로 | 샘플링과 비용 |

세 가지를 trace id, service name, deployment version으로 연결해야 효과가 큽니다.

## 3. OpenTelemetry

OpenTelemetry는 로그, 메트릭, 트레이스 수집을 표준화하는 프로젝트입니다.

핵심 개념:

- span.
- trace.
- context propagation.
- attribute.
- resource.
- exporter.
- collector.

벤더 종속을 줄이고 서비스 간 추적 컨텍스트를 일관되게 전달합니다.

## 4. 메트릭 설계

좋은 메트릭 이름과 label 설계가 중요합니다.

주의할 것:

- cardinality 폭발.
- user id 같은 고유값 label 금지.
- histogram bucket 설계.
- counter/gauge/histogram/summary 구분.
- RED: Rate, Errors, Duration.
- USE: Utilization, Saturation, Errors.

## 5. 로그 설계

구조화 로그 필드:

- timestamp.
- level.
- service.
- environment.
- traceId/spanId.
- user/account id는 정책에 따라 마스킹.
- event code.
- result.
- durationMs.

민감정보는 절대 로그에 남기지 않습니다.

## 6. Trace 설계

Trace는 모든 함수를 span으로 만드는 것이 아닙니다.

좋은 span:

- 외부 API 호출.
- DB query.
- 큐 publish/consume.
- 큰 도메인 단계.
- cache get/set.

너무 세밀하면 비용과 노이즈가 증가합니다.

## 7. 알림 품질

알림은 사용자 영향 중심이어야 합니다.

- SLO burn rate alert.
- error budget 소비.
- p95/p99 latency.
- queue lag.
- synthetic monitoring.
- dependency availability.

CPU 80%는 원인 지표일 뿐 사용자 영향과 연결되지 않으면 소음이 될 수 있습니다.

## 8. 체크리스트

- [ ] trace id가 프론트엔드부터 백엔드, 큐까지 전파된다.
- [ ] label cardinality를 관리한다.
- [ ] SLO 기반 알림이 있다.
- [ ] 로그에 민감정보가 없다.
- [ ] 배포 버전과 환경 정보가 모든 신호에 포함된다.
- [ ] 대시보드가 사용자 영향에서 원인 지표로 내려간다.
