package ru.aeroclub.informer;

/** Local message draft/history item. Delivery is not implied by saving or opening the share chooser. */
public final class MessageRecord {
    public final String id;
    public final String flightId;
    public final String text;
    public final String state;
    public final long updatedAt;

    public MessageRecord(String id, String flightId, String text, String state, long updatedAt) {
        this.id=id; this.flightId=flightId; this.text=text; this.state=state; this.updatedAt=updatedAt;
    }
}
