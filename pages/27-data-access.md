# 27. 데이터 접근: JPA·MyBatis·트랜잭션

> **학습 목표**: ORM(JPA)과 SQL 매퍼(MyBatis)의 차이를 이해하고, 트랜잭션 전파·격리수준·락을 증권 원장 정합성 관점에서 적용할 수 있다.

---

## 27.1 ORM이란? — 왜 SQL을 직접 안 쓰는가?

### 객체와 관계형 데이터베이스의 불일치

증권 시스템에서 "주문(Order)"을 코드로 표현하면 객체입니다. 하지만 DB는 테이블·행·컬럼으로 저장합니다. 이 둘 사이의 차이를 **임피던스 불일치(Impedance Mismatch)** 라고 합니다.

```
객체 세계                         관계형 DB
───────────────────               ─────────────────
Order                    ←→       orders 테이블
  orderId: String        ←→       order_id VARCHAR(36)
  account: Account       ←→       account_id VARCHAR(10) (FK)
  items: List<OrderItem> ←→       order_items 테이블 (별도 조인)
  상속(Inheritance)      ←→       조인 테이블 or 단일 테이블
```

**ORM(Object-Relational Mapping)** 은 이 변환을 자동화하는 기술입니다.

```kotlin
// ORM 없이 직접 JDBC 코딩
fun findOrderById(orderId: String): Order {
    val sql = "SELECT o.order_id, o.account_id, o.ticker, o.quantity, o.price, " +
              "o.status, o.created_at FROM orders o WHERE o.order_id = ?"
    return jdbcTemplate.queryForObject(sql, { rs, _ ->
        Order(
            orderId = rs.getString("order_id"),
            accountId = rs.getString("account_id"),
            ticker = rs.getString("ticker"),
            quantity = rs.getInt("quantity"),
            price = rs.getBigDecimal("price"),
            status = OrderStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime()
        )
    }, orderId)
}

// JPA 사용 시
fun findOrderById(orderId: String): Order? =
    orderRepository.findById(orderId).orElse(null)
```

---

## 27.2 JPA / Hibernate 엔티티 설계

### 기본 엔티티

```kotlin
@Entity
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_orders_account_id", columnList = "account_id"),
        Index(name = "idx_orders_ticker_status", columnList = "ticker, status"),
        Index(name = "idx_orders_created_at", columnList = "created_at")
    ]
)
class Order(
    @Id
    @Column(name = "order_id", length = 36)
    val orderId: String = UUID.randomUUID().toString(),

    @Column(name = "account_id", nullable = false, length = 10)
    val accountId: String,

    @Column(name = "ticker", nullable = false, length = 6)
    val ticker: String,

    @Enumerated(EnumType.STRING)  // ← STRING 권장 (ORDINAL은 Enum 순서 변경 시 데이터 오염)
    @Column(name = "side", nullable = false, length = 10)
    val side: OrderSide,

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    val orderType: OrderType,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    @Column(name = "price", precision = 15, scale = 2)
    var price: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING,

    @Version  // 낙관적 락(Optimistic Lock) — 아래 섹션 참조
    @Column(name = "version")
    var version: Long = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    // 비즈니스 메서드 — 상태 변경 로직은 엔티티 내부에
    fun cancel() {
        check(status == OrderStatus.PENDING) { "미체결 주문만 취소 가능합니다" }
        this.status = OrderStatus.CANCELLED
    }

    fun fill(filledQuantity: Int) {
        require(filledQuantity > 0) { "체결 수량은 0보다 커야 합니다" }
        require(filledQuantity <= this.quantity) { "체결 수량이 주문 수량을 초과합니다" }
        this.quantity -= filledQuantity
        if (this.quantity == 0) this.status = OrderStatus.FILLED
    }
}
```

> **코틀린 + JPA 주의사항**
> - JPA는 기본 생성자(no-arg constructor)를 요구합니다. `kotlin("plugin.jpa")`가 `@Entity` 클래스에 자동으로 추가해줍니다.
> - 엔티티는 `data class`로 만들지 마세요. `equals()`/`hashCode()`가 모든 필드를 비교해 영속성 컨텍스트에서 문제가 됩니다.
> - `@ManyToOne` 등 연관관계 필드를 `data class` `copy()`로 복사하면 예상치 못한 동작이 발생합니다.

### 연관관계 매핑

```kotlin
@Entity
@Table(name = "accounts")
class Account(
    @Id
    @Column(name = "account_id", length = 10)
    val accountId: String,

    @Column(name = "account_number", unique = true, nullable = false, length = 20)
    val accountNumber: String,

    @Column(name = "balance", precision = 20, scale = 2, nullable = false)
    var balance: BigDecimal = BigDecimal.ZERO,

    // 양방향: 한 계좌에 여러 주문
    @OneToMany(mappedBy = "account", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val orders: MutableList<Order> = mutableListOf()
)

@Entity
@Table(name = "orders")
class Order(
    // ... 위 코드와 동일

    // 계좌와 다대일 관계
    @ManyToOne(fetch = FetchType.LAZY)  // ← LAZY가 기본값이 되어야 함
    @JoinColumn(name = "account_id")
    val account: Account
)
```

