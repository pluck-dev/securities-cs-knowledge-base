# 87. 실시간 협업 시스템

> 채팅, 공동 편집, 실시간 대시보드는 지연, 순서, 충돌, 연결 상태를 함께 설계해야 합니다.

## 1. 실시간의 종류

| 종류 | 예시 | 핵심 문제 |
|---|---|---|
| Notification | 알림 배지 | 유실/중복 |
| Chat | 채팅방 | 순서/읽음 상태 |
| Live Dashboard | 시세/모니터링 | 지연/샘플링 |
| Collaborative Editing | 문서 공동 편집 | 충돌 해결 |
| Multiplayer State | 게임/화이트보드 | latency compensation |

실시간은 “빠름”뿐 아니라 상태 수렴이 중요합니다.

## 2. 전송 방식

- WebSocket: 양방향 연결.
- SSE: 서버에서 클라이언트로 스트리밍.
- Long polling: 호환성 좋지만 비효율.
- WebRTC: P2P 미디어/데이터.
- Push notification: 앱이 꺼져도 알림.

프록시 timeout, 재연결, 인증 갱신을 고려해야 합니다.

## 3. 연결 상태

클라이언트는 항상 연결되어 있지 않습니다.

필요한 설계:

- heartbeat.
- reconnect with backoff.
- resume token.
- last seen event id.
- offline queue.
- presence TTL.

서버는 끊어진 연결을 정리해야 합니다.

## 4. 순서와 중복

실시간 메시지는 중복되거나 순서가 바뀔 수 있습니다.

- server sequence number.
- client timestamp는 신뢰하지 않음.
- idempotency key.
- de-duplication window.
- per-room/per-document ordering.

전체 글로벌 순서를 요구하면 확장성이 떨어집니다.

## 5. CRDT와 OT

공동 편집 충돌 해결 방식:

- OT(Operational Transform): 동시 편집 operation을 변환.
- CRDT: 병합 가능한 자료구조로 모든 복제본이 수렴.

둘 다 구현 난도가 높으므로 라이브러리 선택과 데이터 모델이 중요합니다.

## 6. Fan-out

메시지를 많은 사용자에게 전달하는 방식:

- fan-out on write: 쓰기 시 각 수신자 큐에 복사.
- fan-out on read: 읽을 때 모아서 계산.
- topic/channel 기반 publish-subscribe.

대규모 채팅과 알림은 fan-out 비용이 핵심 병목입니다.

## 7. 운영 지표

- active connections.
- reconnect rate.
- message publish latency.
- delivery latency.
- dropped messages.
- fan-out queue lag.
- presence accuracy.
- memory per connection.

## 8. 체크리스트

- [ ] 재연결과 resume 전략이 있다.
- [ ] 메시지 중복과 순서 뒤바뀜을 처리한다.
- [ ] presence는 TTL 기반으로 설계한다.
- [ ] fan-out 비용을 추정했다.
- [ ] 실시간 지연과 유실을 측정한다.
- [ ] 공동 편집은 OT/CRDT 선택 이유가 있다.
