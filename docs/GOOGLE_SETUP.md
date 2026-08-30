# Google 할 일(Tasks) 연동 설정

앱이 운동 기록을 본인 Google 계정의 할 일 목록에 "완료"로 올리려면 OAuth 클라이언트가 필요합니다. 한 번만 해 두면 됩니다.

## 1. 서명 키의 SHA-1 확인

CI가 APK에 서명하는 키의 지문이 필요합니다. README의 안내대로 `pullup-release.jks`를 만들었다면:

```bash
keytool -list -v -keystore pullup-release.jks -alias pullup | grep SHA1
```

`SHA1: AB:CD:...` 형태의 값을 복사해 둡니다.

> 로컬에서 디버그 빌드로도 테스트하려면 디버그 키의 SHA-1도 같은 방법으로 뽑아 클라이언트를 하나 더 만드세요.
> (`~/.android/debug.keystore`, alias `androiddebugkey`, 비밀번호 `android`, 패키지명은 `com.pullup.tracker.debug`)

## 2. Google Cloud 프로젝트

1. https://console.cloud.google.com 에서 프로젝트를 새로 만듭니다.
2. **API 및 서비스 → 라이브러리** 에서 **Google Tasks API** 를 검색해 사용 설정합니다.

## 3. OAuth 동의 화면

1. **API 및 서비스 → OAuth 동의 화면**
2. User Type: **외부(External)**
3. 앱 이름, 지원 이메일, 개발자 이메일만 채우면 됩니다.
4. 범위(Scope)에 `https://www.googleapis.com/auth/tasks` 추가
5. **테스트 사용자**에 본인 Gmail 주소를 추가합니다. (게시하지 않아도 테스트 사용자는 그대로 쓸 수 있습니다)

## 4. OAuth 클라이언트 ID 만들기

1. **API 및 서비스 → 사용자 인증 정보 → 사용자 인증 정보 만들기 → OAuth 클라이언트 ID**
2. 애플리케이션 유형: **Android**
3. 패키지 이름: `com.pullup.tracker`
4. SHA-1 인증서 지문: 1단계에서 복사한 값
5. 만들어진 클라이언트 ID(`123456789-abcdefg.apps.googleusercontent.com`)를 복사합니다.

Android 클라이언트에는 클라이언트 보안 비밀이 없습니다. 앱은 PKCE로 인증하므로 클라이언트 ID는 저장소 변수로 공개해도 안전합니다.

## 5. 저장소에 등록하고 빌드

GitHub 저장소 → **Settings → Secrets and variables → Actions → Variables** 탭에서:

- 이름: `GOOGLE_OAUTH_CLIENT_ID`
- 값: 위 클라이언트 ID

등록 후 `main`에 아무 커밋이나 밀거나 **Actions → Android CI → Run workflow** 로 새 APK를 만듭니다.

## 6. 앱에서 연결

1. 새 APK 설치
2. **설정 → Google 할 일 연동 → Google 계정 연결**
3. 브라우저에서 로그인 및 권한 허용
4. **불러오기** 를 눌러 할 일 목록을 가져온 뒤 "운동" 목록 선택
5. **기록하면 자동으로 업로드** 를 켜 두면, 오늘 탭에서 기록할 때마다 완료된 할 일이 쌓입니다

## 만들어지는 할 일 모양

- 제목: `풀업 24개 (6/5/5/4/4)` — 설정에서 형식을 바꿀 수 있습니다 (`{exercise} {total} {sets} {date} {session}`)
- 메모: 세트별 실제/목표, 총계, 남긴 메모
- 상태: 완료, 완료 시각은 기록한 시각, 기한은 운동한 날짜

## 잘 안 될 때

| 증상 | 원인 |
| --- | --- |
| 로그인 화면에서 `redirect_uri_mismatch` | 클라이언트 유형이 Android가 아니거나 패키지명이 다름 |
| `Error 403: access_denied` | OAuth 동의 화면의 테스트 사용자에 본인 계정이 없음 |
| 로그인 창이 바로 닫힘 | APK 서명 키의 SHA-1이 클라이언트에 등록된 값과 다름 |
| 목록 불러오기 실패 | Google Tasks API가 사용 설정되지 않음 |