> **항상 `FetchType.LAZY` 사용**: `EAGER`는 연관 엔티티를 무조건 즉시 로딩해 불필요한 쿼리가 발생합니다. 특히 컬렉션(`@OneToMany`)에서 EAGER는 N+1 문제의 주 원인입니다.

---

## 27.3 영속성 컨텍스트(Persistence Context)

### 1차 캐시와 동일성 보장

```kotlin
@Service
@Transactional
class OrderService(private val em: EntityManager) {

    fun demonstratePersistenceContext(orderId: String) {
        val order1 = em.find(Order::class.java, orderId)  // DB 조회 (SQL 실행)
        val order2 = em.find(Order::class.java, orderId)  // 1차 캐시에서 조회 (SQL 미실행!)

        println(order1 === order2)  // true — 같은 인스턴스!
    }
}
```

### 더티 체킹(Dirty Checking)

```kotlin
@Service
@Transactional
class OrderService(private val orderRepository: OrderRepository) {

    fun cancelOrder(orderId: String) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException(orderId) }

        order.cancel()  // 상태만 변경
        // orderRepository.save(order) 호출 불필요!
        // 트랜잭션 커밋 시 JPA가 변경 감지 → UPDATE 자동 실행
    }
}
```

**더티 체킹 동작 원리**:

```
엔티티 조회
    ↓
스냅샷 저장 (조회 시점 상태 복사)
    ↓
비즈니스 로직 실행 (엔티티 상태 변경)
    ↓
트랜잭션 커밋 시
    ↓
현재 상태 vs 스냅샷 비교
    ↓
변경된 필드만 UPDATE SQL 생성 & 실행
```

> **함정**: `@Transactional`이 없는 서비스 메서드에서 엔티티를 변경하면 더티 체킹이 동작하지 않습니다. 의도치 않은 미적용 사례가 많으니 항상 확인하세요.

---

## 27.4 지연 로딩과 N+1 문제

### N+1 문제 발생 시나리오

```kotlin
@Service
@Transactional(readOnly = true)
class TradeHistoryService(
    private val accountRepository: AccountRepository
) {
    // ❌ N+1 문제 발생!
    fun getAccountsWithOrders(): List<AccountSummary> {
        val accounts = accountRepository.findAll()  // 쿼리 1번: 계좌 100개 조회

        return accounts.map { account ->
            AccountSummary(
                accountId = account.accountId,
                orderCount = account.orders.size  // 계좌마다 쿼리 1번씩: 총 100번!
                // ↑ LAZY 로딩이므로 .size 접근 시 SELECT * FROM orders WHERE account_id = ?
            )
        }
        // 총 쿼리: 1 + 100 = 101번 → "N+1 문제"
    }
}
```

### 해결책 1: Fetch Join (JPQL)

```kotlin
interface AccountRepository : JpaRepository<Account, String> {

    @Query("""
        SELECT DISTINCT a FROM Account a
        LEFT JOIN FETCH a.orders o
        WHERE o.status = :status
    """)
    fun findAccountsWithOrders(@Param("status") status: OrderStatus): List<Account>
}

// 실행 SQL: 1번의 JOIN 쿼리
// SELECT a.*, o.* FROM accounts a LEFT JOIN orders o ON a.account_id = o.account_id
// WHERE o.status = 'PENDING'
```

### 해결책 2: @EntityGraph

```kotlin
interface OrderRepository : JpaRepository<Order, String> {

    @EntityGraph(attributePaths = ["account"])  // account를 함께 로딩
    fun findByStatus(status: OrderStatus): List<Order>

    @EntityGraph(attributePaths = ["account", "orderItems"])
    fun findWithDetailsById(orderId: String): Optional<Order>
}
```

### 해결책 3: BatchSize (컬렉션 N+1 완화)

```kotlin
@Entity
class Account(
    // ...
    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @BatchSize(size = 100)  // IN 절로 한 번에 100개 로딩
    val orders: MutableList<Order> = mutableListOf()
)

// 또는 전역 설정 (application.properties)
// spring.jpa.properties.hibernate.default_batch_fetch_size=100
```

**N+1 vs Fetch Join 비교**:

| 방식 | 쿼리 수 | 메모리 | 페이징 가능 |
|------|---------|--------|------------|
| N+1 | 1 + N번 | 적음 | 가능 |
| Fetch Join | 1번 | 더 큼 (카테시안 곱) | ❌ (컬렉션) |
| @BatchSize | 1 + N/size번 | 중간 | 가능 |

> **증권 거래내역 조회 패턴**: 계좌별 거래내역 조회는 N+1이 발생하기 쉬운 전형적인 케이스입니다. 운영 환경에서 `spring.jpa.show-sql=true` + `p6spy` 등으로 실제 실행 쿼리를 모니터링하는 습관을 들이세요.

