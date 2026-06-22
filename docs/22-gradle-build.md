# 22. Gradle 빌드 시스템

## 왜 빌드 도구가 필요한가?

처음 자바·코틀린을 배울 때는 `kotlinc Main.kt -include-runtime -d Main.jar && java -jar Main.jar` 같은 명령어 한 줄로 충분하다. 그러나 증권사 백엔드는 그렇지 않다.

| 문제 상황 | 빌드 도구 없이 해결하려면 |
|---|---|
| Spring Boot + JPA + Jackson + Kafka + 보안 라이브러리 수십 개 의존성 관리 | JAR 파일을 직접 다운로드, CLASSPATH 수동 설정 |
| 팀원 10명이 동일한 버전으로 빌드해야 함 | 슬랙에 JAR 첨부 후 "각자 CLASSPATH에 추가해요" |
| CI/CD 파이프라인에서 테스트 자동 실행 | 쉘 스크립트 수백 줄 |
| 운영/개발 환경 분리 | 소스 코드 수정 후 재컴파일 |

**빌드 도구(Build Tool)** 는 이 모든 반복 작업을 자동화한다. 의존성을 중앙 저장소(Maven Central, Nexus)에서 자동으로 내려받고, 컴파일·테스트·패키징을 재현 가능하게(reproducible) 만들어 준다.

---

## Gradle vs Maven

자바/코틀린 생태계의 양대 빌드 도구다.

| 항목 | Gradle | Maven |
|---|---|---|
| 설정 파일 | `build.gradle.kts` (Kotlin DSL) 또는 `build.gradle` (Groovy) | `pom.xml` (XML) |
| 빌드 정의 | 프로그래밍 가능한 스크립트 | 선언적 XML |
| 빌드 캐시 | 증분 빌드(Incremental Build), 빌드 캐시 우수 | 기본 캐시 없음 |
| 유연성 | 커스텀 태스크 자유롭게 작성 | 플러그인 생명주기에 강하게 결합 |
| 학습 곡선 | 상대적으로 높음 | XML만 알면 비교적 쉬움 |
| Spring Boot 신규 프로젝트 | 기본 권장 | 여전히 많이 사용 |

> **실무 팁**: 2020년 이후 신규 스프링 부트 프로젝트는 Gradle Kotlin DSL을 기본으로 선택하는 추세다. IDE 자동완성이 Groovy DSL보다 훨씬 강력하고, 오타를 컴파일 타임에 잡을 수 있기 때문이다.

---

## Gradle Wrapper

실무에서는 Gradle을 직접 설치하지 않고 **Gradle Wrapper(gradlew)** 를 사용한다.

```bash
# 프로젝트 루트에 존재하는 파일들
./gradlew          # Unix/Mac 실행 스크립트
./gradlew.bat      # Windows 실행 스크립트
gradle/wrapper/
  gradle-wrapper.jar
  gradle-wrapper.properties
```

`gradle-wrapper.properties` 예시:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

팀원이 `./gradlew build`를 실행하면, 이 파일에 명시된 **정확한 버전**의 Gradle이 자동으로 다운로드된다. "내 PC에서는 됐는데요?" 문제를 근본에서 차단한다.

---

## `build.gradle.kts` 구조 상세

Kotlin DSL로 작성된 실제 증권 백엔드 `build.gradle.kts`를 분해해 보자.

### 전체 예시 — 주문 처리 서비스

