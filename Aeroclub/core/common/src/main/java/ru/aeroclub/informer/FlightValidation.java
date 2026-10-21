package ru.aeroclub.informer;

/** Shared validation for required flight fields. */
public final class FlightValidation {
    private FlightValidation() {}

    public static boolean isValidTitle(String title) {
        return title != null && !title.trim().isEmpty();
    }
}
