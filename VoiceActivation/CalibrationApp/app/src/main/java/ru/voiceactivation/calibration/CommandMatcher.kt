package ru.voiceactivation.calibration

import java.util.Locale
import kotlin.math.max

data class CommandMatch(
    val wakeFound: Boolean,
    val actionFound: Boolean,
    val complete: Boolean,
    val wakeToken: String?,
    val actionToken: String?,
    val wakeSimilarity: Float,
    val actionSimilarity: Float,
    val normalizedText: String
)

class CommandMatcher(
    private val wakeVariants: Set<String> = setOf("яша", "яжа", "яаша", "ясша"),
    private val wakeThreshold: Float = 0.72f,
    private val actionThreshold: Float = 0.78f
) {
    fun normalize(text: String): String = text.lowercase(Locale("ru"))
        .replace('ё', 'е')
        .replace(Regex("[^а-яa-z0-9\\s]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun match(text: String, action: String? = "поехали", wakeLimit: Float = wakeThreshold, actionLimit: Float = actionThreshold): CommandMatch {
        val normalized = normalize(text)
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        var bestWake: Pair<String, Float>? = null
        var bestAction: Pair<String, Float>? = null

        for (token in tokens) {
            val wake = wakeVariants.map { it to similarity(token, it) }.maxByOrNull { it.second }
            if (wake != null && (bestWake == null || wake.second > bestWake!!.second)) bestWake = token to wake.second
            if (action != null) {
                val candidate = similarity(token, normalize(action))
                if (bestAction == null || candidate > bestAction!!.second) bestAction = token to candidate
            }
        }
        val wakeScore = bestWake?.second ?: 0f
        val actionScore = bestAction?.second ?: 0f
        val wakeOk = bestWake != null && wakeScore >= wakeLimit
        val actionOk = action == null || (bestAction != null && actionScore >= actionLimit)
        return CommandMatch(
            wakeFound = wakeOk,
            actionFound = actionOk && action != null,
            complete = wakeOk && actionOk,
            wakeToken = if (wakeOk) bestWake?.first else null,
            actionToken = if (actionOk && action != null) bestAction?.first else null,
            wakeSimilarity = wakeScore,
            actionSimilarity = actionScore,
            normalizedText = normalized
        )
    }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val tmp = previous.copyOf()
            for (j in current.indices) previous[j] = current[j]
            current = tmp
        }
        val distance = previous[b.length]
        return (1f - distance.toFloat() / max(a.length, b.length)).coerceIn(0f, 1f)
    }
}
