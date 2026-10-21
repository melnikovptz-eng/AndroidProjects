package ru.aeroclub.informer;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlightPresentationTest {
    private Flight flight(boolean demo) {
        return new Flight("flight-1", "Тренировочный вылет", "Вс, 25 октября",
            "10:30", "аэродром", false, "2026-10-25", demo);
    }

    @Test public void marksDemoTitlesButNotRealTitles() {
        assertEquals("ДЕМО · Тренировочный вылет", FlightPresentation.title(flight(true)));
        assertEquals("Тренировочный вылет", FlightPresentation.title(flight(false)));
        assertEquals("Ближайший вылет", FlightPresentation.title(null));
    }

    @Test public void shareHeadingUsesClubNameAndDemoMarker() {
        assertEquals("ДЕМО · Клуб Небо — Тренировочный вылет",
            FlightPresentation.shareHeading(flight(true), "Клуб Небо"));
        assertEquals("Аэроклуб — Тренировочный вылет",
            FlightPresentation.shareHeading(flight(false), " "));
    }
}
