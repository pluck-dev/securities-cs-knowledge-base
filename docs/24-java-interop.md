# 24. 자바 상호운용(Java Interoperability)

> 대상: 증권사 레거시 자바 시스템과 코틀린 신규 모듈을 함께 운용해야 하는 백엔드 개발자  
> 목표: 자바↔코틀린 경계에서 발생하는 함정을 이해하고, 안전하게 상호운용하는 코드를 작성한다.

---

## 1. 왜 자바 상호운용이 증권사에서 중요한가

증권사 IT 시스템은 **10~20년 이상 된 레거시 자바 코드베이스**를 흔히 갖고 있다.

```
전형적인 증권사 기술 스택 현황:
┌─────────────────────────────────────────────────────────────┐
│ 레거시 (Java 8, WebLogic)                                    │
│  - 계좌 원장 시스템 (2010년 오픈)                             │
│  - FIX 프로토콜 주문 처리 엔진 (2008년 구축)                  │
│  - 결제/청산 배치 (Java EE)                                   │
├─────────────────────────────────────────────────────────────┤
│ 전환 중 (Java 11/17, Spring Boot)                            │
│  - 모바일 HTS API (Kotlin + Spring Boot 3)                   │
│  - 이벤트 기반 시세 처리 (Kotlin Coroutines)                  │
│  - 리스크 관리 마이크로서비스 (Kotlin)                         │
└─────────────────────────────────────────────────────────────┘
```

신규 코틀린 코드는 레거시 자바 라이브러리를 호출해야 하고, 레거시 자바 코드는 코틀린으로 작성된 새 모듈을 사용해야 한다. 이 경계를 이해하지 못하면 `NullPointerException`, `ClassCastException`, 컴파일 에러가 예상치 못한 곳에서 터진다.

---

## 2. 코틀린에서 자바 호출 — 기본

### 2-1. 자바 클래스 그냥 사용하기

코틀린은 JVM 위에서 동작하므로 자바 클래스를 추가 설정 없이 사용할 수 있다.

```java
// 레거시 자바 코드 (수정 불가 상황)
// com/securities/legacy/OrderEngine.java
public class OrderEngine {
    private final String marketId;

    public OrderEngine(String marketId) {
        this.marketId = marketId;
    }

    public OrderResult submitOrder(String stockCode, int quantity, double price) {
        // FIX 프로토콜로 거래소에 주문 전송하는 레거시 로직
        return new OrderResult(stockCode, "ACCEPTED");
    }

    public String getMarketId() {
        return marketId;
    }

    // 자바 getter/setter 패턴
    private boolean connected;

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
}
```

```kotlin
// 코틀린에서 레거시 자바 OrderEngine 사용
fun sendOrderToExchange(stockCode: String, quantity: Int, price: Double) {
    val engine = OrderEngine("KRX")  // 자바 생성자 호출 — 그냥 됨

    // getter → 코틀린 프로퍼티처럼 접근
    println(engine.marketId)         // getMarketId() 자동 매핑
    println(engine.isConnected)      // isXxx() → xxx 프로퍼티

    // setter → 프로퍼티 대입
    engine.isConnected = true        // setConnected(true) 자동 매핑

    val result = engine.submitOrder(stockCode, quantity, price)
    println(result)
}
```

### 2-2. getter/setter 자동 매핑 규칙

| 자바 메서드 | 코틀린 접근 방식 |
|------------|----------------|
| `getFoo()` | `obj.foo` |
| `setFoo(v)` | `obj.foo = v` |
| `isFoo()` | `obj.isFoo` (Boolean) |
| `getFoo()` 없이 필드만 | 직접 접근 불가 (접근 제어자 확인 필요) |

> **함정**: 자바에서 `boolean`의 getter는 `isFoo()`, `Boolean`(래퍼)의 getter도 `isFoo()`. 하지만 코틀린은 `Boolean?`으로 보기 때문에 null 체크가 필요할 수 있다.

---

## 3. 플랫폼 타입(Platform Type) — 가장 위험한 함정

### 3-1. 플랫폼 타입이란

