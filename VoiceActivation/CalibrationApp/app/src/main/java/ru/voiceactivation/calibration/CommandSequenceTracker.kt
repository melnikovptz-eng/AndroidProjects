package ru.voiceactivation.calibration

enum class CommandSequenceState {
    COMPLETE_SAME_SEGMENT,
    WAKE_DETECTED_WAITING_ACTION,
    COMPLETE_TWO_SEGMENTS,
    ACTION_WITHOUT_WAKE,
    INVALID_ORDER,
    SECOND_WORD_TIMEOUT,
    NO_MATCH
}

data class CommandSequenceResult(
    val state: CommandSequenceState,
    val message: String,
    val wakeSimilarity: Float = 0f,
    val actionSimilarity: Float = 0f
)

class CommandSequenceTracker(private val matcher: CommandMatcher) {
    private var pendingWakeEndMs: Long? = null

    @Synchronized
    fun accept(
        text: String,
        segmentStartMs: Long,
        segmentEndMs: Long,
        config: CalibrationConfig
    ): CommandSequenceResult {
        val c = config.normalized()
        val full = matcher.match(text, "поехали", c.wakeSimilarityThreshold, c.actionSimilarityThreshold)
        if (full.complete) {
            pendingWakeEndMs = null
            return CommandSequenceResult(
                CommandSequenceState.COMPLETE_SAME_SEGMENT,
                "ЯША + ПОЕХАЛИ — ОК",
                full.wakeSimilarity,
                full.actionSimilarity
            )
        }
        if (full.wakeFound && full.actionFound) {
            pendingWakeEndMs = null
            return CommandSequenceResult(
                CommandSequenceState.INVALID_ORDER,
                "Слова найдены, но порядок неверный",
                full.wakeSimilarity,
                full.actionSimilarity
            )
        }
        if (full.wakeFound) {
            pendingWakeEndMs = segmentEndMs
            return CommandSequenceResult(
                CommandSequenceState.WAKE_DETECTED_WAITING_ACTION,
                "ЯША — ОК. ОЖИДАЮ «ПОЕХАЛИ»",
                full.wakeSimilarity,
                full.actionSimilarity
            )
        }

        val pending = pendingWakeEndMs
        if (pending != null) {
            val gap = (segmentStartMs - pending).coerceAtLeast(0L)
            val elapsed = (segmentEndMs - pending).coerceAtLeast(0L)
            if (elapsed > c.secondWordTimeoutMs || gap > c.maxInterWordPauseMs) {
                pendingWakeEndMs = null
                return CommandSequenceResult(
                    CommandSequenceState.SECOND_WORD_TIMEOUT,
                    "ВРЕМЯ ОЖИДАНИЯ ВТОРОГО СЛОВА ИСТЕКЛО"
                )
            }
            val actionFound = matcher.matchAction(text, "поехали", c.actionSimilarityThreshold)
            if (actionFound) {
                pendingWakeEndMs = null
                return CommandSequenceResult(
                    CommandSequenceState.COMPLETE_TWO_SEGMENTS,
                    "ЯША → ПОЕХАЛИ — ОК"
                )
            }
            return CommandSequenceResult(
                CommandSequenceState.WAKE_DETECTED_WAITING_ACTION,
                "ОЖИДАЮ «ПОЕХАЛИ» · пауза ${gap} ms"
            )
        }

        if (matcher.matchAction(text, "поехали", c.actionSimilarityThreshold)) {
            return CommandSequenceResult(CommandSequenceState.ACTION_WITHOUT_WAKE, "ДЕЙСТВИЕ ЕСТЬ, ОБРАЩЕНИЕ «ЯША» НЕ НАЙДЕНО")
        }
        return CommandSequenceResult(CommandSequenceState.NO_MATCH, "КОМАНДА НЕ ОБНАРУЖЕНА")
    }

    @Synchronized
    fun expire(nowMs: Long, config: CalibrationConfig): Boolean {
        val pending = pendingWakeEndMs ?: return false
        val c = config.normalized()
        if (nowMs - pending > minOf(c.secondWordTimeoutMs, c.maxInterWordPauseMs)) {
            pendingWakeEndMs = null
            return true
        }
        return false
    }

    @Synchronized
    fun reset() { pendingWakeEndMs = null }
}
