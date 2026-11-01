package ru.voiceactivation.demo.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.Closeable

/**
 * Streaming sherpa-onnx KWS wrapper.
 * Model files and keyword file must be downloaded/provisioned separately.
 * Language support depends on the selected acoustic/token model.
 */
class KeywordSpotterEngine(
    model: KwsModelFiles,
    keywordsFile: String,
    private val keywordPhrase: String,
    sampleRate: Int = SAMPLE_RATE,
    featureDim: Int = 80,
    keywordsScore: Float = 1.5f,
    keywordsThreshold: Float = 0.25f,
) : Closeable {
    private val spotter = KeywordSpotter(
        config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = featureDim),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = model.encoder,
                    decoder = model.decoder,
                    joiner = model.joiner,
                ),
                tokens = model.tokens,
                modelType = model.modelType,
                numThreads = 2,
                provider = "cpu",
            ),
            keywordsFile = keywordsFile,
            keywordsScore = keywordsScore,
            keywordsThreshold = keywordsThreshold,
        )
    )
    private val stream = spotter.createStream(keywordPhrase)

    @Synchronized
    fun acceptAudio(samples: FloatArray): String? {
        require(samples.isNotEmpty())
        stream.acceptWaveform(samples, SAMPLE_RATE)
        var detected: String? = null
        while (spotter.isReady(stream)) {
            spotter.decode(stream)
            val keyword = spotter.getResult(stream).keyword
            if (keyword.isNotBlank()) {
                detected = keyword
                spotter.reset(stream)
                break
            }
        }
        return detected
    }

    override fun close() {
        stream.release()
        spotter.release()
    }

    data class KwsModelFiles(
        val encoder: String,
        val decoder: String,
        val joiner: String,
        val tokens: String,
        val modelType: String = "zipformer2",
    )

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
