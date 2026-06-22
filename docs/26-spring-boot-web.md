# 26. 스프링 웹 계층: MVC와 WebFlux

> **학습 목표**: 스프링 웹 계층의 자동설정 원리를 이해하고, 증권 주문 API를 MVC 모범사례로 설계하며, 블로킹/논블로킹 모델 선택 기준을 파악한다.

---

## 26.1 자동설정(Auto-Configuration) 원리

### "스프링 부트는 왜 설정이 거의 없어도 동작하는가?"

스프링 부트의 핵심 철학은 **Convention over Configuration(관례 우선)**입니다.

```kotlin
// 이것만 있어도 웹 서버가 뜨는 이유는?
@SpringBootApplication
fun main(args: Array<String>) {
    runApplication<BrokerageApplication>(*args)
}
```

내부에서는 다음이 일어납니다:

```
runApplication()
    ↓
SpringApplication.run()
    ↓
@EnableAutoConfiguration 활성화
    ↓
클래스패스 스캔 (spring-boot-autoconfigure JAR 내부)
    ↓
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 읽기
    ↓
@ConditionalOn* 조건 평가
    ↓
조건 충족 시 자동 빈 등록
```

### 조건부 자동설정

```kotlin
// spring-boot-autoconfigure 내부의 코드 (참고용)
@AutoConfiguration
@ConditionalOnClass(DataSource::class, JdbcTemplate::class)  // 클래스패스에 존재할 때만
@ConditionalOnMissingBean(JdbcOperations::class)              // 개발자가 직접 등록 안 했을 때만
class JdbcTemplateAutoConfiguration {
    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)
}
```

| 조건 어노테이션 | 의미 |
|----------------|------|
| `@ConditionalOnClass` | 특정 클래스가 클래스패스에 있을 때 |
| `@ConditionalOnMissingBean` | 해당 타입의 빈이 없을 때 |
| `@ConditionalOnProperty` | 특정 프로퍼티 값일 때 |
| `@ConditionalOnWebApplication` | 웹 환경일 때 |

> **실무 팁**: `application.properties`에서 `debug=true`로 설정하거나, 실행 시 `--debug` 플래그를 붙이면 어떤 자동설정이 적용됐는지 로그에 출력됩니다. 새 라이브러리 추가 후 기대한 설정이 안 되면 이 로그를 먼저 확인하세요.

```properties
# application.properties
debug=true
```

---

## 26.2 @RestController와 라우팅

```kotlin
@RestController                         // @Controller + @ResponseBody
@RequestMapping("/api/v1/orders")       // 기본 경로
class OrderController(
    private val orderService: OrderService,
    private val orderMapper: OrderMapper
) {
    // GET /api/v1/orders
    @GetMapping
    fun getOrders(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Page<OrderResponse>> {
        val orders = orderService.findAll(PageRequest.of(page, size))
        return ResponseEntity.ok(orders.map(orderMapper::toResponse))
    }

    // GET /api/v1/orders/{orderId}
    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: String): ResponseEntity<OrderResponse> {
        val order = orderService.findById(orderId)
        return ResponseEntity.ok(orderMapper.toResponse(order))
    }

    // POST /api/v1/orders
    @PostMapping
    fun placeOrder(
        @Valid @RequestBody request: PlaceOrderRequest
    ): ResponseEntity<OrderResponse> {
        val order = orderService.placeOrder(orderMapper.toOrder(request))
        return ResponseEntity
            .created(URI.create("/api/v1/orders/${order.orderId}"))
            .body(orderMapper.toResponse(order))
    }

    // DELETE /api/v1/orders/{orderId}
    @DeleteMapping("/{orderId}")
    fun cancelOrder(@PathVariable orderId: String): ResponseEntity<Void> {
        orderService.cancelOrder(orderId)
        return ResponseEntity.noContent().build()
    }

    // PATCH /api/v1/orders/{orderId}
    @PatchMapping("/{orderId}")
    fun modifyOrder(
        @PathVariable orderId: String,
        @Valid @RequestBody request: ModifyOrderRequest
    ): ResponseEntity<OrderResponse> {
        val order = orderService.modifyOrder(orderId, request)
        return ResponseEntity.ok(orderMapper.toResponse(order))
    }
}
```

