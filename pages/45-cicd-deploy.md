# 45. CI/CD와 배포 전략

소프트웨어를 작성하는 것과, 그것을 안정적으로 운영 환경에 올리는 것은 전혀 다른 문제다.
특히 증권·금융 시스템에서는 배포 하나가 수천억 원의 거래를 멈출 수 있기 때문에, CI/CD와 배포 전략은 단순한 DevOps 편의 도구가 아니라 **리스크 관리 체계**다.

---

## 1. CI/CD란 무엇인가

### 1.1 핵심 개념

- **CI (Continuous Integration)**: 개발자가 코드를 메인 브랜치에 자주 통합하고, 통합할 때마다 자동으로 빌드·테스트를 돌려 문제를 조기 발견하는 관행.
- **CD (Continuous Delivery)**: CI를 통과한 산출물을 언제든 운영 배포 가능한 상태로 유지하는 관행. 배포 자체는 수동 승인.
- **CD (Continuous Deployment)**: 승인 없이 자동으로 운영까지 배포. 금융권에서는 거의 채택하지 않는다.

> **왜 중요한가**: "작동하는 코드"와 "운영에서 작동하는 코드"는 다르다. CI 없이 격주로 대규모 통합을 하면, 충돌 해소에 드는 비용이 기하급수로 늘어난다. 업계에서는 이를 **Integration Hell**이라 부른다.

### 1.2 파이프라인 전체 흐름

```
개발자 Push
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  CI Pipeline                                                │
│                                                             │
│  [1. 소스 체크아웃]                                          │
│       │                                                     │
│  [2. 빌드 (Compile)]  ──── 실패 시 즉시 중단                 │
│       │                                                     │
│  [3. 단위 테스트 (Unit Test)]                                │
│       │                                                     │
│  [4. 정적 분석 (Static Analysis)]                            │
│       │   - detekt, ktlint, SonarQube                      │
│       │                                                     │
│  [5. 통합 테스트 (Integration Test)]                         │
│       │                                                     │
│  [6. 패키징 (Docker Image Build & Push)]                    │
│       │                                                     │
│  [7. 취약점 스캔 (Image Scan)]                               │
│                                                             │
└───────────────────────┬─────────────────────────────────────┘
                        │ 성공 시
                        ▼
┌─────────────────────────────────────────────────────────────┐
│  CD Pipeline                                                │
│                                                             │
│  [8. dev 환경 자동 배포]                                     │
│       │                                                     │
│  [9. stg 환경 배포] ──── 수동 승인(QA팀)                     │
│       │                                                     │
│  [10. prod 배포] ──────── 수동 승인(CAB / 장 마감 후)        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Git 브랜치 전략

### 2.1 Trunk-Based Development

```
main (trunk)
  │
  ├── feature/order-validation   (수명 1~2일)
  ├── feature/margin-calc        (수명 1~2일)
  └── hotfix/stop-loss-bug       (긴급)
```

- 모든 개발자가 `main`에 자주(하루에도 여러 번) 통합.
- 장기 브랜치를 금지하거나 Feature Flag로 미완성 코드를 숨긴다.
- **장점**: Integration Hell 최소화, 항상 배포 가능한 main 유지.
- **단점**: 팀 규율이 약하면 main이 불안정해진다.

### 2.2 Git Flow

```
main ─────────────────────────────── (운영 태그)
  │                    ▲
  │              release/1.3.0
  │                    │
develop ──────────────────────────── (통합)
  │           │
  feature/A   feature/B
