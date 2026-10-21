package ru.aeroclub.informer;

/** Shared presentation rules so preview, widget, reminders and sharing mark demo data identically. */
public final class FlightPresentation {
    private FlightPresentation() {}

    public static String title(Flight flight) {
        if (flight == null) return "Ближайший вылет";
        return demoPrefix(flight) + safeTitle(flight);
    }

    public static String shareHeading(Flight flight, String clubName) {
        if (flight == null) return clubNameOrDefault(clubName);
        return demoPrefix(flight) + clubNameOrDefault(clubName) + " — " + safeTitle(flight);
    }

    private static String safeTitle(Flight flight) {
        return flight.title == null || flight.title.trim().isEmpty() ? "Вылет клуба" : flight.title.trim();
    }

    private static String demoPrefix(Flight flight) {
        return flight != null && flight.isDemo ? "ДЕМО · " : "";
    }

    public static String clubNameOrDefault(String clubName) {
        return clubName == null || clubName.trim().isEmpty() ? "Аэроклуб" : clubName.trim();
    }
}
