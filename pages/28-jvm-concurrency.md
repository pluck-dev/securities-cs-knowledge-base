# 28. JVM 동시성 깊게

> 증권 시스템에서 초당 수만 건의 주문이 동시에 들어올 때, 잔고가 정확히 유지되려면 JVM이 동시성을 어떻게 다루는지 깊이 이해해야 한다. 이 문서는 스레드 생성부터 락프리(lock-free) 알고리즘까지, 실무에서 마주치는 동시성 문제와 해법을 코드로 설명한다.

---

## 1. 프로세스 vs 스레드 (Process vs Thread)

### 1.1 프로세스(Process)

운영체제가 프로그램을 실행할 때 할당하는 독립적인 메모리 공간과 자원의 단위다.

- 각 프로세스는 독립된 힙(Heap), 스택(Stack), 코드(Code), 데이터(Data) 세그먼트를 가진다.
- 프로세스 간 통신(IPC: Inter-Process Communication)은 소켓, 파이프, 공유 메모리 등 별도 메커니즘이 필요하다.
- 하나의 프로세스가 죽어도 다른 프로세스에 영향을 주지 않는다(고립성).

```
[프로세스 A: 주문서버]          [프로세스 B: 체결서버]
┌─────────────────────┐       ┌─────────────────────┐
│ Code  │ Data  │ Heap│       │ Code  │ Data  │ Heap│
│ Stack │ Stack │     │       │ Stack │ Stack │     │
└─────────────────────┘       └─────────────────────┘
         ↑                             ↑
    독립 메모리                    독립 메모리
         └──────── IPC(소켓) ──────────┘
```

### 1.2 스레드(Thread)

프로세스 내부에서 실행되는 경량 실행 단위다.

- 같은 프로세스의 스레드들은 **힙과 코드 세그먼트를 공유**한다.
- 각 스레드는 독립적인 **스택(Stack)**과 **프로그램 카운터(PC: Program Counter)**를 가진다.
- 메모리 공유 덕분에 스레드 간 통신이 빠르지만, 동시 접근 시 **경쟁 조건(Race Condition)**이 발생한다.

```
[JVM 프로세스: 거래시스템]
┌────────────────────────────────────────────────┐
│  공유 힙(Shared Heap)                           │
│  ┌────────────┐  ┌────────────┐  ┌──────────┐  │
│  │ 주문 객체  │  │ 잔고 객체  │  │ 시세 객체│  │
│  └────────────┘  └────────────┘  └──────────┘  │
│                                                │
│  스레드-1(Thread-1)    스레드-2(Thread-2)       │
│  ┌─────────────────┐   ┌─────────────────┐     │
│  │ 스택(Stack)     │   │ 스택(Stack)     │     │
│  │ PC              │   │ PC              │     │
│  └─────────────────┘   └─────────────────┘     │
└────────────────────────────────────────────────┘
```

### 1.3 증권 시스템에서의 의미

| 구분 | 프로세스 | 스레드 |
|------|---------|--------|
| 격리성 | 완전 격리 (장애 전파 없음) | 공유 메모리 (한 스레드 오류가 JVM 전체 영향 가능) |
| 통신 비용 | 높음 (IPC) | 낮음 (공유 메모리) |
| 생성 비용 | 높음 (~수십ms) | 낮음 (~수백μs) |
| 사용 예 | 주문서버 ↔ 체결서버 분리 배포 | 단일 서버 내 동시 주문 처리 |

---

## 2. 스레드 생성과 생명주기

### 2.1 Kotlin에서 스레드 생성

```kotlin
import java.math.BigDecimal

// 방법 1: Thread 클래스 직접 생성
val orderThread = Thread {
    println("주문 처리 중: ${Thread.currentThread().name}")
    processOrder("ORD-001", BigDecimal("50000"))
}
orderThread.name = "order-processor-1"
orderThread.start()

// 방법 2: thread { } 확장 함수 (kotlin.concurrent)
import kotlin.concurrent.thread

val balanceThread = thread(name = "balance-updater", isDaemon = false) {
    updateBalance("ACC-123", BigDecimal("1000000"))
}

// 방법 3: Runnable 구현 (자바 코드와 연동 시)
val task = Runnable {
    println("시세 업데이트 처리")
}
Thread(task, "market-data-updater").start()

fun processOrder(orderId: String, price: BigDecimal) {
    Thread.sleep(100) // 주문 처리 시뮬레이션
    println("주문 $orderId 처리 완료: $price")
}

fun updateBalance(accountId: String, amount: BigDecimal) {
    Thread.sleep(50)
    println("잔고 $accountId 업데이트: $amount")
}
```

### 2.2 스레드 생명주기(Thread Lifecycle)

```
NEW ──start()──→ RUNNABLE ──CPU 할당──→ RUNNING
                    ↑                      │
                    │              sleep()/wait()/IO
                    │                      ↓
                    └────────── BLOCKED/WAITING/TIMED_WAITING
                                           │
                              작업완료/인터럽트
                                           ↓
                                       TERMINATED
```

