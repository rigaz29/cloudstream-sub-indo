package com.indo

import android.app.Dialog
import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.regex.PatternSyntaxException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class Kuronime : MainAPI() {
    override var mainUrl = "https://kuronime.sbs"
    override var name = "Kuronime"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    // Homepage with paginated new episodes
    // A key, not a URL: mainUrl can move to a new domain (see ensureMainUrl).
    override val mainPage = mainPageOf(
        NEW_EPISODES to "New Episodes"
    )

    private var mainUrlCheckedAt = 0L
    private val mainUrlLock = Mutex()

    /**
     * kuronime.link (named on the site as the address to bookmark) answers 301 to kuronime.net, the
     * official landing page, whose "KE HALAMAN UTAMA" button (/api/anime/home_page_default) answers
     * 302 to the current main domain. Read that Location at most once per MAIN_URL_CHECK_MS and
     * switch mainUrl when it points elsewhere; when it cannot be read, the last known domain stays.
     */
    private suspend fun ensureMainUrl() {
        if (System.currentTimeMillis() - mainUrlCheckedAt < MAIN_URL_CHECK_MS) return
        mainUrlLock.withLock {
            if (System.currentTimeMillis() - mainUrlCheckedAt < MAIN_URL_CHECK_MS) return
            mainUrlCheckedAt = System.currentTimeMillis()
            val location = runCatching {
                app.get(BACKUP_URL, allowRedirects = false, timeout = 8L).okhttpResponse.header("Location")
            }.onFailure { Log.d(TAG, "backup check failed: $it") }.getOrNull()
            val target = location?.let { Regex("""^(https?://[^/]+)""").find(it.trim())?.groupValues?.get(1) }
            if (target == null || !target.contains("kuronime") || target.contains("kuronime.net")) {
                Log.d(TAG, "backup gave no main domain, keeping $mainUrl")
                return
            }
            if (target != mainUrl) {
                Log.d(TAG, "main domain moved: $mainUrl -> $target")
                mainUrl = target
            }
        }
    }

    /** Links stored from an older domain: keep their path, use the current domain. */
    private fun onCurrentDomain(url: String): String {
        val full = fixUrl(url)
        val m = Regex("""^https?://[^/]*kuronime[^/]*(/.*)?$""").find(full) ?: return full
        return mainUrl + (m.groupValues[1].ifBlank { "/" })
    }

    private fun episodeToAnimeUrl(url: String): String {
        val path = url.trimEnd('/').substringAfterLast("/")
        val withoutPrefix = if (path.startsWith("nonton-")) path.removePrefix("nonton-") else path
        val slug = try {
            Regex("-episode-\\d+.*$", RegexOption.IGNORE_CASE).replace(withoutPrefix, "")
        } catch (e: PatternSyntaxException) {
            val idx = withoutPrefix.indexOf("-episode-", ignoreCase = true)
            if (idx != -1) withoutPrefix.substring(0, idx) else withoutPrefix
        }
        return "$mainUrl/anime/$slug/"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        val document = app.get(if (page > 1) "$mainUrl/page/$page/" else "$mainUrl/").document

        // Items in bixbox > listupd > excstf > article.bsu
        val items = document.select("article.bsu").mapNotNull { article ->
            val bsux = article.selectFirst("div.bsux") ?: return@mapNotNull null
            val a = bsux.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("href").ifBlank { null } ?: return@mapNotNull null

            // Title from div.bsuxtt > h2 (clean title without episode info)
            val title = bsux.selectFirst("div.bsuxtt h2")?.text()?.trim()
                ?: return@mapNotNull null

            // Poster from img[itemprop=image] inside div.limit
            val poster = a.selectFirst("div.limit img[itemprop=image]")?.attr("src")?.ifBlank { null }
                ?: a.selectFirst("div.limit img")?.attr("src")?.ifBlank { null }

            // Episode number from div.bt > div.ep
            val epText = a.selectFirst("div.bt div.ep")?.text()?.trim() ?: ""
            val epNum = Regex("(\\d+)").find(epText)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val animeUrl = episodeToAnimeUrl(href)

            newAnimeSearchResponse(title, animeUrl, TvType.Anime) {
                this.posterUrl = poster
                addSub(epNum)
            }
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val document = app.get("$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}").document
        return document.select("article.bs, article.bsu").mapNotNull { article ->
            val bsux = article.selectFirst("div.bsx, div.bsux") ?: article
            val a = bsux.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("href").ifBlank { null } ?: return@mapNotNull null

            val title = a.attr("title").trim().ifBlank { null }
                ?: bsux.selectFirst("div.bsuxtt h2, div.tt h2, h2")?.text()?.trim()?.ifBlank { null }
                ?: return@mapNotNull null

            val poster = a.selectFirst("img[itemprop=image]")?.let { it.attr("src").ifBlank { it.attr("data-src") } }
                ?: a.selectFirst("img:not([src*=controls-play])")?.let { it.attr("src").ifBlank { it.attr("data-src") } }
                ?: a.selectFirst("div.limit img")?.attr("src")?.ifBlank { null }

            val epText = a.selectFirst("div.bt div.ep")?.text()?.trim() ?: ""
            val epNum = Regex("(\\d+)").find(epText)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val animeUrl = if (href.contains("/anime/")) href else episodeToAnimeUrl(href)

            newAnimeSearchResponse(title, animeUrl, TvType.Anime) {
                this.posterUrl = poster
                addSub(epNum)
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val current = onCurrentDomain(url)
        val animeUrl = if (current.contains("/anime/")) current else episodeToAnimeUrl(current)
        val document = app.get(animeUrl).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: throw ErrorLoadingException("Title not found")

        val cleanTitle = title
            .replace(Regex("\\s*Subtitle Indonesia.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*Sub\\s*Indo.*", RegexOption.IGNORE_CASE), "")
            .trim()

        val poster = document.selectFirst("div.ime img, div.thumb img")?.let { img ->
            img.attr("data-src").ifBlank { null } ?: img.attr("src").ifBlank { null }
        } ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }

        val description = document.selectFirst("div.synp p, div.entry-content p")?.text()?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")

        val genres = document.select("div.genxed a, a[href*=/genres/]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        // Episodes: li elements containing a[href] with /nonton-...-episode-N/ URLs
        val episodes = document.select("div.bixbox ul li").mapNotNull { li ->
            val a = li.selectFirst("a[href*=nonton-]") ?: return@mapNotNull null
            val href = a.attr("href").ifBlank { null } ?: return@mapNotNull null
            val epText = a.text().trim().ifBlank { null } ?: return@mapNotNull null
            val epNum = Regex("(\\d+)").find(
                epText.replace(Regex("Episode\\s*", RegexOption.IGNORE_CASE), "")
            )?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("episode-(\\d+)", RegexOption.IGNORE_CASE).find(href)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
            newEpisode(fixUrl(href)) {
                this.name = "Episode ${epNum ?: epText}"
                this.episode = epNum
            }
        }.distinctBy { it.data }.sortedBy { it.episode }

        return newAnimeLoadResponse(cleanTitle, animeUrl, TvType.Anime) {
            engName = cleanTitle
            posterUrl = poster
            backgroundPosterUrl = poster
            plot = description
            this.tags = genres.distinct()
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureMainUrl()
        val pageUrl = onCurrentDomain(data)
        val document = app.get(pageUrl).document
        var found = 0

        // Download links.
        document.select("a[href]").forEach { a ->
            val href = a.attr("href").ifBlank { null } ?: return@forEach
            when {
                href.contains("krakenfiles.com") -> {
                    try {
                        val kfDoc = app.get(href).document
                        val videoUrl = kfDoc.selectFirst("source[src*=krakencloud], source[type=video/mp4]")
                            ?.attr("src")?.ifBlank { null }
                        if (videoUrl != null) {
                            found++
                            callback(newExtractorLink("KrakenFiles", "Download", videoUrl) {
                                this.quality = Qualities.Unknown.value
                                this.referer = href
                            })
                        }
                    } catch (_: Exception) { }
                }
                href.contains("pixeldrain.com") -> {
                    val pdId = Regex("pixeldrain\\.com/u/(\\w+)").find(href)?.groupValues?.getOrNull(1)
                    if (pdId != null) {
                        found++
                        callback(newExtractorLink("PixelDrain", "Download", "https://pixeldrain.com/api/file/$pdId") {
                            this.quality = Qualities.Unknown.value
                            this.referer = href
                        })
                    }
                }
            }
        }

        // Streaming: see playThroughSite.
        val caught = try {
            playThroughSite(pageUrl)
        } catch (e: Exception) {
            Log.d(TAG, "player window failed: $e")
            null
        }
        if (caught != null) {
            fun header(name: String) = caught.headers.entries.firstOrNull { it.key.equals(name, true) }?.value
            val linkHeaders = mutableMapOf<String, String>()
            header("User-Agent")?.let { linkHeaders["User-Agent"] = it }
            header("Origin")?.let { linkHeaders["Origin"] = it }
            runCatching { CookieManager.getInstance().getCookie(caught.url) }.getOrNull()
                ?.takeIf { it.isNotBlank() }?.let { linkHeaders["Cookie"] = it }
            found++
            callback(newExtractorLink(name, "$name HLS", caught.url, type = ExtractorLinkType.M3U8) {
                this.referer = header("Referer") ?: "$mainUrl/"
                this.headers = linkHeaders
                this.quality = Qualities.Unknown.value
            })
        }
        Log.d(TAG, "links: $found")
        return found > 0
    }

    private class Caught(val url: String, val headers: Map<String, String>)

    /**
     * The episode page's player is filled by the site's own script (assets.animeku.org/.../
     * kuronime.js): it posts to animeku.org/api/v9/sources, decrypts the answer in the page and
     * loads player.animeku.org, which plays an HLS master (kuroplayer.xyz/.../index.m3u8) or another
     * mirror (VidHide). It only starts after a real tap on the play button, so the page is shown in
     * a WebView on screen and the viewer taps it; the first .m3u8 the player requests is taken (the
     * test-streams.mux.dev playlist it loads first is a decoy). Nothing here reads or decrypts the
     * site's data, and the page may not navigate to other sites (ads). Closing the window cancels.
     */
    private suspend fun playThroughSite(pageUrl: String): Caught? {
        val activity = CommonActivity.activity ?: run {
            Log.d(TAG, "no activity to show the player window")
            return null
        }
        val found = CompletableDeferred<Caught?>()
        var webView: WebView? = null
        var dialog: Dialog? = null
        val siteHost = Regex("""^https?://([^/]+)""").find(mainUrl)?.groupValues?.get(1).orEmpty()
        Log.d(TAG, "player window: $pageUrl")
        withContext(Dispatchers.Main) {
            val view = WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // Ads must not take the page elsewhere.
                        val host = request.url.host.orEmpty()
                        val keep = host == siteHost || host.endsWith(".$siteHost")
                        if (!keep) Log.d(TAG, "navigation blocked: $host")
                        return !keep
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url.toString()
                        if (PLAYLIST_URL.containsMatchIn(url) && !DECOY_URL.containsMatchIn(url) && !found.isCompleted) {
                            found.complete(Caught(url, request.requestHeaders ?: emptyMap()))
                        }
                        return null
                    }
                }
            }
            webView = view

            val hint = TextView(activity).apply {
                text = "Ketuk tombol putar di halaman ini (tutup iklan bila menutupi). Video dibuka otomatis di CloudStream."
                setTextColor(Color.WHITE)
                textSize = 13f
            }
            val close = Button(activity).apply { text = "Tutup" }
            val bar = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 12, 12, 12)
                addView(hint, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(close)
            }
            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.BLACK)
                addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
            dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen).apply {
                setContentView(root)
                setOnDismissListener { if (!found.isCompleted) found.complete(null) }
                close.setOnClickListener { dismiss() }
                show()
            }
            view.loadUrl(pageUrl, mapOf("Referer" to "$mainUrl/"))
        }

        var scrolled = false
        val caught = withTimeoutOrNull(WINDOW_TIMEOUT_MS) {
            // Once the page is up, bring the play button into view (the viewer still taps it).
            while (withTimeoutOrNull(1500) { found.await() } == null && !found.isCompleted) {
                if (!scrolled) withContext(Dispatchers.Main) {
                    webView?.evaluateJavascript(SCROLL_JS) { r -> if (r == "true") scrolled = true }
                }
            }
            found.await()
        }
        withContext(Dispatchers.Main) {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            runCatching { dialog?.dismiss() }
            dialog = null
        }
        if (caught == null) Log.d(TAG, "no stream from the player window")
        else Log.d(TAG, "stream: ${caught.url.substringAfter("://").substringBefore("/")}")
        return caught
    }

    companion object {
        private const val TAG = "Kuronime"
        private const val NEW_EPISODES = "new-episodes"

        // kuronime.net's "to the main page" button; answers 302 to the current main domain.
        private const val BACKUP_URL = "https://kuronime.net/api/anime/home_page_default?ref=single_page"
        private const val MAIN_URL_CHECK_MS = 30 * 60 * 1000L

        // The viewer taps play and may close an ad first.
        private const val WINDOW_TIMEOUT_MS = 3 * 60 * 1000L

        private val PLAYLIST_URL = Regex("""\.m3u8(\?|$)""")

        // A public test stream the player requests before the real one.
        private val DECOY_URL = Regex("""test-streams\.mux\.dev|mux\.dev/""")

        private const val SCROLL_JS = """
            (function () {
                var b = document.getElementById('play-asu') || document.getElementById('pembed');
                if (!b) return false;
                b.scrollIntoView({ block: 'center' });
                return true;
            })();
        """
    }
}
