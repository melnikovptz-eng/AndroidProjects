# Voice engine integration

- `KeywordSpotterEngine` wraps the upstream Kotlin `KeywordSpotter` API and accepts model/token/keyword file paths.
- `SpeakerEngine` uses upstream `SpeakerEmbeddingExtractor` and `SpeakerEmbeddingManager` APIs.
- `OwnerProfileStore` encrypts the saved speaker embeddings with AES-GCM and an AndroidKeyStore key.
- `TwoStageActivationGate` prevents a target-launch decision from being made before the speaker-verification stage.

All audio passed into the engines must be mono float PCM in [-1, 1] at 16 kHz. The microphone capture/resampling layer is not connected yet.

The KWS wrapper is language/model agnostic at the API level; it does not make a model multilingual. The currently documented official pretrained KWS models include Chinese and English variants. A Russian phrase must not be enabled until a Russian-capable model or a separately validated Russian streaming-ASR phrase detector is selected and tested.
