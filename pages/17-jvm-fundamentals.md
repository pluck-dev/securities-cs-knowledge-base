# 17. JVM 동작 원리

> 대상: 증권사 백엔드 개발자 지망자 — JVM이 어떻게 동작하는지 이해해야 저지연(Low-Latency) 시스템에서 발생하는 장애를 진단할 수 있다.  
> 목표: "왜 GC가 장애를 일으키는가"를 설명할 수 있고, 메모리 구조를 보며 OOM 원인을 추적할 수 있다.

---

## 1. JDK · JRE · JVM의 차이

혼동이 가장 많은 부분이다. 포함 관계로 이해하면 명확하다.

```
JDK (Java Development Kit)
 └── JRE (Java Runtime Environment)
      └── JVM (Java Virtual Machine)
           ├── Class Loader
           ├── Runtime Data Areas (메모리)
           ├── Execution Engine (JIT 컴파일러 포함)
           └── Native Interface (JNI)
      ├── 표준 라이브러리 (java.util, java.io 등)
      └── rt.jar / modules
 ├── 컴파일러 (javac, kotlinc)
 ├── 디버거 (jdb)
 ├── 프로파일러 (jcmd, jstack, jmap)
 └── 기타 개발 도구 (jar, javadoc)
```

| 항목 | 역할 | 누가 필요한가 |
|------|------|--------------|
| **JVM** | 바이트코드를 실제 CPU 명령으로 변환·실행 | 모든 Java/Kotlin 프로그램 실행 환경 |
| **JRE** | JVM + 표준 라이브러리 | 배포 서버 (앱 실행만 필요한 경우) |
| **JDK** | JRE + 개발/진단 도구 | 개발자, CI/CD 빌드 서버 |

> **실무 변화**: Java 9 이후 모듈 시스템(JPMS) 도입으로 JRE가 별도 배포판으로 제공되지 않는다. 컨테이너 환경에서는 `jlink`로 필요한 모듈만 포함한 최소 런타임을 직접 빌드한다.

---

## 2. 소스 → 바이트코드 → 실행 과정

### 2-1. 컴파일 단계

```
[.kt 소스 파일]
       │  kotlinc (코틀린 컴파일러)
       ▼
[.class 바이트코드 파일]  ← 플랫폼 독립적인 중간 표현
       │  JVM 로드
       ▼
[JVM 내부 실행]
       │  JIT 컴파일러
       ▼
[네이티브 머신 코드]  ← CPU가 직접 실행
```

```bash
# 직접 체험해보기
kotlinc Order.kt -include-runtime -d Order.jar

# 바이트코드 역어셈블
javap -c -verbose Order.class
```

코틀린 컴파일러(`kotlinc`)가 `.kt` 파일을 `.class` 파일(바이트코드)로 변환한다. 이 바이트코드는 자바 바이트코드와 완전히 호환된다. `javap`로 바이트코드를 보면 `INVOKEVIRTUAL`, `ILOAD`, `BIPUSH` 같은 JVM 명령어(Instruction Set)들이 나열된다.

### 2-2. 바이트코드 예시

다음 코틀린 코드가:
```kotlin
fun add(a: Int, b: Int): Int = a + b
```

이런 바이트코드로 컴파일된다:
```
public final int add(int, int);
  Code:
     0: iload_1      // 로컬 변수 1번(a)을 스택에 올림
     1: iload_2      // 로컬 변수 2번(b)을 스택에 올림
     2: iadd         // 스택 상위 두 값을 더해 결과를 스택에 올림
     3: ireturn      // 반환
```

JVM은 **스택 기반 가상 머신(Stack-Based VM)**이다. CPU 레지스터 대신 오퍼랜드 스택(Operand Stack)을 사용해 계산한다.

---

## 3. 클래스로더(ClassLoader) 동작

JVM은 모든 클래스를 한꺼번에 메모리에 올리지 않는다. 필요한 시점에 클래스를 동적으로 로드한다.

### 3-1. 클래스로더 계층 구조

```
Bootstrap ClassLoader      ← JVM 내부, C++로 구현
        │  (java.lang.*, java.util.* 등 핵심 라이브러리)
        ▼
Platform ClassLoader       ← Java 9+ (구 Extension ClassLoader)
        │  (javax.*, java.sql.* 등 확장 모듈)
        ▼
Application ClassLoader    ← 개발자가 작성한 코드, 의존성 JAR
        │
        ▼
Custom ClassLoader         ← 필요 시 직접 구현 (OSGi, WAS 등)
```

