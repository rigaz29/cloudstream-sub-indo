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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import java.net.URLEncoder
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * spacedom.live. The catalog (home, search, details, seasons and episodes) comes from TMDB through
 * CloudStream's TmdbProvider; the site itself works on TMDB ids too (its API is
 * api.spacedom.fun/api/movies/<tmdb>), but its pages use an 8-character code instead of the id
 * (/watch/movie/<code>, /watch/tv/<code>?season=&episode=), and /watch/movie/<tmdb> is "not found".
 *
 * So loadLinks works through the site's own pages in a hidden WebView: it opens the site's search
 * for the title, takes the result codes from the page, and opens the watch page of each candidate
 * until the page asks for /api/availability/<type>/<our tmdb id> (the first request a watch page
 * makes). That page then loads its servers on its own; the HLS (.m3u8) and DASH (.mpd) manifests it
 * requests in the following seconds are taken, one per host. How the code is made is not looked
 * into, and nothing here calls the site's API or reads its scripts.
 *
 * The site's segment CDN refuses foreign Referers, so links carry the site as Referer and Origin.
 * Subtitles come from the OpenSubtitles Stremio addon by IMDb id.
 */
class Spacedom(private val appContext: Context) : TmdbProvider() {
    override var name = "Spacedom"
    override var mainUrl = "https://spacedom.live"
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
        val title = link.movieName?.takeIf { it.isNotBlank() } ?: return false
        val isTv = link.season != null && link.episode != null

        val streams = coroutineScope {
            // In parallel with the WebView, so the subtitles add no waiting.
            launch { addSubtitles(link, subtitleCallback) }
            try {
                catchStreams(title, id, isTv, link.season, link.episode)
            } catch (e: Exception) {
                Log.d(TAG, "webview failed: $e")
                emptyList()
            }
        }

