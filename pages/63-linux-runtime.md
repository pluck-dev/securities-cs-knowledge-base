# 63. 리눅스와 런타임 운영 기본

> 대부분의 서버는 리눅스 위에서 동작합니다. 코드를 운영하려면 프로세스, 파일, 권한, 로그, 자원 사용량을 읽을 수 있어야 합니다.

## 1. 파일 시스템

- `/`: 루트.
- `/etc`: 설정.
- `/var`: 로그, 캐시, 동적 데이터.
- `/tmp`: 임시 파일.
- `/home`: 사용자 홈.
- `/proc`: 커널이 제공하는 프로세스/시스템 정보.

### 권한

```
rwxr-x---
소유자 그룹 기타
```

- `r`: 읽기.
- `w`: 쓰기.
- `x`: 실행 또는 디렉터리 진입.
- 서비스 계정은 필요한 최소 권한만 가져야 한다.

## 2. 프로세스 관리

자주 쓰는 명령:

```bash
ps aux
 top
 htop
 pgrep -af java
 kill -TERM <pid>
 kill -KILL <pid>
```

- `SIGTERM`은 정상 종료 요청이다.
- `SIGKILL`은 정리 기회를 주지 않고 종료한다.
- graceful shutdown은 요청 처리 중단, 큐 drain, 커넥션 종료, 상태 저장을 포함한다.

## 3. 자원 확인

```bash
free -h          # 메모리
 df -h           # 디스크 사용량
 du -sh *        # 디렉터리별 크기
 iostat          # 디스크 I/O
 vmstat          # CPU/메모리/스왑
 ss -lntp        # 리슨 포트
 lsof -i :8080   # 포트 사용 프로세스
```

## 4. 로그

- 애플리케이션 로그: 서비스가 남기는 구조화 로그.
- 시스템 로그: systemd/journald, 커널 로그.
- 액세스 로그: 프록시나 웹서버 요청 로그.
- 감사 로그: 보안·규제 이벤트.

좋은 로그는 시간, 레벨, 서비스명, trace id, 사용자/계정 식별자, 이벤트 코드, 결과를 포함합니다. 비밀번호·토큰·주민번호 같은 민감정보는 남기지 않습니다.

## 5. 네트워크 도구

```bash
curl -v https://example.com
 dig example.com
 nc -vz host 443
 traceroute host
 ss -antp
```

장애 분석에서는 DNS, TCP 연결, TLS, HTTP 상태코드를 분리해서 확인합니다.

## 6. systemd 서비스

```bash
systemctl status my-service
 journalctl -u my-service -f
 systemctl restart my-service
```

운영 서버에서는 수동 실행보다 systemd, supervisor, 컨테이너 오케스트레이터 같은 프로세스 관리자가 서비스를 살려두게 합니다.

## 7. 컨테이너 런타임 기본

컨테이너는 작은 VM이 아니라 격리된 프로세스입니다.

- namespace: PID, network, mount 등을 분리한다.
- cgroup: CPU/메모리 같은 자원 사용량을 제한한다.
- image layer: 파일 시스템 레이어를 쌓는다.
- volume: 컨테이너 생명주기와 독립된 데이터 저장.

주의점:

- 컨테이너 안에서 root로 실행하지 않는다.
- 로그는 stdout/stderr로 내보내 수집한다.
- 이미지에는 빌드 도구와 비밀값을 남기지 않는다.
- readiness/liveness probe를 구분한다.

## 8. JVM/Node 런타임 운영 감각

### JVM

- heap 크기와 container memory limit을 맞춘다.
- GC 로그와 pause time을 본다.
- thread dump로 deadlock과 blocking을 분석한다.
- connection pool과 thread pool 크기를 근거 없이 키우지 않는다.

### Node.js

- 이벤트 루프가 막히면 전체 요청 처리가 지연된다.
- CPU-heavy 작업은 worker thread나 별도 서비스로 분리한다.
- 메모리 누수는 heap snapshot으로 추적한다.
- unhandled rejection과 uncaught exception 정책을 명확히 한다.

## 9. 운영 체크리스트

- [ ] 서비스는 정상 종료 신호를 처리한다.
- [ ] 로그가 stdout/stderr 또는 표준 수집 경로로 간다.
- [ ] 메모리·CPU limit과 런타임 설정이 맞다.
- [ ] 디스크가 가득 찰 때의 동작을 알고 있다.
- [ ] 포트, DNS, 인증서 문제를 명령어로 확인할 수 있다.
- [ ] 비밀값이 이미지·로그·쉘 히스토리에 남지 않는다.
