# 29. 코루틴 완전정복

> 코루틴(Coroutine)은 "스레드보다 가볍고, 콜백보다 읽기 쉬운" 비동기 프로그래밍 모델이다. 증권 시스템에서 실시간 시세를 수신하고, 수천 건의 주문을 동시에 처리하면서도 코드가 동기 코드처럼 읽히는 것이 목표다. 이 문서는 코루틴의 원리부터 Flow, 채널, 스프링 연동까지 실무 수준으로 다룬다.

---

## 1. 코루틴이 스레드와 다른 점

### 1.1 스레드의 한계

```
[10,000 동시 주문 처리]

스레드 방식:
  스레드 1 ──── [주문처리] ──── [DB대기 300ms] ──── [응답] ──→
  스레드 2 ──── [주문처리] ──── [DB대기 300ms] ──── [응답] ──→
  ...
  스레드 10,000 ──── [주문처리] ──── [DB대기 300ms] ──── [응답] ──→

  문제: 각 스레드 ~1MB 스택 → 10,000 스레드 = 10GB RAM
  DB 대기 중에도 스레드가 OS 스케줄러에 묶여 자원 낭비
```

### 1.2 코루틴의 해법: suspend/resume

코루틴은 **중단점(suspension point)**에서 스레드를 반환하고, 재개(resume) 시 다시 스레드를 얻는다. 스레드는 다른 코루틴을 실행할 수 있으므로 훨씬 적은 스레드로 많은 작업을 처리한다.

```
[10,000 동시 주문 처리 — 코루틴 방식]

스레드-1: [주문A처리] → suspend(DB대기) → [주문B처리] → suspend(DB대기) → [주문A재개] ...
스레드-2: [주문C처리] → suspend(DB대기) → [주문D처리] → ...
스레드-3: [주문E처리] → ...

  10,000 코루틴이 8개 스레드를 돌아가며 사용
  DB 대기 중에는 스레드를 반환 → 다른 코루틴 실행
  메모리: 코루틴 하나 ~수KB (스레드의 1/100~1/1000)
```

### 1.3 CPS 변환(Continuation-Passing Style) 개념

`suspend` 함수는 컴파일 시 **Continuation** 객체를 매개변수로 받는 형태로 변환된다. Continuation은 "이 지점 이후의 나머지 코드"를 담은 콜백 객체다.

```kotlin
// 개발자가 쓰는 코드
suspend fun fetchPrice(symbol: String): BigDecimal {
    val result = dbQuery(symbol)  // suspend point
    return result.price
}

// 컴파일러가 변환하는 개념적 형태 (실제 바이트코드는 더 복잡함)
fun fetchPrice(symbol: String, continuation: Continuation<BigDecimal>): Any {
    // 상태 머신(state machine)으로 변환됨
    // label 0: dbQuery 호출, 완료되면 continuation.resume() 호출
    // label 1: result.price 반환
    return COROUTINE_SUSPENDED  // 아직 결과 없음, 나중에 재개
}
```

> **핵심**: `suspend`는 새 스레드를 만들지 않는다. 현재 스레드를 **양보(yield)**하고 나중에 같은 또는 다른 스레드에서 재개될 뿐이다.

### 1.4 코루틴 vs 스레드 비교표

| 항목 | 스레드 | 코루틴 |
|------|--------|--------|
| 생성 비용 | ~수백μs, ~1MB 스택 | ~수μs, ~수KB |
| 전환 비용 | OS 컨텍스트 스위칭 (비쌈) | 함수 호출 수준 (저렴) |
| 블로킹 | 스레드 점유 | 스레드 반환 후 대기 |
| 취소 | 강제 종료 어려움 | 구조화된 취소 지원 |
| 코드 가독성 | 콜백 또는 Future 체인 | 동기 코드처럼 작성 |
| 동시 작업 수 | OS 제한 (~수천) | JVM 메모리 한도까지 (~수십만) |

---

## 2. CoroutineScope, CoroutineContext, Job

### 2.1 CoroutineContext

코루틴의 실행 환경을 정의하는 **불변 맵(Immutable Map)**. 여러 Element의 합집합으로 구성된다.

```kotlin
import kotlinx.coroutines.*

// CoroutineContext의 주요 구성 요소
val context: CoroutineContext =
    Dispatchers.IO +                          // 어떤 스레드에서 실행할지
    CoroutineName("order-processor") +        // 디버깅용 이름
    Job() +                                   // 생명주기 관리
    CoroutineExceptionHandler { _, ex ->      // 예외 처리
        println("코루틴 예외: ${ex.message}")
    }

// 컨텍스트 요소 읽기
val job = context[Job]
val dispatcher = context[ContinuationInterceptor]
val name = context[CoroutineName]
```

### 2.2 Job: 생명주기 관리

```kotlin
import kotlinx.coroutines.*

fun main() = runBlocking {
    // launch는 Job을 반환
    val job: Job = launch {
        repeat(10) { i ->
            println("주문 처리 중: $i")
            delay(100)
        }
    }

    delay(350)

    // Job 상태 확인
    println("isActive: ${job.isActive}")       // true
    println("isCompleted: ${job.isCompleted}") // false
    println("isCancelled: ${job.isCancelled}") // false

    job.cancel()  // 취소 요청
    job.join()    // 완전히 끝날 때까지 대기
    println("isCompleted: ${job.isCompleted}") // true
    println("isCancelled: ${job.isCancelled}") // true
}
```

