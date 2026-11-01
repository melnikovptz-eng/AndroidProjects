#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

BASE="$HOME/storage/downloads/VoiceActivationModels"
NAME="sherpa-onnx-streaming-t-one-russian-2025-09-08"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/${NAME}.tar.bz2"
ARCHIVE="$BASE/${NAME}.tar.bz2"
DEST="$BASE/${NAME}"

mkdir -p "$BASE"
echo "===== [1/4] ЗАГРУЗКА РУССКОЙ МОДЕЛИ ====="
if [[ ! -s "$ARCHIVE" ]]; then
  curl -fL --retry 3 --connect-timeout 20 "$URL" -o "$ARCHIVE.part"
  test -s "$ARCHIVE.part"
  mv -f "$ARCHIVE.part" "$ARCHIVE"
fi

echo "===== [2/4] РАСПАКОВКА ====="
mkdir -p "$DEST"
tar -xjf "$ARCHIVE" -C "$BASE"

echo "===== [3/4] ПРОВЕРКА ====="
MODEL="$DEST/model.onnx"
TOKENS="$DEST/tokens.txt"
test -s "$MODEL"
test -s "$TOKENS"
MODEL_BYTES="$(wc -c < "$MODEL" | tr -d ' ')"
TOKENS_BYTES="$(wc -c < "$TOKENS" | tr -d ' ')"
if (( MODEL_BYTES < 50000000 || TOKENS_BYTES < 100 )); then
  echo "ОШИБКА: модель или tokens.txt не прошли проверку размера."
  exit 1
fi

echo "Модель: $MODEL"
echo "Размер: $MODEL_BYTES байт"
echo "Токены: $TOKENS_BYTES байт"
sha256sum "$MODEL" "$TOKENS"

echo "===== [4/4] ГОТОВО ====="
echo "В приложении VoiceActivation импортируйте два файла:"
echo "1) $MODEL"
echo "2) $TOKENS"
echo "===== ГОТОВО — ОТПРАВЬ РЕЗУЛЬТАТ ====="
