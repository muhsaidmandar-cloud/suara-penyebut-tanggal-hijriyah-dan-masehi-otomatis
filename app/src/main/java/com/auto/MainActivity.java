package com.auto;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static TextToSpeech tts;
    private static List<Voice> voiceList = new ArrayList<>();
    private static List<String> voiceNames = new ArrayList<>();
    private static ArrayAdapter<String> voiceAdapter;
    private static Spinner spinnerVoice;
    private static Spinner spinnerVolume;

    private EditText etInterval;
    private Button btnMulai;
    private boolean isRunning = false;
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
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        // --- PENGATURAN INTERVAL ---
        TextView labelInterval = new TextView(this);
        labelInterval.setText("Atur Interval Suara (Menit):");
        labelInterval.setPadding(0, 24, 0, 4);
        box.addView(labelInterval);

        etInterval = new EditText(this);
        etInterval.setInputType(InputType.TYPE_CLASS_NUMBER);
        etInterval.setText("5");
        box.addView(etInterval);

        // --- PILIHAN SUARA / VOICE TTS ---
        TextView labelVoice = new TextView(this);
        labelVoice.setText("Pilih Suara TTS:");
        labelVoice.setPadding(0, 16, 0, 4);
        box.addView(labelVoice);

        spinnerVoice = new Spinner(this);
        if (voiceNames.isEmpty()) {
            voiceNames.add("Default (Sistem)");
        }
        voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, voiceNames);
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVoice.setAdapter(voiceAdapter);
        box.addView(spinnerVoice);

        // --- PILIHAN JENIS VOLUME ---
        TextView labelVolume = new TextView(this);
        labelVolume.setText("Pilih Jenis Volume Audio:");
        labelVolume.setPadding(0, 16, 0, 4);
        box.addView(labelVolume);

        spinnerVolume = new Spinner(this);
        String[] pilihanVolume = {"Media", "Notifikasi", "Alarm", "Nada Dering (Ring)"};
        ArrayAdapter<String> volumeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, pilihanVolume);
        volumeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVolume.setAdapter(volumeAdapter);
        box.addView(spinnerVolume);

        // --- TOMBOL KONTROL MULAI/BERHENTI ---
        btnMulai = new Button(this);
        btnMulai.setText("Mulai Pengingat Otomatis");
        btnMulai.setPadding(0, 16, 0, 16);
        box.addView(btnMulai);

        setContentView(box);

        if (tts == null) {
            tts = new TextToSpeech(this, this);
        }

        btnMulai.setOnClickListener(v -> {
            if (!isRunning) {
                String inputInterval = etInterval.getText().toString();
                if (!inputInterval.isEmpty()) {
                    try {
                        int intervalMenit = Integer.parseInt(inputInterval);
                        if (intervalMenit <= 0) {
                            Toast.makeText(this, "Interval minimal 1 menit", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        isRunning = true;
                        btnMulai.setText("Hentikan Pengingat Otomatis");
                        Toast.makeText(this, "Pengingat aktif setiap " + intervalMenit + " menit (Akurat)", Toast.LENGTH_SHORT).show();
                        
                        // Ucapkan langsung sekarang
                        mulaiPengingatAlarm(intervalMenit);
                        panggilUcapkan(this);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Masukkan angka interval yang valid", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Masukkan interval terlebih dahulu", Toast.LENGTH_SHORT).show();
                }
            } else {
                batalkanPengingatAlarm();
                isRunning = false;
                btnMulai.setText("Mulai Pengingat Otomatis");
                Toast.makeText(this, "Pengingat otomatis dihentikan", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("id", "ID"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Bahasa Indonesia tidak didukung pada perangkat", Toast.LENGTH_SHORT).show();
            } else {
                new Handler().postDelayed(this::loadAvailableVoices, 800);
            }
        }
    }

    private void loadAvailableVoices() {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices != null && !voices.isEmpty()) {
                voiceList.clear();
                while (voiceNames.size() > 1) {
                    voiceNames.remove(1);
                }

                for (Voice voice : voices) {
                    if (voice.getLocale() != null) {
                        String lang = voice.getLocale().getLanguage().toLowerCase();
                        if (lang.contains("id") || lang.contains("ind") || lang.contains("in")) {
                            voiceList.add(voice);
                            voiceNames.add(voice.getName() + " (" + voice.getLocale().getDisplayLanguage() + ")");
                        }
                    }
                }
                voiceAdapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mulaiPengingatAlarm(int menit) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAtMillis = System.currentTimeMillis() + ((long) menit * 60 * 1000);
        long intervalMillis = (long) menit * 60 * 1000;

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtMillis, intervalMillis, pendingIntent);
            }
        }
    }

    private void batalkanPengingatAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    public static void panggilUcapkan(Context context) {
        if (tts == null) return;

        try {
            int selectedVoicePos = spinnerVoice.getSelectedItemPosition();
            if (selectedVoicePos > 0 && (selectedVoicePos - 1) < voiceList.size()) {
                tts.setVoice(voiceList.get(selectedVoicePos - 1));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int selectedVolumeIndex = spinnerVolume != null ? spinnerVolume.getSelectedItemPosition() : 0;
        int audioAttributesUsage = AudioAttributes.USAGE_MEDIA;

        switch (selectedVolumeIndex) {
            case 0: audioAttributesUsage = AudioAttributes.USAGE_MEDIA; break;
            case 1: audioAttributesUsage = AudioAttributes.USAGE_NOTIFICATION; break;
            case 2: audioAttributesUsage = AudioAttributes.USAGE_ALARM; break;
            case 3: audioAttributesUsage = AudioAttributes.USAGE_VOICE_COMMUNICATION; break;
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(audioAttributesUsage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        tts.setAudioAttributes(audioAttributes);

        String waktuSekarang = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String tanggalMasehi = new SimpleDateFormat("EEEE, dd MMMM yyyy", new Locale("id", "ID")).format(new Date());

        LocalDate sekarangMasehi = LocalDate.now().plusDays(-1);
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
        int levelBaterai = getBatteryPercentageStatic(context);

        String teksUcapan = "Pukul " + waktuSekarang + ". " +
                "Tanggal Masehi: " + tanggalMasehi + ". " +
                "Tanggal Hijriyah: " + tanggalHijriyahLengkap + ". " +
                "Sisa baterai " + levelBaterai + " persen.";

        tts.speak(teksUcapan, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private static int getBatteryPercentageStatic(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
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

    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = null;
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Auto::VoiceWakeLock");
                wakeLock.acquire(10 * 60 * 1000L); // tahan 10 menit maksimal untuk proses suara
            }

            panggilUcapkan(context);

            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}