### HTTP 메서드와 증권 API 매핑

| HTTP 메서드 | 용도 | 증권 예시 |
|-------------|------|-----------|
| `GET` | 조회 (멱등, 안전) | 주문 조회, 잔고 조회 |
| `POST` | 생성 (비멱등) | 신규 주문, 계좌 개설 |
| `PUT` | 전체 교체 (멱등) | 주문 전체 수정 |
| `PATCH` | 부분 수정 (비멱등) | 주문 수량/가격 변경 |
| `DELETE` | 삭제 (멱등) | 주문 취소 |

---

## 26.3 파라미터 바인딩

### @PathVariable — URL 경로 변수

```kotlin
// GET /api/v1/accounts/{accountId}/orders/{orderId}
@GetMapping("/accounts/{accountId}/orders/{orderId}")
fun getOrderByAccount(
    @PathVariable accountId: String,
    @PathVariable orderId: String
): ResponseEntity<OrderResponse> {
    val order = orderService.findByAccountAndOrder(accountId, orderId)
    return ResponseEntity.ok(orderMapper.toResponse(order))
}
```

### @RequestParam — 쿼리 파라미터

```kotlin
// GET /api/v1/orders?status=PENDING&ticker=005930&from=2024-01-01&to=2024-12-31
@GetMapping
fun searchOrders(
    @RequestParam(required = false) status: OrderStatus?,
    @RequestParam(required = false) ticker: String?,
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int
): ResponseEntity<Page<OrderResponse>> {
    val filter = OrderFilter(status = status, ticker = ticker, from = from, to = to)
    val orders = orderService.search(filter, PageRequest.of(page, size))
    return ResponseEntity.ok(orders.map(orderMapper::toResponse))
}
```

### @RequestBody — JSON 요청 본문

```kotlin
data class PlaceOrderRequest(
    val accountId: String,
    val ticker: String,
    val side: OrderSide,            // BUY / SELL
    val orderType: OrderType,       // LIMIT / MARKET
    val quantity: Int,
    val price: BigDecimal?          // 시장가 주문은 null
)

@PostMapping
fun placeOrder(@Valid @RequestBody request: PlaceOrderRequest): ResponseEntity<OrderResponse> {
    // @Valid가 있으면 Bean Validation 자동 실행
    val order = orderService.placeOrder(orderMapper.toOrder(request))
    return ResponseEntity.created(URI.create("/api/v1/orders/${order.orderId}"))
        .body(orderMapper.toResponse(order))
}
```

### @RequestHeader — HTTP 헤더

```kotlin
@PostMapping
fun placeOrder(
    @RequestHeader("X-Request-Id") requestId: String,      // 클라이언트 요청 ID (멱등키)
    @RequestHeader("Authorization") authorization: String,
    @Valid @RequestBody request: PlaceOrderRequest
): ResponseEntity<OrderResponse> {
    // requestId로 중복 요청 방지 (멱등성 보장)
    if (orderService.isDuplicate(requestId)) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build()
    }
    val order = orderService.placeOrder(orderMapper.toOrder(request), requestId)
    return ResponseEntity.created(URI.create("/api/v1/orders/${order.orderId}"))
        .body(orderMapper.toResponse(order))
}
```

> **증권 실무 팁**: 주문 API는 반드시 **멱등성(Idempotency)** 을 고려해야 합니다. 클라이언트가 네트워크 오류로 같은 요청을 재전송했을 때 주문이 두 번 체결되면 안 됩니다. `X-Request-Id` (또는 `Idempotency-Key`) 헤더를 사용해 중복 요청을 감지하세요.

---

## 26.4 ResponseEntity와 HTTP 상태코드

