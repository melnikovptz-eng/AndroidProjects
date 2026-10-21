package ru.aeroclub.informer;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlightAdminActionsValidationTest {
    @Test public void acceptsNonBlankValue() {
        assertTrue(FlightAdminActions.isValidTitle("Новый вылет"));
    }

    @Test public void rejectsNullAndBlankValues() {
        assertFalse(FlightAdminActions.isValidTitle(null));
        assertFalse(FlightAdminActions.isValidTitle(""));
        assertFalse(FlightAdminActions.isValidTitle("   "));
    }
}
