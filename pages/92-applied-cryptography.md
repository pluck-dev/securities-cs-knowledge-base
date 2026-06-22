# 92. 실무 암호학

> 암호학은 직접 발명하는 것이 아니라 검증된 primitive를 올바르게 조합하는 기술입니다.

## 1. 기본 원칙

- 직접 암호 알고리즘을 만들지 않는다.
- 오래된 알고리즘과 mode를 피한다.
- key와 data를 분리해서 관리한다.
- random은 CSPRNG를 사용한다.
- 암호화와 서명, 해시, MAC을 혼동하지 않는다.
- 보안은 알고리즘보다 key management에서 자주 깨진다.

## 2. Hash와 Password Hash

일반 hash는 무결성 확인에 쓰고, password hash는 느리게 계산되도록 설계됩니다.

- SHA-256: 빠른 hash. 비밀번호 저장에는 부적합.
- bcrypt/scrypt/Argon2: password hashing.
- salt: 같은 비밀번호도 다른 hash가 나오게 함.
- pepper: 별도 secret으로 관리하는 추가 값.

비밀번호는 복호화할 수 없어야 합니다.

## 3. 대칭키와 비대칭키

| 방식 | 예 | 용도 |
|---|---|---|
| 대칭키 | AES-GCM, ChaCha20-Poly1305 | 데이터 암호화 |
| 비대칭키 | RSA, ECDSA, Ed25519 | 키 교환, 서명 |
| MAC | HMAC | 메시지 인증 |
| KDF | HKDF, PBKDF2 | 키 파생 |

대량 데이터는 대칭키로 암호화하고, 비대칭키는 키 교환이나 서명에 주로 씁니다.

## 4. AEAD

현대 애플리케이션 암호화는 인증된 암호화(AEAD)를 선호합니다.

- 기밀성과 무결성을 함께 제공.
- AES-GCM, ChaCha20-Poly1305.
- nonce 재사용은 치명적.
- associated data로 암호화하지 않지만 검증할 메타데이터를 넣을 수 있다.

## 5. Key Management

- KMS/HSM 사용.
- key rotation.
- envelope encryption.
- key version 기록.
- 접근 감사.
- 폐기와 복구 정책.

암호문만 저장하면 안 되고 어떤 key version으로 암호화했는지도 필요합니다.

## 6. 실무 체크리스트

- [ ] 비밀번호는 password hash로 저장한다.
- [ ] 데이터 암호화는 AEAD를 사용한다.
- [ ] nonce/iv 재사용 방지 전략이 있다.
- [ ] key는 KMS/secret manager에서 관리한다.
- [ ] key rotation 절차를 테스트했다.
- [ ] 암호화 전후 로그에 민감정보가 남지 않는다.
