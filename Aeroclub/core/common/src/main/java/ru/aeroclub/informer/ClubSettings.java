package ru.aeroclub.informer;

/** Club-wide configuration, separate from individual flight records. */
public final class ClubSettings {
    public final String clubName;
    public final String defaultMeetingPlace;

    public ClubSettings(String clubName, String defaultMeetingPlace) {
        this.clubName=clubName;
        this.defaultMeetingPlace=defaultMeetingPlace;
    }
}