자바 코드는 코틀린의 null 안전 시스템을 모른다. 자바에서 온 타입은 코틀린이 null 여부를 **알 수 없으므로** 이를 **플랫폼 타입(Platform Type)**이라고 부른다. 표기법은 `String!` (느낌표).

```kotlin
// 자바에서 온 메서드
val engine = OrderEngine("KRX")
val marketId = engine.marketId  // 타입은 String! (플랫폼 타입)

// 코틀린 컴파일러는 이것이 null일 수 있는지 모른다!
// 아래 두 가지 모두 컴파일된다:
val id1: String = engine.marketId   // non-null로 취급 (실제로 null이면 NPE!)
val id2: String? = engine.marketId  // nullable로 취급 (안전)
```

### 3-2. 플랫폼 타입이 숨기는 NPE

```java
// 레거시 자바 서비스
public class AccountService {
    // 계좌가 없으면 null 반환 — 자바 시대의 일반적 관행
    public Account findByCustomerId(String customerId) {
        if (!accounts.containsKey(customerId)) {
            return null;  // ← 코틀린은 이걸 모른다!
        }
        return accounts.get(customerId);
    }
}
```

```kotlin
// 위험한 코틀린 코드
val accountService = AccountService()
val account = accountService.findByCustomerId("C001")  // 타입: Account!

// 컴파일 오류 없음! 런타임에 NPE 폭탄
println(account.balance)  // account가 null이면 NullPointerException!

// ✅ 올바른 방법 1 — nullable로 명시적 선언
val safeAccount: Account? = accountService.findByCustomerId("C001")
safeAccount?.let { println(it.balance) }

// ✅ 올바른 방법 2 — null 체크 후 처리
val account2 = accountService.findByCustomerId("C002")
    ?: throw BusinessException("계좌를 찾을 수 없습니다: C002")
```

### 3-3. 플랫폼 타입 대응 전략

**전략 1: 자바 코드에 Null 어노테이션 추가** (레거시 소스 수정 가능한 경우)

```java
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AccountService {
    @Nullable  // ← 코틀린에게 "null일 수 있어"를 알림
    public Account findByCustomerId(@NotNull String customerId) {
        // ...
    }

    @NotNull   // ← 코틀린에게 "절대 null 아니야"를 알림
    public List<Order> findOrdersByAccount(@NotNull Account account) {
        // ...
    }
}
```

코틀린 컴파일러는 `@Nullable` → `Account?`, `@NotNull` → `Account`로 인식한다.

지원하는 null 어노테이션:
- `org.jetbrains.annotations.@Nullable / @NotNull`
- `javax.annotation.@Nullable / @Nonnull`
- `org.springframework.lang.@Nullable / @NonNull`
- `android.support.annotation.@Nullable / @NonNull`

**전략 2: 코틀린 래퍼(Wrapper) 클래스 작성** (레거시 소스 수정 불가한 경우)

```kotlin
// 레거시 자바 서비스를 감싸는 코틀린 래퍼 — 모든 플랫폼 타입 경계를 여기서 처리
class AccountServiceAdapter(
    private val delegate: AccountService  // 레거시 자바 서비스
) {
    fun findByCustomerId(customerId: String): Account? {
        return delegate.findByCustomerId(customerId)  // 명시적으로 nullable로 변환
    }

    fun findOrdersByAccount(account: Account): List<Order> {
        return delegate.findOrdersByAccount(account) ?: emptyList()
    }
}

// 사용
val adapter = AccountServiceAdapter(AccountService())
val account = adapter.findByCustomerId("C001")  // 이제 Account? — 안전함
```

**전략 3: 컴파일러 플래그로 엄격한 null 체크**

```kotlin
// build.gradle.kts
tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"  // JSR-305 어노테이션 엄격 처리
    }
}
```

---

## 4. 자바에서 코틀린 호출

### 4-1. 기본 코틀린 클래스 호출

```kotlin
// 코틀린 클래스
class OrderValidator(private val maxQuantity: Int = 100_000) {

    fun validate(order: Order): ValidationResult {
        return when {
            order.quantity <= 0 -> ValidationResult.Error("수량은 1 이상이어야 합니다")
            order.quantity > maxQuantity -> ValidationResult.Error("최대 수량 초과: $maxQuantity")
            else -> ValidationResult.Success
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
```

