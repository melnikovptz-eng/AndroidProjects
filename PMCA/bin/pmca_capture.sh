# PMCA_CAPTURE_MODULE
# Запись вывода команд и копирование последних результатов.

if [[ -n "${PMCA_CAPTURE_LOADED:-}" ]]; then
    return 0
fi

export PMCA_CAPTURE_LOADED=1
export PMCA_CAPTURE_DIR="$HOME/PMCA/logs/commands"

mkdir -p "$PMCA_CAPTURE_DIR"

PMCA_CAPTURE_INDEX="$PMCA_CAPTURE_DIR/index.txt"
PMCA_CAPTURE_CURRENT=""
PMCA_CAPTURE_ACTIVE=0
PMCA_CAPTURE_TEE_PID=""

: >> "$PMCA_CAPTURE_INDEX"

__pmca_debug_capture() {
    # Не записываем собственные команды управления копированием.
    case "$BASH_COMMAND" in
        pmca_copy1|pmca_copy2|pmca_copy3|pmca_copy_history*)
            return
            ;;
        __pmca_*)
            return
            ;;
        exit|exit\ *)
            return
            ;;
    esac

    # Не начинаем повторную запись внутри текущей команды.
    [[ "$PMCA_CAPTURE_ACTIVE" == 1 ]] && return

    PMCA_CAPTURE_CURRENT="$PMCA_CAPTURE_DIR/current_${BASHPID}.tmp"
    : > "$PMCA_CAPTURE_CURRENT"

    exec 19>&1 18>&2
    exec > >(tee -a "$PMCA_CAPTURE_CURRENT") 2>&1

    PMCA_CAPTURE_TEE_PID=$!
    PMCA_CAPTURE_ACTIVE=1
}

__pmca_finish_command() {
    [[ "$PMCA_CAPTURE_ACTIVE" == 1 ]] || return 0

    # Восстанавливаем обычный вывод терминала.
    exec 1>&19 2>&18
    exec 19>&- 18>&-

    if [[ -n "$PMCA_CAPTURE_TEE_PID" ]]; then
        wait "$PMCA_CAPTURE_TEE_PID" 2>/dev/null || true
    fi

    local seq=0
    if [[ -f "$PMCA_CAPTURE_DIR/sequence.txt" ]]; then
        read -r seq < "$PMCA_CAPTURE_DIR/sequence.txt" || seq=0
    fi

    seq=$((seq + 1))
    printf '%s\n' "$seq" > "$PMCA_CAPTURE_DIR/sequence.txt"

    local final
    printf -v final '%s/cmd_%06d.log' "$PMCA_CAPTURE_DIR" "$seq"

    mv "$PMCA_CAPTURE_CURRENT" "$final" 2>/dev/null || {
        PMCA_CAPTURE_ACTIVE=0
        return 0
    }

    printf '%s\n' "$final" >> "$PMCA_CAPTURE_INDEX"

    # Храним ссылки только на 50 последних команд.
    tail -n 50 "$PMCA_CAPTURE_INDEX" \
        > "$PMCA_CAPTURE_INDEX.tmp" &&
        mv "$PMCA_CAPTURE_INDEX.tmp" "$PMCA_CAPTURE_INDEX"

    # Удаляем старые файлы, которых больше нет в индексе.
    local file
    for file in "$PMCA_CAPTURE_DIR"/cmd_*.log; do
        [[ -f "$file" ]] || continue
        if ! grep -Fqx "$file" "$PMCA_CAPTURE_INDEX"; then
            rm -f "$file"
        fi
    done

    PMCA_CAPTURE_ACTIVE=0
    PMCA_CAPTURE_TEE_PID=""
    PMCA_CAPTURE_CURRENT=""
}

pmca_copy_history() {
    local n="${1:-1}"
    local tmp="$PMCA_CAPTURE_DIR/copy_result.tmp"

    case "$n" in
        1|2|3) ;;
        *)
            echo "Допустимы только 1, 2 или 3."
            return 1
            ;;
    esac

    if [[ ! -s "$PMCA_CAPTURE_INDEX" ]]; then
        echo "Журнал пока пуст. Выполни команду и повтори."
        return 1
    fi

    : > "$tmp"

    local file
    while IFS= read -r file; do
        [[ -f "$file" ]] || continue
        cat "$file" >> "$tmp"
    done < <(tail -n "$n" "$PMCA_CAPTURE_INDEX")

    if [[ ! -s "$tmp" ]]; then
        echo "Нет сохранённого вывода."
        return 1
    fi

    if ! command -v termux-clipboard-set >/dev/null 2>&1; then
        echo "Не найдена команда termux-clipboard-set."
        return 1
    fi

    if timeout 3s termux-clipboard-set < "$tmp" >/dev/null 2>&1; then
        echo "PMCA: скопирован вывод последних команд: $n"
    else
        echo "Не удалось записать результат в буфер обмена."
        return 1
    fi
}

pmca_copy1() { pmca_copy_history 1; }
pmca_copy2() { pmca_copy_history 2; }
pmca_copy3() { pmca_copy_history 3; }

# Устанавливаем ловушку только после определения функций.
trap '__pmca_debug_capture' DEBUG

if [[ -n "${PROMPT_COMMAND:-}" ]]; then
    PROMPT_COMMAND="${PROMPT_COMMAND}; __pmca_finish_command"
else
    PROMPT_COMMAND="__pmca_finish_command"
fi

# PMCA_CAPTURE_MODULE END
