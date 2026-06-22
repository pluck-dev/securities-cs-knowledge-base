# 25. 스프링 코어: DI·IoC·AOP

> **학습 목표**: 스프링이 "왜" 객체를 대신 관리하는지 이해하고, DI(Dependency Injection)·AOP(Aspect-Oriented Programming)를 증권 도메인에 적용할 수 있다.

---

## 25.1 제어의 역전(IoC, Inversion of Control)이란?

### 전통적인 방식의 문제

개발 경험이 없는 분들을 위해 먼저 "제어(Control)"가 무엇인지 짚어보겠습니다.

```kotlin
// ❌ 전통적 방식: 객체가 자신의 의존성을 직접 생성
class OrderService {
    // OrderService가 직접 구현체를 선택하고 생성
    private val orderRepository = JdbcOrderRepository()  // 직접 new
    private val riskChecker = SimpleRiskChecker()        // 직접 new
    private val auditLogger = FileAuditLogger()          // 직접 new

    fun placeOrder(order: Order) {
        riskChecker.check(order)
        orderRepository.save(order)
        auditLogger.log(order)
    }
}
```

이 방식의 문제:
- `OrderService`가 구체적인 구현체(`JdbcOrderRepository`)에 **강하게 결합(tight coupling)**
- 테스트 시 실제 DB 없이는 `OrderService` 단독 테스트 불가
- 나중에 `MongoOrderRepository`로 바꾸려면 `OrderService` 코드를 수정해야 함
- 스레드 안전성, 객체 수명 관리 등을 개발자가 직접 책임져야 함

### IoC: 제어권을 프레임워크에 넘기다

**IoC(Inversion of Control)** 는 "객체의 생성과 의존성 연결을 개발자가 아닌 프레임워크(스프링 컨테이너)가 담당한다"는 설계 원칙입니다.

```
전통 방식: OrderService → new JdbcOrderRepository()  (내가 만든다)
IoC 방식:  스프링 컨테이너 → OrderService에 Repository 주입  (받는다)
```

**왜 좋은가?**

| 관심사 | 전통 방식 | IoC 방식 |
|--------|-----------|----------|
| 구현체 선택 | 개발자가 코드에 박음 | 설정(config)으로 분리 |
| 객체 수명 | 개발자가 관리 | 스프링이 관리 |
| 테스트 | 실제 구현체 필요 | Mock 주입 가능 |
| 변경 | 코드 수정 필요 | 설정만 바꿈 |

> **증권 도메인 관점**: 한국투자증권, 미래에셋증권 등 대형 증권사는 HTS, MTS, API Gateway, 체결 서비스가 모두 `OrderService`를 사용합니다. IoC 없이는 각 환경마다 다른 구현체를 코드에 박아야 하고, 운영·모의투자·테스트 환경 분리가 매우 힘들어집니다.

---

## 25.2 의존성 주입(DI, Dependency Injection)

IoC를 구현하는 가장 대표적인 패턴이 **DI(Dependency Injection)** 입니다. 스프링은 세 가지 방식을 지원합니다.

### 25.2.1 생성자 주입 (Constructor Injection) ✅ 권장

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,  // 생성자로 주입
    private val riskChecker: RiskChecker,
    private val auditLogger: AuditLogger
) {
    fun placeOrder(order: Order): OrderResult {
        riskChecker.check(order)
        val saved = orderRepository.save(order)
        auditLogger.log(saved)
        return OrderResult.success(saved.orderId)
    }
}
```

**왜 생성자 주입을 권장하는가?**

1. **불변성(Immutability)**: `val`로 선언하면 주입 후 변경 불가 → 스레드 안전
2. **필수 의존성 명시**: 생성자 파라미터가 없으면 컴파일 오류 → null 주입 방지
3. **테스트 용이성**: `new OrderService(mockRepo, mockRisk, mockLogger)` 처럼 테스트에서 직접 생성 가능
4. **순환 의존성 조기 발견**: A→B→A 순환이 있으면 앱 기동 시 즉시 오류

### 25.2.2 필드 주입 (Field Injection) ⚠️ 지양

```kotlin
@Service
class OrderService {
    @Autowired
    private lateinit var orderRepository: OrderRepository  // 필드 주입

