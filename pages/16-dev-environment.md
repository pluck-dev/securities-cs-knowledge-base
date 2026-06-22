# 16. 개발 환경 구축

> 대상: 코틀린과 개발 도구가 처음인 증권사 백엔드 개발자 지망자  
> 목표: "내 PC에서 첫 주문 출력 프로그램이 돌아간다"는 상태까지 도달하기

---

## 1. JDK 설치와 LTS 버전 전략

### 1-1. JDK란 무엇인가

JDK(Java Development Kit)는 코틀린 프로그램을 컴파일하고 실행하는 데 필요한 **개발 도구 전체 묶음**이다. JVM(Java Virtual Machine), 컴파일러(`javac`), 표준 라이브러리, 디버거가 포함된다.

> JDK/JVM의 내부 동작 원리는 [17. JVM 동작 원리](17-jvm-fundamentals)에서 상세히 다룬다.

### 1-2. LTS 버전을 선택해야 하는 이유

| 버전 | 출시 | LTS 지원 종료 | 상태 |
|------|------|--------------|------|
| Java 11 | 2018 | 2026.09 | 구형 — 신규 프로젝트 비권장 |
| **Java 17** | 2021 | 2029.09 | **현재 표준 LTS** |
| **Java 21** | 2023 | 2031.09 | **최신 LTS (Virtual Threads 포함)** |
| Java 25 | 2025 | 2030+ | 미래 LTS (예정) |

LTS(Long-Term Support)가 아닌 버전(예: 18, 19, 20)은 출시 후 6개월만 패치가 제공된다. 증권사처럼 운영 안정성이 최우선인 환경에서는 **LTS 버전만 사용**하는 것이 원칙이다.

**실무 조언**: 2025년 기준 신규 프로젝트는 Java 21 기반으로 시작하되, 팀 표준이 17이라면 맞추는 것이 협업에 유리하다.

### 1-3. SDKMAN으로 JDK 설치 (macOS/Linux 권장)

```bash
# SDKMAN 설치
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# 사용 가능한 Java 목록 확인
sdk list java

# Temurin(Eclipse Adoptium) — 무료, 상업용 허용
sdk install java 21.0.3-tem
sdk install java 17.0.11-tem

# 프로젝트별로 버전 전환
sdk use java 21.0.3-tem

# 설치 확인
java -version
# openjdk version "21.0.3" 2024-04-16
```

