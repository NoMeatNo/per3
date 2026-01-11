package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * IranWiz Provider - Persian Live TV from GLWiz
 * 
 * Uses custom OkHttp client with proper CookieJar to maintain session
 * across requests. This is required because GLWiz returns different
 * content based on session cookies.
 */
class IranWizProvider : MainAPI() {
    override var mainUrl = "https://www.glwiz.com"
    override var name = "IranWiz"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)
    
    private val playerBaseUrl = "$mainUrl/Pages/Player"
    private val ajaxUrl = "$playerBaseUrl/Ajax.aspx"
    
    // Custom OkHttp client with cookie persistence
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    cookieStore.getOrPut(host) { mutableListOf() }.apply {
                        // Remove old cookies with same name
                        cookies.forEach { newCookie ->
                            removeAll { it.name == newCookie.name }
                        }
                        addAll(cookies)
                    }
                }
                
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val host = url.host
                    return cookieStore[host]?.filter { !it.expiresAt.let { exp -> exp < System.currentTimeMillis() } } ?: emptyList()
                }
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // Channel data class
    data class Channel(
        val internalName: String,
        val displayName: String,
        val id: Int,
        val genreId: Int
    ) {
        val logoUrl: String get() = "https://hd200.glwiz.com/menu/epg/imagesNew/cim_${id}.png"
    }
    
    // Channel list
    private val allChannels = listOf(
        // Persian (Genre 1)
        Channel("TapeshAmerica", "Tapesh America", 306606, 1),
        Channel("ChannelOne", "کانال یک", 307514, 1),
        Channel("ITN", "ITN آمریکا و کانادا", 305049, 1),
        Channel("NITV", "NITV", 305315, 1),
        Channel("ParsTv", "Pars TV", 305310, 1),
        Channel("SimayeAzadi", "سیمای آزادی", 305302, 1),
        Channel("PMCTV", "PMC TV", 305064, 1),
        Channel("GEM", "GEM TV", 305059, 1),
        Channel("ManotoTV", "Manoto", 305055, 1),
        
        // News (Genre 89)
        Channel("IranInternational", "ایران اینترنشنال", 306823, 89),
        Channel("BBCPersian", "BBC Persian", 305032, 89),
        Channel("VOA", "صدای آمریکا", 305316, 89),
        Channel("EuronewsFarsi", "یورونیوز فارسی", 305312, 89),
        Channel("IraneFarda", "ایران فردا", 305306, 89),
        Channel("DWPersian", "DW فارسی", 306788, 89),
        Channel("France24Persian", "France 24 فارسی", 306786, 89),
        
        // IRIB (Genre 2)
        Channel("IRIB1", "شبکه یک", 305023, 2),
        Channel("IRIB2", "شبکه دو", 305024, 2),
        Channel("IRIB3", "شبکه سه", 305025, 2),
        Channel("IRIB4", "شبکه چهار", 305026, 2),
        Channel("IRIB5", "شبکه پنج", 305027, 2),
        Channel("IRIBNASIM", "نسیم", 305300, 2),
        Channel("IRIBTAMASHA", "تماشا", 305301, 2),
        Channel("IRIBNEWS", "شبکه خبر", 305028, 2),
        Channel("IRIBVarzesh", "ورزش", 305303, 2),
        Channel("IRIBIfilm", "آی فیلم", 305305, 2),
        
        // Music (Genre 10)
        Channel("PMC", "PMC", 305064, 10),
        
        // Movies & Series (Genre 19)
        Channel("GemMovie", "GEM Movie", 305060, 19),
        Channel("GemSeries", "GEM Series", 305061, 19),
        Channel("Farsi1", "Farsi1", 305062, 19),
        
        // Kids (Genre 4)
        Channel("GemKids", "GEM Kids", 305065, 4),
        Channel("IRIBPooya", "شبکه پویا", 305031, 4),
        
        // Religious (Genre 85)
        Channel("IRIBQuran", "شبکه قرآن", 305030, 85),
        
        // Sports (Genre 84)
        Channel("VarzeshTV", "ورزش TV", 305303, 84),
        
        // Other (Genre 3)
        Channel("AFGTolo", "طلوع", 306001, 3),
        Channel("Ariana", "آریانا", 306003, 3)
    )
    
    override val mainPage = mainPageOf(
        "1" to "📺 Persian",
        "89" to "📰 News",
        "2" to "📡 IRIB",
        "19" to "🎬 Movies & Series",
        "10" to "🎵 Music",
        "84" to "⚽ Sports",
        "4" to "👶 Kids",
        "85" to "🕌 Religious",
        "3" to "🌍 Other Languages"
    )
    
    /**
     * Make HTTP request using our custom client with cookie persistence
     */
    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
        
        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        
        val response = httpClient.newCall(requestBuilder.build()).execute()
        return response.body?.string() ?: ""
    }
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val genreId = request.data.toIntOrNull() ?: 1
        val filtered = allChannels.filter { it.genreId == genreId }
        
        val home = filtered.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/ch/${channel.internalName}",
                TvType.Live
            ) {
                this.posterUrl = channel.logoUrl
            }
        }
        
        return newHomePageResponse(request.name, home)
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        val results = allChannels.filter { 
            it.displayName.contains(query, ignoreCase = true) ||
            it.internalName.contains(query, ignoreCase = true)
        }
        
        return results.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/ch/${channel.internalName}",
                TvType.Live
            ) {
                this.posterUrl = channel.logoUrl
            }
        }
    }
    
    override suspend fun load(url: String): LoadResponse {
        val channelName = url.substringAfterLast("/ch/")
        val channel = allChannels.find { it.internalName == channelName }
        
        return newMovieLoadResponse(
            channel?.displayName ?: channelName,
            url,
            TvType.Live,
            channelName
        ) {
            this.posterUrl = channel?.logoUrl ?: ""
            this.plot = "📺 Live stream from GLWiz\n\nChannel: ${channel?.displayName ?: channelName}"
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val channelName = data
            
            // Clear old cookies and get fresh session
            cookieStore.clear()
            
            // Step 1: Initialize session (cookies will be saved automatically)
            httpGet("$playerBaseUrl/Player.aspx")
            
            // Step 2: Get stream URL (cookies will be sent automatically)
            val ajaxUrl = "$ajaxUrl?action=getStreamURL&ClusterName=zixi-glwiz-mobile&RecType=4&itemName=$channelName&ScreenMode=0"
            val response = httpGet(ajaxUrl, mapOf(
                "Referer" to "$playerBaseUrl/p2.html",
                "X-Requested-With" to "XMLHttpRequest"
            ))
            
            // Extract stream URL
            val respRegex = Regex(""""resp"\s*:\s*"([^"]+)"""")
            val match = respRegex.find(response)
            
            if (match != null) {
                val streamUrl = match.groupValues[1]
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
                
                // Check if it's not the promo stream
                if (!streamUrl.contains("GlwizPromo")) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name - $channelName",
                            url = streamUrl
                        ).apply {
                            this.quality = Qualities.Unknown.value
                            this.referer = "$playerBaseUrl/p2.html"
                            this.headers = mapOf(
                                "Referer" to "$playerBaseUrl/p2.html",
                                "Origin" to mainUrl
                            )
                        }
                    )
                    return true
                }
            }
            
            false
        } catch (e: Exception) {
            false
        }
    }
}
