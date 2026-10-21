package ru.aeroclub.informer;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Canonical local-date conversion. ISO dates are stored; localized labels are display-only. */
public final class FlightDateUtils {
    private static final Locale RU=Locale.forLanguageTag("ru");
    private FlightDateUtils() {}

    public static String toIso(String value) {
        if(value==null || value.trim().isEmpty()) return "";
        String raw=value.trim();
        if(raw.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
                iso.setLenient(false);
                Date parsed=iso.parse(raw);
                return parsed==null?"":new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(parsed);
            } catch(Exception ignored) { return ""; }
        }
        java.util.regex.Matcher matcher=java.util.regex.Pattern
            .compile("(?iu)(?:[\\p{L}.]{2,8},\\s*)?(\\d{1,2})\\s+([\\p{L}.]+)")
            .matcher(raw);
        if(!matcher.find()) return "";
        int day;
        try { day=Integer.parseInt(matcher.group(1)); } catch(Exception ignored) { return ""; }
        String month=matcher.group(2).toLowerCase(RU).replace(".","");
        String[] starts={"янв","фев","мар","апр","ма","июн","июл","авг","сен","окт","ноя","дек"};
        int monthIndex=-1;
        for(int i=0;i<starts.length;i++) if(month.startsWith(starts[i])) { monthIndex=i; break; }
        if(monthIndex<0) return "";
        Calendar result=Calendar.getInstance();
        result.set(Calendar.MONTH,monthIndex);
        result.set(Calendar.DAY_OF_MONTH,1);
        result.set(Calendar.HOUR_OF_DAY,12);
        result.set(Calendar.MINUTE,0);
        result.set(Calendar.SECOND,0);
        result.set(Calendar.MILLISECOND,0);
        int max=result.getActualMaximum(Calendar.DAY_OF_MONTH);
        if(day<1 || day>max) return "";
        result.set(Calendar.DAY_OF_MONTH,day);
        return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(result.getTime());
    }

    public static String toDisplay(String iso) {
        try {
            SimpleDateFormat input=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
            input.setLenient(false);
            Date date=input.parse(iso);
            return date==null?iso:new SimpleDateFormat("EEE, d MMM",RU).format(date);
        } catch(Exception ignored) { return iso==null?"":iso; }
    }

    public static long toMillis(String iso,String time) {
        try {
            SimpleDateFormat input=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
            input.setLenient(false);
            Date date=input.parse(iso);
            String[] parts=time.split(":");
            if(date==null || parts.length!=2) return 0L;
            int hour=Integer.parseInt(parts[0]), minute=Integer.parseInt(parts[1]);
            if(hour<0||hour>23||minute<0||minute>59) return 0L;
            Calendar c=Calendar.getInstance();
            c.setTime(date);
            c.set(Calendar.HOUR_OF_DAY,hour);
            c.set(Calendar.MINUTE,minute);
            c.set(Calendar.SECOND,0);
            c.set(Calendar.MILLISECOND,0);
            return c.getTimeInMillis();
        } catch(Exception ignored) { return 0L; }
    }
}
