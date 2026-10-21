package ru.aeroclub.informer;

import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantAdminActionsValidationTest {
    @Test public void acceptsNonBlankValue() {
        assertTrue(ParticipantAdminActions.isValidName("Иван"));
    }

    @Test public void rejectsNullAndBlankValues() {
        assertFalse(ParticipantAdminActions.isValidName(null));
        assertFalse(ParticipantAdminActions.isValidName(""));
        assertFalse(ParticipantAdminActions.isValidName(" "));
    }
}