### 3-2. 클래스 로딩 3단계

| 단계 | 설명 |
|------|------|
| **로딩(Loading)** | `.class` 파일을 읽어 메서드 영역에 저장 |
| **링킹(Linking)** | 검증(Verify) → 준비(Prepare, static 필드 기본값) → 해석(Resolve, 심볼릭 참조 → 직접 참조) |
| **초기화(Initialization)** | static 블록 실행, static 필드 값 할당 |

### 3-3. 증권사 실무 연관 — ClassLoader 격리

레거시 증권 시스템에서는 **WAS(WebLogic, JBoss)**를 사용하는 경우가 있다. WAS는 각 애플리케이션마다 별도의 ClassLoader를 사용해 격리한다. 서로 다른 버전의 라이브러리(예: jackson 2.10 vs 2.15)를 같은 서버에서 동시에 운영할 수 있는 원리다.

> **함정**: Spring Boot의 내장 Tomcat과 외부 WAS에 배포하는 경우 ClassLoader 구조가 달라 `ClassNotFoundException`이나 `NoSuchMethodError`가 발생할 수 있다. 이는 대부분 의존성 버전 충돌이 원인이다.

---

## 4. JIT(Just-In-Time) 컴파일

JVM이 처음부터 빠르지 않은 이유, 그리고 결국 빠르게 되는 이유가 모두 JIT에 있다.

### 4-1. JIT 동작 방식

```
바이트코드 실행 시작
        │
        ▼
인터프리터로 실행 (느림, 하지만 즉시 시작 가능)
        │  실행 횟수 카운팅 (Profiling)
        ▼
핫스팟(HotSpot) 감지: 자주 호출되는 메서드/루프
        │  컴파일 임계값 초과 (기본 10,000회)
        ▼
C1 컴파일러 (빠른 컴파일, 기본 최적화)
        │  더 많은 프로파일 데이터 수집
        ▼
C2 컴파일러 (느린 컴파일, 공격적 최적화)
        │
        ▼
네이티브 코드로 실행 (C++ 수준의 성능)
```

### 4-2. JIT가 적용하는 최적화 기법

| 최적화 | 설명 | 효과 |
|--------|------|------|
| **인라이닝(Inlining)** | 짧은 메서드 호출을 호출부에 직접 삽입 | 메서드 호출 오버헤드 제거 |
| **루프 언롤링(Loop Unrolling)** | 루프를 펼쳐 반복 조건 체크 횟수 감소 | 분기 예측 실패 감소 |
| **탈출 분석(Escape Analysis)** | 객체가 메서드 밖으로 나가지 않으면 스택에 할당 | GC 부담 감소 |
| **데드 코드 제거** | 실행될 수 없는 코드 제거 | |
| **상수 폴딩** | 컴파일 시점에 상수 식 계산 | |

### 4-3. 워밍업(Warm-up) 문제

JIT는 처음 실행 시 프로파일링이 필요하므로, **프로세스 시작 직후에는 성능이 낮다**. 이를 워밍업 시간이라고 한다.

```
성능
 │         _______ (JIT 최적화 완료)
 │       /
 │     /
 │   / (C1 적용)
 │  /
 │ / (인터프리터)
 └──────────────────────── 시간
   0s    2s    5s    10s
```

> **증권사 실무 중요**: 장 시작 직전(8:50~9:00 AM) 갑자기 트래픽이 몰릴 때 JIT 워밍업이 안 된 상태면 응답 시간이 수초로 튀는 사고가 발생한다. 대응 전략:
> 1. **사전 워밍업 요청**: 장 시작 전 더미 요청으로 핫 경로(Hot Path)를 미리 컴파일
> 2. **GraalVM Native Image**: AOT(Ahead-Of-Time) 컴파일로 시작부터 네이티브 속도

---

## 5. JVM 메모리 구조 (Runtime Data Areas)

가장 중요한 부분이다. OOM(OutOfMemoryError)의 90%는 이 구조를 이해하면 원인을 찾을 수 있다.

### 5-1. 전체 메모리 구조도