    @Autowired
    private lateinit var riskChecker: RiskChecker
}
```

**문제점**:
- `lateinit var`를 써야 함 → 불변성 없음
- 테스트 시 리플렉션으로만 주입 가능
- 스프링 컨테이너 없이는 객체 생성 자체가 불완전
- 코드만 봐서는 어떤 의존성이 필수인지 알 수 없음

> **함정**: 빠르게 코드를 작성할 때 `@Autowired` 필드 주입을 쓰다가, 단위 테스트를 작성하려니 스프링 컨텍스트를 전부 띄워야 하는 상황이 됩니다. 처음부터 생성자 주입 습관을 들이세요.

### 25.2.3 세터 주입 (Setter Injection) — 선택적 의존성에만

```kotlin
@Service
class OrderService {
    private var optionalEnricher: OrderEnricher? = null

    @Autowired(required = false)
    fun setOrderEnricher(enricher: OrderEnricher) {
        this.optionalEnricher = enricher
    }
}
```

선택적으로 있어도 되고 없어도 되는 의존성에만 사용합니다. 대부분의 경우 생성자 주입으로 해결할 수 있습니다.

### 주입 방식 비교표

| 방식 | 불변성 | 테스트 편의 | 순환 감지 | 사용 권장도 |
|------|--------|-------------|-----------|-------------|
| 생성자 | ✅ val 가능 | ✅ 직접 생성 | ✅ 즉시 오류 | ⭐⭐⭐ 최우선 |
| 세터 | ❌ var 필요 | ⚠️ setter 호출 필요 | ❌ 런타임 감지 | ⭐ 선택적 의존성만 |
| 필드 | ❌ var 필요 | ❌ 리플렉션 필요 | ❌ 런타임 감지 | ❌ 지양 |

---

## 25.3 빈(Bean)과 ApplicationContext

### 빈이란?

**빈(Bean)** 은 스프링 IoC 컨테이너가 생성하고 관리하는 객체입니다.

```kotlin
// 스프링이 관리하는 빈 ← @Service 어노테이션이 "나를 빈으로 등록해줘" 라는 신호
@Service
class TradeExecutionService(
    private val orderBook: OrderBook,
    private val tradeRepository: TradeRepository
) {
    // 스프링이 이 객체를 하나 만들어서 필요한 곳에 주입해준다
}
```

### ApplicationContext

**ApplicationContext** 는 빈을 담는 컨테이너입니다. 스프링 부트에서는 앱 시작 시 자동으로 생성됩니다.

```kotlin
// 스프링 부트 진입점
@SpringBootApplication
fun main(args: Array<String>) {
    val context: ApplicationContext = runApplication<BrokerageApp>(*args)

    // 컨테이너에서 빈을 꺼낼 수 있음 (실제로는 주입받아 사용)
    val orderService = context.getBean(OrderService::class.java)
}
```

> **실무 팁**: `ApplicationContext.getBean()`을 직접 호출하는 코드는 **서비스 로케이터 패턴(Service Locator Pattern)** 으로, DI의 장점을 포기하는 것입니다. 특수한 경우(동적 빈 선택 등)가 아니면 주입받아 사용하세요.

---

## 25.4 빈 스코프(Bean Scope)

스프링 빈은 기본적으로 **싱글톤(Singleton)** 입니다. 하지만 목적에 따라 스코프를 바꿀 수 있습니다.

### 25.4.1 Singleton (기본값)

```kotlin
@Service  // scope = singleton이 기본
class MarketDataService(
    private val marketDataRepository: MarketDataRepository
) {
    // 애플리케이션 전체에서 딱 하나의 인스턴스
    // 모든 요청이 이 객체를 공유
    fun getPrice(ticker: String): BigDecimal {
        return marketDataRepository.findLatestPrice(ticker)
    }
}
```

> **⚠️ 싱글톤 함정**: 싱글톤 빈에 **인스턴스 변수(상태)** 를 두면 멀티스레드 환경에서 레이스 컨디션(Race Condition)이 발생합니다.

```kotlin
@Service
class BadOrderService {
    // ❌ 위험! 여러 요청이 동시에 이 변수를 수정할 수 있음
    private var currentOrder: Order? = null