**Job 상태 전이:**
```
New ──start──→ Active ──complete──→ Completing ──→ Completed
                  │                                     ↑
                  └──cancel──→ Cancelling ─────────────┘
```

### 2.3 CoroutineScope

코루틴을 시작할 수 있는 범위(스코프). 스코프 안의 모든 코루틴은 스코프의 Job에 묶인다.

```kotlin
import kotlinx.coroutines.*
import java.math.BigDecimal

// 직접 스코프 생성 (서비스 계층에서 주로 사용)
class OrderService : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext =
        Dispatchers.IO + job + CoroutineName("OrderService")

    fun startProcessing() {
        launch {
            processOrders()
        }
    }

    fun shutdown() {
        job.cancel()  // 모든 자식 코루틴 취소
    }

    private suspend fun processOrders() {
        // 주문 처리 로직
    }
}

// 스코프 빌더 함수
suspend fun fetchMultiplePrices(symbols: List<String>): Map<String, BigDecimal> =
    coroutineScope {  // 현재 스코프 상속, 자식 코루틴 관리
        val deferred = symbols.map { symbol ->
            async(Dispatchers.IO) {
                symbol to fetchPriceFromDB(symbol)
            }
        }
        deferred.awaitAll().toMap()
    }

suspend fun fetchPriceFromDB(symbol: String): BigDecimal {
    delay(50)  // DB 조회 시뮬레이션
    return BigDecimal("75000")
}
```

---

## 3. 구조화된 동시성(Structured Concurrency)

### 3.1 원칙

구조화된 동시성은 **코루틴의 생명주기가 스코프에 묶이는** 설계 원칙이다. 세 가지를 보장한다:
1. 부모 코루틴은 모든 자식이 완료될 때까지 완료되지 않는다
2. 부모가 취소되면 모든 자식도 취소된다
3. 자식의 예외는 부모로 전파된다 (SupervisorJob 제외)

```kotlin
import kotlinx.coroutines.*

suspend fun processOrder(orderId: String) = coroutineScope {
    println("주문 처리 시작: $orderId")

    // 세 작업이 병렬로 실행되지만, 모두 이 스코프에 묶임
    val balanceCheck = async { checkBalance(orderId) }
    val riskCheck = async { checkRisk(orderId) }
    val priceCheck = async { checkPrice(orderId) }

    // 모두 완료될 때까지 대기
    val balance = balanceCheck.await()
    val risk = riskCheck.await()
    val price = priceCheck.await()

    println("검증 완료 — 잔고: $balance, 리스크: $risk, 가격: $price")
    // 이 함수가 반환될 때 세 코루틴이 모두 완료됨이 보장됨
}

// 만약 riskCheck가 예외를 던지면:
// - balanceCheck, priceCheck 자동 취소
// - processOrder도 예외로 종료
// → 리소스 누수 없음

suspend fun checkBalance(orderId: String): Boolean { delay(30); return true }
suspend fun checkRisk(orderId: String): String { delay(50); return "LOW" }
suspend fun checkPrice(orderId: String): BigDecimal { delay(20); return BigDecimal("75000") }
```

### 3.2 부모-자식 Job 관계

```kotlin
import kotlinx.coroutines.*

fun main() = runBlocking {
    val parentJob = launch {
        val child1 = launch {
            delay(1000)
            println("자식1 완료")
        }
        val child2 = launch {
            delay(2000)
            println("자식2 완료")
        }
        println("부모: 자식 완료 대기 중")
        // 명시적 join() 없어도 자식이 모두 끝날 때까지 부모 대기
    }

    delay(500)
    parentJob.cancel()  // 부모 취소 → child1, child2 자동 취소
    parentJob.join()
    println("부모 취소 완료 — 자식들도 모두 취소됨")
}
```

---

## 4. 디스패처(Dispatchers)

디스패처는 코루틴이 **어떤 스레드(풀)에서 실행될지** 결정한다.

### 4.1 주요 디스패처

```kotlin
import kotlinx.coroutines.*
import java.math.BigDecimal

// Dispatchers.Default: CPU 집약적 작업
// - 스레드 수 = CPU 코어 수 (최소 2)
// - 정렬, 계산, JSON 파싱 등
suspend fun calculatePortfolioRisk(holdings: List<Holding>): BigDecimal =
    withContext(Dispatchers.Default) {
        holdings.fold(BigDecimal.ZERO) { acc, h ->
            acc + h.value.multiply(BigDecimal("0.05")) // 간단한 리스크 계산
        }
    }

// Dispatchers.IO: IO 대기 작업
// - 스레드 수 = 최대 64 (또는 코어 수, 더 큰 것)
// - DB 조회, 파일 읽기, HTTP 호출 등
suspend fun fetchOrderHistory(accountId: String): List<Order> =
    withContext(Dispatchers.IO) {
        // JDBC, 파일 I/O, RestTemplate 등 블로킹 호출
        database.query("SELECT * FROM orders WHERE account_id = ?", accountId)
    }

// Dispatchers.Main: UI 스레드 (Android/JavaFX)
// - 증권 서버 백엔드에서는 거의 사용 안 함

// Dispatchers.Unconfined: 첫 suspend point까지 현재 스레드, 이후 재개한 스레드
// - 특수한 경우 외에는 사용 자제

data class Holding(val symbol: String, val quantity: Int, val value: BigDecimal)
data class Order(val id: String, val symbol: String, val price: BigDecimal)
```