| 상태 | 설명 | 증권 예시 |
|------|------|---------|
| NEW | Thread 객체 생성됐으나 start() 미호출 | 주문 처리 스레드 준비 |
| RUNNABLE | 실행 가능하거나 실행 중 | 주문 검증 로직 수행 |
| BLOCKED | 모니터 락 대기 | synchronized 잔고 업데이트 대기 |
| WAITING | Object.wait() / join() | 체결 결과 대기 |
| TIMED_WAITING | sleep(ms) / wait(ms) | 주문 타임아웃 대기 |
| TERMINATED | 실행 완료 또는 예외 | 주문 처리 완료 |

### 2.3 스레드 인터럽트(Interrupt)

```kotlin
val orderProcessor = thread {
    try {
        while (!Thread.currentThread().isInterrupted) {
            // 주문 큐에서 주문을 꺼내 처리
            val order = dequeueOrder() // 블로킹 호출일 수 있음
            processOrder(order)
        }
    } catch (e: InterruptedException) {
        // sleep(), wait() 등 블로킹 중 인터럽트 발생 시 여기로
        println("주문 처리 스레드 정상 종료")
        Thread.currentThread().interrupt() // 인터럽트 상태 복원 (모범 사례)
    }
}

// 서버 종료 시
orderProcessor.interrupt()
orderProcessor.join() // 스레드가 완전히 끝날 때까지 대기
```

> **함정**: `InterruptedException`을 catch하면 인터럽트 플래그가 초기화된다. 반드시 `Thread.currentThread().interrupt()`로 복원하거나, 상위로 예외를 전파해야 한다.

---

## 3. 자바 메모리 모델(JMM: Java Memory Model)과 가시성 문제

### 3.1 CPU 캐시와 가시성 문제(Visibility Problem)

현대 CPU는 성능을 위해 **L1/L2/L3 캐시**를 사용한다. 스레드는 메인 메모리(RAM)의 값을 CPU 캐시에 복사해 작업하므로, **한 스레드의 변경이 다른 스레드에 즉시 보이지 않을 수 있다**.

```kotlin
// 가시성 문제 예시 — 실제로 무한루프가 될 수 있음
class OrderQueue {
    var hasOrder = false  // volatile 없음!

    fun producer() {
        Thread.sleep(100)
        hasOrder = true  // 스레드-1의 캐시에만 씀, 메인 메모리 미반영 가능
        println("주문 생성 완료")
    }

    fun consumer() {
        while (!hasOrder) {  // 스레드-2는 캐시의 false를 계속 읽을 수 있음
            // 바쁜 대기 (busy wait) - CPU 100% 사용
        }
        println("주문 처리 시작")
    }
}
```

```
CPU-1 (스레드-1)          CPU-2 (스레드-2)
┌──────────────────┐       ┌──────────────────┐
│ L1 Cache         │       │ L1 Cache         │
│ hasOrder = true  │       │ hasOrder = false  │ ← 캐시 불일치!
└──────────────────┘       └──────────────────┘
         ↑                          ↑
         └────── 메인 메모리 ────────┘
                 hasOrder = ?
```

### 3.2 volatile의 의미와 한계

`volatile`은 두 가지를 보장한다:
1. **가시성(Visibility)**: 쓰기 즉시 메인 메모리에 반영, 읽기 시 항상 메인 메모리에서 읽음
2. **재정렬 금지(No Reordering)**: 컴파일러/JIT의 명령어 재정렬을 막음

```kotlin
class OrderQueue {
    @Volatile
    var hasOrder = false  // 이제 가시성 보장

    @Volatile
    var orderCount = 0  // 읽기/쓰기 개별 원자성만 보장
}

// 단, volatile은 복합 연산에는 불충분
val queue = OrderQueue()

// 스레드-1
queue.orderCount++  // 이것은 read → increment → write 세 단계!
                    // 두 스레드가 동시에 하면 갱신 유실 가능
```

**volatile의 한계 정리:**

| 보장 항목 | volatile | synchronized |
|----------|---------|-------------|
| 가시성 | ✅ | ✅ |
| 복합 연산 원자성 | ❌ | ✅ |
| 상호 배제(Mutual Exclusion) | ❌ | ✅ |

### 3.3 Happens-Before 관계

JMM은 "어떤 연산의 결과가 다른 연산에게 보인다"를 **happens-before** 규칙으로 정의한다.

주요 happens-before 규칙:
- **프로그램 순서 규칙**: 같은 스레드에서 앞 코드는 뒤 코드보다 먼저 일어남
- **모니터 락 규칙**: `unlock`은 그 락의 `lock`보다 먼저 일어남
- **volatile 규칙**: volatile 쓰기는 그 이후의 volatile 읽기보다 먼저 일어남
- **스레드 시작 규칙**: `thread.start()`는 스레드 내 모든 코드보다 먼저 일어남
- **스레드 종료 규칙**: 스레드 내 모든 코드는 `thread.join()` 반환보다 먼저 일어남

```kotlin
// happens-before 실용 예시
class TradeResult {
    var result: BigDecimal? = null  // 공유 변수
}

val tradeResult = TradeResult()

val worker = Thread {
    // (A) 체결 계산
    tradeResult.result = BigDecimal("52300") // 쓰기
}

worker.start()
worker.join()  // join() 이후에는 (A)의 결과가 반드시 보임 (happens-before 보장)

println(tradeResult.result)  // 안전하게 읽을 수 있음: 52300
```

