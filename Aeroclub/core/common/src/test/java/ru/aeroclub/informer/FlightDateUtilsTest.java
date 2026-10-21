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

    @Test public void marksOnlyExplicitSampleAsDemo() {
        Flight sample = new Flight("demo", "Полёт выходного дня", "Вс, 25 октября", "10:30", "аэродром «Северный»", false, "2026-10-25", true);
        Flight regular = new Flight("real", "Полёт клуба", "Вс, 25 октября", "10:30", "аэродром «Северный»", false, "2026-10-25");
        assertTrue(sample.isDemo);
        assertFalse(regular.isDemo);
    }

    @Test public void acceptsValidFlightTime() {
        assertTrue(FlightDateUtils.isValidTime("10:30"));
        assertTrue(FlightDateUtils.toMillis("2026-10-25", "10:30") > 0L);
    }

    @Test public void rejectsInvalidFlightTime() {
        assertFalse(FlightDateUtils.isValidTime("25:61"));
        assertFalse(FlightDateUtils.isValidTime("9:5"));
        assertEquals(0L, FlightDateUtils.toMillis("2026-10-25", "25:61"));
        assertEquals(0L, FlightDateUtils.toMillis("2026-10-25", "9:5"));
    }

    @Test public void formatsIsoDateForDisplay() {
        assertTrue(FlightDateUtils.toDisplay("2026-10-25").contains("25"));
    }

    @Test public void acceptsLeapDayAndRejectsImpossibleCalendarDays() {
        assertEquals("2024-02-29", FlightDateUtils.toIso("2024-02-29"));
        assertEquals("", FlightDateUtils.toIso("2025-02-29"));
        assertEquals("", FlightDateUtils.toIso("32 октября"));
    }

    @Test public void acceptsTimeBoundariesAndRejectsMalformedValues() {
        assertTrue(FlightDateUtils.isValidTime("00:00"));
        assertTrue(FlightDateUtils.isValidTime("23:59"));
        assertFalse(FlightDateUtils.isValidTime("24:00"));
        assertFalse(FlightDateUtils.isValidTime("12:60"));
        assertFalse(FlightDateUtils.isValidTime("1:30"));
    }
}
