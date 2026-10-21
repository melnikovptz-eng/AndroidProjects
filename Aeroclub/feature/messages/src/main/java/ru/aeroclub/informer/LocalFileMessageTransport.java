package ru.aeroclub.informer;

import android.content.Context;
import android.system.Os;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Single-device simulator: private app file, no network, no claim of remote delivery. */
public final class LocalFileMessageTransport implements MessageTransport {
    static final String DIR = "local_message_transport";
    static final String NAME = "messages.json";
    private static final int LIMIT = 500;

    public static final String ANNOUNCEMENT_CHANNEL = "informer-announcements";
    public static final String OUTGOING = "OUTGOING";
    public static final String INCOMING = "INCOMING";
    public static final String LOCAL_ONLY = "LOCAL_ONLY";
    public static final String SIMULATED_INCOMING = "SIMULATED_INCOMING";

    @Override
    public LocalTransportMessage send(Context context, String recipientId, String recipientLabel, String body) {
        String text = clean(body);
        String recipient = clean(recipientId);
        if (context == null || text.isEmpty() || recipient.isEmpty()) return null;

        synchronized (LocalFileMessageTransport.class) {
            List<LocalTransportMessage> all = read(context);
            if (all == null) return null; // Do not overwrite a damaged/unreadable file.
            LocalTransportMessage message = new LocalTransportMessage(
                    UUID.randomUUID().toString(), recipient, recipient, clean(recipientLabel),
                    OUTGOING, text, LOCAL_ONLY, System.currentTimeMillis());
            all.add(message);
            return write(context, all) ? message : null;
        }
    }

    /** Explicit test hook only; this is not a message received from another device. */
    public LocalTransportMessage simulateIncoming(Context context, String conversationId,
                                                   String senderId, String senderLabel, String body) {
        String text = clean(body);
        String conversation = clean(conversationId);
        if (context == null || text.isEmpty() || conversation.isEmpty()) return null;

        synchronized (LocalFileMessageTransport.class) {
            List<LocalTransportMessage> all = read(context);
            if (all == null) return null;
            LocalTransportMessage message = new LocalTransportMessage(
                    UUID.randomUUID().toString(), conversation, clean(senderId), clean(senderLabel),
                    INCOMING, text, SIMULATED_INCOMING, System.currentTimeMillis());
            all.add(message);
            return write(context, all) ? message : null;
        }
    }

    @Override
    public List<LocalTransportMessage> getMessages(Context context, String conversationId) {
        if (context == null) return Collections.emptyList();
        String filter = clean(conversationId);
        synchronized (LocalFileMessageTransport.class) {
            List<LocalTransportMessage> all = read(context);
            if (all == null) return Collections.emptyList();
            if (filter.isEmpty()) return Collections.unmodifiableList(all);
            List<LocalTransportMessage> result = new ArrayList<>();
            for (LocalTransportMessage message : all) {
                if (filter.equals(message.conversationId)) result.add(message);
            }
            return Collections.unmodifiableList(result);
        }
    }

    /** Returns null for an existing but unreadable/corrupt file, preventing destructive overwrite. */
    private static List<LocalTransportMessage> read(Context context) {
        File file = file(context);
        if (!file.exists()) return new ArrayList<>();
        if (!file.isFile()) return null;

        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);

            JSONArray array = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            List<LocalTransportMessage> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                result.add(new LocalTransportMessage(
                        item.optString("id", ""),
                        item.optString("conversationId", ""),
                        item.optString("recipientId", ""),
                        item.optString("recipientLabel", ""),
                        item.optString("direction", ""),
                        item.optString("body", ""),
                        item.optString("status", ""),
                        item.optLong("createdAt", 0)));
            }
            return result;
        } catch (Exception error) {
            return null;
        }
    }

    private static boolean write(Context context, List<LocalTransportMessage> items) {
        File temporary = null;
        try {
            JSONArray array = new JSONArray();
            int start = Math.max(0, items.size() - LIMIT);
            for (int i = start; i < items.size(); i++) {
                LocalTransportMessage message = items.get(i);
                JSONObject item = new JSONObject();
                item.put("id", message.id);
                item.put("conversationId", message.conversationId);
                item.put("recipientId", message.recipientId);
                item.put("recipientLabel", message.recipientLabel);
                item.put("direction", message.direction);
                item.put("body", message.body);
                item.put("status", message.status);
                item.put("createdAt", message.createdAt);
                array.put(item);
            }

            File target = file(context);
            File parent = target.getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;
            temporary = new File(parent, NAME + ".tmp");

            try (FileOutputStream output = new FileOutputStream(temporary, false)) {
                output.write(array.toString().getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.getFD().sync();
            }

            // Atomic replacement: the previous good file is never deleted before the new one is ready.
            Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            return true;
        } catch (Exception error) {
            return false;
        } finally {
            if (temporary != null && temporary.exists()) temporary.delete();
        }
    }

    private static File file(Context context) {
        return new File(new File(context.getApplicationContext().getFilesDir(), DIR), NAME);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
