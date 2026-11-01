package ru.voiceactivation.next

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.*
import android.graphics.Typeface
import android.content.Context
import kotlin.concurrent.thread
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var command: EditText
    private lateinit var matcher: AcousticMatcher
    private fun ui(s: String) { runOnUiThread { status.text = s } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        matcher = AcousticMatcher(this)
        val root = LinearLayout(this).apply { orientation=1; setPadding(24,28,24,24) }
        root.addView(TextView(this).apply { text="VOICE ACTIVATION"; textSize=24f; typeface=Typeface.DEFAULT_BOLD })
        root.addView(TextView(this).apply { text="Команды: MFCC + DTW. Проверка владельца выполняется отдельным акустическим профилем." })
        command=EditText(this).apply { hint="Название команды" }; root.addView(command)
        fun button(label:String, action:()->Unit) { root.addView(Button(this).apply { text=label; setOnClickListener { action() } }) }
        button("Записать профиль владельца") { capture { matcher.enrollOwner(it); ui("Профиль владельца записан.") } }
        button("Записать образец команды") {
            val name=command.text.toString().trim()
            if(name.isBlank()) { ui("Введите название команды."); return@button }
            capture { matcher.enrollCommand(name,it); ui("Образец «$name» сохранён.") }
        }
        button("Проверить голосовую команду") {
            capture { audio ->
                val owner=matcher.verifyOwner(audio); val result=matcher.match(audio)
                ui(if(!owner) "ОТКЛОНЕНО: голос не прошёл проверку владельца."
                   else if(result==null) "Команда не распознана уверенно."
                   else "Владелец подтверждён. Команда: $result")
            }
        }
        button("Показать команды") { ui("Команды: "+matcher.commands().ifEmpty { listOf("нет") }.joinToString()) }
        status=TextView(this).apply { text="Готово. Запишите профиль владельца и образцы команд."; textSize=16f; setPadding(0,22,0,0) }
        root.addView(status); setContentView(ScrollView(this).apply { addView(root) })
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),7)
    }
    private fun capture(done:(DoubleArray)->Unit) {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),7); ui("Разрешите доступ к микрофону."); return }
        ui("Слушаю 2,5 секунды…")
        thread {
            try {
                val rate=16000
                val min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                val rec=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,max(min,rate*2))
                val pcm=ShortArray(rate*5/2); rec.startRecording(); var n=0
                while(n<pcm.size) { val k=rec.read(pcm,n,pcm.size-n); if(k>0)n+=k else break }
                rec.stop(); rec.release()
                if(n<rate/2) { ui("Запись слишком короткая."); return@thread }
                val x=DoubleArray(n){pcm[it]/32768.0}
                if(x.maxOf{abs(it)}<0.012) { ui("Слишком тихо. Повторите."); return@thread }
                done(x)
            } catch(e:Exception) { ui("Ошибка микрофона: ${e.message}") }
        }
    }
}