---

## 4. 원자성(Atomicity)과 Atomic 클래스

### 4.1 원자성이란

"작업이 중간에 끊기지 않고 전부 실행되거나 전혀 실행되지 않음"을 의미한다.

```kotlin
// 원자성 없는 카운터 — 주문 체결 건수 집계 오류 예시
class UnsafeOrderCounter {
    var count = 0  // Long이어도 32bit JVM에서 비원자적

    fun increment() {
        count++  // read(1) → add(2) → write(3): 세 단계, 경쟁 발생 가능
    }
}

// 100개 스레드가 동시에 1000번씩 increment() 호출
// 기대: 100,000 / 실제: 50,000~99,999 사이 불확정
```

### 4.2 AtomicInteger / AtomicLong

CAS(Compare-And-Swap) 하드웨어 명령어를 사용해 락 없이 원자성을 보장한다.

```kotlin
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.math.BigDecimal

// 안전한 주문 카운터
class SafeOrderCounter {
    private val count = AtomicInteger(0)
    private val totalAmount = AtomicLong(0L) // 원 단위로 관리

    fun increment(amount: BigDecimal) {
        count.incrementAndGet()
        // BigDecimal → Long 변환 (소수점 버림 주의: 원 단위 시스템이면 적합)
        totalAmount.addAndGet(amount.toLong())
    }

    fun getCount() = count.get()
    fun getTotalAmount() = BigDecimal(totalAmount.get())
}

// AtomicReference: 객체 자체를 원자적으로 교체
data class MarketPrice(val symbol: String, val price: BigDecimal, val timestamp: Long)

class PriceFeed {
    private val latestPrice = AtomicReference(
        MarketPrice("005930", BigDecimal("75000"), System.currentTimeMillis())
    )

    fun updatePrice(newPrice: MarketPrice) {
        latestPrice.set(newPrice)  // 원자적 교체
    }

    fun getPrice(): MarketPrice = latestPrice.get()

    // CAS를 직접 사용: 특정 가격보다 낮을 때만 업데이트 (스탑로스 로직)
    fun updateIfLower(newPrice: MarketPrice): Boolean {
        while (true) {
            val current = latestPrice.get()
            if (newPrice.price >= current.price) return false
            if (latestPrice.compareAndSet(current, newPrice)) return true
            // compareAndSet 실패(다른 스레드가 먼저 변경) → 다시 시도
        }
    }
}
```

### 4.3 CAS(Compare-And-Swap)의 원리

```
CAS(메모리주소, 기댓값, 새값):
  if (메모리[주소] == 기댓값):
      메모리[주소] = 새값
      return true
  else:
      return false
  // 이 전체가 하드웨어 수준에서 원자적으로 실행됨
```

**ABA 문제**: 값이 A→B→A로 바뀌었을 때 CAS는 변경을 감지 못한다. 증권에서 잔고가 100→50→100으로 변했는데 CAS는 같다고 인식할 수 있다. 해법: `AtomicStampedReference` 사용.

```kotlin
import java.util.concurrent.atomic.AtomicStampedReference

val balance = AtomicStampedReference(BigDecimal("1000000"), 0)

fun withdraw(amount: BigDecimal): Boolean {
    val stampHolder = IntArray(1)
    val current = balance.get(stampHolder)
    val currentStamp = stampHolder[0]

    if (current < amount) return false

    return balance.compareAndSet(
        current,
        current - amount,
        currentStamp,
        currentStamp + 1  // 스탬프(버전)도 함께 비교/업데이트
    )
}
```

---

## 5. synchronized vs ReentrantLock vs ReadWriteLock

### 5.1 synchronized

JVM 내장 모니터 락(Monitor Lock). 가장 간단하지만 기능이 제한적이다.

```kotlin
class SynchronizedOrderBook {
    private val orders = mutableListOf<Order>()
    private val lock = Any()  // 모든 객체가 모니터 락을 가짐

    // 메서드 전체 잠금
    @Synchronized
    fun addOrder(order: Order) {
        orders.add(order)
    }

    // 블록 단위 잠금 (더 세밀한 제어)
    fun removeOrder(orderId: String) {
        synchronized(lock) {
            orders.removeIf { it.id == orderId }
        }
        // 잠금 범위 밖에서 로깅 등 수행 가능
        println("주문 $orderId 취소 완료")
    }

    fun getOrderCount(): Int {
        synchronized(lock) {
            return orders.size
        }
    }
}

data class Order(val id: String, val symbol: String, val price: BigDecimal, val quantity: Int)
```

**synchronized의 한계:**
- 락 획득 시도 중단 불가 (인터럽트 불가능)
- 타임아웃 없음
- 공정성(fairness) 제어 불가
- 읽기/쓰기 구분 불가

### 5.2 ReentrantLock

`java.util.concurrent.locks.ReentrantLock`. synchronized보다 유연하다.

