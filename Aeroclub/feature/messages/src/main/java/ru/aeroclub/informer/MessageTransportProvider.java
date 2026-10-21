package ru.aeroclub.informer;

/**
 * Single composition point for message transport consumers.
 * Switch the implementation here when a real transport is agreed and implemented.
 */
public final class MessageTransportProvider {
    private static final MessageTransport LOCAL_TRANSPORT = new LocalFileMessageTransport();

    private MessageTransportProvider() {}

    /** Returns the shared stateless local transport implementation. */
    public static MessageTransport get() {
        return LOCAL_TRANSPORT;
    }
}