---

## 27.5 페이징(Paging)

```kotlin
interface OrderRepository : JpaRepository<Order, String> {
    fun findByAccountIdOrderByCreatedAtDesc(
        accountId: String,
        pageable: Pageable
    ): Page<Order>
}

@Service
@Transactional(readOnly = true)
class OrderService(private val orderRepository: OrderRepository) {

    fun getOrderHistory(accountId: String, page: Int, size: Int): Page<OrderResponse> {
        val pageable = PageRequest.of(
            page, size,
            Sort.by(Sort.Direction.DESC, "createdAt")
        )
        return orderRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
            .map { it.toResponse() }
    }
}

// 컨트롤러
@GetMapping("/accounts/{accountId}/orders")
fun getOrderHistory(
    @PathVariable accountId: String,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int
): Page<OrderResponse> = orderService.getOrderHistory(accountId, page, size)
```

> **페이징 + Fetch Join 함정**: `@OneToMany` 컬렉션을 Fetch Join하면서 페이징을 동시에 사용하면 Hibernate가 **메모리에서 페이징**합니다(`HHH90003004` 경고). 전체 데이터를 메모리에 로드 후 잘라내므로 OOM(Out of Memory) 위험이 있습니다. 컬렉션 페이징에는 `@BatchSize`를 사용하세요.

---

## 27.6 JPQL과 QueryDSL

### JPQL (JPA Query Language)

```kotlin
interface OrderRepository : JpaRepository<Order, String> {

    // 메서드명 쿼리 (단순한 경우)
    fun findByAccountIdAndStatus(accountId: String, status: OrderStatus): List<Order>

    // @Query JPQL (복잡한 경우)
    @Query("""
        SELECT o FROM Order o
        WHERE o.accountId = :accountId
          AND o.status IN :statuses
          AND o.createdAt BETWEEN :from AND :to
        ORDER BY o.createdAt DESC
    """)
    fun findOrderHistory(
        @Param("accountId") accountId: String,
        @Param("statuses") statuses: List<OrderStatus>,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        pageable: Pageable
    ): Page<Order>

    // 네이티브 SQL (복잡한 분석 쿼리)
    @Query(
        value = """
            SELECT ticker, SUM(quantity * price) as total_amount, COUNT(*) as order_count
            FROM orders
            WHERE account_id = :accountId AND status = 'FILLED'
            GROUP BY ticker
            ORDER BY total_amount DESC
            LIMIT 10
        """,
        nativeQuery = true
    )
    fun findTopTradedTickers(@Param("accountId") accountId: String): List<TickerSummaryProjection>
}

// Projection 인터페이스 (부분 컬럼 조회)
interface TickerSummaryProjection {
    fun getTicker(): String
    fun getTotalAmount(): BigDecimal
    fun getOrderCount(): Long
}
```

### QueryDSL (타입 안전 동적 쿼리)

```kotlin
// build.gradle.kts에 추가 필요
// implementation("com.querydsl:querydsl-jpa:5.0.0:jakarta")
// kapt("com.querydsl:querydsl-apt:5.0.0:jakarta")

@Repository
class OrderQueryRepository(private val queryFactory: JPAQueryFactory) {

    fun findOrders(filter: OrderFilter, pageable: Pageable): Page<Order> {
        val order = QOrder.order  // 컴파일 타임에 생성된 Q타입

        val condition = BooleanBuilder().apply {
            filter.accountId?.let { and(order.accountId.eq(it)) }
            filter.ticker?.let { and(order.ticker.eq(it)) }
            filter.status?.let { and(order.status.eq(it)) }
            filter.from?.let { and(order.createdAt.goe(it.atStartOfDay())) }
            filter.to?.let { and(order.createdAt.lt(it.plusDays(1).atStartOfDay())) }
            filter.minAmount?.let { and(order.price.multiply(order.quantity).goe(it)) }
        }

        val results = queryFactory
            .selectFrom(order)
            .where(condition)
            .orderBy(order.createdAt.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val total = queryFactory
            .select(order.count())
            .from(order)
            .where(condition)
            .fetchOne() ?: 0L

        return PageImpl(results, pageable, total)
    }
}

data class OrderFilter(
    val accountId: String? = null,
    val ticker: String? = null,
    val status: OrderStatus? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val minAmount: BigDecimal? = null
)
```

---

## 27.7 MyBatis — SQL을 직접 작성하는 이유

### 한국 금융권에서 MyBatis를 선호하는 이유

국내 대형 증권사, 은행, 보험사 대부분은 **MyBatis(또는 iBatis)** 를 사용합니다.

| 이유 | 설명 |
|------|------|
| **SQL 직접 제어** | 금융 쿼리는 복잡하고 성능이 중요 — ORM의 자동 쿼리를 믿기 어려움 |
| **DBA와 협업** | DBA가 검수한 SQL을 그대로 사용 가능 |
| **레거시 DB 구조** | 20~30년 된 테이블 구조에 ORM 매핑이 어려움 |
| **복잡한 조인** | 5~10개 테이블 조인, 분석 쿼리는 JPQL보다 SQL이 명확 |
| **감사·규정** | 실행 SQL이 명확히 보이는 것을 선호 (규제 기관 감사 대비) |

