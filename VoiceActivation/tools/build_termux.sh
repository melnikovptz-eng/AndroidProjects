#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

PROJECT_REPO="melnikovptz-eng/AndroidProjects"
PROJECT_BRANCH="voice-activation"
SRC="$HOME/VoiceActivation-build-src"
SDK="$HOME/android-sdk"
DOWNLOADS="$HOME/storage/downloads"
OUT="$DOWNLOADS/VoiceActivation.apk"
LOG="$DOWNLOADS/VoiceActivation-build.log"
STAGE="INIT"

fail() {
  code=$?
  echo
  echo "===== ОШИБКА СБОРКИ: $STAGE ====="
  echo "Код: $code"
  echo "Лог: $LOG"
  tail -n 35 "$LOG" 2>/dev/null || true
  exit "$code"
}
trap fail ERR

mkdir -p "$DOWNLOADS"

echo "===== [1/6] ПРОВЕРКА TERMUX ====="
STAGE="ENVIRONMENT"
command -v gh >/dev/null
command -v git >/dev/null
command -v gradle >/dev/null
command -v java >/dev/null
gh auth status >/dev/null
gradle --version | grep -q "Gradle 8.7"
java -version 2>&1 | grep -q '21\.'
test -f "$SDK/platforms/android-35/android.jar"
test -x "$SDK/build-tools/35.0.0/aapt"
test -x "$SDK/build-tools/35.0.0/apksigner"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$SDK/platform-tools:$SDK/build-tools/35.0.0:$PATH"
echo "JDK 21: OK"
echo "Gradle 8.7: OK"
echo "Android SDK 35 / Build Tools 35: OK"

echo "===== [2/6] ПОЛУЧЕНИЕ ИСХОДНИКОВ ====="
STAGE="SOURCE"
if [[ ! -d "$SRC/.git" ]]; then
  gh repo clone "$PROJECT_REPO" "$SRC" -- --depth 1 --branch "$PROJECT_BRANCH"
else
  git -C "$SRC" diff --quiet
  git -C "$SRC" diff --cached --quiet
  git -C "$SRC" fetch --depth 1 origin "$PROJECT_BRANCH"
  git -C "$SRC" checkout -B "$PROJECT_BRANCH" FETCH_HEAD
fi
test "$(git -C "$SRC" branch --show-current)" = "$PROJECT_BRANCH"

echo "===== [3/6] ПРОВЕРКА ПРОЕКТА ====="
STAGE="PROJECT"
APP="$SRC/VoiceActivation/android-app"
test -f "$APP/settings.gradle.kts"
test -f "$APP/app/src/main/AndroidManifest.xml"
test -f "$APP/app/src/main/java/ru/voiceactivation/demo/VoiceActivationService.kt"
grep -q 'com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v2.0.1' "$APP/app/build.gradle.kts"

echo "===== [4/6] ОДНА СБОРКА DEBUG APK ====="
STAGE="GRADLE_BUILD"
if ! (cd "$APP" && gradle --no-daemon --stacktrace assembleDebug) > "$LOG" 2>&1; then
  tail -n 45 "$LOG"
  exit 1
fi

echo "===== [5/6] ПРОВЕРКА APK ====="
STAGE="APK_VERIFY"
APK="$APP/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
SIZE="$(wc -c < "$APK" | tr -d ' ')"
(( SIZE > 5000000 ))
BADGING="$(aapt dump badging "$APK" | head -n 1)"
echo "$BADGING" | grep -q "name='ru.voiceactivation.demo'"
apksigner verify --verbose "$APK" >/dev/null
HASH="$(sha256sum "$APK" | awk '{print $1}')"

TMP_OUT="$OUT.part"
cp -f "$APK" "$TMP_OUT"
test -s "$TMP_OUT"
mv -f "$TMP_OUT" "$OUT"
test -s "$OUT"
test "$(sha256sum "$OUT" | awk '{print $1}')" = "$HASH"

echo "===== [6/6] ИТОГ ====="
echo "APK: $OUT"
echo "Размер: $SIZE байт"
echo "SHA-256: $HASH"
echo "Package: ru.voiceactivation.demo"
echo "Сборка: DEBUG / подпись проверена"
echo "Лог: $LOG"
if command -v termux-clipboard-set >/dev/null 2>&1; then
  termux-clipboard-set "$OUT" >/dev/null 2>&1 || true
  echo "Путь APK скопирован в буфер обмена."
fi
echo
tail -n 12 "$LOG"
echo "===== ГОТОВО — ОТПРАВЬ РЕЗУЛЬТАТ ====="
