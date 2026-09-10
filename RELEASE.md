# HabitBridge — 새 버전 만들기 절차 (Android)

같은 머신의 다른 앱 저장소(flux-hourglass-android 등)와 같은 패턴입니다.

1. `android/app/build.gradle.kts`의 `versionName` / `versionCode`가 단일 진실 원천입니다.
2. 릴리스 키스토어는 `android/.keystore/habitbridge-upload.jks`에 두고, 비밀번호는 같은 폴더의
   `release-signing.env`, base64 백업은 `habitbridge-upload.jks.base64.txt`에 둡니다. 세 파일 모두 gitignore 대상입니다.
   **키스토어를 잃으면 같은 서명으로 업데이트할 수 없으므로 반드시 별도 백업하세요.**
3. `vX.Y.Z` 태그를 푸시하면 GitHub Actions(`.github/workflows/release.yml`)가 서명된 APK + AAB를 빌드해
   GitHub Release에 첨부합니다. 저장소 secret 4개가 필요합니다:
   `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`.

## 1. 사전 준비 (최초 1회)

```properties
# android/local.properties (gitignore)
sdk.dir=C:/Users/<USER>/AppData/Local/Android/Sdk
```

키스토어가 없다면:

```powershell
cd android
New-Item -ItemType Directory -Force .keystore | Out-Null
keytool -genkeypair -v -keystore .keystore/habitbridge-upload.jks -alias upload `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -dname "CN=HabitBridge, OU=Apps, O=jeiel85, L=Seoul, ST=Seoul, C=KR" `
  -storepass <STORE_PASSWORD> -keypass <KEY_PASSWORD>
```

`.keystore/release-signing.env`:

```env
STORE_PASSWORD=...
KEY_PASSWORD=...
KEY_ALIAS=upload
KEYSTORE_PATH=D:/Project/habitbridge-android/android/.keystore/habitbridge-upload.jks
```

## 2. 로컬 빌드

```bash
cd android
./gradlew :core:policy:test            # 정책 엔진 테스트
./gradlew :app:assembleDebug           # app/build/outputs/apk/debug/app-debug.apk (applicationId com.habitbridge.debug)
set -a; . ./.keystore/release-signing.env; set +a
./gradlew :app:assembleRelease :app:bundleRelease
# → app/build/outputs/apk/release/app-release.apk, app/build/outputs/bundle/release/app-release.aab
```

서명 확인:

```bash
$ANDROID_HOME/build-tools/36.1.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 3. 에뮬레이터/실기기 검증 체크리스트

```bash
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.habitbridge.debug android:get_usage_stats allow
adb shell appops set com.habitbridge.debug SYSTEM_ALERT_WINDOW allow
adb shell logcat -s HabitBridge/Coord HabitBridge/Overlay HabitBridge/Detect HabitBridge/App
```

- [ ] 온보딩에서 필수 권한 2개(사용 정보 접근, 다른 앱 위에 표시)가 "허용됨"으로 바뀌고 계속 버튼이 켜진다
- [ ] 대상 앱을 열면 3.5초 안에 인터셉터가 뜬다 (`intercept … / show …` 로그)
- [ ] 스왑 4종 각각 완료 시 홈으로 돌아가고 대시보드 최근 기록에 남는다
- [ ] NORMAL 쉴드에서 5초 뒤 `[>] Continue to app`이 활성화되고, 우회 뒤 10분간 같은 앱은 마찰 없이 열린다 (`allow … COOLDOWN`)
- [ ] 취침 시간대/할당 소진에서는 계속 버튼이 잠긴다
- [ ] 메모 내보내기 → `Documents/HabitBridge/Notes_YYYYMMDD.md` 생성, 같은 날 재내보내기 시 파일이 덮어써진다
- [ ] 시스템 설정 앱처럼 오버레이를 숨기는 앱은 대상에서 제외한다(정상 동작, OS 제약)

## 4. 릴리스

```bash
# 버전 올리기: android/app/build.gradle.kts → versionCode +1, versionName
git commit -am "release: vX.Y.Z"
git tag vX.Y.Z && git push && git push --tags
```

Actions가 끝나면 GitHub Release에 `habitbridge-X.Y.Z.apk`, `habitbridge-X.Y.Z.aab`가 첨부됩니다.
Play Console에 올릴 때는 `.aab`를 사용합니다(정책 체크리스트: `docs/PRD-SDD.md` §8.3).