        streams.forEach { stream ->
            val cookie = runCatching { CookieManager.getInstance().getCookie(stream.url) }.getOrNull()?.takeIf { it.isNotBlank() }
            val headers = mutableMapOf("Origin" to mainUrl)
            stream.userAgent?.let { headers["User-Agent"] = it }
            val isDash = stream.url.contains(".mpd")
            val label = stream.url.substringAfter("://").substringBefore("/")
                .split('.').let { parts -> if (parts.size >= 2) parts[parts.size - 2] else parts.first() }
            callback(
                newExtractorLink(
                    name,
                    "$name $label",
                    stream.url,
                    type = if (isDash) ExtractorLinkType.DASH else ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.headers = if (cookie != null) headers + ("Cookie" to cookie) else headers
                    this.quality = stream.height ?: Qualities.Unknown.value
                }
            )
        }
        Log.d(TAG, "links: ${streams.size}")
        return streams.isNotEmpty()
    }

    private class Stream(val url: String, val userAgent: String?, val height: Int?)

    private class Seen(val at: Long, val url: String, val userAgent: String?)

    /**
     * Search -> candidate codes -> watch pages until /api/availability/<type>/<id> matches -> collect
     * the manifests the matching page requests for COLLECT_MS after the first one.
     */
    private suspend fun catchStreams(title: String, id: Int, isTv: Boolean, season: Int?, episode: Int?): List<Stream> {
        val type = if (isTv) "tv" else "movie"
        val availability = ConcurrentLinkedQueue<Pair<Long, Int>>()
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
                        AVAILABILITY.find(url)?.let { m ->
                            if (m.groupValues[1] == type) availability.add(System.currentTimeMillis() to (m.groupValues[2].toIntOrNull() ?: -1))
                        }
                        if (MANIFEST.containsMatchIn(url) && !url.contains("proxy2.spacedom")) {
                            val ua = request.requestHeaders?.entries?.firstOrNull { it.key.equals("User-Agent", true) }?.value
                            manifests.add(Seen(System.currentTimeMillis(), url, ua))
                        }
                        return null
                    }
                }
            }
        }

        suspend fun load(url: String) = withContext(Dispatchers.Main) {
            webView?.loadUrl(url, mapOf("Referer" to "$mainUrl/"))
        }

        suspend fun evalString(js: String): String? {
            val result = kotlinx.coroutines.CompletableDeferred<String?>()
            withContext(Dispatchers.Main) {
                webView?.evaluateJavascript(js) { raw -> result.complete(runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()) }
                    ?: result.complete(null)
            }
            return result.await()
        }

        try {
            Log.d(TAG, "search: $title ($type $id)")
            load("$mainUrl/search?q=${URLEncoder.encode(title, "UTF-8")}")
            // The results are rendered by the page; read the codes of the right type in page order.
            var codes = emptyList<String>()
            for (i in 0 until SEARCH_WAIT_S) {
                delay(1000)
                val json = evalString(CODES_JS.replace("__TYPE__", type)) ?: continue
                codes = runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
                if (codes.isNotEmpty()) break
            }
            Log.d(TAG, "candidates: ${codes.take(MAX_CANDIDATES)}")

            for (code in codes.take(MAX_CANDIDATES)) {
                val watchUrl = if (isTv) "$mainUrl/watch/tv/$code?season=$season&episode=$episode" else "$mainUrl/watch/movie/$code"
                val startedAt = System.currentTimeMillis()
                load(watchUrl)
                // The watch page asks for its availability first; its id tells whether this is ours.
                var seenId: Int? = null
                while (seenId == null && System.currentTimeMillis() - startedAt < MATCH_WAIT_MS) {
                    delay(250)
                    seenId = availability.lastOrNull { it.first >= startedAt }?.second
                }
                if (seenId != id) {
                    Log.d(TAG, "candidate $code is ${seenId ?: "unknown"}, not $id")
                    continue
                }
                Log.d(TAG, "candidate $code matches")
                // Collect the manifests for a few seconds after the first one (several servers load).
                var firstAt = 0L
                while (System.currentTimeMillis() - startedAt < STREAM_WAIT_MS) {
                    delay(500)
                    val first = manifests.firstOrNull { it.at >= startedAt } ?: continue
                    if (firstAt == 0L) firstAt = first.at
                    if (System.currentTimeMillis() - firstAt > COLLECT_MS) break
                }
                return pickPerHost(manifests.filter { it.at >= startedAt })
            }
            return emptyList()
        } finally {
            withContext(Dispatchers.Main) {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
            }
        }
    }

    /** One manifest per host: a master if there is one, else the variant with the largest height. */
    private fun pickPerHost(seen: List<Seen>): List<Stream> {
        return seen.groupBy { it.url.substringAfter("://").substringBefore("/") }.map { (_, list) ->
            val master = list.firstOrNull { MASTER_NAME.containsMatchIn(it.url) || it.url.contains(".mpd") }
            val chosen = master ?: list.maxByOrNull { heightOf(it.url) ?: 0 }!!
            Stream(chosen.url, chosen.userAgent, if (master == null) heightOf(chosen.url) else null)
        }.also { streams -> streams.forEach { Log.d(TAG, "stream: ${it.url.substringAfter("://").substringBefore("?").take(80)}") } }
    }

    private fun heightOf(url: String): Int? = Regex("""/(\d{3,4})x(\d{3,4})/""").find(url)?.groupValues?.get(2)?.toIntOrNull()

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
        private const val TAG = "Spacedom"
        private const val OPENSUBTITLES_ADDON = "https://opensubtitles-v3.strem.io"

        private const val SEARCH_WAIT_S = 15
        private const val MAX_CANDIDATES = 4
        private const val MATCH_WAIT_MS = 8_000L
        // Servers answered 2 to 5 s after the page opened in Chrome on a phone.
        private const val STREAM_WAIT_MS = 30_000L
        private const val COLLECT_MS = 5_000L

        private val AVAILABILITY = Regex("""spacedom\.fun/api/availability/(movie|tv)/(\d+)""")
        private val MANIFEST = Regex("""\.(m3u8|mpd)(\?|$)""")
        private val MASTER_NAME = Regex("""master\.m3u8|playlist\.m3u8|\.urlset/""")

        private val BIBLIOGRAPHIC_TO_TERMINOLOGY = mapOf(
            "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho",
            "cze" to "ces", "dut" to "nld", "fre" to "fra", "geo" to "kat", "ger" to "deu",
            "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa",
            "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
        )

        // The search page's result links of one type (/movie/<code> or /tv/<code>), in page order.
        private const val CODES_JS = """
            (function () {
                var out = [];
                Array.prototype.forEach.call(document.querySelectorAll('a[href]'), function (a) {
                    var m = (a.getAttribute('href') || '').match(/^\/(?:watch\/)?__TYPE__\/([A-Za-z0-9]{6,12})(?:[?#]|$)/);
                    if (m && out.indexOf(m[1]) < 0) out.push(m[1]);
                });
                return JSON.stringify(out);
            })();
        """
    }
}
