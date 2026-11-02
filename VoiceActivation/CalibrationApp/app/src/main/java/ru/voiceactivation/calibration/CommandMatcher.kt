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
    private val wakeThreshold: Float = 0.80f,
    private val actionThreshold: Float = 0.78f
) {
    fun normalize(text: String): String = text.lowercase(Locale("ru"))
        .replace('ё', 'е')
        .replace(Regex("[^а-яa-z0-9\\s]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun match(
        text: String,
        action: String? = "поехали",
        wakeLimit: Float = wakeThreshold,
        actionLimit: Float = actionThreshold
    ): CommandMatch {
        val normalized = normalize(text)
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        var bestWakeToken: String? = null
        var bestWakeIndex = -1
        var wakeScore = 0f
        var bestActionToken: String? = null
        var bestActionIndex = -1
        var actionScore = 0f

        tokens.forEachIndexed { index, token ->
            val wakeCandidate = wakeVariants.maxOfOrNull { similarity(token, it) } ?: 0f
            if (wakeCandidate > wakeScore) {
                wakeScore = wakeCandidate
                bestWakeToken = token
                bestWakeIndex = index
            }
            if (action != null) {
                val candidate = similarity(token, normalize(action))
                if (candidate > actionScore) {
                    actionScore = candidate
                    bestActionToken = token
                    bestActionIndex = index
                }
            }
        }

        val wakeOk = bestWakeToken != null && wakeScore >= wakeLimit
        val actionOk = action == null || (bestActionToken != null && actionScore >= actionLimit)
        val correctOrder = action == null || bestWakeIndex < bestActionIndex
        return CommandMatch(
            wakeFound = wakeOk,
            actionFound = action != null && actionOk,
            complete = wakeOk && actionOk && correctOrder,
            wakeToken = if (wakeOk) bestWakeToken else null,
            actionToken = if (action != null && actionOk) bestActionToken else null,
            wakeSimilarity = wakeScore,
            actionSimilarity = actionScore,
            normalizedText = normalized
        )
    }

    fun matchAction(text: String, action: String = "поехали", threshold: Float = actionThreshold): Boolean {
        val target = normalize(action)
        return normalize(text).split(' ').any { it.isNotBlank() && similarity(it, target) >= threshold }
    }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        var previous = IntArray(b.length + 1) { it }
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
            val temp = previous
            previous = current
            current = temp
        }
        val distance = previous[b.length]
        return (1f - distance.toFloat() / max(a.length, b.length)).coerceIn(0f, 1f)
    }
}
