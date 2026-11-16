# 보안 안내

- 이 공개 저장소에는 TAGO API 키의 예시 값만 포함합니다. 실제 API 키, `local.properties`, 서명키, `.env` 및 기타 인증정보를 커밋하지 마세요.
- TAGO 조회가 필요하면 `app/src/keyTemplate/java/kr/yeaan/bus/config/ApiKeys.kt`를 `app/src/main/java/kr/yeaan/bus/config/ApiKeys.kt`로 복사하고, 직접 승인받은 키를 입력하세요.
- 네이버 지도 Client ID는 Git에서 제외된 `local.properties`에 보관하고, 네이버 클라우드 콘솔에서 패키지 이름을 등록하세요.
- Android APK에 포함된 인증정보는 역공학으로 추출할 수 있습니다. 개인 키를 삽입한 APK를 공개 배포하지 마세요.
- 과거 커밋·빌드 파일·로그 등에 키가 유출됐다면 파일 삭제만으로 해결되지 않습니다. 발급처에서 키를 폐기하거나 교체하세요.
- 유출 의심이나 보안 취약점은 공개 이슈에 실제 값을 올리지 말고 저장소 소유자에게 비공개로 알려주세요.

이 공개본에는 원본 비공개 저장소의 개발 이력과 오래된 스크린샷을 포함하지 않습니다.
