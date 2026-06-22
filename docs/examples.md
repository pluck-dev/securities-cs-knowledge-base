# 실행 가능한 예제 코드

아래 4개 예제는 모두 **실제 컴파일·실행 검증 완료**입니다.

!!! tip "직접 실행하기"
    설치 없이 [play.kotlinlang.org](https://play.kotlinlang.org) 에 코드를 붙여넣고 **Run**을 누르면 바로 돌아갑니다.
    로컬 실행: `kotlinc 파일.kt -include-runtime -d a.jar && java -jar a.jar`

## 1. OrderProcessor — 주문 처리기

주문 검증 → 체결 → 잔고/예수금 반영 → 평단가 재계산.
관련 문서: [07. 실습 주문 처리기](07-order-system-tutorial.md)

```kotlin title="OrderProcessor.kt"
--8<-- "examples/OrderProcessor.kt"
```

## 2. BigDecimalGuide — 금액 계산 함정

Double 오차, 생성 방법, 나눗셈 반올림, compareTo 비교 등 5가지 함정.
관련 문서: [10. 금액 계산 완전정복](10-bigdecimal-deep.md)

```kotlin title="BigDecimalGuide.kt"
--8<-- "examples/BigDecimalGuide.kt"
```

## 3. RaceCondition — 동시성 잔고 꼬임

잠금 없이 동시 출금하면 잔고가 틀어지고, 잠금을 걸면 정확해지는 현장.
관련 문서: [11. 동시성과 잔고 정합성](11-concurrency.md)

```kotlin title="RaceCondition.kt"
--8<-- "examples/RaceCondition.kt"
```

## 4. OrderBook — 호가창 매칭

TreeMap 기반 호가창과 시장가 매칭(부분체결).
관련 문서: [12. 호가창 자료구조](12-orderbook.md)

```kotlin title="OrderBook.kt"
--8<-- "examples/OrderBook.kt"
```

---

_코드를 바꿔가며 실험해보는 게 가장 빠른 학습법입니다._
