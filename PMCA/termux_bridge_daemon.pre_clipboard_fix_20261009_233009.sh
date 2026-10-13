#!/data/data/com.termux/files/usr/bin/bash

set -u

BASE="$HOME/storage/downloads/Taper"
LOCK="$BASE/.daemon.lock"
LOG="$BASE/termux_bridge.log"

mkdir -p "$BASE" || exit 1

# Единая блокировка демона.
if ! mkdir "$LOCK" 2>/dev/null; then
    OLD_PID="$(cat "$LOCK/pid" 2>/dev/null || true)"

    # Даём первому процессу время записать PID.
    if [ -z "$OLD_PID" ]; then
        sleep 1
        OLD_PID="$(cat "$LOCK/pid" 2>/dev/null || true)"
    fi

    if [ -n "$OLD_PID" ] &&
       kill -0 "$OLD_PID" 2>/dev/null; then
        OLD_ARGS="$(ps -p "$OLD_PID" -o args= 2>/dev/null || true)"
        case "$OLD_ARGS" in
            *termux_bridge_daemon.sh*)
                echo "DAEMON_ALREADY_RUNNING PID=$OLD_PID"
                exit 0
                ;;
        esac
    fi

    # Удаляем только устаревшую блокировку.
    rm -f "$LOCK/pid" || exit 1
    rmdir "$LOCK" 2>/dev/null || {
        echo "ERROR: stale lock cannot be safely removed"
        exit 1
    }

    mkdir "$LOCK" 2>/dev/null || {
        echo "ERROR: cannot acquire daemon lock"
        exit 1
    }
fi

printf '%s\n' "$$" > "$LOCK/pid" || {
    rmdir "$LOCK" 2>/dev/null || true
    exit 1
}

cleanup() {
    if [ -f "$LOCK/pid" ] &&
       [ "$(cat "$LOCK/pid" 2>/dev/null || true)" = "$$" ]; then
        rm -f "$LOCK/pid"
        rmdir "$LOCK" 2>/dev/null || true
    fi
}

trap cleanup EXIT
trap 'exit 0' INT TERM

COMMAND="$BASE/command.json"
RESULT="$BASE/result.json"

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
                printf '%s
%s' "[AUTO_COPY][TERMUX][RECEIVED_DATA]" "$DATA" | timeout 3s termux-clipboard-set
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
                        CLIPBOARD_DATA="$(timeout 3s termux-clipboard-get 2>/dev/null || true)"

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
                        printf '%s
%s' "[AUTO_COPY][TERMUX][RESPONSE]" "$RESPONSE" | timeout 3s termux-clipboard-set
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
