# 23. 테스트 완전정복

## 왜 금융 시스템에서 테스트는 생명인가?

주문 처리 버그 하나가 수천만 원의 손실을 유발하거나, 고객의 매도 주문이 체결되지 않아 투자 손실로 이어질 수 있다. 증권사 백엔드는 **정확성(correctness)** 이 선택이 아닌 필수다.

실제로 발생한 금융 소프트웨어 사고의 공통점:

| 사고 유형 | 실제 원인 | 테스트로 예방 가능 여부 |
|---|---|---|
| 주문 수량 2배 체결 | 정수 오버플로우 | 단위 테스트 |
| 원화 금액 달러로 처리 | 통화 단위 혼재 | 파라미터화 테스트 |
| 잔고 부족 검증 누락 | 신규 기능 추가 시 회귀 | 통합 테스트 |
| 동시 주문 잔고 초과 | Race Condition | 동시성 테스트 |
| BigDecimal 반올림 오차 | `.equals()` 비교 버그 | 단위 테스트 |

> **금언**: "테스트 없이 빠르게 개발하는 것은, 안전장치 없이 고속으로 달리는 것과 같다."

---

## 테스트 피라미드 (Test Pyramid)

```
        /\
       /E2E\          소수, 느림, 비용 높음
      /──────\
     /통합 테스트\      중간, 스프링 컨텍스트 로드
    /────────────\
   /  단위 테스트  \    다수, 빠름, 독립적
  /────────────────\
```

### 각 계층의 역할

| 계층 | 테스트 대상 | 속도 | 비율 (권장) |
|---|---|---|---|
| 단위 테스트 (Unit) | 단일 클래스/메서드 | 밀리초 | 70% |
| 통합 테스트 (Integration) | 여러 컴포넌트, DB, 메시지 브로커 | 초 | 20% |
| E2E 테스트 | 실제 HTTP 요청 → 응답 전체 | 분 | 10% |

> **함정**: 피라미드를 뒤집으면(E2E 위주) CI 파이프라인이 30분씩 걸린다. 단위 테스트 기반을 탄탄히 하는 것이 핵심이다.

---

## JUnit 5 기초

### 의존성 (build.gradle.kts)

```kotlin
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // spring-boot-starter-test 안에 JUnit5, AssertJ, Mockito 포함
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### 핵심 어노테이션

```kotlin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*

class OrderValidatorTest {

    private lateinit var validator: OrderValidator

    @BeforeEach   // 각 테스트 메서드 실행 전 호출
    fun setUp() {
        validator = OrderValidator()
    }

    @AfterEach    // 각 테스트 메서드 실행 후 호출
    fun tearDown() {
        // 리소스 정리 등
    }

    @Test
    @DisplayName("정상 매수 주문은 유효성 검증을 통과해야 한다")
    fun `정상 매수 주문은 유효성 검증을 통과해야 한다`() {
        // given
        val order = createBuyOrder(quantity = 10, pricePerShare = BigDecimal("75000"))

        // when
        val result = validator.validate(order)

        // then
        assertTrue(result.isValid)
    }

    @Test
    @DisplayName("수량이 0이면 주문 검증에 실패해야 한다")
    fun `수량이 0이면 주문 검증에 실패해야 한다`() {
        val order = createBuyOrder(quantity = 0, pricePerShare = BigDecimal("75000"))

        val result = validator.validate(order)

        assertFalse(result.isValid)
        assertEquals("수량은 1 이상이어야 합니다.", result.errorMessage)
    }

    @Test
    @DisplayName("음수 주가는 예외를 발생시켜야 한다")
    fun `음수 주가는 예외를 발생시켜야 한다`() {
        val exception = assertThrows<IllegalArgumentException> {
            createBuyOrder(quantity = 10, pricePerShare = BigDecimal("-1000"))
        }
        assertTrue(exception.message!!.contains("주가는 0보다 커야 합니다"))
    }

