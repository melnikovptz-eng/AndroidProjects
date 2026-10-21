package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Single local source of truth for participant records. */
public final class ParticipantRepository {
    private static final String PREFS = "flight_informer";
    private static final String KEY = "participants_json";
    private static final String LEGACY = "custom_participants";
    private static final String STATUS_MIGRATION_DONE = "participant_status_migration_v2_done";
    private ParticipantRepository() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, 0);
    }

    public static synchronized void ensureInitialized(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains(KEY)) {
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
        // Migrate old name-based statuses once; never rescan the full participant × flight
        // matrix on every UI render. The marker is written even when there was nothing to migrate.
        if (!p.getBoolean(STATUS_MIGRATION_DONE, false)) {
            SharedPreferences.Editor edit = p.edit();
            List<Flight> flights = FlightRepository.getFlights(c);
            for (Participant participant : getAllWithoutInit(p)) {
                for (Flight flight : flights) {
                    String oldKey = "participant_status_" + flight.id + "_" + participant.displayName;
                    String newKey = "participant_status_" + flight.id + "_" + participant.id;
                    String sourceKey = p.contains(newKey) ? newKey : oldKey;
                    if (p.contains(sourceKey)) {
                        String oldStatus = p.getString(sourceKey, AttendanceRepository.STATUS_PENDING);
                        String normalized = AttendanceRepository.normalizeStatus(oldStatus);
                        if (normalized != null) {
                            if (!p.contains(newKey)) edit.putString(newKey, normalized);
                            AttendanceRepository.setResponse(c, participant.id, flight.id, normalized);
                            if (p.contains(oldKey)) edit.remove(oldKey);
                        }
                    }
                }
            }
            edit.putBoolean(STATUS_MIGRATION_DONE, true).apply();
        }
    }

    private static List<Participant> getAllWithoutInit(SharedPreferences p) {
        ArrayList<Participant> result = new ArrayList<>();
        JSONArray arr = read(p.getString(KEY, "[]"));
        for (int i=0; i<arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String id=o.optString("id",""), name=o.optString("displayName","").trim();
            if (!id.isEmpty() && !name.isEmpty()) result.add(new Participant(id,name,o.optLong("createdAt",0)));
        }
        return result;
    }

    public static synchronized List<Participant> getAll(Context c) {
        ensureInitialized(c);
        ArrayList<Participant> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        boolean needsMigration = false;
        JSONArray arr = read(prefs(c).getString(KEY, "[]"));
        for (int i=0; i<arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id", "").trim();
            String name = o.optString("displayName", "").trim();
            if (name.isEmpty()) continue;
            if (id.isEmpty() || seenIds.contains(id)) {
                id = UUID.randomUUID().toString();
                needsMigration = true;
            }
            seenIds.add(id);
            result.add(new Participant(id, name, o.optLong("createdAt", 0)));
        }
        if (needsMigration) saveAll(c, result);
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
        SyncRepository.enqueue(c,"participant",created.id,"create");
        return created;
    }

    public static synchronized boolean rename(Context c, String id, String displayName) {
        String name = clean(displayName);
        if (id == null || name.isEmpty()) return false;
        ArrayList<Participant> list = new ArrayList<>(getAll(c));
        for (Participant p : list) if (!p.id.equals(id) && p.displayName.equalsIgnoreCase(name)) return false;
        boolean found = false;
        String previousName = null;
        for (int i=0; i<list.size(); i++) {
            Participant p = list.get(i);
            if (p.id.equals(id)) {
                previousName = p.displayName;
                list.set(i, new Participant(id, name, p.createdAt));
                found = true;
                break;
            }
        }
        if (found) {
            saveAll(c, list);
            SyncRepository.enqueue(c,"participant",id,"update");
            if (previousName != null && !previousName.equals(name)) {
                SharedPreferences p = prefs(c);
                SharedPreferences.Editor edit = p.edit();
                for (String key : p.getAll().keySet()) {
                    if (key.startsWith("participant_status_") && key.endsWith("_" + previousName)) edit.remove(key);
                }
                edit.apply();
            }
        }
        return found;
    }

    public static synchronized boolean delete(Context c, String id) {
        if (id == null) return false;
        ArrayList<Participant> list = new ArrayList<>(getAll(c));
        ArrayList<String> removedNames = new ArrayList<>();
        boolean removed = false;
        for (int i=list.size()-1; i>=0; i--) if (id.equals(list.get(i).id)) {
            removedNames.add(list.get(i).displayName);
            list.remove(i); removed = true;
        }
        if (removed) {
            saveAll(c, list);
            SyncRepository.enqueue(c,"participant",id,"delete");
            AttendanceRepository.deleteParticipant(c, id);
            if (id.equals(AttendanceRepository.getSelectedParticipantId(c))) {
                AttendanceRepository.setSelectedParticipantId(c, null);
            }
            // Remove participant-scoped legacy status keys for all flights.
            SharedPreferences p = prefs(c);
            SharedPreferences.Editor e = p.edit();
            for (String key : p.getAll().keySet()) {
                if (!key.startsWith("participant_status_")) continue;
                boolean belongsToDeletedParticipant = key.endsWith("_" + id);
                if (!belongsToDeletedParticipant) {
                    for (String oldName : removedNames) {
                        if (key.endsWith("_" + oldName)) {
                            belongsToDeletedParticipant = true;
                            break;
                        }
                    }
                }
                if (belongsToDeletedParticipant) e.remove(key);
            }
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