```kotlin
// 다양한 ResponseEntity 패턴
@RestController
class OrderController {

    // 200 OK + 본문
    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: String): ResponseEntity<OrderResponse> {
        val order = orderService.findById(id)
        return ResponseEntity.ok(orderMapper.toResponse(order))
        // = ResponseEntity(body, HttpStatus.OK)
    }

    // 201 Created + Location 헤더 + 본문
    @PostMapping
    fun create(@RequestBody req: PlaceOrderRequest): ResponseEntity<OrderResponse> {
        val order = orderService.placeOrder(orderMapper.toOrder(req))
        return ResponseEntity
            .created(URI.create("/api/v1/orders/${order.orderId}"))
            .body(orderMapper.toResponse(order))
    }

    // 204 No Content (삭제 성공)
    @DeleteMapping("/{id}")
    fun cancel(@PathVariable id: String): ResponseEntity<Void> {
        orderService.cancelOrder(id)
        return ResponseEntity.noContent().build()
    }

    // 조건부 응답
    @GetMapping("/{id}/status")
    fun getStatus(@PathVariable id: String): ResponseEntity<OrderStatus> {
        return try {
            val status = orderService.getStatus(id)
            ResponseEntity.ok(status)
        } catch (e: OrderNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }
}
```

### 증권 API 상태코드 가이드

| 상황 | HTTP 상태코드 |
|------|---------------|
| 주문 조회 성공 | `200 OK` |
| 신규 주문 성공 | `201 Created` |
| 주문 취소 성공 | `204 No Content` |
| 잘못된 주문 파라미터 | `400 Bad Request` |
| 인증 없는 요청 | `401 Unauthorized` |
| 권한 없는 계좌 접근 | `403 Forbidden` |
| 존재하지 않는 주문 | `404 Not Found` |
| 중복 주문 (멱등키 충돌) | `409 Conflict` |
| 미체결 잔량 없음 | `422 Unprocessable Entity` |
| 거래소 API 오류 | `502 Bad Gateway` |
| 서비스 점검 중 | `503 Service Unavailable` |

---

## 26.5 DTO와 검증(Bean Validation)

### DTO 설계 원칙

도메인 엔티티(Entity)를 API에 직접 노출하지 말고 DTO(Data Transfer Object)를 사용합니다.

```kotlin
// ❌ 엔티티 직접 노출 — 절대 금지
@GetMapping("/{id}")
fun getOrder(@PathVariable id: String): Order  // DB 컬럼, 내부 구현 노출

// ✅ DTO 사용
@GetMapping("/{id}")
fun getOrder(@PathVariable id: String): OrderResponse
```

### 주문 요청 DTO + Bean Validation

```kotlin
data class PlaceOrderRequest(

    @field:NotBlank(message = "계좌번호는 필수입니다")
    @field:Pattern(regexp = "^[0-9]{10}$", message = "계좌번호는 10자리 숫자여야 합니다")
    val accountId: String,

    @field:NotBlank(message = "종목코드는 필수입니다")
    @field:Pattern(regexp = "^[A-Z0-9]{6}$", message = "종목코드는 6자리 영문/숫자여야 합니다")
    val ticker: String,

    @field:NotNull(message = "주문 방향은 필수입니다")
    val side: OrderSide,

    @field:NotNull(message = "주문 유형은 필수입니다")
    val orderType: OrderType,

    @field:Min(value = 1, message = "수량은 1 이상이어야 합니다")
    @field:Max(value = 100000, message = "단일 주문 최대 수량은 100,000주입니다")
    val quantity: Int,

    // 지정가 주문 시 가격 필수 — 커스텀 검증으로 처리
    @field:DecimalMin(value = "0.01", message = "주문 가격은 0.01원 이상이어야 합니다")
    @field:Digits(integer = 10, fraction = 2, message = "가격 형식이 올바르지 않습니다")
    val price: BigDecimal?
)
```

> **코틀린 주의사항**: `@field:` 접두사가 필수입니다. 코틀린의 프로퍼티는 필드(field), 게터(getter), 세터(setter) 등 여러 요소로 컴파일되는데, `@field:`가 없으면 어노테이션이 게터에 붙어 Bean Validation이 동작하지 않습니다.

