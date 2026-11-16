# Security / 보안 안내

- This public source repository contains only a placeholder TAGO key. Do not commit personal API keys, `local.properties`, signing keys, `.env` files, or credentials.
- Copy `app/src/keyTemplate/java/kr/yeaan/bus/config/ApiKeys.kt` into the ignored local `app/src/main/java/kr/yeaan/bus/config/ApiKeys.kt` before building with your own approved TAGO key.
- Keep `NAVER_MAP_CLIENT_ID` in ignored `local.properties` and register the Android application package in the NAVER Cloud console.
- Keys embedded in Android binaries can be extracted. Do not publish APKs built with personal keys.
- If a key was previously exposed in source, artifacts, logs, or Git history, revoke/rotate it at the issuer; removing a file alone cannot revoke a credential.
- Report suspected credential exposure privately to the repository owner, without including live secrets in public issues.

This public snapshot contains no private repository commit history or historical screenshots. / 이 공개본은 비공개 저장소의 커밋 이력 및 과거 스크린샷을 포함하지 않습니다.
