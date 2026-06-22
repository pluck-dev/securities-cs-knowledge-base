# 06. 입사 후 30일 학습 로드맵

매주 목표를 두고 차근차근. 무리하지 말고 "흐름 따라가기"에 집중하세요.

## 1주차 — 코틀린 기초

- [ ] [Kotlin Koans](https://play.kotlinlang.org/koans) 절반 풀기
- [ ] [02-kotlin-basics.md](02-kotlin-basics) 문법 손에 익히기
- [ ] [`examples/OrderProcessor.kt`](/examples) 직접 실행 + 값 바꿔보기
- [ ] 회사 코드 받으면 "주문 1건이 어디서 어디로 흐르나" 따라가 보기

## 2주차 — 스프링 + 흐름 이해

- [ ] Controller → Service → Repository 흐름 직접 따라가며 디버깅
- [ ] 우리 팀 시스템의 위치 파악 (주문? 시세? 계좌?) — [01-overview.md](01-overview) 참고
- [ ] 도메인 용어집([09-glossary.md](09-glossary))을 **우리 회사 버전**으로 정리
- [ ] JPA인지 MyBatis인지 확인

## 3주차 — 작은 작업 직접 해보기

- [ ] 간단한 버그 수정, 로그 추가 같은 작은 PR 올려보기
- [ ] `BigDecimal`, 트랜잭션 등 "돈 다루는 규칙" 코드에서 확인
- [ ] 코드 리뷰 받으며 회사 컨벤션 익히기

## 4주차 — 도메인 깊이 + 질문 정리

- [ ] 주문 생명주기 전체를 코드로 추적 ([08-architecture.md](08-architecture) 대조)
- [ ] 모르는 용어/로직 리스트업 → 사수에게 질문
- [ ] 우리 시스템의 "장애 시나리오"가 뭔지 물어보기

## 꿀팁

- 모르는 건 **솔직히** 물어보세요. 도메인을 아는 신입은 환영받습니다.
- **금액 계산 + 동시성 + 시간(영업일) 처리** 이 3개만 실수 안 해도 신뢰를 빨리 얻습니다.
- 매일 배운 용어/개념을 메모로 남기면 한 달 뒤 자산이 됩니다.

## 학습 우선순위 (시간이 없다면)

```
1순위: 코틀린 Null 안전 + data class + BigDecimal
2순위: 스프링 Controller→Service→Repository 흐름
3순위: 주문 생명주기 7단계 + 핵심 용어 15개
```

---

다음: [07. 실습: 주문 처리기](07-order-system-tutorial)
