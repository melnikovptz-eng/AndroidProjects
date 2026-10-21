package ru.aeroclub.informer;

/**
 * Single composition point for the local message transport.
 * UI modules must obtain the transport here instead of constructing implementations themselves.
 */
public final class MessageTransportProvider {
    private static final MessageTransport LOCAL = new LocalFileMessageTransport();

    private MessageTransportProvider() {}

    public static MessageTransport get() {
        return LOCAL;
    }
}
