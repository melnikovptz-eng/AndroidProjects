#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

# AERO-006: create/replace the exact Android Downloads fixture consumed by the informer.
DEST="$HOME/storage/downloads/Aeroclub_Informer_flights.json"
TMP="${DEST}.tmp"
mkdir -p "$(dirname "$DEST")"

if ! command -v python3 >/dev/null 2>&1; then
  echo "ОШИБКА: нужен python3 в Termux (pkg install python)."
  exit 1
fi

python3 - "$TMP" <<'PY'
import json, sys
from datetime import date, datetime, timedelta

today = date.today()
flights = []
specs = [
    (1, "ТЕСТ ИНФОРМЕРА — нажать «Я поеду»", "10:30", "Тестовый аэродром, главный вход"),
    (3, "Тестовый клубный вылет", "12:00", "Тестовый аэродром"),
    (7, "Тестовое мероприятие — маршрут и Telegram", "09:15", "Площадка аэроклуба"),
]
for days, title, clock, place in specs:
    d = today + timedelta(days=days)
    flights.append({
        "id": f"termux-test-flight-{d.isoformat()}-{days}",
        "title": title,
        "date": d.strftime("%d.%m.%Y"),
        "dateIso": d.isoformat(),
        "time": clock,
        "place": place,
        "published": True,
        "isDemo": False
    })
root = {
    "format": "aeroclub-flights",
    "version": 1,
    "updatedAt": int(datetime.now().timestamp() * 1000),
    "activeFlightId": flights[0]["id"],
    "flights": flights,
    "testFixture": True,
    "testInstructions": "Откройте информер, нажмите красную дату. Проверьте «Я поеду», «Я не поеду», «Маршрут», «Telegram». Для ответа сначала добавьте тестового участника в панели администратора."
}
with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(root, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY

mv -f "$TMP" "$DEST"
echo "[1/4] Тестовый JSON создан:"
ls -l "$DEST"
echo
echo "[2/4] Проверка формата и количества мероприятий:"
python3 - "$DEST" <<'PY'
import json, sys
p=sys.argv[1]
with open(p, encoding="utf-8") as f: d=json.load(f)
assert d.get("format") == "aeroclub-flights" and d.get("version") == 1
assert len(d.get("flights", [])) == 3
assert len({x["id"] for x in d["flights"]}) == 3
assert d["activeFlightId"] in {x["id"] for x in d["flights"]}
for x in d["flights"]:
    assert x.get("title") and x.get("dateIso") and x.get("time") and x.get("place")
    print(f"- {x['dateIso']} {x['time']} | {x['title']} | id={x['id']}")
print("JSON_OK=1")
print("FLIGHTS_COUNT=3")
PY
echo
echo "[3/4] Файл, который читает приложение:"
echo "$DEST"
echo "Публичный путь Android: Download/Aeroclub_Informer_flights.json"
echo
echo "[4/4] Готово. Полностью закройте и снова откройте информер либо подождите до 5 секунд."
echo "Примечание: для сохранения ответа «Я поеду/Я не поеду» нужен участник в локальном списке."
