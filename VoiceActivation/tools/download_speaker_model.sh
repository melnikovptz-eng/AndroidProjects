#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

DEST="$HOME/storage/downloads/VoiceActivationModels"
NAME="3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/$NAME"
FILE="$DEST/$NAME"

mkdir -p "$DEST"
echo "===== [1/3] ЗАГРУЗКА МОДЕЛИ ГОЛОСА ====="
if [[ ! -s "$FILE" ]]; then
  curl -fL --retry 3 --connect-timeout 20 "$URL" -o "$FILE.part"
  test -s "$FILE.part"
  mv -f "$FILE.part" "$FILE"
fi

echo "===== [2/3] ПРОВЕРКА ====="
SIZE="$(wc -c < "$FILE" | tr -d ' ')"
if (( SIZE < 1000000 )); then
  echo "ОШИБКА: файл модели слишком мал."
  exit 1
fi
sha256sum "$FILE"

echo "===== [3/3] ГОТОВО ====="
echo "Импортируйте в VoiceActivation:"
echo "$FILE"
echo "Размер: $SIZE байт"
echo "Модель обучена на англоязычном VoxCeleb; пригодность для русской речи нужно проверить на устройстве."
echo "===== ГОТОВО — ОТПРАВЬ РЕЗУЛЬТАТ ====="