```
JVM 메모리
├── 스레드 공유 (Thread-Shared)
│   ├── 힙(Heap)
│   │   ├── Young Generation
│   │   │   ├── Eden Space
│   │   │   ├── Survivor 0 (S0)
│   │   │   └── Survivor 1 (S1)
│   │   └── Old Generation (Tenured)
│   └── 메서드 영역 (Method Area) = Metaspace (Java 8+)
│       ├── 클래스 메타데이터
│       ├── static 변수
│       ├── 상수 풀(Runtime Constant Pool)
│       └── 메서드 바이트코드
│
└── 스레드 전용 (Thread-Local, 스레드마다 생성)
    ├── 스택(Stack)
    │   └── 스택 프레임(Frame) — 메서드 호출마다 하나
    │       ├── 로컬 변수 배열
    │       ├── 오퍼랜드 스택
    │       └── 현재 명령어 포인터
    ├── PC 레지스터 (Program Counter)
    └── 네이티브 메서드 스택 (JNI용)
```

### 5-2. 힙(Heap) — GC의 무대

힙은 `new` 키워드로 생성된 모든 객체가 살아가는 공간이다.

```kotlin
// 이 코드를 실행하면 힙에 어떤 일이 일어나는가?
fun processOrder(orderData: String): Order {
    val parser = OrderParser()        // 힙: Young/Eden에 생성
    val order = parser.parse(orderData) // 힙: Young/Eden에 생성
    return order                      // order는 반환되어 참조 유지
    // parser는 이 함수 끝나면 참조 없음 → GC 대상
}
```

**Young Generation 동작**:
1. 새 객체는 **Eden**에 생성
2. Eden이 차면 **Minor GC** 발생 → 살아남은 객체는 Survivor(S0 또는 S1)로 이동
3. Survivor에서 여러 번 살아남으면(기본 15회) **Old Generation**으로 승격(Promotion)

**Old Generation**:
- 오래 살아남은 객체들의 공간
- 여기가 차면 **Major GC(Full GC)** 발생 → 이게 문제다

### 5-3. 스택(Stack) — 스레드 전용

```kotlin
fun main() {
    val result = calculateCommission(BigDecimal("1000000"), 0.015)  // 스택 프레임 A 생성
    println(result)
}

fun calculateCommission(amount: BigDecimal, rate: Double): BigDecimal {
    // 스택 프레임 B 생성
    val commission = amount.multiply(BigDecimal(rate.toString()))    // 로컬 변수 (스택에 참조, 객체는 힙에)
    val vatAmount = applyVat(commission)  // 스택 프레임 C 생성
    return vatAmount
    // 스택 프레임 B 소멸 (commission, vatAmount 참조 사라짐)
}

fun applyVat(amount: BigDecimal): BigDecimal {
    // 스택 프레임 C
    val vatRate = BigDecimal("0.1")  // 로컬 변수
    return amount + vatRate * amount
    // 스택 프레임 C 소멸
}
```

**중요한 구분**:
- 스택에 저장되는 것: 기본형 값(`int`, `double`, `boolean`), 객체 **참조(Reference)**
- 힙에 저장되는 것: 실제 객체 데이터 (`BigDecimal` 인스턴스 자체)

> **StackOverflowError**: 재귀 호출이 너무 깊어져 스택 공간이 부족할 때 발생. 기본 스택 크기는 약 256KB~1MB. `-Xss2m`으로 늘릴 수 있다.

### 5-4. Metaspace (메서드 영역)

Java 8 이전의 **PermGen(영구 세대)**이 Java 8부터 **Metaspace**로 교체되었다. 가장 큰 차이는 Metaspace는 힙이 아닌 **네이티브 메모리**를 사용한다는 점이다.

클래스 메타데이터, `static` 변수, `companion object`, 상수 풀이 여기에 저장된다.

```kotlin
// static 필드 → Metaspace에 저장
object OrderConstants {
    const val MAX_ORDER_QUANTITY = 100_000  // Metaspace의 상수 풀
    val STOCK_CODE_PATTERN = Regex("^[0-9]{6}$")  // Metaspace에 참조 저장, 객체는 힙에
}
```

> **OOM: Metaspace**: 동적으로 클래스를 매우 많이 생성하는 환경(JRebel, 일부 AOP 프레임워크)에서 발생. `-XX:MaxMetaspaceSize=256m`으로 상한 설정.

---

## 6. 가비지 컬렉션(Garbage Collection) 원리

### 6-1. GC의 기본 원리 — 도달 가능성(Reachability)

