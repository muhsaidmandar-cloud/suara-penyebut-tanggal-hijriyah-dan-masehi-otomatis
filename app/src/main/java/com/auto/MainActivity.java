package com.navigasi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener, SensorEventListener, TextToSpeech.OnInitListener {
    
    private TextView info;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private boolean hasGravity = false;
    private boolean hasGeomagnetic = false;
    private float currentAzimuth = 0.0f; // Arah hadap kompas (0 - 360 derajat)

    private TextToSpeech tts;
    private boolean isTtsReady = false;

    // Pengaturan Suara & TTS
    private float kecepatanBicara = 1.0f; 
    private float nadaBicara = 1.0f;     
    private String targetTtsEngine = null; 
    private String namaEngineAktif = "Default Sistem";

    // Pilihan Stream Audio
    private int selectedAudioStream = AudioManager.STREAM_MUSIC;
    private String namaStreamAktif = "Media / Musik";

    // Penyimpanan Multi-Lokasi
    private List<LokasiTersimpan> daftarLokasiTersimpan = new ArrayList<>();
    private LokasiTersimpan lokasiNavigasiAktif = null;
    
    private boolean isNavigating = false;
    
    // Status Eksplorasi Real-Time dengan Kompas Akurat
    private boolean isEksplorasiFiturAktif = false;
    private boolean sedangMemindaiOtomatis = false;
    private double lastExplorationLat = 0.0;
    private double lastExplorationLon = 0.0;
    
    private List<String> riwayatTempatDiumumkan = new ArrayList<>();
    private List<InstruksiRute> daftarInstruksi = new ArrayList<>();
    private int indexInstruksiAktif = 0;

    public static class LokasiTersimpan {
        String nama;
        double lat;
        double lon;

        public LokasiTersimpan(String nama, double lat, double lon) {
            this.nama = nama;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static class InstruksiRute {
        double lat;
        double lon;
        String pesanPanduan;
        boolean sudahDiumumkan = false;

        public InstruksiRute(double lat, double lon, String pesanPanduan) {
            this.lat = lat;
            this.lon = lon;
            this.pesanPanduan = pesanPanduan;
        }
    }

    @Override 
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(selectedAudioStream);
        muatDataLokasiDariPrefs();
        inisialisasiSensorKompas();
        inisialisasiTtsMandiri();
        tampilkanMenuUtama();
    }

    private void inisialisasiSensorKompas() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
    }

    private void mulaiSensorKompas() {
        if (sensorManager != null) {
            if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            if (magnetometer != null) sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void hentikanSensorKompas() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, event.values.length);
            hasGravity = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, event.values.length);
            hasGeomagnetic = true;
        }

        if (hasGravity && hasGeomagnetic) {
            float[] R = new float[9];
            float[] I = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
            if (success) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                float azimuthInRadians = orientation[0];
                float azimuthInDegress = (float) Math.toDegrees(azimuthInRadians);
                currentAzimuth = (azimuthInDegress + 360) % 360; // Nilai arah hadap 0 - 360 derajat
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void muatDataLokasiDariPrefs() {
        daftarLokasiTersimpan.clear();
        SharedPreferences prefs = getSharedPreferences("NavigasiPrefs", MODE_PRIVATE);
        int jumlah = prefs.getInt("jumlah_lokasi", 0);
        for (int i = 0; i < jumlah; i++) {
            String nama = prefs.getString("nama_" + i, "Lokasi " + (i + 1));
            double lat = Double.longBitsToDouble(prefs.getLong("lat_" + i, 0));
            double lon = Double.longBitsToDouble(prefs.getLong("lon_" + i, 0));
            daftarLokasiTersimpan.add(new LokasiTersimpan(nama, lat, lon));
        }
    }

    private void simpanDataLokasiKePrefs() {
        SharedPreferences prefs = getSharedPreferences("NavigasiPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("jumlah_lokasi", daftarLokasiTersimpan.size());
        for (int i = 0; i < daftarLokasiTersimpan.size(); i++) {
            LokasiTersimpan loc = daftarLokasiTersimpan.get(i);
            editor.putString("nama_" + i, loc.nama);
            editor.putLong("lat_" + i, Double.doubleToRawLongBits(loc.lat));
            editor.putLong("lon_" + i, Double.doubleToRawLongBits(loc.lon));
        }
        editor.apply();
    }

    private void inisialisasiTtsMandiri() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {}
        }
        try {
            if (targetTtsEngine != null && !targetTtsEngine.isEmpty()) {
                tts = new TextToSpeech(this, this, targetTtsEngine);
            } else {
                tts = new TextToSpeech(this, this);
            }
        } catch (Exception e) {
            tts = new TextToSpeech(this, this);
        }
    }

    private void tampilkanMenuUtama() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(32, 32, 32, 32);
        
        TextView title = new TextView(this);
        title.setText("Navigasi Kompas Akurat");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        
        info = new TextView(this);
        if (lokasiNavigasiAktif != null) {
            info.setText("Tujuan Aktif:\n" + lokasiNavigasiAktif.nama + "\nLat: " + lokasiNavigasiAktif.lat + ", Lon: " + lokasiNavigasiAktif.lon);
        } else {
            info.setText("Total Lokasi Tersimpan: " + daftarLokasiTersimpan.size() + "\nSistem Kompas Siap.");
        }
        info.setTextSize(15);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 16, 0, 24);
        box.addView(info);
        
        // --- TOMBOL DI MANA SAYA SEKARANG ---
        Button btnCekPosisi = new Button(this);
        btnCekPosisi.setText("DI MANA SAYA SEKARANG");
        btnCekPosisi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cekPosisiAlamatLengkap();
            }
        });
        box.addView(btnCekPosisi);

        // --- PENCARIAN TUJUAN ---
        Button btnCariLokasi = new Button(this);
        btnCariLokasi.setText("CARI LOKASI TUJUAN (TEKS)");
        btnCariLokasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogPencarianLokasi();
            }
        });
        box.addView(btnCariLokasi);

        // --- SAKLAR EKSPLORASI KOMPAS REAL-TIME ---
        final Button btnToggleEksplorasi = new Button(this);
        updateTeksTombolEksplorasi(btnToggleEksplorasi);
        btnToggleEksplorasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isEksplorasiFiturAktif = !isEksplorasiFiturAktif;
                updateTeksTombolEksplorasi(btnToggleEksplorasi);
                if (isEksplorasiFiturAktif) {
                    riwayatTempatDiumumkan.clear();
                    lastExplorationLat = 0.0;
                    lastExplorationLon = 0.0;
                    mulaiSensorKompas();
                    mulaiMendengarkanGPS();
                    ucapkanSuara("Eksplorasi kompas diaktifkan.");
                    
                    try {
                        if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (loc != null) {
                                new EksplorasiKompasTask().execute(loc.getLatitude(), loc.getLongitude());
                            }
                        }
                    } catch (Exception e) {}
                } else {
                    hentikanSensorKompas();
                    ucapkanSuara("Eksplorasi kompas dinonaktifkan.");
                }
            }
        });
        box.addView(btnToggleEksplorasi);

        // --- SIMPAN LOKASI ---
        Button btnSimpan = new Button(this);
        btnSimpan.setText("SIMPAN LOKASI SAAT INI");
        btnSimpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogSimpanLokasiCustom("Lokasi Saya " + (daftarLokasiTersimpan.size() + 1));
            }
        });
        box.addView(btnSimpan);

        // --- KELOLA LOKASI ---
        Button btnEditLokasi = new Button(this);
        btnEditLokasi.setText("KELOLA LOKASI TERSIMPAN (" + daftarLokasiTersimpan.size() + ")");
        btnEditLokasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogKelolaDaftarLokasi();
            }
        });
        box.addView(btnEditLokasi);

        // --- NAVIGASI ---
        Button btnNavigasi = new Button(this);
        btnNavigasi.setText("MULAI NAVIGASI BELOKAN");
        btnNavigasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiNavigasiTersimpan();
            }
        });
        box.addView(btnNavigasi);

        // --- HENTIKAN NAVIGASI ---
        Button btnHentikanNavigasi = new Button(this);
        btnHentikanNavigasi.setText("HENTIKAN NAVIGASI");
        btnHentikanNavigasi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hentikanNavigasiTotal();
            }
        });
        box.addView(btnHentikanNavigasi);

        // --- PENGATURAN SUARA ---
        Button btnPengaturanTts = new Button(this);
        btnPengaturanTts.setText("PENGATURAN SUARA & VOLUME");
        btnPengaturanTts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanHalamanPengaturanTts();
            }
        });
        box.addView(btnPengaturanTts);
        
        setContentView(box);
    }

    private void hentikanNavigasiTotal() {
        isNavigating = false;
        daftarInstruksi.clear();
        indexInstruksiAktif = 0;
        ucapkanSuara("Navigasi dihentikan.");
        if (lokasiNavigasiAktif != null) {
            info.setText("Navigasi Berhenti.\nTujuan Aktif: " + lokasiNavigasiAktif.nama);
        } else {
            info.setText("Navigasi Berhenti. Belum ada tujuan dipilih.");
        }
    }

    private void updateTeksTombolEksplorasi(Button btn) {
        if (isEksplorasiFiturAktif) {
            btn.setText("EKSPLORASI KOMPAS: AKTIF");
        } else {
            btn.setText("EKSPLORASI KOMPAS: NONAKTIF");
        }
    }

    private void tampilkanDialogPencarianLokasi() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cari Lokasi Tujuan");
        
        final EditText input = new EditText(this);
        input.setHint("Contoh: Masjid Raya Sinjai");
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Cari", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String queryPencarian = input.getText().toString().trim();
                if (!queryPencarian.isEmpty()) {
                    ucapkanSuara("Mencari lokasi " + queryPencarian + ". Mohon tunggu.");
                    info.setText("Mencari lokasi: " + queryPencarian + "...");
                    new CariLokasiTask().execute(queryPencarian);
                } else {
                    ucapkanSuara("Nama lokasi tidak boleh kosong.");
                }
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
        ucapkanSuara("Silakan ketik nama lokasi tujuan.");
    }

    private class CariLokasiTask extends AsyncTask<String, Void, HasilPencarian> {
        @Override
        protected HasilPencarian doInBackground(String... params) {
            try {
                String query = params[0];
                String urlStr = "https://nominatim.openstreetmap.org/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=json&limit=1&addressdetails=1";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "NavigasiAplikasiAndroid");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONArray jsonArray = new JSONArray(sb.toString());
                if (jsonArray.length() > 0) {
                    JSONObject obj = jsonArray.getJSONObject(0);
                    return new HasilPencarian(obj.getDouble("lat"), obj.getDouble("lon"), obj.getString("display_name"));
                }
            } catch (Exception e) {}
            return null;
        }

        @Override
        protected void onPostExecute(final HasilPencarian hasil) {
            if (hasil != null) {
                ucapkanSuara("Lokasi ditemukan: " + hasil.namaPendek() + ". Pilih opsi.");
                info.setText("Lokasi Ditemukan:\n" + hasil.displayName);
                
                AlertDialog.Builder konfirmasi = new AlertDialog.Builder(MainActivity.this);
                konfirmasi.setTitle("Hasil Pencarian Lokasi");
                konfirmasi.setMessage("Ditemukan:\n" + hasil.displayName);
                
                konfirmasi.setPositiveButton("Simpan ke Daftar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        daftarLokasiTersimpan.add(new LokasiTersimpan(hasil.namaPendek(), hasil.lat, hasil.lon));
                        simpanDataLokasiKePrefs();
                        ucapkanSuara("Lokasi disimpan.");
                    }
                });

                konfirmasi.setNeutralButton("Langsung Navigasi", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        lokasiNavigasiAktif = new LokasiTersimpan(hasil.namaPendek(), hasil.lat, hasil.lon);
                        mulaiNavigasiTersimpan();
                    }
                });
                konfirmasi.setNegativeButton("Abaikan", null);
                konfirmasi.show();
            } else {
                ucapkanSuara("Lokasi tidak ditemukan.");
                info.setText("Pencarian gagal.");
            }
        }
    }

    private static class HasilPencarian {
        double lat, lon;
        String displayName;
        public HasilPencarian(double lat, double lon, String displayName) {
            this.lat = lat; this.lon = lon; this.displayName = displayName;
        }
        public String namaPendek() {
            if (displayName != null && displayName.contains(",")) return displayName.split(",")[0];
            return displayName;
        }
    }

    private void tampilkanDialogSimpanLokasiCustom(final String defaultNama) {
        Location loc = null;
        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {}

        if (loc == null) {
            ucapkanSuara("GPS belum aktif atau sinyal tidak ditemukan.");
            return;
        }

        final double currentLat = loc.getLatitude();
        final double currentLon = loc.getLongitude();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Simpan Lokasi Baru");
        final EditText input = new EditText(this);
        input.setText(defaultNama);
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String namaLokasi = input.getText().toString().trim();
                if (namaLokasi.isEmpty()) namaLokasi = "Lokasi Tersimpan";
                daftarLokasiTersimpan.add(new LokasiTersimpan(namaLokasi, currentLat, currentLon));
                simpanDataLokasiKePrefs();
                lokasiNavigasiAktif = daftarLokasiTersimpan.get(daftarLokasiTersimpan.size() - 1);
                ucapkanSuara("Lokasi " + namaLokasi + " disimpan.");
                info.setText("Lokasi Tersimpan:\n" + namaLokasi);
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
        ucapkanSuara("Masukkan nama lokasi.");
    }

    private void tampilkanDialogKelolaDaftarLokasi() {
        if (daftarLokasiTersimpan.isEmpty()) {
            ucapkanSuara("Belum ada lokasi tersimpan.");
            return;
        }
        final CharSequence[] daftarNama = new CharSequence[daftarLokasiTersimpan.size()];
        for (int i = 0; i < daftarLokasiTersimpan.size(); i++) {
            daftarNama[i] = (i + 1) + ". " + daftarLokasiTersimpan.get(i).nama;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Kelola Lokasi (" + daftarLokasiTersimpan.size() + ")");
        builder.setItems(daftarNama, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, final int pilihanIndex) {
                final LokasiTersimpan dipilih = daftarLokasiTersimpan.get(pilihanIndex);
                AlertDialog.Builder opsiItem = new AlertDialog.Builder(MainActivity.this);
                opsiItem.setTitle(dipilih.nama);
                CharSequence[] aksi = {"Jadikan Tujuan Navigasi", "Hapus Lokasi Ini"};
                opsiItem.setItems(aksi, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int a) {
                        if (a == 0) {
                            lokasiNavigasiAktif = dipilih;
                            ucapkanSuara("Tujuan aktif: " + dipilih.nama);
                            info.setText("Tujuan Aktif:\n" + dipilih.nama);
                        } else if (a == 1) {
                            daftarLokasiTersimpan.remove(pilihanIndex);
                            simpanDataLokasiKePrefs();
                            ucapkanSuara("Lokasi dihapus.");
                        }
                    }
                });
                opsiItem.show();
            }
        });
        builder.setNegativeButton("Tutup", null);
        builder.show();
    }

    private void tampilkanHalamanPengaturanTts() {
        LinearLayout boxTts = new LinearLayout(this);
        boxTts.setOrientation(LinearLayout.VERTICAL);
        boxTts.setGravity(Gravity.CENTER);
        boxTts.setPadding(32, 32, 32, 32);

        TextView titleTts = new TextView(this);
        titleTts.setText("Pengaturan Suara");
        titleTts.setTextSize(22);
        titleTts.setTextColor(Color.BLACK);
        titleTts.setGravity(Gravity.CENTER);
        boxTts.addView(titleTts);

        final TextView infoTts = new TextView(this);
        infoTts.setText("Engine: " + namaEngineAktif + "\nStream: " + namaStreamAktif + "\nKecepatan: " + kecepatanBicara + "x");
        infoTts.setTextSize(15);
        infoTts.setGravity(Gravity.CENTER);
        infoTts.setPadding(0, 24, 0, 24);
        boxTts.addView(infoTts);

        Button btnPilihStream = new Button(this);
        btnPilihStream.setText("PILIH STREAM VOLUME");
        btnPilihStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanDialogPilihanStream(infoTts);
            }
        });
        boxTts.addView(btnPilihStream);

        Button btnLebihCepat = new Button(this);
        btnLebihCepat.setText("UBAH KECEPATAN BICARA");
        btnLebihCepat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kecepatanBicara = (kecepatanBicara < 2.0f) ? (kecepatanBicara + 0.25f) : 1.0f;
                terapkanSetelanTts();
                ucapkanSuara("Kecepatan " + kecepatanBicara);
                infoTts.setText("Engine: " + namaEngineAktif + "\nStream: " + namaStreamAktif + "\nKecepatan: " + kecepatanBicara + "x");
            }
        });
        boxTts.addView(btnLebihCepat);

        Button btnKembali = new Button(this);
        btnKembali.setText("KEMBALI");
        btnKembali.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanMenuUtama();
            }
        });
        boxTts.addView(btnKembali);

        setContentView(boxTts);
    }

    private void tampilkanDialogPilihanStream(final TextView infoTts) {
        final String[] namaStreamList = {"Media / Musik", "Volume Dering", "Volume Notifikasi"};
        final int[] streamCodeList = {AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pilih Stream Volume");
        builder.setItems(namaStreamList, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedAudioStream = streamCodeList[which];
                namaStreamAktif = namaStreamList[which];
                setVolumeControlStream(selectedAudioStream);
                infoTts.setText("Engine: " + namaEngineAktif + "\nStream: " + namaStreamAktif + "\nKecepatan: " + kecepatanBicara + "x");
                ucapkanSuara("Stream diubah ke " + namaStreamAktif);
            }
        });
        builder.show();
    }

    private void terapkanSetelanTts() {
        if (tts != null) {
            try {
                tts.setSpeechRate(kecepatanBicara);
                tts.setPitch(nadaBicara);
            } catch (Exception e) {}
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                int result = tts.setLanguage(new Locale("id", "ID"));
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true;
                    terapkanSetelanTts();
                }
            } catch (Exception e) {}
        }
    }

    private void ucapkanSuara(String teks) {
        if (isTtsReady && tts != null) {
            try {
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, selectedAudioStream);
                tts.speak(teks, TextToSpeech.QUEUE_FLUSH, params, null);
            } catch (Exception e) {
                try { tts.speak(teks, TextToSpeech.QUEUE_FLUSH, null, null); } catch (Exception ex) {}
            }
        }
    }

    private void mulaiMendengarkanGPS() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1.5f, this);
            }
        } catch (SecurityException e) {}
    }

    private void cekPosisiAlamatLengkap() {
        Location loc = null;
        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {}

        if (loc == null) {
            ucapkanSuara("Sinyal GPS belum siap.");
            return;
        }

        ucapkanSuara("Mengambil alamat lengkap...");
        new CekAlamatTask().execute(loc.getLatitude(), loc.getLongitude(), (double)loc.getAccuracy());
    }

    private class CekAlamatTask extends AsyncTask<Double, Void, String> {
        double lat, lon, akurasi;
        @Override
        protected String doInBackground(Double... params) {
            lat = params[0]; lon = params[1]; akurasi = params[2];
            try {
                String urlStr = "https://nominatim.openstreetmap.org/reverse?lat=" + lat + "&lon=" + lon + "&format=json&addressdetails=1";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "LazarilloCloneAndroid/1.0");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject json = new JSONObject(sb.toString());
                if (json.has("display_name")) return json.getString("display_name");
            } catch (Exception e) {}
            return null;
        }

        @Override
        protected void onPostExecute(String alamatLengkap) {
            if (alamatLengkap != null) {
                String pengumuman = "Anda berada di " + alamatLengkap + ".";
                ucapkanSuara(pengumuman);
                info.setText("Posisi Anda:\n" + alamatLengkap);
            } else {
                ucapkanSuara("Gagal mengambil detail alamat.");
            }
        }
    }

    // --- EKSPLORASI DENGAN SISTEM KOMPAS PRESISI LAZARILLO ---
    private class EksplorasiKompasTask extends AsyncTask<Double, Void, List<TempatInfoKompas>> {
        double currentLat, currentLon;

        @Override
        protected List<TempatInfoKompas> doInBackground(Double... coords) {
            currentLat = coords[0];
            currentLon = coords[1];
            List<TempatInfoKompas> hasil = new ArrayList<>();
            try {
                String query = "[out:json][timeout:3];(" +
                               "node(around:35," + currentLat + "," + currentLon + ")[name];" +
                               "way(around:35," + currentLat + "," + currentLon + ")[name];" +
                               ");out body 8;";
                
                String urlStr = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8");
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "LazarilloCloneAndroid/1.0");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray elements = json.getJSONArray("elements");
                
                for (int i = 0; i < elements.length(); i++) {
                    JSONObject el = elements.getJSONObject(i);
                    if (el.has("tags")) {
                        JSONObject tags = el.getJSONObject("tags");
                        if (tags.has("name")) {
                            String namaTempat = tags.getString("name");
                            if (!riwayatTempatDiumumkan.contains(namaTempat)) {
                                double itemLat = currentLat;
                                double itemLon = currentLon;
                                if (el.has("lat") && el.has("lon")) {
                                    itemLat = el.getDouble("lat");
                                    itemLon = el.getDouble("lon");
                                }
                                hasil.add(new TempatInfoKompas(namaTempat, itemLat, itemLon));
                            }
                        }
                    }
                }
            } catch (Exception e) {}
            return hasil;
        }

        @Override
        protected void onPostExecute(List<TempatInfoKompas> result) {
            sedangMemindaiOtomatis = false;
            if (result != null && !result.isEmpty()) {
                StringBuilder infoBuilder = new StringBuilder();
                for (TempatInfoKompas t : result) {
                    float[] distArr = new float[1];
                    Location.distanceBetween(currentLat, currentLon, t.lat, t.lon, distArr);
                    int jarakMeter = (int) distArr[0];
                    if (jarakMeter == 0) jarakMeter = 3;

                    // --- MATEMATIKA KOMPAS: MENGHITUNG SUDUT RELATIF ---
                    // 1. Hitung sudut azimuth dari lokasi Anda ke objek tempat
                    double dLon = Math.toRadians(t.lon - currentLon);
                    double y = Math.sin(dLon) * Math.cos(Math.toRadians(t.lat));
                    double x = Math.cos(Math.toRadians(currentLat)) * Math.sin(Math.toRadians(t.lat)) -
                               Math.sin(Math.toRadians(currentLat)) * Math.cos(Math.toRadians(t.lat)) * Math.cos(dLon);
                    double bearingToTarget = Math.toDegrees(Math.atan2(y, x));
                    bearingToTarget = (bearingToTarget + 360) % 360;

                    // 2. Bandingkan sudut target dengan arah hadap kompas HP Anda (currentAzimuth)
                    double selisihSudut = bearingToTarget - currentAzimuth;
                    while (selisihSudut < -180) selisihSudut += 360;
                    while (selisihSudut > 180) selisihSudut -= 360;

                    // 3. Tentukan posisi presisi berdasarkan selisih sudut kompas
                    String posisiAkurat = "di depan Anda";
                    if (selisihSudut > 45 && selisihSudut <= 135) {
                        posisiAkurat = "di sebelah kanan Anda";
                    } else if (selisihSudut > 135 || selisihSudut < -135) {
                        posisiAkurat = "di belakang Anda";
                    } else if (selisihSudut >= -135 && selisihSudut < -45) {
                        posisiAkurat = "di sebelah kiri Anda";
                    }

                    // Format pengumuman cerewet persis Lazarillo dengan kompas akurat
                    String pengumumanItem = t.nama + ", " + jarakMeter + " meter " + posisiAkurat + ". ";
                    ucapkanSuara(pengumumanItem);
                    infoBuilder.append(pengumumanItem).append("\n");

                    if (!riwayatTempatDiumumkan.contains(t.nama)) {
                        riwayatTempatDiumumkan.add(t.nama);
                        if (riwayatTempatDiumumkan.size() > 30) riwayatTempatDiumumkan.remove(0);
                    }
                }
                info.setText("Eksplorasi Kompas Aktif:\n" + infoBuilder.toString());
            }
        }
    }

    private static class TempatInfoKompas {
        String nama;
        double lat, lon;
        public TempatInfoKompas(String nama, double lat, double lon) {
            this.nama = nama; this.lat = lat; this.lon = lon;
        }
    }

    private void mulaiNavigasiTersimpan() {
        if (lokasiNavigasiAktif == null) {
            ucapkanSuara("Pilih tujuan navigasi terlebih dahulu.");
            return;
        }
        isNavigating = true;
        indexInstruksiAktif = 0;
        daftarInstruksi.clear();
        
        ucapkanSuara("Mengunduh rute ke " + lokasiNavigasiAktif.nama + "...");
        Location loc = null;
        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {}

        double startLat = (loc != null) ? loc.getLatitude() : lokasiNavigasiAktif.lat - 0.001;
        double startLon = (loc != null) ? loc.getLongitude() : lokasiNavigasiAktif.lon - 0.001;

        new AmbilRuteTask().execute(startLat, startLon, lokasiNavigasiAktif.lat, lokasiNavigasiAktif.lon);
    }

    private class AmbilRuteTask extends AsyncTask<Double, Void, List<InstruksiRute>> {
        @Override
        protected List<InstruksiRute> doInBackground(Double... coords) {
            List<InstruksiRute> hasil = new ArrayList<>();
            try {
                String urlStr = "https://router.project-osrm.org/route/v1/walking/" + coords[1] + "," + coords[0] + ";" + coords[3] + "," + coords[2] + "?overview=false&steps=true&geometries=geojson&language=id";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray routes = json.getJSONArray("routes");
                if (routes.length() > 0) {
                    JSONArray steps = routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONArray("steps");
                    for (int i = 0; i < steps.length(); i++) {
                        JSONObject step = steps.getJSONObject(i);
                        String maneuver = step.getJSONObject("maneuver").getString("instruction");
                        JSONArray locArr = step.getJSONObject("maneuver").getJSONArray("location");
                        hasil.add(new InstruksiRute(locArr.getDouble(1), locArr.getDouble(0), maneuver));
                    }
                }
            } catch (Exception e) {}
            return hasil;
        }

        @Override
        protected void onPostExecute(List<InstruksiRute> result) {
            if (result != null && !result.isEmpty()) {
                daftarInstruksi = result;
                mulaiMendengarkanGPS();
                mulaiSensorKompas();
                ucapkanSuara("Rute siap. Navigasi dimulai.");
            } else {
                if (lokasiNavigasiAktif != null) {
                    daftarInstruksi.add(new InstruksiRute(lokasiNavigasiAktif.lat, lokasiNavigasiAktif.lon, "Tuju titik akhir."));
                }
                mulaiMendengarkanGPS();
                mulaiSensorKompas();
                ucapkanSuara("Navigasi garis lurus dimulai.");
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            double currentLat = location.getLatitude();
            double currentLon = location.getLongitude();
            
            if (isEksplorasiFiturAktif && !isNavigating && !sedangMemindaiOtomatis) {
                float[] jarakPindah = new float[1];
                if (lastExplorationLat == 0.0) {
                    lastExplorationLat = currentLat;
                    lastExplorationLon = currentLon;
                }
                Location.distanceBetween(lastExplorationLat, lastExplorationLon, currentLat, currentLon, jarakPindah);
                if (jarakPindah[0] >= 8.0f) {
                    lastExplorationLat = currentLat;
                    lastExplorationLon = currentLon;
                    sedangMemindaiOtomatis = true;
                    new EksplorasiKompasTask().execute(currentLat, currentLon);
                }
            }

            if (isNavigating && lokasiNavigasiAktif != null) {
                float[] jarakTotalArr = new float[1];
                Location.distanceBetween(currentLat, currentLon, lokasiNavigasiAktif.lat, lokasiNavigasiAktif.lon, jarakTotalArr);
                
                if (!daftarInstruksi.isEmpty() && indexInstruksiAktif < daftarInstruksi.size()) {
                    InstruksiRute instruksi = daftarInstruksi.get(indexInstruksiAktif);
                    float[] hasilJarak = new float[1];
                    Location.distanceBetween(currentLat, currentLon, instruksi.lat, instruksi.lon, hasilJarak);
                    
                    if (hasilJarak[0] <= 30.0f && !instruksi.sudahDiumumkan) {
                        ucapkanSuara((int)hasilJarak[0] + " meter lagi di depan, " + instruksi.pesanPanduan);
                        instruksi.sudahDiumumkan = true;
                    }

                    if (hasilJarak[0] <= 4.0f) {
                        indexInstruksiAktif++;
                        if (indexInstruksiAktif < daftarInstruksi.size()) {
                            ucapkanSuara("Berikutnya: " + daftarInstruksi.get(indexInstruksiAktif).pesanPanduan);
                        } else {
                            ucapkanSuara("Anda telah tiba di tujuan " + lokasiNavigasiAktif.nama + ".");
                            isNavigating = false;
                        }
                    }
                }
            }
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) { ucapkanSuara("GPS dimatikan."); }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception e) {}
        }
        if (locationManager != null) locationManager.removeUpdates(this);
        hentikanSensorKompas();
        super.onDestroy();
    }
}