### MyBatis 설정 (스프링 부트)

```kotlin
// build.gradle.kts
implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
```

```yaml
# application.yml
mybatis:
  mapper-locations: classpath:mappers/**/*.xml
  configuration:
    map-underscore-to-camel-case: true    # snake_case → camelCase 자동 변환
    default-fetch-size: 100
    default-statement-timeout: 30
  type-aliases-package: com.example.brokerage.domain
```

### 매퍼 인터페이스 + XML

```kotlin
// 매퍼 인터페이스
@Mapper
interface OrderMapper {
    fun findById(orderId: String): Order?
    fun findByAccountId(params: Map<String, Any>): List<Order>
    fun insert(order: Order): Int
    fun updateStatus(params: Map<String, Any>): Int
    fun findOrderSummary(filter: OrderFilter): List<OrderSummary>
}
```

```xml
<!-- resources/mappers/order/OrderMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.brokerage.mapper.OrderMapper">

    <!-- ResultMap: DB 컬럼 → 객체 매핑 정의 -->
    <resultMap id="OrderResultMap" type="Order">
        <id     property="orderId"   column="order_id"/>
        <result property="accountId" column="account_id"/>
        <result property="ticker"    column="ticker"/>
        <result property="side"      column="side"       typeHandler="org.apache.ibatis.type.EnumTypeHandler"/>
        <result property="orderType" column="order_type" typeHandler="org.apache.ibatis.type.EnumTypeHandler"/>
        <result property="quantity"  column="quantity"/>
        <result property="price"     column="price"/>
        <result property="status"    column="status"     typeHandler="org.apache.ibatis.type.EnumTypeHandler"/>
        <result property="createdAt" column="created_at"/>
    </resultMap>

    <!-- 단건 조회 -->
    <select id="findById" parameterType="string" resultMap="OrderResultMap">
        SELECT order_id, account_id, ticker, side, order_type,
               quantity, price, status, version, created_at, updated_at
        FROM orders
        WHERE order_id = #{orderId}
    </select>

    <!-- 동적 쿼리: 계좌별 주문 조회 -->
    <select id="findByAccountId" parameterType="map" resultMap="OrderResultMap">
        SELECT order_id, account_id, ticker, side, order_type,
               quantity, price, status, created_at
        FROM orders
        <where>
            account_id = #{accountId}
            <if test="status != null">
                AND status = #{status}
            </if>
            <if test="ticker != null and ticker != ''">
                AND ticker = #{ticker}
            </if>
            <if test="from != null">
                AND created_at >= #{from}
            </if>
            <if test="to != null">
                AND created_at &lt; #{to}
            </if>
        </where>
        ORDER BY created_at DESC
        LIMIT #{size} OFFSET #{offset}
    </select>

    <!-- 집계 분석 쿼리 (JPA로 표현하기 어려운 복잡한 SQL) -->
    <select id="findOrderSummary" resultType="OrderSummary">
        SELECT
            o.ticker,
            s.company_name,
            SUM(CASE WHEN o.side = 'BUY'  THEN o.quantity ELSE 0 END) AS buy_quantity,
            SUM(CASE WHEN o.side = 'SELL' THEN o.quantity ELSE 0 END) AS sell_quantity,
            SUM(CASE WHEN o.status = 'FILLED' THEN o.quantity * o.price ELSE 0 END) AS filled_amount,
            COUNT(*) AS order_count
        FROM orders o
        JOIN stock_info s ON o.ticker = s.ticker
        <where>
            o.account_id = #{accountId}
            <if test="from != null">AND o.created_at >= #{from}</if>
            <if test="to != null">AND o.created_at &lt; #{to}</if>
        </where>
        GROUP BY o.ticker, s.company_name
        ORDER BY filled_amount DESC
    </select>

    <!-- 삽입 -->
    <insert id="insert" parameterType="Order">
        INSERT INTO orders (
            order_id, account_id, ticker, side, order_type,
            quantity, price, status, version, created_at, updated_at
        ) VALUES (
            #{orderId}, #{accountId}, #{ticker}, #{side}, #{orderType},
            #{quantity}, #{price}, #{status}, 0, NOW(), NOW()
        )
    </insert>

    <!-- 상태 업데이트 -->
    <update id="updateStatus" parameterType="map">
        UPDATE orders
        SET status = #{status}, updated_at = NOW()
        WHERE order_id = #{orderId}
          AND status = #{expectedStatus}  <!-- 낙관적 제어 -->
    </update>

</mapper>
```

### MyBatis 서비스 계층 사용