### 4.2 커스텀 디스패처

```kotlin
import kotlinx.coroutines.*
import java.util.concurrent.Executors

// 외부 체결소 통신 전용 스레드풀
val exchangeDispatcher = Executors.newFixedThreadPool(8) { runnable ->
    Thread(runnable, "exchange-connector-${System.nanoTime()}")
}.asCoroutineDispatcher()

// 계좌 원장 업데이트 — 순서 보장 필요
val ledgerDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "ledger-updater")
}.asCoroutineDispatcher()

suspend fun sendToExchange(order: Order): String =
    withContext(exchangeDispatcher) {
        // 체결소 REST/FIX 프로토콜 통신
        "체결번호-${System.currentTimeMillis()}"
    }

suspend fun updateLedger(accountId: String, amount: BigDecimal) =
    withContext(ledgerDispatcher) {
        // 단일 스레드 = 자동 직렬화, 락 불필요
        println("원장 업데이트: $accountId += $amount")
    }

// 종료 시 반드시 해제
fun shutdown() {
    (exchangeDispatcher as AutoCloseable).close()
    (ledgerDispatcher as AutoCloseable).close()
}
```

### 4.3 디스패처 선택 가이드

| 작업 유형 | 권장 디스패처 | 예시 |
|----------|------------|------|
| CPU 집약 | `Dispatchers.Default` | 포트폴리오 계산, 리스크 분석 |
| DB/네트워크 IO | `Dispatchers.IO` | JPA 쿼리, RestTemplate |
| 순서 보장 직렬화 | 단일 스레드 커스텀 | 원장 업데이트, 상태 기계 |
| 외부 시스템 격리 | 고정 크기 커스텀풀 | 체결소 통신 |

---

## 5. launch vs async

```kotlin
import kotlinx.coroutines.*
import java.math.BigDecimal

// launch: 결과 불필요한 "실행 후 망각(fire-and-forget)" 작업 → Job 반환
fun main() = runBlocking {
    val job: Job = launch {
        sendOrderConfirmationEmail("ORD-001")  // 결과 필요 없음
    }
    // job.join() 없으면 부모 완료 시 취소될 수 있음
    job.join()

    // async: 결과가 필요한 병렬 작업 → Deferred<T> 반환
    val priceDeferred: Deferred<BigDecimal> = async {
        fetchPriceFromDB("005930")
    }
    val quantityDeferred: Deferred<Int> = async {
        fetchAvailableQuantity("005930")
    }

    // 두 작업이 병렬로 실행됨
    val price = priceDeferred.await()    // 완료 대기 + 결과 수신
    val quantity = quantityDeferred.await()

    println("시세: $price, 수량: $quantity")
    println("주문 가능 금액: ${price.multiply(BigDecimal(quantity))}")
}

suspend fun sendOrderConfirmationEmail(orderId: String) {
    delay(200)
    println("$orderId 확인 메일 발송 완료")
}

suspend fun fetchAvailableQuantity(symbol: String): Int {
    delay(30)
    return 1000
}
```

**함정: async 예외 처리**

```kotlin
// 잘못된 패턴: await() 전에 예외가 전파되지 않아 놓치기 쉬움
val deferred = async {
    throw RuntimeException("시세 조회 실패")  // launch와 달리 즉시 전파 안 됨
}
try {
    deferred.await()  // 여기서 예외 발생
} catch (e: RuntimeException) {
    println("예외 처리: ${e.message}")
}

// 안전한 패턴: coroutineScope으로 감싸 예외 자동 전파
coroutineScope {
    val d1 = async { fetchPriceFromDB("005930") }
    val d2 = async { throw RuntimeException("조회 실패") }
    // d2 예외 발생 → d1 자동 취소 → coroutineScope 예외로 종료
    awaitAll(d1, d2)
}
```

---

## 6. withContext

현재 코루틴을 **다른 컨텍스트(디스패처)로 전환**하고, 블록 완료 후 원래 컨텍스트로 복귀한다. `async { }.await()`의 더 효율적인 버전.

```kotlin
import kotlinx.coroutines.*
import java.math.BigDecimal

suspend fun processOrderFull(orderId: String): OrderResult {
    // 1. IO 스레드에서 DB 조회
    val order = withContext(Dispatchers.IO) {
        database.findOrder(orderId)  // 블로킹 JDBC 호출
    }

    // 2. Default 스레드에서 CPU 집약 계산
    val risk = withContext(Dispatchers.Default) {
        calculateRiskScore(order)  // 복잡한 수치 계산
    }

    // 3. 다시 IO 스레드에서 저장
    withContext(Dispatchers.IO) {
        database.saveRiskScore(orderId, risk)
    }

    return OrderResult(orderId, risk)
}

// 안티패턴: withContext를 남발하면 불필요한 컨텍스트 전환 비용
// suspend 함수는 이미 내부에서 적절한 디스패처를 사용해야 함
// 호출자가 매번 withContext로 감쌀 필요 없음

data class OrderResult(val orderId: String, val riskScore: Double)

// 가짜 DB, 실제 구현 아님
object database {
    fun findOrder(id: String) = Order(id, "005930", BigDecimal("75000"))
    fun saveRiskScore(id: String, score: Double) = Unit
}

fun calculateRiskScore(order: Order): Double = 0.05
```