### 커스텀 검증 — 호가 단위(Tick Size) 검증

한국 주식시장에서는 주가에 따라 호가 단위가 다릅니다. 이런 도메인 규칙은 커스텀 `ConstraintValidator`로 구현합니다.

```kotlin
// 1. 커스텀 어노테이션 정의
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidTickSizeValidator::class])
annotation class ValidTickSize(
    val message: String = "주문 가격이 호가 단위에 맞지 않습니다",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

// 2. Validator 구현
class ValidTickSizeValidator : ConstraintValidator<ValidTickSize, PlaceOrderRequest> {

    override fun isValid(request: PlaceOrderRequest, context: ConstraintValidatorContext): Boolean {
        // 시장가 주문은 가격 검증 불필요
        if (request.orderType == OrderType.MARKET) return true
        val price = request.price ?: return false

        val tickSize = getTickSize(price)
        val remainder = price.remainder(tickSize)

        if (remainder.compareTo(BigDecimal.ZERO) != 0) {
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate(
                "호가 단위 오류: 현재 가격(${price}원)의 호가 단위는 ${tickSize}원입니다"
            ).addPropertyNode("price").addConstraintViolation()
            return false
        }
        return true
    }

    /**
     * 코스피/코스닥 호가 단위 (KRX 규정)
     * https://www.krx.co.kr/main/listing/MDCUP00000000.jsp
     */
    private fun getTickSize(price: BigDecimal): BigDecimal = when {
        price < BigDecimal("2000")    -> BigDecimal("1")
        price < BigDecimal("5000")    -> BigDecimal("5")
        price < BigDecimal("20000")   -> BigDecimal("10")
        price < BigDecimal("50000")   -> BigDecimal("50")
        price < BigDecimal("200000")  -> BigDecimal("100")
        price < BigDecimal("500000")  -> BigDecimal("500")
        else                          -> BigDecimal("1000")
    }
}

// 3. DTO에 적용
@ValidTickSize  // 클래스 레벨 어노테이션
data class PlaceOrderRequest(
    val ticker: String,
    val orderType: OrderType,
    val quantity: Int,
    val price: BigDecimal?
)
```

### 크로스 필드 검증 (지정가 주문 시 가격 필수)

```kotlin
// 클래스 레벨 커스텀 어노테이션으로 구현
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [LimitOrderPriceValidator::class])
annotation class ValidLimitOrderPrice(
    val message: String = "지정가 주문은 가격이 필수입니다",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class LimitOrderPriceValidator : ConstraintValidator<ValidLimitOrderPrice, PlaceOrderRequest> {
    override fun isValid(req: PlaceOrderRequest, ctx: ConstraintValidatorContext): Boolean {
        if (req.orderType == OrderType.LIMIT && req.price == null) {
            ctx.disableDefaultConstraintViolation()
            ctx.buildConstraintViolationWithTemplate("지정가 주문에는 가격이 필요합니다")
                .addPropertyNode("price").addConstraintViolation()
            return false
        }
        return true
    }
}
```

---

## 26.6 전역 예외처리(@RestControllerAdvice)

### 표준 에러 응답 설계

