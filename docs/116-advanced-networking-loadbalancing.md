# 116. 고급 네트워킹과 로드밸런싱

> 대규모 서비스에서는 로드밸런싱, 연결 관리, L4/L7 정책, 네트워크 장애를 이해해야 합니다.

## 1. L4와 L7

- L4: TCP/UDP 레벨. 빠르고 단순.
- L7: HTTP 레벨. path/header 기반 라우팅 가능.
- TLS 종료 위치에 따라 관측성과 보안 경계가 달라진다.
- proxy protocol이나 forwarded header 신뢰 정책이 필요하다.

## 2. 로드밸런싱 알고리즘

- round robin.
- least connections.
- weighted.
- consistent hashing.
- latency-based.
- locality-aware.

stateful 연결이나 cache locality가 있으면 단순 round robin이 최선이 아닐 수 있습니다.

## 3. 연결 관리

- keep-alive.
- connection pool.
- idle timeout.
- max connection.
- TIME_WAIT.
- ephemeral port exhaustion.
- HTTP/2 multiplexing.

클라이언트, LB, 서버의 timeout이 서로 맞지 않으면 502/504가 발생합니다.

## 4. 트래픽 제어

- rate limiting.
- load shedding.
- backpressure.
- priority queue.
- circuit breaker.
- retry budget.
- adaptive concurrency.

재시도는 장애를 고치기도 하지만 장애를 증폭하기도 합니다.

## 5. 네트워크 관측

- packet loss.
- retransmission.
- RTT.
- connection reset.
- DNS latency.
- TLS handshake time.
- LB target health.
- egress traffic.

## 6. 체크리스트

- [ ] client/LB/server timeout이 정렬되어 있다.
- [ ] keep-alive와 connection pool 크기가 적절하다.
- [ ] forwarded header 신뢰 경계가 명확하다.
- [ ] retry budget과 rate limit이 있다.
- [ ] L4/L7 health check가 실제 준비 상태를 반영한다.
- [ ] packet loss/retransmission 지표를 볼 수 있다.
