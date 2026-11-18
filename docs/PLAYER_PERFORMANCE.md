# Analisis performa: Home dan layar player

Tanggal: 2026-10-22

Dokumen ini merangkum penelusuran keluhan "app terasa berat dan HP panas saat
idle", dilakukan di perangkat nyata dengan `adb`. Angka di bawah adalah hasil
ukur, bukan perkiraan. Hal yang belum terbukti ditandai jelas.

## Ringkasan

- **Navbar liquid glass bukan penyebab.** Di build non-debug, scroll Home di
  Swell lebih lancar daripada versi lama tanpa glass (p90 28 ms vs 38 ms).
- **Rasa berat saat scroll berasal dari build debug.** Compose di build debug
  tanpa R8 dan baseline profile jauh lebih lambat (p90 89 ms).
- **Panas saat idle berasal dari dua hal yang menggambar terus tanpa henti:**
  1. Kartu "Sign in to YouTube Music" di Home (gradien mesh yang bergerak). Home
     idle 90 fps, sekarang 0 fps.
  2. Shimmer label "Lossless" di player yang tetap jalan saat lagu dipause.
     Player pause 92 fps dan ~100% CPU, sekarang 0 fps dan 1% CPU.
- **Belum selesai:** saat lagu *play*, player masih ~63 fps dan ~95% CPU.
  Baris lirik dan shimmer adalah dua sumber frame terpisah, tetapi masih ada
  sumber lain yang meminta sekitar 30 fps. Penyebabnya belum ditemukan.

## Cara mengukur

Perangkat: Realme RMX3630 (MediaTek Helio G99, GPU Mali-G57, layar 90 Hz,
Android 14).

Tiga build terpasang berdampingan:

| Paket | Keterangan |
|---|---|
| `app.swell.music.dev` | Debug (`debuggable`), flavor `dev` |
| `com.music.bitchord` | Release lama 1.8.1, sebelum liquid glass |
| `app.swell.music.dev.benchmark` | Release-like: R8 aktif, non-debuggable, flavor `dev` |

Alat dan metrik:

- `dumpsys gfxinfo <paket>`: jumlah frame, persentase jank, p50/p90/p99.
  fps idle dihitung dari jumlah frame yang dirender dalam 8 sampai 12 detik tanpa
  disentuh.
- `top -b -p <pid>`: CPU proses (100% sama dengan satu core penuh), diambil rata-rata
  tiga sampel. `top -H` untuk per thread.
- `simpleperf record --app` pada build debug, untuk melihat thread dan simbol
  native yang panas.
- `/sys/class/power_supply/battery/current_now`, satuan diasumsikan mA, hanya
  sebagai indikasi kasar.
- Screenshot sebelum dan sesudah tiap pengukuran untuk memastikan layar yang
  diukur memang layar yang dimaksud (lihat bagian jebakan).

Skenario scroll: 6 kali swipe naik dan 6 kali swipe turun di Home.

## Hasil: scroll Home

Layar bersih (dialog update sudah ditutup):

| Build | Jank | p50 | p90 | p99 | Slow UI thread |
|---|---|---|---|---|---|
| Debug | 19,1% | 26 ms | 89 ms | 200 ms | 90 |
| Release lama (tanpa glass) | 9,2% | 20 ms | 38 ms | 73 ms | 58 |
| Benchmark Swell (R8) | 1,9% | 18 ms | 28 ms | 46 ms | 19 |

Kesimpulan: build non-debug dengan liquid glass tidak lebih lambat dari versi
lama. Penilaian kelancaran harus memakai varian `devBenchmark`, bukan debug.

Batas pengukuran: hanya halaman Home dengan satu pola scroll. Transisi
collapse/expand navbar dan halaman lain belum diukur.

## Home idle: kartu sign-in

`SignInBanner` (di `ui/components/Common.kt`) memakai
`MeshGradientBackground(continuous = true, blurRadius = 36.dp)` dengan warna yang
bergilir (`cyclingMeshPalette`). Efeknya blur 36 dp digambar ulang tiap frame
selama Home terbuka dan pengguna belum login.

| Kondisi | fps idle |
|---|---|
| Home, kartu lama, musik play | ~90 |
| Home, kartu lama, musik pause | ~90 |
| Explore dan Library, musik play atau pause | 0 |
| Home, kartu baru | **0** |

