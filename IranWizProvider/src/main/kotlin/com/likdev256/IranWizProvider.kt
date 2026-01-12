package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * IranWiz Provider - Persian Live TV from GLWiz
 * 
 * Uses custom OkHttpClient with proper CookieJar to maintain session.
 * Channel names extracted directly from GLWiz localStorage (with spaces!).
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
    
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    cookieStore.getOrPut(host) { mutableListOf() }.apply {
                        cookies.forEach { newCookie ->
                            removeAll { it.name == newCookie.name }
                        }
                        addAll(cookies)
                    }
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host]?.filter { it.expiresAt >= System.currentTimeMillis() } ?: emptyList()
                }
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // Channel data class - name is the EXACT internal name from GLWiz (with spaces!)
    data class Channel(
        val name: String,           // Internal name for stream URL (e.g., "Iran International")
        val displayName: String,    // Display name (Persian or English)
        val id: Int,                // For logo URL
        val genreId: Int            // For category filtering
    ) {
        val logoUrl: String get() = "https://hd200.glwiz.com/menu/epg/imagesNew/cim_${id}.png"
    }
    
    // Complete channel list with CORRECT names from GLWiz localStorage
    private val allChannels = listOf(
        // ===== Persian (Genre 1) =====
        Channel("Iran International", "ایران اینترنشنال", 306823, 1),
        Channel("BBC Persian", "بی بی سی فارسی", 301133, 1),
        Channel("VOA", "صدای آمریکا", 300480, 1),
        Channel("EuroNews Farsi", "یورونیوز فارسی", 301404, 1),
        Channel("Iran e Farda", "ایران فردا", 302551, 1),
        Channel("Afghanistan International", "افغانستان اینترنشنال", 307415, 1),
        Channel("Tapesh America", "Tapesh America", 306606, 1),
        Channel("Channel One", "کانال یک", 307514, 1),
        Channel("ITN", "ITN آمریکا و کانادا", 305049, 1),
        Channel("Pars TV", "تلویزیون پارس", 300454, 1),
        Channel("TAPESH", "تپش", 300447, 1),
        Channel("MBC Persia", "MBC Persia", 300513, 1),
        Channel("RJ TV", "RJ TV", 306141, 1),
        Channel("Tasvir E Iran", "تصویر ایران", 300457, 1),
        Channel("Omid E Iran", "امید ایران", 300462, 1),
        Channel("Mihan TV", "میهن", 300902, 1),
        Channel("ITC", "ITC", 300465, 1),
        Channel("Ayeneh TV", "آینه", 300326, 1),
        Channel("T2 International", "T2 International", 307381, 1),
        Channel("T2 America", "T2 America", 307261, 1),
        Channel("Irane Aryaee", "ایران آریایی", 302136, 1),
        Channel("Radio Farda", "رادیوفردا", 306419, 1),
        Channel("Simay Azadi", "سیمای آزادی", 307555, 1),
        Channel("Your Time TV", "یورتایم", 307230, 1),
        Channel("Ekran Movies", "Ekran Movies", 306747, 1),
        Channel("Ekran Kids", "Ekran Kids", 306831, 1),
        Channel("Film 1", "Film 1", 303523, 1),
        Channel("Classic TV", "Classic TV", 302082, 1),
        Channel("Today", "Today", 303003, 1),
        Channel("Mohabat", "محبت", 300931, 1),
        Channel("Salaam", "سلام", 301178, 1),
        Channel("Asre Emrooz", "عصر امروز", 301196, 1),
        Channel("Nahade Azadi", "Nahade Azadi", 307573, 1),
        Channel("TM TV", "TM TV", 301163, 1),
        Channel("PTV1", "PTV1", 300463, 1),
        Channel("AFN", "AFN", 300594, 1),
        Channel("Didgah", "دیدگاه", 305207, 1),
        Channel("Payvand", "پیوند", 305183, 1),
        Channel("ITN Europe", "ITN ایران", 305190, 1),
        Channel("Royal Time TV", "Royal Time", 305269, 1),
        Channel("Shabakeh 7", "شبکه‌۷", 305654, 1),
        Channel("ICnet 1", "ICnet 1", 305143, 1),
        Channel("ICnet 3", "ICnet 3", 300448, 1),
        Channel("ICC", "سینمایی ایرانیان", 302078, 1),
        Channel("Khane Honarmandan", "خانه‌هنرمندان", 306578, 1),
        Channel("Ganj E Hozour", "گنج حضور", 301192, 1),
        Channel("Erfan Halgheh", "عرفان حلقه", 300936, 1),
        Channel("Payame Aramesh", "پیام آرامش", 303657, 1),
        Channel("Woman TV", "زن زندگی‌آزادی", 307008, 1),
        Channel("PJ TV", "پیام جوان", 303012, 1),
        Channel("RVTV", "Radio Vanak TV", 302604, 1),
        Channel("Israel Pars TV", "إسرائيل پارس", 307103, 1),
        
        // Persiana Network
        Channel("Persiana Family", "پرشیانا فمیلی", 306882, 1),
        Channel("Persiana Cinema", "پرشیانا سینما", 306892, 1),
        Channel("Persiana Comedy", "Persiana Comedy", 307313, 1),
        Channel("Persiana Korean", "Persiana Korea", 307341, 1),
        Channel("Persiana Iranian", "پرشیانا ایرانیان", 307273, 1),
        Channel("Persiana Series", "Persiana Series", 307301, 1),
        Channel("Persiana Plus", "Persiana Plus", 307361, 1),
        Channel("Persiana Docs", "Persiana Docs", 307478, 1),
        Channel("Persiana Reality", "Persiana Reality", 307491, 1),
        Channel("Persiana Junior", "پرشیانا جونیور", 307183, 1),
        Channel("Persiana Turkiye", "Persiana Turkiye", 307275, 1),
        
        // AVA Network
        Channel("AVA Family", "AVA Family", 307042, 1),
        Channel("AVA Series", "AVA Series", 307252, 1),
        
        // Other Persian
        Channel("Az Star TV", "Az Star TV", 306657, 1),
        Channel("Hambastegi", "Hambastegi", 307483, 1),
        Channel("4U TV", "4U TV", 306743, 1),
        Channel("4u Family", "4u Family", 307168, 1),
        Channel("FX TV", "FX TV", 307384, 1),
        Channel("FX 22", "FX 22", 307450, 1),
        Channel("Net TV", "Net TV", 307305, 1),
        Channel("MTC", "MTC", 307309, 1),
        Channel("Infinity TV", "Infinity TV", 307335, 1),
        Channel("Cafe Trade TV", "Cafe Trade TV", 307410, 1),
        Channel("Grand Cinema", "Grand Cinema", 307227, 1),
        Channel("Oxir", "Oxir", 307182, 1),
        Channel("Iran Proud Series Plus", "Iran Proud Series Plus", 307357, 1),
        Channel("Maah TV", "Maah TV", 307315, 1),
        Channel("Khatereh TV", "خاطره", 307561, 1),
        Channel("Farsi TV", "Farsi TV HD", 307454, 1),
        Channel("IRTV", "IRTV ایرانیان", 307563, 1),
        Channel("Home Plus", "Home Plus", 307565, 1),
        Channel("Didaniha TV", "دیدنیها", 307591, 1),
        Channel("Nima TV", "Nima TV", 307597, 1),
        
        // Religious
        Channel("Nejat TV", "Nejat TV", 307425, 1),
        Channel("Rahe Nejat", "Rahe Nejat", 307511, 1),
        Channel("Iman Be Masih", "Iman Be Masih", 307373, 1),
        Channel("Derakhte Zendegi", "Derakhte Zendegi", 307363, 1),
        Channel("Omid e Javedan", "امید جاودان", 306672, 1),
        Channel("Aein Jadeed", "Aein Jadeed", 306980, 1),
        Channel("Kanal Jadid", "Kanal Jadid", 303987, 1),
        Channel("Jewish TV", "Jewish TV", 307317, 1),
        Channel("Tahour TV", "Tahour TV", 307466, 1),
        Channel("Imam Hussein TV 1", "امام حسین ۱", 306954, 1),
        
        // ===== IRIB - Erasaneh (Genre 2) =====
        Channel("IRIB Channel 1", "شبکه یک", 300488, 2),
        Channel("IRIB Channel 2", "شبکه دو", 300489, 2),
        Channel("IRIB Channel 3", "شبکه سه", 300490, 2),
        Channel("IRIB Channel 4", "شبکه چهار", 300491, 2),
        Channel("IRIB Channel 5", "شبکه پنج", 300492, 2),
        Channel("IRINN", "شبکه خبر", 300486, 2),
        Channel("IRIB Amoozesh", "شبکه آموزش", 301104, 2),
        Channel("IRIB Ofogh", "افق", 300483, 2),
        Channel("IRIB Varzesh", "ورزش", 304077, 2),
        Channel("IRIB Namayesh", "نمایش", 304081, 2),
        Channel("IRIB Nasim", "نسیم", 306206, 2),
        Channel("IRIB Pooya", "پویا", 304112, 2),
        Channel("IRIB Salamat", "سلامت", 304109, 2),
        Channel("IRIB Mostanad", "مستند", 304104, 2),
        Channel("IRIB Omid", "شبکه امید", 306858, 2),
        Channel("Tamasha", "تماشا", 305052, 2),
        Channel("iFilm Farsi", "آی فیلم", 304079, 2),
        
        // ===== Music (Genre 10) =====
        Channel("PMC Music", "PMC Music", 303569, 10),
        Channel("RJ TV", "RJ TV", 306141, 10),
        Channel("Navahang", "نواهنگ", 307617, 10),
        Channel("Sun Music", "Sun Music", 307213, 10),
        Channel("Ava Film", "آوا فیلم", 300450, 10),
        Channel("Avang", "Avang", 307196, 10),
        Channel("Sonati Music", "موسیقی سنتی", 302661, 10),
        Channel("Music 1", "Music 1", 306898, 10),
        Channel("Me TV", "Me TV", 306959, 10),
        Channel("KMC", "KMC", 307627, 10),
        Channel("Persiana Rap", "Persiana Rap", 307474, 10),
        Channel("Persiana Nostalgia", "پرشیانا نوستالژی", 307299, 10),
        Channel("Persiana Music", "پرشیانا موسیقی", 307519, 10),
        Channel("Persiana Folk", "Persiana Folk", 307353, 10),
        Channel("PMC Royale", "PMC Royale", 307388, 10),
        Channel("Romantico", "Romantico", 307238, 10),
        Channel("AyazTV", "AyazTV", 307553, 10),
        Channel("iTV Persian Music", "iTV Persian Music", 307343, 10),
        
        // ===== Sports (Genre 84) =====
        Channel("IRIB Varzesh", "ورزش", 304077, 84),
        Channel("Persiana Sports 1", "Persiana Sport", 307480, 84),
        Channel("Persiana Sports 2", "Persiana Sports 2", 307548, 84),
        Channel("Persiana Sports 3", "Persiana Sports 3", 307599, 84),
        Channel("Persiana Sports 4", "Persiana Sports 4", 307516, 84),
        Channel("Persiana Fight", "Persiana Fight", 307569, 84),
        Channel("Pro Sport International", "Pro Sport International", 307621, 84),
        Channel("GEM SPORT", "GEM Sport", 307575, 84)
    )
    
    override val mainPage = mainPageOf(
        "1" to "📺 Persian",
        "2" to "📡 IRIB (Erasaneh)",
        "10" to "🎵 Music",
        "84" to "⚽ Sports"
    )
    
    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "*/*")
        
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        val response = httpClient.newCall(requestBuilder.build()).execute()
        return response.body?.string() ?: ""
    }
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val genreId = request.data.toIntOrNull() ?: 1
        val filtered = allChannels.filter { it.genreId == genreId }.distinctBy { it.name }
        
        val home = filtered.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/ch/${java.net.URLEncoder.encode(channel.name, "UTF-8")}",
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
            it.name.contains(query, ignoreCase = true)
        }.distinctBy { it.name }
        
        return results.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/ch/${java.net.URLEncoder.encode(channel.name, "UTF-8")}",
                TvType.Live
            ) {
                this.posterUrl = channel.logoUrl
            }
        }
    }
    
    override suspend fun load(url: String): LoadResponse {
        val encodedName = url.substringAfterLast("/ch/")
        val channelName = java.net.URLDecoder.decode(encodedName, "UTF-8")
        val channel = allChannels.find { it.name == channelName }
        
        return newMovieLoadResponse(
            channel?.displayName ?: channelName,
            url,
            TvType.Live,
            channelName  // Pass decoded name for loadLinks
        ) {
            this.posterUrl = channel?.logoUrl ?: ""
            this.plot = "📺 Live stream: ${channel?.displayName ?: channelName}"
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
            
            // Step 1: Initialize session
            httpGet("$playerBaseUrl/Player.aspx")
            
            // Step 2: Get stream URL - REMOVE SPACES from channel name (API needs no-space names)
            val apiChannelName = channelName.replace(" ", "")
            val requestUrl = "$ajaxUrl?action=getStreamURL&ClusterName=zixi-glwiz-mobile&RecType=4&itemName=$apiChannelName&ScreenMode=0"
            val response = httpGet(requestUrl, mapOf(
                "Referer" to "$playerBaseUrl/p2.html",
                "X-Requested-With" to "XMLHttpRequest"
            ))
            
            // Extract stream URL - only unescape JSON encoding, no other manipulation
            val respRegex = Regex(""""resp"\s*:\s*"([^"]+)"""")
            val match = respRegex.find(response)
            
            if (match != null) {
                val streamUrl = match.groupValues[1]
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
                
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
