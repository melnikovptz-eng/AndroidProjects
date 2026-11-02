package ru.voiceactivation.calibration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandMatcherTest {
    private val matcher = CommandMatcher()

    @Test fun acceptsRussianWakeVariantsAndRequiredAction() {
        listOf("Яша поехали", "Яжа поехали", "Яаша поехали", "Ясша поехали").forEach {
            assertTrue(it, matcher.match(it, "поехали").complete)
        }
    }

    @Test fun wakeOnlyDoesNotCompleteDoubleCommand() {
        val result = matcher.match("Яша", "поехали")
        assertTrue(result.wakeFound)
        assertFalse(result.complete)
    }

    @Test fun unrelatedWordDoesNotMatchWake() {
        assertFalse(matcher.match("Саша поехали", "поехали").wakeFound)
    }
}