Kode kartu ini lebih tua dari commit liquid glass, jadi ini bukan regresi dari
glass. Komentar di `MeshGradient.kt` sendiri menyebut orbit tanpa henti dibuang
dari layar penuh karena itu hal paling mahal di app, tetapi kartu kecil ini lolos.

Perbaikan: kartu diganti menjadi kartu datar (judul dan tombol Google putih,
tanpa mesh, tanpa animasi selain efek tekan).

## Player saat pause: shimmer Lossless

Pengukuran player terbuka, 10 detik idle:

| Kondisi | Frame | fps | CPU | Jank |
|---|---|---|---|---|
| Play, sebelum perbaikan | 919 | ~92 | 78 sampai 103% | 0% |
| Pause, sebelum perbaikan | 916 | ~92 | 78 sampai 103% | 0% |
| Pause, sesudah perbaikan | 0 | **0** | **1%** | – |

Tidak ada jank (p50 11 sampai 12 ms), tetapi app tidak pernah berhenti
menggambar walau lagu dipause. Penyebab: `ShimmerText` di
`ui/player/PlayerControls.kt` memakai `rememberInfiniteTransition` 3 detik yang
berulang tanpa henti, tidak peduli lagu sedang jalan.

Perbaikan: shimmer hanya menyapu saat lagu play dan tidak di mode "Reduce
animation". Saat berhenti, teks diam dengan warna biasa. Status play/pause
diteruskan lewat `LocalPlayerPlaying`, yang disediakan di `NowPlayingScreen.kt`.

## Player saat play: masih terbuka

Setelah perbaikan shimmer, player saat play masih berat:

| Ukuran | Nilai |
|---|---|
| CPU | 94 sampai 98% |
| fps | ~63 (756 frame dalam 12 detik) |
| Thread panas | RenderThread 38%, main thread 21%, driver GPU Mali ~16% |
| Arus baterai | ~631 sampai 680, vs ~400 sampai 440 di Home |

Pembandingnya: Home dengan musik play hanya 3 sampai 8% CPU dan 0 fps. Jadi
beban ini khas layar player, bukan pemutaran musik itu sendiri.

### Percobaan pembuktian

Semua dilakukan di build benchmark dengan player terbuka (diverifikasi lewat
screenshot), lalu kode percobaan dikembalikan.

| Percobaan | CPU | fps |
|---|---|---|
| Baseline | 93 sampai 98% | ~63 |
| E1: loop `withFrameNanos` lirik dimatikan | 93 sampai 94% | ~63 |
| E2: shimmer dimatikan | 83 sampai 85% | 55 sampai 62 |
| E3: lirik dan shimmer dimatikan bersamaan | 108 sampai 114% | **31 sampai 32** |

Yang bisa disimpulkan:

- Kalau ada beberapa sumber frame sekaligus, mematikan satu sumber tidak
  menurunkan fps karena sumber lain masih meminta frame. Itu sebabnya E1 dan E2
  sendiri tampak "tidak berpengaruh".
- Lirik dan shimmer adalah dua sumber terpisah (E3 menurunkan fps ke setengah).
- Masih ada satu sumber yang meminta sekitar 30 fps. Angka 30 fps mirip video,
  tetapi itu hanya dugaan.

Catatan E3: dijalankan pada lagu lain yang tidak punya label Lossless, jadi
perbandingannya tidak murni satu variabel.

### Profil native

`simpleperf` pada build debug (player terbuka, lagu play):

| Thread | Porsi |
|---|---|
| RenderThread | 39,7% |
| main | 27,2% |
| mali-cmar-backe (driver GPU) | 18,5% |

Simbol teratas di RenderThread menyebar ke banyak fungsi kecil (`memset`,
`free`, mutex, `RenderNode::prepareTreeImpl`) dan tidak didominasi satu efek
atau shader. Artinya biaya datang dari banyaknya gambar per frame, bukan dari satu
operasi grafis yang mahal.

Sampel `top -H` pada build benchmark, kondisi stabil: RenderThread 27%, driver
GPU 17%, main 14%, ExoPlayer playback 9%, MediaCodec 7%. Satu sampel menangkap
lonjakan periodik di decoder dan loader ExoPlayer (di atas 100% per thread).
Penyebab lonjakan itu belum ditelusuri.