---

## 7. 취소(Cancellation)와 협조적 취소

### 7.1 협조적 취소(Cooperative Cancellation)

코루틴 취소는 **협조적**이다. 코루틴이 취소 신호를 스스로 확인해야 한다. 강제 종료가 아니라 정상 종료 흐름을 따른다.

```kotlin
import kotlinx.coroutines.*

// suspend 함수 안의 delay(), yield() 등은 취소 지점(cancellation point)
val job = launch {
    repeat(100) { i ->
        delay(100)  // 취소 가능 지점 — 취소 요청 오면 CancellationException 발생
        println("처리 중: $i")
    }
}

delay(350)
job.cancel()  // 취소 요청
job.join()    // 완료 대기
println("완료")

// CPU 집약 작업에서 취소 확인
val heavyJob = launch(Dispatchers.Default) {
    var result = BigDecimal.ZERO
    for (i in 1..1_000_000) {
        // delay()가 없으면 취소 신호를 못 받음!
        // 명시적으로 isActive 확인 필요
        if (!isActive) {
            println("리스크 계산 취소됨, i=$i")
            return@launch
        }
        result += BigDecimal(i).multiply(BigDecimal("0.0001"))
    }
    println("리스크 계산 완료: $result")
}
```

### 7.2 CancellationException과 리소스 정리

```kotlin
import kotlinx.coroutines.*

val job = launch {
    val connection = openDatabaseConnection()
    try {
        while (isActive) {
            val orders = connection.fetchPendingOrders()
            processOrders(orders)
            delay(1000)
        }
    } catch (e: CancellationException) {
        println("코루틴 취소됨 — CancellationException은 재던지기 권장")
        throw e  // ⚠️ 반드시 재던져야 구조화된 동시성이 올바로 동작
    } finally {
        connection.close()  // 취소되어도 finally는 실행됨 (리소스 정리)
        println("DB 연결 종료")
    }
}

// use() 패턴 (AutoCloseable 구현체)
val job2 = launch {
    openDatabaseConnection().use { conn ->
        // conn.close()는 블록 탈출 시 자동 호출
        processOrdersWith(conn)
    }
}

// 가짜 구현
class DatabaseConnection : AutoCloseable {
    fun fetchPendingOrders(): List<Order> = emptyList()
    override fun close() = println("연결 닫힘")
}
fun openDatabaseConnection() = DatabaseConnection()
fun processOrders(orders: List<Order>) = Unit
fun processOrdersWith(conn: DatabaseConnection) = Unit
```

### 7.3 타임아웃(Timeout)

```kotlin
import kotlinx.coroutines.*
import java.math.BigDecimal

// withTimeout: 초과 시 TimeoutCancellationException (CancellationException 서브클래스)
suspend fun fetchPriceWithTimeout(symbol: String): BigDecimal {
    return withTimeout(3000L) {  // 3초 초과 시 예외
        fetchPriceFromExternalAPI(symbol)
    }
}

// withTimeoutOrNull: 초과 시 null 반환
suspend fun fetchPriceSafely(symbol: String): BigDecimal? {
    return withTimeoutOrNull(3000L) {
        fetchPriceFromExternalAPI(symbol)
    } ?: run {
        println("시세 조회 타임아웃: $symbol")
        null
    }
}

// 재시도와 타임아웃 조합
suspend fun fetchPriceWithRetry(symbol: String, maxRetries: Int = 3): BigDecimal {
    repeat(maxRetries) { attempt ->
        try {
            return withTimeout(2000L) {
                fetchPriceFromExternalAPI(symbol)
            }
        } catch (e: TimeoutCancellationException) {
            println("시도 ${attempt + 1} 타임아웃, 재시도 중...")
            if (attempt == maxRetries - 1) throw e
            delay(500L * (attempt + 1))  // 지수 백오프
        }
    }
    throw IllegalStateException("최대 재시도 초과")
}

suspend fun fetchPriceFromExternalAPI(symbol: String): BigDecimal {
    delay(1500)  // 외부 API 호출 시뮬레이션
    return BigDecimal("75000")
}
```

---

## 8. 예외 처리

### 8.1 launch vs async 예외 전파 차이

```kotlin
import kotlinx.coroutines.*

fun main() = runBlocking {
    // launch: 예외가 즉시 부모로 전파 (CoroutineExceptionHandler 또는 부모 취소)
    val handler = CoroutineExceptionHandler { _, exception ->
        println("launch 예외 처리: ${exception.message}")
    }
    val job = launch(handler) {
        throw RuntimeException("주문 처리 실패")
    }
    job.join()

    // async: await() 호출 시점에 예외 전파
    val deferred = async {
        throw RuntimeException("시세 조회 실패")
    }
    try {
        deferred.await()
    } catch (e: RuntimeException) {
        println("async 예외 처리: ${e.message}")
    }
}
```

### 8.2 SupervisorJob: 자식 실패가 형제에 전파되지 않도록