```kotlin
@Service
@Transactional
class OrderService(
    private val orderMapper: OrderMapper  // MyBatis 매퍼 주입
) {
    fun getOrders(accountId: String, filter: OrderFilter): List<Order> {
        val params = mapOf(
            "accountId" to accountId,
            "status" to filter.status?.name,
            "ticker" to filter.ticker,
            "from" to filter.from?.atStartOfDay(),
            "to" to filter.to?.plusDays(1)?.atStartOfDay(),
            "size" to filter.size,
            "offset" to filter.page * filter.size
        )
        return orderMapper.findByAccountId(params)
    }
}
```

---

## 27.8 JPA vs MyBatis 선택 기준

| 기준 | JPA 선택 | MyBatis 선택 |
|------|----------|--------------|
| **도메인 복잡성** | 도메인 모델 풍부, 객체 관계 복잡 | 테이블 중심, 레거시 스키마 |
| **쿼리 복잡성** | CRUD 위주, 단순 조회 | 복잡한 조인, 집계, 분석 |
| **팀 역량** | ORM 경험 있음 | SQL 전문가, DBA 협업 |
| **성능 요구** | 일반적인 트래픽 | 쿼리 레벨 최적화 필요 |
| **기존 시스템** | 신규 프로젝트 | 레거시 시스템 연동 |
| **한국 금융권** | 신규 핀테크, 인터넷은행 | 전통 증권사, 은행 |

> **실무 팁**: 많은 프로젝트가 **JPA + MyBatis 혼용** 전략을 씁니다. 기본 CRUD는 JPA의 `JpaRepository`로, 복잡한 조회·분석 쿼리는 MyBatis 매퍼 XML로 작성합니다.

---

## 27.9 @Transactional 심화

### 전파(Propagation) 규칙

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val auditService: AuditService,
    private val notificationService: NotificationService
) {

    @Transactional  // 기본: REQUIRED
    fun placeOrder(order: Order): Order {
        val saved = orderRepository.save(order)

        // REQUIRED: 같은 트랜잭션에 참여 (주문 저장 실패 시 감사 로그도 롤백)
        auditService.logOrderCreated(saved)

        // REQUIRES_NEW: 별도 트랜잭션 (알림 실패해도 주문은 성공)
        notificationService.sendOrderConfirmation(saved)

        return saved
    }
}

@Service
class AuditService(private val auditRepository: AuditRepository) {

    @Transactional(propagation = Propagation.REQUIRED)  // 기본값: 부모 트랜잭션 참여
    fun logOrderCreated(order: Order) {
        auditRepository.save(AuditLog(orderId = order.orderId, action = "ORDER_CREATED"))
    }
}

@Service
class NotificationService(private val fcmClient: FcmClient) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // 새 트랜잭션 시작
    fun sendOrderConfirmation(order: Order) {
        // 알림 전송 실패해도 별도 트랜잭션이라 주문 트랜잭션에 영향 없음
        fcmClient.send(order.accountId, "주문이 접수되었습니다: ${order.orderId}")
    }
}
```

### 전파 유형 요약

| 전파 유형 | 기존 트랜잭션 있을 때 | 기존 트랜잭션 없을 때 |
|-----------|----------------------|----------------------|
| `REQUIRED` (기본) | 참여 | 새로 시작 |
| `REQUIRES_NEW` | 기존 일시 중단, 새로 시작 | 새로 시작 |
| `SUPPORTS` | 참여 | 트랜잭션 없이 실행 |
| `NOT_SUPPORTED` | 기존 일시 중단, 없이 실행 | 없이 실행 |
| `MANDATORY` | 참여 | 예외 발생 |
| `NEVER` | 예외 발생 | 없이 실행 |
| `NESTED` | 중첩 트랜잭션 | 새로 시작 |

### 격리 수준(Isolation Level)

```kotlin
// 증권 시스템에서 격리 수준 선택
@Service
class BalanceService(private val accountRepository: AccountRepository) {

    // 잔고 조회: READ COMMITTED (기본값 권장)
    // 다른 트랜잭션이 커밋한 데이터는 읽을 수 있음
    @Transactional(
        readOnly = true,
        isolation = Isolation.READ_COMMITTED
    )
    fun getBalance(accountId: String): BigDecimal {
        return accountRepository.findById(accountId)
            .orElseThrow { AccountNotFoundException(accountId) }
            .balance
    }

