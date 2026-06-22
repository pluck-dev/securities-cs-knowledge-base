# 44. 보안

> 증권 시스템에서 보안 침해는 고객 자산 손실, 규제 제재, 신뢰 붕괴를 동시에 야기한다. 보안은 기능이 아니라 시스템 속성이다.

---

## 1. 인증(Authentication)과 인가(Authorization)

가장 흔한 혼동 중 하나다.

| 구분 | 질문 | 예시 |
|------|------|------|
| 인증(AuthN) | "당신이 누구인가?" | 아이디/비밀번호 확인, OTP 검증 |
| 인가(AuthZ) | "당신이 무엇을 할 수 있는가?" | 주문 권한, 계좌 조회 권한 |

인증 없이 인가를 논하는 것은 의미가 없다. 그러나 인증에 성공했다고 모든 것이 허용되어서는 안 된다.

---

## 2. 세션 vs 토큰(JWT)

### 2.1 세션 기반 인증

```
클라이언트          서버
   │─── POST /login ──▶│
   │                   │ 세션 생성 (서버 메모리/Redis)
   │◀── Set-Cookie ────│ sessionId=abc123
   │                   │
   │─── GET /orders ──▶│ sessionId 확인 → 사용자 식별
   │◀── 200 OK ────────│
```

장점: 서버에서 즉시 세션 무효화 가능
단점: 서버 상태 유지 필요 → 수평 확장 시 Redis 세션 저장소 필요

### 2.2 JWT(JSON Web Token) 기반 인증

```
클라이언트              인증 서버(Auth)         리소스 서버(API)
   │─── POST /login ───▶│                       │
   │◀── access_token ───│                       │
   │                                            │
   │─── GET /orders + Authorization: Bearer ───▶│
   │                    서명 검증만 (DB 조회 불필요)│
   │◀── 200 OK ─────────────────────────────────│
```

```
JWT 구조: Header.Payload.Signature
Header:  {"alg":"RS256","typ":"JWT"}
Payload: {"sub":"user123","roles":["TRADER"],"exp":1705123200}
Signature: RSA256(base64(header).base64(payload), privateKey)
```

JWT 실무 함정:
- **만료시간 짧게**: access_token 15분, refresh_token 7일
- **민감정보 페이로드에 넣지 말 것**: JWT는 서명되지만 암호화가 아님 (base64 디코딩으로 내용 노출)
- **알고리즘 명시**: `alg: none` 공격 방지를 위해 서버에서 허용 알고리즘 명시적 지정
- **블랙리스트 운영**: 로그아웃/강제 만료 시 Redis에 무효 토큰 등록 필요

### 2.3 OAuth2/OIDC

```
사용자 → 증권사 앱 → 인증 서버(Keycloak/자체) → 토큰 발급 → API 접근
```

증권사 내부 시스템에서 OAuth2를 사용하는 주요 이유:
- **시스템 간 API 호출**: 주문 서비스 → 리스크 서비스 (Client Credentials 플로우)
- **직원 SSO**: 내부 도구 단일 로그인
- **핀테크 연동**: 오픈API 제공 시 제3자 앱 인증

---

## 3. 스프링 시큐리티 설정

### 3.1 필터체인(Security Filter Chain) 이해

```
HTTP 요청
    │
    ▼
SecurityContextPersistenceFilter  ← 세션에서 SecurityContext 복원
    │
    ▼
UsernamePasswordAuthenticationFilter  ← /login 처리
    │
    ▼
JwtAuthenticationFilter  ← JWT 검증 (커스텀)
    │
    ▼
ExceptionTranslationFilter  ← 인증/인가 예외 처리
    │
    ▼
FilterSecurityInterceptor  ← URL 접근 권한 확인
    │
    ▼
Controller
```

### 3.2 스프링 시큐리티 JWT 설정 (코틀린)

