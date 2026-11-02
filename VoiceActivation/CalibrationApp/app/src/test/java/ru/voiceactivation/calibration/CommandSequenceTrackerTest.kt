package ru.voiceactivation.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandSequenceTrackerTest {
    @Test fun acceptsWakeAndActionInSeparateSegmentsWithinPauseWindow() {
        val tracker = CommandSequenceTracker(CommandMatcher())
        val config = CalibrationConfig(maxInterWordPauseMs = 1200, secondWordTimeoutMs = 2500)
        val first = tracker.accept("Яша", 1000, 1500, config)
        assertEquals(CommandSequenceState.WAKE_DETECTED_WAITING_ACTION, first.state)
        val second = tracker.accept("поехали", 1900, 2400, config)
        assertEquals(CommandSequenceState.COMPLETE_TWO_SEGMENTS, second.state)
    }

    @Test fun rejectsSecondWordAfterInterWordPauseLimit() {
        val tracker = CommandSequenceTracker(CommandMatcher())
        val config = CalibrationConfig(maxInterWordPauseMs = 500, secondWordTimeoutMs = 3000)
        tracker.accept("Яша", 1000, 1500, config)
        val second = tracker.accept("поехали", 2200, 2600, config)
        assertEquals(CommandSequenceState.SECOND_WORD_TIMEOUT, second.state)
    }

    @Test fun acceptsBothWordsOnlyInCorrectOrder() {
        val tracker = CommandSequenceTracker(CommandMatcher())
        val result = tracker.accept("Яша поехали", 1000, 1800, CalibrationConfig())
        assertEquals(CommandSequenceState.COMPLETE_SAME_SEGMENT, result.state)
    }
}