    fun process(order: Order) {
        currentOrder = order  // 스레드 A가 여기서 설정
        // 스레드 B가 끼어들면?
        validate(currentOrder!!)  // 스레드 B의 order가 검증됨!
    }
}
```

**올바른 패턴**: 상태는 메서드 파라미터나 로컬 변수로 다루세요.

### 25.4.2 Prototype

```kotlin
@Component
@Scope("prototype")
class OrderContext {
    // 요청마다 새 인스턴스 생성
    var orderId: String? = null
    var sessionId: String? = null
}
```

주입할 때마다 새 객체가 생성됩니다. 싱글톤 빈에 프로토타입 빈을 주입하면 주입 시점(앱 기동 시)의 인스턴스만 계속 쓰게 되는 **함정**이 있습니다. 프로토타입이 필요하면 `ObjectProvider<T>`를 사용하세요.

```kotlin
@Service
class OrderProcessor(
    private val orderContextProvider: ObjectProvider<OrderContext>
) {
    fun process(request: OrderRequest) {
        val ctx = orderContextProvider.getObject()  // 매번 새 인스턴스
        ctx.orderId = request.orderId
    }
}
```

### 25.4.3 Request / Session (웹 스코프)

```kotlin
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
class RequestContext {
    var userId: String? = null
    var requestId: String = UUID.randomUUID().toString()
}
```

| 스코프 | 생존 범위 | 사용 예 |
|--------|-----------|---------|
| `singleton` | 앱 전체 | 서비스, 리포지토리 |
| `prototype` | 요청마다 새 객체 | 상태 있는 헬퍼 |
| `request` | HTTP 요청 1건 | 요청 컨텍스트, 감사 추적 |
| `session` | HTTP 세션 | 사용자 세션 데이터 |

---

## 25.5 빈 생명주기(Bean Lifecycle)

```
컨테이너 시작
    ↓
빈 인스턴스 생성
    ↓
의존성 주입 (생성자/세터/필드)
    ↓
@PostConstruct 호출  ← 초기화 로직
    ↓
빈 사용 (서비스 운영)
    ↓
@PreDestroy 호출     ← 정리 로직
    ↓
컨테이너 종료
```

```kotlin
@Service
class MarketDataCacheService(
    private val marketDataRepository: MarketDataRepository
) {
    private val priceCache = ConcurrentHashMap<String, BigDecimal>()

    @PostConstruct
    fun init() {
        // 빈 생성 후 바로 실행: 캐시 워밍업
        log.info("마켓 데이터 캐시 초기화 시작")
        val popularStocks = listOf("005930", "000660", "035420")  // 삼성전자, SK하이닉스, NAVER
        popularStocks.forEach { ticker ->
            priceCache[ticker] = marketDataRepository.findLatestPrice(ticker)
        }
        log.info("마켓 데이터 캐시 초기화 완료: ${priceCache.size}개")
    }

    @PreDestroy
    fun cleanup() {
        // 컨테이너 종료 전 실행: 리소스 해제
        log.info("마켓 데이터 캐시 정리")
        priceCache.clear()
    }

    fun getPrice(ticker: String): BigDecimal {
        return priceCache[ticker]
            ?: marketDataRepository.findLatestPrice(ticker).also { priceCache[ticker] = it }
    }
}
```

> **실무 팁**: `@PostConstruct`는 애플리케이션이 트래픽을 받기 전에 초기화해야 하는 데이터(종목 기본 정보, 호가 단위 테이블 등)를 로드하기에 좋습니다. 다만 무거운 작업은 비동기로 처리하세요.

---

## 25.6 빈 등록 어노테이션

### 스테레오타입 어노테이션

```kotlin
// @Component: 범용 빈 등록
@Component
class TickerValidator {
    fun isValid(ticker: String): Boolean = ticker.matches(Regex("[A-Z0-9]{3,6}"))
}

// @Service: 비즈니스 로직 계층 (기능상 @Component와 동일, 의미 명확화)
@Service
class OrderService(private val orderRepository: OrderRepository) {
    fun placeOrder(order: Order): Order = orderRepository.save(order)
}

