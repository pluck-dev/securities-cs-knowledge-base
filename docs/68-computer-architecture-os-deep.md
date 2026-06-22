# 68. 컴퓨터 구조와 운영체제 심화

> 성능, 장애, 동시성 문제를 깊게 이해하려면 CPU·메모리·커널·I/O의 동작을 알아야 합니다.

## 1. CPU 실행 모델

CPU는 명령어를 순차적으로 실행하는 것처럼 보이지만 실제로는 파이프라인, 캐시, 분기 예측, out-of-order execution을 활용합니다.

실무에서 중요한 점:

- 분기 예측 실패는 비용이 든다.
- 데이터가 CPU 캐시에 있으면 매우 빠르다.
- 메모리 접근 패턴이 성능을 크게 좌우한다.
- false sharing은 멀티스레드 성능을 망칠 수 있다.

## 2. 메모리 계층

```
Register → L1 → L2 → L3 → RAM → SSD → Network
```

- 가까울수록 빠르고 작다.
- 배열처럼 연속된 데이터는 캐시에 유리하다.
- 객체 포인터를 따라가는 구조는 cache miss가 많아질 수 있다.
- 대량 객체 생성은 GC 또는 allocator 부담을 키운다.

## 3. 가상 메모리

운영체제는 프로세스마다 독립된 주소 공간을 제공합니다.

- page: 메모리 관리 단위.
- page fault: 필요한 페이지가 메모리에 없어 커널이 가져오는 상황.
- swap: 메모리가 부족할 때 디스크를 메모리처럼 쓰는 것. 서버에서는 급격한 성능 저하를 만든다.
- mmap: 파일을 메모리처럼 매핑하는 방식.

## 4. 시스템 콜

애플리케이션이 파일, 네트워크, 프로세스 같은 커널 자원에 접근할 때 시스템 콜을 사용합니다.

- `read`, `write`, `open`, `close`
- `socket`, `accept`, `connect`
- `fork`, `exec`, `wait`
- `epoll`, `kqueue`, `select`

시스템 콜은 사용자 모드와 커널 모드 전환이 필요하므로 너무 자주 호출하면 비용이 됩니다.

## 5. 파일 I/O

파일 쓰기는 애플리케이션이 `write()`를 호출했다고 즉시 디스크에 영구 저장되는 것이 아닐 수 있습니다.

- page cache가 중간에 있다.
- fsync는 내구성을 높이지만 비싸다.
- WAL은 DB가 장애 복구를 위해 사용하는 대표 패턴이다.
- 순차 I/O는 랜덤 I/O보다 보통 빠르다.

## 6. 네트워크 I/O 모델

| 모델 | 설명 | 장단점 |
|---|---|---|
| Blocking I/O | 호출 스레드가 기다림 | 단순하지만 연결이 많으면 스레드 비용 증가 |
| Non-blocking I/O | 준비되지 않으면 즉시 반환 | 복잡하지만 많은 연결 처리 가능 |
| Event Loop | 이벤트를 받아 콜백/태스크 처리 | 적은 스레드로 고동시성 처리 |
| Thread-per-request | 요청마다 스레드 할당 | 이해 쉽지만 대규모 연결에서 부담 |

## 7. 스케줄링

운영체제는 CPU 시간을 여러 프로세스/스레드에 나눕니다.

- runnable thread가 CPU보다 많으면 대기한다.
- context switch는 레지스터와 실행 상태를 바꾸는 비용이다.
- CPU-bound 작업과 I/O-bound 작업은 튜닝 방식이 다르다.
- 스레드 풀 크기는 “크게”가 아니라 작업 특성에 맞춰야 한다.

## 8. 락의 실제 비용

락은 단순히 코드 한 줄이 아닙니다.

- uncontended lock은 비교적 싸다.
- 경합이 생기면 대기, context switch, cache invalidation이 발생한다.
- synchronized, mutex, spin lock, read-write lock은 비용과 적합한 상황이 다르다.
- 락을 오래 잡은 채 I/O를 하면 장애가 커질 수 있다.

## 9. 런타임과 GC

GC 언어에서는 객체 할당 패턴이 성능에 직접 영향을 줍니다.

- short-lived object는 young generation에서 빠르게 회수되는 경우가 많다.
- long-lived object가 많으면 old generation 압박이 커진다.
- 큰 객체, 많은 문자열, boxing은 메모리 압박을 키운다.
- GC pause는 p99 지연에 영향을 준다.

## 10. 실무 진단 포인트

- CPU가 높은가, I/O wait가 높은가?
- 메모리가 부족한가, page cache가 정상인가?
- thread dump에서 무엇을 기다리는가?
- file descriptor가 고갈되었는가?
- 네트워크 재전송이나 connection backlog가 있는가?
- GC pause가 지연 시간 spike와 일치하는가?

## 체크리스트

- [ ] CPU-bound와 I/O-bound를 구분할 수 있다.
- [ ] 메모리 계층과 cache locality의 의미를 설명할 수 있다.
- [ ] blocking/non-blocking I/O 차이를 이해한다.
- [ ] thread dump, heap dump, GC log를 분석할 수 있다.
- [ ] 파일 쓰기와 fsync의 차이를 알고 있다.