```kotlin
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock

class ReentrantOrderBook {
    private val orders = mutableListOf<Order>()
    private val lock = ReentrantLock(true)  // true: 공정 모드 (먼저 기다린 스레드 우선)

    // kotlin.concurrent.withLock: try-finally 자동 처리
    fun addOrder(order: Order) = lock.withLock {
        orders.add(order)
    }

    // 타임아웃 있는 락 획득
    fun tryAddOrder(order: Order, timeoutMs: Long): Boolean {
        if (!lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
            println("주문 추가 타임아웃: ${order.id}")
            return false
        }
        try {
            orders.add(order)
            return true
        } finally {
            lock.unlock()  // 반드시 finally에서 해제
        }
    }

    // 인터럽트 가능한 락 대기
    fun addOrderInterruptibly(order: Order) {
        lock.lockInterruptibly()  // 인터럽트 발생 시 InterruptedException 던짐
        try {
            orders.add(order)
        } finally {
            lock.unlock()
        }
    }

    // Condition: 특정 조건 충족까지 대기
    private val notEmpty = lock.newCondition()

    fun waitAndGetOrder(): Order {
        lock.withLock {
            while (orders.isEmpty()) {
                notEmpty.await()  // 락 해제 후 대기, signal 오면 재획득
            }
            return orders.removeFirst()
        }
    }

    fun signalNewOrder(order: Order) = lock.withLock {
        orders.add(order)
        notEmpty.signal()  // 대기 중인 스레드 하나 깨움
    }
}
```

### 5.3 ReadWriteLock

읽기 작업이 많고 쓰기가 드문 경우(시세 조회 시스템 등) 성능을 크게 높인다.

```kotlin
import java.util.concurrent.locks.ReentrantReadWriteLock

class PriceCache {
    private val prices = mutableMapOf<String, BigDecimal>()
    private val rwLock = ReentrantReadWriteLock()
    private val readLock = rwLock.readLock()
    private val writeLock = rwLock.writeLock()

    // 읽기 락: 여러 스레드 동시 진입 가능
    fun getPrice(symbol: String): BigDecimal? {
        readLock.lock()
        try {
            return prices[symbol]
        } finally {
            readLock.unlock()
        }
    }

    // 쓰기 락: 단독 진입, 모든 읽기 락 차단
    fun updatePrice(symbol: String, price: BigDecimal) {
        writeLock.lock()
        try {
            prices[symbol] = price
        } finally {
            writeLock.unlock()
        }
    }

    fun getAllPrices(): Map<String, BigDecimal> {
        readLock.lock()
        try {
            return prices.toMap()  // 스냅샷 반환
        } finally {
            readLock.unlock()
        }
    }
}
```

**락 선택 가이드:**

| 상황 | 권장 방식 |
|------|---------|
| 단순 공유 자원, 짧은 임계구역 | `synchronized` |
| 타임아웃/인터럽트 필요 | `ReentrantLock` |
| 읽기 多, 쓰기 少 | `ReadWriteLock` |
| 단순 카운터/플래그 | `Atomic*` |
| 복잡한 상태 없음 | `ConcurrentHashMap` 등 동시성 컬렉션 |

---

## 6. 데드락(Deadlock: 교착상태)

### 6.1 데드락의 4가지 조건

데드락은 다음 네 조건이 **동시에** 만족될 때 발생한다:

1. **상호 배제(Mutual Exclusion)**: 자원은 한 번에 하나의 스레드만 사용
2. **점유 대기(Hold and Wait)**: 자원을 점유한 채 다른 자원 대기
3. **비선점(No Preemption)**: 타 스레드가 자원을 강제로 빼앗을 수 없음
4. **순환 대기(Circular Wait)**: 스레드들이 순환적으로 서로의 자원을 대기

### 6.2 증권 데드락 예시

```kotlin
// 위험한 코드: 계좌 간 이체 시 데드락 발생 가능
class DangerousTransfer {
    private val lockA = Any()  // 계좌 A 락
    private val lockB = Any()  // 계좌 B 락

    fun transferAtoB(amount: BigDecimal) {
        synchronized(lockA) {       // 스레드-1: A 락 획득
            Thread.sleep(10)
            synchronized(lockB) {   // 스레드-1: B 락 대기 (스레드-2가 점유 중이면 블록)
                println("A→B 이체: $amount")
            }
        }
    }

    fun transferBtoA(amount: BigDecimal) {
        synchronized(lockB) {       // 스레드-2: B 락 획득
            Thread.sleep(10)
            synchronized(lockA) {   // 스레드-2: A 락 대기 (스레드-1이 점유 중이면 블록)
                println("B→A 이체: $amount")
            }
        }
    }
}
// 스레드-1이 transferAtoB, 스레드-2가 transferBtoA 동시 호출 → 데드락!
```

### 6.3 데드락 회피 방법

**방법 1: 락 순서 고정 (Lock Ordering)**

