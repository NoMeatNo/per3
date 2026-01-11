package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * IranWiz Provider - Persian Live TV from GLWiz
 * 
 * Stream extraction pattern (VERIFIED WORKING in tests):
 * 1. Load Player.aspx to initialize session cookie
 * 2. Call Ajax.aspx?action=getStreamURL&itemName=CHANNEL
 * 3. Response contains HLS m3u8 URL with auth tokens
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
    
    // Logo base URL (discovered from GLWiz localStorage)
    private val logoBaseUrl = "https://hd200.glwiz.com/menu/epg/imagesNew/cim_"
    
    // Channel data class
    data class Channel(
        val internalName: String,  // Used in stream URL (no spaces)
        val displayName: String,   // Shown to user
        val id: Int,               // For logo URL
        val genreId: Int           // For category filtering
    ) {
        val logoUrl: String get() = "${logoBaseUrl}${id}.png"
    }
    
    // Complete channel list (from GLWiz localStorage dump)
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
        Channel("RudiaoTV", "Rudiao TV", 305051, 1),
        
        // News (Genre 89)
        Channel("IranInternational", "ایران اینترنشنال", 306823, 89),
        Channel("BBCPersian", "BBC Persian", 305032, 89),
        Channel("VOA", "صدای آمریکا", 305316, 89),
        Channel("EuronewsFarsi", "یورونیوز فارسی", 305312, 89),
        Channel("IraneFarda", "ایران فردا", 305306, 89),
        Channel("PayamAfghanistan", "پیام افغانستان", 306752, 89),
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
        Channel("IRIBAMIN", "شبکه آموزش", 305029, 2),
        Channel("IRIBQURAN", "شبکه قرآن", 305030, 2),
        Channel("IRIBVarzesh", "ورزش", 305303, 2),
        Channel("IRIBOfogh", "افق", 305304, 2),
        Channel("IRIBIfilm", "آی فیلم", 305305, 2),
        
        // Music (Genre 10)
        Channel("PMC", "PMC", 305064, 10),
        Channel("TapeshMusic", "Tapesh Music", 306607, 10),
        Channel("MusicPersian", "Music Persian", 306789, 10),
        Channel("RadioJavan", "Radio Javan TV", 306790, 10),
        
        // Sports (Genre 84)
        Channel("VarzeshTV", "ورزش", 305303, 84),
        Channel("IRIBVarzesh", "IRIB Varzesh", 305303, 84),
        
        // Movies & Series (Genre 19)
        Channel("GemMovie", "GEM Movie", 305060, 19),
        Channel("GemSeries", "GEM Series", 305061, 19),
        Channel("Farsi1", "Farsi1", 305062, 19),
        Channel("GemBollywood", "GEM Bollywood", 305063, 19),
        Channel("IRIBIfilm", "iFilm", 305305, 19),
        Channel("MBC4", "MBC4", 305998, 19),
        Channel("MBCPersia", "MBC Persia", 305999, 19),
        
        // Kids (Genre 4)
        Channel("GemKids", "GEM Kids", 305065, 4),
        Channel("IRIBPooya", "شبکه پویا", 305031, 4),
        Channel("Babytvfarsi", "Baby TV Farsi", 306000, 4),
        
        // Religious (Genre 85)
        Channel("IRIBQuran", "شبکه قرآن", 305030, 85),
        Channel("Kawthar", "کوثر", 305307, 85),
        Channel("Velayat", "ولایت", 305308, 85),
        
        // Other Languages (Genre 3)
        Channel("AFGTolo", "طلوع", 306001, 3),
        Channel("AFGLemar", "لمر", 306002, 3),
        Channel("Ariana", "آریانا", 306003, 3),
        Channel("KurdChannel", "کورد چنل", 306004, 3),
        Channel("TRT", "TRT", 306005, 3)
    )
    
    // Category definitions
    private val categories = listOf(
        1 to "📺 Persian",
        89 to "📰 News",
        2 to "📡 IRIB",
        19 to "🎬 Movies & Series",
        10 to "🎵 Music",
        84 to "⚽ Sports",
        4 to "👶 Kids",
        85 to "🕌 Religious",
        3 to "🌍 Other Languages"
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
            this.plot = "Live stream from GLWiz"
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val channelName = data
            
            // Step 1: Initialize session by loading player page
            try {
                app.get("$playerBaseUrl/Player.aspx").text
            } catch (e: Exception) {
                // Continue even if this fails
            }
            
            // Step 2: Get stream URL
            val streamUrl = getStreamUrl(channelName) ?: return false
            
            // Step 3: Return the link
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
        } catch (e: Exception) {
            return false
        }
    }
    
    private suspend fun getStreamUrl(channelName: String): String? {
        try {
            val url = "$ajaxUrl?action=getStreamURL&ClusterName=zixi-glwiz-mobile&RecType=4&itemName=$channelName&ScreenMode=0"
            
            val response = app.get(
                url,
                headers = mapOf(
                    "Referer" to "$playerBaseUrl/p2.html",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).text
            
            // Response: {"resp":"https://GLWizHHLS20.glwiz.com:443/Channel.m3u8?user=...&session=..."}
            val respRegex = Regex(""""resp"\s*:\s*"([^"]+)"""")
            val match = respRegex.find(response)
            
            if (match != null) {
                return match.groupValues[1]
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
}