```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("io.jsonwebtoken:jjwt-api:0.12.3")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
```

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }  // JWT 사용 시 CSRF 불필요 (stateless)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/**").hasRole("TRADER")
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다")
                }
                ex.accessDeniedHandler { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "권한이 없습니다")
                }
            }
        return http.build()
    }
}
```

```kotlin
@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)

        if (token != null && jwtTokenProvider.validateToken(token)) {
            val authentication = jwtTokenProvider.getAuthentication(token)
            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else null
    }
}
```

```kotlin
@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") private val secretKey: String,
    @Value("\${jwt.access-token-validity-ms}") private val accessTokenValidity: Long
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretKey.toByteArray())
    }

    fun createAccessToken(userId: String, roles: List<String>): String =
        Jwts.builder()
            .subject(userId)
            .claim("roles", roles)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + accessTokenValidity))
            .signWith(key)
            .compact()

    fun validateToken(token: String): Boolean = runCatching {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
        true
    }.getOrDefault(false)

    fun getAuthentication(token: String): Authentication {
        val claims = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).payload
        val roles = (claims["roles"] as List<*>).map { SimpleGrantedAuthority("ROLE_$it") }
        val principal = User(claims.subject, "", roles)
        return UsernamePasswordAuthenticationToken(principal, token, roles)
    }
}
```

### 3.3 메서드 레벨 인가

```kotlin
@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    @PostMapping
    @PreAuthorize("hasRole('TRADER') and #request.accountId == authentication.principal.username")
    fun placeOrder(@RequestBody request: OrderRequest): OrderResponse { ... }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('TRADER') or hasRole('ADMIN')")
    fun cancelOrder(@PathVariable orderId: String): Unit { ... }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllOrders(): List<OrderResponse> { ... }
}
```

---

## 4. 암호화

### 4.1 비밀번호 해싱 (bcrypt)

```kotlin
@Configuration
class PasswordConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        BCryptPasswordEncoder(12) // cost factor 12 (2^12 반복)
}

// 사용
@Service
class AuthService(private val encoder: PasswordEncoder) {
    fun register(request: RegisterRequest) {
        val hashedPassword = encoder.encode(request.password)
        // DB 저장
    }

    fun login(rawPassword: String, storedHash: String): Boolean =
        encoder.matches(rawPassword, storedHash)
}
```

> bcrypt cost factor: 값이 1 증가할 때마다 계산 시간 2배. 12 기준 ~300ms. 브루트포스 공격 비용을 기하급수적으로 증가시킨다.

### 4.2 대칭 암호화 (AES) — 저장 데이터

```kotlin
@Component
class AesEncryptor(
    @Value("\${encryption.key}") private val keyBase64: String
) {
    private val key: SecretKey by lazy {
        val decoded = Base64.getDecoder().decode(keyBase64)
        SecretKeySpec(decoded, "AES")
    }

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray())
        // IV + 암호문을 함께 저장
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(ciphertext: String): String {
        val data = Base64.getDecoder().decode(ciphertext)
        val iv = data.copyOfRange(0, 12)
        val encrypted = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted))
    }
}
```

### 4.3 전송 구간 암호화 (TLS)

```yaml
# application.yml
server:
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    enabled: true
  port: 8443

# 내부 서비스 간도 mTLS 적용 (증권사 보안 정책)
# Istio 서비스 메시에서 mTLS 자동화 가능
```

---

## 5. 키 관리 (KMS / Vault)

### 5.1 절대 하면 안 되는 것

```kotlin
// 절대 금지 ❌
val secret = "my-super-secret-key-1234567890ab"  // 하드코딩
val dbPassword = "prod_password_123"              // 소스코드에 평문
```

```yaml
# 절대 금지 ❌
spring:
  datasource:
    password: prod_password_123  # application.yml에 평문 커밋
```

### 5.2 HashiCorp Vault 연동

```yaml
# application.yml
spring:
  cloud:
    vault:
      host: vault.internal.securities.com
      port: 8200
      scheme: https
      authentication: AWS_IAM  # EC2/EKS IAM 역할로 인증
      kv:
        enabled: true
        backend: secret
        default-context: order-service