```kotlin
class SafeTransferByOrdering {
    // 계좌 ID 기반 정렬로 항상 같은 순서로 락 획득
    fun transfer(fromAccount: Account, toAccount: Account, amount: BigDecimal) {
        val (first, second) = if (fromAccount.id < toAccount.id) {
            fromAccount to toAccount
        } else {
            toAccount to fromAccount
        }

        synchronized(first.lock) {
            synchronized(second.lock) {
                if (fromAccount.balance < amount) {
                    throw InsufficientBalanceException("잔고 부족")
                }
                fromAccount.balance -= amount
                toAccount.balance += amount
            }
        }
    }
}

data class Account(val id: String, var balance: BigDecimal) {
    val lock = Any()
}

class InsufficientBalanceException(message: String) : Exception(message)
```

**방법 2: 타임아웃 있는 락 (Timeout)**

```kotlin
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit

class SafeTransferByTimeout {
    fun transfer(
        from: AccountWithLock, to: AccountWithLock, amount: BigDecimal
    ): Boolean {
        val fromLocked = from.lock.tryLock(500, TimeUnit.MILLISECONDS)
        if (!fromLocked) {
            println("from 계좌 락 획득 실패, 재시도 필요")
            return false
        }
        try {
            val toLocked = to.lock.tryLock(500, TimeUnit.MILLISECONDS)
            if (!toLocked) {
                println("to 계좌 락 획득 실패, 재시도 필요")
                return false
            }
            try {
                if (from.balance < amount) throw InsufficientBalanceException("잔고 부족")
                from.balance -= amount
                to.balance += amount
                return true
            } finally {
                to.lock.unlock()
            }
        } finally {
            from.lock.unlock()
        }
    }
}

data class AccountWithLock(val id: String, var balance: BigDecimal) {
    val lock = ReentrantLock()
}
```

**방법 3: 단일 글로벌 락 또는 단일 스레드 직렬화**

이체 요청을 단일 큐에 넣고 하나의 스레드가 처리하면 데드락 자체가 불가능하다. 처리량(throughput)이 줄지만 단순성과 안전성이 높아진다.

---

## 7. 동시성 컬렉션(Concurrent Collections)

### 7.1 ConcurrentHashMap

일반 `HashMap`은 동시 수정 시 `ConcurrentModificationException` 또는 무한루프 발생 가능. `Collections.synchronizedMap()`은 전체 맵에 락을 걸어 병렬성 없음. `ConcurrentHashMap`은 내부를 세그먼트(버킷)로 나눠 부분 락을 사용한다.

```kotlin
import java.util.concurrent.ConcurrentHashMap
import java.math.BigDecimal

// 실시간 시세 캐시
val priceCache = ConcurrentHashMap<String, BigDecimal>()

// 안전한 동시 읽기/쓰기
priceCache["005930"] = BigDecimal("75000")
priceCache["000660"] = BigDecimal("132000")

// computeIfAbsent: 없으면 생성, 있으면 기존 값 반환 (원자적)
val order = priceCache.computeIfAbsent("005380") {
    fetchPriceFromMarket(it)  // 한 번만 호출됨
}

// compute: 기존 값을 기반으로 새 값 계산 (원자적)
priceCache.compute("005930") { _, currentPrice ->
    currentPrice?.multiply(BigDecimal("1.01"))  // 1% 상승
}

// merge: 쓰기 편의 메서드
priceCache.merge("005930", BigDecimal("76000")) { old, new ->
    if (new > old) new else old  // 더 높은 가격만 반영
}

fun fetchPriceFromMarket(symbol: String): BigDecimal {
    // 실제로는 외부 API 호출
    return BigDecimal("50000")
}
```

> **주의**: `ConcurrentHashMap`의 개별 연산은 원자적이지만, 여러 연산의 조합(read-then-write)은 원자적이지 않다. 복합 연산에는 `compute`, `merge`, `computeIfAbsent`를 사용하라.

### 7.2 BlockingQueue

생산자-소비자(Producer-Consumer) 패턴에 최적. 큐가 꽉 차면 생산자 블록, 비면 소비자 블록.

```kotlin
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

data class OrderRequest(
    val id: String,
    val symbol: String,
    val price: BigDecimal,
    val quantity: Int,
    val side: OrderSide
)

enum class OrderSide { BUY, SELL }

// 주문 처리 파이프라인
val orderQueue = LinkedBlockingQueue<OrderRequest>(1000)  // 최대 1000건

// 생산자 (주문 수신)
val producer = Thread {
    repeat(10000) { i ->
        val order = OrderRequest("ORD-$i", "005930", BigDecimal("75000"), 10, OrderSide.BUY)
        orderQueue.put(order)  // 꽉 차면 블록
        // offer(order, 100, TimeUnit.MILLISECONDS)  // 타임아웃 버전
    }
}

// 소비자 (주문 처리)
val consumer = Thread {
    while (!Thread.currentThread().isInterrupted) {
        val order = orderQueue.poll(1, TimeUnit.SECONDS)  // 최대 1초 대기
            ?: continue  // null이면 루프 재시작
        processOrderRequest(order)
    }
}

fun processOrderRequest(order: OrderRequest) {
    println("주문 처리: ${order.id} ${order.symbol} ${order.price} x ${order.quantity}")
}
```

**BlockingQueue 구현체 비교:**