### Hipotesis yang sudah gugur dan yang belum teruji

| Hipotesis | Status |
|---|---|
| Navbar liquid glass | Gugur untuk scroll dan idle (lihat atas) |
| Loop frame di navbar dan mini player | Gugur: spring yang berhenti sendiri |
| Penghitung posisi lagu | Gugur: dilaporkan sekitar 2 kali per detik, bukan per frame |
| Baris lirik saja | Gugur sebagai satu-satunya sumber (E1) |
| Shimmer saja | Gugur sebagai satu-satunya sumber (E2) |
| Cover bergerak (`animatedCanvas`, default aktif) | **Belum teruji** |
| Sumber lain (komponen player lain) | Belum diketahui |

Percobaan mematikan canvas pernah dicoba lewat edit prefs dan hasilnya tidak
valid, lihat bagian jebakan. Kesimpulan awal bahwa "artwork adalah video" juga
keliru: area yang dibandingkan ikut mencakup baris lirik yang sedang menyala kata
demi kata.

## Perubahan kode (belum di-commit)

| File | Perubahan |
|---|---|
| `sharedUi/.../ui/components/Common.kt` dan salinan di `desktopUi` | `SignInBanner` jadi kartu datar tanpa mesh |
| `sharedUi/.../ui/player/PlayerControls.kt` | `LocalPlayerPlaying`; `ShimmerText` hanya menyapu saat play dan tidak di "Reduce animation" |
| `sharedUi/.../ui/player/NowPlayingScreen.kt` | Menyediakan `LocalPlayerPlaying` dari `isPlaying` |

Catatan: salinan `PlayerControls.kt` di `desktopUi` sudah berbeda dari `sharedUi`
dan tidak diubah, jadi aplikasi desktop belum mendapat perbaikan shimmer.

Belum disentuh: commit liquid glass memaksa `reduceAnimation` dan
`reduceDynamicBlur` selalu `false` di `AppSettings.kt`, sehingga mode hemat tidak
bisa lagi mematikan animasi dan blur.

## Jebakan pengukuran

Hal-hal yang membuat beberapa pengukuran awal tidak valid dan harus diulang:

1. **Dialog "Software Update"** menutupi Home di tiap peluncuran baru. Swipe
   mengenai dialog, bukan list. Tutup dulu dengan "Remind Me Later".
2. **Tap pertama setelah app dibuka sering tidak membuka player.** Selalu ambil
   screenshot dan pastikan layar yang diukur benar sebelum dan sesudah. Jangan
   tap dua kali tanpa melihat layar: di dalam player titik yang sama mengenai
   slider volume.
3. **Tombol back dari Home menutup app.** Tap berikutnya jatuh ke launcher dan
   sempat membuka aplikasi lain. Jangan akhiri skrip dengan `KEYCODE_BACK`.
4. **Media key lewat adb tidak berfungsi kalau sesi media `NONE`** (setelah app
   dibuka ulang). Tekan tombol play di mini player.
5. **Lagu berganti di tengah jendela ukur** (lagu hampir habis) mengubah beban
   (label "Upgrading quality" berdenyut, lirik dimuat). Ukur setelah lagu stabil.
6. **Jangan memberi nama cadangan prefs `*.xml.bak`.** Itu nama cadangan internal
   Android SharedPreferences. Android menimpa file asli dengan `.bak` itu saat
   app dibuka, sehingga edit tidak pernah berlaku.
7. **`screenrecord` crash (segfault) di RMX3630**, jadi video tidak bisa dipakai
   untuk melihat apa yang berubah per frame.
8. **Heap Gradle 1536m membuat R8 gagal (`OutOfMemoryError`).** Build benchmark
   butuh sekitar 3 GB. Berhasil dengan `-Dorg.gradle.jvmargs="-Xmx3072m ..."`
   tanpa mengubah `gradle.properties`.
9. **Pengukuran terganggu kalau layar disentuh bersamaan.** Jangan pakai HP saat
   skrip jalan.

## Belum diukur

- Suhu perangkat secara langsung. Yang diukur hanya fps, CPU, dan arus baterai
  sebagai indikasi.
- Halaman selain Home, dan transisi collapse/expand navbar.
- Perilaku di akun yang sudah login (kartu sign-in tidak muncul).

## Langkah berikutnya