```

```kotlin
// Vault에서 시크릿 자동 주입
@Component
class DatabaseConfig {
    @Value("\${secret.db.password}")  // Vault에서 자동 주입
    private lateinit var dbPassword: String
}
```

### 5.3 AWS KMS 연동 (봉투 암호화)

```kotlin
@Component
class KmsEncryptionService(
    private val kmsClient: KmsClient,
    @Value("\${aws.kms.key-id}") private val keyId: String
) {
    // KMS로 데이터 암호화
    fun encrypt(plaintext: String): String {
        val response = kmsClient.encrypt {
            it.keyId(keyId)
            it.plaintext(SdkBytes.fromUtf8String(plaintext))
        }
        return Base64.getEncoder().encodeToString(response.ciphertextBlob().asByteArray())
    }

    fun decrypt(ciphertext: String): String {
        val response = kmsClient.decrypt {
            it.ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(ciphertext)))
        }
        return response.plaintext().asUtf8String()
    }
}
```

---

## 6. OWASP Top 10 방어

### 6.1 SQL 인젝션

```kotlin
// 나쁜 예 ❌ — SQL 인젝션 취약
fun findOrders(status: String): List<Order> {
    val sql = "SELECT * FROM orders WHERE status = '$status'"  // 절대 금지
    return jdbcTemplate.query(sql) { rs, _ -> mapOrder(rs) }
}

// 좋은 예 ✅ — 파라미터 바인딩
fun findOrders(status: String): List<Order> =
    jdbcTemplate.query(
        "SELECT * FROM orders WHERE status = ?",
        { rs, _ -> mapOrder(rs) },
        status
    )

// JPA 사용 시 자동으로 파라미터 바인딩
fun findByStatus(status: OrderStatus): List<Order>
```

### 6.2 XSS (Cross-Site Scripting)

```kotlin
// 응답 헤더 설정
@Configuration
class WebSecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.headers { headers ->
            headers
                .contentTypeOptions(Customizer.withDefaults())
                .xssProtection(Customizer.withDefaults())
                .contentSecurityPolicy { csp ->
                    csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; object-src 'none'"
                    )
                }
        }
        return http.build()
    }
}

// 사용자 입력 HTML 이스케이프
fun sanitize(input: String): String =
    HtmlUtils.htmlEscape(input)  // Spring 제공
```

### 6.3 CSRF

REST API + JWT(stateless) 구조에서는 CSRF가 사실상 불필요하지만, 세션 기반이면 반드시 활성화:

```kotlin
// 세션 기반 인증 시 CSRF 활성화
http.csrf { csrf ->
    csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
}
```

### 6.4 인증 우회 방어

```kotlin
// 모든 엔드포인트 명시적 인가 설정 (화이트리스트 방식)
.authorizeHttpRequests { auth ->
    auth
        .requestMatchers("/public/**").permitAll()
        .anyRequest().authenticated()  // 명시 안 된 모든 경로 = 인증 필요
}
// 절대 .anyRequest().permitAll() 사용 금지
```

---

## 7. API 보안

### 7.1 레이트 리밋(Rate Limiting)

```kotlin
// Bucket4j 기반 레이트 리밋
@Component
class RateLimitFilter : OncePerRequestFilter() {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val clientIp = request.remoteAddr
        val bucket = buckets.computeIfAbsent(clientIp) { createBucket() }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response)
        } else {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.writer.write("""{"error":"요청 한도 초과"}""")
        }
    }

    private fun createBucket(): Bucket = Bucket.builder()
        .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1))))
        .build()
}
```

### 7.2 입력 검증

```kotlin
data class OrderRequest(
    @field:NotBlank @field:Size(max = 12)
    val symbol: String,

    @field:NotNull @field:Min(1) @field:Max(1_000_000)
    val quantity: Int,

    @field:NotNull @field:DecimalMin("0.01") @field:DecimalMax("99999999.99")
    val price: BigDecimal,

    @field:NotNull
    val side: OrderSide
)

@PostMapping
fun placeOrder(@Valid @RequestBody request: OrderRequest): OrderResponse { ... }
```

---

## 8. 감사 로그(Audit Log)

금융 규제상 누가, 언제, 무엇을 했는지 추적 가능해야 한다.

```kotlin
@Component
class AuditLogService(private val auditRepository: AuditLogRepository) {

