package ru.voiceactivation.calibration

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class VoskResult(val text: String, val isFinal: Boolean, val rawJson: String)

class VoskEngine : Closeable {
    private var model: Model? = null
    private var recognizer: Recognizer? = null

    @Synchronized
    fun load(modelDirectory: String, sampleRate: Int = 16_000) {
        close()
        val loaded = Model(modelDirectory)
        try {
            recognizer = Recognizer(loaded, sampleRate.toFloat())
            model = loaded
        } catch (t: Throwable) {
            loaded.close()
            throw t
        }
    }

    @Synchronized
    fun accept(samples: ShortArray, count: Int = samples.size): VoskResult? {
        val rec = recognizer ?: return null
        val safeCount = count.coerceIn(0, samples.size)
        if (safeCount == 0) return null
        val bytes = ByteBuffer.allocate(safeCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until safeCount) bytes.putShort(samples[i])
        val finalChunk = rec.acceptWaveForm(bytes.array(), bytes.position())
        val json = if (finalChunk) rec.result else rec.partialResult
        val text = try {
            val obj = JSONObject(json)
            if (finalChunk) obj.optString("text", "") else obj.optString("partial", "")
        } catch (_: Throwable) { "" }
        return VoskResult(text, finalChunk, json)
    }

    @Synchronized
    fun finish(): VoskResult? {
        val rec = recognizer ?: return null
        val json = rec.finalResult
        val text = try { JSONObject(json).optString("text", "") } catch (_: Throwable) { "" }
        return VoskResult(text, true, json)
    }

    @Synchronized
    fun recognizeSegment(samples: ShortArray): VoskResult? {
        val rec = recognizer ?: return null
        var latestText = ""
        var finalText = ""
        var offset = 0
        val chunkSize = 3_200
        while (offset < samples.size) {
            val count = minOf(chunkSize, samples.size - offset)
            val chunk = samples.copyOfRange(offset, offset + count)
            val result = accept(chunk, count)
            if (result != null) {
                latestText = result.text
                if (result.isFinal && result.text.isNotBlank()) finalText = result.text
            }
            offset += count
        }
        val tail = finish()
        if (tail != null && tail.text.isNotBlank()) finalText = tail.text
        try { rec.reset() } catch (_: Throwable) {}
        val chosen = if (finalText.isNotBlank()) finalText else latestText
        return VoskResult(chosen, true, """{"text":"$chosen"}""")
    }

    fun isLoaded() = recognizer != null

    @Synchronized
    override fun close() {
        try { recognizer?.close() } catch (_: Throwable) {}
        recognizer = null
        try { model?.close() } catch (_: Throwable) {}
        model = null
    }
}
