#!/data/data/com.termux/files/usr/bin/bash

OUT="$HOME/PMCA/PMCA_FULL_AUDIT.txt"
SRC="$HOME/TaperV6/termux_bridge_daemon.sh"
BAK="$HOME/PMCA/backups/termux_bridge_daemon.sh.backup"
BASE="$HOME/storage/downloads/Taper"

mkdir -p "$HOME/PMCA"
: > "$OUT"

exec > >(tee -a "$OUT") 2>&1

echo "===== PMCA FULL AUDIT ====="
date
echo "MODE=READ_ONLY"
echo "Демон не запускается. Файлы не удаляются и не изменяются, кроме отчёта."

echo
echo "===== 1. ENVIRONMENT ====="
printf 'HOME=%s\nPREFIX=%s\n' "$HOME" "$PREFIX"
for c in bash python timeout sha256sum termux-clipboard-set termux-clipboard-get; do
    printf '%-25s' "$c"
    command -v "$c" || echo "NOT_FOUND"
done

echo
echo "===== 2. BRIDGE SOURCE AND BACKUP ====="
for f in "$SRC" "$BAK" "$HOME/termux_bridge_daemon.sh"; do
    if [ -f "$f" ]; then
        ls -l "$f"
        sha256sum "$f"
    else
        echo "ABSENT: $f"
    fi
done

echo
echo "===== 3. BACKUP COMPARISON ====="
if [ -f "$SRC" ] && [ -f "$BAK" ]; then
    if cmp -s "$SRC" "$BAK"; then
        echo "SOURCE_EQUALS_BACKUP=YES"
    else
        echo "SOURCE_EQUALS_BACKUP=NO"
    fi
else
    echo "BACKUP_COMPARISON=UNAVAILABLE"
fi

echo
echo "===== 4. SHELL SYNTAX ====="
for f in "$SRC" "$BAK" "$HOME/termux_bridge_daemon.sh"; do
    if [ -f "$f" ]; then
        if bash -n "$f"; then
            echo "SYNTAX_OK: $f"
        else
            echo "SYNTAX_ERROR: $f"
        fi
    fi
done

echo
echo "===== 5. ACTIVE BRIDGE PROCESSES ====="
ps -A -o PID,PPID,ARGS 2>/dev/null |
    grep -E '[t]ermux_bridge_daemon\.sh' ||
    echo "NO_ACTIVE_BRIDGE"

echo
echo "===== 6. OTHER POSSIBLE DAEMONS ====="
ps -A -o PID,PPID,ARGS 2>/dev/null |
    grep -Ei '[t]aper.*daemon|[b]ridge.*daemon' ||
    echo "NO_OTHER_MATCHING_DAEMONS"

echo
echo "===== 7. LOCK FILES ====="
for f in \
    "$BASE/.daemon_internal.lockdir/pid" \
    "$BASE/.daemon.lock" \
    "$BASE/.daemon.lock/pid"
do
    if [ -f "$f" ]; then
        printf 'FILE: %s\nCONTENT: ' "$f"
        cat "$f"
        echo
    elif [ -d "$f" ]; then
        echo "DIRECTORY: $f"
    else
        echo "ABSENT: $f"
    fi
done

echo
echo "===== 8. LOCK PID VALIDATION ====="
for p in 18619; do
    if kill -0 "$p" 2>/dev/null; then
        echo "PID_$p=EXISTS"
        ps -p "$p" -o PID,PPID,ARGS 2>/dev/null || true
    else
        echo "PID_$p=NOT_RUNNING"
    fi
done

echo
echo "===== 9. LOCKS AND TRAPS IN SOURCE ====="
if [ -f "$SRC" ]; then
    grep -nE \
        'LOCKDIR|LOCK_FILE|LOCK_DIR|INTERNAL_LOCKDIR|daemon\.lock|trap |mkdir .*lock|rmdir|rm -rf.*LOCK|kill -0' \
        "$SRC" || echo "NO_LOCK_PATTERN_FOUND"
fi

echo
echo "===== 10. LOOP AND CLIPBOARD CALLS ====="
if [ -f "$SRC" ]; then
    grep -nE \
        'while true|termux-clipboard-set|termux-clipboard-get|command\.json|result\.json|sleep ' \
        "$SRC" || echo "NO_LOOP_OR_CLIPBOARD_PATTERN_FOUND"
fi

echo
echo "===== 11. AUTOSTART LOCATIONS ====="
for f in \
    "$HOME/.bashrc" \
    "$HOME/.profile" \
    "$PREFIX/etc/bash.bashrc"
do
    if [ -f "$f" ]; then
        echo "--- $f"
        grep -nEi \
            'termux_bridge_daemon|TaperV6/termux_bridge|DAEMON_START REAL BRIDGE' \
            "$f" || echo "NO_MATCH"
    else
        echo "ABSENT: $f"
    fi
done

for d in "$HOME/.termux/boot" "$HOME/.shortcuts"; do
    if [ -d "$d" ]; then
        echo "--- SEARCH: $d"
        grep -RniE \
            'termux_bridge_daemon|TaperV6/termux_bridge|DAEMON_START REAL BRIDGE' \
            "$d" 2>/dev/null | head -60 || echo "NO_MATCH"
    else
        echo "DIRECTORY_ABSENT: $d"
    fi
done

echo
echo "===== 12. BRIDGE FILES ====="
for f in "$BASE/command.json" "$BASE/result.json"; do
    if [ -f "$f" ]; then
        ls -l "$f"
        echo "--- FIRST 40 LINES: $f"
        head -40 "$f"
    else
        echo "ABSENT: $f"
    fi
done

echo
echo "===== 13. RECENT BRIDGE LOGS ====="
for f in \
    "$HOME/TaperV6/bridge_status.log" \
    "$HOME/TaperV6/single_daemon_status.log" \
    "$HOME/TaperV6/final_daemon_check.log"
do
    if [ -f "$f" ]; then
        echo "--- $f"
        tail -30 "$f"
    else
        echo "ABSENT: $f"
    fi
done

echo
echo "===== 14. PMCA REPORT FILES ====="
find "$HOME/PMCA" -maxdepth 1 -type f \
    \( -name '*AUDIT*.txt' -o -name '*CHECK*.txt' -o -name '*DIAGNOSTIC*.txt' \) \
    -printf '%f | %s bytes\n' 2>/dev/null | sort

echo
echo "===== AUDIT COMPLETE ====="
echo "REPORT=$OUT"
echo "===== END OF REPORT ====="

# Вывод отчёта уже записан в файл; копирование ограничено по времени.
if command -v termux-clipboard-set >/dev/null 2>&1; then
    timeout 3s termux-clipboard-set < "$OUT" 2>/dev/null ||
        echo "CLIPBOARD_COPY=FAILED_OR_TIMEOUT"
else
    echo "CLIPBOARD_COPY=UNAVAILABLE"
fi
