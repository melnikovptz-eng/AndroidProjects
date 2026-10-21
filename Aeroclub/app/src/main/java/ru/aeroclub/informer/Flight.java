package ru.aeroclub.informer;

/** Immutable domain model for one club flight. */
public final class Flight {
    public final String id;
    public final String title;
    public final String date;
    public final String time;
    public final String place;
    public final boolean published;

    public Flight(String id, String title, String date, String time, String place) {
        this(id,title,date,time,place,true);
    }

    public Flight(String id, String title, String date, String time, String place, boolean published) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.time = time;
        this.place = place;
        this.published = published;
    }
}