    // 잔고 차감: SERIALIZABLE (최강 격리, 동시성 낮음)
    // 팬텀 리드, 더티 리드, 반복 불가능 읽기 모두 방지
    // → 실제로는 락(@Lock)을 사용하는 것이 성능 면에서 유리
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun deductBalance(accountId: String, amount: BigDecimal) {
        val account = accountRepository.findById(accountId)
            .orElseThrow { AccountNotFoundException(accountId) }

        if (account.balance < amount) {
            throw InsufficientBalanceException(
                requiredAmount = amount,
                availableAmount = account.balance
            )
        }
        account.balance = account.balance.subtract(amount)
    }
}
```

### 격리 수준 비교

| 격리 수준 | Dirty Read | Non-Repeatable Read | Phantom Read | 성능 |
|-----------|-----------|---------------------|--------------|------|
| `READ_UNCOMMITTED` | ✅ 가능 | ✅ 가능 | ✅ 가능 | 가장 빠름 |
| `READ_COMMITTED` | ❌ 방지 | ✅ 가능 | ✅ 가능 | 빠름 |
| `REPEATABLE_READ` | ❌ 방지 | ❌ 방지 | ✅ 가능 | 중간 |
| `SERIALIZABLE` | ❌ 방지 | ❌ 방지 | ❌ 방지 | 느림 |

> **증권 실무**: MySQL(InnoDB)의 기본 격리 수준은 `REPEATABLE_READ`입니다. Oracle은 `READ_COMMITTED`입니다. 증권사 레거시 시스템 대부분이 Oracle을 사용하므로 주의가 필요합니다.

### readOnly = true

```kotlin
// 조회 전용 트랜잭션
@Transactional(readOnly = true)  // ← 반드시 붙이세요
fun getOrderHistory(accountId: String): List<Order> {
    return orderRepository.findByAccountId(accountId)
}
```

**`readOnly = true`의 효과**:
- Hibernate의 더티 체킹(스냅샷 비교) 비활성화 → 성능 향상
- 읽기 전용 DB 복제본(Replica)으로 라우팅 가능 (DB 라우팅 설정 필요)
- 실수로 엔티티를 수정해도 DB에 반영되지 않음 (안전망)

### 롤백 규칙

```kotlin
// 기본: RuntimeException(unchecked)은 롤백, CheckedException은 롤백 안 함
@Transactional
fun placeOrder(order: Order) {
    orderRepository.save(order)
    throw RuntimeException("런타임 예외")  // ← 롤백됨 (기본 동작)
}

// Checked Exception도 롤백하려면 명시
@Transactional(rollbackFor = [IOException::class, BusinessException::class])
fun processWithCheckedException(order: Order) {
    orderRepository.save(order)
    externalApiClient.notify(order)  // IOException 발생 시 롤백
}

// 특정 예외는 롤백 안 함 (로그 기록 등 실패해도 주문은 유지)
@Transactional(noRollbackFor = [NotificationException::class])
fun placeOrderWithNotification(order: Order) {
    orderRepository.save(order)
    notificationService.send(order)  // 실패해도 주문 롤백 안 됨
}
```

> **코틀린 주의**: 코틀린은 Checked Exception 개념이 없습니다. 코틀린에서 `IOException`을 던져도 컴파일러가 강제하지 않지만, `@Transactional`은 Java의 Checked Exception 기준으로 동작합니다. 외부 자바 라이브러리 호출 시 주의하세요.

---

## 27.10 트랜잭션 함정 — self-invocation

[25. 스프링 코어](25-spring-core#2510-프록시-동작-원리와-self-invocation-함정)에서 다룬 self-invocation 문제가 `@Transactional`에서도 그대로 적용됩니다.

```kotlin
@Service
class OrderService(private val orderRepository: OrderRepository) {

    fun placeOrder(order: Order): Order {
        validateOrder(order)
        return saveOrder(order)  // ❌ this.saveOrder() → 프록시 미통과 → @Transactional 미적용!
    }

    @Transactional  // ← 적용 안 됨!
    fun saveOrder(order: Order): Order {
        return orderRepository.save(order)
    }
}

// ✅ 해결: 별도 빈으로 분리
@Service
class OrderPersistenceService(private val orderRepository: OrderRepository) {
    @Transactional
    fun save(order: Order): Order = orderRepository.save(order)
}

@Service
class OrderService(
    private val orderPersistenceService: OrderPersistenceService
) {
    fun placeOrder(order: Order): Order {
        validateOrder(order)
        return orderPersistenceService.save(order)  // ✅ 프록시를 거침
    }
}
```

---

## 27.11 낙관적 락과 비관적 락

### 낙관적 락(Optimistic Lock) — @Version

동시에 같은 데이터를 수정하는 충돌을 **"낮은 빈도"** 라고 가정하고, 충돌 시 감지합니다.

```kotlin
@Entity
class Order(
    @Id val orderId: String,
    var status: OrderStatus,
    var quantity: Int,

    @Version  // 이 필드가 낙관적 락의 핵심
    var version: Long = 0
)

// 동작 원리:
// 조회 시 version = 5
// UPDATE orders SET status = 'CANCELLED', version = 6
//         WHERE order_id = ? AND version = 5  ← 버전 조건 추가
// 다른 트랜잭션이 이미 수정해서 version = 6이면 → 0건 수정 → OptimisticLockingFailureException
```

```kotlin
@Service
class OrderService(private val orderRepository: OrderRepository) {

