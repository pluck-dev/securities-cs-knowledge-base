# 115. 고급 캐싱, CDN, Edge 전략

> 캐시는 가장 강력한 성능 도구이면서 가장 흔한 정합성 사고 원인입니다.

## 1. 캐시 계층

- browser cache.
- service worker cache.
- CDN cache.
- reverse proxy cache.
- application cache.
- distributed cache.
- database buffer cache.

각 계층의 TTL과 무효화 정책이 다르면 디버깅이 어려워집니다.

## 2. HTTP 캐시

주요 헤더:

- Cache-Control.
- ETag.
- Last-Modified.
- Vary.
- Age.
- Surrogate-Control.

Vary 헤더를 잘못 쓰면 언어/인증/디바이스별 응답이 섞일 수 있습니다.

## 3. CDN 전략

- static asset immutable cache.
- URL fingerprinting.
- stale-while-revalidate.
- stale-if-error.
- purge/invalidation.
- origin shield.
- signed URL/cookie.

CDN은 origin 보호에도 중요하지만 개인정보 응답을 캐시하면 큰 사고가 됩니다.

## 4. 캐시 무효화

대표 전략:

- TTL 기반.
- write-through invalidation.
- event-driven invalidation.
- versioned key.
- namespace bump.
- cache tag purge.

무효화가 어려우면 짧은 TTL과 stale 허용 여부를 업무적으로 결정합니다.

## 5. 장애 패턴

- cache stampede.
- hot key.
- stale sensitive data.
- dogpile.
- thundering herd.
- partial purge.
- origin overload.

request coalescing과 jittered TTL이 도움이 됩니다.

## 6. 체크리스트

- [ ] 캐시 계층별 TTL과 owner가 문서화되어 있다.
- [ ] 인증/개인화 응답은 공유 캐시에 저장되지 않는다.
- [ ] Vary 헤더를 검토했다.
- [ ] stampede/hot key 대응이 있다.
- [ ] purge 실패 시 fallback이 있다.
- [ ] stale 허용 범위가 업무적으로 정의되어 있다.