| 구현체 | 특징 | 사용 사례 |
|--------|------|---------|
| `LinkedBlockingQueue` | 동적 크기, 높은 처리량 | 일반 주문 큐 |
| `ArrayBlockingQueue` | 고정 크기, 공정 모드 지원 | 배압(Backpressure) 제어 |
| `PriorityBlockingQueue` | 우선순위 정렬 | VIP 주문 우선 처리 |
| `DelayQueue` | 지정 시간 후 꺼내짐 | 예약 주문 |
| `SynchronousQueue` | 크기 0, 핸드오프 | 스레드 간 직접 전달 |

### 7.3 CopyOnWriteArrayList

쓰기 시 전체 배열을 복사하므로, **읽기가 압도적으로 많고 쓰기가 드문** 경우에만 적합하다.

```kotlin
import java.util.concurrent.CopyOnWriteArrayList

// 구독 중인 시세 종목 목록 (자주 읽고, 드물게 추가/삭제)
val subscribedSymbols = CopyOnWriteArrayList<String>()

subscribedSymbols.add("005930")
subscribedSymbols.add("000660")

// 읽기는 락 없음, 매우 빠름
for (symbol in subscribedSymbols) {  // ConcurrentModificationException 없음
    println("시세 조회: $symbol")
    // 이 반복 도중 다른 스레드가 add/remove 해도 이터레이터는 스냅샷을 봄
}

// 쓰기: 내부 배열 전체 복사 후 교체 → 비용 높음
subscribedSymbols.add("035420")  // NAVER 추가
subscribedSymbols.remove("000660")  // SK하이닉스 제거
```

---

## 8. 스레드풀(ExecutorService)과 적정 크기

### 8.1 왜 스레드풀인가

스레드 하나 생성에 ~1MB 스택 메모리 + OS 스케줄러 컨텍스트 스위칭 비용이 든다. 10,000개 동시 주문에 10,000개 스레드를 만들면 시스템이 붕괴된다.

```kotlin
import java.util.concurrent.*

// 고정 크기 스레드풀: CPU 집약적 주문 검증
val orderValidationPool: ExecutorService = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()  // CPU 코어 수
)

// 캐시 스레드풀: 짧고 빈번한 작업 (주의: 무제한 스레드 생성 가능)
val shortTaskPool: ExecutorService = Executors.newCachedThreadPool()

// 스케줄 스레드풀: 주기적 시세 수신
val scheduledPool: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

// 단일 스레드: 순서 보장이 필요한 처리 (예: 계좌원장 업데이트)
val ledgerUpdatePool: ExecutorService = Executors.newSingleThreadExecutor()

// 직접 구성한 ThreadPoolExecutor (실무 권장)
val orderProcessingPool = ThreadPoolExecutor(
    4,                              // corePoolSize: 기본 스레드 수
    16,                             // maximumPoolSize: 최대 스레드 수
    60L, TimeUnit.SECONDS,          // keepAliveTime: 유휴 스레드 유지 시간
    ArrayBlockingQueue(500),        // workQueue: 대기 큐 (배압 제어)
    ThreadFactory { runnable ->     // 스레드 이름 지정
        Thread(runnable, "order-processor-${System.nanoTime()}").apply {
            isDaemon = false
        }
    },
    ThreadPoolExecutor.CallerRunsPolicy()  // 큐 꽉 차면 호출자 스레드가 직접 실행
)
```

### 8.2 적정 스레드풀 크기 공식

```
CPU 집약적 작업: 스레드 수 = CPU 코어 수 + 1
IO 집약적 작업: 스레드 수 = CPU 코어 수 × (1 + IO 대기 시간 / CPU 사용 시간)
```

증권 시스템 예시:
- CPU 코어: 8개
- DB 조회 시간: 5ms, CPU 계산 시간: 1ms → 비율 = 5
- IO 집약 스레드 수 = 8 × (1 + 5) = 48

> **실무 팁**: 이론값에서 시작해 **부하 테스트(Load Test)**로 최적값을 찾는다. 스레드풀 크기를 설정 파일로 외부화해 재배포 없이 튜닝 가능하게 한다.

### 8.3 스레드풀 작업 제출과 결과 수집

```kotlin
import java.util.concurrent.Callable
import java.util.concurrent.Future

// submit: Future로 결과 수신
val future: Future<BigDecimal> = orderProcessingPool.submit(Callable {
    calculateOrderValue("005930", 100, BigDecimal("75000"))
})

// 다른 작업 수행...
println("주문 가치 계산 요청 완료, 결과 대기 중")

val orderValue: BigDecimal = future.get(5, TimeUnit.SECONDS)  // 최대 5초 대기
println("주문 가치: $orderValue")

fun calculateOrderValue(symbol: String, quantity: Int, price: BigDecimal): BigDecimal {
    return price.multiply(BigDecimal(quantity))
}

// invokeAll: 여러 작업 동시 제출, 모두 완료 대기
val symbols = listOf("005930", "000660", "035420")
val priceTasks = symbols.map { symbol ->
    Callable { symbol to fetchPriceFromMarket(symbol) }
}
val results: List<Future<Pair<String, BigDecimal>>> = orderProcessingPool.invokeAll(priceTasks)
val prices = results.map { it.get() }.toMap()
println("조회된 시세: $prices")

// 우아한 종료
fun shutdownPool(pool: ExecutorService) {
    pool.shutdown()  // 새 작업 거부, 기존 작업 완료 대기
    if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
        pool.shutdownNow()  // 강제 종료
        println("스레드풀 강제 종료")
    }
}
```

