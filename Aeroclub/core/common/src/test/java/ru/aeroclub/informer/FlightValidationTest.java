package ru.aeroclub.informer;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlightValidationTest {
    @Test public void acceptsNonBlankTitle() {
        assertTrue(FlightValidation.isValidTitle("Тренировочный вылет"));
    }

    @Test public void rejectsMissingTitle() {
        assertFalse(FlightValidation.isValidTitle(null));
        assertFalse(FlightValidation.isValidTitle(""));
        assertFalse(FlightValidation.isValidTitle("   "));
    }
}
