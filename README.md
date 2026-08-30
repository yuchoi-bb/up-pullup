# 업풀업 (Up-Pullup)

풀업을 매일 몇 개 했고 오늘 몇 개 해야 하는지 한 화면에서 확인하고, 체크하면 **Google 할 일(Google Tasks)** 에 완료로 올라가는 안드로이드 앱입니다.
GitHub Actions가 APK를 만들고, 앱은 스스로 새 버전을 찾아 내려받아 설치합니다.

## 무엇이 들어 있나

| 화면 | 하는 일 |
| --- | --- |
| **오늘** | 지난 기록(어제 몇 개), 오늘 목표 세트, 세트별 개수 조절·체크, 휴식 타이머, 기록 + Google 업로드 |
| **플랜** | 100개까지의 전체 로드맵, 진행률, 남은 세션, 완주 예상일 |
| **기록** | 누적/연속/최고 기록, 최근 14세션 막대 차트, 기록 목록(동기화 상태·재시도·삭제) |
| **AI 코치** | Gemini로 새 종목·목표·기간에 맞는 세션 계획 생성, 자유 질문 |
| **설정** | Google 연동·할 일 목록 선택, Gemini 키/모델, 운동 요일·휴식·자동 조절, 알림, 앱 업데이트, CSV 내보내기 |

## 처음부터 들어 있는 플랜

첫 실행 시 **풀업 100 프로젝트**가 자동으로 만들어집니다.

- 시작: `6 / 5 / 5 / 4 / 4` = 24개 (오늘 한 기록으로 1번 세션이 이미 완료 처리되어 들어갑니다)
- 목표: `20 / 20 / 20 / 20 / 20` = 100개
- 총 48세션. 주 5일이면 약 10주.
- 규칙: 뒤쪽 세트를 앞 세트 개수까지 채운 뒤 전체를 한 칸 올립니다. 세션당 +2개, 5번째 세션마다 증가 없이 "다지기".

```
#1  6/5/5/4/4   24     #10 8/8/8/7/7   38 (다지기)
#2  6/6/6/4/4   26     #20 11/11/11/11/10  54
#3  6/6/6/6/4   28     #30 14/14/14/14/14  70
#4  6/6/6/6/6   30     #40 18/17/17/17/17  86
#5  6/6/6/6/6   30     #48 20/20/20/20/20 100 🎉
```

초기 기록이 마음에 안 들면 **기록** 탭에서 지우면 됩니다. 목표를 못 채우면(자동 조절 ON) 같은 세션을 한 번 더, 크게 초과하면 한 세션을 건너뜁니다.

## 빌드와 배포

- `main` 또는 `claude/**` 브랜치에 push하면 GitHub Actions가 APK를 빌드합니다.
- **기본 브랜치** push와 `v*` 태그 push는 릴리스를 발행하고 APK를 첨부합니다. 버전은 `1.0.0.<run_number>` 형태입니다.
- 서명 키가 등록되어 있으면 정식 릴리스로, 없으면 **프리릴리스**로 올립니다.
  앱은 기본적으로 정식 릴리스만 업데이트 대상으로 보므로, 서명이 매번 바뀌는 debug 빌드가
  자동 업데이트로 내려와 설치에 실패하는 일이 없습니다.
- 앱은 시작할 때(설정에서 끌 수 있음) 그리고 **설정 → 앱 업데이트**에서 GitHub 릴리스를 확인하고, 더 높은 버전이면 내려받아 설치합니다.

### 서명 키 (업데이트 설치와 Google 로그인에 필요)

키가 없으면 CI는 debug 키로 서명하고, 그 빌드는 프리릴리스로만 올라갑니다. debug 키는 빌드마다 달라져 **덮어쓰기 설치가 실패**하고 Google 로그인도 막힙니다. 한 번만 만들어 두세요.

> 이 저장소는 **공개** 상태입니다. `.jks` 파일과 base64 문자열은 커밋하지 말고,
> Actions 아티팩트로도 올리지 마세요. 공개 저장소의 아티팩트는 누구나 내려받을 수 있습니다.

```bash
keytool -genkeypair -v -keystore pullup-release.jks \
  -alias pullup -keyalg RSA -keysize 2048 -validity 10000

base64 -w0 pullup-release.jks    # 이 값을 시크릿에 넣습니다
keytool -list -v -keystore pullup-release.jks -alias pullup | grep SHA1
```

저장소 **Settings → Secrets and variables → Actions** 에 등록합니다.

| 이름 | 종류 | 값 |
| --- | --- | --- |
| `KEYSTORE_BASE64` | Secret | 위 base64 문자열 |
| `KEYSTORE_PASSWORD` | Secret | keystore 비밀번호 |
| `KEY_ALIAS` | Secret | `pullup` |
| `KEY_PASSWORD` | Secret | 키 비밀번호 |
| `GOOGLE_OAUTH_CLIENT_ID` | **Variable** | `123-xxxx.apps.googleusercontent.com` |

`pullup-release.jks` 파일은 저장소에 커밋하지 말고 따로 보관하세요.

## Google 할 일 연동 설정

[docs/GOOGLE_SETUP.md](docs/GOOGLE_SETUP.md) 에 단계별로 정리해 두었습니다. 요약하면:

1. Google Cloud 프로젝트에서 **Google Tasks API** 사용 설정
2. OAuth 동의 화면 구성 후 본인 계정을 테스트 사용자로 추가
3. **Android** 유형 OAuth 클라이언트 ID 생성 (패키지명 `com.pullup.tracker`, 위에서 뽑은 SHA-1)
4. 클라이언트 ID를 저장소 변수 `GOOGLE_OAUTH_CLIENT_ID` 로 등록하고 다시 빌드
5. 앱 **설정 → Google 계정 연결** → **불러오기** → "운동" 목록 선택

클라이언트 ID가 없어도 앱은 그대로 동작합니다. Google 업로드 기능만 꺼진 상태가 됩니다.

## Gemini 연동

Google AI Studio에서 API 키를 발급받아 **설정 → Gemini AI** 에 붙여넣으면 됩니다. 키는 기기 안에만 저장됩니다.
키가 없어도 AI 코치 탭의 **"앱 규칙으로 만들기"** 로 같은 사다리 플랜을 만들 수 있습니다.

## 로컬 빌드

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Android SDK 35, JDK 17이 필요합니다.

## 기술 구성

Kotlin · Jetpack Compose (Material 3) · kotlinx.serialization(JSON 파일 저장) · OkHttp · AppAuth(OAuth 2.0 + PKCE) · AlarmManager 알림 · GitHub Releases 자체 업데이트. DB나 서버 없이 전부 기기 안에서 돌아갑니다.
