# 03. 스프링 부트(Spring Boot)

증권사 코틀린 백엔드는 거의 무조건 **Spring(스프링) / Spring Boot**를 같이 씁니다. 코틀린만으로는 부족하고, 이 프레임워크가 실제 서버를 굴리는 뼈대입니다.

## 알아야 할 스프링 개념 (최소한)

### 의존성 주입 (DI, Dependency Injection)

- 객체를 직접 만들지 않고 스프링이 "주입"해줍니다.
- `@Service`, `@Component`, `@RestController` 같은 애너테이션(`@`)으로 역할을 표시합니다.

### 계층 구조 (백엔드의 90%가 이 흐름)

```
Controller   (요청 받음 — HTTP/JSON)
    ↓
Service      (업무 로직 — 주문 검증, 계산)
    ↓
Repository   (DB 읽고 쓰기)
    ↓
Database
```

각 계층은 자기 책임만 집니다.
- **Controller**: 들어온 요청을 받아 Service에 넘기고, 결과를 응답으로 변환.
- **Service**: 진짜 업무 로직. "예수금 충분한가?" 같은 판단.
- **Repository**: DB 접근만 담당.

### 예시 흐름 — "주문 넣기" API

```kotlin
// 1) Controller — 요청을 받음
@RestController
class OrderController(
    private val orderService: OrderService   // 생성자로 주입받음 (DI)
) {
    @PostMapping("/orders")
    fun placeOrder(@RequestBody req: OrderRequest): OrderResponse {
        return orderService.place(req)        // Service에 위임
    }
}

// 2) Service — 업무 로직
@Service
class OrderService(
    private val orderRepository: OrderRepository
) {
    fun place(req: OrderRequest): OrderResponse {
        // 검증 → 저장 → 결과 반환
        val order = Order(/* ... */)
        orderRepository.save(order)
        return OrderResponse(orderId = order.orderId, status = "ACCEPTED")
    }
}

// 3) Repository — DB 접근
interface OrderRepository : JpaRepository<Order, String>
```

## 같이 따라오는 기술들

| 기술 | 역할 |
|------|------|
| **JPA / Hibernate** | DB 테이블을 코틀린 객체로 다루는 ORM |
| **MyBatis** | SQL을 직접 작성하는 방식 (한국 금융권에서 여전히 많이 씀) |
| **REST API / JSON** | 앱 ↔ 서버 통신 방식 |
| **Gradle** | 빌드 도구 (라이브러리 관리, 컴파일) — `build.gradle.kts` |
| **Spring WebFlux** | 비동기/논블로킹 처리 (코루틴과 함께) |

> 💡 회사가 JPA를 쓰는지 MyBatis를 쓰는지 먼저 확인하세요. 둘은 스타일이 꽤 다릅니다.
> 전통 금융권은 SQL을 직접 통제하려고 MyBatis를 선호하는 경우가 많습니다.

## build.gradle.kts 미리보기

처음 보면 낯설지만, "어떤 라이브러리를 쓰는지" 적어둔 파일입니다.

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.oracle.database.jdbc:ojdbc8")   // 오라클 DB 드라이버
}
```

## 학습 추천

- 인프런/유튜브의 "코틀린 + 스프링 부트" 입문 강의 1개 완주
- 영문이 편하면 [Baeldung](https://www.baeldung.com/kotlin) (코틀린+스프링 예제 풍부)
- 공식: [Spring Boot Docs](https://docs.spring.io/spring-boot/index.html), [Context7](https://context7.com)로 최신 문서 조회

---

다음: [04. 증권 도메인](04-securities-domain)
