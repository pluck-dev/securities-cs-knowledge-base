# 89. 제품 분석과 실험 플랫폼

> 개발자는 기능을 배포하는 것에서 끝나지 않고 사용자가 실제로 가치를 얻는지 측정할 수 있어야 합니다.

## 1. 제품 지표

제품 지표는 기술 지표와 다릅니다.

- activation.
- retention.
- engagement.
- conversion.
- churn.
- revenue.
- funnel drop-off.

기술적으로 성공한 배포가 제품적으로 실패할 수 있습니다.

## 2. 이벤트 설계

좋은 이벤트 이름:

- 일관된 동사와 명사.
- 화면/소스 정보.
- user/session id.
- timestamp.
- schema version.
- 중요한 속성.

나쁜 이벤트는 중복, 모호한 이름, 누락된 속성, 개인정보 포함입니다.

## 3. 퍼널 분석

퍼널은 사용자가 목표까지 가는 단계별 전환을 봅니다.

예:

```text
방문 → 가입 시작 → 본인 인증 → 계좌 연결 → 첫 주문
```

각 단계의 정의와 시간 창을 명확히 해야 합니다.

## 4. 코호트와 리텐션

코호트는 같은 시점이나 조건으로 묶은 사용자 그룹입니다.

- 가입 주차별 리텐션.
- 캠페인별 리텐션.
- 기능 사용 여부별 리텐션.
- 유료 전환 cohort.

리텐션은 제품 가치의 핵심 지표입니다.

## 5. A/B 테스트

A/B 테스트 기본 요소:

- hypothesis.
- primary metric.
- guardrail metric.
- randomization unit.
- sample size.
- experiment duration.
- statistical significance.

실험은 원하는 결론을 얻기 위한 도구가 아니라 불확실성을 줄이는 도구입니다.

## 6. Feature Flag

Feature flag는 배포와 공개를 분리합니다.

주의:

- flag cleanup.
- 사용자 bucket 안정성.
- 서버/클라이언트 평가 위치.
- kill switch.
- 권한과 audit.
- 성능 영향.

## 7. 데이터 해석 함정

- correlation과 causation 혼동.
- sample bias.
- novelty effect.
- seasonality.
- Simpson paradox.
- p-hacking.
- metric gaming.

숫자는 질문을 더 잘하게 만들 뿐 자동으로 답을 주지 않습니다.

## 8. 체크리스트

- [ ] 이벤트 스키마가 문서화되어 있다.
- [ ] 핵심 퍼널 단계 정의가 명확하다.
- [ ] A/B 테스트에 guardrail metric이 있다.
- [ ] feature flag 제거 계획이 있다.
- [ ] 개인정보를 분석 이벤트에 넣지 않는다.
- [ ] 실험 결과를 통계와 제품 맥락으로 함께 해석한다.
