package ru.aeroclub.informer;

/** Immutable domain model for one club flight. */
public final class Flight {
    public final String id;
    public final String title;
    /** Localized display label; never use this value for date comparisons. */
    public final String date;
    /** Canonical local date in yyyy-MM-dd format. */
    public final String dateIso;
    public final String time;
    public final String place;
    public final boolean published;

    public Flight(String id,String title,String date,String time,String place) {
        this(id,title,date,time,place,true,FlightDateUtils.toIso(date));
    }

    public Flight(String id,String title,String date,String time,String place,boolean published) {
        this(id,title,date,time,place,published,FlightDateUtils.toIso(date));
    }

    public Flight(String id,String title,String date,String time,String place,boolean published,String dateIso) {
        this.id=id;
        this.title=title;
        this.date=date;
        String canonical=dateIso==null||dateIso.trim().isEmpty()?FlightDateUtils.toIso(date):FlightDateUtils.toIso(dateIso);
        this.dateIso=canonical.isEmpty()?FlightDateUtils.toIso(date):canonical;
        this.time=time;
        this.place=place;
        this.published=published;
    }
}
