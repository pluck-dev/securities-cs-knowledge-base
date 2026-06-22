# 02. 코틀린(Kotlin) 기초

## 코틀린이 뭐고 왜 쓰나

- **JVM(자바 가상머신) 위에서 도는 언어.** 자바와 100% 호환됩니다. 증권사는 수십 년간 자바로 만든 시스템이 많아서, 그 위에 코틀린을 얹어 씁니다.
- 자바보다 **코드가 짧고, null(널) 오류에 안전**해서 금융권에서 빠르게 확산 중입니다.
- "코틀린 안다 = 사실상 자바 생태계(Spring, JVM)를 안다"고 보면 됩니다. 자바 개념도 같이 알아두면 좋습니다.

## 꼭 알아야 할 핵심 문법

### 변수: val / var

```kotlin
val price = 70000      // val = 변하지 않는 값 (상수처럼)
var quantity = 10      // var = 바꿀 수 있는 값
```

- `val`을 기본으로, 꼭 바꿔야 할 때만 `var`.
- 금융에선 "값이 함부로 안 바뀌는 것"이 중요해서 `val`을 선호합니다.

### Null 안전성 (코틀린의 핵심, 가장 중요)

```kotlin
var name: String = "홍길동"    // 절대 null 불가
var nickname: String? = null   // ? 붙이면 null 가능
```

- 증권 시스템에서 "값이 없는데 계산하다가 터지는" 사고(NullPointerException)가 제일 흔합니다.
- 코틀린은 `?`로 이걸 **컴파일 단계에서** 막아줍니다. 이 개념이 코틀린 배우는 이유의 절반입니다.

**엘비스 연산자 `?:`** — "없으면 기본값"

```kotlin
val held = positions[symbol] ?: 0   // 맵에 없으면(null이면) 0을 사용
```

### 함수

```kotlin
fun calcTotal(price: Int, qty: Int): Int {
    return price * qty
}
// 한 줄로 축약:
fun calcTotal(price: Int, qty: Int) = price * qty
```

### data class — "데이터 덩어리" 표현

```kotlin
data class Order(
    val symbol: String,   // 종목코드
    val price: Int,       // 가격
    val quantity: Int,    // 수량
    val side: String      // "BUY" or "SELL"
)
```

- 증권 개발에서 `data class`를 정말 많이 만듭니다. "주문 한 건", "체결 한 건" 같은 걸 표현하죠.
- `equals`, `hashCode`, `toString`, `copy`를 자동 생성해줍니다.

### enum — 정해진 값만 허용

```kotlin
enum class Side { BUY, SELL }
enum class OrderType { LIMIT, MARKET }
```

- `side`에 `"BYU"` 같은 오타가 절대 못 들어옵니다. 컴파일러가 막아줌 → "잘못된 값" 사고를 원천 차단.

### when — 조건 분기

```kotlin
val result = when (order.side) {
    Side.BUY  -> "매수 처리"
    Side.SELL -> "매도 처리"
}
// 구간 분기도 가능:
val tick = when {
    price < 2000  -> 1
    price < 5000  -> 5
    else          -> 10
}
```

- enum + when 조합은 모든 케이스를 다뤘는지 컴파일러가 체크해줍니다 (처리 누락 방지).

### 컬렉션 다루기 (거래내역 처리에 필수)

```kotlin
val orders = listOf(order1, order2, order3)
val buyOrders = orders.filter { it.side == Side.BUY }       // 매수만 골라내기
val symbols   = orders.map { it.symbol }                    // 종목코드만 추출
val total     = orders.sumOf { it.price * it.quantity }     // 총 금액 합산
```

- `filter`, `map`, `sumOf` 같은 함수형 스타일 — 거래내역/잔고 계산할 때 매일 씁니다.
- `it`은 람다에서 "현재 항목"을 가리키는 기본 이름입니다.

### sealed class — 결과 타입을 제한

```kotlin
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Rejected(val reason: String) : ValidationResult()
}
```

- 결과를 "성공" 아니면 "사유 있는 거절"로만 제한. when으로 다룰 때 케이스 누락을 컴파일러가 잡아줍니다.

### 코루틴(coroutine) — 동시성 (지금은 개념만)

- 수많은 사용자의 주문/시세를 동시에 처리해야 하는데, 코틀린은 코루틴으로 이걸 가볍게 합니다.
- 처음엔 몰라도 됩니다. "비동기 처리는 코루틴으로 한다" 정도만 기억하세요.

## ⚠️ 금융 개발자의 숫자 철칙

**금액은 절대 `Double`(소수점 실수형)로 계산하지 마세요. `BigDecimal`을 씁니다.**

```kotlin
import java.math.BigDecimal

// ❌ 잘못된 예 — 오차 발생
val wrong = 0.1 + 0.2          // 0.30000000000000004

// ✅ 올바른 예
val amount = BigDecimal("70000").multiply(BigDecimal("10"))  // 700000
```

- `BigDecimal`은 `+` 대신 `.add()`, `.subtract()`, `.multiply()`, `.divide()`를 씁니다.
- **생성할 때 반드시 문자열로**: `BigDecimal("70000")` (O), `BigDecimal(0.1)` (X — 오차 들어감).

## 학습 추천

- **[Kotlin Koans](https://play.kotlinlang.org/koans)** — 브라우저에서 문제 풀며 배움. 하루 30분씩 2주면 위 내용 다 익힙니다.
- 공식 문서: [kotlinlang.org/docs](https://kotlinlang.org/docs/home.html)

---

다음: [03. 스프링 부트](03-spring-boot.md)
