# 42. 관측성(Observability)

> 장애는 반드시 일어난다. 문제는 *얼마나 빨리 발견하고 진단하느냐*다.
> 금융 시스템에서 관측성(Observability)은 규제 요건이자 생존 전략이다.

---

## 1. 관측성이란 무엇인가?

**관측성(Observability)**이란 시스템 외부 출력(로그·메트릭·트레이스)만으로 시스템 내부 상태를 이해하고 추론할 수 있는 능력이다. 단순한 모니터링(monitoring)과 차별화되는 핵심 개념이다.

| 구분 | 모니터링(Monitoring) | 관측성(Observability) |
|---|---|---|
| 목적 | 알려진 이상 감지 | 알 수 없는 문제 탐구 |
| 질문 유형 | "지금 CPU가 높은가?" | "왜 특정 사용자의 주문이 지연되는가?" |
| 데이터 | 사전 정의 지표 | 임의 질의 가능 |
| 활용 | 경보 발송 | 근본 원인 분석(RCA) |

### 관측성 3대 축 (Three Pillars of Observability)

```
┌─────────────────────────────────────────────────┐
│                관측성 3대 축                      │
│                                                 │
│  📋 로그(Logs)     📊 메트릭(Metrics)   🔍 트레이스(Traces)  │
│  ─────────────    ──────────────     ───────────────  │
│  무슨 일이         얼마나 자주/         어디서 시간이    │
│  일어났는가?       얼마나 빠른가?        걸렸는가?       │
│                                                 │
│  이벤트 기록       수치 측정            요청 흐름 추적   │
│  (텍스트/JSON)     (시계열 데이터)       (span 트리)     │
└─────────────────────────────────────────────────┘
```

세 축은 각각 독립적이지만, 상관관계 ID(Correlation ID)로 연결할 때 진정한 관측성이 완성된다.

---

## 2. 구조화 로깅(Structured Logging)

### 2.1 왜 구조화 로깅인가?

전통적인 평문 로그:
```
2024-01-15 09:23:45 INFO OrderService - 주문 처리 완료: order-123, user: kim, amount: 50000
```

구조화 JSON 로그:
```json
{
  "timestamp": "2024-01-15T09:23:45.123Z",
  "level": "INFO",
  "service": "order-service",
  "traceId": "a1b2c3d4e5f6g7h8",
  "spanId": "b2c3d4e5",
  "orderId": "order-123",
  "userId": "kim",
  "amount": 50000,
  "event": "ORDER_COMPLETED",
  "durationMs": 142
}
```

구조화 로그는 로그 집계 시스템(ELK, Loki)에서 필드 기반 검색·집계·시각화가 가능하다.

### 2.2 MDC(Mapped Diagnostic Context)와 상관관계 ID

MDC는 현재 스레드의 컨텍스트 정보를 로그에 자동으로 주입하는 메커니즘이다.

```kotlin
// 상관관계 ID 필터 (Spring Security Filter Chain)
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val correlationId = request.getHeader("X-Correlation-ID")
            ?: UUID.randomUUID().toString()
        val orderId = request.getHeader("X-Order-ID")

        MDC.put("correlationId", correlationId)
        MDC.put("orderId", orderId ?: "")
        MDC.put("requestUri", request.requestURI)
        MDC.put("clientIp", request.remoteAddr)

        response.setHeader("X-Correlation-ID", correlationId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear() // 메모리 누수 방지 - 반드시 클리어
        }
    }
}
```

```kotlin
// logback-spring.xml - JSON 구조화 로그 설정
// resources/logback-spring.xml
```

```xml
<configuration>
    <springProfile name="prod">
        <appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>correlationId</includeMdcKeyName>
                <includeMdcKeyName>orderId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>spanId</includeMdcKeyName>
                <fieldNames>
                    <timestamp>timestamp</timestamp>
                    <message>message</message>
                    <logger>logger</logger>
                </fieldNames>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON_STDOUT"/>
        </root>
    </springProfile>
</configuration>
```