```kotlin
// RFC 7807 Problem Details 스펙 기반 에러 응답
data class ErrorResponse(
    val code: String,           // 내부 에러 코드 (예: ORDER_NOT_FOUND)
    val message: String,        // 사용자 메시지
    val details: List<String> = emptyList(),  // 상세 검증 오류
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val traceId: String? = null  // 분산 추적 ID
)
```

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    // 비즈니스 예외 처리
    @ExceptionHandler(OrderNotFoundException::class)
    fun handleOrderNotFound(ex: OrderNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                code = "ORDER_NOT_FOUND",
                message = ex.message ?: "주문을 찾을 수 없습니다"
            ))
    }

    @ExceptionHandler(InsufficientBalanceException::class)
    fun handleInsufficientBalance(ex: InsufficientBalanceException): ResponseEntity<ErrorResponse> {
        log.warn("잔고 부족: ${ex.message}")
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(
                code = "INSUFFICIENT_BALANCE",
                message = "잔고가 부족합니다. 필요 금액: ${ex.requiredAmount}, 보유 금액: ${ex.availableAmount}"
            ))
    }

    @ExceptionHandler(RiskLimitExceededException::class)
    fun handleRiskLimit(ex: RiskLimitExceededException): ResponseEntity<ErrorResponse> {
        log.warn("리스크 한도 초과: ${ex.message}")
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(
                code = "RISK_LIMIT_EXCEEDED",
                message = ex.message ?: "리스크 한도를 초과했습니다"
            ))
    }

    // Bean Validation 오류 처리
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.map { error ->
            "${error.field}: ${error.defaultMessage}"
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                code = "VALIDATION_ERROR",
                message = "요청 파라미터가 올바르지 않습니다",
                details = details
            ))
    }

    // JSON 파싱 오류
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleJsonParsing(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                code = "INVALID_JSON",
                message = "요청 본문 JSON 형식이 올바르지 않습니다"
            ))
    }

    // 나머지 모든 예외 (500)
    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("처리되지 않은 예외: ${request.method} ${request.requestURI}", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                code = "INTERNAL_ERROR",
                message = "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                // 내부 오류 상세는 절대 클라이언트에 노출 금지!
            ))
    }
}
```

> **보안 중요**: `@ExceptionHandler(Exception::class)` 핸들러에서 `ex.message`나 스택 트레이스를 응답에 포함하지 마세요. 내부 구현(DB 쿼리, 패키지 경로 등)이 외부에 노출될 수 있습니다. 상세 정보는 로그에만 남기세요.

---

## 26.7 인터셉터와 필터

### 필터(Filter) vs 인터셉터(Interceptor)

```
HTTP 요청 →  [필터1] → [필터2] → DispatcherServlet → [인터셉터] → Controller
HTTP 응답 ←  [필터1] ← [필터2] ←         ↑          ← [인터셉터] ←   ↑
```

| 구분 | 필터(Filter) | 인터셉터(Interceptor) |
|------|-------------|----------------------|
| 레벨 | 서블릿 레벨 | 스프링 MVC 레벨 |
| 스프링 빈 접근 | 제한적 | 가능 |
| 용도 | 인코딩, CORS, 인증 토큰 추출 | 로깅, 권한 검사, 공통 처리 |
| 예외처리 | `@RestControllerAdvice` 미적용 | 적용됨 |

### 요청 로깅 인터셉터

```kotlin
@Component
class RequestLoggingInterceptor : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val requestId = request.getHeader("X-Request-Id") ?: UUID.randomUUID().toString()
        request.setAttribute("requestId", requestId)
        request.setAttribute("startTime", System.currentTimeMillis())

        log.info("[요청] {} {} | requestId={} | IP={}",
            request.method, request.requestURI, requestId, request.remoteAddr)

        return true  // false 반환 시 컨트롤러 미호출
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        val elapsed = System.currentTimeMillis() - (request.getAttribute("startTime") as Long)
        val requestId = request.getAttribute("requestId")

        log.info("[응답] {} {} | status={} | {}ms | requestId={}",
            request.method, request.requestURI, response.status, elapsed, requestId)
    }
}

