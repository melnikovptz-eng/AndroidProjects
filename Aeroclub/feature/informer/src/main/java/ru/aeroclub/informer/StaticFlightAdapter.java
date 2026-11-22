package ru.aeroclub.informer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.List;
import ru.aeroclub.informer.feature.R;

public final class StaticFlightAdapter extends RecyclerView.Adapter<StaticFlightAdapter.Holder> {
    public static final class Flight {
        public final String date, time, place;
        public final int[] avatars;
        public final int plusCount, total;
        public Flight(String date, String time, String place, int[] avatars, int plusCount, int total) {
            this.date=date; this.time=time; this.place=place; this.avatars=avatars; this.plusCount=plusCount; this.total=total;
        }
    }
    private final List<Flight> flights;
    public StaticFlightAdapter(List<Flight> flights) { this.flights=flights; }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView date,time,place; final LinearLayout avatars;
        Holder(View v) { super(v); date=v.findViewById(R.id.tvDate); time=v.findViewById(R.id.tvTime); place=v.findViewById(R.id.tvPlace); avatars=v.findViewById(R.id.rowAvatars); }
    }
    @Override public Holder onCreateViewHolder(ViewGroup parent,int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_flight,parent,false));
    }
    @Override public void onBindViewHolder(Holder h,int position) {
        Flight f=flights.get(position); h.date.setText(f.date); h.time.setText(f.time); h.place.setText(f.place); h.avatars.removeAllViews();
        Context c=h.avatars.getContext(); float d=c.getResources().getDisplayMetrics().density; int size=(int)(36*d), overlap=(int)(12*d);
        for(int i=0;i<f.avatars.length;i++) {
            CircleImageView image=new CircleImageView(c);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(size,size); if(i>0)p.setMarginStart(-overlap); p.rightMargin=(int)(4*d);
            image.setLayoutParams(p); image.setImageResource(f.avatars[i]); image.setCircleBackgroundColor(ContextCompat.getColor(c,R.color.bg_block));
            image.setBorderWidth((int)(2*d)); image.setBorderColor(ContextCompat.getColor(c,R.color.bg_block)); h.avatars.addView(image);
        }
        TextView plus=new TextView(c); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(size,size); pp.setMarginStart((int)(-4*d)); pp.rightMargin=(int)(4*d); plus.setLayoutParams(pp);
        plus.setBackgroundResource(R.drawable.bg_badge_plus); plus.setText("+"+f.plusCount); plus.setTextColor(ContextCompat.getColor(c,R.color.primary_blue)); plus.setTextSize(12); plus.setGravity(android.view.Gravity.CENTER); h.avatars.addView(plus);
        TextView count=new TextView(c); count.setLayoutParams(new LinearLayout.LayoutParams(size,size)); count.setBackgroundResource(R.drawable.bg_badge_count);
        count.setText(String.valueOf(f.total)); count.setTextColor(ContextCompat.getColor(c,R.color.white)); count.setTextSize(12); count.setGravity(android.view.Gravity.CENTER); h.avatars.addView(count);
    }
    @Override public int getItemCount(){return flights.size();}
}