> **Windows 사용자**: [Adoptium 공식 사이트](https://adoptium.net)에서 MSI 설치 파일을 내려받거나, Chocolatey(`choco install temurin21`)를 사용한다.

### 1-4. 주요 JDK 배포판 비교

| 배포판 | 제공사 | 상업용 무료 | 특징 |
|--------|--------|------------|------|
| Eclipse Temurin | Eclipse Foundation | ✅ | 가장 보편적, 증권사 내부 표준 多 |
| Amazon Corretto | Amazon | ✅ | AWS 환경 최적화 |
| GraalVM CE | Oracle | ✅ | 네이티브 이미지 컴파일 지원 |
| Oracle JDK | Oracle | ❌ (구독 필요) | 기업 지원, 라이선스 주의 |

> **함정(Gotcha)**: Oracle JDK 8u211 이후 버전은 상업적 사용 시 유료다. 증권사 망분리 환경에서 라이선스 감사(Audit)가 나올 수 있으니 **반드시 Temurin 또는 Corretto**를 사용하라.

---

## 2. IntelliJ IDEA 설치와 핵심 활용법

### 2-1. 버전 선택

| 에디션 | 가격 | 코틀린/Spring 지원 |
|--------|------|------------------|
| Community Edition | 무료 | 코틀린 기본 지원, Spring 플러그인 없음 |
| **Ultimate Edition** | 유료 (학생/스타트업 할인) | Spring, JPA, SQL, HTTP Client 모두 포함 |

증권사 백엔드 개발에는 **Ultimate Edition이 사실상 필수**다. Spring Boot, JPA, 데이터베이스 도구가 한 번에 통합되기 때문이다.

### 2-2. 필수 단축키 (macOS 기준, Windows는 Ctrl = ⌘)

#### 탐색 (Navigation)
| 단축키 | 기능 |
|--------|------|
| `⌘ + Shift + A` | 모든 액션 검색 (모르면 이것부터) |
| `⌘ + Shift + F` | 전체 파일에서 문자열 검색 |
| `⌘ + N` (프로젝트창) | 새 파일/클래스 생성 |
| `⌘ + E` | 최근 파일 목록 |
| `⌘ + B` | 선언부로 이동 |
| `⌘ + Alt + B` | 구현체로 이동 |
| `⌘ + F12` | 현재 파일의 메서드/프로퍼티 목록 |
| `Double Shift` | 어디서나 검색 (Search Everywhere) |

#### 편집 (Editing)
| 단축키 | 기능 |
|--------|------|
| `⌘ + D` | 현재 줄 복제 |
| `⌘ + Y` | 현재 줄 삭제 |
| `⌘ + /` | 줄 주석 토글 |
| `⌘ + Alt + L` | 코드 포맷팅 |
| `Alt + Enter` | 빠른 수정 (Quick Fix) — 가장 자주 씀 |
| `⌘ + Shift + Enter` | 현재 구문 완성 (자동 세미콜론, 괄호 닫기) |
| `Ctrl + T` | 리팩터링 메뉴 |
| `Shift + F6` | 이름 변경 (Rename) |

#### 실행/디버그
| 단축키 | 기능 |
|--------|------|
| `Ctrl + R` | 이전과 동일한 설정으로 실행 |
| `Ctrl + D` | 이전과 동일한 설정으로 디버그 |
| `⌘ + F8` | 브레이크포인트 토글 |
| `F8` | 디버그 중 다음 줄로 (Step Over) |
| `F7` | 디버그 중 메서드 내부로 (Step Into) |
| `Shift + F8` | 디버그 중 메서드 밖으로 (Step Out) |
| `F9` | 다음 브레이크포인트까지 실행 (Resume) |

### 2-3. 디버거 활용법 — 실전 예제

주문 처리 중 금액 계산이 이상할 때 디버거로 추적하는 시나리오다.

```kotlin
data class Order(
    val stockCode: String,
    val quantity: Int,
    val unitPrice: java.math.BigDecimal
)

fun calculateTotalAmount(orders: List<Order>): java.math.BigDecimal {
    return orders
        .filter { it.quantity > 0 }
        .fold(java.math.BigDecimal.ZERO) { acc, order ->
            // ← 여기에 브레이크포인트 설정 (⌘+F8)
            acc + order.unitPrice.multiply(java.math.BigDecimal(order.quantity))
        }
}
```

**디버거 활용 팁**:
1. `fold` 람다 첫 줄에 브레이크포인트를 찍는다.
2. `F9`로 실행하면 람다가 호출될 때마다 멈춘다.
3. **Variables 패널**에서 `acc`, `order`의 현재 값을 실시간으로 확인한다.
4. **Watches**에 `order.unitPrice.toPlainString()` 같은 표현식을 추가하면 매 스텝마다 자동 평가된다.
5. **Evaluate Expression** (`Alt + F8`)으로 임의 코드를 실행해 볼 수 있다.

> **조건부 브레이크포인트**: 브레이크포인트를 우클릭하면 조건을 추가할 수 있다. 예: `order.stockCode == "005930"` — 삼성전자 주문일 때만 멈춘다. 수천 건 중 특정 케이스만 디버깅할 때 필수다.

---

## 3. Gradle 프로젝트 구조 완전 이해

### 3-1. 디렉터리 구조

```
my-securities-app/
├── build.gradle.kts          ← 빌드 스크립트 (Kotlin DSL)
├── settings.gradle.kts       ← 프로젝트 이름, 멀티모듈 설정
├── gradlew                   ← Gradle Wrapper 스크립트 (Unix)
├── gradlew.bat               ← Gradle Wrapper 스크립트 (Windows)
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties  ← Gradle 버전 명시
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/securities/app/
│   │   │       ├── Application.kt        ← 진입점
│   │   │       ├── order/
│   │   │       │   ├── Order.kt
│   │   │       │   ├── OrderService.kt
│   │   │       │   └── OrderRepository.kt
│   │   │       └── account/
│   │   │           └── Account.kt
│   │   └── resources/
│   │       ├── application.yml           ← Spring 설정
│   │       └── db/migration/             ← Flyway SQL 파일
│   └── test/
│       ├── kotlin/
│       │   └── com/securities/app/
│       │       └── order/
│       │           └── OrderServiceTest.kt
│       └── resources/
│           └── application-test.yml
├── .gitignore
└── .editorconfig
```

### 3-2. settings.gradle.kts

```kotlin
rootProject.name = "my-securities-app"

// 멀티모듈 프로젝트라면 (예: 공통 모듈 분리)
// include(":core", ":order-service", ":account-service")
```

### 3-3. build.gradle.kts 핵심 구조

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"  // @Component, @Service 등 open 처리
    kotlin("plugin.jpa") version "1.9.24"     // JPA 엔티티 no-arg 생성자 자동 생성
}

