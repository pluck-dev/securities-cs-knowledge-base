# 10. 심화: 금액 계산 완전정복 (BigDecimal)

증권 개발에서 **가장 사고가 잦은 영역**입니다. 실행 예제: [`examples/BigDecimalGuide.kt`](/examples) (검증 완료)

## 왜 Double을 쓰면 안 되나

컴퓨터는 0.1 같은 소수를 2진수로 정확히 표현 못 합니다.

```
0.1 + 0.2 (Double)     = 0.30000000000000004   ← 0.3이 아님
0.1 + 0.2 (BigDecimal) = 0.3
```

- 한 번은 작은 오차지만, 수백만 건 거래에 누적되면 **계좌 잔고가 안 맞는 사고**가 됩니다.
- 그래서 금융권은 금액·수량·환율 등 **돈과 관련된 모든 계산에 `BigDecimal`**을 씁니다.

## 5가지 함정과 해법

### 함정 1 — Double 오차
→ 금액은 무조건 `BigDecimal`.

### 함정 2 — 생성은 반드시 문자열로
```kotlin
BigDecimal(0.1)    // ❌ 0.1000000000000000055511...  (Double 오차가 그대로)
BigDecimal("0.1")  // ✅ 0.1
```
**규칙: `BigDecimal`은 항상 큰따옴표 문자열로 생성.**

### 함정 3 — 사칙연산은 메서드로
```kotlin
a.add(b)        // a + b
a.subtract(b)   // a - b
a.multiply(b)   // a * b
a.divide(b, …)  // a / b  (아래 함정 4 참고)
```
`+`, `-` 연산자도 코틀린에선 동작하지만, 팀 컨벤션 따라 메서드를 명시적으로 쓰는 곳이 많습니다.

### 함정 4 — 나눗셈은 scale + RoundingMode 필수
```kotlin
// ❌ 1.0/3 같은 무한소수에서 ArithmeticException 발생
totalValue.divide(totalQty)
// ✅ 소수 자리수(scale)와 반올림 방식을 반드시 지정
totalValue.divide(totalQty, 2, RoundingMode.HALF_UP)  // 73333.33
```

| RoundingMode | 의미 | 73333.335 → |
|--------------|------|-------------|
| `HALF_UP` | 반올림 (가장 흔함) | 73333.34 |
| `DOWN` | 버림 (절사) | 73333.33 |
| `UP` | 올림 | 73333.34 |
| `HALF_EVEN` | 은행가 반올림 (통계적 편향 제거) | 상황별 |

> 회사가 어떤 반올림 정책을 쓰는지 **반드시 확인**하세요. 수수료/세금은 보통 원 미만 절사(`DOWN`)가 많습니다.

### 함정 5 — 값 비교는 `compareTo` (★실수 매우 잦음)
```kotlin
BigDecimal("1.0") == BigDecimal("1.00")              // false! (scale이 달라서)
BigDecimal("1.0").compareTo(BigDecimal("1.00")) == 0 // true  (값만 비교)
```
- `equals`는 scale(소수 자리수)까지 같아야 true.
- **값이 같은지 보려면 항상 `compareTo(...) == 0`** 또는 `0 < x`, `x <= y` 같은 부등호 사용.

## 실전: 매도 수수료 + 거래세 계산

```kotlin
val sellAmount = BigDecimal("850000")
val fee = sellAmount.multiply(BigDecimal("0.00015")).setScale(0, RoundingMode.DOWN) // 수수료 0.015%
val tax = sellAmount.multiply(BigDecimal("0.0018")).setScale(0, RoundingMode.DOWN)  // 거래세 0.18%
val net = sellAmount.subtract(fee).subtract(tax)
// 매도금액 850,000 → 수수료 127, 거래세 1,530, 실수령 848,343
```
> 세율은 예시이며 실제 세율/제도는 시점에 따라 다릅니다. 항상 사내 기준을 따르세요.

## 체크리스트

- [ ] 금액에 `Double`/`Float` 안 썼나?
- [ ] `BigDecimal`을 문자열로 생성했나?
- [ ] 나눗셈에 scale + RoundingMode 지정했나?
- [ ] 값 비교에 `compareTo` 또는 부등호 썼나?
- [ ] 반올림 정책이 사내 기준과 일치하나?

---

다음: [11. 동시성과 잔고 정합성](11-concurrency)
