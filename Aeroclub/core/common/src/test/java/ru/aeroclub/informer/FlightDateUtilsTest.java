package ru.aeroclub.informer;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlightDateUtilsTest {
    @Test public void acceptsCanonicalIsoDate() {
        assertEquals("2026-10-21", FlightDateUtils.toIso("2026-10-21"));
    }

    @Test public void rejectsImpossibleIsoDate() {
        assertEquals("", FlightDateUtils.toIso("2026-02-31"));
    }

    @Test public void convertsRussianMonthLabel() {
        assertTrue(FlightDateUtils.toIso("25 октября").matches("\\d{4}-10-25"));
    }

    @Test public void formatsIsoDateForDisplay() {
        assertTrue(FlightDateUtils.toDisplay("2026-10-25").contains("25"));
    }
}