    @Transactional
    @Retryable(
        value = [OptimisticLockingFailureException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100)
    )
    fun cancelOrder(orderId: String) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException(orderId) }
        order.cancel()  // 더티 체킹으로 UPDATE (version 조건 포함)
    }
}
```

### 비관적 락(Pessimistic Lock) — @Lock

충돌이 **"높은 빈도"** 라고 가정하고, 조회 시점에 미리 DB 락을 겁니다.

```kotlin
interface AccountRepository : JpaRepository<Account, String> {

    // SELECT ... FOR UPDATE — 다른 트랜잭션이 읽기 가능, 쓰기 불가
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    fun findByIdWithLock(@Param("accountId") accountId: String): Account?

    // SELECT ... FOR SHARE — 다른 트랜잭션이 읽기 가능, 쓰기 불가
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    fun findByIdForRead(@Param("accountId") accountId: String): Account?
}

@Service
class BalanceService(private val accountRepository: AccountRepository) {

    @Transactional
    fun deductBalance(accountId: String, amount: BigDecimal) {
        // 잔고 차감: 동시 요청 시 한 번에 하나씩만 처리
        val account = accountRepository.findByIdWithLock(accountId)
            ?: throw AccountNotFoundException(accountId)

        if (account.balance < amount) {
            throw InsufficientBalanceException(
                requiredAmount = amount,
                availableAmount = account.balance
            )
        }
        account.balance = account.balance.subtract(amount)
        // 트랜잭션 종료 시 락 해제
    }
}
```

### 낙관적 vs 비관적 락 선택

| 기준 | 낙관적 락 | 비관적 락 |
|------|-----------|-----------|
| **충돌 빈도** | 낮음 (READ 많고 WRITE 적음) | 높음 (동시 WRITE 많음) |
| **성능** | 충돌 없으면 빠름 | 항상 DB 락 오버헤드 |
| **대기** | 재시도 로직 필요 | 다른 트랜잭션이 대기 |
| **증권 적용** | 주문 취소/수정 | 잔고 차감, 한도 체크 |
| **교착상태** | 없음 | 가능 (Deadlock) |

> **증권 도메인 원장 정합성**: 투자자 예수금(잔고) 차감은 비관적 락이 적합합니다. 동시에 여러 주문이 접수될 때 잔고를 중복 사용하는 오버슈팅(Overshooting)이 발생하면 안 됩니다. 반면 주문 상태 변경(접수→체결)은 낙관적 락과 재시도로 충분합니다.

---

## 27.12 커넥션 풀(HikariCP)

### HikariCP 설정

스프링 부트 기본 커넥션 풀은 **HikariCP**입니다.

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/brokerage
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: BrokerageHikariPool
      minimum-idle: 10          # 유휴 시 최소 커넥션 수
      maximum-pool-size: 50     # 최대 커넥션 수
      idle-timeout: 600000      # 유휴 커넥션 유지 시간 (10분)
      connection-timeout: 30000 # 커넥션 획득 대기 시간 (30초)
      max-lifetime: 1800000     # 커넥션 최대 수명 (30분)
      keepalive-time: 60000     # DB에 keepalive 주기 (1분)
      connection-test-query: SELECT 1  # 커넥션 유효성 검사 쿼리
      leak-detection-threshold: 60000  # 커넥션 누수 감지 (1분 이상 미반환 시 경고)
```

### 커넥션 풀 크기 계산

```
적정 풀 크기 ≈ (CPU 코어 수 × 2) + 유효 디스크 수

예시: 4코어 서버, SSD 기준
= (4 × 2) + 1 = 9 → 10 전후

주의: DB 서버의 max_connections도 고려
전체 앱 서버 수 × 풀 최대 크기 < DB max_connections
```

> **함정**: 풀 크기를 무작정 크게 잡으면 오히려 성능이 떨어집니다. DB 서버 CPU가 과부하되고, 커넥션 경합이 증가합니다. HikariCP 블로그의 ["About Pool Sizing"](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)을 꼭 읽어보세요.

### 커넥션 누수 감지

```kotlin
// 트랜잭션 없이 EntityManager를 장시간 점유하는 안티패턴
@Service
class BadService(private val em: EntityManager) {
    fun processAll() {
        val orders = em.createQuery("SELECT o FROM Order o").resultList
        orders.forEach { order ->
            Thread.sleep(5000)  // 5초 대기 × 10000개 = DB 커넥션 점유!
            processOrder(order as Order)
        }
        // ← 커넥션이 여기서야 반환됨 → 누수처럼 동작
    }
}

// ✅ 올바른 패턴: 페이징으로 분할 처리
@Service
class GoodService(private val orderRepository: OrderRepository) {
    @Transactional
    fun processAll() {
        var page = 0
        var hasNext = true
        while (hasNext) {
            val orders = orderRepository.findAll(PageRequest.of(page, 100))
            orders.forEach { processOrder(it) }
            hasNext = orders.hasNext()
            page++
        }
    }
}
```

---

## 27.13 원장 데이터 정합성 관점

### 금융 시스템의 데이터 정합성 요구사항

증권 원장(Ledger)은 잔고, 보유 종목, 거래 내역을 기록하는 핵심 데이터입니다.