```java
// 자바에서 코틀린 클래스 호출
import com.securities.order.OrderValidator;
import com.securities.order.ValidationResult;

public class LegacyOrderProcessor {
    private final OrderValidator validator = new OrderValidator(100_000);  // 기본값 있어도 명시

    public void process(Order order) {
        ValidationResult result = validator.validate(order);

        // sealed class는 instanceof 체크로 분기
        if (result instanceof ValidationResult.Success) {
            // 성공 처리
        } else if (result instanceof ValidationResult.Error) {
            ValidationResult.Error error = (ValidationResult.Error) result;
            System.err.println("검증 실패: " + error.getMessage());  // data class 프로퍼티 → getter
        }
    }
}
```

### 4-2. 코틀린 object (싱글톤)

```kotlin
// 코틀린 singleton object
object MarketCalendar {
    val holidays: Set<LocalDate> = setOf(
        LocalDate.of(2025, 1, 1),   // 신정
        LocalDate.of(2025, 3, 1),   // 삼일절
    )

    fun isHoliday(date: LocalDate): Boolean = date in holidays
    fun isTradingDay(date: LocalDate): Boolean = !isHoliday(date) && date.dayOfWeek.value < 6
}
```

```java
// 자바에서 코틀린 object 호출 — INSTANCE를 통해 접근
import com.securities.MarketCalendar;
import java.time.LocalDate;

public class LegacyScheduler {
    public boolean canTrade(LocalDate date) {
        // 코틀린 object → 자바에서는 .INSTANCE 필요
        return MarketCalendar.INSTANCE.isTradingDay(date);
    }
}
```

---

## 5. @JvmStatic, @JvmField, @JvmOverloads

이 세 어노테이션은 자바에서 코틀린 코드를 더 자연스럽게 호출하기 위한 컴파일러 지시자다.

### 5-1. @JvmStatic

```kotlin
class StockUtils {
    companion object {
        // @JvmStatic 없이
        fun validateCode(code: String): Boolean = code.matches(Regex("^[0-9]{6}$"))

        // @JvmStatic 있음
        @JvmStatic
        fun formatCode(code: String): String = code.padStart(6, '0')
    }
}
```

```java
// 자바에서 호출 비교
// @JvmStatic 없을 때 — companion을 통해 접근해야 함
StockUtils.Companion.validateCode("005930");  // 불편함

// @JvmStatic 있을 때 — 자바 static처럼 직접 호출
StockUtils.formatCode("5930");  // 자연스러움
```

**object에서도 동일하게 적용:**

```kotlin
object OrderIdGenerator {
    private var counter = 0L

    @JvmStatic
    fun next(): Long = ++counter

    @JvmStatic
    fun reset() { counter = 0L }
}
```

```java
// 자바 레거시 코드에서
long newId = OrderIdGenerator.next();  // .INSTANCE 없이 바로 호출
```

### 5-2. @JvmField

```kotlin
data class StockInfo(
    @JvmField val code: String,         // 자바에서 필드 직접 접근 허용
    @JvmField val name: String,
    val marketCap: Long                  // @JvmField 없음 → getter만 생성
)
```

```java
// 자바에서 접근 방식 비교
StockInfo stock = new StockInfo("005930", "삼성전자", 500_000_000_000L);

// @JvmField 있는 필드 → 필드 직접 접근
System.out.println(stock.code);    // ✅ 바로 접근
System.out.println(stock.name);    // ✅ 바로 접근

// @JvmField 없는 필드 → getter 호출
System.out.println(stock.getMarketCap());  // getter 필요
// stock.marketCap;  // ← 컴파일 오류! 필드가 private이므로
```

> **언제 @JvmField를 쓰는가**: Jackson 역직렬화, JPA 엔티티 필드, 성능이 중요한 내부 필드 접근 시 사용. 단, 캡슐화가 약해지므로 public API에는 신중히 사용.

### 5-3. @JvmOverloads