```kotlin
import kotlinx.coroutines.*
import java.math.BigDecimal

// 일반 Job: 자식 하나 실패 → 다른 자식도 취소
// SupervisorJob: 자식 독립 — 하나 실패해도 나머지 계속 실행

class PriceMonitorService : CoroutineScope {
    private val supervisorJob = SupervisorJob()
    override val coroutineContext = Dispatchers.IO + supervisorJob

    fun startMonitoring(symbols: List<String>) {
        symbols.forEach { symbol ->
            // 각 종목 모니터링이 독립적으로 실패/재시작 가능
            launch {
                try {
                    while (isActive) {
                        val price = fetchPriceFromExternalAPI(symbol)
                        updatePriceCache(symbol, price)
                        delay(1000)
                    }
                } catch (e: Exception) {
                    println("$symbol 시세 오류: ${e.message} — 다른 종목에 영향 없음")
                    // SupervisorJob이면 이 자식만 죽고 나머지는 계속 실행
                }
            }
        }
    }

    fun stop() = supervisorJob.cancel()

    private fun updatePriceCache(symbol: String, price: BigDecimal) {
        println("캐시 업데이트: $symbol = $price")
    }
}

// supervisorScope: 함수 단위 SupervisorJob 컨텍스트
suspend fun fetchAllPricesSafely(symbols: List<String>): Map<String, BigDecimal?> {
    return supervisorScope {
        symbols.associateWith { symbol ->
            async {
                try { fetchPriceFromExternalAPI(symbol) }
                catch (e: Exception) { null }  // 개별 실패 → null 반환
            }
        }.mapValues { (_, deferred) -> deferred.await() }
    }
}
```

### 8.3 CoroutineExceptionHandler

```kotlin
import kotlinx.coroutines.*

val exceptionHandler = CoroutineExceptionHandler { context, exception ->
    val coroutineName = context[CoroutineName]?.name ?: "unnamed"
    when (exception) {
        is NetworkException -> println("[$coroutineName] 네트워크 오류: ${exception.message}")
        is TimeoutCancellationException -> println("[$coroutineName] 타임아웃")
        is CancellationException -> { /* 정상 취소, 로깅 불필요 */ }
        else -> println("[$coroutineName] 예상치 못한 오류: ${exception.message}")
    }
    // 여기서 메트릭 기록, 알림 발송 등 수행
}

// CoroutineExceptionHandler는 최상위 launch에서만 동작
// (async나 자식 코루틴에서는 무시됨 — await()에서 처리해야 함)
val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)
scope.launch(CoroutineName("order-processor")) {
    throw NetworkException("체결소 연결 실패")
}

class NetworkException(message: String) : Exception(message)
```

---

## 9. Flow 심화

### 9.1 Cold Flow vs Hot Flow

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal

// Cold Flow: collect 할 때마다 새로 시작, 구독자마다 독립 실행
fun priceTickerCold(symbol: String): Flow<BigDecimal> = flow {
    println("Cold Flow 시작: $symbol")  // collect 호출마다 실행
    while (true) {
        emit(fetchPriceFromExternalAPI(symbol))
        delay(1000)
    }
}

// Hot Flow: 구독자 수에 무관하게 항상 실행
// StateFlow, SharedFlow가 대표적

// StateFlow: 항상 최신 값 하나를 보유하는 상태 홀더
val currentPriceFlow = MutableStateFlow(BigDecimal("75000"))

// SharedFlow: 여러 구독자에게 이벤트 방송
val tradeEventFlow = MutableSharedFlow<TradeEvent>(
    replay = 10,           // 새 구독자에게 최근 10개 이벤트 재생
    extraBufferCapacity = 100,  // 느린 구독자를 위한 버퍼
    onBufferOverflow = BufferOverflow.DROP_OLDEST  // 버퍼 꽉 차면 오래된 것 삭제
)

data class TradeEvent(val symbol: String, val price: BigDecimal, val quantity: Int)
```

### 9.2 Flow 연산자

```kotlin
import kotlinx.coroutines.flow.*
import java.math.BigDecimal

// 실시간 시세 처리 파이프라인
fun marketDataPipeline(symbol: String): Flow<PriceAlert> {
    return priceTickerCold(symbol)
        .filter { price -> price > BigDecimal("70000") }  // 기준가 이상만
        .map { price ->
            PriceAlert(symbol, price, price > BigDecimal("80000"))
        }
        .distinctUntilChanged { old, new ->              // 동일 가격 중복 제거
            old.price == new.price
        }
        .debounce(500L)                                  // 500ms 내 마지막 값만
        .catch { e ->                                    // 예외 처리
            println("시세 스트림 오류: ${e.message}")
            emit(PriceAlert(symbol, BigDecimal.ZERO, false))
        }
        .onEach { alert ->                               // 부수 효과 (로깅, 메트릭)
            println("알림 생성: $alert")
        }
        .flowOn(Dispatchers.IO)                          // 업스트림을 IO 스레드에서
}

// buffer: 생산자/소비자 속도 차이 완화
fun bufferedPipeline(symbol: String): Flow<BigDecimal> =
    priceTickerCold(symbol)
        .buffer(capacity = 64)  // 생산자가 소비자보다 빠를 때 버퍼링

// conflate: 처리가 늦으면 최신 값만 유지 (중간 값 버림)
fun conflatedPipeline(symbol: String): Flow<BigDecimal> =
    priceTickerCold(symbol)
        .conflate()  // 소비가 늦으면 최신 시세만 처리

