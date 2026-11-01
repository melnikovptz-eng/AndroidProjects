package ru.voiceactivation.demo.voice

import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager
import java.io.Closeable

/** Local speaker enrollment and verification. Audio is mono PCM float [-1,1] at 16 kHz. */
class SpeakerEngine(
    modelPath: String,
    private val threshold: Float = 0.6f,
    numThreads: Int = 2,
    private val profileStore: OwnerProfileStore? = null,
) : Closeable {
    private val extractor = SpeakerEmbeddingExtractor(
        config = SpeakerEmbeddingExtractorConfig(
            model = modelPath,
            numThreads = numThreads,
            debug = false,
            provider = "cpu",
        )
    )
    private val manager = SpeakerEmbeddingManager(extractor.dim())

    init {
        profileStore?.load()?.let { saved ->
            check(manager.add(OWNER_ID, saved)) { "Unable to restore owner voice profile" }
        }
    }

    fun enrollOwner(utterances: List<FloatArray>, sampleRate: Int = SAMPLE_RATE): Boolean {
        require(utterances.size >= MIN_ENROLLMENT_UTTERANCES) {
            "At least $MIN_ENROLLMENT_UTTERANCES enrollment utterances are required"
        }
        val embeddings = utterances.map { extract(it, sampleRate) }.toTypedArray()
        val added = manager.add(OWNER_ID, embeddings)
        if (added) profileStore?.save(embeddings)
        return added
    }

    fun verifyOwner(candidate: FloatArray, sampleRate: Int = SAMPLE_RATE): VerificationResult {
        val embedding = extract(candidate, sampleRate)
        val accepted = manager.verify(OWNER_ID, embedding, threshold)
        // The current native Kotlin API exposes a boolean verification result,
        // not a calibrated similarity score. Never fabricate a confidence value.
        return VerificationResult(accepted = accepted, threshold = threshold)
    }

    fun hasOwnerProfile(): Boolean = manager.contains(OWNER_ID)

    fun clearOwnerProfile() {
        if (manager.contains(OWNER_ID)) manager.remove(OWNER_ID)
        profileStore?.clear()
    }

    private fun extract(samples: FloatArray, sampleRate: Int): FloatArray {
        require(sampleRate == SAMPLE_RATE) { "Speaker model expects 16 kHz audio" }
        require(samples.size >= MIN_SAMPLES) { "Audio sample is too short" }
        val stream: OnlineStream = extractor.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            stream.inputFinished()
            extractor.compute(stream)
        } finally {
            stream.release()
        }
    }

    override fun close() {
        manager.release()
        extractor.release()
    }

    data class VerificationResult(
        val accepted: Boolean,
        val threshold: Float,
    )

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MIN_ENROLLMENT_UTTERANCES = 3
        private const val MIN_SAMPLES = SAMPLE_RATE * 2
        private const val OWNER_ID = "owner"
    }
}
