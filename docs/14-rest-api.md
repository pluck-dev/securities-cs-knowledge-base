# 14. 심화: 주문 처리기를 스프링 REST API로

[07장의 콘솔 예제](07-order-system-tutorial.md)를 실제 서버 API로 발전시킵니다.
앱(MTS)이 HTTP로 주문을 보내면 서버가 받아 처리하는 구조입니다.

> ⚠️ 이 코드는 Spring Boot 프로젝트(Gradle)에서 실행됩니다. 개념과 구조를 익히는 용도입니다.

## 전체 흐름

```
[앱] --POST /api/orders--> [Controller] --> [Service(검증+체결)] --> [Repository(DB)]
                              ↑                                          │
                              └──────────── JSON 응답 ────────────────────┘
```

## 1. 요청/응답 DTO (앱과 주고받는 데이터)

```kotlin
// 앱이 보내는 주문 요청
data class OrderRequest(
    val symbol: String,
    val side: Side,
    val type: OrderType,
    val price: BigDecimal,
    val quantity: Int
)

// 서버가 돌려주는 응답
data class OrderResponse(
    val orderId: String,
    val status: String,        // "ACCEPTED" or "REJECTED"
    val message: String? = null
)
```

> DTO(Data Transfer Object)는 "외부와 주고받는 전용 데이터". 내부 도메인 객체(Order)와 분리하는 게 좋은 습관입니다.

## 2. Controller — HTTP 요청을 받음

```kotlin
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {
    @PostMapping
    fun placeOrder(@RequestBody req: OrderRequest): ResponseEntity<OrderResponse> {
        val result = orderService.place(req)
        val httpStatus = if (result.status == "ACCEPTED") HttpStatus.OK else HttpStatus.BAD_REQUEST
        return ResponseEntity.status(httpStatus).body(result)
    }

    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: String): OrderResponse {
        return orderService.findById(orderId)
    }
}
```

| 애너테이션 | 역할 |
|-----------|------|
| `@RestController` | 이 클래스는 REST API 컨트롤러 |
| `@RequestMapping("/api/orders")` | 공통 URL 경로 |
| `@PostMapping` | HTTP POST 요청 처리 (주문 생성) |
| `@GetMapping("/{id}")` | HTTP GET 요청 (주문 조회) |
| `@RequestBody` | 요청 본문 JSON → 코틀린 객체 변환 |
| `@PathVariable` | URL 경로의 값 추출 |

## 3. Service — 업무 로직 (검증 + 체결)

```kotlin
@Service
class OrderService(
    private val accountRepository: AccountRepository,
    private val orderRepository: OrderRepository
) {
    @Transactional   // ★ 검증~저장을 하나의 트랜잭션으로 (정합성 보장)
    fun place(req: OrderRequest): OrderResponse {
        val account = accountRepository.findByIdForUpdate(req.accountId)  // 비관적 락
            ?: return OrderResponse("-", "REJECTED", "계좌 없음")

        val order = req.toOrder(generateOrderId())

        // 07장의 validateOrder 로직을 그대로 재사용
        when (val result = validateOrder(order, account)) {
            is ValidationResult.Rejected ->
                return OrderResponse(order.orderId, "REJECTED", result.reason)
            is ValidationResult.Success -> {
                applyExecution(order, account)        // 잔고 반영
                accountRepository.save(account)
                orderRepository.save(order)
                return OrderResponse(order.orderId, "ACCEPTED")
            }
        }
    }
}
```

**핵심: `@Transactional`**
- 검증 → 잔고 차감 → 주문 저장을 **하나의 묶음**으로 처리.
- 중간에 실패하면 전부 롤백 → 잔고만 줄고 주문은 저장 안 되는 사고 방지.
- `findByIdForUpdate`는 [비관적 락](11-concurrency.md)으로 동시 주문을 직렬화.

## 4. Repository — DB 접근

```kotlin
interface OrderRepository : JpaRepository<Order, String>

interface AccountRepository : JpaRepository<Account, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)   // SELECT ... FOR UPDATE
    @Query("select a from Account a where a.accountId = :id")
    fun findByIdForUpdate(id: String): Account?
}
```

## 5. 호출 예시 (앱 → 서버)

```http
POST /api/orders
Content-Type: application/json

{
  "symbol": "005930",
  "side": "BUY",
  "type": "LIMIT",
  "price": 70000,
  "quantity": 10
}
```

**성공 응답 (200)**
```json
{ "orderId": "ORD-20260615-0001", "status": "ACCEPTED", "message": null }
```

**거절 응답 (400)**
```json
{ "orderId": "ORD-20260615-0002", "status": "REJECTED", "message": "예수금 부족" }
```

## 콘솔 예제 → API 매핑

| 07장 콘솔 | 14장 API |
|-----------|----------|
| `main()`의 `process()` | `OrderController.placeOrder()` |
| `validateOrder()` | `OrderService` 내부에서 재사용 |
| `applyExecution()` | `@Transactional` 안에서 실행 |
| `Account` (메모리) | DB의 Account 테이블 + 락 |

> **같은 로직, 다른 껍데기.** 콘솔에서 검증한 비즈니스 로직을 그대로 API 계층에 올리는 게 핵심입니다.

---

다음: [15. 이벤트 기반 아키텍처](15-event-driven.md)
