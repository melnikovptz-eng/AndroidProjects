#!/data/data/com.termux/files/usr/bin/bash

# === INTERNAL SINGLE DAEMON LOCK ===
INTERNAL_LOCKDIR="$HOME/storage/downloads/Taper/.daemon_internal.lockdir"
if ! mkdir "$INTERNAL_LOCKDIR" 2>/dev/null; then
    OLD_PID=$(cat "$INTERNAL_LOCKDIR/pid" 2>/dev/null || true)
    if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
        echo "DAEMON_ALREADY_RUNNING PID=$OLD_PID"
        exit 0
    fi
    rm -rf "$INTERNAL_LOCKDIR"
    mkdir "$INTERNAL_LOCKDIR" || exit 1
fi
echo "$$" > "$INTERNAL_LOCKDIR/pid"
trap 'rm -rf "$INTERNAL_LOCKDIR"' EXIT INT TERM
# === END INTERNAL SINGLE DAEMON LOCK ===


# ===== SINGLE DAEMON LOCK =====
LOCK_FILE="/data/data/com.termux/files/home/storage/downloads/Taper/.daemon.lock"

if [ -f "$LOCK_FILE" ]; then
    OLD_PID="$(cat "$LOCK_FILE" 2>/dev/null || true)"

    if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
        echo "DAEMON BLOCKED: already running PID=$OLD_PID"
        exit 0
    fi

    rm -f "$LOCK_FILE"
fi

echo "$$" > "$LOCK_FILE"

cleanup_daemon_lock() {
    if [ -f "$LOCK_FILE" ] && [ "$(cat "$LOCK_FILE" 2>/dev/null)" = "$$" ]; then
        rm -f "$LOCK_FILE"
    fi
}

trap cleanup_daemon_lock EXIT INT TERM
# ===== END SINGLE DAEMON LOCK =====


set -u

BASE="$HOME/storage/downloads/Taper"
LOCK="$BASE/.daemon.lock"
LOG="$BASE/termux_bridge.log"

mkdir -p "$BASE"

if ! mkdir "$LOCK" 2>/dev/null; then
    if [ -f "$LOCK/pid" ]; then
        OLD_PID="$(cat "$LOCK/pid" 2>/dev/null || true)"

        if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
            echo "TERMUX BRIDGE ALREADY RUNNING PID=$OLD_PID"
            exit 0
        fi
    fi

    rm -rf "$LOCK"

    if ! mkdir "$LOCK" 2>/dev/null; then
        echo "ERROR: LOCK"
        exit 1
    fi
fi

echo "$$" > "$LOCK/pid"

cleanup() {
    rm -rf "$LOCK"
}

trap cleanup EXIT INT TERM

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG"
}

COMMAND="$BASE/command.json"
RESULT="$BASE/result.json"

log "========================================"
log "DAEMON START REAL BRIDGE V7"
log "BASE=$BASE"
log "========================================"

while true; do

    if [ -f "$COMMAND" ]; then

        RAW="$(cat "$COMMAND" 2>/dev/null || true)"

        if [ -n "$RAW" ]; then

            OPERATION_ID="$(
                printf '%s' "$RAW" |
                python -c '
import json,sys
try:
    print(json.load(sys.stdin).get("operation_id",""))
except:
    print("")
' 2>/dev/null || true
            )"

            TYPE="$(
                printf '%s' "$RAW" |
                python -c '
import json,sys
try:
    print(json.load(sys.stdin).get("type",""))
except:
    print("")
' 2>/dev/null || true
            )"

            CMD="$(
                printf '%s' "$RAW" |
                python -c '
import json,sys
try:
    print(json.load(sys.stdin).get("command",""))
except:
    print("")
' 2>/dev/null || true
            )"

            DATA="$(
                printf '%s' "$RAW" |
                python -c '
import json,sys
try:
    print(json.load(sys.stdin).get("data",""))
except:
    print("")
