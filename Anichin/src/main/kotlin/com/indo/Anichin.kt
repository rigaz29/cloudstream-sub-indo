package com.indo

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document

class Anichin : MainAPI() {
    override var mainUrl = "https://anichin.moe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.TvSeries)

    // Keys, not URLs: mainUrl can move to a new domain (see ensureMainUrl).
    // The home page has no "Popular Today" block any more; it has "Rilisan Terbaru" and a
    // "Rekomendasi" block with one tab per genre.
    override val mainPage = mainPageOf(
        LATEST to "Rilisan Terbaru",
        RECOMMENDED to "Rekomendasi",
    )

    private var mainUrlCheckedAt = 0L
    private val mainUrlLock = Mutex()

    /**
     * anichin.care is the site's official landing page: it points to the current main domain with a
     * meta refresh and a "Main Website" link (anichin.moe now; it also lists old domains). Read it at
     * most once per MAIN_URL_CHECK_MS and switch mainUrl when it points elsewhere; when it cannot be
     * read, the last known domain stays.
     */
    private suspend fun ensureMainUrl() {
        if (System.currentTimeMillis() - mainUrlCheckedAt < MAIN_URL_CHECK_MS) return
        mainUrlLock.withLock {
            if (System.currentTimeMillis() - mainUrlCheckedAt < MAIN_URL_CHECK_MS) return
            mainUrlCheckedAt = System.currentTimeMillis()
            val doc = runCatching { app.get(BACKUP_URL, timeout = 8L).document }
                .onFailure { Log.d(TAG, "backup page failed: $it") }.getOrNull() ?: return
            val refresh = doc.selectFirst("meta[http-equiv=refresh]")?.attr("content")
                ?.let { Regex("""url\s*=\s*(\S+)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            val button = doc.select("a[href]").firstOrNull { it.text().contains("Main Website", true) }?.attr("href")
            val target = (refresh ?: button)?.let { Regex("""^(https?://[^/]+)""").find(it.trim())?.groupValues?.get(1) }
            if (target == null || !target.contains("anichin") || target.contains(BACKUP_HOST)) {
                Log.d(TAG, "backup page gave no main domain, keeping $mainUrl")
                return
            }
            if (target != mainUrl) {
                Log.d(TAG, "main domain moved: $mainUrl -> $target (via $BACKUP_HOST)")
                mainUrl = target
            }
        }
    }

    /** Links stored from an older domain: keep their path, use the current domain. */
    private fun onCurrentDomain(url: String): String {
        val full = fixUrl(url)
        val m = Regex("""^https?://[^/]*anichin[^/]*(/.*)?$""").find(full) ?: return full
        return mainUrl + (m.groupValues[1].ifBlank { "/" })
    }

    private fun parseItems(doc: org.jsoup.nodes.Element, selector: String): List<SearchResponse> {
        return doc.select(selector).mapNotNull { item ->
            val a = item.selectFirst(".bsx > a") ?: return@mapNotNull null
            val href = fixUrl(a.attr("href"))
            val poster = item.selectFirst(".limit img")?.let { img ->
                img.attr("data-src").ifBlank { null } ?: img.attr("src").ifBlank { null }
            }?.let { fixUrl(it) }

            val type = item.selectFirst(".typez")?.text()?.trim()
            val tvType = when (type) {
                "Movie" -> TvType.AnimeMovie
                else -> TvType.Anime
            }

            val title = item.selectFirst(".tt")?.ownText()?.trim()?.ifBlank { null }
                ?: item.selectFirst(".tt h2")?.text()?.trim()?.ifBlank { null }
                ?: return@mapNotNull null

            val epText = item.selectFirst(".bt .epx")?.text()?.trim()
            val epNum = Regex("""(\d+)""").find(epText ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addSub(epNum)
            }
        }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        if (request.data == RECOMMENDED) {
            if (page > 1) return newHomePageResponse(emptyList<HomePageList>(), false)
            val doc = app.get("$mainUrl/").document
            // .series-gen: a.nav-tabs link per genre (#series-<id>) and a .tab-pane with that id.
            val gen = doc.selectFirst(".series-gen") ?: return newHomePageResponse(emptyList<HomePageList>(), false)
            val rows = gen.select("ul.nav-tabs a[href^=#]").mapNotNull { tab ->
                val pane = gen.getElementById(tab.attr("href").removePrefix("#")) ?: return@mapNotNull null
                val items = parseItems(pane, "article.bs")
                if (items.isEmpty()) null else HomePageList("${request.name}: ${tab.text().trim()}", items)
            }
            return newHomePageResponse(rows, false)
        }

        val doc = app.get(if (page > 1) "$mainUrl/page/$page/" else "$mainUrl/").document
        val items = parseItems(doc, ".releases.latesthome + .listupd article.bs, .listupd.normal article.bs")
        return newHomePageResponse(HomePageList(request.name, items), items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val doc = app.get("$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}").document
        return parseItems(doc, "div.listupd article.bs")
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val fullUrl = onCurrentDomain(url)
        val doc = app.get(fullUrl).document

        val breadcrumbSeries = doc.selectFirst(".ts-breadcrumb [itemprop=itemListElement]:nth-child(2) a [itemprop=name]")?.text()?.trim()
            ?: doc.selectFirst(".ts-breadcrumb [itemprop=itemListElement]:nth-child(2) a")?.text()?.trim()

        val title = breadcrumbSeries
            ?: doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst(".infolimit h2")?.text()?.trim()
            ?: throw ErrorLoadingException("Title not found")

        val poster = doc.selectFirst(".thumb img, .thumbook img")?.let { img ->
            img.attr("data-src").ifBlank { null } ?: img.attr("src").ifBlank { null }
        }?.let { fixUrl(it) } ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.let { fixUrl(it) }

        val synopsis = doc.select(".desc p, .entry-content p, .synp p").text().trim().ifBlank { null }
        val tags = doc.select(".genxed a, a[href*=/genres/]").mapNotNull { it.text().trim().ifBlank { null } }.distinct()

        val statusText = doc.selectFirst(".spe span:contains(Status)")?.text()?.trim()
        val status = when {
            statusText?.contains("Ongoing", ignoreCase = true) == true -> ShowStatus.Ongoing
            statusText?.contains("Completed", ignoreCase = true) == true -> ShowStatus.Completed
            else -> null
        }

        val year = Regex("""\b(20\d{2}|19\d{2})\b""").find(
            doc.select(".spe span:contains(Tanggal rilis), .spe span:contains(Released)").text()
        )?.groupValues?.getOrNull(1)?.toIntOrNull()

        val eplisterElements = doc.select(".eplister ul li a")
        val episodelistElements = doc.select(".episodelist ul li a")

        val episodes = if (eplisterElements.isNotEmpty()) {
            eplisterElements.mapNotNull { a ->
                val epHref = fixUrl(a.attr("href"))
                val epTitle = a.selectFirst(".epl-title")?.text()?.trim()?.ifBlank { null }
                val numText = a.selectFirst(".epl-num")?.text()?.trim() ?: ""
                val epNum = Regex("""\d+""").find(numText)?.value?.toIntOrNull()
                newEpisode(epHref) {
                    this.name = epTitle ?: (if (epNum != null) "Episode $epNum" else numText.ifBlank { "Episode" })
                    this.episode = epNum
                }
            }
        } else if (episodelistElements.isNotEmpty()) {
            episodelistElements.mapNotNull { a ->
                val epHref = fixUrl(a.attr("href"))
                val epThumb = a.selectFirst(".thumbnel img")?.let { img ->
                    img.attr("data-src").ifBlank { null } ?: img.attr("src").ifBlank { null }
                }?.let { fixUrl(it) }
                val epTitle = a.selectFirst(".playinfo h3")?.text()?.trim()?.ifBlank { null }
                val epInfo = a.selectFirst(".playinfo span")?.text()?.trim() ?: ""
                val epNum = Regex("""(?:Eps?|Episode)\s*(\d+)""", RegexOption.IGNORE_CASE).find(epInfo)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: Regex("""(?:Eps?|Episode)\s*(\d+)""", RegexOption.IGNORE_CASE).find(epTitle ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
                newEpisode(epHref) {
                    this.name = epTitle ?: "Episode ${epNum ?: ""}"
                    this.episode = epNum
                    this.posterUrl = epThumb
                }
            }
        } else {
            // Fallback to series page from breadcrumbs if applicable
            val seriesHref = doc.selectFirst(".ts-breadcrumb [itemprop=itemListElement]:nth-child(2) a")?.attr("href")?.let { fixUrl(it) }
            if (seriesHref != null && seriesHref != fullUrl) {
                val seriesDoc = app.get(seriesHref).document
                seriesDoc.select(".eplister ul li a").mapNotNull { a ->
                    val epHref = fixUrl(a.attr("href"))
                    val epTitle = a.selectFirst(".epl-title")?.text()?.trim()?.ifBlank { null }
                    val numText = a.selectFirst(".epl-num")?.text()?.trim() ?: ""
                    val epNum = Regex("""\d+""").find(numText)?.value?.toIntOrNull()
                    newEpisode(epHref) {
                        this.name = epTitle ?: (if (epNum != null) "Episode $epNum" else numText.ifBlank { "Episode" })
                        this.episode = epNum
                    }
                }
            } else {
                emptyList()
            }
        }

        val sortedEpisodes = if (episodes.any { it.episode != null }) {
            episodes.sortedWith(compareBy(nullsLast()) { it.episode })
        } else {
            episodes.reversed()
        }

        val typeText = doc.selectFirst(".typez, .spe span:contains(Tipe), .spe span:contains(Type)")?.text()?.trim()
        val isMovie = typeText?.contains("Movie", ignoreCase = true) == true
        val tvType = if (isMovie) TvType.AnimeMovie else TvType.Anime

        if (sortedEpisodes.isNotEmpty()) {
            return newAnimeLoadResponse(title, fullUrl, tvType) {
                engName = title
                posterUrl = poster
                addEpisodes(DubStatus.Subbed, sortedEpisodes)
                showStatus = status
                this.year = year
                plot = synopsis
                this.tags = tags
            }
        }

        return newMovieLoadResponse(title, fullUrl, TvType.AnimeMovie, fullUrl) {
            this.posterUrl = poster
            this.plot = synopsis
            this.tags = tags
        }
    }

    private suspend fun extractVidhide(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val html = app.get(url, referer = "$mainUrl/").text
            val packed = Regex(
                """eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*d\s*\)\s*\{[\s\S]*?\}\s*\(\s*'((?:[^'\\]|\\.)*+)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'([^']+)'\s*\.split\s*\(\s*'\|\s*'\s*\)\s*\)""",
                RegexOption.IGNORE_CASE
            ).find(html) ?: return

            val encoded = packed.groupValues[1]
            val radix = packed.groupValues[2].toIntOrNull() ?: return
            val count = packed.groupValues[3].toIntOrNull() ?: return
            val dictStr = packed.groupValues[4]
            val dictionary = dictStr.split("|")

            var result = encoded
            for (i in (count - 1) downTo 0) {
                val replacement = dictionary.getOrNull(i)
                if (!replacement.isNullOrEmpty()) {
                    val word = i.toString(radix)
                    result = result.replace(Regex("\\b" + Regex.escape(word) + "\\b"), replacement)
                }
            }

            val m3u8Url = Regex("""['"]file['"]\s*:\s*['"]((?:[^'"]|\\.)*+)['"]""").find(result)?.groupValues?.getOrNull(1)
                ?: Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(result)?.value ?: return

            val links = M3u8Helper.generateM3u8(
                source = "VidHide",
                streamUrl = m3u8Url,
                referer = url,
                headers = mapOf("Referer" to url)
            )
            if (links.isNotEmpty()) {
                links.forEach { callback(it) }
            } else {
                callback.invoke(
                    newExtractorLink("VidHide", "VidHide", m3u8Url) {
                        this.referer = url
                        this.type = ExtractorLinkType.M3U8
                    }
                )
            }
        } catch (_: Exception) { }
    }

    /**
     * rumble.com/embedJS answers with a Cloudflare challenge now; the embed page itself carries the
     * sources: "hls":{"auto":{"url":".../playlist.m3u8"}} (adaptive) among the "ua" entries.
     */
    private suspend fun extractRumble(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val videoId = Regex("""(?:embed/|v=)([a-zA-Z0-9]+)""").find(url)?.groupValues?.getOrNull(1) ?: return
            val page = app.get("https://rumble.com/embed/$videoId/").text.replace("\\/", "/")
            val hls = Regex(""""hls"\s*:\s*\{\s*"auto"\s*:\s*\{\s*"url"\s*:\s*"([^"]+)"""").find(page)?.groupValues?.get(1)
                ?: Regex("""https://rumble\.com/hls-vod/[^"\s]+\.m3u8[^"\s]*""").find(page)?.value
                ?: return
            callback.invoke(
                newExtractorLink("Rumble", "Rumble", hls) {
                    this.referer = "https://rumble.com/"
                    this.type = ExtractorLinkType.M3U8
                    this.quality = Qualities.Unknown.value
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "rumble failed: $e")
        }
    }

    /**
     * Streamruby (embeds on nuvorel.top and other mirrors, an XFileSharing host): the embed page has
     * a plain jwplayer setup, sources: [{file:"https://<cdn>.streamruby.net/.../master.m3u8?..."}].
     */
    private suspend fun extractStreamruby(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val html = app.get(url, referer = "$mainUrl/").text
            val m3u8 = Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(html)?.value
                ?: return
            val origin = Regex("""^(https?://[^/]+)""").find(url)?.groupValues?.get(1) ?: url
            val links = M3u8Helper.generateM3u8("Streamruby", m3u8, "$origin/")
            if (links.isNotEmpty()) links.forEach(callback) else callback.invoke(
                newExtractorLink("Streamruby", "Streamruby", m3u8) {
                    this.referer = "$origin/"
                    this.type = ExtractorLinkType.M3U8
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "streamruby failed: $e")
        }
    }

    /** Odysee embed pages carry the file in their JSON-LD "contentUrl". */
    private suspend fun extractOdysee(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val html = app.get(url).text
            val file = Regex(""""contentUrl"\s*:\s*"(https?://[^"]+)"""").find(html)?.groupValues?.get(1) ?: return
            callback.invoke(
                newExtractorLink("Odysee", "Odysee", file) {
                    this.referer = "https://odysee.com/"
                    this.type = ExtractorLinkType.VIDEO
                    this.quality = Qualities.Unknown.value
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "odysee failed: $e")
        }
    }

    private suspend fun extractOkru(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url).text
            val opts = Regex("""data-options=["']([^"']+)["']""").find(doc)?.groupValues?.getOrNull(1) ?: return
            val jsonStr = opts.replace("&quot;", "\"")
            val root = tryParseJson<Map<String, Any?>>(jsonStr) ?: return
            val flashvars = root["flashvars"] as? Map<*, *> ?: return
            val metadataRaw = flashvars["metadata"]
            val metadata = (metadataRaw as? Map<*, *>) ?: (metadataRaw as? String)?.let { tryParseJson<Map<String, Any?>>(it) } ?: return
            val videos = metadata["videos"] as? List<*> ?: return
            videos.forEach { v ->
                val map = v as? Map<*, *> ?: return@forEach
                val qualityName = map["name"]?.toString() ?: "SD"
                val videoUrl = map["url"]?.toString() ?: return@forEach
                if (!videoUrl.startsWith("http")) return@forEach
                val q = when (qualityName.lowercase()) {
                    "full" -> Qualities.P1080.value
                    "hd" -> Qualities.P720.value
                    "sd" -> Qualities.P480.value
                    "low" -> Qualities.P360.value
                    "lowest", "mobile" -> Qualities.P240.value
                    else -> Qualities.Unknown.value
                }
                callback(
                    newExtractorLink("Okru", "Okru $qualityName", videoUrl) {
                        this.quality = q
                        this.type = ExtractorLinkType.VIDEO
                    }
                )
            }
        } catch (_: Exception) { }
    }

    private suspend fun extractPlaymogo(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url).text
            val passMd5 = Regex("/pass_md5/([^/]+)/([^/\\s\"')]+)").find(doc) ?: return
            val videoBase = app.get("https://playmogo.com${passMd5.value}", referer = url).text.trim()
            if (videoBase.isBlank() || !videoBase.startsWith("http")) return
            val token = passMd5.groupValues[2]
            val expiry = (System.currentTimeMillis() + 86400000).toString()
            val videoUrl = if (videoBase.endsWith("~")) "$videoBase$token?token=$token&expiry=$expiry" else videoBase
            if (!videoUrl.startsWith("http")) return
            callback.invoke(
                newExtractorLink("DoodStream", "DoodStream", videoUrl) {
                    this.referer = "https://playmogo.com"
                    this.headers = mapOf("Referer" to "https://playmogo.com")
                }
            )
        } catch (_: Exception) { }
    }

    private suspend fun extractUrl(
        url: String,
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fullUrl = if (url.startsWith("//")) "https:$url" else url
        when {
            fullUrl.contains("ok.ru") -> {
                extractOkru(fullUrl, callback)
            }
            fullUrl.contains("morencius.com") || fullUrl.contains("minochinos.com") ||
            fullUrl.contains("vidhide") || fullUrl.contains("bingezove.com") || fullUrl.contains("vidhidepre.com") -> {
                extractVidhide(fullUrl, callback)
            }
            fullUrl.contains("rumble.com") -> {
                extractRumble(fullUrl, callback)
            }
            fullUrl.contains("playmogo.com") || fullUrl.contains("dood") -> {
                extractPlaymogo(fullUrl, callback)
            }
            fullUrl.contains("nuvorel.") || fullUrl.contains("streamruby") || fullUrl.contains("rubystm") -> {
                extractStreamruby(fullUrl, callback)
            }
            fullUrl.contains("odysee.com") -> {
                extractOdysee(fullUrl, callback)
            }
            // A Dailymotion player behind a wrapper (player.nunadrama.sbs/index.php?video=<id>,
            // geo.dailymotion.com/player/...?video=<id>): hand the video to the Dailymotion extractor.
            fullUrl.contains("nunadrama") || fullUrl.contains("geo.dailymotion.com") -> {
                val id = Regex("""[?&]video=([A-Za-z0-9]+)""").find(fullUrl)?.groupValues?.get(1)
                if (id != null) loadExtractor("https://www.dailymotion.com/embed/video/$id", data, subtitleCallback, callback)
            }
            else -> {
                loadExtractor(fullUrl, data, subtitleCallback, callback)
            }
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
        val doc = app.get(pageUrl).document

        // Every embed once: the player iframe is usually also one of the mirror options.
        val embeds = linkedMapOf<String, String>()
        doc.select("#embed_holder iframe[src], .player-embed iframe[src], #pembed iframe[src]").forEach { iframe ->
            val src = iframe.attr("src").trim().ifBlank { return@forEach }
            embeds.putIfAbsent(fullEmbedUrl(src), "player")
        }

        doc.select("select.mirror option").forEach { option ->
            val encoded = option.attr("value").trim().ifBlank { return@forEach }
            if (encoded.length < 10) return@forEach
            val label = option.text().trim()

            if (encoded.startsWith("http://") || encoded.startsWith("https://")) {
                embeds.putIfAbsent(fullEmbedUrl(encoded), label)
                return@forEach
            }

            try {
                val decoded = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)).trim()
                val iframeSrc = Regex("""<iframe[^>]+src=["']?([^"'>\s]+)""", RegexOption.IGNORE_CASE)
                    .find(decoded)?.groupValues?.getOrNull(1)?.trim()
                    ?: Regex("""https?://[^\s"'<>]+""").find(decoded)?.value

                if (!iframeSrc.isNullOrBlank()) embeds.putIfAbsent(fullEmbedUrl(iframeSrc), label)
            } catch (_: Exception) { }
        }

        var total = 0
        embeds.forEach { (url, label) ->
            var count = 0
            extractUrl(url, pageUrl, subtitleCallback) { link -> count++; callback(link) }
            total += count
            Log.d(TAG, "embed $label ${url.substringAfter("://").substringBefore("/")}: $count link(s)")
        }
        return total > 0
    }

    private fun fullEmbedUrl(url: String) = if (url.startsWith("//")) "https:$url" else url

    companion object {
        private const val TAG = "Anichin"
        private const val LATEST = "latest"
        private const val RECOMMENDED = "recommended"

        // Official landing page that points to the current main domain.
        private const val BACKUP_HOST = "anichin.care"
        private const val BACKUP_URL = "https://$BACKUP_HOST/"
        private const val MAIN_URL_CHECK_MS = 30 * 60 * 1000L
    }
}