---

## 9. CompletableFuture

자바 8+의 비동기 파이프라인 구성 도구. 콜백 지옥 없이 비동기 작업을 연결한다.

```kotlin
import java.util.concurrent.CompletableFuture

// 비동기 주문 처리 파이프라인
fun processOrderAsync(orderId: String): CompletableFuture<String> {
    return CompletableFuture
        .supplyAsync({
            // 1단계: 주문 검증 (IO 스레드풀)
            validateOrder(orderId)
        }, orderProcessingPool)
        .thenApply { validOrder ->
            // 2단계: 잔고 확인 (같은 스레드 또는 다른 스레드)
            checkBalance(validOrder)
            validOrder
        }
        .thenCompose { validOrder ->
            // 3단계: 체결소 전송 (비동기, 새 Future 반환)
            sendToExchangeAsync(validOrder)
        }
        .thenApply { exchangeResult ->
            // 4단계: 결과 처리
            "주문 $orderId 체결 완료: $exchangeResult"
        }
        .exceptionally { ex ->
            // 예외 처리
            "주문 $orderId 실패: ${ex.message}"
        }
}

// 여러 시세 병렬 조회 후 합산
fun fetchPortfolioValueAsync(symbols: List<String>): CompletableFuture<BigDecimal> {
    val priceFutures = symbols.map { symbol ->
        CompletableFuture.supplyAsync({ fetchPriceFromMarket(symbol) }, orderProcessingPool)
            .thenApply { price -> price.multiply(BigDecimal("100")) }  // 100주 기준
    }

    return CompletableFuture.allOf(*priceFutures.toTypedArray())
        .thenApply {
            priceFutures.fold(BigDecimal.ZERO) { acc, future -> acc + future.get() }
        }
}

fun validateOrder(orderId: String): String = orderId  // 검증 로직 생략
fun checkBalance(orderId: String) = Unit
fun sendToExchangeAsync(orderId: String): CompletableFuture<String> =
    CompletableFuture.completedFuture("체결가: 75,100")
```

---

## 10. 잔고 동시 갱신: 방식 비교

실무에서 가장 많이 마주치는 문제인 **잔고 동시 갱신**을 여러 방식으로 해결해 비교한다.

```kotlin
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class BalanceResult(val success: Boolean, val newBalance: BigDecimal, val message: String)

// ── 방식 1: synchronized ──────────────────────────────────────────────────────
class SynchronizedBalance(initialBalance: BigDecimal) {
    private var balance = initialBalance

    @Synchronized
    fun withdraw(amount: BigDecimal): BalanceResult {
        if (balance < amount) return BalanceResult(false, balance, "잔고 부족")
        balance -= amount
        return BalanceResult(true, balance, "출금 성공")
    }

    @Synchronized
    fun deposit(amount: BigDecimal): BalanceResult {
        balance += amount
        return BalanceResult(true, balance, "입금 성공")
    }

    @Synchronized
    fun getBalance() = balance
}

// ── 방식 2: ReentrantLock ─────────────────────────────────────────────────────
class ReentrantBalance(initialBalance: BigDecimal) {
    private var balance = initialBalance
    private val lock = ReentrantLock()

    fun withdraw(amount: BigDecimal): BalanceResult = lock.withLock {
        if (balance < amount) return BalanceResult(false, balance, "잔고 부족")
        balance -= amount
        BalanceResult(true, balance, "출금 성공")
    }

    fun deposit(amount: BigDecimal): BalanceResult = lock.withLock {
        balance += amount
        BalanceResult(true, balance, "입금 성공")
    }
}

// ── 방식 3: AtomicReference + CAS ────────────────────────────────────────────
class LockFreeBalance(initialBalance: BigDecimal) {
    private val balance = AtomicReference(initialBalance)

    fun withdraw(amount: BigDecimal): BalanceResult {
        while (true) {
            val current = balance.get()
            if (current < amount) return BalanceResult(false, current, "잔고 부족")
            val next = current - amount
            if (balance.compareAndSet(current, next)) {
                return BalanceResult(true, next, "출금 성공")
            }
            // CAS 실패: 다른 스레드가 먼저 변경 → 재시도
        }
    }

    fun deposit(amount: BigDecimal): BalanceResult {
        while (true) {
            val current = balance.get()
            val next = current + amount
            if (balance.compareAndSet(current, next)) {
                return BalanceResult(true, next, "입금 성공")
            }
        }
    }

    fun getBalance(): BigDecimal = balance.get()
}

// ── 방식 4: 단일 스레드 직렬화 ───────────────────────────────────────────────
class SingleThreadBalance(initialBalance: BigDecimal) {
    private var balance = initialBalance
    private val executor = Executors.newSingleThreadExecutor()

    fun withdrawAsync(amount: BigDecimal): CompletableFuture<BalanceResult> {
        return CompletableFuture.supplyAsync({
            if (balance < amount) {
                BalanceResult(false, balance, "잔고 부족")
            } else {
                balance -= amount
                BalanceResult(true, balance, "출금 성공")
            }
        }, executor)
    }
}
```