코틀린의 기본 인자(Default Parameter)는 자바에서 인식하지 못한다.

```kotlin
// 기본 인자가 있는 코틀린 함수
fun createOrder(
    stockCode: String,
    quantity: Int,
    orderType: OrderType = OrderType.BUY,      // 기본값
    unitPrice: java.math.BigDecimal = java.math.BigDecimal.ZERO  // 시장가 주문
): Order {
    return Order(stockCode, quantity, orderType, unitPrice)
}
```

```java
// @JvmOverloads 없을 때 — 자바는 기본값을 모른다
// 아래만 가능:
Order order = OrderKt.createOrder("005930", 100, OrderType.BUY, BigDecimal.ZERO);
// 이것은 불가능 (컴파일 오류):
// Order order = OrderKt.createOrder("005930", 100);  // ❌
```

```kotlin
// @JvmOverloads 추가
@JvmOverloads
fun createOrder(
    stockCode: String,
    quantity: Int,
    orderType: OrderType = OrderType.BUY,
    unitPrice: java.math.BigDecimal = java.math.BigDecimal.ZERO
): Order {
    return Order(stockCode, quantity, orderType, unitPrice)
}
```

```java
// @JvmOverloads 있으면 오버로드 메서드 자동 생성:
// createOrder(String, Int)
// createOrder(String, Int, OrderType)
// createOrder(String, Int, OrderType, BigDecimal)

// 자바에서 다양하게 호출 가능
Order o1 = OrderKt.createOrder("005930", 100);
Order o2 = OrderKt.createOrder("005930", 100, OrderType.SELL);
Order o3 = OrderKt.createOrder("005930", 100, OrderType.BUY, new BigDecimal("75400"));
```

---

## 6. 자바 컬렉션 ↔ 코틀린 컬렉션

### 6-1. 코틀린 컬렉션의 구조

코틀린 컬렉션은 **읽기 전용(Read-Only)**과 **변경 가능(Mutable)**으로 나뉜다. 그러나 JVM 위에서 둘 다 자바의 같은 클래스를 사용한다.

```
코틀린 타입              자바 구현 클래스
List<T>         →       java.util.Arrays$ArrayList (또는 java.util.ArrayList)
MutableList<T>  →       java.util.ArrayList
Set<T>          →       java.util.LinkedHashSet
MutableSet<T>   →       java.util.LinkedHashSet
Map<K,V>        →       java.util.LinkedHashMap
MutableMap<K,V> →       java.util.LinkedHashMap
```

**중요**: 코틀린의 읽기 전용 `List`는 **컴파일 시점 보장**이다. 런타임에는 실제 `ArrayList`이므로 자바에서 `add()`를 호출하면 **런타임에 성공**할 수 있다. 이 경계를 인식하는 것이 핵심이다.

### 6-2. 자바 컬렉션을 코틀린에서 받을 때

```java
// 레거시 자바 서비스
public class LegacyPortfolioService {
    public List<Stock> getPortfolio(String customerId) {
        // java.util.ArrayList 반환
        List<Stock> stocks = new ArrayList<>();
        stocks.add(new Stock("005930", "삼성전자", new BigDecimal("75400")));
        return stocks;
    }
}
```

```kotlin
val portfolioService = LegacyPortfolioService()

// 자바 List<Stock> → 코틀린 (Mutable)List<Stock>! (플랫폼 타입)
val portfolio = portfolioService.getPortfolio("C001")

// 안전하게 변환 — 불변 리스트로 방어적 복사
val immutablePortfolio: List<Stock> = portfolio.toList()          // 방어적 복사
val mutablePortfolio: MutableList<Stock> = portfolio.toMutableList()  // 변경 가능 복사

// 코틀린 확장 함수는 자바 컬렉션에도 바로 적용됨
val totalValue = portfolio
    .filter { it.price > java.math.BigDecimal("50000") }
    .sumOf { it.price }

println("총 고가 종목 평가액: $totalValue")
```

### 6-3. 코틀린 컬렉션을 자바에 넘길 때

```kotlin
// 코틀린에서 반환
fun getWatchList(): List<String> = listOf("005930", "000660", "035720")
```

