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
                Toast.makeText(this, "Bahasa Indonesia tidak didukung pada TTS perangkat", Toast.LENGTH_SHORT).show();
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
        int tahunMasehiInt = calendar.get(Calendar.YEAR);
        int bulanMasehiInt = calendar.get(Calendar.MONTH);
        int hariMasehiInt = calendar.get(Calendar.DAY_OF_MONTH);

        long totalHariJuli = toJdn(tahunMasehiInt, bulanMasehiInt + 1, hariMasehiInt);
        long hariHijriyahTotal = totalHariJuli - 1948440 + 10632;
        long n = (long) ((hariHijriyahTotal - 10616) / 10631.0);
        hariHijriyahTotal = hariHijriyahTotal - 10631 * n + 354;
        long tahunHijriyah = (long) ((10965 * hariHijriyahTotal + 1000) / 325465) + 30 * n + 1;
        long sisaHari = hariHijriyahTotal - (long) ((354 * (tahunHijriyah - 30 * n - 1)) + (int)((tahunHijriyah - 30 * n - 1) / 30));
        if (sisaHari < 0) {
            tahunHijriyah--;
            sisaHari = hariHijriyahTotal - (long) ((354 * (tahunHijriyah - 30 * n - 1)) + (int)((tahunHijriyah - 30 * n - 1) / 30));
        }
        int bulanHijriyah = (int) ((29 * sisaHari + 295) / 295);
        if (bulanHijriyah > 12) bulanHijriyah = 12;
        long tanggalHijriyah = sisaHari - (long) ((29.5 * (bulanHijriyah - 1)) + 0.5);
        if (tanggalHijriyah < 1) tanggalHijriyah = 1;

        String[] namaBulanHijriyahArray = {"Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir", "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban", "Ramadhan", "Syawal", "Dulqaidah", "Dulhijjah"};
        String namaBulanHijriyahStr = namaBulanHijriyahArray[Math.max(0, Math.min(11, bulanHijriyah - 1))];

        String tanggalHijriyahLengkap = tanggalHijriyah + " " + namaBulanHijriyahStr + " " + tahunHijriyah;

        int levelBaterai = getBatteryPercentage();

        String teksUcapan = "Pukul " + waktuSekarang + ". " +
                "Tanggal Masehi: " + tanggalMasehi + ". " +
                "Tanggal Hijriyah: " + tanggalHijriyahLengkap + ". " +
                "Sisa baterai " + levelBaterai + " persen.";

        if (tts != null) {
            tts.speak(teksUcapan, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private long toJdn(int tahun, int bulan, int hari) {
        if (bulan < 3) {
            tahun -= 1;
            bulan += 12;
        }
        int a = tahun / 100;
        int b = a / 4;
        int c = 2 - a + b;
        long e = (long) (365.25 * (tahun + 4716));
        long f = (long) (30.6001 * (bulan + 1));
        return c + hari + e + f - 1524;
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