**방식 비교표:**

| 방식 | 성능 | 복잡도 | 공정성 | 사용 추천 상황 |
|------|------|--------|--------|--------------|
| synchronized | 보통 | 낮음 | JVM 결정 | 단순, 성능 무관 |
| ReentrantLock | 보통~높음 | 중간 | 설정 가능 | 타임아웃/공정성 필요 |
| AtomicReference CAS | 높음 (경합 적을 때) | 높음 | 없음 | 경합 적고 성능 중요 |
| 단일 스레드 직렬화 | 낮음 | 낮음 | 자동 (FIFO) | 순서 보장, 단순성 |

---

## 11. 증권 주문 처리 동시성 설계 종합

```kotlin
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.math.BigDecimal

// 주문 ID 생성기 (원자적)
object OrderIdGenerator {
    private val sequence = AtomicLong(System.currentTimeMillis() * 1000)
    fun next(): String = "ORD-${sequence.incrementAndGet()}"
}

// 주문 처리 시스템 — 실무 구조에 가까운 예시
class OrderProcessingSystem {
    // 접수 큐: 외부 요청 → 내부 처리
    private val receivedQueue = LinkedBlockingQueue<OrderRequest>(5000)

    // 처리 스레드풀: IO 집약 (DB, 체결소 통신)
    private val workerPool = ThreadPoolExecutor(
        8, 32, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(1000),
        ThreadFactory { Thread(it, "order-worker-${System.nanoTime()}") },
        ThreadPoolExecutor.AbortPolicy()  // 큐 꽉 차면 RejectedExecutionException
    )

    // 시세 캐시: 읽기 多, 쓰기 少
    private val priceCache = ConcurrentHashMap<String, BigDecimal>()
    private val priceCacheLock = ReentrantReadWriteLock()

    // 잔고 관리: 계좌별 독립 락
    private val accountBalances = ConcurrentHashMap<String, ReentrantBalance>()

    fun submit(order: OrderRequest): Boolean {
        return receivedQueue.offer(order, 100, TimeUnit.MILLISECONDS).also { accepted ->
            if (!accepted) println("주문 큐 포화: ${order.id} 거부")
        }
    }

    fun startProcessing() {
        // 수신 스레드: 큐에서 꺼내 워커풀에 위임
        Thread({
            while (!Thread.currentThread().isInterrupted) {
                val order = receivedQueue.poll(1, TimeUnit.SECONDS) ?: continue
                workerPool.submit { handleOrder(order) }
            }
        }, "order-dispatcher").start()
    }

    private fun handleOrder(order: OrderRequest) {
        try {
            val price = priceCache[order.symbol]
                ?: throw IllegalStateException("시세 없음: ${order.symbol}")

            val account = accountBalances.computeIfAbsent(order.accountId) {
                ReentrantBalance(BigDecimal("10000000"))  // 초기 잔고 1000만원
            }

            val required = price.multiply(BigDecimal(order.quantity))
            val result = account.withdraw(required)

            if (result.success) {
                println("주문 체결: ${order.id}, 잔여 잔고: ${result.newBalance}")
            } else {
                println("주문 거부: ${order.id}, ${result.message}")
            }
        } catch (e: Exception) {
            println("주문 처리 오류: ${order.id}, ${e.message}")
        }
    }

    fun shutdown() {
        workerPool.shutdown()
        if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
            workerPool.shutdownNow()
        }
    }
}

data class OrderRequest(
    val id: String,
    val accountId: String,
    val symbol: String,
    val quantity: Int,
    val side: OrderSide
)
```

---

## 핵심 정리

- **가시성 문제**는 CPU 캐시 때문에 발생. `volatile` 또는 동기화로 해결.
- **원자성**이 필요한 복합 연산에는 `synchronized`, `Lock`, 또는 `Atomic*` 클래스 사용.
- **데드락**은 락 순서 고정 또는 타임아웃으로 회피.
- **스레드풀 크기**: CPU 집약 = 코어 수, IO 집약 = 코어 수 × (1 + IO/CPU 비율).
- **잔고 갱신**: 경합이 적으면 CAS(AtomicReference), 많으면 ReentrantLock, 순서 보장이 필요하면 단일 스레드.

---

### 체크리스트

- [ ] `volatile`과 `synchronized`의 차이를 설명할 수 있다
- [ ] `AtomicInteger.compareAndSet()`의 동작을 직접 코딩할 수 있다
- [ ] 데드락이 발생하는 코드를 보고 수정할 수 있다
- [ ] `ThreadPoolExecutor` 생성자 파라미터 각각의 의미를 안다
- [ ] `ConcurrentHashMap.compute()`와 단순 `get/put`의 차이를 설명할 수 있다
- [ ] `ReentrantReadWriteLock`을 사용해 읽기 많은 캐시를 구현할 수 있다

---

이전: [11. 잔고 정합성 입문](11-concurrency) · 다음: [29. 코루틴 완전정복](29-coroutines-deep) · [전체 커리큘럼](/curriculum)
