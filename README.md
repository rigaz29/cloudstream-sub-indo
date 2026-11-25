<p align="center">
  <a href="https://github.com/rigaz29/cloudstream-sub-indo">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://cdn-icons-png.flaticon.com/512/2920/2920297.png">
      <source media="(prefers-color-scheme: light)" srcset="https://cdn-icons-png.flaticon.com/512/3136/3136684.png">
      <img src="https://cdn-icons-png.flaticon.com/512/3136/3136684.png" alt="Logo" width="80" height="80">
    </picture>
  </a>

  <h3 align="center">CloudStream Sub Indo</h3>

  <p align="center">
    Repo CloudStream untuk nonton anime, donghua, film, dan drama subtitle Indonesia (sub indo), plus beberapa situs streaming berbahasa Inggris
    <br />
    <a href="https://rigaz29.github.io/cloudstream-sub-indo/">Website</a>
    &middot;
    <a href="#cara-pasang">Cara pasang</a>
    &middot;
    <a href="#daftar-plugin">Daftar plugin</a>
  </p>
</p>

---

## Tentang

**CloudStream Sub Indo** adalah kumpulan ekstensi [CloudStream 3](https://github.com/recloudstream/cloudstream) untuk situs streaming: situs anime, donghua, film, dan drama Indonesia dengan subtitle Indonesia (sub indo), ditambah beberapa situs film dan serial berbahasa Inggris. Pasang repo ini di CloudStream di Android, dan situs-situsnya muncul sebagai provider.

Proyek ini berawal dari fork [ExtremeBoyGG/nonton-indo](https://github.com/ExtremeBoyGG/nonton-indo) dan dulu bernama rigaz29/nonton-indo. URL repo lama masih bisa dipakai.

---

## Cara pasang

Butuh [CloudStream 3](https://github.com/recloudstream/cloudstream) di Android.

1. Buka CloudStream, lalu masuk ke **Settings > Extensions**.
2. Ketuk **Add repository** dan masukkan:

   ```
   https://raw.githubusercontent.com/rigaz29/cloudstream-sub-indo/builds/repo.json
   ```

3. Buka repo itu dan pasang plugin yang diinginkan.

Di HP yang sudah terpasang CloudStream, tombol **Add to CloudStream** di [website proyek](https://rigaz29.github.io/cloudstream-sub-indo/) langsung menambahkan repo ini.

Branch `builds` dibangun ulang oleh GitHub Actions setiap ada push ke `main`, dan plugin yang sudah terpasang diperbarui dari sana.

---

## Daftar plugin

"Jalan" berarti sebuah judul berhasil diputar di HP (Pixel 8 atau POCO X3) pada tanggal yang tertulis. "Belum dicek ulang" berarti situsnya masih menjawab pada 2026-11-24, tapi plugin-nya belum dites di repo ini. Situs bisa pindah domain kapan saja; plugin yang bertanda "mengikuti domain cadangan" ikut pindah sendiri.

### Anime dan donghua

| Plugin | Situs | Status | Catatan |
|---|---|---|---|
| <img src="https://www.google.com/s2/favicons?domain=anichin.moe&sz=16" width="16" height="16"> Anichin | anichin.moe | Jalan (2026-11-22) | Donghua. Mengikuti domain cadangan (anichin.care). Mirror OK.ru, Streamruby, VidHide, Dailymotion, Rumble, Odysee, dan Dood. |
| <img src="https://www.google.com/s2/favicons?domain=kuronime.sbs&sz=16" width="16" height="16"> Kuronime | kuronime.sbs | Jalan (2026-11-22) | Halaman episode dibuka di sebuah jendela: ketuk tombol putar sekali. Mengikuti domain cadangan (kuronime.net, tujuan kuronime.link). |
| <img src="https://www.google.com/s2/favicons?domain=oploverz.ch&sz=16" width="16" height="16"> Oploverz | oploverz.ch | Jalan (2026-11-20) | Mirror Blogger dan Upbolt; Gofile tidak menghasilkan link. |
| <img src="https://www.google.com/s2/favicons?domain=anime-indo.lol&sz=16" width="16" height="16"> AnimeIndo | anime-indo.lol | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=animasuid.com&sz=16" width="16" height="16"> Animasu | animasuid.com | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=ca3d.site&sz=16" width="16" height="16"> CA3D | ca3d.site | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=donghive.vip&sz=16" width="16" height="16"> Donghub | donghive.vip | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=v20.kuramanime.ing&sz=16" width="16" height="16"> Kuramanime | v20.kuramanime.ing | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=nimegami.id&sz=16" width="16" height="16"> Nimegami | nimegami.id | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=otakudesu.blog&sz=16" width="16" height="16"> Otakudesu | otakudesu.blog | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=x6.sokuja.uk&sz=16" width="16" height="16"> Sokuja | x6.sokuja.uk | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=yunshanid.site&sz=16" width="16" height="16"> YunshanID | yunshanid.site | Belum dicek ulang | |

### Film dan serial (situs Indonesia)

| Plugin | Situs | Status | Catatan |
|---|---|---|---|
| <img src="https://www.google.com/s2/favicons?domain=z3.idlixku.com&sz=16" width="16" height="16"> Idlix | z3.idlixku.com | Jalan (2026-11-21) | Subtitle dan pembaruan token stream. Mengikuti domain cadangan (idlixian.com, idlix.com). |
| <img src="https://www.google.com/s2/favicons?domain=gfymultimedx.online&sz=16" width="16" height="16"> GfyMultimedx | gfymultimedx.online | Jalan (2026-11-21) | Tidak perlu akun, tapi setiap kali memutar situs meminta verifikasi Cloudflare: centang di jendela yang terbuka. Iklan diblokir di jendela itu. |
| <img src="https://www.google.com/s2/favicons?domain=officialmoviebox.com&sz=16" width="16" height="16"> MovieBox | officialmoviebox.com | Jalan (2026-11-25) | Link MP4 (H.264, sampai 1080p) dan DASH (HEVC), versi asli plus dub Inggris/Indonesia kalau ada. Subtitle dari situs, termasuk Indonesia. Judul yang dikunci VIP dilewati. |
| <img src="https://www.google.com/s2/favicons?domain=pahe.ink&sz=16" width="16" height="16"> Pahe | pahe.ink | Hanya katalog | Situs hanya menyediakan unduhan di balik pemendek link exe.io (CAPTCHA), jadi tidak ada yang bisa diputar. |
| <img src="https://www.google.com/s2/favicons?domain=blurayufr.site&sz=16" width="16" height="16"> BlurayUFR | blurayufr.site | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=server-1.layar.asia&sz=16" width="16" height="16"> LayarAsia | server-1.layar.asia | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=new1.pusatfilm21info.com&sz=16" width="16" height="16"> PusatFilm | new1.pusatfilm21info.com | Belum dicek ulang | |
| <img src="https://www.google.com/s2/favicons?domain=tv9.nontondrama.my&sz=16" width="16" height="16"> LK21 | tv9.nontondrama.my | Situs tidak menjawab (2026-11-24) | |
| <img src="https://www.google.com/s2/favicons?domain=139.59.197.199&sz=16" width="16" height="16"> Rebahin | 139.59.197.199 | Situs tidak menjawab (2026-11-24) | |
| <img src="https://www.google.com/s2/favicons?domain=167.71.237.49&sz=16" width="16" height="16"> SemiRebahin | 167.71.237.49 | Situs tidak menjawab (2026-11-24) | |

### Film dan serial (situs berbahasa Inggris)

Plugin ini mengambil katalog (beranda, pencarian, detail, season, episode) dari TMDB dan memutar lewat pemutar situsnya sendiri, yang dibuka di WebView tersembunyi. Subtitle diambil dari OpenSubtitles, termasuk subtitle Indonesia kalau tersedia.

| Plugin | Situs | Status | Catatan |
|---|---|---|---|
| <img src="https://www.google.com/s2/favicons?domain=cinezo.st&sz=16" width="16" height="16"> Cinezo | cinezo.st | Jalan (2026-11-24) | Satu link per server yang aktif untuk judul itu (Jennifer 4K, Zendaya, Sadie (Hindi), ...). 4K untuk film baru. |
| <img src="https://www.google.com/s2/favicons?domain=arrowtv.net&sz=16" width="16" height="16"> ArrowTV | arrowtv.net | Jalan (2026-11-24) | Server default Cinezo; 4K untuk film baru (pilih di menu Tracks pemutar). |
| <img src="https://www.google.com/s2/favicons?domain=spacedom.live&sz=16" width="16" height="16"> Spacedom | spacedom.live | Jalan (2026-11-22) | Beberapa server per judul, sampai 4K. Ada pemeriksaan Cloudflare yang dilewati WebView dengan sendirinya. |
| <img src="https://www.google.com/s2/favicons?domain=atlantic.bz&sz=16" width="16" height="16"> Atlantic | atlantic.bz | Jalan (2026-11-21) | Situs butuh 3 sampai 30 detik untuk menemukan sumber. |
| <img src="https://www.google.com/s2/favicons?domain=vivarium.st&sz=16" width="16" height="16"> Vivarium | vivarium.st | Jalan (2026-11-21) | Memakai sumber yang menyajikan HLS biasa (Hyperion, Hermes, Hemera, lalu Tyche, Theia, Eos, Nyx). |

### Dewasa (18+)

| Plugin | Situs | Status | Catatan |
|---|---|---|---|
| <img src="https://www.google.com/s2/favicons?domain=hanime.tv&sz=16" width="16" height="16"> Hanime | hanime.tv | Jalan (2026-11-20) | Tamu mendapat 720p. |
| <img src="https://www.google.com/s2/favicons?domain=nekopoi.care&sz=16" width="16" height="16"> Nekopoi | nekopoi.care | Jalan (2026-11-21) | Mirror Playmogo, Streampoi, dan Vidara. |

---

## Cara build

Kebutuhan: JDK 17 dan Android SDK.

```bash
git clone https://github.com/rigaz29/cloudstream-sub-indo.git
cd cloudstream-sub-indo

# Build semua plugin dan plugins.json (sama dengan CI)
./gradlew make makePluginsJson

# Build satu plugin
./gradlew :Idlix:make

# Build satu plugin dan kirim ke HP yang tersambung lewat adb
./gradlew :Idlix:deployWithAdb
```

File `.cs3` tiap plugin ditulis ke folder `build/` plugin itu.

### Menambah plugin

1. Buat folder di root repo dengan `build.gradle.kts` (salin dari plugin yang sudah ada). Setiap folder yang punya `build.gradle.kts` otomatis dimuat oleh `settings.gradle.kts`, jadi tidak ada yang perlu didaftarkan.
2. Tambahkan provider: kelas turunan `MainAPI`, atau `TmdbProvider` kalau situsnya memakai ID TMDB.
3. Tambahkan kelas plugin: diberi anotasi `@CloudstreamPlugin`, turunan `Plugin`, dan memanggil `registerMainAPI(...)`.
4. Naikkan `version` di `build.gradle.kts` setiap ada perubahan, supaya plugin yang terpasang ikut diperbarui.

---

## Kontribusi

Pull request dipersilakan, di sini atau di [upstream](https://github.com/ExtremeBoyGG/nonton-indo). Untuk situs yang rusak, sebutkan judul dan episode yang dicoba. Kalau bisa, sertakan log saat pemutaran gagal: kebanyakan plugin mencatat log dengan namanya sendiri (misalnya `adb logcat -s Idlix`).

---

## Disclaimer

- Plugin hanya membaca halaman yang diterbitkan situs-situs tersebut; plugin tidak menyimpan konten apa pun.
- Konten adalah milik situs pihak ketiga. Gunakan plugin sesuai hukum yang berlaku di tempatmu.

---

## Lisensi

MIT License. Lihat [LICENSE](LICENSE).

---

## Kredit

- [ExtremeBoyGG/nonton-indo](https://github.com/ExtremeBoyGG/nonton-indo), proyek awal repo ini
- [CloudStream 3](https://github.com/recloudstream/cloudstream)
- [hexated](https://github.com/hexated/cloudstream-extensions-hexated), referensi pembuatan ekstensi
- [TMDB](https://www.themoviedb.org/) untuk katalog plugin situs berbahasa Inggris
- [Addon OpenSubtitles untuk Stremio](https://opensubtitles-v3.strem.io/) untuk subtitle