```kotlin
// build.gradle.kts (루트 또는 order 모듈)

// ── 1. 플러그인 블록 ─────────────────────────────────────────────
plugins {
    kotlin("jvm") version "1.9.24"              // 코틀린 JVM 컴파일러
    kotlin("plugin.spring") version "1.9.24"    // 스프링 빈을 open class로 자동 처리
    kotlin("plugin.jpa") version "1.9.24"       // JPA 엔티티 no-arg 생성자 자동 생성
    id("org.springframework.boot") version "3.3.0"  // bootJar, bootRun 태스크 추가
    id("io.spring.dependency-management") version "1.1.5"  // BOM으로 버전 통합 관리
}

// ── 2. 프로젝트 메타데이터 ────────────────────────────────────────
group = "com.yeouido"
version = "0.0.1-SNAPSHOT"

// ── 3. JVM 대상 버전 ─────────────────────────────────────────────
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")  // 스프링의 @Nullable 엄격 처리
    }
}

// ── 4. 저장소 설정 ───────────────────────────────────────────────
repositories {
    // 사내 Nexus 프록시 저장소 (아래 '사내 Nexus' 섹션 참조)
    // maven { url = uri("https://nexus.yeouido.internal/repository/maven-public/") }
    mavenCentral()   // Maven Central (공개)
}

// ── 5. 의존성 블록 ───────────────────────────────────────────────
dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kafka (비동기 주문 이벤트)
    implementation("org.springframework.kafka:spring-kafka")

    // 코틀린 표준 라이브러리 및 리플렉션
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // 금액 처리 — JSR-354 Money API
    implementation("org.javamoney:moneta:1.4.4")

    // DB 드라이버 (런타임에만 필요)
    runtimeOnly("org.postgresql:postgresql")

    // ─── 테스트 의존성 ───────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")  // MockK 사용하므로 제거
    }
    testImplementation("com.ninja-squad:springmockk:4.0.2")  // MockK + 스프링 통합
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    testImplementation("org.springframework.security:spring-security-test")

    // H2 인메모리 DB — 통합 테스트용
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ── 6. 테스트 설정 ───────────────────────────────────────────────
tasks.withType<Test> {
    useJUnitPlatform()   // JUnit5 + Kotest 모두 이 플랫폼 사용
    systemProperty("spring.profiles.active", "test")
}

// ── 7. 빌드 태스크 커스터마이징 ──────────────────────────────────
tasks.bootJar {
    archiveFileName.set("yeouido-order-service.jar")
}

// JAR manifest에 빌드 정보 삽입 (운영 추적용)
springBoot {
    buildInfo()
}
```

---

## 플러그인 상세 설명

### `kotlin("jvm")`

코틀린 소스를 JVM 바이트코드로 컴파일하는 핵심 플러그인. 이것 없이는 `.kt` 파일을 인식조차 못 한다.

### `kotlin("plugin.spring")`

스프링은 프록시(AOP)를 위해 클래스를 상속해야 한다. 코틀린 클래스는 기본이 `final`이라 스프링 프록시를 만들 수 없다. 이 플러그인이 `@Component`, `@Service`, `@Repository`, `@Controller`, `@Configuration` 어노테이션이 붙은 클래스를 컴파일 타임에 자동으로 `open`으로 변환한다.

```kotlin
// 플러그인이 없다면 직접 open을 붙여야 했다 — 이제 불필요
@Service
class OrderService(private val orderRepository: OrderRepository) { ... }
```

### `kotlin("plugin.jpa")`

JPA 엔티티는 no-arg(인자 없는) 생성자가 필요하다. 코틀린 data class에는 없다. 이 플러그인이 `@Entity`, `@MappedSuperclass`, `@Embeddable`에 no-arg 생성자를 바이트코드 수준에서 추가한다.

### `id("org.springframework.boot")`

- `bootJar` 태스크: 실행 가능한 Fat JAR 생성
- `bootRun` 태스크: 개발 중 로컬 실행
- `bootBuildImage` 태스크: Docker 이미지 빌드 (Cloud Native Buildpacks)

### `id("io.spring.dependency-management")`

**BOM(Bill of Materials)** 을 통해 스프링 생태계 라이브러리 버전을 일괄 관리한다. `spring-boot-starter-web`의 버전을 명시하지 않아도 Spring Boot 버전에 맞는 버전이 자동으로 결정된다.

---

## 의존성 범위(Dependency Scope)

```
의존성 범위          컴파일 타임   런타임   테스트 컴파일  테스트 런타임
implementation          O          O          X            X
api                     O          O          O (전이)      O (전이)
compileOnly             O          X          X            X
runtimeOnly             X          O          X            X
testImplementation      X          X          O            O
testRuntimeOnly         X          X          X            O
```

### `implementation` vs `api` — 핵심 차이

```
모듈 구조: market-data-api → order-service → gateway
```

- **`api`**: `order-service`가 `market-data-api`를 `api`로 선언하면, `gateway`도 `market-data-api`의 클래스에 접근할 수 있다 (전이 의존성 노출).
- **`implementation`**: `order-service` 내부에서만 사용. `gateway`는 접근 불가. **멀티모듈에서 기본값으로 사용해야 한다.**

> **함정**: 모든 것을 `api`로 선언하면 모듈 경계가 무의미해진다. 변경 사항이 의도치 않게 상위 모듈까지 전파된다. 원칙: **`implementation` 우선, 공개 API만 `api`.**

---

## 버전 카탈로그 (`libs.versions.toml`)

Gradle 7.4+에서 도입된 **공식 버전 중앙화** 방식.

`gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "1.9.24"
springBoot = "3.3.0"
springDependencyManagement = "1.1.5"
mockk = "1.13.11"
kotest = "5.9.1"
kotestSpring = "1.3.0"
springmockk = "4.0.2"
postgresql = "42.7.3"
javamoney = "1.4.4"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
kotest-runner = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-spring = { module = "io.kotest.extensions:kotest-extensions-spring", version.ref = "kotestSpring" }
springmockk = { module = "com.ninja-squad:springmockk", version.ref = "springmockk" }
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
javamoney = { module = "org.javamoney:moneta", version.ref = "javamoney" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
kotlin-jpa = { id = "org.jetbrains.kotlin.plugin.jpa", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "springBoot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "springDependencyManagement" }
```

`build.gradle.kts`에서 사용:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jpa)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.runner)
    runtimeOnly(libs.postgresql)
}
```

**장점**: IDE 자동완성, 버전을 한 곳에서 관리, PR 리뷰 시 버전 변경 추적 용이.

---

## 멀티모듈 프로젝트 — 증권 시스템 분리

증권 백엔드를 단일 모듈로 만들면 주문·원장·시세 코드가 뒤섞인다. 멀티모듈로 경계를 강제한다.

### 프로젝트 구조

```
yeouido/
├── settings.gradle.kts          ← 모듈 등록
├── build.gradle.kts             ← 루트(공통 설정)
├── gradle/
│   └── libs.versions.toml
├── order-service/               ← 주문 처리 모듈
│   └── build.gradle.kts
├── ledger-service/              ← 원장(잔고) 모듈
│   └── build.gradle.kts
├── market-data/                 ← 시세 데이터 모듈
│   └── build.gradle.kts
└── common/                      ← 공통 도메인/유틸리티
    └── build.gradle.kts
```

### `settings.gradle.kts` (루트)

```kotlin
rootProject.name = "yeouido"

include(
    ":common",
    ":order-service",
    ":ledger-service",
    ":market-data"
)
```

### 루트 `build.gradle.kts` (공통 설정)

```kotlin
// 모든 서브프로젝트에 공통 적용
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = "com.yeouido"
    version = "0.0.1-SNAPSHOT"

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
```

### `order-service/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    // 같은 프로젝트의 다른 모듈 참조
    implementation(project(":common"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jpa)
    implementation(libs.spring.boot.starter.security)

    // 주문 처리에만 필요한 Kafka
    implementation("org.springframework.kafka:spring-kafka")

    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.runner)
    testRuntimeOnly("com.h2database:h2")
}
```

### `common/build.gradle.kts`

```kotlin
// common은 Spring Boot 앱이 아니라 라이브러리
// bootJar 대신 jar만 생성
tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // 금액 타입 등 도메인 공통 의존성만
}
```

> **실무 팁**: `common` 모듈이 비대해지는 것을 경계하라. 모든 것을 common에 넣으면 멀티모듈의 의미가 없다. 각 모듈의 도메인 객체는 해당 모듈에 두고, 정말 공통적인 것(Money 타입, 공통 예외, 유틸리티)만 common에 넣는다.

---

## 주요 Gradle 태스크

```bash
# 전체 빌드 (컴파일 + 테스트 + 패키징)
./gradlew build

# 테스트만 실행
./gradlew test

# 테스트 스킵하고 빌드
./gradlew build -x test

# 특정 모듈만 빌드
./gradlew :order-service:build

# 로컬 실행 (Spring Boot DevTools와 함께)
./gradlew :order-service:bootRun

# 실행 가능 Fat JAR 생성 (배포용)
./gradlew :order-service:bootJar

# 태스크 목록 확인
./gradlew tasks

# 의존성 트리 확인
./gradlew :order-service:dependencies

# 특정 설정의 의존성만 확인
./gradlew :order-service:dependencies --configuration runtimeClasspath

# 빌드 캐시 초기화
./gradlew clean

# 특정 태스크의 입력/출력 확인 (디버깅)
./gradlew :order-service:build --info