GC는 **GC 루트(GC Roots)**에서 출발해 참조를 따라가며 도달 가능한 객체(Live Objects)를 표시(Mark)하고, 나머지를 회수(Sweep)한다.

```
GC 루트:
├── 각 스레드의 스택 로컬 변수
├── static 변수 (Metaspace에 있는 참조들)
├── JNI 글로벌 참조
└── 시스템 클래스로더가 로드한 클래스

GC 루트 → 참조 체인 추적:

   [스레드 스택]
        │
        ▼
   [OrderService]  ──참조──▶  [Order #1]  ──참조──▶  [BigDecimal 금액]
        │                     [Order #2]
        │
        ▼
   [OrderRepository]  ──참조──▶  [DB Connection Pool]

   (참조되지 않는 객체들) ← GC가 회수
```

### 6-2. GC 알고리즘 세대별 전략

#### Minor GC (Young Generation)
```
[Eden]  가득 참
   │  Minor GC 시작 (보통 수 ms ~ 수십 ms)
   ├── 살아있는 객체 → Survivor(S0)로 복사
   ├── 이미 S1에 있던 객체 → S0로 복사 (나이 +1)
   └── 나이 > 임계값 → Old Generation으로 승격
Eden + S1 완전 비워짐
```

Minor GC는 빠르다. Young Generation만 대상이고, 살아있는 객체 수가 적기 때문이다.

#### Major GC / Full GC (Old Generation)
```
[Old Generation]  가득 참 또는 Metaspace 가득 참
   │  Full GC 시작 — Stop-The-World!
   ├── 모든 애플리케이션 스레드 정지
   ├── 전체 힙 스캔 (Mark)
   ├── 압축(Compact) 또는 스윕(Sweep)
   └── 모든 스레드 재개
   수백 ms ~ 수 초 소요 가능
```

### 6-3. Stop-The-World(STW) — 증권사 저지연 시스템의 핵심 문제

**STW**는 GC가 수행되는 동안 애플리케이션의 모든 스레드가 일시 정지되는 현상이다.

```
시간축 →
정상: [주문처리][주문처리][주문처리][주문처리]
GC발생: [주문처리][ ← STW 200ms → ][주문처리]

이 200ms 동안:
- 주문 체결이 지연됨
- HTS 사용자는 응답 없음 화면을 봄
- 자동매매 시스템은 타임아웃 발생
- 슬리피지(Slippage) 발생 가능
```

> **실제 사고 사례 유형**: 대형 증권사에서 GC 튜닝이 안 된 상태로 장 마감 직전 대량 주문이 몰리면서 Full GC 500ms가 발생. 이 사이 시세 업데이트가 끊겨 잘못된 가격으로 체결되는 사고가 발생할 수 있다.

### 6-4. 주요 GC 알고리즘 비교

| GC | JDK | 특징 | 적합한 워크로드 |
|----|-----|------|----------------|
| **Serial GC** | 모든 버전 | 단일 스레드, STW 길다 | 임베디드, 소형 앱 |
| **Parallel GC** | Java 8 기본 | 멀티 스레드 Minor/Major GC | 처리량(Throughput) 우선 |
| **G1 GC** | Java 9 기본 | 힙을 Region으로 분할, 예측 가능한 STW | **증권 시스템 현실적 선택** |
| **ZGC** | Java 15 GA | STW < 1ms 목표, 대용량 힙 | 저지연 요구 시스템 |
| **Shenandoah** | OpenJDK 15 | ZGC와 유사 | Red Hat 계열 |

#### G1 GC (Garbage-First Garbage Collector)

Java 9 이후 기본 GC. 힙을 고정 크기 **Region**들로 나누어 관리한다.

```
힙 레이아웃 (G1 GC):
┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
│ E   │ S   │ E   │ O   │ H   │ E   │ O   │ S   │
└─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘
E=Eden, S=Survivor, O=Old, H=Humongous(대형 객체)

G1은 가장 쓰레기가 많은 Region을 우선 수집 → "Garbage-First"
```

#### ZGC (Z Garbage Collector)

STW를 극한으로 줄인 GC. 착색 포인터(Colored Pointers)와 로드 배리어(Load Barriers)를 사용해 GC 작업 대부분을 **애플리케이션 스레드와 동시(Concurrent)**에 수행한다.