### 2.3 주문 흐름 추적 예시

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val riskCheckService: RiskCheckService,
    private val matchingEngineClient: MatchingEngineClient
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun processOrder(command: OrderCommand): OrderResult {
        val startTime = System.currentTimeMillis()

        MDC.put("orderId", command.orderId)
        MDC.put("userId", command.userId)
        MDC.put("symbol", command.symbol)

        log.info("주문 처리 시작",
            kv("event", "ORDER_PROCESSING_START"),
            kv("orderType", command.type.name),
            kv("quantity", command.quantity),
            kv("price", command.price)
        )

        return try {
            val riskResult = riskCheckService.check(command)
            if (!riskResult.passed) {
                log.warn("리스크 체크 실패",
                    kv("event", "ORDER_RISK_REJECTED"),
                    kv("reason", riskResult.reason)
                )
                return OrderResult.rejected(riskResult.reason)
            }

            val matchResult = matchingEngineClient.submit(command)

            val elapsed = System.currentTimeMillis() - startTime
            log.info("주문 처리 완료",
                kv("event", "ORDER_COMPLETED"),
                kv("matchId", matchResult.matchId),
                kv("filledQty", matchResult.filledQuantity),
                kv("durationMs", elapsed)
            )
            OrderResult.success(matchResult)

        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            log.error("주문 처리 오류",
                kv("event", "ORDER_ERROR"),
                kv("errorType", e.javaClass.simpleName),
                kv("durationMs", elapsed),
                e
            )
            throw e
        }
    }
}
```

---

## 3. 로그 레벨 전략

### 3.1 레벨별 사용 기준

| 레벨 | 사용 상황 | 예시 |
|---|---|---|
| `ERROR` | 즉시 대응 필요한 장애 | DB 연결 실패, 결제 오류 |
| `WARN` | 잠재적 문제, 추적 필요 | 리스크 체크 실패, 재시도 발생 |
| `INFO` | 비즈니스 이벤트 기록 | 주문 생성/체결/취소 |
| `DEBUG` | 개발/디버깅용 상세 정보 | SQL 쿼리, HTTP 요청 세부 |
| `TRACE` | 극히 상세한 추적 | 메서드 진입/종료 |

```kotlin
// 레벨별 설정 예시 (application.yml)
```

```yaml
logging:
  level:
    root: INFO
    com.yeouido.order: INFO
    com.yeouido.matching: DEBUG
    org.springframework.security: WARN
    org.hibernate.SQL: DEBUG  # 개발 환경에서만
    org.hibernate.type: TRACE # 개발 환경에서만
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### 3.2 금융 규제 준수 - 민감정보 마스킹

금융권에서는 개인정보보호법, 전자금융거래법에 따라 로그에 민감정보가 남으면 안 된다.

```kotlin
// 마스킹 유틸리티
object LogMasker {

    // 계좌번호 마스킹: 1234-567890-12345 → 1234-****90-12345
    fun maskAccountNumber(account: String): String {
        if (account.length < 8) return "****"
        return account.take(4) + "-****" + account.takeLast(7)
    }

    // 이름 마스킹: 홍길동 → 홍*동
    fun maskName(name: String): String = when {
        name.length <= 1 -> "*"
        name.length == 2 -> name.first() + "*"
        else -> name.first() + "*".repeat(name.length - 2) + name.last()
    }

    // 이메일 마스킹: user@example.com → u***@example.com
    fun maskEmail(email: String): String {
        val (local, domain) = email.split("@")
        val maskedLocal = local.first() + "*".repeat(local.length - 1)
        return "$maskedLocal@$domain"
    }

    // 카드번호 마스킹: 1234-5678-9012-3456 → 1234-****-****-3456
    fun maskCardNumber(card: String): String {
        val digits = card.replace("-", "")
        return digits.take(4) + "-****-****-" + digits.takeLast(4)
    }
}
```