data class PriceAlert(val symbol: String, val price: BigDecimal, val isHigh: Boolean)
```

### 9.3 StateFlow와 SharedFlow: 실시간 시세 스트림

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal

class MarketDataService(private val scope: CoroutineScope) {

    // StateFlow: 현재 시세 — 새 구독자는 즉시 최신 값 받음
    private val _prices = MutableStateFlow(mapOf<String, BigDecimal>())
    val prices: StateFlow<Map<String, BigDecimal>> = _prices.asStateFlow()

    // SharedFlow: 체결 이벤트 — 구독 이후 발생한 이벤트만 받음
    private val _tradeEvents = MutableSharedFlow<TradeEvent>()
    val tradeEvents: SharedFlow<TradeEvent> = _tradeEvents.asSharedFlow()

    fun startReceiving() {
        scope.launch {
            // 외부 시세 수신 (WebSocket 등)
            receiveMarketData().collect { (symbol, price) ->
                // StateFlow 업데이트
                _prices.update { current ->
                    current + (symbol to price)
                }
            }
        }

        scope.launch {
            // 외부 체결 수신
            receiveTradeData().collect { event ->
                _tradeEvents.emit(event)  // 모든 구독자에게 방송
            }
        }
    }

    // 특정 종목 시세 변화만 추적
    fun watchSymbol(symbol: String): Flow<BigDecimal> =
        prices.map { it[symbol] ?: BigDecimal.ZERO }
              .distinctUntilChanged()

    // 모의 외부 데이터 수신
    private fun receiveMarketData(): Flow<Pair<String, BigDecimal>> = flow {
        while (true) {
            emit("005930" to BigDecimal("75000").plus(BigDecimal(Math.random() * 1000)))
            delay(100)
        }
    }

    private fun receiveTradeData(): Flow<TradeEvent> = flow {
        while (true) {
            delay(500)
            emit(TradeEvent("005930", BigDecimal("75100"), 10))
        }
    }
}

// 구독자 예시
suspend fun MonitorPortfolio(service: MarketDataService) = coroutineScope {
    // 삼성전자 시세 모니터
    launch {
        service.watchSymbol("005930")
            .take(10)  // 10개만 수신
            .collect { price ->
                println("삼성전자 현재가: $price")
            }
    }

    // 체결 이벤트 수신
    launch {
        service.tradeEvents
            .filter { it.symbol == "005930" }
            .collect { event ->
                println("체결: ${event.symbol} ${event.price} x ${event.quantity}")
            }
    }
}
```

### 9.4 Backpressure 처리

```kotlin
import kotlinx.coroutines.flow.*

// 생산자가 소비자보다 빠른 경우
val fastProducer: Flow<Int> = flow {
    var i = 0
    while (true) {
        emit(i++)
        // delay 없음 — 매우 빠른 생산
    }
}

// 전략 1: buffer — 큐에 저장
fastProducer.buffer(1000).collect { value ->
    Thread.sleep(10)  // 느린 처리
    println(value)
}

// 전략 2: conflate — 최신 값만 처리 (중간 값 손실 허용)
fastProducer.conflate().collect { value ->
    Thread.sleep(10)
    println("최신 값: $value")  // 많은 값을 건너뜀
}

// 전략 3: collectLatest — 새 값 오면 이전 처리 취소
fastProducer.collectLatest { value ->
    delay(10)  // 취소 가능한 suspend 함수
    println("처리 완료: $value")  // 마지막으로 완료된 것만 출력
}
```

---

## 10. 채널(Channel)

### 10.1 채널의 특징

Flow가 선언적(declarative) 파이프라인이라면, Channel은 **명령적(imperative) 스트림**이다. 생산자와 소비자가 독립적으로 동작하며, Channel은 둘 사이의 큐 역할을 한다.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.math.BigDecimal

// 주문 처리 채널
fun main() = runBlocking {
    val orderChannel = Channel<Order>(capacity = 100)  // 버퍼 크기 100

    // 생산자: 주문 수신
    val producer = launch {
        repeat(10) { i ->
            val order = Order("ORD-$i", "005930", BigDecimal("75000"))
            orderChannel.send(order)  // 꽉 차면 suspend
            println("주문 전송: ${order.id}")
        }
        orderChannel.close()  // 생산 완료 신호
    }

    // 소비자: 주문 처리
    val consumer = launch {
        for (order in orderChannel) {  // close()까지 반복
            delay(50)  // 처리 시뮬레이션
            println("주문 처리 완료: ${order.id}")
        }
        println("모든 주문 처리 완료")
    }

    joinAll(producer, consumer)
}
```

### 10.2 채널 용량 모드

```kotlin
import kotlinx.coroutines.channels.*

// UNLIMITED: 무제한 버퍼 (메모리 주의)
val unlimitedChannel = Channel<Order>(Channel.UNLIMITED)

// CONFLATED: 최신 값만 유지 (시세 최신화에 적합)
val conflatedChannel = Channel<BigDecimal>(Channel.CONFLATED)

// RENDEZVOUS (기본, 0): 생산자와 소비자가 동시 준비될 때만 전달
val rendezvousChannel = Channel<Order>()  // = Channel(0)

