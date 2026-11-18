# Optimasi player Android Swell — 22 Oktober 2026

Canvas dan synced lyrics tetap aktif. Perubahan ini mengurangi pekerjaan UI
berulang dan menghentikan video dekoratif yang tidak sedang ditampilkan atau
ketika musik dipause. Timing provider, offset lirik, serta clock audio tidak
diubah.

## Perubahan

- Strip lirik memiliki satu clock bersama untuk arah teks, pemilihan baris, dan
  sweep. Target pembaruan strip 30 Hz; panel lirik penuh memakai target 60 Hz.
  Coroutine tidur di antara sampel, bukan meminta setiap vsync lalu membuang
  hasilnya. Posisi highlight tetap dihitung dari waktu aktual dan pembacaan
  player; timing kata/suku kata tidak disintesis atau dikuantisasi ulang.
- `LyricTimingIndex` memakai pencarian biner untuk timestamp dan indeks batas
  akhir vokal untuk mencari baris yang bertumpuk. Timestamp duplikat memilih
  baris terakhir seperti sebelumnya. Input tidak terurut mempertahankan jalur
  lama. Seek mundur dan backing vocal panjang tetap dipertahankan.
- Canvas mengikuti status musik, lifecycle, fokus jendela, dan transisi yang
  menutupnya. Clock lirik juga berhenti ketika jendelanya kehilangan fokus.
  Poll posisi playback di `PlayerConnection` tetap mengikuti lifecycle aplikasi
  sehingga player dalam dialog terus mendapat posisi audio.
- Pilihan track Canvas dibatasi ke preferensi 960 x 1280, 3 Mbps, dan 30 fps;
  TV tetap dapat menggunakan batas 24 fps. Ini memilih rendition bila tersedia,
  bukan mentranskode atau menjamin file MP4 tetap menjadi 30 fps.
- Callback capture frame opsional: layar yang tidak memakai warna video tidak
  melakukan GPU readback awal. Capture berkala juga berhenti saat video pause,
  aplikasi di background, jendela tidak fokus, atau panel menutup backdrop.
- Indikator equalizer pada hasil Search berhenti membuat infinite transition
  ketika jendelanya kehilangan fokus atau aplikasi tidak aktif. Tiga animasi
  ini sebelumnya terus menggambar halaman di belakang dialog player.
- Cover landscape tetap bergerak ketika masih terlihat di sebelah lirik atau
  queue. Tidak ada penghentian berdasarkan nama panel saja pada layout itu.
- `Reduce animation` dan `Reduce dynamic blur` kembali membaca/menyimpan nilai
  pengguna dan tersedia di Settings. Mengaktifkan salah satunya mematikan mode
  high performance; mengaktifkan high performance mematikan keduanya seperti
  kontrak sebelumnya. Reduce blur juga memilih mode solid pada navbar Swell.

Pengaturan default tetap Canvas aktif, synced lyrics aktif, dan kedua opsi
pengurangan efek nonaktif. Efek blur video Spotify sudah memakai input 18%;
intensitas dan resolusi blur itu tidak diturunkan lagi tanpa bukti visual baru.

## Validasi source dan artefak

- `:sharedUi:jvmTest`: 47 tes, 0 gagal, 0 error, 0 dilewati.
- Tiga regresi baru membandingkan indeks dengan perilaku lama pada timestamp
  duplikat, seek mundur, input tidak terurut, gap, dan batas akhir backing vocal.
- Build offline `:app:assembleDevBenchmark` berhasil. Heap Gradle 3 GB disuplai
  lewat argumen perintah; `gradle.properties` tidak diubah pada sesi ini.
- R8 memperingatkan parsing metadata Kotlin pada toolchain yang sudah ada;
  build tetap berhasil. Tidak ada klaim bahwa peringatan itu sudah diperbaiki.
- APK ARM64 benchmark, paket `app.swell.music.dev.benchmark`, non-debuggable,
  memakai R8 dan sertifikat Android Debug untuk pengujian.
- SHA-256 APK final dan APK terpasang sama:
  `fa5625199651e4c6a55548a3faadd6655ea16a9392e1332b6ea0708ba5f4a761`.
- `apksigner verify` serta `zipalign -c -P 16 4` berhasil.
- Tablet Xiaomi `25040RP0AG`, Android 16, landscape 2560 x 1600, Wireless ADB.
- Toggle pengurangan efek diuji aktif, ditutup paksa, dibuka lagi, dan tetap
  aktif; setelah itu dikembalikan nonaktif untuk pengukuran Canvas+lirik.

## Batas perbandingan

APK pembanding awal (`fa293b2e…`) dibangun pukul 02:50 dan sudah tersedia sebelum
sesi implementasi. Build sekarang menggunakan checkout yang juga berisi banyak
perubahan pengguna dari sebelum sesi ini. Perbandingan kedua APK adalah hasil
observasi pada perangkat, bukan eksperimen yang mengisolasi patch ini saja.

