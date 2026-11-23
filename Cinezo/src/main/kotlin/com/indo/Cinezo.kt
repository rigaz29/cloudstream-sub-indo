package com.indo

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * cinezo.st. The catalog (home, search, details, seasons and episodes) comes from TMDB through
 * CloudStream's TmdbProvider; Cinezo uses TMDB ids (/movie/<id>, /watch/tv/<id>?season=&episode=).
 *
 * Its watch page embeds the Cinezo player (player.cinezo.live/embed/movie/<id>, /embed/tv/<id>/S/E),
 * which also opens on its own with cinezo.st as Referer. The player has several servers in its
 * quality menu ("Jennifer 4K", "Zendaya", "Scarlet", "Berlin", "Sadie (Hindi)"), each marked Live,
 * Online or Offline per title, and plays the first one by itself (an HLS master on punkflix).
 *
 * loadLinks opens the player in a hidden WebView, takes the manifest the default server loads, then
 * picks each other Live/Online server through the player's own menu and takes the manifest (HLS or
 * DASH) it loads next: one link per server. The servers' lookups are left to the player; nothing
 * here calls them. Links send what the player sent (Origin, Referer).
 *
 * Subtitles come from the OpenSubtitles Stremio addon by IMDb id.
 */
class Cinezo(private val appContext: Context) : TmdbProvider() {
    override var name = "Cinezo"
    override var mainUrl = "https://cinezo.st"
    override var lang = "en"
    override val hasMainPage = true
    override val useMetaLoadResponse = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val link = tryParseJson<TmdbLink>(data) ?: return false
        val id = link.tmdbID ?: return false
        val playerUrl = if (link.season != null && link.episode != null) {
            "$PLAYER/embed/tv/$id/${link.season}/${link.episode}"
        } else {
            "$PLAYER/embed/movie/$id"
        }

        val streams = coroutineScope {
            // In parallel with the WebView, so the subtitles add no waiting.
            launch { addSubtitles(link, subtitleCallback) }
            try {
                catchServers(playerUrl)
            } catch (e: Exception) {
                Log.d(TAG, "webview failed: $e")
                emptyList()
            }
        }

