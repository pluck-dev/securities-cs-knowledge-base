# 13. 심화: 코루틴(Coroutine)과 비동기

증권 백엔드는 "수많은 요청을 동시에" 처리해야 합니다. 코틀린은 이걸 **코루틴**으로 가볍게 합니다.

> ⚠️ 코루틴은 `kotlinx-coroutines` 라이브러리가 필요해서, 이 문서의 코드는 Gradle 프로젝트에서 실행됩니다 (단독 `kotlinc`로는 라이브러리 추가 필요). 개념 위주로 읽으세요.

## 왜 비동기가 필요한가?

서버가 외부(거래소, DB, 다른 서버)를 호출하면 **응답을 기다리는 시간**이 생깁니다.

```
동기(blocking):   요청A 처리 ████(대기)████ 완료 → 요청B 시작 ...
                  → 기다리는 동안 스레드가 놀아서 비효율

비동기(coroutine): 요청A가 대기하는 동안 → 요청B, C를 처리
                  → 같은 스레드로 훨씬 많은 요청 소화
```

## 스레드 vs 코루틴

| | 스레드(Thread) | 코루틴(Coroutine) |
|---|---|---|
| 무게 | 무거움 (메모리 수 MB) | 가벼움 (수 KB) |
| 개수 | 수천 개가 한계 | 수십만 개 가능 |
| 대기 비용 | 스레드가 통째로 블록 | 일시정지(suspend)하고 양보 |

## 핵심 키워드

### suspend — "잠시 멈출 수 있는 함수"
```kotlin
suspend fun fetchPrice(symbol: String): BigDecimal {
    // 외부 시세 서버 호출 (대기 동안 스레드를 양보)
    return priceClient.get(symbol)
}
```

### launch — "비동기 작업 시작 (결과 안 기다림)"
```kotlin
scope.launch {
    sendNotification(order)   // 알림은 백그라운드로
}
```

### async / await — "동시에 실행하고 결과 모으기"
```kotlin
suspend fun loadDashboard(accountId: String) = coroutineScope {
    val balance = async { loadBalance(accountId) }   // 둘이
    val orders  = async { loadOrders(accountId) }    // 동시에 실행
    Dashboard(balance.await(), orders.await())       // 둘 다 끝나면 합침
}
```
→ 잔고 조회와 주문내역 조회가 **순차(합산)가 아니라 병렬**로 돌아 응답이 빨라집니다.

### Flow — "연속해서 흘러오는 데이터" (실시간 시세에 딱)
```kotlin
fun priceStream(symbol: String): Flow<BigDecimal> = flow {
    while (true) {
        emit(currentPrice(symbol))   // 가격이 바뀔 때마다 흘려보냄
        delay(100)
    }
}
// 구독:
priceStream("005930").collect { price -> updateUI(price) }
```

## 증권 도메인에서의 활용

| 상황 | 코루틴 활용 |
|------|------------|
| 실시간 시세 스트리밍 | `Flow`로 가격 변화 방출 |
| 주문 → 거래소 전송 | `suspend`로 응답 대기 중 스레드 양보 |
| 대시보드 (잔고+주문+시세) | `async`로 병렬 조회 |
| 체결 알림 발송 | `launch`로 백그라운드 처리 |
| 대량 종목 일괄 조회 | 여러 `async`를 모아 `awaitAll` |

## 주의점

- **블로킹 호출 금지**: 코루틴 안에서 `Thread.sleep()`이나 동기 JDBC를 막 쓰면 이점이 사라집니다. `delay()`, 논블로킹 드라이버(R2DBC 등)를 씁니다.
- **구조화된 동시성**: `coroutineScope`로 묶으면 하위 작업이 모두 끝나거나 함께 취소됩니다. 누수 방지.
- **예외 전파**: 자식 코루틴의 예외가 부모로 전파됩니다. `try/catch` 위치 주의.

## Spring과의 연결

- **Spring WebFlux** + 코루틴: 컨트롤러 함수를 `suspend`로 선언하면 논블로킹 처리.
```kotlin
@GetMapping("/balance/{id}")
suspend fun balance(@PathVariable id: String): BalanceResponse {
    return accountService.loadBalance(id)
}
```

> 처음엔 "비동기는 코루틴으로 한다, 실시간 스트림은 Flow다" 정도만 기억하면 충분합니다.

---

다음: [14. 스프링 REST API 버전](14-rest-api)
