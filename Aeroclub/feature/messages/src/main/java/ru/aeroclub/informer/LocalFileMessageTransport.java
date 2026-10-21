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
        if (context == null || text.isEmpty() || recipient.isEmpty()) {
            ModuleDebugLog.log(context, "MSG-02", "local_send", "rejected", "invalid_input");
            return null;
        }

        synchronized (LocalFileMessageTransport.class) {
            List<LocalTransportMessage> all = read(context);
            if (all == null) {
                ModuleDebugLog.log(context, "MSG-02", "local_send", "error", "store_unreadable");
                return null; // Do not overwrite a damaged/unreadable file.
            }
            LocalTransportMessage message = new LocalTransportMessage(
                    UUID.randomUUID().toString(), recipient, recipient, clean(recipientLabel),
                    OUTGOING, text, LOCAL_ONLY, System.currentTimeMillis());
            all.add(message);
            boolean saved = write(context, all);
            ModuleDebugLog.log(context, "MSG-02", "local_send", saved ? "success_local_only" : "error", saved ? "announcement_saved" : "write_failed");
            return saved ? message : null;
        }
    }

    /** Explicit test hook only; this is not a message received from another device. */
    public LocalTransportMessage simulateIncoming(Context context, String conversationId,
                                                   String senderId, String senderLabel, String body) {
        String text = clean(body);
        String conversation = clean(conversationId);
        if (context == null || text.isEmpty() || conversation.isEmpty()) {
            ModuleDebugLog.log(context, "MSG-02", "simulate_incoming", "rejected", "invalid_input");
            return null;
        }

        synchronized (LocalFileMessageTransport.class) {
            List<LocalTransportMessage> all = read(context);
            if (all == null) {
                ModuleDebugLog.log(context, "MSG-02", "simulate_incoming", "error", "store_unreadable");
                return null;
            }
            LocalTransportMessage message = new LocalTransportMessage(
                    UUID.randomUUID().toString(), conversation, clean(senderId), clean(senderLabel),
                    INCOMING, text, SIMULATED_INCOMING, System.currentTimeMillis());
            all.add(message);
            boolean saved = write(context, all);
            ModuleDebugLog.log(context, "MSG-02", "simulate_incoming", saved ? "success_test_only" : "error", saved ? "simulated_record_saved" : "write_failed");
            return saved ? message : null;
        }
    }


    /** Export a versioned JSON exchange file through Android's Storage Access Framework. */
    public int exportTo(Context context, java.io.OutputStream output) throws java.io.IOException {
        if (context == null || output == null) throw new java.io.IOException("missing_context_or_output");
        synchronized (LocalFileMessageTransport.class) {
            List<LocalTransportMessage> all = read(context);
            if (all == null) throw new java.io.IOException("message_store_unreadable");
            try {
                JSONObject root = new JSONObject();
                root.put("format", "aeroclub-message-exchange");
                root.put("version", 1);
                root.put("exportedAt", System.currentTimeMillis());
                JSONArray array = new JSONArray();
                for (LocalTransportMessage message : all) {
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
                root.put("messages", array);
                output.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
                output.flush();
                ModuleDebugLog.log(context, "MSG-02", "file_export", "success", "count=" + all.size());
                return all.size();
            } catch (org.json.JSONException error) {
                throw new java.io.IOException("message_export_json_error", error);
            }
        }
    }

    /**
     * Imports and merges a validated exchange file. Existing IDs are never duplicated;
     * malformed files are rejected before any local data is changed.
     */
    public int importFrom(Context context, java.io.InputStream input) throws java.io.IOException {
        if (context == null || input == null) throw new java.io.IOException("missing_context_or_input");
        byte[] bytes;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (buffer.size() + count > 2 * 1024 * 1024) throw new java.io.IOException("exchange_file_too_large");
                buffer.write(chunk, 0, count);
            }
            bytes = buffer.toByteArray();
        }
        final JSONArray incoming;
        try {
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (!"aeroclub-message-exchange".equals(root.optString("format"))
                    || root.optInt("version", -1) != 1
                    || !root.has("messages")) throw new java.io.IOException("unsupported_exchange_format");
            incoming = root.getJSONArray("messages");
            if (incoming.length() > LIMIT) throw new java.io.IOException("too_many_messages");
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject item = incoming.optJSONObject(i);
                if (item == null || item.optString("id").trim().isEmpty()
                        || item.optString("conversationId").trim().isEmpty()
                        || item.optString("body").trim().isEmpty()
                        || !("INCOMING".equals(item.optString("direction"))
                            || "OUTGOING".equals(item.optString("direction")))) {
                    throw new java.io.IOException("invalid_message_record_" + i);
                }
            }
        } catch (org.json.JSONException error) {
            throw new java.io.IOException("invalid_exchange_json", error);
        }

        synchronized (LocalFileMessageTransport.class) {
            List<LocalTransportMessage> existing = read(context);
            if (existing == null) throw new java.io.IOException("message_store_unreadable");
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (LocalTransportMessage message : existing) ids.add(message.id);
            int added = 0;
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject item = incoming.optJSONObject(i);
                String id = item.optString("id").trim();
                if (!ids.add(id)) continue;
                existing.add(new LocalTransportMessage(
                        id, item.optString("conversationId"), item.optString("recipientId"),
                        item.optString("recipientLabel"), item.optString("direction"),
                        item.optString("body"), item.optString("status", "IMPORTED"),
                        item.optLong("createdAt", System.currentTimeMillis())));
                added++;
            }
            if (added > 0 && !write(context, existing)) throw new java.io.IOException("local_merge_write_failed");
            ModuleDebugLog.log(context, "MSG-02", "file_import", "success",
                    "added=" + added + "; received=" + incoming.length());
            return added;
        }
    }

    @Override
    public List<LocalTransportMessage> getMessages(Context context, String conversationId) {
        if (context == null) return Collections.emptyList();
        String filter = clean(conversationId);
        synchronized (LocalFileMessageTransport.class) {
            List<LocalTransportMessage> all = read(context);
            if (all == null) {
                ModuleDebugLog.log(context, "MSG-02", "local_read", "error", "store_unreadable");
                throw new IllegalStateException("Local message store is unreadable");
            }
            if (filter.isEmpty()) {
                ModuleDebugLog.log(context, "MSG-02", "local_read", "success", "count=" + all.size());
                return Collections.unmodifiableList(all);
            }
            List<LocalTransportMessage> result = new ArrayList<>();
            for (LocalTransportMessage message : all) {
                if (filter.equals(message.conversationId)) result.add(message);
            }
            ModuleDebugLog.log(context, "MSG-02", "local_read", "success", "count=" + result.size());
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