        streams.forEach { stream ->
            val headers = mutableMapOf<String, String>()
            stream.userAgent?.let { headers["User-Agent"] = it }
            stream.origin?.takeIf { it != "null" }?.let { headers["Origin"] = it }
            runCatching { CookieManager.getInstance().getCookie(stream.url) }.getOrNull()
                ?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
            callback(
                newExtractorLink(
                    name,
                    "$name ${stream.server}",
                    stream.url,
                    type = if (stream.url.substringBefore("?").endsWith(".mpd")) ExtractorLinkType.DASH else ExtractorLinkType.M3U8
                ) {
                    this.referer = stream.referer.orEmpty()
                    this.headers = headers
                    this.quality = Qualities.Unknown.value
                }
            )
        }
        Log.d(TAG, "links: ${streams.size}")
        return streams.isNotEmpty()
    }

    private class Seen(val at: Long, val url: String, val userAgent: String?, val origin: String?, val referer: String?)

    private class Stream(val server: String, val url: String, val userAgent: String?, val origin: String?, val referer: String?)

    private data class Server(val name: String, val status: String)

    private suspend fun catchServers(playerUrl: String): List<Stream> {
        val manifests = ConcurrentLinkedQueue<Seen>()
        var webView: WebView? = null
        withContext(Dispatchers.Main) {
            webView = WebView(appContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url.toString()
                        if (MANIFEST.containsMatchIn(url)) {
                            fun header(name: String) = request.requestHeaders?.entries?.firstOrNull { it.key.equals(name, true) }?.value
                            manifests.add(Seen(System.currentTimeMillis(), url, header("User-Agent"), header("Origin"), header("Referer")?.takeIf { it.isNotBlank() }))
                        }
                        return null
                    }
                }
                Log.d(TAG, "player: $playerUrl")
                loadUrl(playerUrl, mapOf("Referer" to "$mainUrl/"))
            }
        }

        suspend fun evalString(js: String): String? {
            val result = CompletableDeferred<String?>()
            withContext(Dispatchers.Main) {
                webView?.evaluateJavascript(js) { raw -> result.complete(runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()) }
                    ?: result.complete(null)
            }
            return result.await()
        }

        val streams = mutableListOf<Stream>()

        // A manifest is new unless it is the same URL or an HLS playlist next to one already taken
        // (the variant and audio playlists of a master sit in the master's folder).
        fun isNew(seen: Seen): Boolean = streams.none { taken ->
            taken.url == seen.url || (seen.url.contains(".m3u8") && taken.url.contains(".m3u8") && folder(taken.url) == folder(seen.url))
        }

        suspend fun waitForManifest(since: Long, timeoutMs: Long): Seen? {
            val until = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < until) {
                manifests.firstOrNull { it.at >= since && isNew(it) }?.let { return it }
                delay(300)
            }
            return null
        }

        try {
            val startedAt = System.currentTimeMillis()
            val first = waitForManifest(startedAt, FIRST_WAIT_MS)
            if (first == null) {
                Log.d(TAG, "no manifest from the default server")
                return emptyList()
            }
            val current = evalString(CURRENT_JS)?.ifBlank { null } ?: "Default"
            streams.add(Stream(current, first.url, first.userAgent, first.origin, first.referer))
            Log.d(TAG, "server $current: ${first.url.substringAfter("://").substringBefore("?").take(80)}")

            // The menu lists the servers with their state for this title.
            evalString(OPEN_MENU_JS)
            delay(900)
            val servers = runCatching {
                val arr = JSONArray(evalString(LIST_JS) ?: "[]")
                (0 until arr.length()).map { arr.getJSONObject(it) }.map { Server(it.getString("name"), it.getString("status")) }
            }.getOrDefault(emptyList())
            Log.d(TAG, "servers: ${servers.joinToString { "${it.name}=${it.status}" }}")

            for (server in servers.filter { it.name != current && it.status != "Offline" }.take(MAX_EXTRA)) {
                val pickedAt = System.currentTimeMillis()
                val r = evalString(PICK_JS.replace("__NAME__", JSONObject.quote(server.name)))
                if (r == "opening") {
                    delay(900)
                    evalString(PICK_JS.replace("__NAME__", JSONObject.quote(server.name)))
                }
                val seen = waitForManifest(pickedAt, SERVER_WAIT_MS)
                if (seen == null) {
                    Log.d(TAG, "server ${server.name}: no manifest")
                    continue
                }
                streams.add(Stream(server.name, seen.url, seen.userAgent, seen.origin, seen.referer))
                Log.d(TAG, "server ${server.name}: ${seen.url.substringAfter("://").substringBefore("?").take(80)}")
            }
            return streams
        } finally {
            withContext(Dispatchers.Main) {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
            }
        }
    }

    private fun folder(url: String) = url.substringBefore("?").substringBeforeLast("/")

    private data class OsSubtitle(
        @JsonProperty("lang") val lang: String? = null,
        @JsonProperty("url") val url: String? = null,
    )

    private data class OsResponse(@JsonProperty("subtitles") val subtitles: List<OsSubtitle>? = null)

    /**
     * Subtitles from the OpenSubtitles Stremio addon: movie/<imdb> or series/<imdb>:S:E (episode
     * links carry the show's IMDb id). Each is passed with its language name only; CloudStream
     * numbers equal names itself ("English 1", "English 2", ...).
     */
    private suspend fun addSubtitles(link: TmdbLink, subtitleCallback: (SubtitleFile) -> Unit) {
        val imdb = link.imdbID?.takeIf { it.startsWith("tt") } ?: return
        val path = if (link.season != null && link.episode != null) {
            "series/$imdb:${link.season}:${link.episode}"
        } else {
            "movie/$imdb"
        }
        val subs = try {
            app.get("$OPENSUBTITLES_ADDON/subtitles/$path.json").parsedSafe<OsResponse>()?.subtitles
        } catch (e: Exception) {
            Log.d(TAG, "subtitles failed: $e")
            null
        }.orEmpty().filter { !it.url.isNullOrBlank() && !it.lang.isNullOrBlank() }

        subs.map { languageName(it.lang!!) to it.url!! }
            .sortedBy { it.first }
            .distinctBy { it.second }
            .forEach { (language, url) -> subtitleCallback(newSubtitleFile(language, url)) }
        Log.d(TAG, "subtitles: ${subs.size} for $path")
    }

    private fun languageName(code: String): String {
        val low = code.lowercase()
        // OpenSubtitles' own code for Brazilian Portuguese, not in CloudStream's table.
        if (low == "pob") return "Portuguese (Brazil)"
        // The addon uses ISO 639-2/B codes, which CloudStream's table leaves empty for these.
        val tag = BIBLIOGRAPHIC_TO_TERMINOLOGY[low] ?: low
        return SubtitleHelper.fromTagToEnglishLanguageName(tag) ?: code
    }

    companion object {
        private const val TAG = "Cinezo"
        private const val PLAYER = "https://player.cinezo.live"
        private const val OPENSUBTITLES_ADDON = "https://opensubtitles-v3.strem.io"

        private const val FIRST_WAIT_MS = 30_000L
        private const val SERVER_WAIT_MS = 8_000L
        private const val MAX_EXTRA = 4

        private val MANIFEST = Regex("""\.(m3u8|mpd)(\?|$)""")

        private val BIBLIOGRAPHIC_TO_TERMINOLOGY = mapOf(
            "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho",
            "cze" to "ces", "dut" to "nld", "fre" to "fra", "geo" to "kat", "ger" to "deu",
            "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa",
            "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
        )

        // The player's menu button reads "Quality<level> · <server>".
        private const val CURRENT_JS = """
            (function () {
                var b = Array.prototype.find.call(document.querySelectorAll('button, [role=button]'), function (e) { return /Quality/i.test(e.textContent || ''); });
                return b ? ((b.textContent || '').split('·')[1] || '').trim() : '';
            })();
        """

        private const val OPEN_MENU_JS = """
            (function () {
                var b = Array.prototype.find.call(document.querySelectorAll('button, [role=button]'), function (e) { return /Quality/i.test(e.textContent || ''); });
                if (!b) return 'no menu';
                b.click();
                return 'opened';
            })();
        """

        // Visible menu entries that end with a server state: [{name, status}].
        private const val LIST_JS = """
            (function () {
                var out = [];
                Array.prototype.forEach.call(document.querySelectorAll('button, [role=button], [role=menuitem], [role=option], [role=radio], li, label'), function (e) {
                    if (e.offsetParent === null) return;
                    var t = (e.textContent || '').trim().split(/\s+/).join(' ');
                    var m = t.match(/^(.+?)\s*(Live|Online|Offline)$/);
                    if (m && out.every(function (o) { return o.name !== m[1].trim(); })) out.push({ name: m[1].trim(), status: m[2] });
                });
                return JSON.stringify(out);
            })();
        """

        // Clicks the menu entry of server __NAME__; opens the menu first when it is closed.
        private const val PICK_JS = """
            (function () {
                var want = __NAME__;
                var entry = Array.prototype.find.call(document.querySelectorAll('button, [role=button], [role=menuitem], [role=option], [role=radio], li, label'), function (e) {
                    return e.offsetParent !== null && (e.textContent || '').trim().indexOf(want) === 0;
                });
                if (entry) { entry.click(); return 'picked'; }
                var b = Array.prototype.find.call(document.querySelectorAll('button, [role=button]'), function (e) { return /Quality/i.test(e.textContent || ''); });
                if (b) { b.click(); return 'opening'; }
                return 'no menu';
            })();
        """
    }
}
