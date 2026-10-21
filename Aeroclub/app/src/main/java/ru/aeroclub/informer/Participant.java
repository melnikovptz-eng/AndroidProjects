package ru.aeroclub.informer;

/** Stable local participant record. Contact fields are intentionally omitted until approved. */
public final class Participant {
    public final String id;
    public final String displayName;
    public final long createdAt;

    public Participant(String id, String displayName, long createdAt) {
        this.id = id;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }
}
