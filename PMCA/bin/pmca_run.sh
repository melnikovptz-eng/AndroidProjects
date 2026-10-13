# PMCA command runner.
# Runs a command, displays its output, and stores it in output history.
# No background daemons, DEBUG traps, or clipboard calls.

pmca_run() {
    if [ "$#" -eq 0 ]; then
        printf 'Использование: pmca_run команда [аргументы...]\n'
        return 2
    fi

    local dir="$HOME/PMCA/logs/output_history"
    local stamp file rc
    mkdir -p "$dir"

    stamp="$(date +%Y%m%d_%H%M%S)_$$_${RANDOM}"
    file="$dir/${stamp}.txt"

    printf '===== PMCA RUN:'
    printf ' %q' "$@"
    printf ' =====\n'

    # Capture stdout and stderr while displaying them immediately.
    "$@" 2>&1 | tee "$file"
    rc=${PIPESTATUS[0]}

    # Append this result to the rolling index.
    printf '%s\n' "$file" >> "$dir/index.txt"
    tail -n 3 "$dir/index.txt" > "$dir/index.tmp" &&
        mv "$dir/index.tmp" "$dir/index.txt"

    printf '\n===== PMCA RUN EXIT CODE: %s =====\n' "$rc"
    return "$rc"
}