    @Nested
    @DisplayName("시장가 주문 검증")
    inner class MarketOrderValidation {

        @Test
        fun `시장가 주문은 가격 없이 유효해야 한다`() {
            val order = Order(
                type = OrderType.MARKET,
                side = OrderSide.BUY,
                symbol = "005930",
                quantity = 5,
                price = null  // 시장가는 가격 없음
            )
            assertTrue(validator.validate(order).isValid)
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic  // companion object 안에서 BeforeAll 사용 시 필요
        fun globalSetup() {
            // DB 연결, 테스트 컨테이너 시작 등 전체 클래스에서 한 번
        }
    }
}
```

---

## 코틀린 테스트 관용구 — 백틱 함수명

JUnit5는 메서드명에 공백을 허용하지 않는다. 코틀린에서는 **백틱(backtick)** 으로 감싸면 공백 포함 함수명을 쓸 수 있어 테스트 의도가 명확해진다.

```kotlin
@Test
fun `삼성전자 주식 100주 매수 시 총 주문금액은 7_500_000원이어야 한다`() { ... }

@Test
fun `잔고 부족 시 주문 접수는 REJECTED 상태여야 한다`() { ... }

@Test
fun `장 마감 후 주문은 다음날 개장 시 처리되어야 한다`() { ... }
```

---

## BigDecimal 비교 — 가장 흔한 함정

금융 시스템에서 `BigDecimal`을 잘못 비교하면 테스트가 통과해도 실제 버그가 숨어있다.

```kotlin
val a = BigDecimal("75000.00")
val b = BigDecimal("75000")

// ❌ 잘못된 방법 — equals()는 scale(소수점 자릿수)까지 비교
println(a == b)           // false! "75000.00" ≠ "75000" (scale 다름)
println(a.equals(b))      // false!

// ✅ 올바른 방법 — compareTo()는 수학적 값만 비교
println(a.compareTo(b) == 0)  // true

// ✅ JUnit5 AssertJ 스타일
import org.assertj.core.api.Assertions.assertThat

assertThat(a).isEqualByComparingTo(b)             // ✅ scale 무시하고 값만 비교
assertThat(totalAmount).isEqualByComparingTo("7500000")

// ❌ 이건 scale이 다르면 실패
assertEquals(BigDecimal("7500000.00"), totalAmount)  // 위험!
```

> **실무 팁**: 금융 금액 테스트에서는 `assertEquals` 대신 반드시 `assertThat(x).isEqualByComparingTo(y)` 또는 `x.compareTo(y) == 0`을 사용하라. BigDecimal의 `equals()`는 `"75000.00" != "75000"`으로 판단한다.

---

## MockK로 목(Mock) 객체 작성

코틀린에서는 자바의 Mockito보다 **MockK** 가 훨씬 관용적이다. 코틀린 클래스는 기본이 `final`이라 Mockito가 목을 만들기 어렵지만, MockK는 이를 기본 처리한다.

### 의존성

```kotlin
testImplementation("io.mockk:mockk:1.13.11")
testImplementation("com.ninja-squad:springmockk:4.0.2")  // @MockkBean, @SpykBean
```

### 기본 사용법

```kotlin
import io.mockk.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderServiceTest {

    // 목 객체 생성
    private val orderRepository: OrderRepository = mockk()
    private val balanceService: BalanceService = mockk()
    private val eventPublisher: OrderEventPublisher = mockk(relaxed = true)
    // relaxed = true: 스텁 정의 없이 호출해도 기본값 반환 (Unit 반환 메서드에 유용)

    private val orderService = OrderService(orderRepository, balanceService, eventPublisher)

    @Test
    fun `잔고 충분 시 주문이 정상 접수되어야 한다`() {
        // given — every: 목 메서드 스텁 정의
        val order = createBuyOrder(quantity = 10, pricePerShare = BigDecimal("75000"))
        val requiredAmount = BigDecimal("750000")

        every { balanceService.getAvailableBalance("ACC001") } returns BigDecimal("1000000")
        every { orderRepository.save(any()) } answers { firstArg() }  // 입력 그대로 반환

        // when
        val result = orderService.placeOrder("ACC001", order)

        // then
        assertEquals(OrderStatus.ACCEPTED, result.status)

        // verify: 특정 메서드가 호출되었는지 검증
        verify(exactly = 1) { orderRepository.save(match { it.status == OrderStatus.ACCEPTED }) }
        verify(exactly = 1) { eventPublisher.publish(any()) }
    }

    @Test
    fun `잔고 부족 시 주문이 거부되어야 한다`() {
        val order = createBuyOrder(quantity = 100, pricePerShare = BigDecimal("75000"))

        every { balanceService.getAvailableBalance("ACC001") } returns BigDecimal("100000")
        every { orderRepository.save(any()) } answers { firstArg() }

        val result = orderService.placeOrder("ACC001", order)

        assertEquals(OrderStatus.REJECTED, result.status)
        assertEquals("잔고 부족", result.rejectionReason)

        // 이벤트는 발행되지 않아야 함
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `주문 저장 시 전달된 실제 객체를 캡처할 수 있다`() {
        val slot = slot<Order>()   // 캡처용 슬롯

        every { balanceService.getAvailableBalance(any()) } returns BigDecimal("1000000")
        every { orderRepository.save(capture(slot)) } answers { firstArg() }

        orderService.placeOrder("ACC001", createBuyOrder(10, BigDecimal("75000")))

        // 실제로 저장된 객체를 검사
        val savedOrder = slot.captured
        assertEquals("005930", savedOrder.symbol)
        assertThat(savedOrder.totalAmount).isEqualByComparingTo("750000")
    }
}
```

### `every` 고급 매처

```kotlin
// 특정 조건을 만족하는 인자
every { repository.findByStatus(match { it == OrderStatus.PENDING }) } returns listOf(...)

// 어떤 값이든
every { repository.save(any()) } returns savedOrder

// 특정 타입
every { repository.save(ofType<MarketOrder>()) } returns savedOrder

// 예외 던지기
every { balanceService.getAvailableBalance("INVALID") } throws
    AccountNotFoundException("계좌를 찾을 수 없습니다.")

// 순서대로 다른 값 반환 (첫 호출, 두 번째 호출...)
every { marketDataService.getPrice("005930") } returnsMany
    listOf(BigDecimal("75000"), BigDecimal("75500"), BigDecimal("76000"))
```

---

## Kotest — 코틀린 네이티브 테스트 프레임워크

MockK와 함께 코틀린 프로젝트에서 JUnit5를 보완하는 강력한 옵션. **BDD 스타일**의 가독성 높은 테스트를 제공한다.

### 의존성

```kotlin
testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
testImplementation("io.kotest:kotest-assertions-core:5.9.1")
testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
```

### BehaviorSpec — 증권 도메인 예시

```kotlin
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow
import java.math.BigDecimal

class OrderPricingBehaviorTest : BehaviorSpec({

    val pricingService = OrderPricingService()

    Given("삼성전자 주식 매수 주문이 있을 때") {
        val symbol = "005930"
        val quantity = 100

        When("현재가가 75,000원이면") {
            val currentPrice = BigDecimal("75000")

            Then("총 주문금액은 7,500,000원이어야 한다") {
                val total = pricingService.calculateTotal(symbol, quantity, currentPrice)
                total shouldBeEqualComparingTo BigDecimal("7500000")
            }

            Then("위탁수수료(0.015%)는 1,125원이어야 한다") {
                val commission = pricingService.calculateCommission(BigDecimal("7500000"))
                commission shouldBeEqualComparingTo BigDecimal("1125")
            }
        }

        When("상한가(29.99% 상승)에 주문을 넣으면") {
            val upperLimitPrice = BigDecimal("75000").multiply(BigDecimal("1.2999"))

            Then("주문이 허용되어야 한다") {
                val result = pricingService.isPriceWithinLimit(symbol, upperLimitPrice)
                result shouldBe true
            }
        }

        When("상한가를 초과한 가격으로 주문하면") {
            val overLimitPrice = BigDecimal("75000").multiply(BigDecimal("1.31"))

            Then("가격 제한 초과 예외가 발생해야 한다") {
                shouldThrow<PriceLimitExceededException> {
                    pricingService.validatePrice(symbol, overLimitPrice)
                }
            }
        }
    }
})
```

### 유용한 Kotest 매처

```kotlin
// 값 비교
result shouldBe OrderStatus.ACCEPTED
result shouldNotBe null

// BigDecimal (금액 비교의 핵심)
totalAmount shouldBeEqualComparingTo BigDecimal("7500000")
totalAmount shouldBeEqualComparingTo "7500000"  // 문자열로도 가능

// 컬렉션
orders shouldHaveSize 3
orders shouldContain specificOrder
orders.map { it.status } shouldContainAll listOf(OrderStatus.PENDING, OrderStatus.ACCEPTED)

// 문자열
errorMessage shouldContain "잔고 부족"
errorMessage shouldStartWith "주문 오류"

// 예외
val ex = shouldThrow<InsufficientBalanceException> {
    orderService.placeOrder(order)
}
ex.message shouldContain "750000"
```

---

## 파라미터화 테스트

같은 로직을 다양한 입력값으로 반복 검증할 때 사용한다. 증권 도메인에서 가격 제한, 수수료 계산, 세금 계산에 특히 유용하다.

### JUnit5 `@ParameterizedTest`

```kotlin
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class CommissionCalculatorTest {

    private val calculator = CommissionCalculator(rate = BigDecimal("0.00015"))

    @ParameterizedTest(name = "주문금액 {0}원의 위탁수수료는 {1}원이어야 한다")
    @CsvSource(
        "1000000, 150",
        "5000000, 750",
        "10000000, 1500",
        "100000000, 15000"
    )
    fun `다양한 주문금액에 대한 위탁수수료 계산`(
        orderAmount: String,
        expectedCommission: String
    ) {
        val commission = calculator.calculate(BigDecimal(orderAmount))
        assertThat(commission).isEqualByComparingTo(BigDecimal(expectedCommission))
    }

    // 복잡한 파라미터는 @MethodSource 사용
    @ParameterizedTest
    @MethodSource("invalidOrderProvider")
    fun `유효하지 않은 주문은 검증에 실패해야 한다`(order: Order, expectedError: String) {
        val result = OrderValidator().validate(order)
        assertFalse(result.isValid)
        assertEquals(expectedError, result.errorMessage)
    }

    companion object {
        @JvmStatic
        fun invalidOrderProvider(): Stream<Arguments> = Stream.of(
            Arguments.of(
                createBuyOrder(quantity = 0, pricePerShare = BigDecimal("75000")),
                "수량은 1 이상이어야 합니다."
            ),
            Arguments.of(
                createBuyOrder(quantity = 10, pricePerShare = BigDecimal.ZERO),
                "주가는 0보다 커야 합니다."
            ),
            Arguments.of(
                createBuyOrder(quantity = -5, pricePerShare = BigDecimal("75000")),
                "수량은 1 이상이어야 합니다."
            )
        )
    }
}
```

### Kotest의 `forAll`

```kotlin
import io.kotest.property.forAll
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.positiveInt

class OrderQuantityPropertyTest : StringSpec({

    "주문 수량은 항상 양수여야 한다" {
        forAll(Arb.positiveInt(max = 10000)) { quantity ->
            val order = createBuyOrder(quantity = quantity, pricePerShare = BigDecimal("75000"))
            order.quantity > 0
        }
    }
})
```

---

## 테스트 더블 (Test Doubles) 비교

| 종류 | 설명 | 코틀린/MockK 예시 | 사용 시점 |
|---|---|---|---|
| **Stub** | 미리 정해진 값 반환 | `every { repo.find() } returns order` | 의존성의 리턴값이 필요할 때 |
| **Mock** | 호출 여부·횟수 검증 | `verify(exactly = 1) { publisher.publish(any()) }` | 사이드 이펙트 검증 |
| **Fake** | 실제 동작하는 간략 구현 | 인메모리 저장소 직접 구현 | 빠른 통합 테스트 |
| **Spy** | 실제 객체에 일부 동작만 오버라이드 | `spyk(realService)` | 실제 로직 일부 유지하며 일부만 목 처리 |
| **Dummy** | 인자 채우기용, 실제 사용 안 됨 | `mockk<Logger>(relaxed = true)` | 인터페이스 구현이 필요하지만 검증은 불필요 |

### Fake 저장소 예시

```kotlin
// 실제 DB 없이 동작하는 가짜 저장소
class FakeOrderRepository : OrderRepository {
    private val storage = mutableMapOf<String, Order>()

    override fun save(order: Order): Order {
        storage[order.id] = order
        return order
    }

    override fun findById(id: String): Order? = storage[id]

    override fun findByAccountId(accountId: String): List<Order> =
        storage.values.filter { it.accountId == accountId }
}

// 테스트에서 사용
class OrderServiceFakeTest {
    private val fakeRepo = FakeOrderRepository()
    private val orderService = OrderService(fakeRepo, mockk(relaxed = true))

    @Test
    fun `주문 접수 후 조회가 가능해야 한다`() {
        val order = orderService.placeOrder("ACC001", createBuyOrder(10, BigDecimal("75000")))
        val found = orderService.findOrder(order.id)
        assertNotNull(found)
        assertEquals(order.id, found!!.id)
    }
}
```

---

## 주문 검증 로직 단위 테스트 — 전체 예시

### 도메인 모델

```kotlin
data class Order(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val symbol: String,
    val type: OrderType,
    val side: OrderSide,
    val quantity: Int,
    val price: BigDecimal?,  // 지정가 주문만 있음
    val status: OrderStatus = OrderStatus.PENDING
) {
    val totalAmount: BigDecimal
        get() = price?.multiply(BigDecimal(quantity)) ?: BigDecimal.ZERO
}

enum class OrderType { LIMIT, MARKET }
enum class OrderSide { BUY, SELL }
enum class OrderStatus { PENDING, ACCEPTED, REJECTED, FILLED, CANCELLED }

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
```

### 검증기

```kotlin
@Component
class OrderValidator {
    fun validate(order: Order): ValidationResult {
        if (order.quantity <= 0) {
            return ValidationResult(false, "수량은 1 이상이어야 합니다.")
        }
        if (order.type == OrderType.LIMIT) {
            if (order.price == null) {
                return ValidationResult(false, "지정가 주문은 가격이 필요합니다.")
            }
            if (order.price <= BigDecimal.ZERO) {
                return ValidationResult(false, "주가는 0보다 커야 합니다.")
            }
        }
        if (order.symbol.isBlank()) {
            return ValidationResult(false, "종목코드는 필수입니다.")
        }
        return ValidationResult(true)
    }
}
```

### 완전한 단위 테스트 스위트

```kotlin
class OrderValidatorTest {

    private val validator = OrderValidator()

    // ─── 수량 검증 ──────────────────────────────────────────────────

    @Test
    fun `수량 1 이상은 통과해야 한다`() {
        val order = createLimitBuyOrder(quantity = 1, price = BigDecimal("75000"))
        assertTrue(validator.validate(order).isValid)
    }

    @Test
    fun `수량 0은 실패해야 한다`() {
        val result = validator.validate(
            createLimitBuyOrder(quantity = 0, price = BigDecimal("75000"))
        )
        assertFalse(result.isValid)
        assertEquals("수량은 1 이상이어야 합니다.", result.errorMessage)
    }

    @Test
    fun `음수 수량은 실패해야 한다`() {
        val result = validator.validate(
            createLimitBuyOrder(quantity = -1, price = BigDecimal("75000"))
        )
        assertFalse(result.isValid)
    }

    // ─── 지정가 주문 가격 검증 ──────────────────────────────────────

    @Test
    fun `지정가 주문에 가격이 없으면 실패해야 한다`() {
        val order = Order(
            accountId = "ACC001",
            symbol = "005930",
            type = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = 10,
            price = null  // 지정가인데 가격 없음
        )
        val result = validator.validate(order)
        assertFalse(result.isValid)
        assertEquals("지정가 주문은 가격이 필요합니다.", result.errorMessage)
    }

    @Test
    fun `지정가 주문의 가격이 0이면 실패해야 한다`() {
        val result = validator.validate(
            createLimitBuyOrder(quantity = 10, price = BigDecimal.ZERO)
        )
        assertFalse(result.isValid)
    }

    // ─── 시장가 주문 ────────────────────────────────────────────────

    @Test
    fun `시장가 주문은 가격 없이 유효해야 한다`() {
        val order = Order(
            accountId = "ACC001",
            symbol = "005930",
            type = OrderType.MARKET,
            side = OrderSide.BUY,
            quantity = 10,
            price = null
        )
        assertTrue(validator.validate(order).isValid)
    }

    // ─── 종목코드 검증 ──────────────────────────────────────────────

    @Test
    fun `빈 종목코드는 실패해야 한다`() {
        val result = validator.validate(
            createLimitBuyOrder(quantity = 10, price = BigDecimal("75000"), symbol = "")
        )
        assertFalse(result.isValid)
        assertEquals("종목코드는 필수입니다.", result.errorMessage)
    }

    // ─── 헬퍼 메서드 ────────────────────────────────────────────────

    private fun createLimitBuyOrder(
        quantity: Int,
        price: BigDecimal,
        symbol: String = "005930"
    ) = Order(
        accountId = "ACC001",
        symbol = symbol,
        type = OrderType.LIMIT,
        side = OrderSide.BUY,
        quantity = quantity,
        price = price
    )
}
```

---

## 스프링 통합 테스트

단위 테스트가 개별 클래스를 검증한다면, 통합 테스트는 **스프링 컨텍스트, 데이터베이스, 보안 필터**가 함께 작동하는지 검증한다.

### `@SpringBootTest` — 전체 컨텍스트 로드

```kotlin
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")  // application-test.yml 적용
class OrderControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val orderRepository: OrderRepository
) {

    @BeforeEach
    fun cleanUp() {
        orderRepository.deleteAll()
    }

    @Test
    @WithMockUser(username = "testuser", roles = ["TRADER"])
    fun `POST 주문 접수 API는 201을 반환해야 한다`() {
        val requestBody = """
            {
                "symbol": "005930",
                "type": "LIMIT",
                "side": "BUY",
                "quantity": 10,
                "price": "75000"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("X-Account-Id", "ACC001")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.totalAmount").value("750000"))
            .andDo(print())
    }

    @Test
    fun `인증 없이 주문 접수는 401을 반환해야 한다`() {
        mockMvc.perform(post("/api/v1/orders"))
            .andExpect(status().isUnauthorized)
    }
}
```

### `@DataJpaTest` — JPA 레이어만 테스트

전체 컨텍스트를 로드하지 않고 JPA/데이터베이스 계층만 빠르게 테스트한다. H2 인메모리 DB를 자동으로 사용한다.

```kotlin
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager

@DataJpaTest
class OrderRepositoryTest(
    @Autowired private val entityManager: TestEntityManager,
    @Autowired private val orderRepository: OrderRepository
) {

    @Test
    fun `계좌 ID로 주문 목록을 조회할 수 있어야 한다`() {
        // given — 테스트 데이터 직접 삽입
        val order1 = entityManager.persistAndFlush(
            Order(accountId = "ACC001", symbol = "005930",
                  type = OrderType.LIMIT, side = OrderSide.BUY,
                  quantity = 10, price = BigDecimal("75000"))
        )
        val order2 = entityManager.persistAndFlush(
            Order(accountId = "ACC001", symbol = "000660",
                  type = OrderType.LIMIT, side = OrderSide.SELL,
                  quantity = 5, price = BigDecimal("150000"))
        )
        // ACC002 계좌의 주문 (조회 결과에 포함되면 안 됨)
        entityManager.persistAndFlush(
            Order(accountId = "ACC002", symbol = "035720",
                  type = OrderType.MARKET, side = OrderSide.BUY,
                  quantity = 1, price = null)
        )

        // when
        val results = orderRepository.findByAccountId("ACC001")

        // then
        assertEquals(2, results.size)
        assertTrue(results.all { it.accountId == "ACC001" })
        assertThat(results[0].totalAmount).isEqualByComparingTo("750000")
    }

    @Test
    fun `PENDING 상태 주문만 조회할 수 있어야 한다`() {
        entityManager.persistAndFlush(
            Order(accountId = "ACC001", symbol = "005930",
                  type = OrderType.LIMIT, side = OrderSide.BUY,
                  quantity = 10, price = BigDecimal("75000"),
                  status = OrderStatus.PENDING)
        )
        entityManager.persistAndFlush(
            Order(accountId = "ACC001", symbol = "000660",
                  type = OrderType.LIMIT, side = OrderSide.BUY,
                  quantity = 5, price = BigDecimal("150000"),
                  status = OrderStatus.FILLED)
        )

        val pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING)
        assertEquals(1, pendingOrders.size)
    }
}
```

### `@MockkBean` — 스프링 통합 테스트에서 MockK 사용

```kotlin
import com.ninjasquad.springmockk.MockkBean

@WebMvcTest(OrderController::class)  // 컨트롤러만 로드
class OrderControllerUnitTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockkBean  // 스프링 컨텍스트에 MockK 주입 (Mockito의 @MockBean 대신)
    private lateinit var orderService: OrderService

    @Test
    @WithMockUser(roles = ["TRADER"])
    fun `서비스에서 잔고 부족 예외 시 400 응답이어야 한다`() {
        every { orderService.placeOrder(any(), any()) } throws
            InsufficientBalanceException("잔고가 부족합니다.")

        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"symbol":"005930","type":"LIMIT","side":"BUY","quantity":100,"price":"75000"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("잔고가 부족합니다."))
    }
}
```

---

## `application-test.yml` 설정

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop   # 테스트마다 스키마 재생성
    show-sql: true
  kafka:
    bootstrap-servers: localhost:9999   # 실제 연결 없음 — MockK로 처리

logging:
  level:
    org.hibernate.SQL: DEBUG
    com.yeouido: DEBUG
```

---

## 테스트 커버리지

```kotlin
// build.gradle.kts
plugins {
    id("jacoco")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)   // SonarQube 연동용
        html.required.set(true)  // 사람이 읽는 리포트
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()  // 80% 미만 빌드 실패
            }
        }
        rule {
            element = "CLASS"
            includes = listOf("com.yeouido.order.domain.*")  // 도메인 레이어는 더 엄격하게
            limit {
                counter = "BRANCH"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}
```

```bash
# 커버리지 리포트 생성
./gradlew test jacocoTestReport

# 리포트 위치
open build/reports/jacoco/test/html/index.html

# 커버리지 기준 미달 시 빌드 실패
./gradlew jacocoTestCoverageVerification
```

> **함정**: 커버리지 숫자만 보고 품질을 판단하지 마라. 100% 커버리지라도 검증(assertion)이 없는 테스트는 아무 의미가 없다. `assertThat(true).isTrue()`처럼 항상 참인 검증은 커버리지만 올리고 버그를 잡지 못한다.

---

## 동시성 테스트의 어려움

증권 시스템의 가장 어려운 버그는 **Race Condition**이다. 잔고 100만 원인 계좌에서 두 주문이 동시에 들어오면?

```kotlin
@Test
fun `동시 주문에서 잔고 초과가 발생하지 않아야 한다`() {
    // given: 잔고 750,000원
    val accountId = "ACC001"
    setupBalance(accountId, BigDecimal("750000"))

    // when: 500,000원짜리 주문 2개를 동시 접수
    val latch = CountDownLatch(2)
    val results = ConcurrentLinkedQueue<OrderResult>()

    repeat(2) {
        Thread {
            try {
                val result = orderService.placeOrder(
                    accountId,
                    createBuyOrder(quantity = 1, pricePerShare = BigDecimal("500000"))
                )
                results.add(result)
            } finally {
                latch.countDown()
            }
        }.start()
    }

    latch.await(5, TimeUnit.SECONDS)

    // then: 두 주문 중 하나는 반드시 REJECTED 되어야 함
    val accepted = results.count { it.status == OrderStatus.ACCEPTED }
    val rejected = results.count { it.status == OrderStatus.REJECTED }

    assertEquals(1, accepted, "하나의 주문만 승인되어야 한다")
    assertEquals(1, rejected, "잔고 초과 주문은 거부되어야 한다")
}
```

> **실무 팁**: 이런 동시성 테스트는 비결정론적(nondeterministic)이라 가끔 통과하고 가끔 실패한다. 데이터베이스 트랜잭션과 `SELECT FOR UPDATE` 혹은 낙관적 잠금(Optimistic Locking)으로 실제 문제를 해결해야 한다. 테스트는 문제의 **존재 증명**, 해결은 **비즈니스 로직**에서 해야 한다.

---

## TDD (Test-Driven Development) 흐름

TDD는 **테스트 먼저** 작성하는 개발 방법론이다.

```
Red → Green → Refactor 사이클
```

| 단계 | 행동 | 목표 |
|---|---|---|
| Red | 실패하는 테스트 작성 | "무엇을 만들어야 하는가?" 명확화 |
| Green | 테스트를 통과시키는 최소 코드 작성 | 일단 동작하게 만들기 |
| Refactor | 중복 제거, 설계 개선 | 깔끔하게 만들기 |

### TDD 예시 — 수수료 계산기

```kotlin
// Step 1: Red — 테스트 먼저 (CommissionCalculator 클래스 아직 없음)
class CommissionCalculatorTddTest {

    @Test
    fun `1,000,000원 주문의 기본 수수료는 150원이어야 한다`() {
        val calculator = CommissionCalculator()
        val commission = calculator.calculate(BigDecimal("1000000"))
        assertThat(commission).isEqualByComparingTo("150")
    }
}

// Step 2: Green — 최소한의 구현
class CommissionCalculator {
    fun calculate(orderAmount: BigDecimal): BigDecimal =
        orderAmount.multiply(BigDecimal("0.00015"))
            .setScale(0, RoundingMode.HALF_UP)
}

// Step 3: Refactor — 요율을 주입 가능하게 개선
class CommissionCalculator(
    private val rate: BigDecimal = DEFAULT_RATE,
    private val scale: Int = 0,
    private val roundingMode: RoundingMode = RoundingMode.HALF_UP
) {
    fun calculate(orderAmount: BigDecimal): BigDecimal =
        orderAmount.multiply(rate).setScale(scale, roundingMode)

    companion object {
        val DEFAULT_RATE = BigDecimal("0.00015")
    }
}
```

---

## 증권 도메인 테스트 케이스 설계 예시

| 기능 | 정상 케이스 | 경계 케이스 | 예외 케이스 |
|---|---|---|---|
| 지정가 매수 주문 | 잔고 충분, 정상 가격 | 잔고 = 정확히 주문금액 | 잔고 1원 부족 |
| 시장가 매도 주문 | 보유 주식 있음 | 보유 수량 = 주문 수량 | 보유 주식 없음 |
| 주문 취소 | PENDING 상태 주문 | - | 이미 FILLED된 주문 취소 |
| 수수료 계산 | 일반 위탁 | 최소 수수료 적용 구간 | 금액 0원 |
| 잔고 조회 | 매수 후 잔고 감소 | 여러 통화 혼재 | 계좌 없음 |

---

## 체크리스트

- [ ] `useJUnitPlatform()`이 `build.gradle.kts`의 테스트 태스크에 설정되어 있는가?
- [ ] `BigDecimal` 비교에 `isEqualByComparingTo()` 또는 `compareTo()`를 사용하는가?
- [ ] Mockito 대신 MockK를 사용하고 있는가? (`mockito-core` exclude 여부 확인)
- [ ] 각 테스트는 독립적으로 실행 가능한가? (`@BeforeEach`로 상태 초기화)
- [ ] 통합 테스트에 `@ActiveProfiles("test")`가 적용되어 있는가?
- [ ] JaCoCo 커버리지 기준이 CI에서 강제되고 있는가?
- [ ] 동시성이 관련된 비즈니스 로직에 동시성 테스트가 있는가?
- [ ] 백틱 함수명으로 테스트 의도가 명확하게 표현되어 있는가?
- [ ] Fake 또는 H2를 사용해 DB 계층 테스트가 실제 외부 DB 없이 동작하는가?

---

이전: [22. Gradle 빌드 시스템](22-gradle-build) · 다음: [24. Spring Security 심화](24-spring-security) · [전체 커리큘럼](/curriculum)