// @Repository: 데이터 접근 계층 (DataAccessException 변환 추가)
@Repository
class JpaOrderRepository(private val em: EntityManager) : OrderRepository {
    override fun save(order: Order): Order = em.merge(order)
}

// @Controller / @RestController: 웹 계층
@RestController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {
    @PostMapping
    fun placeOrder(@RequestBody request: OrderRequest): ResponseEntity<OrderResponse> {
        // ...
    }
}
```

### @Configuration과 @Bean

외부 라이브러리 클래스처럼 어노테이션을 붙일 수 없는 경우 `@Configuration` + `@Bean`을 사용합니다.

```kotlin
@Configuration
class InfrastructureConfig {

    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            // 금액 직렬화: BigDecimal을 문자열로 (부동소수점 오차 방지)
            enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
        }
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    @ConditionalOnProperty("broker.fee.calculator", havingValue = "tiered")
    fun feeCalculator(): FeeCalculator = TieredFeeCalculator()
}
```

> **@Configuration의 특수성**: `@Configuration` 클래스는 CGLIB으로 프록시됩니다. 클래스 내에서 `@Bean` 메서드를 직접 호출해도 같은 빈 인스턴스가 반환되는 이유입니다.

---

## 25.7 컴포넌트 스캔(Component Scan)

`@SpringBootApplication`은 해당 패키지와 하위 패키지를 자동으로 스캔합니다.

```kotlin
@SpringBootApplication  // = @Configuration + @EnableAutoConfiguration + @ComponentScan
class BrokerageApplication

// 패키지 구조 예시
// com.example.brokerage
//   ├── BrokerageApplication.kt   ← 스캔 시작점
//   ├── order/
//   │   ├── OrderService.kt       ← 자동 감지
//   │   └── OrderRepository.kt    ← 자동 감지
//   └── account/
//       └── AccountService.kt     ← 자동 감지
```

> **함정**: `BrokerageApplication`이 `com.example.brokerage`에 있는데, 별도 모듈의 빈이 `com.external.library`에 있다면 스캔되지 않습니다. `@ComponentScan(basePackages = [...])` 또는 `@Import`로 해결합니다.

---

## 25.8 빈 충돌과 @Qualifier / @Primary

같은 타입의 빈이 여러 개 있으면 스프링은 어떤 것을 주입해야 할지 모릅니다.

```kotlin
interface FeeCalculator {
    fun calculate(amount: BigDecimal, assetClass: AssetClass): BigDecimal
}

@Component
class StockFeeCalculator : FeeCalculator {
    override fun calculate(amount: BigDecimal, assetClass: AssetClass): BigDecimal {
        return amount.multiply(BigDecimal("0.00015"))  // 주식: 0.015%
    }
}

@Component
class EtfFeeCalculator : FeeCalculator {
    override fun calculate(amount: BigDecimal, assetClass: AssetClass): BigDecimal {
        return amount.multiply(BigDecimal("0.0001"))  // ETF: 0.01%
    }
}

// ❌ 오류: FeeCalculator 타입 빈이 2개라 어떤 걸 주입할지 모름
@Service
class OrderService(private val feeCalculator: FeeCalculator)
```

### 해결책 1: @Primary

```kotlin
@Component
@Primary  // 기본으로 사용할 빈 지정
class StockFeeCalculator : FeeCalculator { ... }
```

### 해결책 2: @Qualifier

```kotlin
@Service
class OrderService(
    @Qualifier("stockFeeCalculator")
    private val stockFeeCalculator: FeeCalculator,

    @Qualifier("etfFeeCalculator")
    private val etfFeeCalculator: FeeCalculator
) {
    fun calculateFee(order: Order): BigDecimal {
        val calculator = when (order.assetClass) {
            AssetClass.STOCK -> stockFeeCalculator
            AssetClass.ETF -> etfFeeCalculator
        }
        return calculator.calculate(order.amount, order.assetClass)
    }
}
```

### 해결책 3: 전략 패턴 + Map 주입

```kotlin
// 더 우아한 방법: 모든 FeeCalculator를 Map으로 주입
@Service
class OrderService(
    private val feeCalculators: Map<String, FeeCalculator>  // key = 빈 이름
) {
    fun calculateFee(order: Order): BigDecimal {
        val calculatorName = "${order.assetClass.name.lowercase()}FeeCalculator"
        val calculator = feeCalculators[calculatorName]
            ?: throw IllegalStateException("수수료 계산기 없음: $calculatorName")
        return calculator.calculate(order.amount, order.assetClass)
    }
}
```

---

## 25.9 AOP(관점지향 프로그래밍, Aspect-Oriented Programming)

### AOP가 해결하는 문제

모든 주문 처리 메서드에 로깅을 추가한다고 가정해보겠습니다.

```kotlin
// ❌ 횡단 관심사(Cross-Cutting Concern)가 비즈니스 로직과 뒤섞임
fun placeOrder(order: Order): Order {
    log.info("[AUDIT] 주문 시작: userId=${order.userId}, 시각=${LocalDateTime.now()}")
    val startTime = System.currentTimeMillis()

    try {
        riskChecker.check(order)
        val result = orderRepository.save(order)
        log.info("[AUDIT] 주문 완료: orderId=${result.orderId}, 소요=${System.currentTimeMillis()-startTime}ms")
        return result
    } catch (e: Exception) {
        log.error("[AUDIT] 주문 실패: ${e.message}")
        throw e
    }
}