    fun log(
        userId: String,
        action: String,
        resourceType: String,
        resourceId: String,
        details: Map<String, Any> = emptyMap(),
        result: AuditResult = AuditResult.SUCCESS
    ) {
        auditRepository.save(
            AuditLog(
                userId = userId,
                action = action,          // "ORDER_PLACED", "ORDER_CANCELLED"
                resourceType = resourceType,
                resourceId = resourceId,
                details = objectMapper.writeValueAsString(details),
                result = result,
                ipAddress = getCurrentIp(),
                timestamp = Instant.now()
            )
        )
    }
}

// AOP로 자동 감사 로깅
@Aspect
@Component
class AuditAspect(private val auditLogService: AuditLogService) {

    @Around("@annotation(Auditable)")
    fun audit(joinPoint: ProceedingJoinPoint): Any? {
        val annotation = getAnnotation(joinPoint)
        val userId = SecurityContextHolder.getContext().authentication?.name ?: "anonymous"

        return try {
            val result = joinPoint.proceed()
            auditLogService.log(userId, annotation.action, annotation.resource, extractResourceId(joinPoint))
            result
        } catch (e: Exception) {
            auditLogService.log(userId, annotation.action, annotation.resource, "", result = AuditResult.FAILURE)
            throw e
        }
    }
}
```

---

## 9. 금융권 특수 보안 요구사항

### 9.1 망분리(Network Segmentation)

```
인터넷 ──▶ DMZ(방화벽) ──▶ 업무망 ──▶ 내부망
             └── WAF           └── AP 서버    └── DB 서버
                                              └── 시세 서버

규칙:
- 인터넷에서 내부망 직접 접근 불가
- DB 서버는 AP 서버에서만 접근 가능
- 외부 API 호출은 정해진 프록시(Proxy)를 통해서만 가능
- USB, 외부 저장매체 차단
```

### 9.2 법적 의무

| 법률 | 주요 요구사항 | 위반 시 |
|------|------------|--------|
| 전자금융거래법 | 거래기록 5년 보존, 접근통제 | 과태료, 업무정지 |
| 개인정보보호법 | 최소 수집, 암호화 저장, 접근 로그 | 과징금(매출액 3%) |
| 금융보안원 전자금융감독규정 | 취약점 점검, 보안 솔루션 도입 | 검사·제재 |
| 자본시장법 | 시세 조작·내부자 거래 탐지 의무 | 형사처벌 |

### 9.3 접근통제 원칙

```
최소 권한(Least Privilege):
  개발자는 운영 DB 직접 조회 금지
  운영 배포는 배포 전용 계정으로만
  DBA만 스키마 변경 가능

직무 분리(Separation of Duties):
  개발자 ≠ 배포 승인자
  주문 입력자 ≠ 주문 확인자 (4-eyes principle)

접근 로그:
  모든 운영 시스템 접근 기록 (SSH, DB, 어플리케이션)
  6개월~1년 보존
```

---

## 10. 보안 체크리스트

### 인증/인가
- [ ] 모든 API 엔드포인트 인증 필수 (화이트리스트 방식)
- [ ] JWT 만료시간 15분 이하
- [ ] 비밀번호 bcrypt 해싱 (cost >= 12)
- [ ] 메서드 레벨 인가(@PreAuthorize) 적용

### 암호화
- [ ] 전송 구간 TLS 1.2 이상
- [ ] 민감 정보 AES-256-GCM 암호화 저장
- [ ] 시크릿 Vault/KMS 관리 (하드코딩 0건)

### 취약점 방어
- [ ] SQL 파라미터 바인딩 (인젝션 방지)
- [ ] 입력 검증(@Valid) 모든 API 적용
- [ ] 레이트 리밋 적용
- [ ] 보안 응답 헤더(CSP, X-XSS-Protection) 설정

### 감사/모니터링
- [ ] 감사 로그(로그인, 주요 거래, 설정 변경)
- [ ] 로그 5년 보존 (불변 스토리지)
- [ ] 이상 접근 탐지 알림 설정

---

이전: [43. 성능 튜닝](43-performance-tuning) · 다음: [45. CI/CD와 배포 전략](45-cicd-deploy) · [전체 커리큘럼](/curriculum)
