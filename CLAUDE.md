# 업풀업 작업 규칙

## APK를 만들면 반드시 알린다

**빌드가 새 APK를 만들면 그 턴 안에서 사용자에게 파일을 전달한다.** 예외 없다.

- "CI 끝나면 올리겠습니다"라고 말한 뒤 턴을 끝내지 않는다. 그건 안 알린 것이다.
- 기다려야 하면 백그라운드로 기다린 뒤, **같은 대화에서** 릴리스를 확인하고 APK를 첨부한다.
- 앱 코드가 안 바뀐 빌드(문서·설정만 고친 커밋)라도 새 버전이 나왔다는 사실과 **설치할 필요가 없다는 것**을 한 줄로 말한다.
- 전달 전에 받은 파일의 SHA-256이 릴리스 digest와 같은지 확인하고, 맞다고 밝힌다.
- 무엇이 들어갔는지, 그래서 화면에서 뭘 확인하면 되는지 함께 적는다.

사용자가 "?"라고 물어야 APK가 나오는 상황을 만들지 않는다.

## 이 저장소

- 개발·푸시 브랜치: `claude/pullup-tracker-google-tasks-ia8ozq`. 다른 브랜치로 푸시하지 않는다.
- **공개 저장소다.** 서명 키(`.jks`), base64, 비밀번호는 커밋도 Actions 아티팩트 업로드도 하지 않는다.
- 버전은 GitHub Actions가 `1.0.0.<run_number>`로 매기고, 기본 브랜치 push면 릴리스까지 발행한다.
- PR은 사용자가 명시적으로 요청할 때만 만든다.

## 검증

로컬에 Android SDK가 없다(dl.google.com 차단). 푸시 전에 두 가지를 돌린다.

1. 스크래치패드의 standalone `kotlinc`로 `app/src/main/java` 전체 문법 검사.
   Compose/Android 클래스가 없어서 `unresolved reference`가 잔뜩 나오는데 그건 cascade다.
   실제로 볼 것은 `expecting` / `unexpected` / `syntax error` / `redeclaration`,
   그리고 이번에 추가한 심볼 이름이 오타 없이 잡히는지다.
   판단이 서지 않으면 **내가 안 건드린 파일에도 같은 오류가 나는지** 확인한다. 나오면 cascade다.
2. 순수 JVM 데이터 계층(`data/`)은 Maven Central jar로 실제 컴파일해서 JUnit을 돌린다.
3. **코드를 지웠으면 호출처가 남아 있는지 본다.** `unresolved reference`가 cascade에
   묻혀서 로컬 검사로는 안 잡힌다. 아래를 돌려서 "프로젝트에 정의도 import도 없는
   호출"이 비어 있는지 확인한다.

```bash
for f in $(find app/src/main/java -name '*.kt'); do
  for name in $(grep -oE '(^|[^A-Za-z0-9_.])[A-Z][A-Za-z0-9]*\(' "$f" | grep -oE '[A-Z][A-Za-z0-9]*' | sort -u); do
    grep -rqE "(fun|class|object|interface) $name[ (<]" app/src/main/java/ && continue
    grep -qE "^import .*\.$name$" "$f" && continue
    echo "$name <- $(basename $f)"
  done
done | sort -u
```

kotlin stdlib(`List`, `String`, `IllegalStateException`, `OptIn`…)과 확장 멤버만
남아야 한다. 그 밖의 이름이 나오면 진짜로 빠진 것이다.

## Compose 상태를 안 읽어서 화면이 안 바뀌는 버그

이 저장소에서 같은 버그를 세 번 냈다(+/- 가 안 먹음, 루틴 추가해도 목록 그대로).

원인은 늘 같다. `viewModel.something()` 안에서 `stateFlow.value`를 읽으면
**Compose는 그 화면이 상태를 읽었다고 기록하지 않는다.** 값은 바뀌는데 화면을
다시 그리지 않아서 "버튼이 안 먹는다"로 보인다. 컴파일도 테스트도 다 통과한다.

그래서 데이터에 기대는 조회 함수는 **AppData를 인자로 받는다.** 호출하는 쪽이
`collectAsState()`로 받은 값을 넘길 수밖에 없어서 실수가 안 난다.
새 조회 함수를 만들 때도 이 규칙을 지킬 것 — 안에서 `data.value`를 읽지 않는다.

**함정:** 파라미터 이름 `data`가 ViewModel의 `data` StateFlow를 가린다. 그 함수 안에
`data.value`가 남아 있으면 `AppData.value`를 찾다가 컴파일이 깨진다. 로컬 kotlinc는
이걸 `unresolved reference` 더미에 묻어 버리니 아래로 따로 본다.

```bash
grep -rn "data\.value" app/src/main/java/com/pullup/tracker/ui/screens/   # 화면에는 하나도 없어야 한다
```

화면을 고쳤으면 확인:

```bash
for f in TodayScreen HistoryScreen PlanScreen SettingsScreen CoachScreen; do
  grep -q "viewModel.data.collectAsState" app/src/main/java/com/pullup/tracker/ui/screens/$f.kt \
    && grep -qE "viewModel\.[a-zA-Z]+\(data" app/src/main/java/com/pullup/tracker/ui/screens/$f.kt \
    && echo "$f OK" || echo "$f 확인 필요"
done
```

## 사용자

한국어로 답한다. 모바일에서 짧게 쓴다. 모호하면 넘겨짚지 말고 물어본다.
