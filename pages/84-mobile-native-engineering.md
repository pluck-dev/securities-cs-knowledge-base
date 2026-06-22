# 84. 모바일과 네이티브 클라이언트 기본

> 웹만 알아도 서비스는 만들 수 있지만, 모바일 환경의 제약을 이해하면 제품 품질과 API 설계가 좋아집니다.

## 1. 모바일 환경의 제약

모바일은 서버나 데스크톱과 다릅니다.

- 네트워크가 자주 끊긴다.
- 배터리가 제한된다.
- 백그라운드 실행이 제한된다.
- 디바이스 성능 편차가 크다.
- 앱 업데이트가 즉시 반영되지 않는다.
- OS 권한과 스토어 정책을 따라야 한다.

## 2. Android/iOS 차이

| 영역 | Android | iOS |
|---|---|---|
| 언어 | Kotlin/Java | Swift/Objective-C |
| UI | Activity/Fragment/Compose | UIView/SwiftUI |
| 배포 | Play Store, 다양한 기기 | App Store, 제한된 기기군 |
| 백그라운드 | WorkManager 등 | Background modes 제한 |

둘 다 lifecycle과 권한 모델을 이해해야 합니다.

## 3. 앱 생명주기

모바일 앱은 언제든 중단되거나 복원될 수 있습니다.

- foreground/background 전환.
- process death.
- configuration change.
- deep link.
- push notification entry.

상태 복원과 임시 저장을 설계해야 합니다.

## 4. 오프라인과 동기화

모바일에서는 offline-first 사고가 중요합니다.

- local cache.
- optimistic update.
- sync queue.
- conflict resolution.
- retry with backoff.
- stale data 표시.

서버 API는 중복 요청과 지연된 요청을 견뎌야 합니다.

## 5. 푸시 알림

푸시는 사용자 경험과 운영 모두에 중요합니다.

주의:

- 권한 요청 타이밍.
- 토큰 갱신.
- 중복 발송.
- 개인화와 개인정보.
- 딥링크.
- 알림 피로도.

## 6. 모바일 보안

- 루팅/탈옥 탐지의 한계 이해.
- 민감정보는 안전한 저장소 사용.
- 인증서 pinning은 운영 리스크와 함께 검토.
- 앱에 secret을 하드코딩하지 않는다.
- API 권한은 서버에서 최종 검증.
- 스크린 캡처/백업 정책 검토.

## 7. 성능과 관측성

- cold start.
- frame drop.
- memory pressure.
- battery usage.
- network usage.
- crash-free sessions.
- ANR.

모바일 로그는 사용자가 나중에 전송할 수 있으므로 privacy와 샘플링이 중요합니다.

## 8. 체크리스트

- [ ] 네트워크 끊김과 재시도를 고려했다.
- [ ] 앱 버전별 API 호환성을 유지한다.
- [ ] push token 갱신과 딥링크를 처리한다.
- [ ] 민감정보 저장 위치를 검토했다.
- [ ] crash/ANR/startup 지표를 수집한다.
- [ ] 오프라인 동기화 충돌 정책이 있다.