```kotlin
// 주문 체결 시 원장 업데이트 — 반드시 원자적(Atomic)으로
@Service
class TradeSettlementService(
    private val accountRepository: AccountRepository,
    private val positionRepository: PositionRepository,
    private val tradeRepository: TradeRepository,
    private val orderRepository: OrderRepository
) {

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun settleTrade(trade: Trade) {
        // 1. 매수자 잔고 차감 (비관적 락)
        val buyerAccount = accountRepository.findByIdWithLock(trade.buyerId)
            ?: throw AccountNotFoundException(trade.buyerId)
        val tradeAmount = trade.price.multiply(BigDecimal(trade.quantity))
        buyerAccount.balance = buyerAccount.balance.subtract(tradeAmount)

        // 2. 매도자 잔고 증가 (비관적 락)
        val sellerAccount = accountRepository.findByIdWithLock(trade.sellerId)
            ?: throw AccountNotFoundException(trade.sellerId)
        sellerAccount.balance = sellerAccount.balance.add(tradeAmount)

        // 3. 보유 포지션 업데이트
        positionRepository.upsertPosition(
            accountId = trade.buyerId,
            ticker = trade.ticker,
            deltaQuantity = trade.quantity,
            avgPrice = trade.price
        )
        positionRepository.upsertPosition(
            accountId = trade.sellerId,
            ticker = trade.ticker,
            deltaQuantity = -trade.quantity,
            avgPrice = trade.price
        )

        // 4. 거래 내역 기록
        tradeRepository.save(trade)

        // 5. 원주문 상태 업데이트
        orderRepository.findById(trade.buyOrderId).ifPresent { it.fill(trade.quantity) }
        orderRepository.findById(trade.sellOrderId).ifPresent { it.fill(trade.quantity) }

        // 모든 작업이 하나의 트랜잭션 — 일부 실패 시 전체 롤백
    }
}
```

### 이중 장부 방지 — 유니크 제약

```kotlin
@Entity
@Table(
    name = "trades",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_trades_buy_sell_order",
            columnNames = ["buy_order_id", "sell_order_id", "sequence_no"]
        )
    ]
)
class Trade(
    @Id val tradeId: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val sequenceNo: Int,  // 부분 체결 순서
    val quantity: Int,
    val price: BigDecimal,
    val tradedAt: LocalDateTime = LocalDateTime.now()
)
```

---

## 27.14 실전 체크리스트

### JPA 체크리스트

- [ ] 엔티티에 `@Enumerated(EnumType.STRING)` 사용하는가? (ORDINAL은 위험)
- [ ] 연관관계 필드는 `FetchType.LAZY` 사용하는가?
- [ ] 컬렉션 조회 시 N+1 문제를 Fetch Join 또는 @BatchSize로 해결했는가?
- [ ] 컬렉션 + 페이징 동시 사용 시 메모리 페이징 경고를 확인했는가?
- [ ] 조회 메서드에 `@Transactional(readOnly = true)` 붙였는가?
- [ ] `kotlin("plugin.jpa")`가 build.gradle.kts에 포함되어 있는가?

### 트랜잭션 체크리스트

- [ ] `@Transactional` 메서드가 같은 클래스 내부에서 호출되지 않는가 (self-invocation)?
- [ ] Checked Exception 롤백이 필요하면 `rollbackFor`를 명시했는가?
- [ ] 잔고 차감 등 동시성 민감한 코드에 비관적 락을 적용했는가?
- [ ] 낙관적 락 충돌 시 재시도 로직이 있는가?
- [ ] 하나의 트랜잭션에서 너무 많은 작업을 처리하고 있지 않은가?

### HikariCP 체크리스트

- [ ] `leak-detection-threshold` 설정으로 커넥션 누수를 감지하는가?
- [ ] 풀 최대 크기가 DB `max_connections` 대비 적절한가?
- [ ] 운영 환경에서 HikariCP 메트릭을 모니터링하는가?

---

## 정리

- **JPA**: 도메인 모델 중심 개발, 더티 체킹·영속성 컨텍스트 이해 필수, N+1 문제 주의
- **MyBatis**: 한국 금융권 선호, 복잡한 SQL·레거시 스키마에 강함, JPA와 혼용 가능
- **트랜잭션**: `REQUIRED` 기본, `REQUIRES_NEW`로 독립 트랜잭션, self-invocation 함정 주의
- **격리수준**: 대부분 `READ_COMMITTED`로 충분, 원장 처리는 비관적 락으로 보완
- **락 전략**: 잔고·한도처럼 충돌 빈번한 데이터는 비관적 락, 주문 상태는 낙관적 락
- **HikariCP**: 풀 크기는 CPU×2+1 공식, 누수 감지 설정 필수

---

이전: [26. 스프링 웹 계층: MVC와 WebFlux](26-spring-boot-web) · 다음: [03. 스프링 부트 입문](03-spring-boot) · [전체 커리큘럼](/curriculum)