```

| 브랜치 | 용도 |
|--------|------|
| `main` | 운영 배포 소스. 태그(v1.2.3)로 버전 관리 |
| `develop` | 통합 브랜치. 항상 빌드 가능 상태 유지 |
| `feature/*` | 기능 개발. develop에서 분기, develop으로 머지 |
| `release/*` | QA·스테이징 배포용. 버그 수정만 허용 |
| `hotfix/*` | 운영 긴급 패치. main과 develop 양쪽에 머지 |

> **금융권 실무**: Git Flow가 더 흔하다. release 브랜치가 CAB(Change Advisory Board) 심의 단위가 되기 때문이다. "이번 배포에 무엇이 들어가는가"를 release 브랜치로 명시적으로 통제할 수 있다.

---

## 3. 코드 리뷰와 PR 게이트

### 3.1 PR 머지 조건 (Branch Protection)

```yaml
# GitHub Branch Protection (예시 개념)
required_status_checks:
  - build
  - unit-test
  - integration-test
  - sonar-quality-gate
required_pull_request_reviews:
  required_approving_review_count: 2
  dismiss_stale_reviews: true
  require_code_owner_reviews: true
enforce_admins: true
```

### 3.2 CODEOWNERS 설정

```
# .github/CODEOWNERS
/src/main/kotlin/com/pluck/trading/    @trading-team
/src/main/kotlin/com/pluck/settlement/ @settlement-team
/infra/                                @devops-team
/docs/                                 @tech-lead
```

### 3.3 PR 리뷰 체크리스트 (실무)

- [ ] 비즈니스 로직이 요건과 일치하는가
- [ ] 예외 처리(입금 누락, 타임아웃)가 누락 없이 처리되었는가
- [ ] SQL 인젝션·XSS·권한 에스컬레이션 위험 없는가
- [ ] N+1 쿼리 또는 불필요한 DB 호출이 없는가
- [ ] 테스트 커버리지가 신규 로직을 덮는가
- [ ] API 변경 시 하위 호환성을 깨지 않는가

---

## 4. 정적 분석

### 4.1 detekt (Kotlin 코드 품질)

```kotlin
// build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}

detekt {
    config.setFrom(files("$rootDir/config/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)   // SonarQube 연동용
    }
}
```

```yaml
# config/detekt.yml
complexity:
  LongMethod:
    threshold: 60
  CyclomaticComplexMethod:
    threshold: 15
naming:
  FunctionNaming:
    functionPattern: '[a-z][a-zA-Z0-9]*'
style:
  MagicNumber:
    active: true
    ignoreNumbers: ['-1', '0', '1', '2', '100']
```

### 4.2 ktlint (코드 포맷)

```kotlin
// build.gradle.kts
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

ktlint {
    version.set("1.2.1")
    android.set(false)
    reporters {
        reporter(ReporterType.CHECKSTYLE)  // SonarQube 연동용
    }
}
```

CI에서 `./gradlew ktlintCheck` 를 반드시 실행한다. 포맷 위반이 있으면 빌드 실패.

### 4.3 SonarQube

```yaml
# GitHub Actions step 예시
- name: SonarQube 분석
  run: |
    ./gradlew sonarqube \
      -Dsonar.projectKey=yeouido \
      -Dsonar.host.url=${{ secrets.SONAR_URL }} \
      -Dsonar.login=${{ secrets.SONAR_TOKEN }}
```

SonarQube Quality Gate 조건(예시):

| 지표 | 기준 |
|------|------|
| Coverage (신규 코드) | ≥ 80% |
| Duplicated Lines | ≤ 3% |
| Maintainability Rating | A |
| Security Hotspots Reviewed | 100% |
| Bugs | 0 (Blocker·Critical) |

---

## 5. GitHub Actions CI 파이프라인 전체 예시

```yaml
# .github/workflows/ci.yml
name: CI Pipeline

on:
  push:
    branches: [main, develop, 'release/**']
  pull_request:
    branches: [main, develop]

env:
  JAVA_VERSION: '21'
  GRADLE_OPTS: '-Dorg.gradle.daemon=false'

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: yeouido_test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports: ['5432:5432']
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      redis:
        image: redis:7
        ports: ['6379:6379']

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0   # SonarQube 전체 히스토리 필요

      - name: JDK 설정
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: gradle

      - name: Gradle 캐시
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}

      - name: 빌드
        run: ./gradlew classes testClasses --parallel

      - name: ktlint 검사
        run: ./gradlew ktlintCheck

      - name: detekt 검사
        run: ./gradlew detekt

      - name: 단위 테스트
        run: ./gradlew test

      - name: 통합 테스트
        run: ./gradlew integrationTest
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/yeouido_test
          SPRING_REDIS_HOST: localhost

      - name: 테스트 리포트
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: Test Results
          path: '**/build/test-results/**/*.xml'
          reporter: java-junit

      - name: JaCoCo 커버리지
        run: ./gradlew jacocoTestReport jacocoTestCoverageVerification

      - name: SonarQube 분석
        run: ./gradlew sonarqube
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}

  docker-build:
    needs: build-and-test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/heads/release/')

    steps:
      - uses: actions/checkout@v4

      - name: Docker Buildx 설정
        uses: docker/setup-buildx-action@v3

      - name: 레지스트리 로그인
        uses: docker/login-action@v3
        with:
          registry: ${{ secrets.REGISTRY_URL }}
          username: ${{ secrets.REGISTRY_USER }}
          password: ${{ secrets.REGISTRY_PASSWORD }}

      - name: 이미지 빌드 & 푸시
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ${{ secrets.REGISTRY_URL }}/yeouido:${{ github.sha }}
            ${{ secrets.REGISTRY_URL }}/yeouido:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Trivy 취약점 스캔
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: '${{ secrets.REGISTRY_URL }}/yeouido:${{ github.sha }}'
          format: sarif
          output: trivy-results.sarif
          exit-code: '1'
          severity: CRITICAL,HIGH
