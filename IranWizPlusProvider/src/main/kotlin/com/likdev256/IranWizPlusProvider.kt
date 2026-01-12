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
 * IranWiz Plus Provider - Persian Live TV from GLWiz + Other News Channels
 */
class IranWizPlusProvider : MainAPI() {
    override var mainUrl = "https://www.glwiz.com"
    override var name = "IranWiz Plus"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)
    
    // Additional New Channels Source
    private val otherNewsUrls = mapOf(
        "FoxNews" to listOf("https://www.newslive.com/american/fox-news.html"),
        "CNN" to listOf("https://www.newslive.com/american/cnn-stream.html"),
        "MSNBC" to listOf(
            "https://www.newslive.com/american/msnbc-news-live.html",
            "https://iptv-web.app/US/MSNBC.us/"
        ),
        "CNBC" to listOf("https://www.newslive.com/business/cnbc-live-free.html"),
        "ABCNews" to listOf("https://iptv-web.app/US/ABCNewsLive.us/"),
        "InfoWars" to listOf("https://iptv-web.app/US/InfoWars.us/"),
        "NBCNews" to listOf("https://iptv-web.app/US/NBCNewsNOW.us/"),
        "WAGA" to listOf("https://iptv-web.app/US/WAGADT1.us/"),
        // Cooking
        "AmericasTestKitchen" to listOf("https://iptv-web.app/US/AmericasTestKitchen.us/"),
        "bonappetit" to listOf("https://iptv-web.app/US/bonappetit.us/"),
        "GordonRamsaysHellsKitchen" to listOf("https://iptv-web.app/US/GordonRamsaysHellsKitchen.us/"),
        "Tastemade" to listOf("https://iptv-web.app/US/Tastemade.us/"),
        // Documentary
        "CourtTV" to listOf("https://iptv-web.app/US/CourtTV.us/"),
        "Dateline247" to listOf("https://iptv-web.app/US/Dateline247.us/"),
        "ForensicFiles" to listOf("https://iptv-web.app/US/ForensicFiles.us/"),
        "History" to listOf("https://iptv-web.app/US/History.us/"),
        "InkMaster" to listOf("https://iptv-web.app/US/InkMaster.us/"),
        "LawCrime" to listOf("https://iptv-web.app/US/LawCrime.us/"),
        "MilitaryHistory" to listOf("https://iptv-web.app/US/MilitaryHistory.us/"),
        "NationalGeographic" to listOf("https://iptv-web.app/US/NationalGeographic.us/"),
        "NationalGeographicWild" to listOf("https://iptv-web.app/US/NationalGeographicWild.us/"),
        "PlutoTVCrime" to listOf("https://iptv-web.app/US/PlutoTVCrime.us/"),
        "PlutoTVHistory" to listOf("https://iptv-web.app/US/PlutoTVHistory.us/"),
        "PlutoTVInvestigation" to listOf("https://iptv-web.app/US/PlutoTVInvestigation.us/")
    )

    private val playerBaseUrl = "$mainUrl/Pages/Player"
    private val ajaxUrl = "$playerBaseUrl/Ajax.aspx"
    
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    cookieStore.getOrPut(host) { mutableListOf() }.apply {
                        cookies.forEach { newCookie -> removeAll { it.name == newCookie.name } }
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
    
    // Genre IDs for categories
    companion object {
        const val GENRE_NEWS = 89
        const val GENRE_PERSIAN = 1
        const val GENRE_IRIB = 2
        const val GENRE_MOVIES = 19
        const val GENRE_MUSIC = 10
        const val GENRE_SPORTS = 84
        const val GENRE_KIDS = 4
        const val GENRE_RELIGIOUS = 85
        const val GENRE_OTHER_NEWS = 100
        const val GENRE_COOKING = 101
        const val GENRE_DOCUMENTARY = 102
    }
    
    data class Channel(
        val streamName: String,
        val displayName: String,
        val id: Int,
        val genreId: Int
    ) {
        val logoUrl: String get() = "https://hd200.glwiz.com/menu/epg/imagesNew/cim_${id}.png"
    }
    
    // Channel list
    private val allChannels = listOf(
         // ===== Other News (Genre 100) =====
        Channel("FoxNews", "Fox News", 0, GENRE_OTHER_NEWS),
        Channel("CNN", "CNN", 0, GENRE_OTHER_NEWS),
        Channel("MSNBC", "MSNBC", 0, GENRE_OTHER_NEWS),
        Channel("CNBC", "CNBC", 0, GENRE_OTHER_NEWS),
        Channel("ABCNews", "ABC News Live", 0, GENRE_OTHER_NEWS),
        Channel("InfoWars", "InfoWars", 0, GENRE_OTHER_NEWS),
        Channel("NBCNews", "NBC News NOW", 0, GENRE_OTHER_NEWS),
        Channel("WAGA", "WAGA Fox 5", 0, GENRE_OTHER_NEWS),
        
        // ===== Cooking (Genre 101) =====
        Channel("GordonRamsaysHellsKitchen", "Hell's Kitchen", 0, GENRE_COOKING),
        Channel("bonappetit", "Bon Appétit", 0, GENRE_COOKING),
        Channel("AmericasTestKitchen", "America's Test Kitchen", 0, GENRE_COOKING),
        Channel("Tastemade", "Tastemade", 0, GENRE_COOKING),
        
        // ===== Documentary (Genre 102) =====
        Channel("NationalGeographic", "National Geographic", 0, GENRE_DOCUMENTARY),
        Channel("NationalGeographicWild", "Nat Geo Wild", 0, GENRE_DOCUMENTARY),
        Channel("History", "History Channel", 0, GENRE_DOCUMENTARY),
        Channel("MilitaryHistory", "Military History", 0, GENRE_DOCUMENTARY),
        Channel("ForensicFiles", "Forensic Files", 0, GENRE_DOCUMENTARY),
        Channel("Dateline247", "Dateline 24/7", 0, GENRE_DOCUMENTARY),
        Channel("CourtTV", "Court TV", 0, GENRE_DOCUMENTARY),
        Channel("LawCrime", "Law & Crime", 0, GENRE_DOCUMENTARY),
        Channel("InkMaster", "Ink Master", 0, GENRE_DOCUMENTARY),
        Channel("PlutoTVHistory", "Pluto TV History", 0, GENRE_DOCUMENTARY),
        Channel("PlutoTVCrime", "Pluto TV Crime", 0, GENRE_DOCUMENTARY),
        Channel("PlutoTVInvestigation", "Pluto TV Investigation", 0, GENRE_DOCUMENTARY),
        
        // ===== News (Genre 89) =====
        Channel("IranInternational", "ایران اینترنشنال", 306823, GENRE_NEWS),
        Channel("BBCPersian", "بی بی سی فارسی", 301133, GENRE_NEWS),
        Channel("VoA", "صدای آمریکا", 300480, GENRE_NEWS),
        Channel("EuroNewsFarsi", "یورونیوز فارسی", 301404, GENRE_NEWS),
        Channel("IraneFarda", "ایران فردا", 302551, GENRE_NEWS),
        Channel("AfghanistanInternational", "افغانستان اینترنشنال", 307415, GENRE_NEWS),
        Channel("IRINN", "شبکه خبر IRINN", 300486, GENRE_NEWS),
        Channel("RadioFarda", "رادیوفردا", 306419, GENRE_NEWS),
        Channel("IranOneNews", "Iran One News", 307625, GENRE_NEWS),
        
        // ===== Movies & Series (Genre 19) =====
        Channel("EkranMovies", "Ekran Movies", 306747, GENRE_MOVIES),
        Channel("Film1", "Film 1", 303523, GENRE_MOVIES),
        Channel("PersianaCinema", "پرشیانا سینما", 306892, GENRE_MOVIES),
        Channel("PersianaSeries", "Persiana Series", 307301, GENRE_MOVIES),
        Channel("PersianaComedy", "Persiana Comedy", 307313, GENRE_MOVIES),
        Channel("PersianaKorea", "Persiana Korea", 307341, GENRE_MOVIES),
        Channel("AVASeries", "AVA Series", 307252, GENRE_MOVIES),
        Channel("AVAFamily", "AVA Family", 307042, GENRE_MOVIES),
        Channel("GrandCinema", "Grand Cinema", 307227, GENRE_MOVIES),
        Channel("iFilmFarsi", "آی فیلم", 304079, GENRE_MOVIES),
        Channel("IRIBNamayesh", "نمایش", 304081, GENRE_MOVIES),
        Channel("T2Movies", "T2 Movies", 307377, GENRE_MOVIES),
        Channel("IranProudSeriesPlus", "Iran Proud Series Plus", 307357, GENRE_MOVIES),
        Channel("ClassicTV", "Classic TV", 302082, GENRE_MOVIES),
        Channel("ICC", "سینمایی ایرانیان", 302078, GENRE_MOVIES),
        
        // ===== Persian General (Genre 1) =====
        Channel("TapeshAmerica", "Tapesh America", 306606, GENRE_PERSIAN),
        Channel("ChannelOne", "کانال یک", 307514, GENRE_PERSIAN),
        Channel("ITN", "ITN آمریکا", 305049, GENRE_PERSIAN),
        Channel("ParsTV", "تلویزیون پارس", 300454, GENRE_PERSIAN),
        Channel("TAPESH", "تپش", 300447, GENRE_PERSIAN),
        Channel("MBCPersia", "MBC Persia", 300513, GENRE_PERSIAN),
        Channel("RJTV", "RJ TV", 306141, GENRE_PERSIAN),
        Channel("TasvirEIran", "تصویر ایران", 300457, GENRE_PERSIAN),
        Channel("OmidEIran", "امید ایران", 300462, GENRE_PERSIAN),
        Channel("MihanTV", "میهن", 300902, GENRE_PERSIAN),
        Channel("AyenehTV", "آینه", 300326, GENRE_PERSIAN),
        Channel("T2International", "T2 International", 307381, GENRE_PERSIAN),
        Channel("T2America", "T2 America", 307261, GENRE_PERSIAN),
        Channel("IraneAryaee", "ایران آریایی", 302136, GENRE_PERSIAN),
        Channel("SimayAzadi", "سیمای آزادی", 307555, GENRE_PERSIAN),
        Channel("YourTimeTV", "یورتایم", 307230, GENRE_PERSIAN),
        Channel("Today", "Today", 303003, GENRE_PERSIAN),
        Channel("AsreEmrooz", "عصر امروز", 301196, GENRE_PERSIAN),
        Channel("Didgah", "دیدگاه", 305207, GENRE_PERSIAN),
        Channel("Payvand", "پیوند", 305183, GENRE_PERSIAN),
        Channel("ITNEurope", "ITN ایران", 305190, GENRE_PERSIAN),
        Channel("Shabakeh7", "شبکه‌۷", 305654, GENRE_PERSIAN),
        Channel("WomanTV", "زن زندگی‌آزادی", 307008, GENRE_PERSIAN),
        Channel("PJTV", "پیام جوان", 303012, GENRE_PERSIAN),
        Channel("PersianaFamily", "پرشیانا فمیلی", 306882, GENRE_PERSIAN),
        Channel("PersianaIranian", "پرشیانا ایرانیان", 307273, GENRE_PERSIAN),
        Channel("PersianaPlus", "Persiana Plus", 307361, GENRE_PERSIAN),
        Channel("PersianaDocs", "Persiana Docs", 307478, GENRE_PERSIAN),
        Channel("PersianaReality", "Persiana Reality", 307491, GENRE_PERSIAN),
        Channel("PersianaTurkiye", "Persiana Turkiye", 307275, GENRE_PERSIAN),
        Channel("AzStarTV", "Az Star TV", 306657, GENRE_PERSIAN),
        Channel("Hambastegi", "Hambastegi", 307483, GENRE_PERSIAN),
        Channel("4UTV", "4U TV", 306743, GENRE_PERSIAN),
        Channel("4uFamily", "4u Family", 307168, GENRE_PERSIAN),
        Channel("FXTV", "FX TV", 307384, GENRE_PERSIAN),
        Channel("NetTV", "Net TV", 307305, GENRE_PERSIAN),
        Channel("MTC", "MTC", 307309, GENRE_PERSIAN),
        Channel("InfinityTV", "Infinity TV", 307335, GENRE_PERSIAN),
        Channel("MaahTV", "Maah TV", 307315, GENRE_PERSIAN),
        Channel("KhaterehTV", "خاطره", 307561, GENRE_PERSIAN),
        Channel("FarsiTV", "Farsi TV HD", 307454, GENRE_PERSIAN),
        Channel("IRTV", "IRTV ایرانیان", 307563, GENRE_PERSIAN),
        Channel("HomePlus", "Home Plus", 307565, GENRE_PERSIAN),
        Channel("DidanihaTV", "دیدنیها", 307591, GENRE_PERSIAN),
        
        // ===== IRIB (Genre 2) =====
        Channel("IRIBChannel1", "شبکه یک", 300488, GENRE_IRIB),
        Channel("IRIBChannel2", "شبکه دو", 300489, GENRE_IRIB),
        Channel("IRIBChannel3", "شبکه سه", 300490, GENRE_IRIB),
        Channel("IRIBChannel4", "شبکه چهار", 300491, GENRE_IRIB),
        Channel("IRIBChannel5", "شبکه پنج", 300492, GENRE_IRIB),
        Channel("IRIBAmoozesh", "شبکه آموزش", 301104, GENRE_IRIB),
        Channel("IRIBOfogh", "افق", 300483, GENRE_IRIB),
        Channel("IRIBVarzesh", "ورزش", 304077, GENRE_IRIB),
        Channel("IRIBNasim", "نسیم", 306206, GENRE_IRIB),
        Channel("IRIBSalamat", "سلامت", 304109, GENRE_IRIB),
        Channel("IRIBMostanad", "مستند", 304104, GENRE_IRIB),
        Channel("IRIBOmid", "شبکه امید", 306858, GENRE_IRIB),
        Channel("Tamasha", "تماشا", 305052, GENRE_IRIB),
        
        // ===== Music (Genre 10) =====
        Channel("PMCMusic", "PMC Music", 303569, GENRE_MUSIC),
        Channel("Navahang", "نواهنگ", 307617, GENRE_MUSIC),
        Channel("SunMusic", "Sun Music", 307213, GENRE_MUSIC),
        Channel("AvaFilm", "آوا فیلم", 300450, GENRE_MUSIC),
        Channel("Avang", "Avang", 307196, GENRE_MUSIC),
        Channel("SonatiMusic", "موسیقی سنتی", 302661, GENRE_MUSIC),
        Channel("Music1", "Music 1", 306898, GENRE_MUSIC),
        Channel("MeTV", "Me TV", 306959, GENRE_MUSIC),
        Channel("KMC", "KMC", 307627, GENRE_MUSIC),
        Channel("PersianaRap", "Persiana Rap", 307474, GENRE_MUSIC),
        Channel("PersianaNostalgia", "پرشیانا نوستالژی", 307299, GENRE_MUSIC),
        Channel("PersianaMusic", "پرشیانا موسیقی", 307519, GENRE_MUSIC),
        Channel("PersianaFolk", "Persiana Folk", 307353, GENRE_MUSIC),
        Channel("PMCRoyale", "PMC Royale", 307388, GENRE_MUSIC),
        Channel("Romantico", "Romantico", 307238, GENRE_MUSIC),
        Channel("AyazTV", "AyazTV", 307553, GENRE_MUSIC),
        Channel("iTVPersianMusic", "iTV Persian Music", 307343, GENRE_MUSIC),
        
        // ===== Sports (Genre 84) =====
        Channel("PersianaSports1", "Persiana Sport", 307480, GENRE_SPORTS),
        Channel("PersianaSports2", "Persiana Sports 2", 307548, GENRE_SPORTS),
        Channel("PersianaSports3", "Persiana Sports 3", 307599, GENRE_SPORTS),
        Channel("PersianaSports4", "Persiana Sports 4", 307516, GENRE_SPORTS),
        Channel("PersianaFight", "Persiana Fight", 307569, GENRE_SPORTS),
        Channel("ProSportInternational", "Pro Sport International", 307621, GENRE_SPORTS),
        Channel("GEMSPORT", "GEM Sport", 307575, GENRE_SPORTS),
        
        // ===== Kids (Genre 4) =====
        Channel("EkranKids", "Ekran Kids", 306831, GENRE_KIDS),
        Channel("PersianaJunior", "پرشیانا جونیور", 307183, GENRE_KIDS),
        Channel("IRIBPooya", "پویا", 304112, GENRE_KIDS),
        
        // ===== Religious (Genre 85) =====
        Channel("NejatTV", "Nejat TV", 307425, GENRE_RELIGIOUS),
        Channel("RaheNejat", "Rahe Nejat", 307511, GENRE_RELIGIOUS),
        Channel("ImanBeMasih", "Iman Be Masih", 307373, GENRE_RELIGIOUS),
        Channel("DerakhteZendegi", "Derakhte Zendegi", 307363, GENRE_RELIGIOUS),
        Channel("OmideJavedan", "امید جاودان", 306672, GENRE_RELIGIOUS),
        Channel("Mohabat", "محبت", 300931, GENRE_RELIGIOUS),
        Channel("KanalJadid", "Kanal Jadid", 303987, GENRE_RELIGIOUS),
        Channel("JewishTV", "Jewish TV", 307317, GENRE_RELIGIOUS),
        Channel("TahourTV", "Tahour TV", 307466, GENRE_RELIGIOUS),
        Channel("ImamHusseinTV1", "امام حسین ۱", 306954, GENRE_RELIGIOUS),
        Channel("PayameAramesh", "پیام آرامش", 303657, GENRE_RELIGIOUS),
        Channel("GanjEHozour", "گنج حضور", 301192, GENRE_RELIGIOUS),
        Channel("IsraelParsTV", "إسرائيل پارس", 307103, GENRE_RELIGIOUS)
    )
    
    // Categories for main page
    override val mainPage = mainPageOf(
        "${GENRE_OTHER_NEWS}" to "🌍 Other News",
        "${GENRE_NEWS}" to "📰 News",
        "${GENRE_MOVIES}" to "🎬 Movies & Series",
        "${GENRE_PERSIAN}" to "📺 Persian",
        "${GENRE_IRIB}" to "📡 IRIB",
        "${GENRE_MUSIC}" to "🎵 Music",
        "${GENRE_SPORTS}" to "⚽ Sports",
        "${GENRE_KIDS}" to "👶 Kids",
        "${GENRE_RELIGIOUS}" to "🕌 Religious",
        "${GENRE_COOKING}" to "🍳 Cooking",
        "${GENRE_DOCUMENTARY}" to "🦁 Documentary"
    )
    
    // Get genre name for display
    private fun getGenreName(genreId: Int): String = when(genreId) {
        GENRE_OTHER_NEWS -> "🌍 Other News"
        GENRE_NEWS -> "📰 News"
        GENRE_MOVIES -> "🎬 Movies & Series"
        GENRE_PERSIAN -> "📺 Persian"
        GENRE_IRIB -> "📡 IRIB"
        GENRE_MUSIC -> "🎵 Music"
        GENRE_SPORTS -> "⚽ Sports"
        GENRE_KIDS -> "👶 Kids"
        GENRE_RELIGIOUS -> "🕌 Religious"
        GENRE_RELIGIOUS -> "🕌 Religious"
        GENRE_COOKING -> "🍳 Cooking"
        GENRE_DOCUMENTARY -> "🦁 Documentary"
        else -> "📺 Other"
    }
    
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
        val genreId = request.data.toIntOrNull() ?: GENRE_NEWS
        val filtered = allChannels.filter { it.genreId == genreId }.distinctBy { it.streamName }
        
        val home = filtered.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/ch/${channel.streamName}|${channel.genreId}",
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
            it.streamName.contains(query, ignoreCase = true)
        }.distinctBy { it.streamName }
        
        return results.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/ch/${channel.streamName}|${channel.genreId}",
                TvType.Live
            ) {
                this.posterUrl = channel.logoUrl
            }
        }
    }
    
    override suspend fun load(url: String): LoadResponse {
        // URL format: mainUrl/ch/StreamName|GenreId
        val parts = url.substringAfterLast("/ch/").split("|")
        val streamName = parts.getOrNull(0) ?: ""
        val genreId = parts.getOrNull(1)?.toIntOrNull() ?: GENRE_PERSIAN
        
        val channel = allChannels.find { it.streamName == streamName }
        
        // Get related channels from same genre (excluding current)
        val relatedChannels = allChannels
            .filter { it.genreId == genreId && it.streamName != streamName }
            .shuffled()
            .take(12)
        
        val recommendations = relatedChannels.map { ch ->
            newMovieSearchResponse(
                ch.displayName,
                "$mainUrl/ch/${ch.streamName}|${ch.genreId}",
                TvType.Live
            ) {
                this.posterUrl = ch.logoUrl
            }
        }
        
        return newMovieLoadResponse(
            channel?.displayName ?: streamName,
            url,
            TvType.Live,
            streamName
        ) {
            this.posterUrl = channel?.logoUrl ?: ""
            this.plot = "📺 ${channel?.displayName ?: streamName}\n\n${getGenreName(genreId)}"
            this.recommendations = recommendations
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val streamName = data
            
            // CHECK IF THIS IS AN "OTHER NEWS" CHANNEL
            if (otherNewsUrls.containsKey(streamName)) {
                val urls = otherNewsUrls[streamName]!!
                var foundAny = false
                
                urls.forEach { url ->
                    try {
                        // We use standard app.get() for these sites
                        val response = app.get(url).text
                        // Find .m3u8 link (it works for both static and dynamic with tokens)
                        // 1. Find standard .m3u8 links
                        val m3u8Regex = Regex("""https?://[^"']+\.m3u8[^"']*""")
                        val m3u8Matches = m3u8Regex.findAll(response).map { 
                            it.value.substringBefore("&quot;")
                                .replace("\\/", "/")
                                .replace("&amp;", "&")
                        }

                        // 2. Find links hidden in Astro/JSON props (e.g. [0,&quot;https://...&quot;])
                        val jsonRegex = Regex("""\[0,&quot;(https?://[^&]+)&quot;\]""")
                        val jsonMatches = jsonRegex.findAll(response).map {
                             it.groupValues[1]
                                .replace("\\/", "/")
                        }

                        // Combine and deduplicate
                        val matches = (m3u8Matches + jsonMatches).distinct()
                        
                        matches.forEach { streamUrl ->

                            // Basic deduplication is handled by CloudStream usually, but we send all valid ones
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - $streamName (${if(url.contains("newslive")) "NewsLive" else "IPTVWeb"})",
                                    url = streamUrl
                                ).apply {
                                    this.referer = if (url.contains("iptv-web")) "https://iptv-web.app/" else "https://www.newslive.com/"
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            foundAny = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return foundAny
            }

            // STANDARD GLWIZ LOGIC
            cookieStore.clear()
            httpGet("$playerBaseUrl/Player.aspx")
            
            val requestUrl = "$ajaxUrl?action=getStreamURL&ClusterName=zixi-glwiz-mobile&RecType=4&itemName=$streamName&ScreenMode=0"
            val response = httpGet(requestUrl, mapOf(
                "Referer" to "$playerBaseUrl/p2.html",
                "X-Requested-With" to "XMLHttpRequest"
            ))
            
            val respRegex = Regex(""""resp"\s*:\s*"([^"]+)"""")
            val match = respRegex.find(response)
            
            if (match != null) {
                val streamUrl = match.groupValues[1]
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
                    // Fix unencoded spaces which break playback
                    .replace(" ", "%20")
                
                if (!streamUrl.contains("GlwizPromo")) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name - $streamName",
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
            e.printStackTrace()
            false
        }
    }
}