# 빌드 스캔 (Gradle Cloud에 분석 결과 업로드)
./gradlew build --scan
```

---

## 의존성 트리와 충돌 해결

### 의존성 트리 분석

```bash
./gradlew :order-service:dependencies --configuration runtimeClasspath
```

출력 예시:

```
runtimeClasspath - Runtime classpath of source set 'main'.
+--- org.springframework.boot:spring-boot-starter-web -> 3.3.0
|    +--- org.springframework.boot:spring-boot-starter -> 3.3.0
|    |    +--- org.springframework:spring-core:6.1.8
|    |    \--- ...
|    \--- org.springframework:spring-webmvc:6.1.8
+--- com.fasterxml.jackson.module:jackson-module-kotlin -> 2.17.1
|    +--- com.fasterxml.jackson.core:jackson-databind:2.17.1 (*)
\--- ...
```

`(*)` 표시는 이미 등장한 의존성(중복 제거됨), `->` 는 버전이 조정됨을 의미한다.

### 버전 충돌 해결

같은 라이브러리가 다른 버전으로 두 경로에서 끌려오면 Gradle은 **최신 버전 우선** 전략을 사용한다. 명시적으로 강제하려면:

```kotlin
configurations.all {
    resolutionStrategy {
        // 특정 버전으로 강제 고정 (보안 패치 등)
        force("com.fasterxml.jackson.core:jackson-databind:2.17.1")

        // 특정 버전이 사용되면 빌드 실패 (취약 버전 차단)
        failOnVersionConflict()

        // 캐시 유효 기간 설정 (SNAPSHOT 버전)
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
    }
}

// BOM(Bill of Materials)으로 버전 묶음 관리
dependencies {
    implementation(platform("io.micrometer:micrometer-bom:1.13.0"))
    implementation("io.micrometer:micrometer-core")  // 버전 생략 가능
}
```

> **함정**: `spring-kafka`를 사용할 때 `kafka-clients` 버전이 중복으로 끌려오는 경우가 많다. `./gradlew dependencies` 로 반드시 확인하라.

---

## 사내 Nexus 저장소 설정

대부분의 증권사는 외부 인터넷 접근이 제한된다. **Nexus Repository Manager** 같은 사내 아티팩트 저장소를 프록시로 사용한다.

```kotlin
repositories {
    maven {
        name = "YeouidoNexus"
        url = uri("https://nexus.yeouido.internal/repository/maven-public/")
        credentials {
            // 실제 환경에서는 환경 변수나 gradle.properties로 관리
            username = System.getenv("NEXUS_USER") ?: ""
            password = System.getenv("NEXUS_PASSWORD") ?: ""
        }
        // 자체 서명 인증서 허용 (사내 환경)
        isAllowInsecureProtocol = false
    }
    // 폴백: 외부 연결이 되는 환경에서 fallback
    // mavenCentral()
}
```

`~/.gradle/gradle.properties` (개발자 로컬, Git에 올리지 않음):

```properties
nexusUser=jdeveloper
nexusPassword=s3cr3t!
```

`build.gradle.kts`에서 참조:

```kotlin
val nexusUser: String by project
val nexusPassword: String by project
```

> **실무 팁**: 자격증명을 `build.gradle.kts`에 하드코딩하면 Git에 올라간다. 반드시 환경 변수 또는 `~/.gradle/gradle.properties`로 외부화하라. CI/CD에서는 Secret Manager(AWS SSM, Vault 등)를 사용한다.

---

## Gradle 증분 빌드와 캐시

Gradle은 태스크의 **입력(Input)과 출력(Output)** 을 추적한다. 입력이 변경되지 않았으면 태스크를 재실행하지 않는다(`UP-TO-DATE`).

```
> Task :order-service:compileKotlin UP-TO-DATE
> Task :order-service:processResources UP-TO-DATE
> Task :order-service:classes UP-TO-DATE
> Task :order-service:bootJar
```

**빌드 캐시(Build Cache)** 를 활성화하면 다른 팀원이 같은 입력으로 빌드한 결과를 캐시에서 재사용한다:

```kotlin
// settings.gradle.kts
buildCache {
    local {
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 30
    }
    // remote { ... }  // 원격 캐시 서버 (Gradle Enterprise 등)
}
```

---

## 체크리스트

- [ ] `gradlew` 파일이 Git에 커밋되어 있는가? (`gradle/wrapper/` 포함)
- [ ] Kotlin DSL(`build.gradle.kts`)을 사용하는가?
- [ ] 버전 카탈로그(`libs.versions.toml`)로 버전을 중앙 관리하는가?
- [ ] `implementation`과 `api` 범위를 올바르게 구분하는가?
- [ ] Nexus 자격증명이 소스 코드에 하드코딩되어 있지 않은가?
- [ ] `./gradlew :모듈:dependencies`로 의존성 트리를 확인했는가?
- [ ] `kotlin("plugin.spring")`과 `kotlin("plugin.jpa")`가 필요한 모듈에 적용되어 있는가?
- [ ] 멀티모듈에서 `common` 모듈이 과도하게 비대해지지 않는가?

---

이전: [21. Spring Data JPA 심화](21-spring-data-jpa-advanced.md) · 다음: [23. 테스트 완전정복](23-testing.md) · [전체 커리큘럼](../CURRICULUM.md)