// BUFFERED: 기본 크기 64
val bufferedChannel = Channel<Order>(Channel.BUFFERED)
```

### 10.3 produce와 actor 빌더

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

// produce: 코루틴 스코프에서 채널 생산자 생성
fun CoroutineScope.orderProducer(count: Int): ReceiveChannel<Order> = produce {
    repeat(count) { i ->
        send(Order("ORD-$i", "005930", BigDecimal("75000")))
        delay(10)
    }
}

fun main() = runBlocking {
    val orders = orderProducer(5)

    // consumeEach: 채널 소비 편의 함수
    orders.consumeEach { order ->
        println("처리: ${order.id}")
    }
}
```

### 10.4 Flow vs Channel 선택

| 상황 | 권장 |
|------|------|
| 변환/필터 파이프라인 | Flow |
| 생산자-소비자 분리 | Channel |
| 상태 공유 | StateFlow |
| 이벤트 방송 | SharedFlow |
| 팬아웃(여러 소비자) | Channel + multiple collect |
| 하나의 소비자 | Flow 또는 Channel 모두 가능 |

---

## 11. 블로킹 코드 안전하게 감싸기

### 11.1 문제: suspend 함수 안에서 블로킹 IO

```kotlin
import kotlinx.coroutines.*

// ❌ 위험: Dispatchers.Main 또는 Default에서 JDBC 호출
suspend fun dangerousFetch(id: String): Order {
    return jdbcTemplate.queryForObject(  // 블로킹 JDBC!
        "SELECT * FROM orders WHERE id = ?",
        Order::class.java, id
    )  // Dispatchers.Default 스레드를 블록 → 다른 코루틴 처리 못 함
}

// ✅ 안전: IO 디스패처로 전환
suspend fun safeFetch(id: String): Order = withContext(Dispatchers.IO) {
    jdbcTemplate.queryForObject(
        "SELECT * FROM orders WHERE id = ?",
        Order::class.java, id
    )
}

// ✅ 안전: runInterruptible — 인터럽트 가능한 블로킹 코드
suspend fun safeBlockingFetch(id: String): Order = withContext(Dispatchers.IO) {
    runInterruptible {
        // Thread.currentThread().interrupt()로 취소 가능한 블로킹 코드
        legacyBlockingClient.fetchOrder(id)
    }
}
```

### 11.2 Reactor/RxJava 코드를 코루틴으로

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.rx2.await

// Spring WebFlux Mono/Flux → suspend
suspend fun fetchReactivePrice(symbol: String): BigDecimal {
    val mono: reactor.core.publisher.Mono<BigDecimal> = priceReactiveService.getPrice(symbol)
    return mono.awaitSingle()  // kotlinx-coroutines-reactor 라이브러리
}

// RxJava Single → suspend
suspend fun fetchRxPrice(symbol: String): BigDecimal {
    val single: io.reactivex.Single<BigDecimal> = priceRxService.getPrice(symbol)
    return single.await()  // kotlinx-coroutines-rx2 라이브러리
}
```

---

## 12. 스프링 WebFlux + 코루틴 연동

### 12.1 컨트롤러

```kotlin
import org.springframework.web.bind.annotation.*
import kotlinx.coroutines.flow.Flow

@RestController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {

    // suspend 함수 = 비동기 엔드포인트 (WebFlux 필요)
    @PostMapping
    suspend fun submitOrder(@RequestBody request: OrderRequest): OrderResponse {
        return orderService.processOrder(request)  // suspend 함수 직접 호출
    }

    // Flow<T> 반환 = Flux<T>로 자동 변환 (스트리밍 응답)
    @GetMapping("/stream/{symbol}", produces = ["text/event-stream"])
    fun streamPrices(@PathVariable symbol: String): Flow<PriceEvent> {
        return orderService.getPriceStream(symbol)
    }

    @GetMapping("/{id}")
    suspend fun getOrder(@PathVariable id: String): Order {
        return orderService.findOrder(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "주문 없음: $id")
    }
}