```kotlin
// Logback 커스텀 마스킹 컨버터
class SensitiveDataMaskingConverter : MessageConverter() {

    private val accountPattern = Regex("\\b\\d{4}-\\d{6}-\\d{5}\\b")
    private val cardPattern = Regex("\\b\\d{4}-\\d{4}-\\d{4}-\\d{4}\\b")

    override fun convert(event: ILoggingEvent): String {
        var message = event.formattedMessage
        message = accountPattern.replace(message) { match ->
            LogMasker.maskAccountNumber(match.value)
        }
        message = cardPattern.replace(message) { match ->
            LogMasker.maskCardNumber(match.value)
        }
        return message
    }
}
```

> **실무 함정**: 로그 마스킹을 뒤늦게 추가하면 기존 로그에는 적용되지 않는다. 초기 설계 단계에서 DTO/엔티티에 `@JsonIgnore`, `toString()` 재정의 등을 통해 민감 필드가 절대 로그에 출력되지 않도록 원천 차단해야 한다.

---

## 4. 메트릭(Metrics)

### 4.1 Micrometer와 Prometheus

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
}
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: order-service
      environment: ${SPRING_PROFILES_ACTIVE:local}
      region: ap-northeast-2
```

### 4.2 비즈니스 메트릭 정의

시스템 메트릭(CPU, 메모리)만으로는 부족하다. 금융 서비스에서는 **비즈니스 메트릭**이 핵심이다.

```kotlin
@Component
class OrderMetrics(private val meterRegistry: MeterRegistry) {

    // 카운터: 주문 건수
    private val orderCounter = meterRegistry.counter(
        "order.submitted.total",
        "service", "order-service"
    )

    // 타이머: 주문 처리 지연
    private val orderProcessingTimer = Timer.builder("order.processing.duration")
        .description("주문 처리 소요 시간")
        .publishPercentiles(0.5, 0.95, 0.99)
        .publishPercentileHistogram()
        .register(meterRegistry)

    // 게이지: 대기 중인 주문 수
    private val pendingOrderGauge = meterRegistry.gauge(
        "order.pending.count",
        Tags.of("service", "order-service"),
        AtomicLong(0)
    )!!

    // 히스토그램: 주문 금액 분포
    private val orderAmountDistribution = DistributionSummary.builder("order.amount.distribution")
        .description("주문 금액 분포")
        .baseUnit("KRW")
        .publishPercentiles(0.5, 0.75, 0.95, 0.99)
        .register(meterRegistry)

    fun recordOrderSubmitted(symbol: String, orderType: String) {
        meterRegistry.counter("order.submitted.total",
            "symbol", symbol,
            "type", orderType
        ).increment()
    }

    fun recordOrderFilled(symbol: String, amount: Double, latencyMs: Long) {
        meterRegistry.counter("order.filled.total", "symbol", symbol).increment()
        orderAmountDistribution.record(amount)
        orderProcessingTimer.record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    fun recordOrderRejected(symbol: String, reason: String) {
        meterRegistry.counter("order.rejected.total",
            "symbol", symbol,
            "reason", reason
        ).increment()
    }

    // 체결률(Fill Rate) 메트릭
    fun getFillRate(symbol: String): Double {
        val submitted = meterRegistry.counter("order.submitted.total", "symbol", symbol).count()
        val filled = meterRegistry.counter("order.filled.total", "symbol", symbol).count()
        return if (submitted > 0) filled / submitted else 0.0
    }
}
```

### 4.3 핵심 비즈니스 메트릭 목록

| 메트릭 이름 | 유형 | 설명 | 경보 임계값 |
|---|---|---|---|
| `order.submitted.total` | Counter | 제출 주문 건수 | - |
| `order.filled.total` | Counter | 체결 주문 건수 | - |
| `order.rejected.total` | Counter | 거부 주문 건수 | 분당 100건 이상 |
| `order.processing.duration` | Timer | 주문 처리 지연 | p99 > 500ms |
| `order.pending.count` | Gauge | 대기 주문 수 | 1000건 이상 |
| `matching.latency.ms` | Timer | 매칭 엔진 지연 | p99 > 10ms |
| `settlement.delay.seconds` | Gauge | 결제 지연 시간 | 30초 이상 |
| `market.data.lag.ms` | Gauge | 시장 데이터 지연 | 100ms 이상 |

---

## 5. Grafana 대시보드

### 5.1 대시보드 구성 원칙

금융 시스템 Grafana 대시보드는 **USE 방법론**과 **RED 방법론**을 함께 사용한다.

```
USE 방법론 (인프라):         RED 방법론 (서비스):
- Utilization (사용률)      - Rate (요청 비율)
- Saturation (포화도)       - Errors (오류 비율)
- Errors (오류)             - Duration (지연)
```

### 5.2 주요 패널 구성

```yaml
# grafana/dashboards/order-service.json (요약)
panels:
  - title: "주문 체결률 (Fill Rate)"
    type: stat
    query: |
      rate(order_filled_total[5m]) / rate(order_submitted_total[5m]) * 100
    thresholds:
      - color: red
        value: 80    # 80% 미만이면 빨간색 경고
      - color: yellow
        value: 90
      - color: green
        value: 95

