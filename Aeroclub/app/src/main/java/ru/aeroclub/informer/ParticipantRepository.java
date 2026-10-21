package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Single local source of truth for participant records. */
public final class ParticipantRepository {
    private static final String PREFS = "flight_informer";
    private static final String KEY = "participants_json";
    private static final String LEGACY = "custom_participants";
    private ParticipantRepository() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, 0);
    }

    public static synchronized void ensureInitialized(Context c) {
        SharedPreferences p = prefs(c);
        if (p.contains(KEY)) return;
        JSONArray migrated = new JSONArray();
        String legacy = p.getString(LEGACY, "");
        if (legacy != null && !legacy.trim().isEmpty()) {
            for (String line : legacy.split("\\n")) {
                String name = line.trim();
                if (name.isEmpty() || containsName(migrated, name)) continue;
                migrated.put(toJson(new Participant(UUID.randomUUID().toString(), name, System.currentTimeMillis())));
            }
        }
        p.edit().putString(KEY, migrated.toString()).apply();
    }

    public static synchronized List<Participant> getAll(Context c) {
        ensureInitialized(c);
        ArrayList<Participant> result = new ArrayList<>();
        JSONArray arr = read(prefs(c).getString(KEY, "[]"));
        for (int i=0; i<arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id", "");
            String name = o.optString("displayName", "").trim();
            if (!id.isEmpty() && !name.isEmpty()) result.add(new Participant(id, name, o.optLong("createdAt", 0)));
        }
        return result;
    }

    public static synchronized Participant add(Context c, String displayName) {
        String name = clean(displayName);
        if (name.isEmpty()) return null;
        for (Participant p : getAll(c)) if (p.displayName.equalsIgnoreCase(name)) return null;
        ArrayList<Participant> list = new ArrayList<>(getAll(c));
        Participant created = new Participant(UUID.randomUUID().toString(), name, System.currentTimeMillis());
        list.add(created);
        saveAll(c, list);
        return created;
    }

    public static synchronized boolean rename(Context c, String id, String displayName) {
        String name = clean(displayName);
        if (id == null || name.isEmpty()) return false;
        ArrayList<Participant> list = new ArrayList<>(getAll(c));
        for (Participant p : list) if (!p.id.equals(id) && p.displayName.equalsIgnoreCase(name)) return false;
        boolean found = false;
        for (int i=0; i<list.size(); i++) {
            Participant p = list.get(i);
            if (p.id.equals(id)) {
                list.set(i, new Participant(id, name, p.createdAt));
                found = true;
                break;
            }
        }
        if (found) saveAll(c, list);
        return found;
    }

    public static synchronized boolean delete(Context c, String id) {
        if (id == null) return false;
        ArrayList<Participant> list = new ArrayList<>(getAll(c));
        boolean removed = false;
        for (int i=list.size()-1; i>=0; i--) if (id.equals(list.get(i).id)) {
            list.remove(i); removed = true;
        }
        if (removed) {
            saveAll(c, list);
            // Remove participant-scoped attendance statuses for all flights.
            SharedPreferences p = prefs(c);
            SharedPreferences.Editor e = p.edit();
            for (String key : p.getAll().keySet()) if (key.startsWith("participant_status_") && key.endsWith("_" + id)) e.remove(key);
            e.apply();
        }
        return removed;
    }

    private static void saveAll(Context c, List<Participant> list) {
        JSONArray arr = new JSONArray();
        for (Participant p : list) arr.put(toJson(p));
        prefs(c).edit().putString(KEY, arr.toString()).apply();
    }
    private static JSONObject toJson(Participant p) {
        JSONObject o = new JSONObject();
        try { o.put("id", p.id); o.put("displayName", p.displayName); o.put("createdAt", p.createdAt); }
        catch (Exception ignored) {}
        return o;
    }
    private static JSONArray read(String s) {
        try { return new JSONArray(s); } catch (Exception ignored) { return new JSONArray(); }
    }
    private static boolean containsName(JSONArray arr, String name) {
        for (int i=0; i<arr.length(); i++) {
            JSONObject o=arr.optJSONObject(i);
            if (o!=null && name.equalsIgnoreCase(o.optString("displayName",""))) return true;
        }
        return false;
    }
    private static String clean(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }
}
