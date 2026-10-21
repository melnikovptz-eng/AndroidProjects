package ru.aeroclub.informer;

import android.content.Context;
import java.util.List;

/**
 * Replaceable message transport boundary.
 * Implementations must report their actual delivery guarantees; local persistence is not remote delivery.
 */
public interface MessageTransport {
    LocalTransportMessage send(Context context, String recipientId, String recipientLabel, String body);
    List<LocalTransportMessage> getMessages(Context context, String conversationId);
}
