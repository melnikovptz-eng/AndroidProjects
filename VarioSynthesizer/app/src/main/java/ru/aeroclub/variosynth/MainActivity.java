package ru.aeroclub.variosynth;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int RATE = 44100;
    private final ArrayList<Event> events = new ArrayList<>();
    private final ExecutorService audioWorker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicBoolean keyHeld = new AtomicBoolean(false);
    private EditText duration, pause;
    private TextView status, sequence;
    private Button recordButton, playButton;
    private boolean recording = false;
    private long recordingStart;
    private long pressStart;
    private int pressedLevel;
    private JSONArray recorded = new JSONArray();
    private AudioTrack currentTrack;
    // Protects a new tone from an older worker finishing late.
    private volatile long playbackGeneration = 0;

    static class Event {
        int level; long startMs; long durationMs;
        Event(int l,long s,long d){level=l;startMs=s;durationMs=d;}
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff263238);
        getWindow().setNavigationBarColor(0xff263238);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(14),dp(16),dp(18)); root.setBackgroundColor(0xfff4f6f8);
        scroll.addView(root); setContentView(scroll);
        TextView title = text("Vario Synthesizer",22,true); root.addView(title);
        TextView subtitle = text("Уровни −5…+5 • ручная запись и воспроизведение",14,false);
        subtitle.setPadding(0,dp(5),0,dp(14)); root.addView(subtitle);
        TextView settings = text("ПАРАМЕТРЫ ЗВУКА",14,true); root.addView(settings);
        LinearLayout params = new LinearLayout(this); params.setOrientation(LinearLayout.HORIZONTAL);
        duration = field("Тон, мс","120"); pause = field("Пауза, мс","100");
        params.addView(column("Длит.",duration),new LinearLayout.LayoutParams(0,-2,1));
        params.addView(column("Пауза",pause),new LinearLayout.LayoutParams(0,-2,1)); root.addView(params);
        TextView hint=text("Удерживайте уровень для непрерывного тона. Короткое нажатие — один сигнал. Частота меняется с уровнем; длительность и пауза задают импульсы.",13,false);
        hint.setPadding(0,dp(10),0,dp(12)); root.addView(hint);
        TextView levels=text("УРОВНИ ВАРИОМЕТРА",14,true); root.addView(levels);
        TextView frequencyMap=text("−5: 150 Гц  •  −4: 250 Гц  •  −3: 350 Гц  •  −2: 450 Гц  •  −1: 550 Гц  •  0: 700 Гц  •  +1: 850 Гц  •  +2: 1000 Гц  •  +3: 1150 Гц  •  +4: 1300 Гц  •  +5: 1500 Гц",12,false);
        frequencyMap.setPadding(0,dp(5),0,dp(8)); root.addView(frequencyMap);
        int[] lv={-5,-4,-3,-2,-1,0,1,2,3,4,5};
        for(int row=0;row<3;row++){
            LinearLayout line=new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL);
            for(int c=0;c<4;c++){int ix=row*4+c;if(ix>=lv.length)break;int level=lv[ix];
                Button b=new Button(this); b.setText((level>0?"+":"")+level); b.setTextSize(17);
                b.setMinHeight(dp(54)); b.setAllCaps(false);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(level<0?0xffdce7ef:level>0?0xffffe0b2:0xffcfd8dc));
                b.setOnTouchListener((v,e)->{
                    if(e.getAction()==MotionEvent.ACTION_DOWN){ beginLevel(level); return true; }
                    if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){ endLevel(level); return true; }
                    return true;
                });
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(58),1); bp.setMargins(dp(2),dp(2),dp(2),dp(2));line.addView(b,bp);
            }
            while(line.getChildCount()<4){View spacer=new View(this);line.addView(spacer,new LinearLayout.LayoutParams(0,dp(58),1));}
            root.addView(line);
        }
        recordButton=button("●  Записать",0xffb71c1c); playButton=button("▶  Воспроизвести",0xff1565c0);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(recordButton,new LinearLayout.LayoutParams(0,dp(56),1)); controls.addView(playButton,new LinearLayout.LayoutParams(0,dp(56),1));
        root.addView(controls);
        recordButton.setOnClickListener(v->toggleRecording());
        playButton.setOnClickListener(v->playSequence());
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button stop=button("■ Стоп",0xff455a64), export=button("Экспорт WAV + JSON",0xff2e7d32), clear=button("Очистить",0xff607d8b);
        actions.addView(stop,new LinearLayout.LayoutParams(0,dp(50),1)); actions.addView(clear,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(actions);
        LinearLayout actions2=new LinearLayout(this);actions2.addView(export,new LinearLayout.LayoutParams(-1,dp(50)));root.addView(actions2);
        stop.setOnClickListener(v->stopPlayback()); clear.setOnClickListener(v->{stopPlayback();events.clear();updateSequence();status.setText("Запись очищена");});
        export.setOnClickListener(v->exportAll());
        status=text("Готово. Нажмите «Записать», затем играйте уровни.",14,false);status.setPadding(0,dp(12),0,dp(4));root.addView(status);
        sequence=text("Последовательность: 0 событий",13,false);root.addView(sequence);
    }
    private LinearLayout column(String label, EditText f){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);TextView t=text(label,12,false);l.addView(t);l.addView(f);return l;}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setHint(hint);e.setInputType(2);e.setTextSize(16);return e;}
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff263238);if(bold)t.setTypeface(null,1);return t;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(0xffffffff);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));return b;}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private int number(EditText e,int fallback){try{return Math.max(1,Math.min(10000,Integer.parseInt(e.getText().toString().trim())));}catch(Exception ex){return fallback;}}
    private int frequency(int level){
        switch(Math.max(-5,Math.min(5,level))){
            case -5:return 150; case -4:return 250; case -3:return 350; case -2:return 450; case -1:return 550;
            case 0:return 700; case 1:return 850; case 2:return 1000; case 3:return 1150; case 4:return 1300; default:return 1500;
        }
    }
    private void beginLevel(int level){
        keyHeld.set(true);
        if(recording){pressStart=System.currentTimeMillis();pressedLevel=level;}
        startTone(level);
    }
    private void endLevel(int level){
        // A quick tap must still finish one audible pulse.
        keyHeld.set(false);
        if(recording){long now=System.currentTimeMillis();long dur=Math.max(20,now-pressStart);long start=Math.max(0,pressStart-recordingStart);events.add(new Event(level,start,dur));updateSequence();}
    }
    private void startTone(int level){
        stopPlayback();
        final long token = ++playbackGeneration;
        keyHeld.set(true);
        playing.set(true);
        final int hz=frequency(level), toneMs=number(duration,120), gapMs=number(pause,100);
        audioWorker.execute(()->{
            AudioTrack track=null;
            try{
                int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
                track=new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    Math.max(min,((RATE/4+1)&~1)),AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);
                synchronized(this){currentTrack=track;}
                track.setVolume(1.0f);
                track.play(); short[] buf=new short[512];double phase=0;long last=System.currentTimeMillis();boolean on=true;
                while(playing.get() && playbackGeneration == token){
                    long elapsed=System.currentTimeMillis()-last;
                    // Stop shortly after finger release, but keep a 40 ms minimum pulse for quick taps.
                    if(!keyHeld.get() && elapsed>=40) break;
                    if(elapsed >= (on ? toneMs : gapMs)){
                        on=!on; last=System.currentTimeMillis();
                        if(!keyHeld.get() && !on) break;
                    }
                    for(int i=0;i<buf.length;i++){buf[i]=(short)(on?Math.sin(phase)*10000:0);phase+=2*Math.PI*hz/RATE;if(phase>2*Math.PI)phase-=2*Math.PI;}
                    track.write(buf,0,buf.length);
                }
            }catch(Exception ex){runOnUiThread(()->status.setText("Ошибка звука: "+ex.getMessage()));}
            finally {
                if(track!=null){try{track.pause();track.flush();track.release();}catch(Exception ignored){}}
                synchronized(this){if(currentTrack==track)currentTrack=null;}
                // An old audio task must never silence a newer button press.
                if (playbackGeneration == token) {
                    playing.set(false);
                    keyHeld.set(false);
                }
            }
        });
    }
    private synchronized void stopPlayback(){
        playbackGeneration++;
        playing.set(false);
        keyHeld.set(false);
        try{if(currentTrack!=null){currentTrack.pause();currentTrack.flush();}}catch(Exception ignored){}
    }
    private void toggleRecording(){
        recording=!recording;
        if(recording){events.clear();recordingStart=System.currentTimeMillis();recordButton.setText("■ Завершить запись");recordButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff6a1b9a));status.setText("Идёт запись нажатий. Нажимайте и удерживайте уровни −5…+5.");}
        else{recordButton.setText("● Записать");recordButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffb71c1c));status.setText("Записано событий: "+events.size());}
        updateSequence();
    }
    private void updateSequence(){sequence.setText("Последовательность: "+events.size()+" событий"+(events.isEmpty()?"":" • последнее: "+(events.get(events.size()-1).level>0?"+":"")+events.get(events.size()-1).level+" ("+events.get(events.size()-1).durationMs+" мс)"));}
    private void playSequence(){
        if(recording){toggleRecording();}
        if(events.isEmpty()){status.setText("Нет записи. Нажмите «Записать» и сыграйте уровни.");return;}
        stopPlayback(); playing.set(true); final ArrayList<Event> copy=new ArrayList<>(events);
        audioWorker.execute(()->{
            try{long cursor=0;for(Event e:copy){if(!playing.get())break;long wait=e.startMs-cursor;if(wait>0)Thread.sleep(Math.min(wait,10000));if(!playing.get())break;
                playOne(frequency(e.level),e.durationMs);cursor=e.startMs+e.durationMs;}
            }catch(Exception ignored){} finally{playing.set(false);runOnUiThread(()->status.setText("Воспроизведение завершено."));}
        });
        status.setText("Воспроизведение записанной последовательности…");
    }
    private void playOne(int hz,long ms)throws Exception{
        int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track=new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
          new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),Math.max(min,((RATE/4+1)&~1)),AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);
        track.play();short[] buf=new short[512];double phase=0;long samples=ms*RATE/1000;
        try{while(samples>0&&playing.get()){int n=(int)Math.min(buf.length,samples);for(int i=0;i<n;i++){buf[i]=(short)(Math.sin(phase)*6500);phase+=2*Math.PI*hz/RATE;}track.write(buf,0,n);samples-=n;}}finally{try{track.stop();}catch(Exception ignored){}track.release();}
    }
    private JSONObject profile()throws Exception{
        JSONObject root=new JSONObject();root.put("format","vario-synth-profile");root.put("version",1);root.put("sampleRate",RATE);root.put("frequencyTableHz",new JSONArray(new int[]{150,250,350,450,550,700,850,1000,1150,1300,1500}));root.put("toneDurationMs",number(duration,120));root.put("pauseDurationMs",number(pause,100));
        JSONArray arr=new JSONArray();for(Event e:events){JSONObject o=new JSONObject();o.put("level",e.level);o.put("frequencyHz",frequency(e.level));o.put("startMs",e.startMs);o.put("durationMs",e.durationMs);arr.put(o);}root.put("events",arr);root.put("totalDurationMs",events.isEmpty()?0:events.get(events.size()-1).startMs+events.get(events.size()-1).durationMs);return root;
    }
    private void exportAll(){
        if(events.isEmpty()){status.setText("Сначала запишите последовательность.");return;}
        try{
            String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
            byte[] json=(profile().toString(2)+"\n").getBytes(StandardCharsets.UTF_8);
            byte[] wav=makeWav();
            saveFile("vario_"+stamp+".json","application/json",json);
            saveFile("vario_"+stamp+".wav","audio/wav",wav);
            status.setText("Экспортированы WAV и JSON. Файлы находятся в Downloads/vario-synthesizer.");Toast.makeText(this,"Экспорт WAV + JSON выполнен",Toast.LENGTH_LONG).show();
        }catch(Exception e){status.setText("Ошибка экспорта: "+e.getMessage());}
    }
    private byte[] makeWav()throws Exception{
        long total=events.get(events.size()-1).startMs+events.get(events.size()-1).durationMs;total=Math.max(total,100);
        int count=(int)Math.min(RATE*300L,total*RATE/1000);byte[] out=new byte[44+count*2];
        putAscii(out,0,"RIFF");putInt(out,4,36+count*2);putAscii(out,8,"WAVE");putAscii(out,12,"fmt ");putInt(out,16,16);putShort(out,20,1);putShort(out,22,1);putInt(out,24,RATE);putInt(out,28,RATE*2);putShort(out,32,2);putShort(out,34,16);putAscii(out,36,"data");putInt(out,40,count*2);
        for(int i=0;i<count;i++){long ms=i*1000L/RATE;Event active=null;for(Event e:events){if(ms>=e.startMs&&ms<e.startMs+e.durationMs){active=e;break;}if(e.startMs>ms)break;}
            short sample=0;if(active!=null){double phase=2*Math.PI*frequency(active.level)*i/RATE;sample=(short)(Math.sin(phase)*6500);}out[44+i*2]=(byte)(sample&255);out[45+i*2]=(byte)((sample>>8)&255);}
        return out;
    }
    private void saveFile(String name,String mime,byte[] bytes)throws Exception{
        if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);v.put(MediaStore.MediaColumns.MIME_TYPE,mime);v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/vario-synthesizer");try(OutputStream os=getContentResolver().openOutputStream(getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v))){os.write(bytes);}}
        else {if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},91);throw new Exception("Разрешите доступ к файлам и повторите экспорт");}
            File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"vario-synthesizer");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Не удалось создать папку Downloads");try(FileOutputStream os=new FileOutputStream(new File(dir,name))){os.write(bytes);}}
    }
    private void putAscii(byte[] b,int off,String s){for(int i=0;i<s.length();i++)b[off+i]=(byte)s.charAt(i);}
    private void putInt(byte[] b,int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>8);b[o+2]=(byte)(v>>16);b[o+3]=(byte)(v>>24);}
    private void putShort(byte[] b,int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>8);}
    @Override protected void onDestroy(){stopPlayback();audioWorker.shutdownNow();super.onDestroy();}
}
