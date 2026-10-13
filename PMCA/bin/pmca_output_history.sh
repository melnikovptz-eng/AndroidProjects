# PMCA output history module.
# Safe standalone module: no traps, daemons, or clipboard calls.

PMCA_OUTPUT_HISTORY_DIR="$HOME/PMCA/logs/output_history"
mkdir -p "$PMCA_OUTPUT_HISTORY_DIR"

pmca_save_output() {
    local label="${1:-manual}"
    local content="${2-}"
    local stamp file seq=0

    stamp="$(date +%Y%m%d_%H%M%S)_$$_${RANDOM}"
    file="$PMCA_OUTPUT_HISTORY_DIR/${stamp}.txt"
    while [ -e "$file" ]; do
        seq=$((seq + 1))
        file="$PMCA_OUTPUT_HISTORY_DIR/${stamp}_${seq}.txt"
    done

    printf '%s\n' "$content" > "$file"
    printf '%s\n' "$file" >> "$PMCA_OUTPUT_HISTORY_DIR/index.txt"

    # Keep only the newest three indexed results.
    tail -n 3 "$PMCA_OUTPUT_HISTORY_DIR/index.txt" \
      > "$PMCA_OUTPUT_HISTORY_DIR/index.tmp"
    mv "$PMCA_OUTPUT_HISTORY_DIR/index.tmp" \
      "$PMCA_OUTPUT_HISTORY_DIR/index.txt"

    printf 'PMCA: результат сохранён (%s): %s\n' "$label" "$file"
}

pmca_show_output_history() {
    local count="${1:-1}"
    local index="$PMCA_OUTPUT_HISTORY_DIR/index.txt"

    case "$count" in
      1|2|3) ;;
      *) printf 'Используй: pmca_show_output_history 1|2|3\n'; return 2 ;;
    esac

    if [ ! -s "$index" ]; then
        printf 'История выводов пока пуста.\n'
        return 1
    fi

    printf '===== PMCA: ПОСЛЕДНИЕ %s РЕЗУЛЬТАТ(А) =====\n' "$count"
    tail -n "$count" "$index" | while IFS= read -r file; do
        if [ -f "$file" ]; then
            printf '\n--- %s ---\n' "$(basename "$file")"
            cat "$file"
        fi
    done
    printf '\n===== КОНЕЦ ИСТОРИИ =====\n'
}

pmca_output1() { pmca_show_output_history 1; }
pmca_output2() { pmca_show_output_history 2; }
pmca_output3() { pmca_show_output_history 3; }