fun cancelOrder(orderId: String): Order { /* 위와 동일한 로깅 코드... */ }
fun modifyOrder(order: Order): Order { /* 위와 동일한 로깅 코드... */ }
```

이런 **횡단 관심사(Cross-Cutting Concern)** 를 AOP로 분리합니다.

### AOP 핵심 용어

| 용어 | 의미 | 예시 |
|------|------|------|
| **Aspect** | 횡단 관심사 모듈 | `AuditAspect`, `PerformanceAspect` |
| **Advice** | 실제 실행 코드 | Before, After, Around 메서드 |
| **Pointcut** | Advice 적용 대상 | `execution(* com.example.order.*.*(..))` |
| **JoinPoint** | Advice가 끼어드는 지점 | 메서드 호출 시점 |
| **Weaving** | Aspect 적용 과정 | 컴파일 타임, 로드 타임, 런타임 |

### 주문 감사 추적(Audit Trail) Aspect

```kotlin
@Aspect
@Component
class OrderAuditAspect(
    private val auditRepository: AuditLogRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Pointcut: order 패키지의 Service 계층 모든 메서드
    @Pointcut("execution(* com.example.brokerage.order..*Service.*(..))")
    fun orderServiceMethods() {}

    // Around Advice: 메서드 실행 전후를 모두 감싼다
    @Around("orderServiceMethods()")
    fun auditOrderOperation(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = joinPoint.signature.name
        val args = joinPoint.args
        val startTime = System.currentTimeMillis()

        log.info("[감사추적] 시작: method=$methodName, args=${args.contentToString()}")

        return try {
            val result = joinPoint.proceed()  // 실제 메서드 실행
            val elapsed = System.currentTimeMillis() - startTime

            log.info("[감사추적] 완료: method=$methodName, 소요=${elapsed}ms")
            auditRepository.save(AuditLog(
                method = methodName,
                status = "SUCCESS",
                elapsedMs = elapsed
            ))

            result
        } catch (ex: Exception) {
            log.error("[감사추적] 실패: method=$methodName, error=${ex.message}")
            auditRepository.save(AuditLog(
                method = methodName,
                status = "FAILURE",
                errorMessage = ex.message
            ))
            throw ex  // 예외 재던지기 필수!
        }
    }
}
```

### 실행 시간 측정 Aspect (커스텀 어노테이션)

```kotlin
// 커스텀 어노테이션 정의
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MeasureTime(val description: String = "")

// Aspect
@Aspect
@Component
class PerformanceAspect {
    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(measureTime)")
    fun measureExecutionTime(
        joinPoint: ProceedingJoinPoint,
        measureTime: MeasureTime
    ): Any? {
        val start = System.currentTimeMillis()
        return try {
            joinPoint.proceed()
        } finally {
            val elapsed = System.currentTimeMillis() - start
            val description = measureTime.description.ifEmpty { joinPoint.signature.toShortString() }
            log.info("[성능] $description: ${elapsed}ms")

            // 임계값 초과 시 경고
            if (elapsed > 1000) {
                log.warn("[성능경고] $description 1초 초과: ${elapsed}ms")
            }
        }
    }
}

