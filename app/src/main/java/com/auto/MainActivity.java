package com.auto;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private Handler handler = new Handler();
    private Runnable runnable;
    private int intervalMenit = 30;

    private EditText etInterval;

    // Nilai koreksi (offset) untuk mencocokkan kalender sistem dengan Kemenag
    private int offsetHari = -1; 

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("suara penyebut tanggal hijriyah dan masehi otomatis");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView labelInterval = new TextView(this);
        labelInterval.setText("Atur Interval Suara (Menit):");
        labelInterval.setPadding(0, 32, 0, 8);
        box.addView(labelInterval);

        etInterval = new EditText(this);
        etInterval.setInputType(InputType.TYPE_CLASS_NUMBER);
        etInterval.setText("30");
        box.addView(etInterval);

        Button btnMulai = new Button(this);
        btnMulai.setText("Mulai Pengingat Otomatis");
        btnMulai.setPadding(0, 16, 0, 16);
        box.addView(btnMulai);

        setContentView(box);

        tts = new TextToSpeech(this, this);

        btnMulai.setOnClickListener(v -> {
            String inputInterval = etInterval.getText().toString();
            if (!inputInterval.isEmpty()) {
                intervalMenit = Integer.parseInt(inputInterval);
                Toast.makeText(this, "Interval diatur setiap " + intervalMenit + " menit", Toast.LENGTH_SHORT).show();
                mulaiPengingatOtomatis();
            } else {
                Toast.makeText(this, "Masukkan interval terlebih dahulu", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("id", "ID"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Bahasa Indonesia tidak didukung pada perangkat", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void mulaiPengingatOtomatis() {
        handler.removeCallbacks(runnable);
        runnable = new Runnable() {
            @Override
            public void run() {
                ucapkanInformasiLengkap();
                handler.postDelayed(this, (long) intervalMenit * 60 * 1000);
            }
        };
        handler.post(runnable);
    }

    private void ucapkanInformasiLengkap() {
        String waktuSekarang = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        String tanggalMasehi = new SimpleDateFormat("EEEE, dd MMMM yyyy", new Locale("id", "ID")).format(new Date());

        LocalDate sekarangMasehi = LocalDate.now().plusDays(offsetHari);
        HijrahDate hijrahDate = HijrahDate.from(sekarangMasehi);
        
        long hariH = hijrahDate.get(ChronoField.DAY_OF_MONTH);
        long bulanH = hijrahDate.get(ChronoField.MONTH_OF_YEAR);
        long tahunH = hijrahDate.get(ChronoField.YEAR);

        String[] namaBulanHijriyahArray = {
            "Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir", 
            "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban", 
            "Ramadhan", "Syawal", "Dulqaidah", "Dulhijjah"
        };
        
        String namaBulanHijriyahStr = "";
        if (bulanH >= 1 && bulanH <= 12) {
            namaBulanHijriyahStr = namaBulanHijriyahArray[(int) bulanH - 1];
        }

        String tanggalHijriyahLengkap = hariH + " " + namaBulanHijriyahStr + " " + tahunH;

        int levelBaterai = getBatteryPercentage();

        String teksUcapan = "Pukul " + waktuSekarang + ". " +
                "Tanggal Masehi: " + tanggalMasehi + ". " +
                "Tanggal Hijriyah: " + tanggalHijriyahLengkap + ". " +
                "Sisa baterai " + levelBaterai + " persen.";

        if (tts != null) {
            tts.speak(teksUcapan, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private int getBatteryPercentage() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = -1;
        int scale = -1;
        if (batteryStatus != null) {
            level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        }
        if (level >= 0 && scale > 0) {
            return (int) ((level / (float) scale) * 100);
        }
        return 0;
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        handler.removeCallbacks(runnable);
        super.onDestroy();
    }
}