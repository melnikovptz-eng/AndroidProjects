package ru.aeroclub.informer;

/** Shared validation for required participant fields. */
public final class ParticipantValidation {
    private ParticipantValidation() {}

    public static boolean isValidName(String name) {
        return name != null && !name.trim().isEmpty();
    }
}