group = "com.securities"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    // 사내 Nexus가 있다면:
    // maven { url = uri("https://nexus.company.com/repository/maven-public/") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"  // 자바 null 어노테이션 엄격 처리
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### 3-4. 자주 쓰는 Gradle 명령어

```bash
# 빌드 (컴파일 + 테스트 + JAR 생성)
./gradlew build

# 테스트만
./gradlew test

# 실행 (Spring Boot)
./gradlew bootRun

# 의존성 트리 확인 (버전 충돌 디버깅)
./gradlew dependencies --configuration runtimeClasspath

# 캐시 클리어 (이상할 때)
./gradlew clean build

# 특정 테스트만 실행
./gradlew test --tests "com.securities.app.order.OrderServiceTest"
```

> **Gradle Wrapper를 항상 사용하라**: `gradle` 명령이 아닌 `./gradlew`를 써야 한다. Wrapper는 `gradle-wrapper.properties`에 명시된 특정 Gradle 버전을 자동으로 내려받아 사용하므로, 팀원 간 빌드 환경이 완전히 동일해진다.

---

## 4. Git 기초 — 증권사 실무 흐름

### 4-1. Git이 필요한 이유

증권사에서는 모든 소스 변경이 감사 추적(Audit Trail) 대상이다. 누가, 언제, 무엇을, 왜 바꿨는지를 Git 히스토리로 추적한다. 장애 발생 시 "어떤 커밋이 문제를 일으켰나"를 `git bisect`로 이분 탐색할 수 있다.

### 4-2. 기본 흐름 (Feature Branch 전략)

```bash
# 1. 원격 저장소에서 최신 상태 가져오기
git clone https://git.company.com/securities/backend.git
cd backend

# 2. 작업 브랜치 생성 (main/develop에서 직접 작업 금지)
git checkout -b feature/JIRA-1234-order-cancel-api

# 3. 파일 수정 후 스테이징
git add src/main/kotlin/com/securities/order/OrderService.kt
git add src/test/kotlin/com/securities/order/OrderServiceTest.kt

# 4. 커밋 (의미 있는 단위로)
git commit -m "feat(order): 주문 취소 API 구현 및 단위 테스트 추가

- 주문 상태가 PENDING일 때만 취소 가능하도록 검증
- 이미 체결된 주문 취소 시 BusinessException 발생
- OrderServiceTest: 정상/예외 케이스 4가지 추가
"

# 5. 원격에 푸시
git push origin feature/JIRA-1234-order-cancel-api

# 6. GitLab/GitHub에서 Pull Request(Merge Request) 생성
# → 팀장/동료 코드 리뷰 → 승인 후 main/develop에 머지
```

### 4-3. 자주 쓰는 Git 명령어

