<p align="center">
  <a href="https://github.com/rigaz29/nonton-indo">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://cdn-icons-png.flaticon.com/512/2920/2920297.png">
      <source media="(prefers-color-scheme: light)" srcset="https://cdn-icons-png.flaticon.com/512/3136/3136684.png">
      <img src="https://cdn-icons-png.flaticon.com/512/3136/3136684.png" alt="Logo" width="80" height="80">
    </picture>
  </a>

  <h3 align="center">Nonton Indo</h3>

  <p align="center">
    CloudStream 3 extensions for Indonesian and international streaming sites
  </p>
</p>

---

## About

**Nonton Indo** is a set of [CloudStream 3](https://github.com/recloudstream/cloudstream) plugins for streaming sites: Indonesian anime and film sites, plus a few English film and series sites. Install the repository in CloudStream on Android and the sites show up as providers.

This is a fork of [ExtremeBoyGG/nonton-indo](https://github.com/ExtremeBoyGG/nonton-indo). Fixes and new plugins made here are sent upstream as pull requests.

---

## Install

You need [CloudStream 3](https://github.com/recloudstream/cloudstream) on Android.

1. Open CloudStream and go to **Settings > Extensions**.
2. Tap **Add repository** and enter:

   ```
   https://raw.githubusercontent.com/rigaz29/nonton-indo/builds/repo.json
   ```

3. Open the repository and install the plugins you want.

The `builds` branch is rebuilt by GitHub Actions on every push to `main`, and installed plugins update from it.

---

## Plugins

"Works" means a title was played on a phone (Pixel 8 or POCO X3) on the date given. "Not re-checked" means the site answered on 2026-11-24 but the plugin has not been tested in this fork. A site can move to a new domain at any time; the plugins marked "follows its backup domain" pick up the move on their own.

### Anime

| Plugin | Site | Status | Notes |
|---|---|---|---|
| <img src="https://www.google.com/s2/favicons?domain=anichin.moe&sz=16" width="16" height="16"> Anichin | anichin.moe | Works (2026-11-22) | Donghua. Follows its backup domain (anichin.care). OK.ru, Streamruby, VidHide, Dailymotion, Rumble, Odysee and Dood mirrors. |
| <img src="https://www.google.com/s2/favicons?domain=kuronime.sbs&sz=16" width="16" height="16"> Kuronime | kuronime.sbs | Works (2026-11-22) | Opens the episode page in a window: tap play once. Follows its backup domain (kuronime.net, which kuronime.link points to). |
| <img src="https://www.google.com/s2/favicons?domain=oploverz.ch&sz=16" width="16" height="16"> Oploverz | oploverz.ch | Works (2026-11-20) | Blogger and Upbolt mirrors; Gofile gives no links. |
| <img src="https://www.google.com/s2/favicons?domain=anime-indo.lol&sz=16" width="16" height="16"> AnimeIndo | anime-indo.lol | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=animasuid.com&sz=16" width="16" height="16"> Animasu | animasuid.com | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=ca3d.site&sz=16" width="16" height="16"> CA3D | ca3d.site | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=donghive.vip&sz=16" width="16" height="16"> Donghub | donghive.vip | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=v20.kuramanime.ing&sz=16" width="16" height="16"> Kuramanime | v20.kuramanime.ing | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=nimegami.id&sz=16" width="16" height="16"> Nimegami | nimegami.id | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=otakudesu.blog&sz=16" width="16" height="16"> Otakudesu | otakudesu.blog | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=x6.sokuja.uk&sz=16" width="16" height="16"> Sokuja | x6.sokuja.uk | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=yunshanid.site&sz=16" width="16" height="16"> YunshanID | yunshanid.site | Not re-checked | |

### Film and series (Indonesian sites)

| Plugin | Site | Status | Notes |
|---|---|---|---|
| <img src="https://www.google.com/s2/favicons?domain=z3.idlixku.com&sz=16" width="16" height="16"> Idlix | z3.idlixku.com | Works (2026-11-21) | Subtitles, stream token refresh. Follows its backup domains (idlixian.com, idlix.com). |
| <img src="https://www.google.com/s2/favicons?domain=gfymultimedx.online&sz=16" width="16" height="16"> GfyMultimedx | gfymultimedx.online | Works (2026-11-21) | No account needed, but every play asks for a Cloudflare check: tick it in the window that opens. Ads are blocked in that window. |
| <img src="https://www.google.com/s2/favicons?domain=pahe.ink&sz=16" width="16" height="16"> Pahe | pahe.ink | Catalog only | The site only offers downloads behind the exe.io link shortener (CAPTCHA), so nothing plays. |
| <img src="https://www.google.com/s2/favicons?domain=blurayufr.site&sz=16" width="16" height="16"> BlurayUFR | blurayufr.site | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=server-1.layar.asia&sz=16" width="16" height="16"> LayarAsia | server-1.layar.asia | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=themoviebox.org&sz=16" width="16" height="16"> MovieBox | themoviebox.org | Not re-checked | The domain now redirects to officialmoviebox.com. |
| <img src="https://www.google.com/s2/favicons?domain=new1.pusatfilm21info.com&sz=16" width="16" height="16"> PusatFilm | new1.pusatfilm21info.com | Not re-checked | |
| <img src="https://www.google.com/s2/favicons?domain=tv9.nontondrama.my&sz=16" width="16" height="16"> LK21 | tv9.nontondrama.my | Site not answering (2026-11-24) | |
| <img src="https://www.google.com/s2/favicons?domain=139.59.197.199&sz=16" width="16" height="16"> Rebahin | 139.59.197.199 | Site not answering (2026-11-24) | |
| <img src="https://www.google.com/s2/favicons?domain=167.71.237.49&sz=16" width="16" height="16"> SemiRebahin | 167.71.237.49 | Site not answering (2026-11-24) | |

### Film and series (English sites)

These take their catalog (home, search, details, seasons, episodes) from TMDB and play through the site's own player, opened in a hidden WebView. Subtitles come from OpenSubtitles.

| Plugin | Site | Status | Notes |
|---|---|---|---|
| <img src="https://www.google.com/s2/favicons?domain=cinezo.st&sz=16" width="16" height="16"> Cinezo | cinezo.st | Works (2026-11-24) | One link per server that is up for the title (Jennifer 4K, Zendaya, Sadie (Hindi), ...). 4K on recent films. |
| <img src="https://www.google.com/s2/favicons?domain=arrowtv.net&sz=16" width="16" height="16"> ArrowTV | arrowtv.net | Works (2026-11-24) | Cinezo's default server; 4K on recent films (pick it in the player's track menu). |
| <img src="https://www.google.com/s2/favicons?domain=spacedom.live&sz=16" width="16" height="16"> Spacedom | spacedom.live | Works (2026-11-22) | Several servers per title, up to 4K. Behind a Cloudflare check that the WebView passes on its own. |
| <img src="https://www.google.com/s2/favicons?domain=atlantic.bz&sz=16" width="16" height="16"> Atlantic | atlantic.bz | Works (2026-11-21) | The site takes 3 to 30 s to find a source. |
| <img src="https://www.google.com/s2/favicons?domain=vivarium.st&sz=16" width="16" height="16"> Vivarium | vivarium.st | Works (2026-11-21) | Uses the sources that serve plain HLS (Hyperion, Hermes, Hemera, then Tyche, Theia, Eos, Nyx). |

### Adult (18+)

| Plugin | Site | Status | Notes |
|---|---|---|---|
| <img src="https://www.google.com/s2/favicons?domain=hanime.tv&sz=16" width="16" height="16"> Hanime | hanime.tv | Works (2026-11-20) | Guests get 720p. |
| <img src="https://www.google.com/s2/favicons?domain=nekopoi.care&sz=16" width="16" height="16"> Nekopoi | nekopoi.care | Works (2026-11-21) | Playmogo, Streampoi and Vidara mirrors. |

---

## Build

Requirements: JDK 17 and the Android SDK.

```bash
git clone https://github.com/rigaz29/nonton-indo.git
cd nonton-indo

# Build every plugin and plugins.json (what CI runs)
./gradlew make makePluginsJson

# Build one plugin
./gradlew :Idlix:make

# Build one plugin and push it to a phone connected over adb
./gradlew :Idlix:deployWithAdb
```

Each plugin's `.cs3` file is written to its own `build/` folder.

### Adding a plugin

1. Create a folder at the repository root with a `build.gradle.kts` (copy one from an existing plugin). Every folder with a `build.gradle.kts` is picked up by `settings.gradle.kts`; nothing has to be registered.
2. Add the provider: a class that extends `MainAPI`, or `TmdbProvider` when the site uses TMDB ids.
3. Add the plugin class: annotated with `@CloudstreamPlugin`, it extends `Plugin` and calls `registerMainAPI(...)`.
4. Bump `version` in `build.gradle.kts` on every change, so installed copies update.

---

## Contributing

Pull requests are welcome, here or [upstream](https://github.com/ExtremeBoyGG/nonton-indo). For a broken site, say which title and episode you tried. If you can, add the log of a failed play: most plugins log under their own name (`adb logcat -s Idlix`, for example).

---

## Disclaimer

- The plugins only read pages that the sites publish; they do not host any content.
- The content belongs to third-party sites. Use the plugins in line with the laws where you live.

---

## License

MIT License. See [LICENSE](LICENSE).

---

## Credits

- [ExtremeBoyGG/nonton-indo](https://github.com/ExtremeBoyGG/nonton-indo), the project this fork comes from
- [CloudStream 3](https://github.com/recloudstream/cloudstream)
- [hexated](https://github.com/hexated/cloudstream-extensions-hexated), used as a reference for extensions
- [TMDB](https://www.themoviedb.org/) for the catalog of the English-site plugins
- The [OpenSubtitles Stremio addon](https://opensubtitles-v3.strem.io/) for subtitles
