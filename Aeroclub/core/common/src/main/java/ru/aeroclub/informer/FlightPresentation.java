package ru.aeroclub.informer;

/** Shared presentation rules so preview, widget, reminders and sharing mark demo data identically. */
public final class FlightPresentation {
    private FlightPresentation() {}

    public static String title(Flight flight) {
        if (flight == null) return "Ближайший вылет";
        return demoPrefix(flight) + flight.title;
    }

    public static String shareHeading(Flight flight, String clubName) {
        if (flight == null) return cleanClubName(clubName);
        return demoPrefix(flight) + cleanClubName(clubName) + " — " + flight.title;
    }

    private static String demoPrefix(Flight flight) {
        return flight != null && flight.isDemo ? "ДЕМО · " : "";
    }

    private static String cleanClubName(String clubName) {
        return clubName == null || clubName.trim().isEmpty() ? "Аэроклуб" : clubName.trim();
    }
}