// 사용
@Service
class OrderMatchingService {
    @MeasureTime(description = "주문 매칭")
    fun matchOrders(buyOrder: Order, sellOrders: List<Order>): List<Trade> {
        // 주문 매칭 로직 (증권사의 핵심 연산)
        return sellOrders
            .filter { it.price <= buyOrder.price }
            .sortedBy { it.price }
            .take(10)
            .map { Trade(buyOrder, it) }
    }
}
```

### Before / After / AfterReturning / AfterThrowing

```kotlin
@Aspect
@Component
class OrderSecurityAspect {

    // Before: 메서드 실행 전
    @Before("execution(* com.example.brokerage.order.OrderService.placeOrder(..))")
    fun checkOrderPermission(joinPoint: JoinPoint) {
        val userId = SecurityContextHolder.getContext().authentication?.name
            ?: throw SecurityException("인증되지 않은 사용자")
        log.info("주문 권한 확인: userId=$userId")
    }

    // AfterReturning: 정상 반환 후
    @AfterReturning(
        pointcut = "execution(* com.example.brokerage.order.OrderService.placeOrder(..))",
        returning = "order"
    )
    fun afterOrderPlaced(order: Order) {
        log.info("주문 완료 이벤트 발행: orderId=${order.orderId}")
        // 알림 발송, 이벤트 발행 등
    }

    // AfterThrowing: 예외 발생 시
    @AfterThrowing(
        pointcut = "execution(* com.example.brokerage.order..*(..))",
        throwing = "ex"
    )
    fun handleOrderException(ex: Exception) {
        log.error("주문 처리 중 예외: ${ex.javaClass.simpleName} - ${ex.message}")
    }
}
```

---

## 25.10 프록시 동작 원리와 self-invocation 함정

### 스프링 AOP는 프록시 기반

```
클라이언트 코드
    ↓
[스프링 프록시 객체]   ← Aspect 로직 실행
    ↓
실제 OrderService 객체
```

스프링은 `@Transactional`, `@Async`, AOP Aspect를 적용할 때 실제 객체를 프록시로 감쌉니다.

### self-invocation 함정 (매우 중요!)

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository
) {
    // ❌ 함정: 같은 클래스 내부에서 호출 시 AOP 적용 안 됨!
    fun placeOrder(order: Order): Order {
        validateOrder(order)  // this.validateOrder() = 프록시를 거치지 않음!
        return orderRepository.save(order)
    }

    @Transactional  // ← 이 어노테이션이 적용되지 않는다!
    fun validateAndSave(order: Order): Order {
        validateOrder(order)
        return orderRepository.save(order)
    }

    @AuditLog  // ← 이것도 적용되지 않는다!
    fun validateOrder(order: Order) {
        if (order.quantity <= 0) throw IllegalArgumentException("수량 오류")
    }
}
```

**이유**: 내부 메서드 호출은 `this.method()` 형태로, 프록시 객체가 아닌 실제 객체의 메서드를 직접 호출합니다.

**해결책**:

```kotlin
// 해결책 1: 메서드를 다른 빈으로 분리 (가장 권장)
@Service
class OrderValidationService {
    @AuditLog
    fun validate(order: Order) {
        if (order.quantity <= 0) throw IllegalArgumentException("수량 오류")
    }
}

@Service
class OrderService(
    private val orderValidationService: OrderValidationService,
    private val orderRepository: OrderRepository
) {
    fun placeOrder(order: Order): Order {
        orderValidationService.validate(order)  // 프록시를 거침 ✅
        return orderRepository.save(order)
    }
}

// 해결책 2: ApplicationContext에서 자기 자신의 프록시를 가져오기 (비권장, 코드 냄새)
@Service
class OrderService(
    private val context: ApplicationContext,
    private val orderRepository: OrderRepository
) {
    private val self: OrderService by lazy { context.getBean(OrderService::class.java) }

    fun placeOrder(order: Order): Order {
        self.validateOrder(order)  // 프록시를 거침
        return orderRepository.save(order)
    }
}
```

---