Percobaan pembukaan dari Search pada kandidat perantara menemukan frame aktif
pada jendela halaman di belakang player. Kandidat berikutnya memberi syarat
fokus jendela untuk Canvas dan clock lirik. Data kandidat tidak dicampur dengan
hasil APK final. Penelusuran berikutnya menemukan tiga infinite transition
pada `SearchPlayingBars`; guard fokus/lifecycle ditambahkan di `Common.kt`.
APK final diverifikasi dengan hasil Search lagu aktif di belakang player:
jendela yang tersembunyi mencatat 0 frame selama jendela pengukuran.

CPU 100% di `top` berarti satu core penuh. Cadence frame dihitung per ring buffer
jendela; counter agregat vendor tidak langsung dianggap fps. Suhu CPU/GPU HAL
-274 C tidak digunakan; suhu baterai dan permukaan dilaporkan terpisah. Uji
termal satu sesi tanpa kontrol sebelum/sesudah tidak membuktikan besarnya
penurunan suhu atau penghematan baterai.


## Hasil perangkat

Seluruh sampel pendek memakai lagu Risk It All — Bruno Mars, Canvas aktif,
synced lyrics aktif, dan opsi pengurangan efek nonaktif. CPU adalah rata-rata
empat sampel 3 detik setelah sampel pertama dibuang.

| Skenario pada APK final | CPU rata-rata | Catatan |
|---|---:|---|
| Main player, dibuka dari Home | 95,05% | Ring buffer frame terakhir sekitar 46,6 Hz |
| Main player, dibuka dari hasil Search lagu aktif | 87,62% | Jendela halaman Search di belakang player: 0 frame |
| Main player pause, layar menyala | 0,70% | 0 frame; crop cover tidak berubah pada dua screenshot terpisah |
| Panel lirik penuh + cover video landscape | 129,25% | Lebih berat daripada strip; tidak ada baseline panel penuh yang setara |
| Audio play, aplikasi di background | 17,88% | 0 frame UI; sesi media tetap PLAYING |

Pembanding lama dari laporan awal: main player play 111,50% dan pause 71,95%.
Pada kandidat perantara sebelum guard equalizer Search, player dari Search
mencapai 154,75% dan jendela belakang mencatat 2094 frame. Pada APK final,
jendela belakang tidak lagi menggambar. Sampel main play antarjendela tidak
memakai detik lagu yang identik, sehingga selisih CPU bukan benchmark statistik
atau isolasi satu perubahan.

### Uji 10 menit

- CPU rata-rata 88,85%, rentang sampel 72,6–104,0%, 120 sampel setelah sampel
  pertama dibuang.
- Semua pemeriksaan per menit menunjukkan Risk It All tetap PLAYING; repeat
  one dipakai untuk menjaga lagu tetap sama.
- Suhu baterai 36,0–37,1 C, berakhir 36,8 C. Suhu permukaan berakhir 40,529 C.
- Status thermal 0 di setiap pemeriksaan. Log fatal untuk PID yang diuji kosong.
- Uji ini memakai APK `994f1864…`, dengan implementasi clock, Canvas, capture,
  dan pengaturan efek yang sama dengan APK final. Satu perubahan terakhir,
  guard equalizer Search di `Common.kt`, ditambahkan setelah uji panjang dan
  diverifikasi dengan sampel Search pada APK final `fa562519…`.
- Timeout layar dinaikkan sementara dari 600000 ke 900000 ms agar layar tidak
  padam di tengah uji. Nilai awal 600000 ms sudah dipulihkan.

Setelah timeout dikembalikan, satu percobaan pause terkena layar padam. Data
`optimized-pause` dan screenshot hitam `full-lyrics.png` dikeluarkan dari hasil.
Sampel pause yang sah adalah `final-foreground-pause`, yang screenshot sebelum
serta sesudahnya terlihat dan covernya tetap diam.

Panel lirik penuh masih membutuhkan kerja GPU untuk sweep, glow, blur baris,
dan auto-scroll sambil video terlihat. Pengujian ini tidak membuktikan bahwa
kasus tersebut sudah ringan atau tidak akan terasa panas. Belum ada pengukuran
baseline panel penuh yang setara, uji portrait, atau pengukuran daya listrik.

## Bukti dan perubahan lokal

- [Patch khusus sesi ini](performance/player-optimization-2026-10-22/session.patch)
  memisahkan perubahan optimasi dari perubahan pengguna yang sudah ada.
- [Manifest source](performance/player-optimization-2026-10-22/source-manifest.json),
  [sampel pendek](performance/player-optimization-2026-10-22/short-summary.json),
  [ringkasan 10 menit](performance/player-optimization-2026-10-22/soak-result.json),
  [sampel per menit](performance/player-optimization-2026-10-22/soak-samples.json).
- [Bukti mentah dan screenshot](performance/player-optimization-2026-10-22/evidence.zip).

Tidak ada commit atau push. Perubahan lain di checkout dipertahankan.


Pemeriksaan akhir: audio tetap PLAYING selama background, dan player kembali
menampilkan konten setelah aplikasi dibuka lagi dalam keadaan play maupun pause.
Repeat dikembalikan OFF, timeout layar 600000 ms, opsi pengurangan efek tetap
nonaktif, Canvas dan synced lyrics tetap aktif. Aplikasi ditinggalkan di Home
dengan musik dipause.
