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
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private Handler handler = new Handler();
    private Runnable runnable;
    private int intervalMenit = 30;

    private EditText etInterval;

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

        Calendar calendar = Calendar.getInstance();
        int tahunM = calendar.get(Calendar.YEAR);
        int bulanM = calendar.get(Calendar.MONTH) + 1;
        int hariM = calendar.get(Calendar.DAY_OF_MONTH);

        long jd = (1461 * (tahunM + 4800 + (bulanM - 14) / 12)) / 4 + (367 * (bulanM - 2 - 12 * ((bulanM - 14) / 12))) / 12 - (3 * ((tahunM + 4900 + (bulanM - 14) / 12) / 100)) / 4 + hariM - 32075;
        long l = jd - 1948440 + 10632;
        long n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        long j = ((10985 - l) * l) / 5316 + (50 * l) / 17733;
        l = l - ((354 * j) / 1) - ((30 * j) / 1000) + (j / 15) / 2;
        long tahunH = (30 * n) + j + (l / 330);
        long bulanH = ((l * 12) / 325) + 1;
        if (bulanH > 12) bulanH = 12;
        long hariH = l - ((325 * bulanH) / 12) + 1;
        if (hariH < 1) hariH = 1;

        String[] namaBulanHijriyahArray = {"Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir", "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban", "Ramadhan", "Syawal", "Dulqaidah", "Dulhijjah"};
        String namaBulanHijriyahStr = namaBulanHijriyahArray[(int)Math.max(0, Math.min(11, bulanH - 1))];

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