package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Single local repository for flight records. Server synchronization is not configured. */
public final class FlightRepository {
    private static final String PREFS = "flight_informer";
    private static final String FLIGHTS = "flights_json";
    private static final String ACTIVE_ID = "active_flight_id";
    private static final String DEMO_OUTBOX_CLEANUP = "demo_outbox_cleanup_v1";
    private FlightRepository() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, 0);
    }

    public static synchronized void ensureInitialized(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains(FLIGHTS)) {
            String id = UUID.randomUUID().toString();
            String title = p.getString("flight_title", "Полёт выходного дня");
            String date = p.getString("flight_date", "Вс, 25 октября");
            String time = p.getString("flight_time", "10:30");
            String place = p.getString("flight_place", "аэродром «Северный»");
            boolean demoSample = "Полёт выходного дня".equals(title)
                && "Вс, 25 октября".equals(date)
                && "10:30".equals(time)
                && "аэродром «Северный»".equals(place);
            Flight initial = new Flight(id, title, date, time, place, false, FlightDateUtils.toIso(date), demoSample);
            JSONArray arr = new JSONArray();
            arr.put(toJson(initial));
            p.edit().putString(FLIGHTS, arr.toString()).putString(ACTIVE_ID, id).apply();
            writeLegacy(p, initial);
        } else if (getActive(c) == null && !getFlights(c).isEmpty()) {
            activate(c, getFlights(c).get(0).id);
        }
    }

    public static synchronized List<Flight> getFlights(Context c) {
        if (!prefs(c).contains(FLIGHTS)) ensureInitialized(c);
        ArrayList<Flight> result = new ArrayList<>();
        boolean needsMigration=false;
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(FLIGHTS, "[]"));
            for (int i=0; i<arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    if (!o.has("dateIso") || o.optString("dateIso","").trim().isEmpty() || !o.has("isDemo")) needsMigration=true;
                    result.add(fromJson(o));
                }
            }
        } catch (Exception ignored) {}
        if(needsMigration && !result.isEmpty()) persist(c,result);
        if (!p.getBoolean(DEMO_OUTBOX_CLEANUP, false)) {
            for (Flight flight : result) if (flight.isDemo) SyncRepository.removeDemoFlightOperations(c, flight.id);
            p.edit().putBoolean(DEMO_OUTBOX_CLEANUP, true).apply();
        }
        return result;
    }

    public static synchronized String getActiveId(Context c) {
        return prefs(c).getString(ACTIVE_ID, null);
    }

    public static synchronized Flight getActive(Context c) {
        return getById(c, getActiveId(c));
    }

    public static synchronized Flight getById(Context c, String id) {
        if (id == null) return null;
        for (Flight f : getFlights(c)) if (id.equals(f.id)) return f;
        return null;
    }

    public static synchronized Flight create(Context c, String title, String date, String time, String place) {
        return create(c,title,date,time,place,FlightDateUtils.toIso(date));
    }

    public static synchronized Flight create(Context c, String title, String date, String time, String place, String dateIso) {
        String canonicalDate = FlightDateUtils.toIso(dateIso);
        if (canonicalDate.isEmpty()) canonicalDate = FlightDateUtils.toIso(date);
        if (canonicalDate.isEmpty() || !FlightDateUtils.isValidTime(time)) return null;
        Flight f = new Flight(UUID.randomUUID().toString(), clean(title,"Новый вылет"),
            clean(date,FlightDateUtils.toDisplay(canonicalDate)), clean(time,"Время уточняется"),
            clean(place,"Место встречи не указано"), false, canonicalDate, false);
        List<Flight> list = getFlights(c);
        list.add(f);
        persist(c, list);
        SyncRepository.enqueue(c,"flight",f.id,"create");
        return f;
    }

    public static synchronized boolean save(Context c, String id, String title, String date, String time, String place) {
        return save(c,id,title,date,time,place,FlightDateUtils.toIso(date));
    }

    public static synchronized boolean save(Context c, String id, String title, String date, String time, String place, String dateIso) {
        if (id == null || id.trim().isEmpty()) return false;
        String canonicalDate = FlightDateUtils.toIso(dateIso);
        if (canonicalDate.isEmpty()) canonicalDate = FlightDateUtils.toIso(date);
        if (canonicalDate.isEmpty() || !FlightDateUtils.isValidTime(time)) return false;
        List<Flight> list = getFlights(c);
        boolean found = false;
        boolean wasDemo = false;
        for (int i=0; i<list.size(); i++) if (list.get(i).id.equals(id)) {
            Flight old = list.get(i);
            wasDemo = old.isDemo;
            list.set(i, new Flight(id, clean(title,"Новый вылет"),
                clean(date,FlightDateUtils.toDisplay(canonicalDate)), clean(time,"Время уточняется"),
                clean(place,"Место встречи не указано"), old.published, canonicalDate, false));
            found = true; break;
        }
        if (!found) return false;
        persist(c, list);
        SyncRepository.enqueue(c,"flight",id,wasDemo ? "create" : "update");
        if (id.equals(getActiveId(c))) activate(c, id);
        return true;
    }

    public static synchronized void setPublished(Context c, String id, boolean published) {
        List<Flight> list = getFlights(c);
        boolean found = false;
        boolean wasDemo = false;
        for (int i=0; i<list.size(); i++) {
            Flight old = list.get(i);
            if (old.id.equals(id)) {
                list.set(i,new Flight(old.id,old.title,old.date,old.time,old.place,published,old.dateIso,old.isDemo));
                wasDemo = old.isDemo;
                found = true;
                break;
            }
        }
        if (found) {
            persist(c,list);
            if (!wasDemo) SyncRepository.enqueue(c,"flight",id,"update_publication");
        }
    }

    public static synchronized void activate(Context c, String id) {
        Flight f = getById(c, id);
        if (f == null) return;
        SharedPreferences p = prefs(c);
        p.edit().putString(ACTIVE_ID, f.id).apply();
        writeLegacy(p, f);
        if (!f.isDemo) SyncRepository.enqueue(c,"active_flight","active","set:"+f.id);
    }

    public static synchronized boolean delete(Context c, String id) {
        if (id == null || id.trim().isEmpty()) return false;
        List<Flight> list = getFlights(c);
        if (list.size() <= 1) return false;
        String active = getActiveId(c);
        ArrayList<Flight> remaining = new ArrayList<>();
        boolean found = false;
        boolean deletingDemo = false;
        for (Flight f : list) {
            if (id.equals(f.id)) { found = true; deletingDemo = f.isDemo; }
            else remaining.add(f);
        }
        if (!found || remaining.isEmpty()) return false;
        persist(c, remaining);
        if (!deletingDemo) SyncRepository.enqueue(c,"flight",id,"delete");
        if (id.equals(active)) activate(c, remaining.get(0).id);
        return true;
    }

    private static void persist(Context c, List<Flight> list) {
        JSONArray arr = new JSONArray();
        for (Flight f : list) arr.put(toJson(f));
        prefs(c).edit().putString(FLIGHTS, arr.toString()).apply();
    }

    private static JSONObject toJson(Flight f) {
        JSONObject o = new JSONObject();
        try { o.put("id",f.id); o.put("title",f.title); o.put("date",f.date); o.put("time",f.time); o.put("place",f.place); o.put("published",f.published); o.put("dateIso",f.dateIso); o.put("isDemo",f.isDemo); }
        catch (Exception ignored) {}
        return o;
    }

    private static Flight fromJson(JSONObject o) {
        String date=o.optString("date","Дата уточняется");
        String dateIso=o.optString("dateIso",FlightDateUtils.toIso(date));
        return new Flight(o.optString("id",UUID.randomUUID().toString()), o.optString("title","Вылет"),
            date, o.optString("time","Время уточняется"),
            o.optString("place","Место встречи не указано"), o.optBoolean("published",true),dateIso,
            o.optBoolean("isDemo", isLegacyDemo(o)));
    }

    private static boolean isLegacyDemo(JSONObject o) {
        return "Полёт выходного дня".equals(o.optString("title", ""))
            && "Вс, 25 октября".equals(o.optString("date", ""))
            && "10:30".equals(o.optString("time", ""))
            && "аэродром «Северный»".equals(o.optString("place", ""));
    }

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static void writeLegacy(SharedPreferences p, Flight f) {
        p.edit().putString("flight_title",f.title).putString("flight_date",f.date)
            .putString("flight_time",f.time).putString("flight_place",f.place).apply();
    }
}