// 인터셉터 등록
@Configuration
class WebConfig(
    private val requestLoggingInterceptor: RequestLoggingInterceptor
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(requestLoggingInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/health", "/api/actuator/**")
    }
}
```

---

## 26.8 직렬화(Serialization) — Jackson과 코틀린

### 코틀린 모듈 설정

```kotlin
@Configuration
class JacksonConfig {

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper = ObjectMapper().apply {
        // 코틀린 지원 (data class, nullable, default parameter 등)
        registerModule(KotlinModule.Builder()
            .withReflectionCacheSize(512)
            .configure(KotlinFeature.NullToEmptyCollection, false)
            .configure(KotlinFeature.NullToEmptyMap, false)
            .configure(KotlinFeature.NullIsSameAsDefault, false)
            .configure(KotlinFeature.SingletonSupport, false)
            .configure(KotlinFeature.StrictNullChecks, false)
            .build())

        // Java 8 날짜/시간 지원
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)  // ISO 8601 형식 사용

        // BigDecimal: 지수 표기법 방지 (1.5E+2 → 150)
        enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)

        // 모르는 필드 무시 (하위 호환성)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        // null 필드 미포함 (선택적)
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }
}
```

### BigDecimal 직렬화 함정

```kotlin
// ❌ 문제: 기본 설정에서 BigDecimal 직렬화
val price = BigDecimal("1500000")  // 삼성전자 가정

// JSON 결과 (문제):
// "price": 1.5E+6  ← 지수 표기법! 프론트엔드에서 파싱 오류 가능

// ✅ 해결: WRITE_BIGDECIMAL_AS_PLAIN + @JsonSerialize 조합
// JSON 결과:
// "price": 1500000
```

```kotlin
// DTO에서 BigDecimal 직렬화 커스터마이징
data class OrderResponse(
    val orderId: String,

    @JsonSerialize(using = BigDecimalSerializer::class)
    val price: BigDecimal,           // "price": "150000.00"

    @JsonSerialize(using = BigDecimalSerializer::class)
    val totalAmount: BigDecimal,     // "totalAmount": "15000000.00"

    val orderedAt: LocalDateTime     // "orderedAt": "2024-01-15T09:30:00"
)

// 커스텀 직렬화기 (문자열로 직렬화하여 정밀도 보장)
class BigDecimalSerializer : JsonSerializer<BigDecimal>() {
    override fun serialize(value: BigDecimal, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toPlainString())
    }
}
```

> **증권 도메인 필수**: 주가, 잔고, 수수료 등 금액 데이터는 JSON 직렬화 시 반드시 BigDecimal 설정을 확인하세요. `1.5E+6` 같은 지수 표기법이 클라이언트나 외부 시스템에서 파싱 오류를 일으키거나, 부동소수점 오차가 발생할 수 있습니다.

### Enum 직렬화

```kotlin
enum class OrderSide {
    BUY, SELL
}

enum class OrderStatus(val displayName: String) {
    @JsonProperty("PENDING")   PENDING("대기"),
    @JsonProperty("FILLED")    FILLED("체결"),
    @JsonProperty("CANCELLED") CANCELLED("취소"),
    @JsonProperty("REJECTED")  REJECTED("거부")
}

// Enum 역직렬화 시 대소문자 무시 설정
objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
// "buy", "BUY", "Buy" 모두 OrderSide.BUY로 매핑
```

---

## 26.9 MVC(블로킹) vs WebFlux(논블로킹)

### 전통적 블로킹 I/O (Spring MVC)

```
스레드 1: [요청 수신] → [DB 쿼리 대기 ⏳] → [응답 전송]
스레드 2: [요청 수신] → [외부 API 대기 ⏳] → [응답 전송]
스레드 3: 대기 중...
스레드 N: 대기 중...
```

- 요청 1건 = 스레드 1개 점유
- 기본 스레드 풀: 약 200개 (Tomcat 기본값)
- 동시 요청 200개 초과 시 요청 대기 큐에 쌓임

### 논블로킹 I/O (Spring WebFlux)

```
이벤트 루프 스레드 (CPU 코어 수개):
  → [요청A 수신] → DB 쿼리 등록 후 즉시 반환
  → [요청B 수신] → 외부 API 등록 후 즉시 반환
  → [DB 결과 도착] → 응답A 처리
  → [API 결과 도착] → 응답B 처리
```

- 소수의 스레드로 수천 개의 동시 연결 처리 가능
- Reactor 라이브러리 (`Mono<T>`, `Flux<T>`)

### WebFlux 코드 예시

```kotlin
@RestController
@RequestMapping("/api/v1/market-data")
class MarketDataController(
    private val marketDataService: MarketDataService
) {
    // Mono: 0 또는 1개 결과
    @GetMapping("/{ticker}/price")
    fun getPrice(@PathVariable ticker: String): Mono<PriceResponse> {
        return marketDataService.getLatestPrice(ticker)
            .map { PriceResponse(ticker, it) }
    }

    // Flux: 0~N개 결과 (SSE 스트리밍)
    @GetMapping("/{ticker}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamPrice(@PathVariable ticker: String): Flux<PriceResponse> {
        return marketDataService.priceStream(ticker)
            .map { PriceResponse(ticker, it) }
    }
}

@Service
class MarketDataService(
    private val webClient: WebClient  // 논블로킹 HTTP 클라이언트
) {
    fun getLatestPrice(ticker: String): Mono<BigDecimal> {
        return webClient.get()
            .uri("/market-data/{ticker}", ticker)
            .retrieve()
            .bodyToMono(BigDecimal::class.java)
    }
}
```

### MVC vs WebFlux 선택 기준

| 기준 | Spring MVC 선택 | Spring WebFlux 선택 |
|------|----------------|---------------------|
| **팀 역량** | JPA/JDBC 익숙 | 리액티브 프로그래밍 경험 |
| **데이터 접근** | JPA (블로킹) 위주 | R2DBC, MongoDB 리액티브 드라이버 |
| **트래픽 패턴** | 수백~수천 TPS | 수만 TPS 이상, 대용량 스트리밍 |
| **지연시간** | 외부 IO 적은 경우 | 외부 IO 많은 경우 (체인 API 호출) |
| **디버깅 난이도** | 낮음 (스택 트레이스 명확) | 높음 (콜백 체인, 스케줄러 전환) |
| **라이브러리 호환** | 대부분 호환 | 리액티브 지원 라이브러리 필요 |

> **증권사 현장 조언**: 대부분의 국내 증권사 백엔드는 **Spring MVC + JPA/MyBatis** 조합입니다. WebFlux는 실시간 시세 스트리밍, 대용량 체결 데이터 처리 등 특정 영역에만 도입하는 경우가 많습니다. 입문 단계에서는 MVC에 집중하세요. [13. 코루틴](13-coroutines.md)에서 다루는 코루틴은 WebFlux 없이도 비동기 처리가 가능한 코틀린 대안입니다.

---

## 26.10 증권 주문 API 설계 모범사례

### API 버전 전략

```kotlin
// URL 버전 관리 (가장 명확)
@RestController
@RequestMapping("/api/v1/orders")  // v1
class OrderControllerV1

@RestController
@RequestMapping("/api/v2/orders")  // v2 (Breaking Change 시)
class OrderControllerV2
```

### 응답 래퍼(Response Wrapper) 설계

```kotlin
// 공통 응답 래퍼
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorResponse? = null,
    val meta: Meta? = null
) {
    companion object {
        fun <T> ok(data: T, meta: Meta? = null) =
            ApiResponse(success = true, data = data, meta = meta)

        fun error(error: ErrorResponse) =
            ApiResponse<Nothing>(success = false, error = error)
    }
}

data class Meta(
    val page: Int? = null,
    val size: Int? = null,
    val totalElements: Long? = null,
    val totalPages: Int? = null
)

// 사용 예
@GetMapping
fun getOrders(pageable: Pageable): ApiResponse<List<OrderResponse>> {
    val page = orderService.findAll(pageable)
    return ApiResponse.ok(
        data = page.content.map(orderMapper::toResponse),
        meta = Meta(
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages
        )
    )
}
```

### 완성된 주문 API 전체 구조

```kotlin
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "주문 API", description = "증권 주문 관련 API")
class OrderController(
    private val orderService: OrderService,
    private val orderMapper: OrderMapper
) {
    /**
     * 신규 주문 접수
     * - 멱등성 보장: X-Request-Id 헤더로 중복 주문 방지
     * - 응답: 201 Created + Location 헤더
     */
    @PostMapping
    @Operation(summary = "신규 주문 접수")
    fun placeOrder(
        @RequestHeader("X-Request-Id") requestId: String,
        @Valid @RequestBody request: PlaceOrderRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val userId = authentication.name
        val order = orderService.placeOrder(
            request = orderMapper.toOrder(request),
            userId = userId,
            requestId = requestId
        )
        return ResponseEntity
            .created(URI.create("/api/v1/orders/${order.orderId}"))
            .body(ApiResponse.ok(orderMapper.toResponse(order)))
    }

    /**
     * 주문 목록 조회
     * - 페이징: page/size 파라미터
     * - 필터링: status, ticker, 날짜 범위
     */
    @GetMapping
    @Operation(summary = "주문 목록 조회")
    fun getOrders(
        @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false) ticker: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") @Max(100) size: Int,
        authentication: Authentication
    ): ApiResponse<List<OrderResponse>> {
        val orders = orderService.findOrders(
            userId = authentication.name,
            status = status,
            ticker = ticker,
            pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        )
        return ApiResponse.ok(
            data = orders.content.map(orderMapper::toResponse),
            meta = Meta(page = orders.number, size = orders.size,
                totalElements = orders.totalElements, totalPages = orders.totalPages)
        )
    }

    /**
     * 주문 취소
     * - 미체결 주문만 취소 가능
     * - 부분 체결된 주문은 미체결 잔량만 취소
     */
    @DeleteMapping("/{orderId}")
    @Operation(summary = "주문 취소")
    fun cancelOrder(
        @PathVariable orderId: String,
        authentication: Authentication
    ): ResponseEntity<Void> {
        orderService.cancelOrder(orderId = orderId, userId = authentication.name)
        return ResponseEntity.noContent().build()
    }
}
```

---

## 26.11 실전 체크리스트

### API 설계 체크리스트

- [ ] DTO에 `@field:` 접두사 붙인 Bean Validation 어노테이션 사용하는가?
- [ ] `@RestControllerAdvice`로 전역 예외처리 구성했는가?
- [ ] 500 오류 응답에 내부 구현 상세(스택 트레이스, DB 쿼리 등)가 노출되지 않는가?
- [ ] 금액 필드(price, amount)에 BigDecimal 직렬화 설정이 적용됐는가?
- [ ] 주문 API에 멱등성(X-Request-Id) 처리가 있는가?
- [ ] API 응답 페이지네이션 메타 정보(총 건수, 페이지 수)를 포함하는가?
- [ ] KotlinModule이 ObjectMapper에 등록됐는가?
- [ ] JavaTimeModule이 등록되고 타임스탬프 직렬화가 비활성화됐는가?

### 호가 단위 관련 체크리스트

- [ ] 지정가 주문 시 호가 단위 검증 로직이 있는가?
- [ ] 호가 단위 테이블은 KRX 최신 규정 기준인가?
- [ ] 해외 주식(HTS/MTS 해외거래) 처리 시 별도 호가 단위 로직이 있는가?

---

## 정리

- **자동설정**: 클래스패스와 `@Conditional`로 필요한 빈만 자동 등록, 오버라이드 가능
- **라우팅**: `@RestController` + HTTP 메서드 어노테이션으로 선언적 매핑
- **검증**: `@field:` 접두사 Bean Validation + 호가 단위 같은 도메인 규칙은 커스텀 Validator
- **예외처리**: `@RestControllerAdvice`로 중앙화, 500 오류 시 내부 정보 노출 금지
- **BigDecimal**: 지수 표기법 방지 설정 필수 (`WRITE_BIGDECIMAL_AS_PLAIN`)
- **MVC vs WebFlux**: 대부분 MVC + JPA로 충분, 고처리량 스트리밍에만 WebFlux 검토

---

이전: [25. 스프링 코어: DI·IoC·AOP](25-spring-core.md) · 다음: [27. 데이터 접근: JPA·MyBatis·트랜잭션](27-data-access.md) · [전체 커리큘럼](../CURRICULUM.md)