```bash
# ZGC 활성화
java -XX:+UseZGC -Xms4g -Xmx4g -jar securities-app.jar

# STW 측정 (GC 로그)
java -XX:+UseZGC \
     -Xlog:gc*:file=gc.log:time,uptime \
     -jar securities-app.jar
```

### 6-5. GC 튜닝 JVM 플래그

```bash
# 일반적인 증권 시스템 G1 GC 설정 예시
java \
  -Xms8g                          # 초기 힙 = 최대 힙 (동적 리사이징 방지)
  -Xmx8g                          # 최대 힙
  -XX:+UseG1GC                    # G1 GC 명시
  -XX:MaxGCPauseMillis=50         # 목표 STW 시간 (ms)
  -XX:G1HeapRegionSize=16m        # Region 크기
  -XX:G1NewSizePercent=30         # Young 최소 비율
  -XX:G1MaxNewSizePercent=40      # Young 최대 비율
  -XX:+GCTimeRatio=19             # GC 시간 비율 목표 (95% 앱, 5% GC)
  -XX:+PrintGCDetails             # GC 상세 로그
  -XX:+PrintGCDateStamps          # GC 로그에 타임스탬프
  -Xlog:gc*:file=/var/log/app/gc.log:time,uptime:filecount=5,filesize=20m
  -jar securities-app.jar
```

### 6-6. GC 모니터링 도구

```bash
# GC 통계 실시간 확인
jstat -gc <PID> 1000  # 1초마다 출력

# 출력 컬럼 의미
# S0C/S1C: Survivor 0/1 크기 (KB)
# S0U/S1U: Survivor 0/1 사용량 (KB)
# EC: Eden 크기, EU: Eden 사용량
# OC: Old 크기, OU: Old 사용량
# MC: Metaspace 크기, MU: Metaspace 사용량
# YGC: Minor GC 횟수, YGCT: Minor GC 소요 시간 합
# FGC: Full GC 횟수, FGCT: Full GC 소요 시간 합

# 힙 덤프 (OOM 분석)
jmap -dump:live,format=b,file=heap.hprof <PID>
# → Eclipse MAT(Memory Analyzer Tool)로 분석
```

---

## 7. 코틀린이 JVM 위에서 동작하는 방식

코틀린은 JVM 위에서 도는 언어지만, 컴파일러가 자바와 다른 방식으로 바이트코드를 생성한다. 이 차이를 이해하면 성능 특성을 예측할 수 있다.

### 7-1. 데이터 클래스의 바이트코드

```kotlin
data class OrderPrice(val amount: BigDecimal, val currency: String = "KRW")
```

코틀린 컴파일러는 이 한 줄을 자바로 치면 약 80줄에 해당하는 바이트코드로 변환한다:
- `equals()`, `hashCode()`, `toString()`, `copy()`, `componentN()` 메서드 자동 생성
- 기본값 처리를 위한 합성 생성자(Synthetic Constructor) 생성

### 7-2. 인라인 함수 — GC 부담 감소

```kotlin
// 인라인 아닌 람다 → 힙에 Function 객체 생성
fun filterOrders(orders: List<Order>, predicate: (Order) -> Boolean): List<Order> {
    return orders.filter(predicate)  // predicate는 힙에 할당된 Function1 객체
}

// 인라인 람다 → Function 객체 생성 없음, 힙 부담 ↓
inline fun filterOrdersFast(orders: List<Order>, predicate: (Order) -> Boolean): List<Order> {
    return orders.filter(predicate)  // 컴파일 시점에 람다 코드가 호출부에 인라이닝
}
```

`inline` 함수는 JIT 컴파일러의 인라이닝과는 별개로, **코틀린 컴파일러 수준**에서 람다를 인라이닝한다. 결과적으로 람다를 나타내는 `Function` 객체가 힙에 생성되지 않아 GC 압박이 감소한다.

### 7-3. 코루틴의 JVM 표현

```kotlin
suspend fun fetchOrderStatus(orderId: Long): OrderStatus {
    delay(100)  // IO 대기
    return OrderStatus.FILLED
}
```

코틀린 컴파일러는 `suspend` 함수를 **상태 머신(State Machine)**으로 변환한다. 각 `suspend` 포인트가 상태가 되고, 재개(Resume) 시 해당 상태로 점프한다. 이 과정에서 스레드를 점유하지 않아 수천 개의 동시 코루틴을 적은 수의 스레드로 처리할 수 있다.