```bash
# 현재 상태 확인
git status
git diff                  # 스테이징 전 변경 내용
git diff --staged         # 스테이징 후 변경 내용

# 로그 확인
git log --oneline --graph --all  # 브랜치 그래프 포함 한 줄씩
git log -p -3                    # 최근 3개 커밋의 변경 내용 포함

# 실수 복구
git restore <파일>        # 스테이징 전 변경 취소
git restore --staged <파일>  # 스테이징 취소
git revert <커밋해시>     # 커밋을 되돌리는 새 커밋 (push한 뒤라면 이걸 써야 함)

# 브랜치 관리
git branch -a             # 로컬 + 원격 브랜치 모두 보기
git branch -d feature/done-branch  # 머지된 브랜치 삭제

# 최신 upstream 반영
git fetch origin
git rebase origin/develop  # 내 브랜치를 최신 develop 위로 재정렬
```

### 4-4. .gitignore 필수 항목

```gitignore
# 빌드 결과물
build/
.gradle/
out/

# IntelliJ IDEA
.idea/
*.iws
*.iml
*.ipr

# 환경 변수 (절대 커밋 금지)
.env
*.env.local
application-local.yml
application-secret.yml

# OS
.DS_Store
Thumbs.db

# 로그
*.log
logs/
```

> **보안 경고**: DB 비밀번호, API 키, 인증서가 Git에 올라가면 감사에서 지적되고 즉시 키 교체가 필요하다. `application-secret.yml` 같은 파일은 반드시 `.gitignore`에 포함하고, 별도 시크릿 관리 시스템(Vault, Kubernetes Secret)을 사용한다.

---

## 5. 증권사 망분리 환경과 Nexus

### 5-1. 망분리(Network Segmentation)란

증권사는 금융감독원 규정에 따라 **업무망과 인터넷망을 물리적으로 분리**해야 한다. 이는 개발자에게 실질적인 제약을 만든다.

```
인터넷 PC                     업무 PC (개발/운영)
─────────────────            ─────────────────────
Maven Central ─── 단절 ───  회사 서버만 접근 가능
GitHub.com    ─── 단절 ───  사내 GitLab만 사용
npm, PyPI     ─── 단절 ───  사내 Nexus Repository
```

### 5-2. Nexus Repository Manager

Nexus는 **내부 아티팩트 저장소**다. Maven Central, npm, PyPI의 패키지들을 Nexus가 대신 미러링(Proxy)하고, 개발자는 Nexus를 통해서만 의존성을 내려받는다.

```kotlin
// build.gradle.kts — 망분리 환경 설정
repositories {
    maven {
        url = uri("https://nexus.company.com/repository/maven-public/")
        credentials {
            username = System.getenv("NEXUS_USER") ?: "developer"
            password = System.getenv("NEXUS_PASS") ?: ""
        }
    }
    // mavenCentral() ← 망분리 환경에서는 주석 처리
}
```

**Nexus 그룹 저장소 구조**:

```
maven-public (Group)
├── maven-central   (Proxy → Maven Central)
├── maven-releases  (Hosted — 내부 배포 라이브러리)
└── maven-snapshots (Hosted — 개발 중인 라이브러리)
```

### 5-3. 망분리 환경에서의 일반적인 함정

| 상황 | 원인 | 해결 |
|------|------|------|
| `Connection refused` (Gradle 빌드 시) | 인터넷 직접 접근 차단 | `build.gradle.kts`의 `repositories`를 Nexus URL로 변경 |
| 새 라이브러리 추가 불가 | Nexus에 아직 캐시 없음 | Nexus 관리자에게 "신규 라이브러리 등록 요청" 프로세스 진행 |
| `git clone` 실패 | GitHub 차단 | 사내 GitLab URL 사용 |
| Docker Hub 이미지 pull 실패 | 외부 레지스트리 차단 | 사내 Harbor/Nexus Docker 레지스트리 사용 |

---

## 6. 첫 코틀린 프로젝트 — Hello World에서 주문 출력까지

### 6-1. Spring Initializr로 프로젝트 생성