```java
// 자바에서 받아서 수정 시도
List<String> watchList = KotlinServiceKt.getWatchList();

// 이게 실패할 수도 있다!
// listOf()는 java.util.Arrays$ArrayList 반환 → add() 시 UnsupportedOperationException!
watchList.add("005380");  // ← 런타임 예외 발생

// 안전하게 받으려면 new ArrayList<>()로 복사
List<String> mutableList = new ArrayList<>(KotlinServiceKt.getWatchList());
mutableList.add("005380");  // ✅
```

### 6-4. 자바 배열과 코틀린

```kotlin
// 자바 배열 처리
fun processJavaArray(prices: Array<Double>): Double {
    return prices.average()  // 코틀린 확장 함수 사용 가능
}

// 기본형 배열 (성능 중요 시)
fun processIntArray(quantities: IntArray): Int {
    return quantities.sum()  // IntArray = int[] (박싱 없음)
}

// 코틀린 → 자바 배열 변환
val stockCodes: List<String> = listOf("005930", "000660")
val javaArray: Array<String> = stockCodes.toTypedArray()  // String[]
```

---

## 7. SAM 변환 (Single Abstract Method)

### 7-1. 자바 함수형 인터페이스를 람다로

```java
// 레거시 자바 FIX 엔진 — 콜백 기반 설계
public interface OrderCallback {
    void onOrderAccepted(String orderId, String stockCode);
    // (다른 메서드 없음 — Single Abstract Method)
}

public class FixOrderEngine {
    public void submitOrder(String stockCode, int qty, OrderCallback callback) {
        // 거래소에 주문 전송
        String orderId = generateOrderId();
        callback.onOrderAccepted(orderId, stockCode);
    }
}
```

```kotlin
// 코틀린에서 람다로 자바 SAM 인터페이스 사용 (SAM 변환)
val engine = FixOrderEngine()

// 람다가 OrderCallback으로 자동 변환됨
engine.submitOrder("005930", 100) { orderId, stockCode ->
    println("주문 접수됨: orderId=$orderId, stock=$stockCode")
    // 이후 처리 로직
}

// 명시적 SAM 생성자도 가능
val callback = OrderCallback { orderId, stockCode ->
    logOrderAccepted(orderId, stockCode)
}
engine.submitOrder("000660", 50, callback)
```

### 7-2. 코틀린 인터페이스 — SAM 변환 주의

```kotlin
// 코틀린 인터페이스 — 자바에서 SAM 변환 안 됨 (Kotlin 1.4 이전)
interface PriceChangeListener {
    fun onPriceChanged(stockCode: String, newPrice: java.math.BigDecimal)
}

// Kotlin 1.4+ 에서는 fun interface로 선언하면 자바에서도 SAM 변환 가능
fun interface PriceChangeListener {
    fun onPriceChanged(stockCode: String, newPrice: java.math.BigDecimal)
}
```

```java
// fun interface 선언 시 자바에서도 람다 사용 가능
PriceChangeListener listener = (stockCode, price) -> {
    System.out.println(stockCode + ": " + price);
};
```

---

## 8. 체크 예외(Checked Exception) 차이

자바에는 **체크 예외(Checked Exception)**가 있다. 컴파일러가 `throws`를 강제하고, 호출부에서 `try-catch`나 `throws` 선언을 요구한다. 코틀린은 체크 예외를 **구분하지 않는다** — 모든 예외가 언체크(Unchecked)처럼 취급된다.

### 8-1. 자바 체크 예외를 코틀린에서

```java
// 레거시 자바 — 체크 예외 선언
public class LegacyDatabaseService {
    public Account findAccount(String id) throws SQLException, AccountNotFoundException {
        // DB 조회 로직
    }
}
```