  - title: "주문 처리 지연 분포 (p50/p95/p99)"
    type: graph
    queries:
      - legend: "p50"
        query: histogram_quantile(0.50, rate(order_processing_duration_seconds_bucket[5m]))
      - legend: "p95"
        query: histogram_quantile(0.95, rate(order_processing_duration_seconds_bucket[5m]))
      - legend: "p99"
        query: histogram_quantile(0.99, rate(order_processing_duration_seconds_bucket[5m]))

  - title: "거부 주문 원인 분류"
    type: piechart
    query: |
      sum by (reason) (rate(order_rejected_total[5m]))
```

---

## 6. 분산 추적(Distributed Tracing)

### 6.1 TraceId / SpanId 개념

```
클라이언트 요청
    │
    ▼ TraceId: "abc123" (전체 요청 흐름 식별)
┌──────────────────────────────────────┐
│ API Gateway                          │ SpanId: "span-001"
│ POST /api/orders                     │ (200ms)
└──────────────────┬───────────────────┘
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
┌───────────────┐    ┌─────────────────┐
│ Order Service │    │  Risk Service   │
│ SpanId:002    │    │  SpanId:003     │
│ (150ms)       │    │  (80ms)         │
└───────┬───────┘    └─────────────────┘
        │
        ▼
┌─────────────────────┐
│  Matching Engine    │
│  SpanId: 004        │
│  (30ms)             │
└─────────────────────┘
```

### 6.2 OpenTelemetry 설정

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0")
}
```

```yaml
# application.yml - OpenTelemetry 설정
management:
  tracing:
    sampling:
      probability: 1.0  # 개발: 100%, 운영: 0.1 (10%)
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
    metrics:
      endpoint: http://otel-collector:4318/v1/metrics

spring:
  application:
    name: order-service
```

```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

exporters:
  jaeger:
    endpoint: jaeger:14250
    tls:
      insecure: true
  prometheus:
    endpoint: "0.0.0.0:8889"

service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [jaeger]
    metrics:
      receivers: [otlp]
      exporters: [prometheus]
```

### 6.3 주문 흐름 커스텀 스팬 추가

```kotlin
@Service
class OrderService(
    private val tracer: Tracer,
    private val orderRepository: OrderRepository
) {
    fun processOrder(command: OrderCommand): OrderResult {
        // 커스텀 스팬 생성
        val span = tracer.nextSpan()
            .name("order.process")
            .tag("order.id", command.orderId)
            .tag("order.symbol", command.symbol)
            .tag("order.type", command.type.name)
            .start()

        return tracer.withSpan(span).use {
            try {
                val result = doProcessOrder(command)
                span.tag("order.status", "COMPLETED")
                span.tag("fill.rate", result.fillRate.toString())
                result
            } catch (e: Exception) {
                span.tag("error", "true")
                span.tag("error.message", e.message ?: "unknown")
                throw e
            } finally {
                span.end()
            }
        }
    }

    private fun doProcessOrder(command: OrderCommand): OrderResult {
        // 리스크 체크 스팬
        val riskSpan = tracer.nextSpan().name("risk.check").start()
        val riskResult = try {
            riskCheckService.check(command).also {
                riskSpan.tag("risk.passed", it.passed.toString())
            }
        } finally {
            riskSpan.end()
        }

        if (!riskResult.passed) return OrderResult.rejected(riskResult.reason)

        // 매칭 엔진 스팬
        val matchSpan = tracer.nextSpan().name("matching.submit").start()
        return try {
            matchingEngineClient.submit(command).let {
                matchSpan.tag("match.id", it.matchId)
                OrderResult.success(it)
            }
        } finally {
            matchSpan.end()
        }
    }
}
```

> **실무 함정**: 운영 환경에서 100% 샘플링은 성능 저하를 유발한다. Jaeger의 적응형 샘플링(Adaptive Sampling)이나 헤드 기반/테일 기반 샘플링을 적용해 중요 요청(에러, 지연)은 반드시 수집하고 나머지는 일부만 수집하는 전략을 세워야 한다.

---

## 7. 알림(Alerting)과 SLO/에러 버짓

### 7.1 SLO(Service Level Objective) 정의

```yaml
# SLO 정의 (금융 서비스 예시)
slos:
  - name: "주문 처리 성공률"
    target: 99.95%  # 월간 다운타임 21.9분 허용
    window: 30d
    metric: |
      1 - (rate(order_rejected_total{reason="SYSTEM_ERROR"}[30d])
           / rate(order_submitted_total[30d]))

  - name: "주문 처리 지연"
    target: 99%  # 99%의 요청이 500ms 이내
    window: 7d
    metric: |
      histogram_quantile(0.99, rate(order_processing_duration_seconds_bucket[7d])) < 0.5

  - name: "시장 데이터 갱신 지연"
    target: 99.9%
    window: 1d
    metric: |
      market_data_lag_ms < 100
```

### 7.2 에러 버짓(Error Budget)

```
에러 버짓 = 100% - SLO 목표

99.95% SLO 기준:
- 월간 허용 다운타임: 21.9분
- 에러 버짓: 0.05% = 21.9분/월

에러 버짓 소진율이 50% 초과 → 신중한 릴리즈
에러 버짓 소진율이 100% 초과 → 신규 기능 배포 중단, 안정화 집중
```

### 7.3 Prometheus 알림 규칙

```yaml
# prometheus/alerts/order-service.yml
groups:
  - name: order-service-alerts
    rules:
      # 주문 오류율 경보
      - alert: HighOrderErrorRate
        expr: |
          rate(order_rejected_total{reason="SYSTEM_ERROR"}[5m]) /
          rate(order_submitted_total[5m]) > 0.01
        for: 2m
        labels:
          severity: critical
          team: trading
        annotations:
          summary: "주문 시스템 오류율 1% 초과"
          description: "지난 5분간 주문 오류율: {{ $value | humanizePercentage }}"
          runbook: "https://wiki.yeouido.com/runbooks/high-order-error-rate"

      # 주문 처리 지연 경보
      - alert: SlowOrderProcessing
        expr: |
          histogram_quantile(0.99,
            rate(order_processing_duration_seconds_bucket[5m])
          ) > 0.5
        for: 5m
        labels:
          severity: warning
          team: trading
        annotations:
          summary: "주문 처리 p99 지연 500ms 초과"
          description: "현재 p99: {{ $value | humanizeDuration }}"

      # 매칭 엔진 연결 끊김
      - alert: MatchingEngineDown
        expr: up{job="matching-engine"} == 0
        for: 30s
        labels:
          severity: critical
          team: trading
          pagerduty: "true"
        annotations:
          summary: "매칭 엔진 연결 불가"
          description: "즉시 대응 필요"

      # 에러 버짓 소진 경보
      - alert: ErrorBudgetBurnRate
        expr: |
          (
            1 - (
              rate(order_rejected_total{reason="SYSTEM_ERROR"}[1h]) /
              rate(order_submitted_total[1h])
            )
          ) < 0.9995
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "SLO 에러 버짓 소진 속도 과다"
          description: "현재 소진율이 SLO 목표를 초과 중"
```

### 7.4 알림 라우팅 전략

```yaml
# alertmanager/config.yml
route:
  group_by: ['alertname', 'team']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'slack-trading'

  routes:
    - match:
        severity: critical
      receiver: 'pagerduty-trading'
      continue: true  # PagerDuty와 Slack 모두 발송

    - match:
        team: trading
      receiver: 'slack-trading'

receivers:
  - name: 'slack-trading'
    slack_configs:
      - api_url: '${SLACK_WEBHOOK_URL}'
        channel: '#trading-alerts'
        title: '[{{ .Status | toUpper }}] {{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'

  - name: 'pagerduty-trading'
    pagerduty_configs:
      - service_key: '${PAGERDUTY_SERVICE_KEY}'
        severity: '{{ if eq .Labels.severity "critical" }}critical{{ else }}warning{{ end }}'
```

---

## 8. 헬스체크와 액추에이터(Actuator)

### 8.1 커스텀 헬스 인디케이터

```kotlin
@Component
class MatchingEngineHealthIndicator(
    private val matchingEngineClient: MatchingEngineClient
) : HealthIndicator {

    override fun health(): Health {
        return try {
            val pingResult = matchingEngineClient.ping()
            if (pingResult.latencyMs < 50) {
                Health.up()
                    .withDetail("latencyMs", pingResult.latencyMs)
                    .withDetail("status", "CONNECTED")
                    .build()
            } else {
                Health.degraded()
                    .withDetail("latencyMs", pingResult.latencyMs)
                    .withDetail("status", "SLOW")
                    .withDetail("threshold", 50)
                    .build()
            }
        } catch (e: Exception) {
            Health.down()
                .withDetail("error", e.message)
                .withDetail("status", "DISCONNECTED")
                .build()
        }
    }
}

@Component
class MarketDataHealthIndicator(
    private val marketDataCache: MarketDataCache
) : HealthIndicator {

    override fun health(): Health {
        val lastUpdateMs = System.currentTimeMillis() - marketDataCache.lastUpdatedAt
        return if (lastUpdateMs < 1000) {
            Health.up().withDetail("lastUpdateMs", lastUpdateMs).build()
        } else {
            Health.down()
                .withDetail("lastUpdateMs", lastUpdateMs)
                .withDetail("message", "시장 데이터 갱신 지연")
                .build()
        }
    }
}
```

### 8.2 Kubernetes 프로브 연동

```yaml
# k8s/deployment.yaml
spec:
  containers:
    - name: order-service
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        initialDelaySeconds: 60
        periodSeconds: 10
        failureThreshold: 3

      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8080
        initialDelaySeconds: 30
        periodSeconds: 5
        failureThreshold: 3

      startupProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        failureThreshold: 30
        periodSeconds: 10
```

```kotlin
// 그레이스풀 셧다운 - 진행 중인 주문 처리 완료 후 종료
@Component
class GracefulShutdownHandler(
    private val pendingOrderTracker: PendingOrderTracker
) : DisposableBean {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun destroy() {
        log.info("셧다운 시작 - 진행 중인 주문 처리 대기")
        val deadline = System.currentTimeMillis() + 30_000 // 30초 대기

        while (pendingOrderTracker.count > 0 && System.currentTimeMillis() < deadline) {
            log.info("대기 중인 주문: ${pendingOrderTracker.count}건")
            Thread.sleep(1000)
        }

        if (pendingOrderTracker.count > 0) {
            log.warn("타임아웃: 미처리 주문 ${pendingOrderTracker.count}건 남음")
        } else {
            log.info("모든 주문 처리 완료 - 정상 종료")
        }
    }
}
```

---

## 9. 장애 발견·진단 체계

### 9.1 장애 대응 플로우차드

```
경보 수신
    │
    ▼
[1] 영향 범위 파악 (5분 이내)
    - Grafana: 어느 서비스가 영향받는가?
    - 로그: 최초 에러 발생 시각은?
    - 트레이스: 어디서 지연/실패가 발생했는가?
    │
    ▼
[2] 임시 조치 (Mitigation) (15분 이내)
    - 트래픽 우회 / 롤백 / 서킷브레이커 수동 Open
    │
    ▼
[3] 근본 원인 분석 (RCA)
    - 로그 상관관계 분석
    - 트레이스 병목 구간 특정
    - 메트릭 이상 패턴 확인
    │
    ▼
[4] 영구 해결책 적용
[5] 사후 회고 (Post-mortem) 작성
```

### 9.2 로그 쿼리 예시 (Grafana Loki)

```logql
# 특정 주문 ID 전체 흐름 추적
{service="order-service"} |= `orderId="order-12345"`

# 지난 1시간 내 시스템 오류만 조회
{service=~"order-service|matching-engine"}
  | json
  | level="ERROR"
  | event="ORDER_ERROR"
  | __error__=""

# 느린 주문(500ms 초과) 필터링
{service="order-service"}
  | json
  | durationMs > 500
  | line_format "{{.orderId}} {{.durationMs}}ms {{.event}}"
```

### 9.3 장애 발견을 빠르게 하는 체크리스트

- [ ] 모든 서비스에 헬스체크 엔드포인트 구현
- [ ] 비즈니스 메트릭에 의미 있는 임계값 설정
- [ ] 알림에 Runbook 링크 포함
- [ ] TraceId가 클라이언트 응답 헤더에 포함됨
- [ ] 에러 발생 시 즉시 구조화 로그 출력
- [ ] 주요 의존성(DB, 메시지 큐, 외부 API) 헬스체크 연동
- [ ] PagerDuty/OpsGenie 온콜 로테이션 설정
- [ ] Grafana 대시보드 즐겨찾기 팀 공유
- [ ] 정기 게임데이(GameDay) / 카오스 엔지니어링 실시

---

## 10. 전체 관측성 아키텍처 요약

```
애플리케이션 (Spring Boot + Kotlin)
    │
    ├── 로그 (Logback → Loki)
    │       └── Grafana (로그 탐색)
    │
    ├── 메트릭 (Micrometer → Prometheus)
    │       └── Grafana (대시보드 + 알림)
    │               └── Alertmanager → Slack / PagerDuty
    │
    └── 트레이스 (OpenTelemetry → Jaeger/Tempo)
            └── Grafana (트레이스 뷰어)
```

세 가지 신호(로그·메트릭·트레이스)가 Grafana에서 통합되어 **단일 유리창(Single Pane of Glass)**을 구성한다. 메트릭에서 이상 감지 → 트레이스로 원인 구간 특정 → 로그로 상세 컨텍스트 확인의 흐름이 이상적인 장애 진단 경로다.

---

## 11. 금융권 특수 고려사항

1. **감사 로그(Audit Log) 분리**: 거래 기록은 일반 애플리케이션 로그와 별도 저장소에 보관. 변조 방지를 위해 WORM(Write Once Read Many) 스토리지 사용 권장.

2. **로그 보존 기간**: 전자금융거래법상 5년 이상 보존 의무. 오래된 로그는 저비용 S3/오브젝트 스토리지로 아카이빙.

3. **개인정보 국외 반출 제한**: 로그 수집 인프라가 해외 리전에 있으면 개인정보가 포함된 로그가 국외로 전송될 수 있음. 국내 리전 사용 또는 마스킹 후 전송 필수.

4. **장중/장외 시간 구분**: 증권 거래 시스템은 장중(09:00-15:30)에 트래픽이 집중됨. 알림 임계값을 시간대별로 다르게 적용해 오탐(False Positive)을 줄인다.

5. **규제 기관 리포팅**: 금감원 등 규제 기관 요청 시 특정 기간의 거래 로그를 즉시 제출할 수 있어야 한다. 검색 인덱스 보존 기간을 비용과 함께 설계해야 한다.

---

---
이전: [41. 메시지 큐와 이벤트 드리븐](41-message-queue) · 다음: [43. 성능 튜닝](43-performance-tuning) · [전체 커리큘럼](/curriculum)
