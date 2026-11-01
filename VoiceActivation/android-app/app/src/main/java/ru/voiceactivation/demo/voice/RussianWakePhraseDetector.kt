package ru.voiceactivation.demo.voice

import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineToneCtcModelConfig
import java.io.Closeable
import java.util.Locale

/**
 * Russian wake phrase candidate based on sherpa-onnx streaming T-one CTC ASR.
 *
 * T-one expects 8 kHz input. The app's shared microphone stream is 16 kHz;
 * this class performs a simple 2:1 low-pass/decimation step before inference.
 * It confirms a wake phrase only after the recognizer reaches an endpoint.
 */
class RussianWakePhraseDetector(
    modelPath: String,
    tokensPath: String,
    wakePhrase: String,
    numThreads: Int = 2,
) : Closeable {
    private val expectedPhrase = normalize(wakePhrase).also {
        require(it.isNotBlank()) { "Wake phrase must not be empty" }
    }

    private val recognizer = OnlineRecognizer(
        config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = MODEL_SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                toneCtc = OnlineToneCtcModelConfig(model = modelPath),
                tokens = tokensPath,
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 0.8f, 0.0f),
                rule2 = EndpointRule(true, 0.5f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 10.0f),
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )
    )
    private val stream = recognizer.createStream()

    /** Supply mono 16 kHz PCM float samples normalized to [-1, 1]. */
    @Synchronized
    fun acceptAudio16k(samples: FloatArray): Detection? {
        require(samples.isNotEmpty())
        val samples8k = downsample16kTo8k(samples)
        stream.acceptWaveform(samples8k, MODEL_SAMPLE_RATE)

        var detection: Detection? = null
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream)
            if (recognizer.isEndpoint(stream)) {
                val text = recognizer.getResult(stream).text
                val normalized = normalize(text)
                if (normalized.contains(expectedPhrase)) {
                    detection = Detection(
                        phrase = expectedPhrase,
                        transcript = normalized,
                    )
                }
                recognizer.reset(stream)
                break
            }
        }
        return detection
    }

    override fun close() {
        stream.release()
        recognizer.release()
    }

    private fun downsample16kTo8k(input: FloatArray): FloatArray {
        val output = FloatArray(input.size / 2)
        for (i in output.indices) {
            // Two-sample box filter before decimation.
            output[i] = (input[i * 2] + input[i * 2 + 1]) * 0.5f
        }
        return output
    }

    data class Detection(
        val phrase: String,
        val transcript: String,
    )

    companion object {
        const val INPUT_SAMPLE_RATE = 16_000
        const val MODEL_SAMPLE_RATE = 8_000

        private fun normalize(text: String): String = text
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
