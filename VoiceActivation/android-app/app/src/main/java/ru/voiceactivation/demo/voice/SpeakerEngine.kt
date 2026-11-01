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

    fun enrollOwner(utterances: List<FloatArray>, sampleRate: Int = SAMPLE_RATE): Boolean {
        require(utterances.size >= MIN_ENROLLMENT_UTTERANCES) {
            "At least $MIN_ENROLLMENT_UTTERANCES enrollment utterances are required"
        }
        val embeddings = utterances.map { extract(it, sampleRate) }.toTypedArray()
        return manager.add(OWNER_ID, embeddings)
    }

    fun verifyOwner(candidate: FloatArray, sampleRate: Int = SAMPLE_RATE): VerificationResult {
        val embedding = extract(candidate, sampleRate)
        val accepted = manager.verify(OWNER_ID, embedding, threshold)
        return VerificationResult(accepted, threshold, threshold)
    }

    fun hasOwnerProfile(): Boolean = manager.contains(OWNER_ID)

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
        val score: Float,
        val threshold: Float,
    )

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MIN_ENROLLMENT_UTTERANCES = 3
        private const val MIN_SAMPLES = SAMPLE_RATE * 2
        private const val OWNER_ID = "owner"
    }
}
