package ru.aeroclub.informer;

import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantValidationTest {
    @Test public void acceptsNonBlankParticipantName() {
        assertTrue(ParticipantValidation.isValidName("Иван"));
    }

    @Test public void rejectsMissingParticipantName() {
        assertFalse(ParticipantValidation.isValidName(null));
        assertFalse(ParticipantValidation.isValidName(""));
        assertFalse(ParticipantValidation.isValidName("   "));
    }
}