' 2>/dev/null || true
            )"

            log "RECEIVED operation_id=$OPERATION_ID"
            log "RECEIVED type=$TYPE"
            log "RECEIVED command=$CMD"
            log "RECEIVED data_length=${#DATA}"
            log "RECEIVED data=$DATA"

            # AUTO CLIPBOARD: INCOMING DATA
            if command -v termux-clipboard-set >/dev/null 2>&1; then
                printf '%s\n%s'                     "[AUTO_COPY][TERMUX][RECEIVED_DATA]"                     "$DATA" | termux-clipboard-set
                log "CLIPBOARD: AUTO_COPY RECEIVED_DATA length=${#DATA}"
            else
                log "CLIPBOARD: ERROR termux-clipboard-set NOT FOUND"
            fi

            STATUS="SUCCESS"
            RESPONSE=""

            case "$CMD" in

                TEST)
                    RESPONSE="TEST_OK:$DATA"
                    ;;

                TAPER_DATA)
                    log "TAPER_DATA RECEIVE_START"

                    RESPONSE="TERMUX_DATA_RECEIVED: $DATA"

                    log "TAPER_DATA RESPONSE_LENGTH=${#RESPONSE}"
                    log "TAPER_DATA RECEIVE_COMPLETE"
                    ;;

                CLIPBOARD_GET)
                    log "CLIPBOARD_GET START"

                    if command -v termux-clipboard-get >/dev/null 2>&1; then
                        CLIPBOARD_DATA="$(termux-clipboard-get 2>/dev/null || true)"

                        RESPONSE="CLIPBOARD_GET_OK"
                        if [ -n "$CLIPBOARD_DATA" ]; then
                            RESPONSE="${RESPONSE}
CLIPBOARD_DATA:
${CLIPBOARD_DATA}"
                        else
                            RESPONSE="${RESPONSE}
CLIPBOARD_DATA:
"
                        fi

                        log "CLIPBOARD_GET LENGTH=${#CLIPBOARD_DATA}"
                    else
                        RESPONSE="CLIPBOARD_GET_ERROR: termux-clipboard-get NOT FOUND"
                        log "CLIPBOARD_GET ERROR termux-clipboard-get NOT FOUND"
                    fi

                    log "CLIPBOARD_GET COMPLETE"
                    ;;

                SHELL)
                    log "SHELL EXECUTE_START"
                    log "SHELL COMMAND_LENGTH=${#DATA}"
                    log "SHELL COMMAND_BEGIN"
                    log "$DATA"
                    log "SHELL COMMAND_END"

                    SHELL_TMP="$BASE/.shell_output_${OPERATION_ID}.tmp"

                    if command -v timeout >/dev/null 2>&1; then
                        timeout 60s bash -c "$DATA" >"$SHELL_TMP" 2>&1
                        SHELL_EXIT=$?
                    else
                        bash -c "$DATA" >"$SHELL_TMP" 2>&1
                        SHELL_EXIT=$?
                    fi

                    SHELL_OUTPUT="$(cat "$SHELL_TMP" 2>/dev/null || true)"
                    rm -f "$SHELL_TMP"

                    log "SHELL EXIT_CODE=$SHELL_EXIT"
                    log "SHELL OUTPUT_LENGTH=${#SHELL_OUTPUT}"

                    RESPONSE="SHELL_EXIT_CODE=$SHELL_EXIT"

                    if [ -n "$SHELL_OUTPUT" ]; then
                        RESPONSE="${RESPONSE}
SHELL_OUTPUT:
${SHELL_OUTPUT}"
                    else
                        RESPONSE="${RESPONSE}
SHELL_OUTPUT:
<EMPTY>"
                    fi

                    log "SHELL EXECUTE_COMPLETE"
                    ;;

                *)
                    RESPONSE="COMMAND_OK:$CMD:$DATA"
                    ;;
            esac

            if python - "$OPERATION_ID" "$RESPONSE" > "$RESULT" <<'PY'
import json
import sys
import time

obj = {
    "operation_id": sys.argv[1],
    "timestamp": int(time.time() * 1000),
    "status": "SUCCESS",
    "response": sys.argv[2]
}

print(json.dumps(obj, ensure_ascii=False))
PY
            then

                if [ -s "$RESULT" ]; then

                    log "SENT operation_id=$OPERATION_ID"
                    log "SENT status=SUCCESS"
                    log "SENT response=$RESPONSE"
                    log "RESULT_WRITTEN=YES"

                    # AUTO CLIPBOARD: RESPONSE
                    if command -v termux-clipboard-set >/dev/null 2>&1; then
                        printf '%s\n%s'                             "[AUTO_COPY][TERMUX][RESPONSE]"                             "$RESPONSE" | termux-clipboard-set
                        log "CLIPBOARD: AUTO_COPY RESPONSE length=${#RESPONSE}"
                    else
                        log "CLIPBOARD: ERROR termux-clipboard-set NOT FOUND"
                    fi

                    rm -f "$COMMAND"

                    log "COMMAND COMPLETE"

                else

                    log "ERROR RESULT_EMPTY"

                fi

            else

                log "ERROR RESULT_WRITE_FAILED"

            fi
        fi
    fi

    sleep 0.1
done