```kotlin
// 코틀린에서 호출 — try-catch 선택 사항 (강제 아님)
val dbService = LegacyDatabaseService()

// try-catch 없어도 컴파일됨 (코틀린은 체크 예외 강제 안 함)
val account = dbService.findAccount("C001")  // ← SQLException이 발생하면 런타임에 전파

// 하지만 명시적으로 처리하는 것이 실무에서 올바름
val account = try {
    dbService.findAccount("C001")
} catch (e: java.sql.SQLException) {
    logger.error("DB 조회 실패: accountId=C001", e)
    throw InfrastructureException("계좌 조회 중 오류가 발생했습니다", e)
} catch (e: AccountNotFoundException) {
    throw BusinessException("계좌를 찾을 수 없습니다: C001")
}
```

### 8-2. 코틀린 함수에서 체크 예외 선언 — @Throws

자바 코드가 코틀린 함수를 호출할 때, 코틀린 함수가 체크 예외를 던질 수 있음을 알리려면 `@Throws` 어노테이션이 필요하다.

```kotlin
import kotlin.jvm.Throws

class KotlinOrderService {

    // @Throws 없이 — 자바에서 SQLException 체크 불필요 (위험)
    fun findOrder(orderId: Long): Order {
        // SQL 예외 발생 가능
        return repository.findById(orderId)
            ?: throw java.sql.SQLException("주문을 찾을 수 없습니다: $orderId")
    }

    // @Throws 있음 — 자바 호출부에서 체크 예외 처리 강제
    @Throws(java.sql.SQLException::class, OrderNotFoundException::class)
    fun findOrderSafe(orderId: Long): Order {
        return repository.findById(orderId)
            ?: throw OrderNotFoundException("주문을 찾을 수 없습니다: $orderId")
    }
}
```

```java
// 자바 레거시 코드에서 호출
KotlinOrderService service = new KotlinOrderService();

// @Throws 없는 함수 — 자바 컴파일러가 체크 예외를 모름 → try-catch 없어도 컴파일
Order o1 = service.findOrder(1L);  // ← 런타임에 예외 전파될 수 있음

// @Throws 있는 함수 — 자바 컴파일러가 체크 예외 처리 강제
try {
    Order o2 = service.findOrderSafe(1L);
} catch (SQLException e) {
    // 강제로 처리
} catch (OrderNotFoundException e) {
    // 강제로 처리
}
```

---

## 9. 실무 함정 목록 — 레거시 연동 시 자주 만나는 문제

### 9-1. 함정 1: 플랫폼 타입 NPE

```kotlin
// ❌ 위험 패턴
val order = legacyService.getOrder(id)  // Order! (플랫폼 타입)
println(order.status)  // null이면 NPE

// ✅ 안전 패턴
val order: Order? = legacyService.getOrder(id)
println(order?.status ?: "알 수 없음")
```

### 9-2. 함정 2: 자바 컬렉션 수정 불가

```kotlin
// ❌ 자바 Arrays.asList() 반환 — 크기 변경 불가
val list: java.util.List<String> = java.util.Arrays.asList("A", "B")
// list.add("C")  // UnsupportedOperationException!

// ✅ ArrayList로 복사
val mutableList = ArrayList(list)
mutableList.add("C")
```

### 9-3. 함정 3: data class와 자바 직렬화

```kotlin
// 자바 직렬화 사용 레거시 시스템과 연동 시
data class LegacyOrder(
    val id: Long,
    val amount: java.math.BigDecimal
) : java.io.Serializable {  // Serializable 구현 필요
    companion object {
        @JvmField
        val serialVersionUID: Long = 1L  // 명시적 선언 필수
    }
}
```

### 9-4. 함정 4: Kotlin Unit vs Java void

```kotlin
// 코틀린 Unit 반환 함수
fun updateOrderStatus(orderId: Long, status: OrderStatus) {
    // Unit 반환 (void와 유사)
}
```

```java
// 자바에서 받으면 Unit 객체가 반환됨
Unit result = KotlinServiceKt.updateOrderStatus(1L, OrderStatus.FILLED);
// 보통 void처럼 취급하면 됨:
KotlinServiceKt.updateOrderStatus(1L, OrderStatus.FILLED);  // ✅ 반환값 무시
```

### 9-5. 함정 5: 코틀린 기본 타입의 null 처리