## 25.11 코틀린에서 스프링 빈 작성 시 주의사항

### open 클래스 문제

코틀린 클래스는 기본이 `final`입니다. 스프링 AOP는 CGLIB으로 클래스를 상속하여 프록시를 생성하므로, `final` 클래스는 프록시 생성이 불가합니다.

```kotlin
// ❌ 문제: 코틀린 클래스는 기본이 final
class OrderService {  // = final class OrderService
    @Transactional
    fun placeOrder(order: Order): Order { ... }
    // Transactional이 동작하지 않을 수 있음!
}
```

### 해결책: all-open 플러그인

`build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"  // ← 이게 핵심!
    kotlin("plugin.jpa") version "1.9.22"     // JPA 엔티티용
}
```

`kotlin("plugin.spring")`은 `@Component`, `@Transactional`, `@Async` 등이 붙은 클래스를 자동으로 `open`으로 만들어줍니다.

```kotlin
// plugin.spring 적용 후: 어노테이션이 있으면 자동으로 open
@Service  // ← plugin.spring이 이 클래스를 open으로 만듦
class OrderService {
    @Transactional  // ← 이 메서드도 open으로 만듦
    fun placeOrder(order: Order): Order { ... }
}
```

### 코틀린 null 안전성과 스프링

```kotlin
@Service
class AccountService(
    private val accountRepository: AccountRepository  // non-null 타입으로 선언
) {
    fun getAccount(accountId: String): Account {
        // findById는 Optional<Account> 반환 → Kotlin에서 편리하게 처리
        return accountRepository.findById(accountId)
            .orElseThrow { AccountNotFoundException("계좌 없음: $accountId") }
    }
}

// Spring Data JPA에서 Kotlin null-safe 사용
interface AccountRepository : JpaRepository<Account, String> {
    // 반환 타입을 nullable로 선언하면 Optional 불필요
    fun findByAccountNumber(accountNumber: String): Account?
}
```

---

## 25.12 실전 체크리스트

### 빈 설계 체크리스트

- [ ] 서비스 클래스는 생성자 주입을 사용하는가?
- [ ] 싱글톤 빈에 가변 인스턴스 변수가 없는가? (스레드 안전성)
- [ ] 같은 타입의 빈이 여러 개라면 `@Primary` 또는 `@Qualifier`로 명확히 했는가?
- [ ] `@PostConstruct`에서 초기화할 무거운 작업은 비동기 처리했는가?
- [ ] 코틀린 `plugin.spring`이 `build.gradle.kts`에 포함되어 있는가?

### AOP 설계 체크리스트

- [ ] Aspect는 비즈니스 로직이 아닌 횡단 관심사만 담당하는가?
- [ ] `@Around`에서 예외를 잡으면 반드시 재던지는가?
- [ ] self-invocation이 없는지 확인했는가?
- [ ] Pointcut 표현식이 의도한 범위만 정확히 타겟하는가?

### 증권 도메인 권장 Aspect

| Aspect | 대상 | 용도 |
|--------|------|------|
| `AuditAspect` | 주문·계좌 변경 메서드 | 금융 감사 추적(법적 요구사항) |
| `PerformanceAspect` | 체결·조회 메서드 | SLA 모니터링 |
| `SecurityAspect` | 주문 API | 권한 검증 |
| `RetryAspect` | 외부 시스템 호출 | 한국 거래소 API 재시도 |

---

## 정리

- **IoC**: 객체 생성과 의존성 관리를 스프링 컨테이너에 위임하여 결합도를 낮춤
- **DI**: 생성자 주입을 기본으로, 불변성과 테스트 편의성을 확보
- **Bean Scope**: 대부분 싱글톤이지만, 상태가 있는 객체는 prototype 또는 메서드 로컬 변수로
- **AOP**: 감사추적·성능측정 등 횡단 관심사를 비즈니스 로직에서 분리, self-invocation 함정 주의
- **코틀린**: `plugin.spring`으로 `open` 문제 해결, null-safe API 활용

---

이전: [14. REST API 설계](14-rest-api.md) · 다음: [26. 스프링 웹 계층: MVC와 WebFlux](26-spring-boot-web.md) · [전체 커리큘럼](../CURRICULUM.md)