private class AcousticMatcher(ctx:Context) {
    private val p=ctx.getSharedPreferences("templates",Context.MODE_PRIVATE)
    fun commands()=p.getStringSet("names",emptySet())!!.toList().sorted()
    fun enrollOwner(x:DoubleArray) { p.edit().putString("owner",enc(Mfcc.extract(x).average())).apply() }
    fun verifyOwner(x:DoubleArray):Boolean {
        val saved=p.getString("owner",null) ?: return false
        return cosine(dec(saved),Mfcc.extract(x).average())>0.88
    }
    fun enrollCommand(name:String,x:DoubleArray) {
        val key="cmd_"+name; val items=p.getString(key,null)?.split("|")?.toMutableList() ?: mutableListOf()
        items.add(enc(Mfcc.extract(x).flatMap { it.toList() }.toDoubleArray()))
        p.edit().putString(key,items.takeLast(6).joinToString("|")).putStringSet("names",(commands()+name).toSet()).apply()
    }
    fun match(x:DoubleArray):String? {
        val seq=Mfcc.extract(x); var best:String?=null; var first=Double.MAX_VALUE; var next=Double.MAX_VALUE
        for(name in commands()) for(item in p.getString("cmd_"+name,null)?.split("|") ?: emptyList()) {
            val ref=dec(item).toList().chunked(13).map{it.toDoubleArray()}; val d=dtw(seq,ref)
            if(d<first) { next=first; first=d; best=name } else if(d<next) next=d
        }
        return if(first<7.0 && first<next*0.88) best else null
    }
    private fun enc(a:DoubleArray)=android.util.Base64.encodeToString(a.joinToString(",").toByteArray(),android.util.Base64.NO_WRAP)
    private fun dec(s:String)=String(android.util.Base64.decode(s,android.util.Base64.DEFAULT)).split(",").mapNotNull{it.toDoubleOrNull()}.toDoubleArray()
    private fun cosine(a:DoubleArray,b:DoubleArray):Double {
        if(a.size!=b.size)return -1.0
        val dot=a.indices.sumOf{a[it]*b[it]}; val na=sqrt(a.sumOf{it*it}); val nb=sqrt(b.sumOf{it*it})
        return if(na*nb==0.0)-1.0 else dot/(na*nb)
    }
    private fun dtw(a:List<DoubleArray>,b:List<DoubleArray>):Double {
        if(a.isEmpty()||b.isEmpty())return 999.0
        var prev=DoubleArray(b.size+1){1e9}; prev[0]=0.0
        for(i in a.indices) {
            val cur=DoubleArray(b.size+1){1e9}
            for(j in max(0,i-12)..min(b.size-1,i+12)) {
                val cost=sqrt(a[i].indices.sumOf{k->(a[i][k]-b[j][k]).pow(2)}/13.0)
                cur[j+1]=cost+minOf(prev[j+1],cur[j],prev[j])
            }
            prev=cur
        }
        return prev[b.size]/max(a.size,b.size)
    }
    private fun List<DoubleArray>.average()=DoubleArray(13){i->if(isEmpty())0.0 else map{it[i]}.average()}
}
private object Mfcc {
    fun extract(x:DoubleArray):List<DoubleArray> {
        val frame=400; val hop=160; val nfft=512; val filters=26; val coeff=13; val sr=16000
        val out=mutableListOf<DoubleArray>(); var pos=0
        while(pos+frame<=x.size) {
            val power=DoubleArray(nfft/2+1)
            for(k in 0..nfft/2) {
                var re=0.0; var im=0.0
                for(t in 0 until frame) {
                    val v=x[pos+t]*(0.54-0.46*cos(2*PI*t/(frame-1))); val ang=2*PI*k*t/nfft
                    re+=v*cos(ang); im-=v*sin(ang)
                }
                power[k]=re*re+im*im
            }
            val low=0.0; val high=2595*ln(1+sr/2.0/700)
            val points=(0..filters+1).map{p->val m=low+(high-low)*p/(filters+1); (700*(exp(m/2595)-1)*nfft/sr).toInt().coerceIn(0,nfft/2)}
            val mel=DoubleArray(filters)
            for(m in 0 until filters) {
                for(k in points[m]..points[m+1]) if(points[m+1]>points[m]) mel[m]+=(k-points[m]).toDouble()/(points[m+1]-points[m])*power[k]
                for(k in points[m+1]..points[m+2]) if(points[m+2]>points[m+1]) mel[m]+=(points[m+2]-k).toDouble()/(points[m+2]-points[m+1])*power[k]
            }
            val logs=DoubleArray(filters){ln(mel[it].coerceAtLeast(1e-10))}
            out.add(DoubleArray(coeff){c->(0 until filters).sumOf{m->logs[m]*cos(PI*c*(m+0.5)/filters)}/filters})
            pos+=hop
        }
        return out
    }
}