1. [start.spring.io](https://start.spring.io) 접속 (망분리 환경이면 IntelliJ IDEA의 New Project > Spring Initializr 사용)
2. 설정:
   - **Language**: Kotlin
   - **Build tool**: Gradle - Kotlin
   - **Spring Boot**: 3.3.x
   - **Java**: 21
   - **Dependencies**: Spring Web, Spring Data JPA, H2 Database (개발용)
3. **Generate** 클릭 후 ZIP 다운로드 → 압축 해제 → IntelliJ IDEA로 열기

### 6-2. Step 1 — Hello World

```kotlin
// src/main/kotlin/com/securities/app/Application.kt
package com.securities.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
```

```kotlin
// src/main/kotlin/com/securities/app/HelloController.kt
package com.securities.app

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {

    @GetMapping("/hello")
    fun hello(): String = "안녕하세요, 여의도 개발자!"
}
```

**실행**: `./gradlew bootRun` 후 브라우저에서 `http://localhost:8080/hello` 확인

### 6-3. Step 2 — 주문 출력 프로그램 (도메인 예제)

```kotlin
// src/main/kotlin/com/securities/app/order/Order.kt
package com.securities.app.order

import java.math.BigDecimal
import java.time.LocalDateTime

enum class OrderType { BUY, SELL }
enum class OrderStatus { PENDING, FILLED, CANCELLED }

data class Order(
    val id: Long,
    val stockCode: String,          // 종목코드 (예: "005930" = 삼성전자)
    val stockName: String,
    val type: OrderType,
    val quantity: Int,
    val unitPrice: BigDecimal,      // 주문 단가 — 항상 BigDecimal 사용
    val status: OrderStatus = OrderStatus.PENDING,
    val orderedAt: LocalDateTime = LocalDateTime.now()
) {
    /** 주문 총액 = 수량 × 단가 */
    fun totalAmount(): BigDecimal = unitPrice.multiply(BigDecimal(quantity))

    override fun toString(): String =
        "[${orderedAt.toLocalTime()}] ${type.name} ${stockName}(${stockCode}) " +
        "${quantity}주 × ${unitPrice.toPlainString()}원 = ${totalAmount().toPlainString()}원 [${status}]"
}
```

```kotlin
// src/main/kotlin/com/securities/app/order/OrderPrinter.kt
package com.securities.app.order

import java.math.BigDecimal

object OrderPrinter {

    fun printOrders(orders: List<Order>) {
        println("=" .repeat(70))
        println(" 주문 내역 (총 ${orders.size}건)")
        println("=".repeat(70))

        orders.forEach { println(it) }

        println("-".repeat(70))

        val totalBuy = orders
            .filter { it.type == OrderType.BUY }
            .fold(BigDecimal.ZERO) { acc, o -> acc + o.totalAmount() }

        val totalSell = orders
            .filter { it.type == OrderType.SELL }
            .fold(BigDecimal.ZERO) { acc, o -> acc + o.totalAmount() }

        println("매수 합계: ${totalBuy.toPlainString()}원")
        println("매도 합계: ${totalSell.toPlainString()}원")
        println("순 손익 (매도-매수): ${(totalSell - totalBuy).toPlainString()}원")
        println("=".repeat(70))
    }
}
```

```kotlin
// src/main/kotlin/com/securities/app/order/OrderDemoRunner.kt
package com.securities.app.order

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class OrderDemoRunner : CommandLineRunner {

    override fun run(vararg args: String?) {
        val orders = listOf(
            Order(
                id = 1L,
                stockCode = "005930",
                stockName = "삼성전자",
                type = OrderType.BUY,
                quantity = 100,
                unitPrice = BigDecimal("75400")
            ),
            Order(
                id = 2L,
                stockCode = "000660",
                stockName = "SK하이닉스",
                type = OrderType.BUY,
                quantity = 50,
                unitPrice = BigDecimal("182500")
            ),
            Order(
                id = 3L,
                stockCode = "005930",
                stockName = "삼성전자",
                type = OrderType.SELL,
                quantity = 30,
                unitPrice = BigDecimal("78200"),
                status = OrderStatus.FILLED
            )
        )

        OrderPrinter.printOrders(orders)
    }
}
```

**실행 결과 예시**:
```
======================================================================
 주문 내역 (총 3건)
======================================================================
[10:30:00] BUY 삼성전자(005930) 100주 × 75400원 = 7540000원 [PENDING]
[10:31:00] BUY SK하이닉스(000660) 50주 × 182500원 = 9125000원 [PENDING]
[10:32:00] SELL 삼성전자(005930) 30주 × 78200원 = 2346000원 [FILLED]
----------------------------------------------------------------------
매수 합계: 16665000원
매도 합계: 2346000원
순 손익 (매도-매수): -14319000원
======================================================================
```

### 6-4. Step 3 — REST API로 확장

```kotlin
// src/main/kotlin/com/securities/app/order/OrderController.kt
package com.securities.app.order

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    // 실제로는 서비스 레이어와 DB를 사용하지만, 여기서는 인메모리 데이터
    private val orders = mutableListOf(
        Order(1L, "005930", "삼성전자", OrderType.BUY, 100, BigDecimal("75400")),
        Order(2L, "000660", "SK하이닉스", OrderType.BUY, 50, BigDecimal("182500"))
    )

    @GetMapping
    fun getOrders(): ResponseEntity<List<Order>> =
        ResponseEntity.ok(orders)

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: Long): ResponseEntity<Order> {
        val order = orders.find { it.id == id }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(order)
    }

    @GetMapping("/summary")
    fun getSummary(): ResponseEntity<Map<String, Any>> {
        val totalAmount = orders.fold(BigDecimal.ZERO) { acc, o -> acc + o.totalAmount() }
        return ResponseEntity.ok(mapOf(
            "count" to orders.size,
            "totalAmount" to totalAmount
        ))
    }
}
```

**테스트 (HTTPie 또는 curl)**:
```bash
# 전체 주문 조회
curl http://localhost:8080/api/v1/orders

# 특정 주문 조회
curl http://localhost:8080/api/v1/orders/1

# 요약
curl http://localhost:8080/api/v1/orders/summary
```

---

## 7. IntelliJ IDEA 생산성 플러그인

| 플러그인 | 용도 |
|----------|------|
| **Kotlin** | 기본 내장, 항상 최신 버전 유지 |
| **Spring Boot** | Spring 컴포넌트 자동 완성, 설정 파일 지원 |
| **Database Tools** | IntelliJ Ultimate 내장, DB 직접 쿼리 |
| **HTTP Client** | `.http` 파일로 API 테스트 (Postman 대체) |
| **GitToolBox** | Git blame 인라인 표시 |
| **SonarLint** | 코드 품질/보안 경고 실시간 표시 |
| **Kotest** | Kotest 테스트 프레임워크 지원 |

### 7-1. HTTP Client 예제 (`.http` 파일)

```http
### 주문 전체 조회
GET http://localhost:8080/api/v1/orders
Accept: application/json

### 주문 요약
GET http://localhost:8080/api/v1/orders/summary

### 변수 사용
@baseUrl = http://localhost:8080
@orderId = 1

GET {{baseUrl}}/api/v1/orders/{{orderId}}
```

---

## 8. 개발 환경 체크리스트

```
[ ] JDK 17 또는 21 설치 확인 (java -version)
[ ] IntelliJ IDEA 설치 (Ultimate 권장)
[ ] 사내 Nexus URL 확인 및 build.gradle.kts에 등록
[ ] 사내 GitLab 계정 생성 및 SSH 키 등록
[ ] .gitignore에 .env, application-secret.yml 포함 확인
[ ] ./gradlew bootRun 실행 → http://localhost:8080/hello 응답 확인
[ ] OrderDemoRunner 콘솔 출력 확인
[ ] REST API /api/v1/orders 응답 확인
```

---

이전: [15. 이벤트 기반 아키텍처](15-event-driven) · 다음: [17. JVM 동작 원리](17-jvm-fundamentals) · [전체 커리큘럼](/curriculum)