1. Matikan canvas dengan benar (lewat pengaturan di app, atau lewat nilai default
   di kode, bukan file `.bak`), lalu ukur player saat play dengan lirik dan
   shimmer dimatikan juga. Kalau fps turun ke sekitar 0, ketiganya terkonfirmasi
   dan bisa dipisah satu per satu.
2. Untuk baris lirik: pertimbangkan menjalankan loop frame hanya selama baris itu
   terlihat dan lagu play, atau menurunkan lajunya saat layar tidak menjadi fokus.
3. Pertimbangkan mengembalikan toggle "Reduce animation" dan "Reduce blur" agar
   pengguna HP lemah bisa mematikan efek.
4. Setelah itu ukur ulang scroll Home, transisi navbar, dan player, lalu commit.


## Pengukuran lanjutan: tablet Xiaomi, 22 Oktober 2026

Pengujian ini memakai APK benchmark yang sudah tersedia, tanpa build baru dan
tanpa perubahan kode aplikasi. Tablet terhubung lewat Wireless ADB; APK dipasang
dengan `adb install -r`. SHA-256 APK terpasang sama dengan artefak lokal.

### Artefak dan perangkat

- Model Xiaomi `25040RP0AG` (`taiko`), ARM64; detail Android ada pada bukti mentah.
- Layar landscape 2560 x 1600, panel mendukung hingga 90 Hz.
- Paket `app.swell.music.dev.benchmark`, versi `1.8.1-beta1` (28).
- APK: `app/build/outputs/apk/dev/benchmark/app-dev-arm64-v8a-benchmark.apk`.
- Waktu artefak: 22 Oktober 2026, 02:50 WIB.
- SHA-256: `fa293b2eddaf9a1b3f7c9ba6c65e8a1f5745d61d0a187f59808ba3536d19746e`.
- Non-debuggable; varian benchmark mengikuti konfigurasi release dengan R8,
  ditandatangani Android Debug untuk pengujian. Ini bukan APK produksi.
- Artefak `prodRelease` yang tersedia masih Sonorea (`app.sonorea.music`) dari
  20 Oktober. Pengguna memilih benchmark Swell tanpa build ulang.

### Metode

Lagu tetap **Risk It All — Bruno Mars**, tidak diganti sepanjang enam skenario.
Posisi lagu bergerak sesuai playback; potongan lirik antarjendela tidak identik.
Layar diverifikasi dengan screenshot sebelum dan setelah setiap jendela.
Dialog update ditutup sebelum pengukuran. Tidak ada screenshot, sentuhan, atau
scroll selama jendela `top` sekitar 12,3–12,4 detik.

CPU diambil dari `top -b -d 3 -n 5 -p <pid>`: sampel pertama dibuang, lalu empat
sampel berikutnya dirata-ratakan. Angka 100% berarti satu core CPU penuh, bukan
seluruh kapasitas CPU tablet. Data menunjukkan satu jendela per kondisi, bukan
rata-rata beberapa pengulangan atau pengujian termal jangka panjang.

Canvas dan synced lyrics dimatikan melalui toggle Settings aplikasi, bukan
edit SharedPreferences. Kedua toggle semula aktif dan dikembalikan aktif setelah
pengujian. Musik ditinggalkan dalam keadaan pause di Home.

### Hasil

| Kondisi | CPU rata-rata | Rentang empat sampel | Rendering |
|---|---:|---:|---|
| Home, musik play | 16,48% | 16,0–17,3% | Tidak ada frame aplikasi selama jendela |
| Player, musik play, Canvas dan lirik aktif | 111,50% | 110–113% | Terus-menerus; cadence sampel terakhir sekitar 90 Hz |
| Player, musik pause, Canvas aktif | 71,95% | 71,6–72,3% | Terus-menerus; cadence sampel terakhir sekitar 24 Hz |
| Player, musik pause, Canvas mati | 0,53% | 0,3–0,6% | Tidak ada frame aplikasi selama jendela |
| Player, musik play, Canvas mati, lirik aktif | 80,80% | 76,6–83,0% | Terus-menerus; cadence sampel terakhir sekitar 90 Hz |
| Player, musik play, Canvas dan lirik mati | 18,62% | 17,6–19,6% | Frame berkala, bukan rendering terus pada refresh layar |