> 코루틴의 상세 동작은 [13. 코루틴과 비동기 프로그래밍](13-coroutines)에서 다룬다.

### 7-4. Null 처리의 비용

```kotlin
val name: String? = getNameFromDB()  // nullable
val length = name?.length ?: 0       // 안전 호출

// 컴파일된 바이트코드는 대략:
// if (name != null) { name.length() } else { 0 }
// → 추가 null 체크 분기 발생, 성능 영향은 무시할 수준
```

코틀린의 null 안전성은 컴파일 시점 체크다. 런타임에는 null 체크 분기가 추가되지만, 이는 JIT가 빠르게 최적화하므로 실무에서 성능 문제가 되지 않는다.

---

## 8. 스레드 스택과 힙의 관계 — 종합 그림

```
스레드 1 (주문 처리 스레드)          스레드 2 (시세 업데이트 스레드)
─────────────────────────────       ──────────────────────────────
스택 (Thread-Local)                 스택 (Thread-Local)
  ├── Frame: processOrder()           ├── Frame: updatePrice()
  │     로컬: order(참조) ─────────▶  │     로컬: price(참조) ──────────▶
  │     로컬: amount(참조)            │                                    │
  └── Frame: validateOrder()         └── Frame: fetchMarketData()         │
        로컬: isValid(boolean)                                              │
                                                                            │
                          힙 (Heap — 모든 스레드 공유)                      │
                          ┌─────────────────────────────────┐              │
                          │  Young Generation                │              │
                          │  ┌─────┬─────┬────┬────┐        │              │
                          │  │Eden │Eden │ S0 │ S1 │        │◀─────────────┘
                          │  └─────┴─────┴────┴────┘        │
                          │                                  │
              ◀───────────│  Old Generation                  │
              (참조)       │  ┌──────────────────────────┐   │
                          │  │ OrderService (싱글톤)      │   │
                          │  │ Cache<StockCode, Price>   │   │
                          │  └──────────────────────────┘   │
                          └─────────────────────────────────┘

⚠ 두 스레드가 힙의 같은 객체를 동시에 수정하면 경쟁 조건(Race Condition) 발생!
  → 동시성 제어 필요 (synchronized, Mutex, 불변 객체 등)
  → 자세한 내용: [11. 동시성](11-concurrency)
```

---

## 9. 메모리 관련 OOM 유형 및 진단

| OOM 메시지 | 원인 | 진단 도구 |
|-----------|------|---------|
| `Java heap space` | 힙이 가득 참, 메모리 누수 | `jmap -histo`, `heap dump + MAT` |
| `GC overhead limit exceeded` | GC에 98% 시간을 쓰는데 힙 회수 2% 미만 | GC 로그 분석, 힙 덤프 |
| `Metaspace` | 클래스 과다 로딩, 동적 클래스 생성 과다 | `-XX:MaxMetaspaceSize` 설정 |
| `unable to create new native thread` | OS 프로세스 스레드 한도 초과 | `ulimit -u` 확인, 스레드 덤프 |
| `StackOverflowError` | 재귀 깊이 초과 | 스택 트레이스로 재귀 패턴 확인 |

```bash
# 힙 히스토그램 — 어떤 클래스 인스턴스가 많은지
jmap -histo:live <PID> | head -30

# 스레드 덤프 — 스레드 상태 확인
jstack <PID> > thread-dump.txt

# JVM 플래그 확인
jcmd <PID> VM.flags
```

---

## 10. 실무 체크리스트

```
[ ] JDK, JRE, JVM의 포함 관계를 설명할 수 있다
[ ] 코틀린 소스가 바이트코드를 거쳐 실행되는 과정을 그릴 수 있다
[ ] 힙의 Young/Old Generation 구조와 Minor/Major GC 차이를 설명할 수 있다
[ ] Stop-The-World가 증권 시스템에서 왜 위험한지 설명할 수 있다
[ ] G1 GC와 ZGC의 차이를 (짧게라도) 설명할 수 있다
[ ] 스택은 Thread-Local이고 힙은 공유 자원임을 이해한다
[ ] OOM 발생 시 jmap, jstack으로 진단하는 방법을 안다
[ ] jstat -gc <PID>로 실시간 GC 통계를 볼 수 있다
```

---

이전: [16. 개발 환경 구축](16-dev-environment) · 다음: [24. 자바 상호운용(Interop)](24-java-interop) · [전체 커리큘럼](/curriculum)
