# 86. 검색, 추천, 벡터 데이터베이스

> 검색과 추천은 데이터 구조, 랭킹, 품질 평가, 사용자 행동 로그가 결합된 시스템입니다.

## 1. 검색 시스템 구조

```text
Document → Tokenize/Normalize → Index → Query Parse → Retrieve → Rank → Highlight
```

검색은 단순 DB LIKE가 아니라 역색인과 랭킹 문제입니다.

## 2. 역색인

역색인은 단어에서 문서 목록으로 가는 자료구조입니다.

- tokenization.
- stemming/lemmatization.
- stopword.
- posting list.
- term frequency.
- document frequency.

한국어 검색은 형태소 분석과 복합어 처리가 특히 중요합니다.

## 3. 랭킹

랭킹 요소:

- BM25.
- field boost.
- recency.
- popularity.
- personalization.
- business rule.
- learning-to-rank.

검색 품질은 “나왔는가”보다 “좋은 결과가 위에 있는가”입니다.

## 4. 자동완성과 오타 교정

- prefix index.
- n-gram.
- trie.
- edit distance.
- synonym dictionary.
- query log 기반 추천.

자동완성은 latency가 매우 중요하고, 개인정보/민감 검색어 저장 정책도 필요합니다.

## 5. 추천 시스템

추천 방식:

- popularity-based.
- content-based.
- collaborative filtering.
- matrix factorization.
- sequence model.
- hybrid.

추천은 정확도뿐 아니라 다양성, 신선도, 설명 가능성, 편향을 고려합니다.

## 6. 벡터 검색

벡터 검색은 의미적으로 가까운 embedding을 찾습니다.

- embedding model.
- vector index.
- ANN.
- HNSW.
- IVF.
- cosine/dot/euclidean similarity.

벡터 검색은 semantic search와 RAG에 자주 쓰입니다.

## 7. 평가

검색/추천 평가 지표:

- precision@k.
- recall@k.
- MRR.
- NDCG.
- CTR.
- conversion.
- long-term retention.

오프라인 평가와 온라인 A/B 테스트를 함께 봐야 합니다.

## 8. 체크리스트

- [ ] 검색 대상 필드와 analyzer가 문서화되어 있다.
- [ ] 랭킹 지표와 business rule이 분리되어 있다.
- [ ] zero-result query를 모니터링한다.
- [ ] 벡터 검색 품질을 평가 데이터로 검증한다.
- [ ] 추천의 편향과 다양성을 점검한다.
- [ ] 사용자 검색 로그의 개인정보 정책이 있다.
