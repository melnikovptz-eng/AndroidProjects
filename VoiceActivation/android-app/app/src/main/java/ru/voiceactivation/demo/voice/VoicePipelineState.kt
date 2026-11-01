package ru.voiceactivation.demo.voice

/** The launcher must only react to OwnerVerified. This gate does not fake recognition. */
sealed interface VoicePipelineState {
    data object WaitingForWakeWord : VoicePipelineState
    data object VerifyingOwner : VoicePipelineState
    data class OwnerVerified(val confidence: Float) : VoicePipelineState
    data class Rejected(val confidence: Float) : VoicePipelineState
    data class Failed(val reason: String) : VoicePipelineState
}

class TwoStageActivationGate {
    fun onWakeWordDetected(): VoicePipelineState = VoicePipelineState.VerifyingOwner

    fun onSpeakerResult(verified: Boolean, confidence: Float): VoicePipelineState =
        if (verified) VoicePipelineState.OwnerVerified(confidence)
        else VoicePipelineState.Rejected(confidence)

    fun reset(): VoicePipelineState = VoicePipelineState.WaitingForWakeWord
}