```

---

## 6. 컨테이너(Docker)와 이미지

### 6.1 멀티스테이지 Dockerfile

```dockerfile
# Dockerfile
# ── 빌드 스테이지 ──────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

# 의존성 먼저 캐싱 (소스 변경 시 재다운로드 방지)
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true
RUN ./gradlew bootJar --no-daemon -x test

# ── 런타임 스테이지 ───────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# 보안: root가 아닌 전용 유저로 실행
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# 레이어 분리로 캐시 효율화 (Spring Boot Layered Jar)
COPY --from=builder --chown=appuser:appgroup \
     /workspace/build/libs/*.jar app.jar

# JVM 튜닝 (컨테이너 메모리 인식)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 6.2 이미지 경량화 함정

> **실무 함정**: `latest` 태그는 배포에 절대 사용하지 않는다. `latest`는 언제 빌드된 이미지인지 추적이 불가능하다. 반드시 `git sha` 또는 `semver` 태그를 사용해야 롤백이 가능하다.

---

## 7. 배포 전략

### 7.1 롤링 업데이트 (Rolling Update)

```
Before:  [v1] [v1] [v1] [v1]
Step 1:  [v2] [v1] [v1] [v1]
Step 2:  [v2] [v2] [v1] [v1]
Step 3:  [v2] [v2] [v2] [v1]
After:   [v2] [v2] [v2] [v2]
```

- 인스턴스를 순차적으로 교체. 배포 중 두 버전이 동시 운영.
- 자원 추가 없이 배포 가능.
- **단점**: v1·v2 공존 시간 동안 API 하위 호환성 필수. DB 스키마도 양 버전 호환이어야 한다.

```yaml
# Kubernetes Deployment 롤링 업데이트 설정
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 0    # 배포 중 다운 인스턴스 0개 유지 (무중단)
    maxSurge: 1          # 여분 인스턴스 최대 1개
```

### 7.2 블루-그린 배포 (Blue-Green)

```
       ┌─────────────────────┐
       │   Load Balancer      │
       └──────────┬───────────┘
                  │
      ┌───────────┴───────────┐
      │                       │
   [Blue: v1]            [Green: v2]  ← 신규 배포, 검증
   (현재 운영)             (대기)
                  │
       트래픽 전환 (순간)
                  │
   [Blue: v1]            [Green: v2]  ← 운영
   (대기/롤백용)           (현재 운영)
```

- 두 환경을 동시에 유지하고, 로드밸런서 스위치로 순간 전환.
- 롤백이 스위치 전환으로 즉각 가능.
- **단점**: 자원이 2배 필요. DB 마이그레이션 전략을 별도로 수립해야 한다.

### 7.3 카나리 배포 (Canary)

```
       ┌─────────────────────┐
       │   Load Balancer      │
       └──────────┬───────────┘
                  │
      ┌───────────┴───────────┐
   95%│                   5%  │
   [v1: 안정]           [v2: 카나리]
                  │
       모니터링: 에러율, 지연시간
                  │
          ┌───────┴──────┐
        문제없음        문제 발생
          │                │
     트래픽 점진 확대    즉시 롤백
     20% → 50% → 100%
```

- 신규 버전에 소량(5~10%)의 트래픽만 먼저 보내 실 트래픽으로 검증.
- 실 데이터로 검증하므로 가장 안전하지만, 모니터링 체계가 전제.

> **금융권 선택**: 주문체결·결제 같은 핵심 시스템은 **블루-그린**을 선호한다. 두 버전 공존 기간을 최소화하고, 문제 발생 시 즉각 전환이 가능하기 때문이다. 카나리는 데이터 분석·알림 등 비핵심 서비스에 적용한다.

---

## 8. 무중단 배포 (Zero-Downtime Deployment)

### 8.1 애플리케이션 레벨 준비

```kotlin
// Spring Boot Graceful Shutdown 설정
// application.yml
server:
  shutdown: graceful   # 처리 중인 요청 완료 후 종료

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s   # 최대 30초 대기
```

```yaml
# Kubernetes Pod 종료 전 신호 처리
spec:
  terminationGracePeriodSeconds: 60
  containers:
    - name: yeouido
      lifecycle:
        preStop:
          exec:
            # 로드밸런서에서 제거될 시간을 벌기 위한 대기
            command: ["/bin/sh", "-c", "sleep 10"]
```

### 8.2 헬스체크와 Readiness Probe

```kotlin
// Spring Boot Actuator 헬스체크
@Component
class TradingEngineHealthIndicator(
    private val tradingEngine: TradingEngine
) : HealthIndicator {

    override fun health(): Health {
        return if (tradingEngine.isReady()) {
            Health.up()
                .withDetail("orderQueueSize", tradingEngine.queueSize())
                .build()
        } else {
            Health.down()
                .withDetail("reason", "Trading engine not initialized")
                .build()
        }
    }
}
```

```yaml
# Kubernetes Probe
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
  failureThreshold: 3
  # Readiness 실패 시 트래픽 차단 (다운은 아님)
```

> **함정**: Liveness와 Readiness를 혼동하면 치명적이다. Liveness 실패 → Pod 재시작. Readiness 실패 → 트래픽 차단. DB 연결 실패는 Liveness가 아닌 Readiness로 처리해야 한다. Liveness에 넣으면 DB 장애 시 Pod가 무한 재시작된다.

---

## 9. 롤백 계획

### 9.1 빠른 롤백의 전제 조건

1. **이미지 태그 보존**: 이전 버전 이미지를 레지스트리에서 삭제하지 않는다.
2. **DB 하위 호환 마이그레이션**: 새 컬럼 추가는 OK, 컬럼 삭제는 2단계 배포.
3. **배포 히스토리 기록**: 어떤 버전이 언제 배포되었는지 추적 가능.

```bash
# Kubernetes 롤백
kubectl rollout undo deployment/yeouido-api
kubectl rollout undo deployment/yeouido-api --to-revision=3  # 특정 버전으로

# 롤백 상태 확인
kubectl rollout status deployment/yeouido-api
kubectl rollout history deployment/yeouido-api
```

### 9.2 DB 롤백을 고려한 2단계 컬럼 삭제

```
1단계 배포: 컬럼을 애플리케이션에서 사용하지 않되 DB에는 유지
2단계 배포 (다음 릴리스): DB에서 컬럼 실제 DROP
```

이 방식이면 1단계에서 롤백해도 DB에 컬럼이 있어 구 버전 앱이 정상 동작한다.

---

## 10. 환경 분리와 설정 관리

### 10.1 환경 구성

| 환경 | 목적 | 배포 트리거 | DB |
|------|------|------------|-----|
| `dev` | 개발 통합 | develop 머지 자동 | 개발 DB |
| `stg` | QA·UAT | 수동 승인 | 운영 복제본 |
| `prod` | 운영 | CAB 승인 + 장 마감 후 | 운영 DB |

### 10.2 Spring Profile로 환경 분리

```yaml
# application.yml (공통)
spring:
  application:
    name: yeouido-api

---
# application-dev.yml
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:postgresql://dev-db:5432/yeouido_dev
logging:
  level:
    com.pluck: DEBUG

---
# application-prod.yml
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${DB_URL}           # 환경변수로 주입 (절대 하드코딩 금지)
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
logging:
  level:
    com.pluck: INFO
```

### 10.3 시크릿 관리

```yaml
# Kubernetes Secret → 환경변수 주입
apiVersion: v1
kind: Secret
metadata:
  name: yeouido-secrets
type: Opaque
data:
  db-password: BASE64_ENCODED_VALUE
  jwt-secret: BASE64_ENCODED_VALUE

---
# Deployment에서 참조
envFrom:
  - secretRef:
      name: yeouido-secrets
```

> **절대 금지**: 비밀번호·API 키·JWT 시크릿을 Git에 커밋하지 않는다. `.env` 파일도 `.gitignore`에 반드시 포함. Vault(HashiCorp), AWS Secrets Manager, Kubernetes Secret 등을 사용한다.

---

## 11. 데이터베이스 마이그레이션

### 11.1 Flyway 설정

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
}
```

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
    out-of-order: false       # 순서 외 마이그레이션 금지
    validate-on-migrate: true # 체크섬 검증 필수
```

### 11.2 마이그레이션 파일 명명 규칙

```
src/main/resources/db/migration/
├── V1__create_order_table.sql
├── V2__add_order_status_index.sql
├── V3__create_portfolio_table.sql
└── V3_1__add_portfolio_risk_column.sql   # 핫픽스
```

```sql
-- V4__add_settlement_date_nullable.sql
-- 안전한 예: NULL 허용으로 추가 (기존 데이터에 영향 없음)
ALTER TABLE orders ADD COLUMN settlement_date DATE NULL;

-- 위험한 예 (절대 금지: 기존 데이터 날아감)
-- DROP TABLE orders;
```

### 11.3 금융 DB 마이그레이션 원칙

1. **ADD는 안전, DROP·RENAME은 위험**: 컬럼 추가는 하위 호환이지만 삭제·이름 변경은 구 버전 앱을 즉시 깨뜨린다.
2. **대용량 테이블 ALTER**: 수백만 건 테이블에 `NOT NULL + DEFAULT` 컬럼 추가는 테이블 락이 발생한다. `NULL`로 먼저 추가 → 배치 업데이트 → `NOT NULL` 제약 추가 순서로 진행.
3. **마이그레이션 파일은 불변**: 한 번 운영에 적용된 마이그레이션 파일을 수정하면 Flyway 체크섬 검증이 실패한다. 수정이 필요하면 새 버전 파일을 만들어야 한다.

---

## 12. 증권 IT 특수: 망분리·CAB·장 마감 배포

### 12.1 망분리 환경

증권사는 금융감독원 규정에 따라 **인터넷망과 업무망이 물리적으로 분리**되어 있다.

```
[인터넷망]                    [업무망(내부)]
  GitHub  ──── (차단) ────  Git 내부 미러(Gitea/GitLab)
  DockerHub ── (차단) ────  내부 Container Registry
  pypi/npm ─── (차단) ────  내부 Artifact Repository (Nexus)
```

**실무 함정**: 외부 패키지를 직접 `pip install`하거나 `npm install`하면 실패한다. 내부 Nexus 프록시를 통해 허용 목록(whitelist)에 등록된 패키지만 사용 가능. 신규 라이브러리 도입 시 보안팀 승인 + 취약점 스캔 후 Nexus에 등록 요청이 필요하다.

### 12.2 변경관리 (CAB: Change Advisory Board)

```
개발 완료
    │
    ▼
변경요청서(RFC) 작성
  - 변경 목적, 영향 범위
  - 롤백 계획
  - 배포 시간 (장 마감 후)
  - 테스트 결과 첨부
    │
    ▼
CAB 심의 (주 1회 화요일)
  - IT 운영, 보안, 리스크, 컴플라이언스 참석
    │
    ├── 승인 → 배포 일정 확정
    └── 반려 → 재작업 후 재심의
    │
    ▼
배포 실행 (장 마감 후 또는 주말)
    │
    ▼
배포 확인·테스트 (30분~1시간)
    │
    ▼
변경요청서 완료 처리
```

### 12.3 장 마감 후·주말 배포 원칙

| 구분 | 배포 가능 시간 |
|------|--------------|
| 주식 시장 정규장 | 09:00 ~ 15:30 (배포 금지) |
| 시간외 거래 | 15:40 ~ 18:00 (긴급 외 금지) |
| 정규 배포 | 18:30 이후 ~ 다음 날 08:30 |
| 대규모 변경 | 토요일 또는 일요일 |
| 명절·연휴 전 | 전날 배포 동결(freeze) |

**배포 동결(Freeze) 기간**: 설·추석 연휴 전후, 결산 기간, 시스템 대규모 점검일에는 배포가 원칙적으로 금지된다. 긴급 패치(P1 장애 대응)만 CAB 긴급 승인으로 예외 허용.

---

## 13. 배포 승인 절차

```
개발팀 배포 요청
    │
    ▼
QA팀 스테이징 검증
  - 기능 테스트
  - 회귀 테스트
  - 성능 기준 충족 여부
    │
    ▼
보안팀 검토 (코드·이미지 스캔 결과 확인)
    │
    ▼
CAB 승인
    │
    ▼
운영팀 배포 실행 (개발팀 대기)
    │
    ▼
배포 후 스모크 테스트 (15분)
    │
    ├── 정상 → 완료 통보
    └── 이상 → 즉시 롤백 → 인시던트 보고
```

---

## 14. 안전한 배포 체크리스트

### 배포 전 (Pre-Deployment)

- [ ] CI 파이프라인 전 단계 GREEN
- [ ] SonarQube Quality Gate 통과
- [ ] 보안 취약점 스캔(Trivy) 결과 Critical 없음
- [ ] DB 마이그레이션 스크립트 DBA 검토 완료
- [ ] 스테이징 환경에서 회귀 테스트 통과
- [ ] 롤백 절차 문서화 (이전 이미지 태그 확인)
- [ ] 배포 중 모니터링 담당자 지정
- [ ] CAB 승인 완료
- [ ] 배포 시간이 장 마감 이후인가

### 배포 중 (During Deployment)

- [ ] 헬스체크 엔드포인트 응답 정상
- [ ] 에러율 모니터링 (Datadog/Grafana 대시보드 오픈)
- [ ] 로그 스트림 실시간 확인
- [ ] DB 연결 수 비정상 증가 여부

### 배포 후 (Post-Deployment)

- [ ] 핵심 기능 스모크 테스트 (주문, 체결, 조회)
- [ ] 응답시간 P95 기준치 이내
- [ ] 에러 로그 없음 (15분 관찰)
- [ ] DB 슬로우 쿼리 증가 없음
- [ ] 변경요청서 완료 처리

---

## 마무리: CI/CD는 '신뢰의 파이프라인'이다

테스트·정적 분석·보안 스캔·환경 분리·배포 승인은 모두 "이 코드를 신뢰할 수 있는가"를 검증하는 단계다.
특히 증권 시스템에서는 코드 한 줄의 오류가 고객 자산 손실로 이어질 수 있다.
파이프라인이 느리고 불편하게 느껴지는 순간이 있더라도, 그 불편함이 **장애를 막는 마찰**임을 기억하자.

빠른 배포가 목표가 아니라, **신뢰할 수 있는 배포**가 목표다.

---

이전: [44. 보안](44-security) · 다음: [46. 장애 대응과 사후 분석](46-incident-response) · [전체 커리큘럼](/curriculum)
