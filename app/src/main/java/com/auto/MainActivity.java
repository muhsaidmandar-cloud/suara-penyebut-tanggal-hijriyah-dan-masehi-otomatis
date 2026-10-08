package com.auto;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
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

    private TextToSpeech tts;
    private Handler handler = new Handler();
    private Runnable runnable;
    private int intervalMenit = 30;

    private EditText etInterval;
    private Spinner spinnerVoice;
    private Spinner spinnerVolume;
    
    private List<Voice> voiceList = new ArrayList<>();
    private List<String> voiceNames = new ArrayList<>();
    private ArrayAdapter<String> voiceAdapter;

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
        etInterval.setText("30");
        box.addView(etInterval);

        // --- PILIHAN SUARA / VOICE TTS ---
        TextView labelVoice = new TextView(this);
        labelVoice.setText("Pilih Suara TTS:");
        labelVoice.setPadding(0, 16, 0, 4);
        box.addView(labelVoice);

        spinnerVoice = new Spinner(this);
        voiceNames.add("Default (Bahasa Indonesia)");
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

        // --- TOMBOL MULAI ---
        Button btnMulai = new Button(this);
        btnMulai.setText("Mulai Pengingat Otomatis");
        btnMulai.setPadding(0, 16, 0, 16);
        box.addView(btnMulai);

        setContentView(box);

        // Inisialisasi TextToSpeech dengan AudioAttributes agar bisa mengikuti tipe volume yang dipilih
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
            } else {
                // Ambil daftar suara (voices) yang tersedia di mesin TTS perangkat
                loadAvailableVoices();
            }
        }
    }

    private void loadAvailableVoices() {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices != null) {
                for (Voice voice : voices) {
                    // Filter yang mendukung bahasa Indonesia atau lokal terkait
                    if (voice.getLocale() != null && voice.getLocale().getLanguage().equals("id")) {
                        voiceList.add(voice);
                        voiceNames.add(voice.getName());
                    }
                }
                voiceAdapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        if (tts == null) return;

        // 1. Terapkan Suara (Voice) yang dipilih dari Spinner
        int selectedVoicePos = spinnerVoice.getSelectedItemPosition();
        if (selectedVoicePos > 0 && selectedVoicePos - 1 < voiceList.size()) {
            tts.setVoice(voiceList.get(selectedVoicePos - 1));
        }

        // 2. Terapkan Tipe Audio Stream berdasarkan pilihan Spinner Volume
        int selectedVolumeIndex = spinnerVolume.getSelectedItemPosition();
        int audioStreamType = AudioManager.STREAM_MUSIC; // Default Media
        int audioAttributesUsage = AudioAttributes.USAGE_MEDIA;

        switch (selectedVolumeIndex) {
            case 0: // Media
                audioStreamType = AudioManager.STREAM_MUSIC;
                audioAttributesUsage = AudioAttributes.USAGE_MEDIA;
                break;
            case 1: // Notifikasi
                audioStreamType = AudioManager.STREAM_NOTIFICATION;
                audioAttributesUsage = AudioAttributes.USAGE_NOTIFICATION;
                break;
            case 2: // Alarm
                audioStreamType = AudioManager.STREAM_ALARM;
                audioAttributesUsage = AudioAttributes.USAGE_ALARM;
                break;
            case 3: // Nada Dering / Ring
                audioStreamType = AudioManager.STREAM_RING;
                audioAttributesUsage = AudioAttributes.USAGE_VOICE_COMMUNICATION;
                break;
        }

        // Konfigurasi AudioAttributes untuk TTS modern
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(audioAttributesUsage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        tts.setAudioAttributes(audioAttributes);

        // --- PERHITUNGAN WAKTU & TANGGAL ---
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

        // Ucapkan teks
        tts.speak(teksUcapan, TextToSpeech.QUEUE_FLUSH, null, null);
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