data class OrderRequest(val symbol: String, val quantity: Int, val side: String)
data class OrderResponse(val orderId: String, val status: String)
data class PriceEvent(val symbol: String, val price: BigDecimal, val timestamp: Long)
```

### 12.2 서비스 계층

```kotlin
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlinx.coroutines.flow.*

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val priceService: PriceService
) {
    // @Transactional + suspend: Spring이 코루틴 트랜잭션 지원
    @Transactional
    suspend fun processOrder(request: OrderRequest): OrderResponse {
        val price = priceService.getCurrentPrice(request.symbol)
        val order = Order(
            id = generateOrderId(),
            symbol = request.symbol,
            price = price,
            quantity = request.quantity
        )
        orderRepository.save(order)  // R2DBC (리액티브 DB 드라이버)
        return OrderResponse(order.id, "ACCEPTED")
    }

    suspend fun findOrder(id: String): Order? = orderRepository.findById(id)

    fun getPriceStream(symbol: String): Flow<PriceEvent> = flow {
        while (true) {
            val price = priceService.getCurrentPrice(symbol)
            emit(PriceEvent(symbol, price, System.currentTimeMillis()))
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)

    private fun generateOrderId() = "ORD-${System.currentTimeMillis()}"
}
```

### 12.3 R2DBC 리포지토리 (리액티브 DB)

```kotlin
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

// CoroutineCrudRepository: suspend 함수와 Flow를 기본 제공
interface OrderRepository : CoroutineCrudRepository<Order, String> {
    // suspend 반환 = 단건 조회
    suspend fun findBySymbolAndStatus(symbol: String, status: String): Order?

    // Flow 반환 = 다건 스트리밍
    fun findAllByAccountId(accountId: String): Flow<Order>

    // @Query로 커스텀 쿼리
    @Query("SELECT * FROM orders WHERE symbol = :symbol AND price > :minPrice")
    fun findHighPriceOrders(symbol: String, minPrice: BigDecimal): Flow<Order>
}
```

---

## 13. 흔한 안티패턴

### 13.1 GlobalScope 사용 금지

```kotlin
// ❌ 안티패턴: GlobalScope는 구조화된 동시성 깨뜨림
GlobalScope.launch {
    processOrder("ORD-001")
    // 서버 종료 시 이 코루틴이 계속 실행됨
    // 예외 전파 없음
    // 생명주기 추적 불가
}

// ✅ 올바른 방식: 스코프 주입 또는 coroutineScope 사용
class OrderProcessor(private val scope: CoroutineScope) {
    fun process(orderId: String) {
        scope.launch { processOrder(orderId) }
    }
}
```

### 13.2 suspend 함수 안에서 runBlocking 금지

```kotlin
// ❌ 안티패턴: suspend 함수 안에서 runBlocking
suspend fun badFetch(): BigDecimal {
    return runBlocking {  // 현재 스레드를 완전히 블록
        fetchPriceFromDB("005930")  // 코루틴 스케줄러가 멈출 수 있음
    }
}

// ✅ 그냥 suspend 함수 직접 호출
suspend fun goodFetch(): BigDecimal {
    return fetchPriceFromDB("005930")
}
```

### 13.3 Deferred 결과를 즉시 await하지 않기 (병렬성 손실)

```kotlin
// ❌ 순차 실행 (병렬성 없음)
suspend fun sequentialFetch(): Pair<BigDecimal, BigDecimal> = coroutineScope {
    val price1 = async { fetchPriceFromDB("005930") }.await()  // 여기서 블록
    val price2 = async { fetchPriceFromDB("000660") }.await()  // 이후 시작
    price1 to price2
}

// ✅ 병렬 실행
suspend fun parallelFetch(): Pair<BigDecimal, BigDecimal> = coroutineScope {
    val d1 = async { fetchPriceFromDB("005930") }  // 즉시 시작
    val d2 = async { fetchPriceFromDB("000660") }  // 즉시 시작
    d1.await() to d2.await()                        // 둘 다 시작된 후 대기
}
```

### 13.4 Flow에서 collect 중복 방지

```kotlin
// ❌ 안티패턴: Cold Flow를 여러 번 collect하면 각각 독립 실행
val priceFlow = priceTickerCold("005930")
launch { priceFlow.collect { /* 구독 1 */ } }  // DB 조회 1번
launch { priceFlow.collect { /* 구독 2 */ } }  // DB 조회 또 1번 (중복!)

// ✅ shareIn / stateIn으로 Hot Flow로 변환
val sharedPriceFlow = priceTickerCold("005930")
    .shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),  // 구독자 없으면 5초 후 중단
        replay = 1
    )

launch { sharedPriceFlow.collect { /* 구독 1 */ } }  // DB 조회 공유
launch { sharedPriceFlow.collect { /* 구독 2 */ } }  // 같은 데이터 공유
```

---

## 핵심 정리

- **suspend 함수**는 스레드를 블록하지 않고 중단·재개한다. 비동기 코드를 동기처럼 작성 가능.
- **구조화된 동시성**: 스코프 바깥으로 코루틴을 탈출시키지 말 것. GlobalScope, 날 Job 생성 금지.
- **디스패처**: CPU 작업 → Default, IO 작업 → IO, 순서 보장 → 단일 스레드.
- **예외**: launch는 즉시 전파, async는 await() 시 전파. SupervisorJob으로 독립 실패 허용.
- **Flow**: Cold는 매번 새로 실행, StateFlow/SharedFlow는 Hot. shareIn으로 변환 가능.
- **취소**: 협조적 취소. 긴 CPU 루프에서는 `isActive` 또는 `yield()` 명시적 확인 필요.

---

### 체크리스트

- [ ] `suspend` 함수를 직접 스레드에서 호출하면 어떻게 되는지 설명할 수 있다
- [ ] `launch`와 `async`의 예외 전파 차이를 설명하고 코드로 보일 수 있다
- [ ] `SupervisorJob`이 필요한 상황을 실제 예시로 설명할 수 있다
- [ ] `StateFlow`와 `SharedFlow`의 차이를 말할 수 있다
- [ ] `Flow.buffer()`, `.conflate()`, `.collectLatest()`의 차이를 설명할 수 있다
- [ ] 블로킹 JDBC 코드를 코루틴에서 안전하게 감싸는 방법을 안다
- [ ] `GlobalScope` 대신 어떤 스코프를 써야 하는지 답할 수 있다

---

이전: [28. JVM 동시성 깊게](28-jvm-concurrency.md) · 다음: [13. 코루틴 입문](13-coroutines.md) · [전체 커리큘럼](../CURRICULUM.md)
