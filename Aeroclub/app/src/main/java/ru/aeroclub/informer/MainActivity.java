package ru.aeroclub.informer;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final int navy = Color.rgb(23,43,77), muted = Color.rgb(82,100,119);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(245,247,250));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1);
        root.setPadding(dp(20),dp(18),dp(20),dp(20)); root.setBackgroundColor(Color.rgb(245,247,250));
        root.addView(text("АЭРОКЛУБ",12,muted,true));
        TextView title = text("Информер полётов",26,navy,true);
        LinearLayout.LayoutParams tp = lp(-1,-2); tp.topMargin=dp(6); root.addView(title,tp);
        TextView intro = text("Ближайший вылет и подтверждение участия",14,muted,false);
        tp=lp(-1,-2); tp.topMargin=dp(4); root.addView(intro,tp);
        LinearLayout card = new LinearLayout(this); card.setOrientation(1);
        card.setPadding(dp(16),dp(18),dp(16),dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE); bg.setCornerRadius(dp(18)); bg.setStroke(dp(1),Color.rgb(220,228,236)); card.setBackground(bg);
        tp=lp(-1,-2); tp.topMargin=dp(22); root.addView(card,tp);
        card.addView(text("БЛИЖАЙШИЙ ВЫЛЕТ",11,muted,true));
        TextView event=text("Полёт выходного дня",21,navy,true); tp=lp(-1,-2); tp.topMargin=dp(10); card.addView(event,tp);
        addLine(card,"Сб, 25 октября · 10:30",15,navy,8);
        addLine(card,"Место встречи: аэродром «Северный»",14,muted,5);
        TextView status=text("Статус: ждём ваш ответ",13,Color.rgb(19,117,71),false);
        tp=lp(-1,-2); tp.topMargin=dp(12); card.addView(status,tp);
        Button going=button("Я еду"), no=button("Я не еду"), route=button("Маршрут");
        card.addView(going,lp(-1,dp(52))); tp=lp(-1,dp(52)); tp.topMargin=dp(8); card.addView(no,tp);
        tp=lp(-1,dp(52)); tp.topMargin=dp(8); card.addView(route,tp);
        going.setOnClickListener(v -> status.setText("Статус: вы подтвердили участие"));
        no.setOnClickListener(v -> status.setText("Статус: вы не участвуете"));
        route.setOnClickListener(v -> status.setText("Маршрут: уточните место встречи у организатора"));
        TextView note=text("Чтобы вынести информер на главный экран Android: удерживайте пустое место на рабочем столе → Виджеты → «Аэроклуб — информер».",13,muted,false);
        tp=lp(-1,-2); tp.topMargin=dp(18); root.addView(note,tp);
        setContentView(root);
    }
    private void addLine(LinearLayout box,String s,int size,int color,int top) {
        TextView t=text(s,size,color,false); LinearLayout.LayoutParams p=lp(-1,-2); p.topMargin=dp(top); box.addView(t,p);
    }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }
    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(15); return b; }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
