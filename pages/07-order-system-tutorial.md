# 07. 실습: 작은 "주문 처리기" 만들며 코틀린 익히기

실제 증권 주문이 거치는 과정을 코틀린 코드로 작게 재현합니다.
완성된 실행 가능한 코드는 [`examples/OrderProcessor.kt`](/examples) 에 있습니다.

> 🛠 **설치 없이 실행하기**: 예제 코드를 [play.kotlinlang.org](https://play.kotlinlang.org) 에 붙여넣고 `Run`을 누르면 바로 돌아갑니다.

## STEP 1. 데이터 표현하기 — enum, data class

```kotlin
import java.math.BigDecimal

enum class Side { BUY, SELL }          // 주문 방향
enum class OrderType { LIMIT, MARKET } // 지정가 / 시장가

data class Order(
    val orderId: String,        // 주문 번호
    val symbol: String,         // 종목코드 (예: "005930" 삼성전자)
    val side: Side,             // 매수/매도
    val type: OrderType,        // 지정가/시장가
    val price: BigDecimal,      // 가격 (★금액은 BigDecimal!)
    val quantity: Int           // 수량
)
```

**왜 이렇게 쓰나:**
- `enum`을 쓰면 `side`에 오타가 못 들어옵니다. 컴파일러가 막아줌.
- `price`가 `BigDecimal`인 이유는 소수점 오차 방지.

## STEP 2. 계좌 표현하기 — var와 상태 변경

```kotlin
data class Account(
    val accountId: String,
    var cash: BigDecimal,                                    // 예수금 (변하니까 var)
    val positions: MutableMap<String, Int> = mutableMapOf() // 보유 종목 → 수량
)
```

- `cash`는 주문마다 변하니 `var`, 나머지는 `val`.
- `positions`는 "삼성전자 → 10주" 같은 맵. `<String, Int>`는 "키=종목코드, 값=수량".

## STEP 3. 주문 검증하기 — when, null 안전, sealed class

가장 중요한 단계. 검증 없이 거래소로 보내면 사고입니다.

```kotlin
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Rejected(val reason: String) : ValidationResult()
}

fun validateOrder(order: Order, account: Account): ValidationResult {
    if (order.quantity <= 0) {
        return ValidationResult.Rejected("수량은 1주 이상이어야 합니다")
    }

    val tickSize = tickSizeOf(order.price)
    if (order.price.remainder(tickSize) != BigDecimal.ZERO) {
        return ValidationResult.Rejected("호가단위(${tickSize}원)에 맞지 않습니다")
    }

    if (order.side == Side.BUY) {
        val needed = order.price.multiply(BigDecimal(order.quantity))
        if (account.cash < needed) {
            return ValidationResult.Rejected("예수금 부족 (필요: $needed, 보유: ${account.cash})")
        }
    }

    if (order.side == Side.SELL) {
        val held = account.positions[order.symbol] ?: 0   // ★ null 안전
        if (held < order.quantity) {
            return ValidationResult.Rejected("보유수량 부족 (보유: $held)")
        }
    }

    return ValidationResult.Success
}
```

**배우는 코틀린 핵심:**
- `account.positions[order.symbol] ?: 0` → **엘비스 연산자**. 없으면 0. 자바였다면 여기서 NPE로 서버가 죽을 수 있음.
- `sealed class` → 결과를 성공/거절로만 제한.
- `$needed` → 문자열 템플릿.

## STEP 4. 체결 처리하기 — 잔고 반영

```kotlin
fun applyExecution(order: Order, account: Account) {
    val amount = order.price.multiply(BigDecimal(order.quantity))
    when (order.side) {
        Side.BUY -> {
            account.cash = account.cash.subtract(amount)
            val current = account.positions[order.symbol] ?: 0
            account.positions[order.symbol] = current + order.quantity
        }
        Side.SELL -> {
            account.cash = account.cash.add(amount)
            val current = account.positions[order.symbol] ?: 0
            account.positions[order.symbol] = current - order.quantity
        }
    }
}
```

- `when (order.side)`에서 enum 둘 다 처리 안 하면 경고. 처리 누락 방지.
- `BigDecimal`은 `.add()`, `.subtract()`, `.multiply()`를 씁니다.

## STEP 5. 전체 흐름 합치기

```kotlin
fun main() {
    val account = Account("ACC-001", cash = BigDecimal("1000000"))
    val order = Order("ORD-001", "005930", Side.BUY, OrderType.LIMIT,
                      price = BigDecimal("70000"), quantity = 10)

    when (val result = validateOrder(order, account)) {
        is ValidationResult.Success -> {
            applyExecution(order, account)
            println("✅ 주문 체결 완료")
            println("남은 예수금: ${account.cash}")
            println("보유 종목: ${account.positions}")
        }
        is ValidationResult.Rejected -> {
            println("❌ 주문 거절: ${result.reason}")
        }
    }
}
```

**실행 결과:**
```
✅ 주문 체결 완료
남은 예수금: 300000
보유 종목: {005930=10}
```

## 직접 실험해보기

[`examples/OrderProcessor.kt`](/examples) 를 실행하고 값을 바꿔보세요.

| 실험 | 바꿀 값 | 기대 결과 |
|------|---------|----------|
| 호가단위 위반 | `price = "71001"` | "호가단위에 맞지 않습니다" 거절 |
| 예수금 부족 | `quantity = 100` | "예수금 부족" 거절 |
| 보유수량 부족 매도 | `side = SELL`, 보유 없는 종목 | "보유수량 부족" 거절 |
| 평단가 계산 | 같은 종목 2번 매수 | 평단가 변화 확인 (확장 예제) |

---

다음: [08. 아키텍처](08-architecture)