```kotlin
// 코틀린 Int → JVM int (기본형)
// 코틀린 Int? → JVM Integer (박싱형)

fun getQuantity(): Int = 100         // Java: int (기본형)
fun getQuantityNullable(): Int? = null  // Java: Integer (박싱, null 가능)
```

```java
// 자바에서
int qty = service.getQuantity();           // int 기본형 — OK
Integer nullableQty = service.getQuantityNullable();  // Integer 박싱형 — null 가능
if (nullableQty != null) {
    System.out.println(nullableQty.intValue());
}
```

### 9-6. 함정 6: 코틀린 확장 함수의 자바 표현

```kotlin
// 코틀린 확장 함수
fun Order.isHighValue(): Boolean = totalAmount() > java.math.BigDecimal("10000000")
```

```java
// 자바에서는 정적 메서드로 접근
import com.securities.order.OrderExtensionsKt;

Order order = new Order(...);
boolean highValue = OrderExtensionsKt.isHighValue(order);  // 확장 함수 → static 메서드
```

---

## 10. 상호운용 모범 사례 체크리스트

```
코틀린에서 자바 호출 시:
[ ] 자바 메서드 반환값은 항상 nullable(?)로 먼저 받는다
[ ] @Nullable/@NotNull 어노테이션을 자바 소스에 추가할 수 있는지 확인
[ ] 소스 수정 불가 시 래퍼 클래스로 경계를 명확히 한다
[ ] 자바 컬렉션을 받으면 방어적 복사(toList(), toMutableList())를 고려한다
[ ] 자바 체크 예외는 코틀린에서도 명시적으로 처리한다

자바에서 코틀린 호출 시:
[ ] companion object 내 함수는 @JvmStatic을 붙인다
[ ] 자바에서 람다로 쓸 코틀린 인터페이스는 fun interface로 선언한다
[ ] 기본 인자가 있는 함수에 @JvmOverloads를 붙인다
[ ] 자바에서 체크 예외로 처리해야 할 코틀린 예외는 @Throws로 선언한다
[ ] object(싱글톤)는 .INSTANCE를 통해 접근한다

공통:
[ ] 플랫폼 타입 경계에서는 절대 컴파일러를 맹신하지 않는다
[ ] 빌드 설정에 -Xjsr305=strict를 추가하여 null 어노테이션 엄격 처리
[ ] 새 코드는 코틀린으로, 레거시와의 경계는 Adapter 클래스로 격리
```

---

## 11. 실전 예제 — 레거시 FIX 엔진 + 코틀린 서비스 연동

```kotlin
// 레거시 자바 FIX 엔진을 코틀린 서비스에서 안전하게 사용하는 전체 패턴

class FixOrderGateway(
    private val fixEngine: FixOrderEngine  // 레거시 자바 객체
) {
    private val logger = LoggerFactory.getLogger(FixOrderGateway::class.java)

    fun submitOrder(
        order: Order,
        onAccepted: (String) -> Unit,  // 코틀린 함수 타입 (SAM으로 전달)
        onRejected: (String) -> Unit
    ) {
        // 플랫폼 타입 안전 처리
        val stockCode: String = order.stockCode  // non-null 보장됨 (우리가 생성한 객체)

        try {
            // SAM 변환 — 람다 → OrderCallback 자동 변환
            fixEngine.submitOrder(stockCode, order.quantity) { orderId, code ->
                // null 방어 (플랫폼 타입 경계)
                val safeOrderId = orderId ?: run {
                    logger.error("FIX 엔진이 null orderId 반환: stock=$code")
                    onRejected("주문 ID를 받지 못했습니다")
                    return@submitOrder
                }
                logger.info("주문 접수: orderId=$safeOrderId, stock=$code")
                onAccepted(safeOrderId)
            }
        } catch (e: java.io.IOException) {
            logger.error("FIX 연결 오류", e)
            onRejected("거래소 연결 오류")
        } catch (e: Exception) {
            logger.error("예상치 못한 오류", e)
            onRejected("시스템 오류")
        }
    }
}
```

---

이전: [17. JVM 동작 원리](17-jvm-fundamentals.md) · 다음: — · [전체 커리큘럼](../CURRICULUM.md)