Cadence dihitung dari selisih timestamp `IntendedVsync` unik dalam ring buffer
`framestats`, paling banyak 120 frame terakhir, bukan fps rata-rata seluruh
jendela. Counter agregat `gfxinfo` pada tablet ini sekitar dua kali cadence
tersebut (contoh play: 2246 frame dalam 12,4 detik meski panel 90 Hz). Karena itu
counter agregat tidak dikonversi langsung menjadi fps. Pada kondisi tanpa frame,
persentil 4950 ms yang dicetak `gfxinfo` tidak dianggap sebagai latency nyata.

### Kesimpulan yang kini didukung pengukuran

1. **Canvas adalah sumber beban besar ketika musik dipause pada lagu ini.**
   Mematikan Canvas pada layar dan posisi lagu yang sama menurunkan CPU dari
   sekitar 72% menjadi 0,5% dan menghilangkan rendering kontinu. Cover berubah
   saat musik pause; setelah Canvas dimatikan, cover tetap diam.
2. **Synced lyrics mempertahankan rendering kontinu saat musik play.** Dengan
   Canvas sudah mati, mematikan synced lyrics menurunkan CPU dari sekitar 81%
   menjadi 19%, mendekati Home dengan audio yang sama. Pengukuran ini mencakup
   seluruh jalur synced lyrics, bukan pembuktian biaya dua clock secara terpisah.
3. Beban player bukan hanya audio decoding: audio tetap play pada skenario
   terakhir, tetapi CPU turun jauh. Kedua fitur bersama menjelaskan sebagian
   besar selisih beban pada kasus tablet ini. Selisih CPU tidak boleh dianggap
   aditif karena pekerjaan render kedua fitur berbagi frame.
4. Temuan 24 Hz saat pause di tablet tidak membuktikan sumber 30 fps pada
   pengukuran Realme sebelumnya; perangkat, lagu, dan konteks berbeda.

Kode yang sesuai dengan hasil ini: `CanvasArtworkPlayer.kt:265–267` menetapkan
`playWhenReady = foreground && !pausedForTransition`, mengabaikan status pause
musik. Di `PlayerLyrics.kt`, clock per frame dibuat di `CurrentLyricStrip:612`
dan `CurrentLyricLine:3219`; keduanya melewati loop `withFrameNanos:761`.
Ini penjelasan dari source checkout sekarang, bukan hasil dekompilasi APK atau
jaminan bahwa seluruh perubahan checkout sudah ada dalam artefak pukul 02:50.

### Suhu dan batas kesimpulan

Sensor baterai `dumpsys battery` terbaca sekitar 34,8–35,5 C selama rangkaian
pengujian. HAL melaporkan suhu permukaan sekitar 37,4–38,4 C setelah jendela.
Status thermal tetap 0. Nilai CPU/GPU pada bagian *cached temperatures* tetap
sama antarjendela, sedangkan pembacaan HAL terkini untuk CPU/GPU adalah -274 C,
sehingga keduanya tidak dipakai sebagai suhu CPU/GPU yang valid.

Jendela pendek ini membuktikan perbedaan beban, belum mengukur suhu setimbang,
besar penghematan baterai, atau penurunan suhu setelah optimasi. Shimmer Lossless,
full-screen Spotify Canvas, halaman lirik penuh, serta portrait tidak diisolasi
dalam rangkaian ini. Tidak ada perbaikan kode, build, atau commit yang dilakukan.

Bukti: [ringkasan terstruktur](performance/tablet-2026-10-22/summary.json) dan
[log mentah, skrip ukur, serta screenshot](performance/tablet-2026-10-22/measurements.zip).


## Implementasi optimasi player, 22 Oktober 2026

Perubahan telah diterapkan dan APK benchmark baru dipasang di tablet.
Main player dengan Canvas+lirik aktif: 87,62–95,05% CPU pada sampel APK final;
versi sebelumnya 111,50%. Pause dengan layar menyala: 0,70%, sebelumnya 71,95%.
Uji 10 menit pada implementasi player yang sama menghasilkan rata-rata 88,85%.
Panel lirik penuh masih lebih berat (129,25% CPU); belum ada baseline setara
untuk mengukur selisih pada panel tersebut.

Lihat [laporan implementasi dan batas pengukuran](PLAYER_OPTIMIZATION_2026-10-22.md)
untuk perbedaan APK uji panjang dan final, patch sesi, tes, serta bukti mentah.
