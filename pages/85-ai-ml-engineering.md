# 85. AI/ML 엔지니어링 기본

> AI 기능을 제품에 넣으려면 모델보다 데이터, 평가, 운영, 비용, 안전장치가 더 중요해지는 경우가 많습니다.

## 1. ML 시스템 구성

일반적인 ML 시스템은 다음으로 구성됩니다.

- 데이터 수집.
- feature engineering.
- training.
- evaluation.
- model registry.
- serving.
- monitoring.
- feedback loop.

모델 파일 하나가 아니라 데이터와 운영 파이프라인 전체가 제품입니다.

## 2. 학습과 추론

- Training: 데이터로 모델 파라미터를 학습.
- Inference: 학습된 모델로 예측.
- Batch inference: 대량 데이터 일괄 예측.
- Online inference: 사용자 요청에 실시간 응답.

온라인 추론은 latency, timeout, fallback이 중요합니다.

## 3. 평가

정확도 하나로는 부족합니다.

- precision/recall.
- F1.
- ROC-AUC.
- calibration.
- latency.
- cost per request.
- fairness.
- hallucination rate.
- human evaluation.

제품 목표와 평가 지표가 연결되어야 합니다.

## 4. LLM 애플리케이션

LLM 제품 구성 요소:

- prompt.
- tool/function calling.
- retrieval.
- memory.
- guardrail.
- evaluation set.
- tracing.
- cost control.

프롬프트는 코드처럼 버전 관리하고 테스트해야 합니다.

## 5. RAG

Retrieval-Augmented Generation은 외부 지식을 검색해 모델 입력에 넣는 방식입니다.

구성:

- 문서 수집.
- chunking.
- embedding.
- vector index.
- retrieval.
- reranking.
- generation.
- citation/grounding.

chunk 전략과 평가 데이터가 품질을 크게 좌우합니다.

## 6. 모델 운영

- model versioning.
- canary release.
- rollback.
- drift detection.
- input/output logging 정책.
- privacy filtering.
- rate limit.
- cost budget.

모델은 시간이 지나며 데이터 분포 변화로 성능이 떨어질 수 있습니다.

## 7. AI 안전과 보안

- prompt injection.
- data leakage.
- unsafe output.
- jailbreak.
- training data contamination.
- over-permissioned tool.
- human-in-the-loop.

모델 출력은 신뢰된 코드가 아니라 검증해야 할 외부 입력처럼 다뤄야 합니다.

## 8. 체크리스트

- [ ] 평가 데이터셋과 성공 기준이 있다.
- [ ] 모델/프롬프트/검색 인덱스 버전이 기록된다.
- [ ] latency와 비용을 측정한다.
- [ ] prompt injection과 데이터 유출 방어가 있다.
- [ ] fallback과 human review 경로가 있다.
- [ ] 운영 중 품질 저하를 모니터링한다.
