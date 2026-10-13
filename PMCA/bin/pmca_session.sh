#!/data/data/com.termux/files/usr/bin/bash

set -u

BASE="$HOME/PMCA"
LOGDIR="$BASE/logs"
mkdir -p "$LOGDIR"

SESSION_LOG="$LOGDIR/session.typescript"

echo "===== PMCA SESSION RECORDER ====="
echo "Запись терминальной сессии запущена."
echo "Файл записи: $SESSION_LOG"
echo
echo "Для завершения записи введи exit."
echo

exec script -q -f "$SESSION_LOG"
