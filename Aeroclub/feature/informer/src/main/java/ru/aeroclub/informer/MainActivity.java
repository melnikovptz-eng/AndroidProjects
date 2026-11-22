package ru.aeroclub.informer;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ru.aeroclub.informer.feature.R;

/** Static visual implementation of the supplied Aeroclub design. No backend or live data. */
public class MainActivity extends Activity {
    private List<StaticFlightAdapter.Flight> sampleFlights() {
        return Arrays.asList(
            new StaticFlightAdapter.Flight("26 октября","10:00–14:00","Аэродром «Сокольники»",
                new int[]{R.drawable.avatar_1,R.drawable.avatar_2,R.drawable.avatar_3,R.drawable.avatar_4},7,12),
            new StaticFlightAdapter.Flight("2 ноября","09:00–13:00","Аэродром «Северный»",
                new int[]{R.drawable.avatar_5,R.drawable.avatar_6,R.drawable.avatar_1,R.drawable.avatar_2},8,3),
            new StaticFlightAdapter.Flight("9 ноября","10:00–14:00","Аэродром «Сокольники»",
                new int[]{R.drawable.avatar_3,R.drawable.avatar_4,R.drawable.avatar_5,R.drawable.avatar_6},10,1),
            new StaticFlightAdapter.Flight("16 ноября","09:00–13:00","Аэродром «Северный»",
                new int[]{R.drawable.avatar_2,R.drawable.avatar_3,R.drawable.avatar_1,R.drawable.avatar_4},4,9)
        );
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(getResources().getColor(R.color.bg_page));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.bg_block));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_main);

        RecyclerView list=findViewById(R.id.rvFlights);
        list.setLayoutManager(new LinearLayoutManager(this));
        StaticFlightAdapter adapter=new StaticFlightAdapter(sampleFlights());
        list.setAdapter(adapter);

        TabLayout tabs=findViewById(R.id.tabs);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if(tab.getPosition()==0) {
                    list.setAdapter(adapter);
                } else {
                    list.setAdapter(new StaticFlightAdapter(new ArrayList<>()));
                    Toast.makeText(MainActivity.this,"Соревнования: демонстрационные данные отсутствуют",Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        ((MaterialButton)findViewById(R.id.btnCreateFlight)).setOnClickListener(v ->
            Toast.makeText(this,"Демонстрационный режим: создание полёта отключено",Toast.LENGTH_SHORT).show());

        ((BottomNavigationView)findViewById(R.id.bottomNav)).setOnItemSelectedListener(item -> {
            if(item.getItemId()==R.id.nav_home) return true;
            String label=item.getTitle()==null?"":item.getTitle().toString();
            Toast.makeText(this,label+" — статический экран макета",Toast.LENGTH_SHORT).show();
            return true;
        });
    }
}